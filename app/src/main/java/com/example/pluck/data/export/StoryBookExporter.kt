package com.example.pluck.data.export

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.exifinterface.media.ExifInterface
import com.example.pluck.domain.export.BookExportFormat
import com.example.pluck.domain.export.BookExportProgress
import com.example.pluck.domain.export.BookExportResult
import com.example.pluck.domain.export.BookExportStatus
import com.example.pluck.domain.export.StoryBook
import com.example.pluck.domain.export.StoryBookChapter
import com.example.pluck.domain.export.StoryBookPhoto
import com.example.pluck.domain.export.StoryBookRoutePoint
import com.example.pluck.domain.model.StoryMood
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

/**
 * Creates private, self-contained PDF and EPUB3 story books.
 *
 * The exporter intentionally has no network dependency. It only opens the photo references supplied
 * in [StoryBook], normalizes route coordinates locally, and embeds decoded JPEG/PNG bytes. Decoding
 * then re-encoding every image strips source EXIF metadata before it reaches an EPUB; PDFs receive
 * pixels rather than the source image file for the same reason.
 *
 * The class is injectable as a singleton, but does not retain a [Context], output stream, bitmap, or
 * story data after an export completes.
 */
@Singleton
class StoryBookExporter @Inject constructor() {

    /**
     * Exports [book] into a Storage Access Framework destination. The URI must have writable access.
     *
     * The method runs all I/O off the main thread. [onProgress] is invoked from that background
     * dispatcher, so UI callers should update a thread-safe state holder or switch dispatchers.
     */
    suspend fun exportToUri(
        context: Context,
        book: StoryBook,
        format: BookExportFormat,
        destination: Uri,
        onProgress: ((BookExportProgress) -> Unit)? = null
    ): BookExportResult = withContext(Dispatchers.IO) {
        val output = try {
            context.contentResolver.openOutputStream(destination, "w")
        } catch (error: Throwable) {
            return@withContext BookExportResult.Failure(
                format = format,
                message = "Pluck could not open the selected destination.",
                cause = error
            )
        } ?: return@withContext BookExportResult.Failure(
            format = format,
            message = "The selected destination cannot be written."
        )

        output.use {
            exportToStream(
                resolver = context.contentResolver,
                book = book,
                format = format,
                output = it,
                onProgress = onProgress
            )
        }
    }

    /**
     * Writes [book] to an already-open [output] without closing it.
     *
     * Use this for tests, a private cache file, or a caller-managed stream. The source photos are
     * resolved through [resolver], allowing app-private paths and granted content URIs.
     */
    suspend fun exportToStream(
        resolver: ContentResolver,
        book: StoryBook,
        format: BookExportFormat,
        output: OutputStream,
        onProgress: ((BookExportProgress) -> Unit)? = null
    ): BookExportResult = withContext(Dispatchers.IO) {
        val counted = CountingOutputStream(output)
        val totalSteps = book.chapters.size + 2
        fun progress(status: BookExportStatus, step: Int, message: String) {
            onProgress?.invoke(BookExportProgress(status, step, totalSteps, message))
        }

        try {
            coroutineContext.ensureActive()
            progress(BookExportStatus.PREPARING, 0, "Preparing your private story book")
            when (format) {
                BookExportFormat.PDF -> writePdf(book, resolver, counted, ::progress)
                BookExportFormat.EPUB -> writeEpub(book, resolver, counted, ::progress)
            }
            counted.flush()
            progress(BookExportStatus.COMPLETED, totalSteps, "Your ${format.name} book is ready")
            BookExportResult.Success(format, book.chapters.size, counted.byteCount)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            BookExportResult.Failure(
                format = format,
                message = "Pluck could not create this ${format.name} book. Please try again.",
                cause = error
            ).also {
                progress(BookExportStatus.FAILED, 0, "Book export could not be completed")
            }
        }
    }

