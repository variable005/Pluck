package com.example.pluck.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/** A private, normalized gallery image plus optional embedded place coordinates. */
data class ImportedJourneyPhoto(
    val file: File,
    val latitude: Double?,
    val longitude: Double?
)

@Singleton
class JourneyPhotoStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val directory = File(context.filesDir, "journey_photos").apply { mkdirs() }

    /** Creates an app-private JPEG destination for a camera capture or gallery import. */
    fun newPhotoFile(): File = File(directory, "pluck_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}.jpg")

    /**
     * Reads a picker URI immediately and writes a normalized JPEG into Pluck's private storage.
     *
     * The system photo picker grants short-lived read access only, so keeping the selected URI in
     * Room would be unsafe. Re-encoding also makes the file type match the JPEG MIME type used by
     * the cloud providers. EXIF orientation is baked into the pixels; only valid GPS coordinates
     * are returned for the locally stored place hint.
     */
    suspend fun importPhoto(uri: Uri): ImportedJourneyPhoto = withContext(Dispatchers.IO) {
        val sourceMetadata = readSourceMetadata(uri)
        val destination = newPhotoFile()
        try {
            val normalized = decodeAndNormalize(uri, sourceMetadata.orientation)
                ?: throw IOException("Pluck couldn't read that image.")
            try {
                FileOutputStream(destination).use { output ->
                    check(normalized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                        "Pluck couldn't save that image."
                    }
                }
            } finally {
                normalized.recycle()
            }
            if (destination.length() == 0L) throw IOException("Pluck couldn't save that image.")
            ImportedJourneyPhoto(
                file = destination,
                latitude = sourceMetadata.latitude,
                longitude = sourceMetadata.longitude
            )
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    private fun readSourceMetadata(uri: Uri): SourceMetadata = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).let { exif ->
                val coordinates = exif.latLong
                val latitude = coordinates?.getOrNull(0)
                val longitude = coordinates?.getOrNull(1)
                SourceMetadata(
                    orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    ),
                    latitude = latitude?.takeIf { it.isFinite() && it in -90.0..90.0 },
                    longitude = longitude?.takeIf { it.isFinite() && it in -180.0..180.0 }
                ).let { metadata ->
                    if (metadata.latitude == null || metadata.longitude == null) {
                        metadata.copy(latitude = null, longitude = null)
                    } else {
                        metadata
                    }
                }
            }
        }
    }.getOrNull() ?: SourceMetadata()

    private fun decodeAndNormalize(uri: Uri, orientation: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sample = sampleSize(bounds.outWidth, bounds.outHeight)
        val decoded = resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(
                input,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            )
        } ?: return null
        return decoded
            .applyExifOrientation(orientation)
            .scaleDown(MAX_STORED_DIMENSION)
            .flattenForJpeg()
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (max(width, height) / sample > MAX_STORED_DIMENSION * 2) sample *= 2
        return sample
    }

    private fun Bitmap.applyExifOrientation(orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return this
        }
        val transformed = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        if (transformed !== this) recycle()
        return transformed
    }

    private fun Bitmap.scaleDown(maxDimension: Int): Bitmap {
        val largest = max(width, height)
        if (largest <= maxDimension) return this
        val scale = maxDimension.toFloat() / largest.toFloat()
        val scaled = Bitmap.createScaledBitmap(
            this,
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (scaled !== this) recycle()
        return scaled
    }

    private fun Bitmap.flattenForJpeg(): Bitmap {
        if (!hasAlpha()) return this
        val flattened = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(flattened).apply {
            drawColor(Color.WHITE)
            drawBitmap(this@flattenForJpeg, 0f, 0f, null)
        }
        recycle()
        return flattened
    }

    private data class SourceMetadata(
        val orientation: Int = ExifInterface.ORIENTATION_NORMAL,
        val latitude: Double? = null,
        val longitude: Double? = null
    )

    private companion object {
        const val MAX_STORED_DIMENSION = 2_048
        const val JPEG_QUALITY = 90
    }
}
