package com.example.pluck.ui.story

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.format.DateFormat
import androidx.core.content.FileProvider
import com.example.pluck.domain.model.JourneyPhoto
import com.example.pluck.domain.model.StoryMood
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Date
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Renders private, local PNG share cards from an existing story.
 *
 * Story graphics are composed entirely on the device. Journey images are read only from their
 * local URI/path, downsampled before drawing, and never include an address, latitude, or
 * longitude in the resulting card. Output lives in the app cache and is shared through the
 * existing [FileProvider].
 */
object StoryShareRenderer {
    private const val PORTRAIT_WIDTH = 1080
    private const val PORTRAIT_HEIGHT = 1350
    private const val INSTAGRAM_STORY_WIDTH = 1080
    private const val INSTAGRAM_STORY_HEIGHT = 1920
    private const val X_LANDSCAPE_WIDTH = 1600
    private const val X_LANDSCAPE_HEIGHT = 900
    private const val HORIZONTAL_PADDING = 86
    private const val COLLAGE_MAX_PHOTOS = 4
    private const val TILE_GAP = 10f

    /** Formats intentionally sized for common social surfaces without relying on a network API. */
    enum class SocialCardFormat(
        /** Output width in pixels. */ val widthPx: Int,
        /** Output height in pixels. */ val heightPx: Int,
        /** A human-readable destination label for a Sharesheet title. */ val label: String
    ) {
        /** 9:16, suitable for Instagram Story and other vertical story surfaces. */
        INSTAGRAM_STORY(INSTAGRAM_STORY_WIDTH, INSTAGRAM_STORY_HEIGHT, "Instagram Story"),

        /** 16:9 landscape, suitable for an X post image. */
        X_LANDSCAPE(X_LANDSCAPE_WIDTH, X_LANDSCAPE_HEIGHT, "X landscape"),

        /** 4:5 portrait, suitable for a feed post. */
        PORTRAIT(PORTRAIT_WIDTH, PORTRAIT_HEIGHT, "Portrait")
    }

    /**
     * All data needed to create one private social card.
     *
     * [photos] must be supplied in journey order. The renderer preserves that order when it
     * selects photos for the collage; it deliberately does not inspect photo coordinates.
     */
    data class SocialCardRequest(
        val title: String,
        val content: String,
        val mood: StoryMood,
        val createdAt: Long,
        val photos: List<JourneyPhoto>,
        val format: SocialCardFormat = SocialCardFormat.INSTAGRAM_STORY,
        val excerpt: String? = null
    )

    /**
     * Builds a social-card PNG on [Dispatchers.IO] and returns a temporary, shareable URI.
     *
     * Call this from a coroutine (for example, a ViewModel or `rememberCoroutineScope`) so bitmap
     * decoding and PNG compression never block Compose or the main thread.
     */
    suspend fun renderSocialCard(context: Context, request: SocialCardRequest): Uri =
        withContext(Dispatchers.IO) {
            coroutineContext.ensureActive()
            writeSocialCard(context.applicationContext, request)
        }

    /**
     * Creates a ready-to-launch Android Sharesheet intent for a rendered [SocialCardRequest].
     * Rendering remains off the main thread because this API is suspend.
     */
    suspend fun socialCardIntent(context: Context, request: SocialCardRequest): Intent =
        shareSingle(
            uri = renderSocialCard(context, request),
            chooserTitle = "Share ${request.format.label} card"
        )

    /**
     * Legacy quote-card shortcut retained for the existing story reader.
     *
     * New UI should prefer [renderSocialCard] or [socialCardIntent] from a coroutine so image
     * work is performed in the background.
     */
    fun quoteIntent(
        context: Context,
        title: String,
        content: String,
        mood: StoryMood,
        createdAt: Long
    ): Intent = shareSingle(
        uri = writeCard(
            context = context,
            fileName = "pluck_quote_${System.currentTimeMillis()}.png",
            title = title,
            body = content.quoteExcerpt(),
            mood = mood,
            createdAt = createdAt,
            pageLabel = "A PLUCK QUOTE"
        ),
        chooserTitle = "Share quote card"
    )