    private suspend fun writePdf(
        book: StoryBook,
        resolver: ContentResolver,
        output: OutputStream,
        progress: (BookExportStatus, Int, String) -> Unit
    ) {
        val document = PdfDocument()
        try {
            val renderer = PdfBookRenderer(document, resolver, book)
            progress(BookExportStatus.RENDERING_COVER, 1, "Designing your cover")
            renderer.drawCover()
            book.chapters.forEachIndexed { index, chapter ->
                coroutineContext.ensureActive()
                progress(
                    BookExportStatus.RENDERING_CHAPTER,
                    index + 2,
                    "Writing chapter ${index + 1} of ${book.chapters.size}"
                )
                renderer.drawChapter(chapter)
            }
            progress(BookExportStatus.WRITING, book.chapters.size + 1, "Saving your PDF book")
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    private suspend fun writeEpub(
        book: StoryBook,
        resolver: ContentResolver,
        output: OutputStream,
        progress: (BookExportStatus, Int, String) -> Unit
    ) {
        val zip = ZipOutputStream(NonClosingOutputStream(output))
        try {
            writeStoredEntry(zip, "mimetype", "application/epub+zip".toByteArray(StandardCharsets.US_ASCII))
            writeUtf8Entry(zip, "META-INF/container.xml", EPUB_CONTAINER_XML)

            progress(BookExportStatus.RENDERING_COVER, 1, "Designing your cover")
            val coverPhoto = book.coverPhoto?.let { photo ->
                loadBitmap(resolver, photo.source, EPUB_IMAGE_MAX_DIMENSION)
            }
            val cover = createCoverBitmap(
                title = book.title,
                subtitle = book.subtitle,
                mood = book.mood,
                createdAt = book.createdAt,
                width = 1200,
                height = 1600,
                coverPhoto = coverPhoto
            )
            try {
                writeBinaryEntry(zip, "OEBPS/images/cover.png", cover.toPngBytes())
            } finally {
                cover.recycle()
                coverPhoto?.recycle()
            }

            val chapterAssets = mutableListOf<EpubChapterAssets>()
            book.chapters.forEachIndexed { chapterIndex, chapter ->
                coroutineContext.ensureActive()
                progress(
                    BookExportStatus.RENDERING_CHAPTER,
                    chapterIndex + 2,
                    "Illustrating chapter ${chapterIndex + 1} of ${book.chapters.size}"
                )
                val assets = writeChapterAssets(zip, resolver, chapter, chapterIndex)
                chapterAssets += assets
                writeUtf8Entry(
                    zip,
                    "OEBPS/text/chapter-${chapterIndex + 1}.xhtml",
                    chapterXhtml(book, chapter, chapterIndex, assets)
                )
            }

            progress(BookExportStatus.WRITING, book.chapters.size + 1, "Packing your EPUB book")
            writeUtf8Entry(zip, "OEBPS/text/cover.xhtml", coverXhtml(book))
            writeUtf8Entry(zip, "OEBPS/nav.xhtml", navigationXhtml(book))
            writeUtf8Entry(zip, "OEBPS/styles/book.css", EPUB_STYLESHEET)
            writeUtf8Entry(zip, "OEBPS/content.opf", packageDocument(book, chapterAssets))
            zip.finish()
        } finally {
            zip.close()
        }
    }

    private fun writeChapterAssets(
        zip: ZipOutputStream,
        resolver: ContentResolver,
        chapter: StoryBookChapter,
        chapterIndex: Int
    ): EpubChapterAssets {
        val images = buildList {
            chapter.photos.take(MAX_EPUB_PHOTOS_PER_CHAPTER).forEachIndexed { photoIndex, photo ->
                val bitmap = loadBitmap(resolver, photo.source, EPUB_IMAGE_MAX_DIMENSION) ?: return@forEachIndexed
                try {
                    val href = "images/chapter-${chapterIndex + 1}-photo-${photoIndex + 1}.jpg"
                    writeBinaryEntry(zip, "OEBPS/$href", bitmap.toJpegBytes())
                    add(EpubImage(href, photo.displayCaption()))
                } finally {
                    bitmap.recycle()
                }
            }
        }

        val route = chapter.routePointsOrPhotoLocations()
        val routeHref = if (route.size >= 2) {
            val diagram = createRouteBitmap(route, chapter.mood ?: StoryMood.CINEMATIC)
            try {
                val href = "images/chapter-${chapterIndex + 1}-route.png"
                writeBinaryEntry(zip, "OEBPS/$href", diagram.toPngBytes())
                href
            } finally {
                diagram.recycle()
            }
        } else {
            null
        }
        return EpubChapterAssets(images = images, routeHref = routeHref)
    }

    /** An in-memory page renderer so the PDF stays free of source image metadata. */
    private inner class PdfBookRenderer(
        private val document: PdfDocument,
        private val resolver: ContentResolver,
        private val book: StoryBook
    ) {
        private var pageNumber = 0
        private var activePage: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = CONTENT_TOP

        fun drawCover() {
            beginPage(chapterTitle = null, chapterDate = null, continuation = false, cover = true)
            val pageCanvas = requireNotNull(canvas)
            val coverPhoto = book.coverPhoto?.let { photo ->
                loadBitmap(resolver, photo.source, PDF_IMAGE_MAX_DIMENSION)
            }
            val cover = createCoverBitmap(
                title = book.title,
                subtitle = book.subtitle,
                mood = book.mood,
                createdAt = book.createdAt,
                width = PAGE_WIDTH,
                height = PAGE_HEIGHT,
                coverPhoto = coverPhoto
            )
            try {
                pageCanvas.drawBitmap(cover, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            } finally {
                cover.recycle()
                coverPhoto?.recycle()
            }
            finishPage(includeFooter = false)
        }

        fun drawChapter(chapter: StoryBookChapter) {
            beginPage(chapter.title, chapter.date, continuation = false)
            val pageCanvas = requireNotNull(canvas)
            val palette = (chapter.mood ?: book.mood).palette()

            val heroPhotos = chapter.photos.take(PDF_HEADER_PHOTO_LIMIT)
            if (heroPhotos.isNotEmpty()) {
                drawPhotoBand(pageCanvas, heroPhotos)
            }

            val route = chapter.routePointsOrPhotoLocations()
            if (route.size >= 2) {
                drawRouteCard(pageCanvas, route, palette)
            }

            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = INK
                textSize = BODY_TEXT_SIZE
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            }
            val paragraphs = chapter.story.normalizedParagraphs()
            paragraphs.forEach { paragraph ->
                drawParagraph(paragraph, bodyPaint, chapter)
            }
            finishPage()
        }

        private fun drawPhotoBand(canvas: Canvas, photos: List<StoryBookPhoto>) {
            val cardTop = y
            val gap = 10f
            val totalWidth = CONTENT_RIGHT - CONTENT_LEFT
            val imageWidth = (totalWidth - (gap * (photos.size - 1))) / photos.size
            photos.forEachIndexed { index, photo ->
                val rect = RectF(
                    CONTENT_LEFT + index * (imageWidth + gap),
                    cardTop,
                    CONTENT_LEFT + index * (imageWidth + gap) + imageWidth,
                    cardTop + PDF_PHOTO_HEIGHT
                )
                val bitmap = loadBitmap(resolver, photo.source, PDF_IMAGE_MAX_DIMENSION)
                if (bitmap != null) {
                    try {
                        drawBitmapCover(canvas, bitmap, rect)
                    } finally {
                        bitmap.recycle()
                    }
                } else {
                    drawPhotoPlaceholder(canvas, rect)
                }
            }
            y = cardTop + PDF_PHOTO_HEIGHT + 18f
        }

        private fun drawRouteCard(canvas: Canvas, points: List<StoryBookRoutePoint>, palette: BookPalette) {
            val card = RectF(CONTENT_LEFT, y, CONTENT_RIGHT, y + PDF_ROUTE_HEIGHT)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.soft }
            canvas.drawRoundRect(card, 18f, 18f, paint)
            val diagram = createRouteBitmap(points, palette.mood, width = 560, height = 190)
            try {
                val imageRect = RectF(card.left + 16f, card.top + 12f, card.right - 16f, card.bottom - 12f)
                drawBitmapContain(canvas, diagram, imageRect)
            } finally {
                diagram.recycle()
            }
            y = card.bottom + 20f
        }

        private fun drawParagraph(text: String, paint: TextPaint, chapter: StoryBookChapter) {
            val layout = StaticLayout.Builder
                .obtain(text, 0, text.length, paint, (CONTENT_RIGHT - CONTENT_LEFT).toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setLineSpacing(PARAGRAPH_LEADING, 1f)
                .build()
            var line = 0
            while (line < layout.lineCount) {
                var available = PAGE_BOTTOM - y
                if (available < paint.textSize * 1.8f) {
                    beginPage(chapter.title, chapter.date, continuation = true)
                    available = PAGE_BOTTOM - y
                }
                val sourceTop = layout.getLineTop(line).toFloat()
                var endExclusive = line
                while (endExclusive < layout.lineCount &&
                    layout.getLineBottom(endExclusive) - sourceTop <= available
                ) {
                    endExclusive++
                }
                if (endExclusive == line) {
                    beginPage(chapter.title, chapter.date, continuation = true)
                    continue
                }
                val height = layout.getLineBottom(endExclusive - 1) - sourceTop
                val pageCanvas = requireNotNull(canvas)
                pageCanvas.save()
                pageCanvas.clipRect(CONTENT_LEFT, y, CONTENT_RIGHT, y + height)
                pageCanvas.translate(CONTENT_LEFT, y - sourceTop)
                layout.draw(pageCanvas)
                pageCanvas.restore()
                y += height
                line = endExclusive
                if (line < layout.lineCount) beginPage(chapter.title, chapter.date, continuation = true)
            }
            y += PARAGRAPH_GAP
        }

        private fun beginPage(
            chapterTitle: String?,
            chapterDate: Long?,
            continuation: Boolean,
            cover: Boolean = false
        ) {
            finishPage()
            pageNumber++
            activePage = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            )
            canvas = requireNotNull(activePage).canvas
            val pageCanvas = requireNotNull(canvas)
            pageCanvas.drawColor(PAPER)
            if (cover) return

            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED_INK }
            headerPaint.textSize = 10f
            headerPaint.letterSpacing = 0.16f
            pageCanvas.drawText(
                if (continuation) "PLUCK · CONTINUED" else "PLUCK · PRIVATE TRAVELOGUE",
                CONTENT_LEFT,
                38f,
                headerPaint
            )
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RULE }
            pageCanvas.drawRect(CONTENT_LEFT, 48f, CONTENT_RIGHT, 49f, linePaint)

            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = INK
                textSize = if (continuation) 16f else 24f
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            }
            val heading = if (continuation) chapterTitle.orEmpty() else chapterTitle.orEmpty()
            val titleLayout = StaticLayout.Builder
                .obtain(heading, 0, heading.length, titlePaint, (CONTENT_RIGHT - CONTENT_LEFT).toInt())
                .setIncludePad(false)
                .setMaxLines(if (continuation) 1 else 2)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .build()
            pageCanvas.save()
            pageCanvas.translate(CONTENT_LEFT, 68f)
            titleLayout.draw(pageCanvas)
            pageCanvas.restore()

