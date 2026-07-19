package com.example.pluck.data.localai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.example.pluck.domain.model.AiProvider
import com.example.pluck.domain.model.ConnectionResult
import com.example.pluck.domain.model.GeneratedStory
import com.example.pluck.domain.model.LocalAiInstallStatus
import com.example.pluck.domain.model.LocalAiModelState
import com.example.pluck.domain.model.StoryGenerationInput
import com.example.pluck.domain.provider.StoryProvider
import com.example.pluck.domain.repository.LocalAiRepository
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.content
import com.google.mlkit.genai.prompt.generateContentRequest
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Integrates only with the Android system-managed AICore model channel. No model file is
 * downloaded, stored, or resolved by Pluck, so untrusted model imports are impossible.
 */
@Singleton
class AicoreLocalAiRepository @Inject constructor() : LocalAiRepository {
    private val _modelState = MutableStateFlow(LocalAiModelState())
    override val modelState: StateFlow<LocalAiModelState> = _modelState.asStateFlow()
    private var model: GenerativeModel? = null

    override suspend fun refresh() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            _modelState.value = LocalAiModelState(LocalAiInstallStatus.UNAVAILABLE, message = "On-device stories require Android 8.0 or newer.")
            return
        }
        _modelState.value = _modelState.value.copy(status = LocalAiInstallStatus.CHECKING, message = null)
        runCatching {
            when (client().checkStatus()) {
                FeatureStatus.AVAILABLE -> LocalAiModelState(LocalAiInstallStatus.INSTALLED)
                FeatureStatus.DOWNLOADABLE -> LocalAiModelState(LocalAiInstallStatus.NOT_INSTALLED, message = "Ready for a Google-managed download.")
                FeatureStatus.DOWNLOADING -> LocalAiModelState(LocalAiInstallStatus.DOWNLOADING, message = "AICore is downloading the model.")
                else -> LocalAiModelState(LocalAiInstallStatus.UNAVAILABLE, message = "This device does not currently offer the required on-device model.")
            }
        }.onSuccess { _modelState.value = it }
            .onFailure { _modelState.value = LocalAiModelState(LocalAiInstallStatus.FAILED, message = it.message ?: "Could not check local AI availability.") }
    }

    override suspend fun download() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            refresh()
            return
        }
        runCatching {
            client().download().collect { status ->
                _modelState.value = when (status) {
                    is DownloadStatus.DownloadStarted -> LocalAiModelState(LocalAiInstallStatus.DOWNLOADING, totalBytes = status.bytesToDownload, message = "Downloading securely through AICore.")
                    is DownloadStatus.DownloadProgress -> _modelState.value.copy(status = LocalAiInstallStatus.DOWNLOADING, downloadedBytes = status.totalBytesDownloaded)
                    DownloadStatus.DownloadCompleted -> LocalAiModelState(LocalAiInstallStatus.INSTALLED, message = "Verified by Android and ready offline.")
                    is DownloadStatus.DownloadFailed -> LocalAiModelState(LocalAiInstallStatus.FAILED, message = status.e.message ?: "The model download failed.")
                }
            }
        }.onFailure { _modelState.value = LocalAiModelState(LocalAiInstallStatus.FAILED, message = it.message ?: "The model download failed.") }
    }

    internal suspend fun availableModel(): GenerativeModel {
        refresh()
        check(_modelState.value.status == LocalAiInstallStatus.INSTALLED) { _modelState.value.message ?: "Download Local AI in Settings before generating a story." }
        return client()
    }

    private fun client(): GenerativeModel = model ?: Generation.getClient().also { model = it }
}

@Singleton
class AicoreStoryProvider @Inject constructor(private val localAi: AicoreLocalAiRepository) : StoryProvider {
    override val type: AiProvider = AiProvider.LOCAL_GEMMA

    override suspend fun generateStory(input: StoryGenerationInput, apiKey: String): GeneratedStory {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { "On-device stories require Android 8.0 or newer." }
        val bitmaps = input.photos.map { loadBitmap(it.imagePath) }
        try {
            val content = content {
                bitmaps.forEach { image(it) }
                text(localStoryPrompt(input))
            }
            val response = localAi.availableModel().generateContent(
                generateContentRequest(SystemInstruction("You are Pluck's private, on-device fiction writer. Respect the ordered visual journey. Never claim uncertain location or identity guesses as facts."), content) {
                    temperature = 0.85f
                    maxOutputTokens = 1800
                }
            )
            return parseLocalStory(response.candidates.firstOrNull()?.text.orEmpty())
        } finally {
            bitmaps.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
        }
    }

    override suspend fun testConnection(apiKey: String): ConnectionResult {
        localAi.refresh()
        return when (localAi.modelState.value.status) {
            LocalAiInstallStatus.INSTALLED -> ConnectionResult.Connected
            LocalAiInstallStatus.UNAVAILABLE -> ConnectionResult.Failed(localAi.modelState.value.message ?: "Unavailable on this device.")
            LocalAiInstallStatus.NOT_INSTALLED -> ConnectionResult.Failed("Download Local AI in Settings first.")
            LocalAiInstallStatus.DOWNLOADING -> ConnectionResult.Failed("Local AI is still downloading.")
            else -> ConnectionResult.Failed(localAi.modelState.value.message ?: "Local AI is not ready.")
        }
    }

    private fun loadBitmap(path: String): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: throw IOException("A journey image could not be read.")
    }
}

private fun localStoryPrompt(input: StoryGenerationInput): String = buildString {
    append("These images are one ordered journey. Write 700–1200 words of original fiction inspired by every place. This must be one continuous narrative, never a diary or image-by-image summary. Keep characters, cause and effect, chronology, and setting details consistent. Return exactly: TITLE: <title> then STORY: <narrative>.")
    input.photos.forEachIndexed { index, photo -> append("\nScene ${index + 1}: time=${photo.timestamp}; place hint=${photo.address ?: "none"}.") }
}

private fun parseLocalStory(text: String): GeneratedStory {
    val title = Regex("(?is)TITLE\\s*:\\s*(.+?)(?:\\n|STORY\\s*:)").find(text)?.groupValues?.get(1)?.trim()?.take(160).orEmpty().ifBlank { "A Plucked Story" }
    val story = Regex("(?is)STORY\\s*:\\s*(.+)").find(text)?.groupValues?.get(1)?.trim().orEmpty().ifBlank { text.trim() }
    if (story.length < 80) throw IOException("The on-device model returned an incomplete story. Please try again.")
    return GeneratedStory(title, story)
}