    /** Legacy carousel shortcut retained for the existing story reader. */
    fun carouselIntent(
        context: Context,
        title: String,
        content: String,
        mood: StoryMood,
        createdAt: Long
    ): Intent {
        val cards = buildList {
            add(
                writeCard(
                    context = context,
                    fileName = "pluck_cover_${System.currentTimeMillis()}.png",
                    title = title,
                    body = "A ${mood.displayName.lowercase()} story, shaped by one real day.",
                    mood = mood,
                    createdAt = createdAt,
                    pageLabel = "PLUCK ORIGINAL"
                )
            )
            content.cardPages(maxPages = 5).forEachIndexed { index, page ->
                add(
                    writeCard(
                        context = context,
                        fileName = "pluck_story_${System.currentTimeMillis()}_$index.png",
                        title = title,
                        body = page,
                        mood = mood,
                        createdAt = createdAt,
                        pageLabel = "CHAPTER ${index + 1}"
                    )
                )
            }
        }
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/png"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(cards))
            clipData = ClipData.newRawUri("Pluck story cards", cards.first())
            cards.drop(1).forEach { clipData?.addItem(ClipData.Item(it)) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.let { Intent.createChooser(it, "Share story cards") }
    }

    private fun shareSingle(uri: Uri, chooserTitle: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Pluck share card", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.let { Intent.createChooser(it, chooserTitle) }

    /** Writes a rich story graphic. This is invoked only inside [renderSocialCard]'s IO context. */
    private fun writeSocialCard(context: Context, request: SocialCardRequest): Uri {
        val format = request.format
        val width = format.widthPx
        val height = format.heightPx
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val palette = request.mood.palette()
        canvas.drawColor(palette.background)

        val layout = socialCardLayout(format)
        drawSocialHeader(canvas, request, palette, width)
        drawCollage(
            context = context,
            canvas = canvas,
            photos = request.photos,
            bounds = layout.collageBounds,
            palette = palette
        )

        val textWidth = layout.textBounds.width().toInt()
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.foreground
            textSize = layout.titleSize
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
            )
        }
        val titleHeight = drawTextBlock(
            canvas = canvas,
            value = request.title,
            paint = titlePaint,
            left = layout.textBounds.left.toInt(),
            top = layout.textBounds.top.toInt(),
            width = textWidth,
            maxLines = layout.titleLines,
            lineSpacing = layout.titleLineSpacing
        )

        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.foreground
            alpha = 224
            textSize = layout.bodySize
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.NORMAL
            )
        }
        val excerpt = request.excerpt?.trim().takeUnless { it.isNullOrBlank() }
            ?: request.content.quoteExcerpt()
        drawTextBlock(
            canvas = canvas,
            value = excerpt,
            paint = bodyPaint,
            left = layout.textBounds.left.toInt(),
            top = (layout.textBounds.top + titleHeight + layout.titleToBodyGap).toInt(),
            width = textWidth,
            maxLines = layout.bodyLines,
            lineSpacing = layout.bodyLineSpacing
        )

        drawTravelBadge(
            canvas = canvas,
            text = request.photos.travelBadge(),
            palette = palette,
            left = layout.badgeLeft,
            top = layout.badgeTop
        )
        drawSocialFooter(
            context = context,
            canvas = canvas,
            mood = request.mood,
            createdAt = request.createdAt,
            palette = palette,
            left = layout.footerLeft,
            baseline = layout.footerBaseline
        )

        val output = cacheOutputFile(
            context,
            "pluck_${format.name.lowercase()}_${System.currentTimeMillis()}_${UUID.randomUUID()}.png"
        )
        val pending = File(output.parentFile, "${output.name}.tmp")
        try {
            FileOutputStream(pending).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    "Unable to encode story share card."
                }
                stream.fd.sync()
            }
            if (!pending.renameTo(output)) {
                pending.copyTo(output, overwrite = true)
                pending.delete()
            }
        } finally {
            bitmap.recycle()
            if (pending.exists()) pending.delete()
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", output)
    }

    private fun socialCardLayout(format: SocialCardFormat): SocialCardLayout = when (format) {
        SocialCardFormat.INSTAGRAM_STORY -> SocialCardLayout(
            collageBounds = RectF(56f, 224f, 1024f, 956f),
            textBounds = RectF(70f, 1058f, 1010f, 1600f),
            badgeLeft = 70f,
            badgeTop = 1694f,
            footerLeft = 70f,
            footerBaseline = 1848f,
            titleSize = 68f,
            bodySize = 38f,
            titleLines = 3,
            bodyLines = 6,
            titleLineSpacing = 8f,
            bodyLineSpacing = 11f,
            titleToBodyGap = 30f
        )

        SocialCardFormat.X_LANDSCAPE -> SocialCardLayout(
            collageBounds = RectF(60f, 160f, 760f, 744f),
            textBounds = RectF(844f, 192f, 1532f, 650f),
            badgeLeft = 844f,
            badgeTop = 682f,
            footerLeft = 844f,
            footerBaseline = 820f,
            titleSize = 58f,
            bodySize = 31f,
            titleLines = 3,
            bodyLines = 5,
            titleLineSpacing = 7f,
            bodyLineSpacing = 8f,
            titleToBodyGap = 22f
        )

        SocialCardFormat.PORTRAIT -> SocialCardLayout(
            collageBounds = RectF(56f, 190f, 1024f, 692f),
            textBounds = RectF(70f, 790f, 1010f, 1148f),
            badgeLeft = 70f,
            badgeTop = 1168f,
            footerLeft = 70f,
            footerBaseline = 1266f,
            titleSize = 66f,
            bodySize = 36f,
            titleLines = 2,
            bodyLines = 3,
            titleLineSpacing = 8f,
            bodyLineSpacing = 10f,
            titleToBodyGap = 24f
        )
    }

    private fun drawSocialHeader(
        canvas: Canvas,
        request: SocialCardRequest,
        palette: Palette,
        width: Int
    ) {
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent }
        canvas.drawRoundRect(RectF(56f, 76f, 116f, 88f), 6f, 6f, accentPaint)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.foreground
            textSize = 27f
            letterSpacing = 0.12f
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
            )
        }
        canvas.drawText("PLUCK ORIGINAL", 136f, 94f, labelPaint)
        val moodPaint = Paint(labelPaint).apply {
            color = palette.foreground
            alpha = 180
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(request.mood.displayName.uppercase(), width - 56f, 94f, moodPaint)
    }

    /** Draws an ordered, locally decoded photo collage; missing photos degrade to a private placeholder. */
    private fun drawCollage(
        context: Context,
        canvas: Canvas,
        photos: List<JourneyPhoto>,
        bounds: RectF,
        palette: Palette
    ) {
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.foreground
            alpha = 24
        }
        canvas.drawRoundRect(bounds, 42f, 42f, borderPaint)
        val innerBounds = RectF(bounds).apply { inset(8f, 8f) }
        val selectedPhotos = photos.selectForCollage()
        if (selectedPhotos.isEmpty()) {
            drawCollagePlaceholder(canvas, innerBounds, palette)
            return
        }

        val path = Path().apply { addRoundRect(innerBounds, 34f, 34f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(path)
        val tileBounds = collageTiles(innerBounds, selectedPhotos.size)
        selectedPhotos.zip(tileBounds).forEach { (photo, tile) ->
            val loaded = decodePhoto(context, photo.imagePath, tile.width().toInt(), tile.height().toInt())
            if (loaded == null) {
                drawMissingTile(canvas, tile, palette)
            } else {
                try {
                    drawBitmapCenterCrop(canvas, loaded, tile)
                } finally {
                    loaded.recycle()
                }
            }
        }
        canvas.restore()
    }

    private fun collageTiles(bounds: RectF, count: Int): List<RectF> {
        val halfWidth = (bounds.width() - TILE_GAP) / 2f
        val halfHeight = (bounds.height() - TILE_GAP) / 2f
        return when (count) {
            1 -> listOf(RectF(bounds))
            2 -> listOf(
                RectF(bounds.left, bounds.top, bounds.left + halfWidth, bounds.bottom),
                RectF(bounds.left + halfWidth + TILE_GAP, bounds.top, bounds.right, bounds.bottom)
            )
            3 -> listOf(
                RectF(bounds.left, bounds.top, bounds.left + halfWidth, bounds.bottom),
                RectF(bounds.left + halfWidth + TILE_GAP, bounds.top, bounds.right, bounds.top + halfHeight),
                RectF(bounds.left + halfWidth + TILE_GAP, bounds.top + halfHeight + TILE_GAP, bounds.right, bounds.bottom)
            )
            else -> listOf(
                RectF(bounds.left, bounds.top, bounds.left + halfWidth, bounds.top + halfHeight),
                RectF(bounds.left + halfWidth + TILE_GAP, bounds.top, bounds.right, bounds.top + halfHeight),
                RectF(bounds.left, bounds.top + halfHeight + TILE_GAP, bounds.left + halfWidth, bounds.bottom),
                RectF(bounds.left + halfWidth + TILE_GAP, bounds.top + halfHeight + TILE_GAP, bounds.right, bounds.bottom)
            )
        }
    }

    private fun drawMissingTile(canvas: Canvas, bounds: RectF, palette: Palette) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.foreground
            alpha = 24
        }
        canvas.drawRect(bounds, paint)
        paint.color = palette.accent
        paint.alpha = 110
        canvas.drawCircle(bounds.centerX(), bounds.centerY(), bounds.width().coerceAtMost(bounds.height()) * .13f, paint)
    }

    private fun drawCollagePlaceholder(canvas: Canvas, bounds: RectF, palette: Palette) {
        val backdrop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.foreground
            alpha = 22
        }
        canvas.drawRoundRect(bounds, 34f, 34f, backdrop)
        val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accent
            alpha = 175
        }
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        canvas.drawCircle(centerX, centerY, bounds.height() * .12f, mark)
        mark.style = Paint.Style.STROKE
        mark.strokeWidth = 7f
        canvas.drawRoundRect(
            RectF(centerX - bounds.width() * .16f, centerY - bounds.height() * .19f, centerX + bounds.width() * .16f, centerY + bounds.height() * .19f),
            24f,
            24f,
            mark
        )
    }

    /** Decodes no more pixels than a collage tile needs and never lets a broken file abort sharing. */
    private fun decodePhoto(context: Context, imagePath: String, requestedWidth: Int, requestedHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openImageStream(context, imagePath) { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val targetDimension = maxOf(requestedWidth, requestedHeight).coerceIn(320, 960)
        val sample = calculateSampleSize(bounds.outWidth, bounds.outHeight, targetDimension)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = openImageStream(context, imagePath) { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return null
        return decoded.rotateForExif(readOrientation(context, imagePath))
    }

    private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        val largestDimension = maxOf(width, height)
        while (largestDimension / sample > maxDimension && sample < 32) sample *= 2
        return sample
    }

    private fun readOrientation(context: Context, imagePath: String): Int = runCatching {
        val orientation = if (imagePath.startsWith("content://")) {
            openImageStream(context, imagePath) { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        } else {
            ExifInterface(imagePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }
        orientation ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun Bitmap.rotateForExif(orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return this
        val rotated = Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(degrees) }, true)
        if (rotated !== this) recycle()
        return rotated
    }

    private inline fun <T> openImageStream(context: Context, imagePath: String, block: (InputStream) -> T): T? =
        runCatching {
            val stream = if (imagePath.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(imagePath))
            } else {
                File(imagePath).takeIf(File::exists)?.inputStream()
            } ?: return null
            stream.use(block)
        }.getOrNull()

    private fun drawBitmapCenterCrop(canvas: Canvas, bitmap: Bitmap, destination: RectF) {
        val sourceWidth = bitmap.width.toFloat()
        val sourceHeight = bitmap.height.toFloat()
        val scale = maxOf(destination.width() / sourceWidth, destination.height() / sourceHeight)
        val scaledWidth = sourceWidth * scale
        val scaledHeight = sourceHeight * scale
        val drawBounds = RectF(
            destination.centerX() - scaledWidth / 2f,
            destination.centerY() - scaledHeight / 2f,
            destination.centerX() + scaledWidth / 2f,
            destination.centerY() + scaledHeight / 2f
        )
        canvas.drawBitmap(bitmap, null, drawBounds, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun drawTravelBadge(
        canvas: Canvas,
        text: String,
        palette: Palette,
        left: Float,
        top: Float
    ) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.foreground
            textSize = 27f
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
            )
        }
        val paddingX = 24f
        val height = 58f
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accent
            alpha = 48
        }
        val width = textPaint.measureText(text) + (paddingX * 2)
        canvas.drawRoundRect(RectF(left, top, left + width, top + height), height / 2, height / 2, badgePaint)
        canvas.drawText(text, left + paddingX, top + 38f, textPaint)
    }

    private fun drawSocialFooter(
        context: Context,
        canvas: Canvas,
        mood: StoryMood,
        createdAt: Long,
        palette: Palette,
        left: Float,
        baseline: Float
    ) {
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.foreground
            alpha = 178
            textSize = 25f
            letterSpacing = .05f
        }
        val date = DateFormat.getMediumDateFormat(context).format(Date(createdAt))
        canvas.drawText("${mood.displayName.uppercase()}  •  $date", left, baseline, footerPaint)
        canvas.drawText("MADE WITH PLUCK", left, baseline + 38f, footerPaint)
    }

    private fun cacheOutputFile(context: Context, name: String): File {
        val directory = File(context.cacheDir, "story_shares").apply { mkdirs() }
        return File(directory, name)
    }

    private fun writeCard(
        context: Context,
        fileName: String,
        title: String,
        body: String,
        mood: StoryMood,
        createdAt: Long,
        pageLabel: String
    ): Uri {
        val bitmap = Bitmap.createBitmap(PORTRAIT_WIDTH, PORTRAIT_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val palette = mood.palette()
        canvas.drawColor(palette.background)

        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent }
        canvas.drawRoundRect(
            RectF(HORIZONTAL_PADDING.toFloat(), 84f, (PORTRAIT_WIDTH - HORIZONTAL_PADDING).toFloat(), 96f),
            6f,
            6f,
            accentPaint
        )

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.foreground
            textSize = 28f
            letterSpacing = 0.14f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        canvas.drawText(pageLabel, HORIZONTAL_PADDING.toFloat(), 154f, labelPaint)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.foreground
            textSize = 64f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        drawTextBlock(canvas, title, titlePaint, 212, PORTRAIT_WIDTH - (HORIZONTAL_PADDING * 2), maxLines = 4)

        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.foreground
            textSize = 38f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        }
        drawTextBlock(canvas, body, bodyPaint, 566, PORTRAIT_WIDTH - (HORIZONTAL_PADDING * 2), maxLines = 13)

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.foreground
            alpha = 190
            textSize = 26f
        }
        val date = DateFormat.getMediumDateFormat(context).format(Date(createdAt))
        canvas.drawText("${mood.displayName.uppercase()}  •  $date", HORIZONTAL_PADDING.toFloat(), (PORTRAIT_HEIGHT - 104).toFloat(), footerPaint)
        canvas.drawText("Made with Pluck", HORIZONTAL_PADDING.toFloat(), (PORTRAIT_HEIGHT - 62).toFloat(), footerPaint)

        val output = cacheOutputFile(context, fileName)
        try {
            FileOutputStream(output).use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
        } finally {
            bitmap.recycle()
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", output)
    }

    private fun drawTextBlock(
        canvas: Canvas,
        value: String,
        paint: TextPaint,
        top: Int,
        width: Int,
        maxLines: Int
    ): Int = drawTextBlock(
        canvas = canvas,
        value = value,
        paint = paint,
        left = HORIZONTAL_PADDING,
        top = top,
        width = width,
        maxLines = maxLines,
        lineSpacing = 10f
    )

    private fun drawTextBlock(
        canvas: Canvas,
        value: String,
        paint: TextPaint,
        left: Int,
        top: Int,
        width: Int,
        maxLines: Int,
        lineSpacing: Float
    ): Int {
        val text = value.trim().ifBlank { "A story from one real day." }
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(lineSpacing, 1f)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(left.toFloat(), top.toFloat())
        layout.draw(canvas)
        canvas.restore()
        return layout.height
    }

    private fun String.quoteExcerpt(): String =
        trim().split(Regex("\\n\\s*\\n")).firstOrNull().orEmpty().take(460).ifBlank { trim().take(460) }

    private fun String.cardPages(maxPages: Int): List<String> {
        val words = trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.isEmpty()) return listOf("Your story is ready in Pluck.")
        val pageSize = 105
        return words.chunked(pageSize).take(maxPages).map { it.joinToString(" ") }
    }

    /** Counts places only. This intentionally never reads [JourneyPhoto.latitude] or longitude. */
    private fun List<JourneyPhoto>.travelBadge(): String = when (size) {
        0 -> "PRIVATE JOURNEY"
        1 -> "1 PLACE  •  PRIVATE JOURNEY"
        else -> "$size PLACES  •  PRIVATE JOURNEY"
    }

    /** Chooses evenly spaced photos while preserving the caller's explicit journey order. */
    private fun List<JourneyPhoto>.selectForCollage(): List<JourneyPhoto> {
        if (size <= COLLAGE_MAX_PHOTOS) return this
        return List(COLLAGE_MAX_PHOTOS) { index ->
            this[(index * (size - 1)) / (COLLAGE_MAX_PHOTOS - 1)]
        }
    }

    private data class SocialCardLayout(
        val collageBounds: RectF,
        val textBounds: RectF,
        val badgeLeft: Float,
        val badgeTop: Float,
        val footerLeft: Float,
        val footerBaseline: Float,
        val titleSize: Float,
        val bodySize: Float,
        val titleLines: Int,
        val bodyLines: Int,
        val titleLineSpacing: Float,
        val bodyLineSpacing: Float,
        val titleToBodyGap: Float
    )

    private data class Palette(val background: Int, val foreground: Int, val accent: Int)

    private fun StoryMood.palette(): Palette = when (this) {
        StoryMood.CINEMATIC -> Palette(Color.rgb(30, 47, 68), Color.WHITE, Color.rgb(152, 206, 255))
        StoryMood.MYSTERIOUS -> Palette(Color.rgb(47, 37, 71), Color.WHITE, Color.rgb(211, 180, 255))
        StoryMood.WHIMSICAL -> Palette(Color.rgb(53, 78, 68), Color.WHITE, Color.rgb(170, 235, 196))
        StoryMood.WARM -> Palette(Color.rgb(112, 61, 43), Color.WHITE, Color.rgb(255, 204, 150))
        StoryMood.ADVENTUROUS -> Palette(Color.rgb(45, 76, 91), Color.WHITE, Color.rgb(167, 224, 236))
        StoryMood.DARK -> Palette(Color.rgb(35, 35, 40), Color.WHITE, Color.rgb(230, 185, 115))
    }
}
