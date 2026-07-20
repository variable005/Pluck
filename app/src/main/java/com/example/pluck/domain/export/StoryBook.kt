package com.example.pluck.domain.export

import com.example.pluck.domain.model.StoryMood

/** The container type written by [com.example.pluck.data.export.StoryBookExporter]. */
data class StoryBook(
    val title: String,
    val chapters: List<StoryBookChapter>,
    val createdAt: Long,
    val subtitle: String? = null,
    val author: String = "Pluck",
    val mood: StoryMood = StoryMood.CINEMATIC,
    /** Optional local image composited into the deterministic title/mood/date cover. */
    val coverPhoto: StoryBookPhoto? = null
) {
    init {
        require(title.isNotBlank()) { "A book title is required." }
        require(chapters.isNotEmpty()) { "A book needs at least one chapter." }
    }
}

/**
 * A single saved story rendered as a chapter in a PDF book or EPUB publication.
 *
 * [photos] are optional and are deliberately referenced by app-local file path or content URI.
 * The exporter decodes and re-encodes them locally, preserving only display orientation and never
 * retaining or exporting source EXIF metadata or contacting a mapping service.
 */
data class StoryBookChapter(
    val title: String,
    val story: String,
    val date: Long,
    val photos: List<StoryBookPhoto> = emptyList(),
    val routePoints: List<StoryBookRoutePoint> = emptyList(),
    val chapterNumber: Int? = null,
    val mood: StoryMood? = null
) {
    init {
        require(title.isNotBlank()) { "A chapter title is required." }
        require(story.isNotBlank()) { "A chapter needs story text." }
    }
}

/** A local photo reference and its optional human-readable place label. */
data class StoryBookPhoto(
    /** A private file path, `file://` URI, or app-granted `content://` URI. */
    val source: String,
    val caption: String? = null,
    val address: String? = null,
    val capturedAt: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    init {
        require(source.isNotBlank()) { "A photo source is required." }
    }
}

/** A location used solely to draw Pluck's private normalized route diagram. */
data class StoryBookRoutePoint(
    val latitude: Double,
    val longitude: Double,
    val label: String? = null
)

/** The two offline, shareable book formats Pluck can create. */
enum class BookExportFormat(val mimeType: String, val fileExtension: String) {
    PDF("application/pdf", "pdf"),
    EPUB("application/epub+zip", "epub")
}

/** Coarse-grained export stages for UI progress and accessibility announcements. */
enum class BookExportStatus {
    PREPARING,
    RENDERING_COVER,
    RENDERING_CHAPTER,
    WRITING,
    COMPLETED,
    FAILED
}

/** A progress event emitted by [com.example.pluck.data.export.StoryBookExporter]. */
data class BookExportProgress(
    val status: BookExportStatus,
    val completedSteps: Int,
    val totalSteps: Int,
    val message: String
) {
    val fraction: Float
        get() = if (totalSteps == 0) 0f else completedSteps.toFloat() / totalSteps.toFloat()
}

/** Result returned instead of exposing exporter implementation exceptions to the UI. */
sealed interface BookExportResult {
    data class Success(
        val format: BookExportFormat,
        val chapterCount: Int,
        val bytesWritten: Long? = null
    ) : BookExportResult

    data class Failure(
        val format: BookExportFormat,
        val message: String,
        val cause: Throwable? = null
    ) : BookExportResult
}
