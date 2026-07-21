package com.example.pluck.domain.model

enum class AiProvider(val displayName: String) {
    GEMINI("Gemini"), OPENAI("OpenAI"), CLAUDE("Claude"), GROQ("Groq"),
    TOGETHER("Together AI"), OPENROUTER("OpenRouter"), LOCAL_GEMMA("Local Gemma (Experimental)");

    val requiresApiKey: Boolean get() = this != LOCAL_GEMMA
}

/** Controls how much tactile confirmation Pluck provides for direct user actions. */
enum class HapticMode(val displayName: String) {
    OFF("Off"),
    ESSENTIAL("Essential"),
    FULL("Full")
}

/** Selects whether Pluck follows the device, stays light/dark, or uses OLED-optimized black. */
enum class ThemeMode(val displayName: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
    AMOLED_BLACK("AMOLED black")
}

/** The narrative atmosphere selected before Pluck writes a story. */
enum class StoryMood(
    val displayName: String,
    val promptDirection: String
) {
    CINEMATIC("Cinematic", "cinematic, vivid, and emotionally resonant"),
    MYSTERIOUS("Mysterious", "intriguing, suspenseful, and quietly uncanny"),
    WHIMSICAL("Whimsical", "playful, imaginative, and gently surprising"),
    WARM("Warm", "tender, hopeful, and human"),
    ADVENTUROUS("Adventurous", "propulsive, daring, and full of discovery"),
    DARK("Dark", "atmospheric, tense, and shadowed without graphic content")
}

/** A one-tap revision direction; Pluck never exposes an AI chat surface. */
enum class StoryVariation(val promptDirection: String) {
    MORE_MYSTERIOUS("Make the story more mysterious, with stronger intrigue and carefully placed reveals."),
    SHORTER("Keep the same coherent premise but write a tighter version of approximately 450 to 650 words."),
    MORE_EMOTIONAL("Strengthen the characters' emotional stakes and intimate moments without becoming melodramatic.")
}

/**
 * Optional fictional direction for one story or novella arc.
 *
 * These values are writing instructions only. They never identify people or animals in a photo.
 */
data class StoryCreativeSettings(
    val genre: String? = null,
    val protagonistName: String? = null,
    val companions: List<String> = emptyList()
) {
    val isEmpty: Boolean
        get() = genre.isNullOrBlank() && protagonistName.isNullOrBlank() && companions.isEmpty()

    /** Trims and bounds free-form input before it reaches a remote or on-device model. */
    fun normalized(): StoryCreativeSettings = copy(
        genre = genre?.trim()?.take(48)?.takeIf { it.isNotBlank() },
        protagonistName = protagonistName?.trim()?.take(48)?.takeIf { it.isNotBlank() },
        companions = companions
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .take(5)
            .map { it.take(56) }
    )
}

data class Journey(val id: Long, val date: String, val timeZoneId: String)

data class JourneyPhoto(
    val id: Long,
    val journeyId: Long,
    val imagePath: String,
    val timestamp: Long,
    val latitude: Double?,
    val longitude: Double?,
    val address: String?
)

data class Story(
    val id: Long,
    val journeyId: Long,
    val title: String,
    val content: String,
    val provider: AiProvider,
    val createdAt: Long,
    val mood: StoryMood = StoryMood.CINEMATIC,
    val creativeSettings: StoryCreativeSettings = StoryCreativeSettings()
)

/** A persisted, model-supplied link between a fictional paragraph and one real captured place. */
data class StorySceneReference(
    val photoId: Long,
    val paragraphIndex: Int
)

/** A saved story plus its explicit reality-to-fiction scene provenance. */
data class StoryDetail(
    val story: Story,
    val scenes: List<StorySceneReference> = emptyList()
)

/**
 * The lightweight story information shown in the journey library.
 *
 * Story content is intentionally excluded so opening the library does not load every saved
 * story into memory. The full [Story] is observed only when the reader is opened.
 */
data class StoryPreview(
    val id: Long,
    val title: String,
    val provider: AiProvider,
    val createdAt: Long,
    val mood: StoryMood = StoryMood.CINEMATIC,
    val genre: String? = null
)

/** A journey with the summary data needed to render it in the user's library. */
data class JourneyLibraryItem(
    val journey: Journey,
    val photoCount: Int,
    val coverImagePath: String?,
    val story: StoryPreview?
)

data class StoryGenerationInput(
    val photos: List<JourneyPhoto>,
    val locale: String,
    val mood: StoryMood = StoryMood.CINEMATIC,
    val variation: StoryVariation? = null,
    val creativeSettings: StoryCreativeSettings = StoryCreativeSettings(),
    val arcContext: ArcGenerationContext? = null
)

/**
 * A compact continuity hand-off for the next chapter of a multi-day novella.
 * It is deliberately separate from prose shown in the story reader.
 */
data class ArcGenerationContext(
    val arcId: Long,
    val title: String,
    val chapterIndex: Int,
    val totalChapters: Int,
    val previousContinuity: String? = null
)

/** A multi-day sequence whose daily stories share a fictional world and cast. */
data class NovellaArc(
    val id: Long,
    val title: String,
    val startDate: String,
    val endDate: String,
    val mood: StoryMood,
    val creativeSettings: StoryCreativeSettings,
    val createdAt: Long,
    val updatedAt: Long
)

/** One daily journey's ordered chapter position in a [NovellaArc]. */
data class NovellaChapter(
    val arcId: Long,
    val journeyId: Long,
    val chapterIndex: Int,
    val storyId: Long? = null,
    val continuitySummary: String? = null,
    val isStale: Boolean = false,
    val updatedAt: Long = 0L
)

data class NovellaArcDetail(
    val arc: NovellaArc,
    val chapters: List<NovellaChapter>
)

data class GeneratedStory(
    val title: String,
    val content: String,
    val sceneReferences: List<StorySceneReference> = emptyList(),
    val continuitySummary: String? = null
)

sealed interface ConnectionResult {
    data object Connected : ConnectionResult
    data object InvalidKey : ConnectionResult
    data object NetworkError : ConnectionResult
    data class Failed(val message: String) : ConnectionResult
}

enum class LocalAiInstallStatus {
    CHECKING, NOT_INSTALLED, DOWNLOADING, PAUSED, VERIFYING, INSTALLED, CORRUPTED, FAILED
}

data class LocalAiModelState(
    val status: LocalAiInstallStatus = LocalAiInstallStatus.CHECKING,
    val modelName: String = "Gemma 4 E2B Instruct",
    val modelVersion: String = "7022fb7",
    val publisher: String = "Google AI Edge LiteRT Community · delivered via Hugging Face",
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val requiredStorageBytes: Long = 0,
    val availableStorageBytes: Long = 0,
    val verified: Boolean = false,
    val updateAvailable: Boolean = false,
    val message: String? = null
) {
    val progress: Float? get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else null
}