            val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = MUTED_INK
                textSize = 11f
            }
            val dateText = chapterDate?.let(::formatDate).orEmpty()
            pageCanvas.drawText(dateText, CONTENT_LEFT, 124f, datePaint)
            y = if (continuation) CONTINUATION_CONTENT_TOP else CONTENT_TOP
        }

        private fun finishPage(includeFooter: Boolean = true) {
            val page = activePage ?: return
            val pageCanvas = requireNotNull(canvas)
            if (includeFooter) {
                val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = MUTED_INK
                    textSize = 10f
                }
                pageCanvas.drawText("Made privately with Pluck", CONTENT_LEFT, PAGE_HEIGHT - 28f, footerPaint)
                val pageLabel = pageNumber.toString()
                pageCanvas.drawText(
                    pageLabel,
                    CONTENT_RIGHT - footerPaint.measureText(pageLabel),
                    PAGE_HEIGHT - 28f,
                    footerPaint
                )
            }
            document.finishPage(page)
            activePage = null
            canvas = null
        }
    }

    private fun coverXhtml(book: StoryBook): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <!DOCTYPE html>
        <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
        <head><title>${book.title.xmlEscape()}</title><link rel="stylesheet" type="text/css" href="../styles/book.css" /></head>
        <body class="cover"><section epub:type="cover"><img src="../images/cover.png" alt="Cover for ${book.title.xmlEscape()}" /></section></body>
        </html>
    """.trimIndent()

    private fun navigationXhtml(book: StoryBook): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <!DOCTYPE html>
        <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
        <head><title>Contents</title><link rel="stylesheet" type="text/css" href="styles/book.css" /></head>
        <body><nav epub:type="toc" id="toc"><h1>${book.title.xmlEscape()}</h1><ol>
        ${book.chapters.mapIndexed { index, chapter -> "<li><a href=\"text/chapter-${index + 1}.xhtml\">${chapter.title.xmlEscape()}</a></li>" }.joinToString("\n")}
        </ol></nav></body>
        </html>
    """.trimIndent()

    private fun chapterXhtml(
        book: StoryBook,
        chapter: StoryBookChapter,
        chapterIndex: Int,
        assets: EpubChapterAssets
    ): String {
        val chapterMood = chapter.mood ?: book.mood
        val figures = buildString {
            assets.images.forEach { image ->
                append("<figure><img src=\"../${image.href.xmlEscape()}\" alt=\"${image.caption.xmlEscape()}\" />")
                if (image.caption.isNotBlank()) append("<figcaption>${image.caption.xmlEscape()}</figcaption>")
                append("</figure>")
            }
            assets.routeHref?.let { href ->
                append("<figure class=\"route\"><img src=\"../${href.xmlEscape()}\" alt=\"Private normalized route diagram\" /><figcaption>Private route sketch</figcaption></figure>")
            }
        }
        val paragraphs = chapter.story.normalizedParagraphs().joinToString("\n") { paragraph ->
            "<p>${paragraph.xmlEscape()}</p>"
        }
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
            <head><title>${chapter.title.xmlEscape()}</title><link rel="stylesheet" type="text/css" href="../styles/book.css" /></head>
            <body class="mood-${chapterMood.name.lowercase(Locale.US)}">
            <article epub:type="chapter">
              <header><p class="eyebrow">CHAPTER ${chapter.chapterNumber ?: chapterIndex + 1} · ${formatDate(chapter.date).xmlEscape()}</p><h1>${chapter.title.xmlEscape()}</h1></header>
              $figures
              $paragraphs
            </article>
            </body>
            </html>
        """.trimIndent()
    }

    private fun packageDocument(book: StoryBook, chapterAssets: List<EpubChapterAssets>): String {
        val identifier = book.epubIdentifier()
        val imageManifest = buildString {
            append("<item id=\"cover-image\" href=\"images/cover.png\" media-type=\"image/png\" properties=\"cover-image\" />")
            chapterAssets.forEachIndexed { chapterIndex, assets ->
                assets.images.forEachIndexed { imageIndex, image ->
                    append("<item id=\"chapter-${chapterIndex + 1}-image-${imageIndex + 1}\" href=\"${image.href.xmlEscape()}\" media-type=\"image/jpeg\" />")
                }
                assets.routeHref?.let { href ->
                    append("<item id=\"chapter-${chapterIndex + 1}-route\" href=\"${href.xmlEscape()}\" media-type=\"image/png\" />")
                }
            }
        }
        val chapterManifest = book.chapters.indices.joinToString("\n") { index ->
            "<item id=\"chapter-${index + 1}\" href=\"text/chapter-${index + 1}.xhtml\" media-type=\"application/xhtml+xml\" />"
        }
        val spine = book.chapters.indices.joinToString("\n") { index ->
            "<itemref idref=\"chapter-${index + 1}\" />"
        }
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id" xml:lang="en" prefix="dcterms: http://purl.org/dc/terms/">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="book-id">$identifier</dc:identifier>
                <dc:title>${book.title.xmlEscape()}</dc:title>
                <dc:creator>${book.author.xmlEscape()}</dc:creator>
                <dc:language>en</dc:language>
                <meta property="dcterms:modified">${formatEpubModified(book.createdAt)}</meta>
              </metadata>
              <manifest>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav" />
                <item id="cover" href="text/cover.xhtml" media-type="application/xhtml+xml" />
                <item id="css" href="styles/book.css" media-type="text/css" />
                $imageManifest
                $chapterManifest
              </manifest>
              <spine>
                <itemref idref="cover" linear="no" />
                $spine
              </spine>
            </package>
        """.trimIndent()
    }

    private fun createCoverBitmap(
        title: String,
        subtitle: String?,
        mood: StoryMood,
        createdAt: Long,
        width: Int,
        height: Int,
        coverPhoto: Bitmap? = null
    ): Bitmap {
        val palette = mood.palette()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.coverBackground)

        val margin = width * 0.105f
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent }
        val fineAccent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent.copyAlpha(110) }
        canvas.drawRoundRect(margin, height * 0.12f, width - margin, height * 0.13f, 8f, 8f, accent)

        // These tonal circles are deterministic from title/date and are deliberately not a gradient.
        val seed = (title.hashCode().toLong() xor createdAt).absolutePositive()
        val firstX = width * (0.18f + ((seed % 29) / 100f))
        val secondX = width * (0.60f + (((seed / 29) % 18) / 100f))
        val firstY = height * (0.73f + (((seed / 841) % 9) / 100f))
        canvas.drawCircle(firstX, firstY, width * 0.19f, fineAccent)
        canvas.drawCircle(secondX, height * 0.79f, width * 0.28f, fineAccent)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.coverForeground.copyAlpha(205)
            textSize = width * 0.032f
            letterSpacing = 0.18f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }
        canvas.drawText("A PLUCK STORY BOOK", margin, height * 0.20f, labelPaint)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.coverForeground
            textSize = width * 0.092f
            typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD)
        }
        drawTextBlock(
            canvas = canvas,
            value = title,
            paint = titlePaint,
            left = margin,
            top = height * 0.28f,
            width = width - (margin * 2),
            maxLines = 5,
            lineSpacing = width * 0.012f
        )
        subtitle?.takeIf { it.isNotBlank() }?.let {
            val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.coverForeground.copyAlpha(210)
                textSize = width * 0.038f
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            }
            drawTextBlock(canvas, it, subtitlePaint, margin, height * 0.61f, width - margin * 2, 3, 8f)
        }
        coverPhoto?.let { photo ->
            val photoBounds = RectF(
                margin,
                height * 0.67f,
                width - margin,
                height * 0.84f
            )
            drawBitmapCover(canvas, photo, photoBounds)
        }
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.coverForeground.copyAlpha(190)
            textSize = width * 0.029f
            letterSpacing = 0.09f
        }
        canvas.drawText(formatDate(createdAt).uppercase(Locale.getDefault()), margin, height * 0.90f, datePaint)
        canvas.drawText(mood.displayName.uppercase(Locale.getDefault()), margin, height * 0.935f, datePaint)
        return bitmap
    }

    /** Draws a location-normalized route with no tiles, addresses, or network requests. */
    private fun createRouteBitmap(
        rawPoints: List<StoryBookRoutePoint>,
        mood: StoryMood,
        width: Int = 1200,
        height: Int = 420
    ): Bitmap {
        val palette = mood.palette()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.routeBackground)
        val points = rawPoints.filter { it.isValidLocation() }
        if (points.size < 2) return bitmap

        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        val latRange = max(maxLat - minLat, ROUTE_MIN_RANGE)
        val lonRange = max(maxLon - minLon, ROUTE_MIN_RANGE)
        val padding = min(width, height) * 0.16f
        fun x(point: StoryBookRoutePoint): Float =
            (padding + ((point.longitude - minLon) / lonRange).toFloat() * (width - padding * 2))
        fun y(point: StoryBookRoutePoint): Float =
            (height - padding - ((point.latitude - minLat) / latRange).toFloat() * (height - padding * 2))

        val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accent
            style = Paint.Style.STROKE
            strokeWidth = min(width, height) * 0.028f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val path = android.graphics.Path().apply {
            moveTo(x(points.first()), y(points.first()))
            points.drop(1).forEach { lineTo(x(it), y(it)) }
        }
        canvas.drawPath(path, routePaint)

        val pinOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.routeBackground }
        val pinInner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent }
        points.forEachIndexed { index, point ->
            val radius = if (index == 0 || index == points.lastIndex) min(width, height) * 0.046f else min(width, height) * 0.027f
            canvas.drawCircle(x(point), y(point), radius, pinOuter)
            canvas.drawCircle(x(point), y(point), radius * 0.62f, pinInner)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.routeInk
            textSize = min(width, height) * 0.07f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }
        canvas.drawText("PRIVATE ROUTE · ${points.size} PLACES", padding, height - padding * 0.35f, labelPaint)
        return bitmap
    }

    private fun loadBitmap(resolver: ContentResolver, source: String, maxDimension: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openBookInput(source)?.use { input -> BitmapFactory.decodeStream(input, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val sample = sampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            val orientation = resolver.readExifOrientation(source)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decoded = resolver.openBookInput(source)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return null
            decoded.applyExifOrientation(orientation).scaleDown(maxDimension)
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun ContentResolver.openBookInput(source: String): InputStream? {
        val parsed = Uri.parse(source)
        return when (parsed.scheme?.lowercase(Locale.US)) {
            null, "" -> File(source).takeIf(File::isFile)?.inputStream()
            ContentResolver.SCHEME_CONTENT, ContentResolver.SCHEME_FILE -> openInputStream(parsed)
            else -> null
        }
    }

    /** Reads only the orientation flag so a re-encoded, metadata-free export is still upright. */
    private fun ContentResolver.readExifOrientation(source: String): Int = try {
        openBookInput(source)?.use { input ->
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (_: Throwable) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun sampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        var nextWidth = width
        var nextHeight = height
        while (nextWidth > maxDimension * 2 || nextHeight > maxDimension * 2) {
            nextWidth /= 2
            nextHeight /= 2
            sample *= 2
        }
        return sample
    }

    private fun Bitmap.scaleDown(maxDimension: Int): Bitmap {
        val largest = max(width, height)
        if (largest <= maxDimension) return this
        val scale = maxDimension.toFloat() / largest.toFloat()
        val targetWidth = max(1, (width * scale).toInt())
        val targetHeight = max(1, (height * scale).toInt())
        val scaled = Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
        if (scaled !== this) recycle()
        return scaled
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

    private fun Bitmap.toJpegBytes(): ByteArray = ByteArrayOutputStream().use { buffer ->
        compress(Bitmap.CompressFormat.JPEG, EPUB_JPEG_QUALITY, buffer)
        buffer.toByteArray()
    }

    private fun Bitmap.toPngBytes(): ByteArray = ByteArrayOutputStream().use { buffer ->
        compress(Bitmap.CompressFormat.PNG, 100, buffer)
        buffer.toByteArray()
    }

    private fun StoryBookChapter.routePointsOrPhotoLocations(): List<StoryBookRoutePoint> =
        routePoints.filter { it.isValidLocation() }.ifEmpty {
            photos.mapNotNull { photo ->
                val latitude = photo.latitude
                val longitude = photo.longitude
                if (latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite() &&
                    latitude in -90.0..90.0 && longitude in -180.0..180.0
                ) {
                    StoryBookRoutePoint(latitude, longitude, photo.address ?: photo.caption)
                } else {
                    null
                }
            }
        }

    private fun StoryBookRoutePoint.isValidLocation(): Boolean =
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

    private fun StoryBookPhoto.displayCaption(): String =
        caption?.takeIf(String::isNotBlank) ?: address?.takeIf(String::isNotBlank) ?: "Journey photo"

    private fun String.normalizedParagraphs(): List<String> =
        trim().split(Regex("\\r?\\n\\s*\\r?\\n"))
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter(String::isNotBlank)
            .ifEmpty { listOf("Your Pluck story is ready to be remembered.") }

    private fun String.xmlEscape(): String = buildString(length) {
        forEach { character ->
            when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '\"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(character)
            }
        }
    }

    private fun StoryBook.epubIdentifier(): String {
        val material = "$title|$createdAt|${chapters.joinToString("|") { it.title + it.date }}"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "urn:pluck:$digest"
    }

    private fun formatDate(value: Long): String =
        SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(value))

    private fun formatEpubModified(value: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(Date(value))

    private fun writeStoredEntry(zip: ZipOutputStream, path: String, bytes: ByteArray) {
        val crc = CRC32().apply { update(bytes) }
        val entry = ZipEntry(path).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc.value
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun writeUtf8Entry(zip: ZipOutputStream, path: String, content: String) {
        writeBinaryEntry(zip, path, content.toByteArray(StandardCharsets.UTF_8))
    }

    private fun writeBinaryEntry(zip: ZipOutputStream, path: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun drawTextBlock(
        canvas: Canvas,
        value: String,
        paint: TextPaint,
        left: Float,
        top: Float,
        width: Float,
        maxLines: Int,
        lineSpacing: Float
    ) {
        val layout = StaticLayout.Builder
            .obtain(value, 0, value.length, paint, width.toInt())
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .setLineSpacing(lineSpacing, 1f)
            .build()
        canvas.save()
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawBitmapCover(canvas: Canvas, bitmap: Bitmap, target: RectF) {
        val scale = max(target.width() / bitmap.width, target.height() / bitmap.height)
        val drawnWidth = bitmap.width * scale
        val drawnHeight = bitmap.height * scale
        val left = target.centerX() - drawnWidth / 2f
        val top = target.centerY() - drawnHeight / 2f
        canvas.save()
        canvas.clipRect(target)
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(left, top, left + drawnWidth, top + drawnHeight),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        canvas.restore()
    }

    private fun drawBitmapContain(canvas: Canvas, bitmap: Bitmap, target: RectF) {
        val scale = min(target.width() / bitmap.width, target.height() / bitmap.height)
        val drawnWidth = bitmap.width * scale
        val drawnHeight = bitmap.height * scale
        val left = target.centerX() - drawnWidth / 2f
        val top = target.centerY() - drawnHeight / 2f
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(left, top, left + drawnWidth, top + drawnHeight),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
    }

    private fun drawPhotoPlaceholder(canvas: Canvas, target: RectF) {
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(230, 227, 220) }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(150, 145, 135)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(target, 12f, 12f, background)
        canvas.drawLine(target.left + 18f, target.bottom - 20f, target.centerX(), target.top + 30f, line)
        canvas.drawLine(target.centerX(), target.top + 30f, target.right - 16f, target.bottom - 42f, line)
    }

    private data class EpubImage(val href: String, val caption: String)

    private data class EpubChapterAssets(
        val images: List<EpubImage>,
        val routeHref: String?
    )

    private data class BookPalette(
        val mood: StoryMood,
        val coverBackground: Int,
        val coverForeground: Int,
        val accent: Int,
        val soft: Int,
        val routeBackground: Int,
        val routeInk: Int
    )

    private fun StoryMood.palette(): BookPalette = when (this) {
        StoryMood.CINEMATIC -> BookPalette(this, Color.rgb(31, 45, 61), Color.WHITE, Color.rgb(159, 210, 255), Color.rgb(226, 239, 247), Color.rgb(239, 247, 252), Color.rgb(31, 45, 61))
        StoryMood.MYSTERIOUS -> BookPalette(this, Color.rgb(51, 40, 72), Color.WHITE, Color.rgb(211, 181, 255), Color.rgb(238, 229, 250), Color.rgb(247, 241, 253), Color.rgb(51, 40, 72))
        StoryMood.WHIMSICAL -> BookPalette(this, Color.rgb(50, 77, 65), Color.WHITE, Color.rgb(171, 236, 196), Color.rgb(228, 244, 232), Color.rgb(239, 250, 242), Color.rgb(50, 77, 65))
        StoryMood.WARM -> BookPalette(this, Color.rgb(108, 60, 43), Color.WHITE, Color.rgb(255, 204, 151), Color.rgb(250, 232, 218), Color.rgb(255, 246, 239), Color.rgb(108, 60, 43))
        StoryMood.ADVENTUROUS -> BookPalette(this, Color.rgb(39, 75, 91), Color.WHITE, Color.rgb(163, 226, 238), Color.rgb(222, 240, 244), Color.rgb(238, 248, 250), Color.rgb(39, 75, 91))
        StoryMood.DARK -> BookPalette(this, Color.rgb(35, 35, 40), Color.WHITE, Color.rgb(229, 184, 115), Color.rgb(239, 232, 221), Color.rgb(248, 245, 239), Color.rgb(35, 35, 40))
    }

    private class CountingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        var byteCount: Long = 0
            private set

        override fun write(value: Int) {
            out.write(value)
            byteCount++
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            out.write(buffer, offset, length)
            byteCount += length.toLong()
        }
    }

    /** Lets ZipOutputStream finish its central directory without taking ownership of caller output. */
    private class NonClosingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        override fun close() = flush()
    }

    private fun Int.copyAlpha(alpha: Int): Int = (this and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun Long.absolutePositive(): Long = if (this == Long.MIN_VALUE) 0 else kotlin.math.abs(this)

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val CONTENT_LEFT = 52f
        const val CONTENT_RIGHT = 543f
        const val CONTENT_TOP = 148f
        const val CONTINUATION_CONTENT_TOP = 120f
        const val PAGE_BOTTOM = 794f
        const val PDF_PHOTO_HEIGHT = 148f
        const val PDF_ROUTE_HEIGHT = 138f
        const val PDF_HEADER_PHOTO_LIMIT = 2
        const val PDF_IMAGE_MAX_DIMENSION = 1400
        const val EPUB_IMAGE_MAX_DIMENSION = 1600
        const val EPUB_JPEG_QUALITY = 86
        const val MAX_EPUB_PHOTOS_PER_CHAPTER = 16
        const val BODY_TEXT_SIZE = 12.5f
        const val PARAGRAPH_LEADING = 3.8f
        const val PARAGRAPH_GAP = 13f
        const val ROUTE_MIN_RANGE = 0.0002
        // Signed ARGB literals keep these values valid Kotlin compile-time constants.
        const val PAPER = -777 // #FFFFFCF7
        const val INK = -14671840 // #FF202020
        const val MUTED_INK = -9804449 // #FF6A655F
        const val RULE = -2567479 // #FFD8D2C9

        const val EPUB_CONTAINER_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n" +
            "  <rootfiles><rootfile full-path=\"OEBPS/content.opf\" " +
            "media-type=\"application/oebps-package+xml\"/></rootfiles>\n" +
            "</container>"

        const val EPUB_STYLESHEET = """
            @namespace epub "http://www.idpf.org/2007/ops";
            html { font-size: 100%; }
            body { margin: 7%; color: #272522; background: #fffdf9; font-family: serif; line-height: 1.58; }
            article { max-width: 42em; margin: auto; }
            header { margin: 1.5em 0 2.4em; }
            h1 { font-size: 2em; line-height: 1.12; margin: 0.25em 0 0; }
            p { margin: 0 0 1.05em; text-align: start; }
            .eyebrow { color: #6d6760; font-family: sans-serif; font-size: .72em; font-weight: bold; letter-spacing: .12em; }
            figure { margin: 1.5em 0; break-inside: avoid; }
            figure img { display: block; width: 100%; max-height: 26em; object-fit: contain; border-radius: .35em; }
            figcaption { color: #6d6760; font-family: sans-serif; font-size: .78em; margin-top: .45em; }
            figure.route img { max-height: 14em; }
            body.cover { margin: 0; padding: 0; background: #1f2d3d; }
            body.cover section { height: 100vh; }
            body.cover img { display: block; width: 100%; height: 100%; object-fit: contain; }
        """
    }
}
