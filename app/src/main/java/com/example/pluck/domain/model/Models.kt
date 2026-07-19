package com.example.pluck.domain.model

enum class AiProvider(val displayName: String) {
    GEMINI("Gemini"), OPENAI("OpenAI"), CLAUDE("Claude"), GROQ("Groq"),
    TOGETHER("Together AI"), OPENROUTER("OpenRouter"), LOCAL_GEMMA("Local Gemma (Experimental)");

    val requiresApiKey: Boolean get() = this != LOCAL_GEMMA
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
    val createdAt: Long
)

data class StoryGenerationInput(
    val photos: List<JourneyPhoto>,
    val locale: String,
    val genre: String? = null
)

data class GeneratedStory(val title: String, val content: String)

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
    val publisher: String = "Google AI Edge · LiteRT Community",
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
