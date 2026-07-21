package com.example.pluck.data.localai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.StatFs
import com.example.pluck.domain.model.AiProvider
import com.example.pluck.domain.model.ConnectionResult
import com.example.pluck.domain.model.GeneratedStory
import com.example.pluck.domain.model.LocalAiInstallStatus
import com.example.pluck.domain.model.LocalAiModelState
import com.example.pluck.domain.model.StoryGenerationInput
import com.example.pluck.domain.provider.StoryProvider
import com.example.pluck.domain.repository.LocalAiRepository
import com.example.pluck.data.prompt.StoryPromptBuilder
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Immutable source manifest for the only model Pluck accepts in this release.
 *
 * The source is the Google-documented LiteRT Community distribution channel,
 * delivered through Hugging Face. It is not a Google-owned download host. The
 * revision and SHA-256 are deliberately compiled into the signed app: changing
 * a URL in preferences or supplying a model file is not possible.
 */
internal object OfficialGemma4E2B {
    const val name = "Gemma 4 E2B Instruct"
    const val publisher = "Google AI Edge LiteRT Community · delivered via Hugging Face"
    const val repository = "litert-community/gemma-4-E2B-it-litert-lm"
    const val revision = "7022fb75cac85d830562b14e8b583bdb7f8cb322"
    const val fileName = "gemma-4-E2B-it.litertlm"
    const val sha256 = "ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42"
    const val modelBytes = 2_580_000_000L
    const val requiredStorageBytes = 4_500_000_000L
    const val downloadUrl = "https://huggingface.co/$repository/resolve/$revision/$fileName"
    const val updateMetadataUrl = "https://huggingface.co/api/models/$repository"
}

/**
 * A deliberately lightweight, read-only status lookup for Pluck's home-screen widget.
 *
 * It checks only the app-private verification marker written by [LiteRtModelManager], so the
 * launcher never receives a model path, prompt, image, or story. Full checksum verification
 * remains the responsibility of the model manager before inference begins.
 */
data class LocalGemmaWidgetSnapshot(
    val isReady: Boolean,
    val installedBytes: Long
)

object LocalGemmaWidgetStatus {
    fun snapshot(context: Context): LocalGemmaWidgetSnapshot {
        val root = File(context.noBackupFilesDir, "local_ai_models")
        val model = File(root, OfficialGemma4E2B.fileName)
        val marker = File(root, "${OfficialGemma4E2B.fileName}.verified")
        val isReady = model.isFile && marker.isFile && runCatching {
            marker.readText().trim() == OfficialGemma4E2B.sha256
        }.getOrDefault(false)
        return LocalGemmaWidgetSnapshot(
            isReady = isReady,
            installedBytes = if (isReady) model.length() else 0L
        )
    }
}

/**
 * Downloads and verifies Pluck's fixed Gemma model inside app-private, no-backup
 * storage. A partial download is retained solely to resume the same pinned file.
 */
@Singleton
class LiteRtModelManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient
) : LocalAiRepository {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferences = context.getSharedPreferences("pluck_local_ai", Context.MODE_PRIVATE)
    private val root = File(context.noBackupFilesDir, "local_ai_models")
    private val modelFile = File(root, OfficialGemma4E2B.fileName)
    private val partialFile = File(root, "${OfficialGemma4E2B.fileName}.part")
    private val verificationFile = File(root, "${OfficialGemma4E2B.fileName}.verified")
    private var activeDownload: Job? = null

    private val _modelState = MutableStateFlow(baseState(LocalAiInstallStatus.CHECKING))
    override val modelState: StateFlow<LocalAiModelState> = _modelState.asStateFlow()

    override suspend fun refresh() {
        var resume = false
        mutex.withLock {
            val availableBytes = availableStorageBytes()
            _modelState.value = when {
                hasVerifiedModel() -> baseState(
                    status = LocalAiInstallStatus.INSTALLED,
                    downloadedBytes = modelFile.length(),
                    availableBytes = availableBytes,
                    verified = true,
                    message = "Verified and ready to generate offline."
                )
                modelFile.exists() -> {
                    deleteQuietly(modelFile)
                    deleteQuietly(verificationFile)
                    baseState(
                        status = LocalAiInstallStatus.CORRUPTED,
                        availableBytes = availableBytes,
                        message = "An unverified model was removed. Download the official model again."
                    )
                }
                partialFile.exists() -> {
                    resume = preferences.getBoolean(KEY_RESUME_REQUESTED, false) && activeDownload?.isActive != true
                    baseState(
                        status = if (resume) LocalAiInstallStatus.DOWNLOADING else LocalAiInstallStatus.PAUSED,
                        downloadedBytes = partialFile.length(),
                        availableBytes = availableBytes,
                        message = if (resume) "Resuming your verified download." else "Download paused. Your verified partial download is kept privately."
                    )
                }
                else -> baseState(
                    status = LocalAiInstallStatus.NOT_INSTALLED,
                    availableBytes = availableBytes,
                    message = storageMessage(availableBytes)
                )
            }
        }
        if (resume) startDownload()
    }

    override suspend fun download() {
        preferences.edit().putBoolean(KEY_RESUME_REQUESTED, true).apply()
        startDownload()
    }

    override suspend fun pause() {
        preferences.edit().putBoolean(KEY_RESUME_REQUESTED, false).apply()
        val running = mutex.withLock { activeDownload }
        running?.cancel()
        running?.join()
        refresh()
    }

    override suspend fun delete() {
        pause()
        mutex.withLock {
            deleteQuietly(partialFile)
            deleteQuietly(modelFile)
            deleteQuietly(verificationFile)
            deleteQuietly(File(context.cacheDir, "pluck_litert_cache"))
            _modelState.value = baseState(
                status = LocalAiInstallStatus.NOT_INSTALLED,
                availableBytes = availableStorageBytes(),
                message = "Local Gemma was removed from this device."
            )
        }
    }

    override suspend fun verify() = withContext(Dispatchers.IO) {
        val canVerify = mutex.withLock {
            if (!modelFile.exists()) {
                _modelState.value = baseState(LocalAiInstallStatus.NOT_INSTALLED, availableBytes = availableStorageBytes(), message = "Download Local Gemma before verifying it.")
                false
            } else {
                _modelState.value = baseState(LocalAiInstallStatus.VERIFYING, downloadedBytes = modelFile.length(), availableBytes = availableStorageBytes(), message = "Checking the model's SHA-256 integrity.")
                true
            }
        }
        if (!canVerify) return@withContext
        val valid = runCatching { sha256(modelFile).equals(OfficialGemma4E2B.sha256, ignoreCase = true) }.getOrDefault(false)
        mutex.withLock {
            if (valid) {
                writeVerificationMarker()
                _modelState.value = baseState(LocalAiInstallStatus.INSTALLED, downloadedBytes = modelFile.length(), availableBytes = availableStorageBytes(), verified = true, message = "Integrity verified. Ready offline.")
            } else {
                deleteQuietly(modelFile)
                deleteQuietly(verificationFile)
                _modelState.value = baseState(LocalAiInstallStatus.CORRUPTED, availableBytes = availableStorageBytes(), message = "The invalid model was deleted. Download the official model again.")
            }
        }
    }

    /** Checks the official repository for a newer revision without trusting it for installation. */
    override suspend fun checkForUpdates(): Unit = withContext(Dispatchers.IO) {
        val result = runCatching {
            httpClient.newCall(Request.Builder().url(OfficialGemma4E2B.updateMetadataUrl).get().build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Could not check for model updates (${response.code}).")
                Regex("\\\"sha\\\"\\s*:\\s*\\\"([0-9a-f]+)\\\"").find(response.body?.string().orEmpty())?.groupValues?.get(1)
                    ?: throw IOException("The official model metadata did not contain a revision.")
            }
        }
        mutex.withLock {
            val current = _modelState.value
            result.onSuccess { latest ->
                val updateAvailable = latest != OfficialGemma4E2B.revision
                _modelState.value = current.copy(
                    updateAvailable = updateAvailable,
                    message = if (updateAvailable) {
                        "A newer official model exists. Update Pluck to receive its new pinned integrity manifest."
                    } else {
                        "You have the latest model pinned by this version of Pluck."
                    }
                )
            }.onFailure { error ->
                _modelState.value = current.copy(message = error.message ?: "Could not check for updates. Your installed model remains usable offline.")
            }
        }
    }

    /** Returns a model path only after this app has verified the model it downloaded. */
    suspend fun verifiedModelPath(): String {
        refresh()
        check(_modelState.value.status == LocalAiInstallStatus.INSTALLED && hasVerifiedModel()) {
            _modelState.value.message ?: "Download Local Gemma in Settings before generating a story."
        }
        return modelFile.absolutePath
    }

    private suspend fun startDownload() {
        mutex.withLock {
            if (activeDownload?.isActive == true) return
            if (hasVerifiedModel()) {
                _modelState.value = baseState(
                    status = LocalAiInstallStatus.INSTALLED,
                    downloadedBytes = modelFile.length(),
                    availableBytes = availableStorageBytes(),
                    verified = true,
                    message = "Gemma is already verified and ready offline."
                )
                return
            }
            val available = availableStorageBytes()
            if (available < OfficialGemma4E2B.requiredStorageBytes) {
                _modelState.value = baseState(LocalAiInstallStatus.FAILED, availableBytes = available, message = storageMessage(available))
                return
            }
            root.mkdirs()
            activeDownload = scope.launch { performDownload() }
        }
    }

    private suspend fun performDownload() {
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                val existingBytes = partialFile.takeIf { it.exists() }?.length() ?: 0L
                _modelState.value = baseState(
                    status = LocalAiInstallStatus.DOWNLOADING,
                    downloadedBytes = existingBytes,
                    totalBytes = OfficialGemma4E2B.modelBytes,
                    availableBytes = availableStorageBytes(),
                    message = "Downloading only the pinned official Gemma release over HTTPS."
                )
                val request = Request.Builder().url(OfficialGemma4E2B.downloadUrl).apply {
                    if (existingBytes > 0) header("Range", "bytes=$existingBytes-")
                }.get().build()
                httpClient.newCall(request).execute().use { response ->
                    if (existingBytes > 0 && response.code == 200) {
                        // The host did not honour Range. Restart rather than append a corrupt file.
                        deleteQuietly(partialFile)
                        continue
                    }
                    if (!response.isSuccessful) throw IOException("Official model download failed (${response.code}).")
                    val expectedTotal = response.header("Content-Range")
                        ?.substringAfter('/')
                        ?.toLongOrNull()
                        ?: response.body?.contentLength()?.takeIf { it >= 0 }?.plus(existingBytes)
                        ?: OfficialGemma4E2B.modelBytes
                    val body = response.body ?: throw IOException("Official model download returned no data.")
                    body.byteStream().use { input ->
                        FileOutputStream(partialFile, existingBytes > 0).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var downloaded = existingBytes
                            var lastUpdate = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                downloaded += count
                                val now = System.currentTimeMillis()
                                if (now - lastUpdate >= PROGRESS_INTERVAL_MS) {
                                    _modelState.value = baseState(
                                        status = LocalAiInstallStatus.DOWNLOADING,
                                        downloadedBytes = downloaded,
                                        totalBytes = expectedTotal,
                                        availableBytes = availableStorageBytes(),
                                        message = "Downloading securely over HTTPS."
                                    )
                                    lastUpdate = now
                                }
                            }
                            output.fd.sync()
                        }
                    }
                }
                break
            }
            _modelState.value = baseState(LocalAiInstallStatus.VERIFYING, downloadedBytes = partialFile.length(), availableBytes = availableStorageBytes(), message = "Verifying the model's cryptographic integrity.")
            if (!sha256(partialFile).equals(OfficialGemma4E2B.sha256, ignoreCase = true)) {
                deleteQuietly(partialFile)
                _modelState.value = baseState(LocalAiInstallStatus.CORRUPTED, availableBytes = availableStorageBytes(), message = "The invalid download was deleted. Please try again.")
                return
            }
            if (modelFile.exists()) deleteQuietly(modelFile)
            check(partialFile.renameTo(modelFile)) { "Could not finalize the verified model installation." }
            writeVerificationMarker()
            preferences.edit().putBoolean(KEY_RESUME_REQUESTED, false).apply()
            _modelState.value = baseState(LocalAiInstallStatus.INSTALLED, downloadedBytes = modelFile.length(), availableBytes = availableStorageBytes(), verified = true, message = "Gemma is verified and ready to run offline.")
        } catch (cancellation: CancellationException) {
            _modelState.value = baseState(LocalAiInstallStatus.PAUSED, downloadedBytes = partialFile.length(), availableBytes = availableStorageBytes(), message = "Download paused. Resume when you are ready.")
            throw cancellation
        } catch (error: Exception) {
            _modelState.value = baseState(LocalAiInstallStatus.FAILED, downloadedBytes = partialFile.length(), availableBytes = availableStorageBytes(), message = error.message ?: "The model download failed. Your partial download can resume securely.")
        } finally {
            mutex.withLock { activeDownload = null }
        }
    }

    private fun hasVerifiedModel(): Boolean = modelFile.exists() && verificationFile.exists() && verificationFile.readText().trim() == OfficialGemma4E2B.sha256

    private fun writeVerificationMarker() {
        root.mkdirs()
        FileOutputStream(verificationFile).use { it.write(OfficialGemma4E2B.sha256.toByteArray()) }
    }

    private fun availableStorageBytes(): Long = StatFs(context.noBackupFilesDir.absolutePath).availableBytes

    private fun baseState(
        status: LocalAiInstallStatus,
        downloadedBytes: Long = 0,
        totalBytes: Long = OfficialGemma4E2B.modelBytes,
        availableBytes: Long = availableStorageBytes(),
        verified: Boolean = false,
        message: String? = null
    ) = LocalAiModelState(
        status = status,
        modelName = OfficialGemma4E2B.name,
        modelVersion = OfficialGemma4E2B.revision.take(7),
        publisher = OfficialGemma4E2B.publisher,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        requiredStorageBytes = OfficialGemma4E2B.requiredStorageBytes,
        availableStorageBytes = availableBytes,
        verified = verified,
        message = message
    )

    private fun storageMessage(availableBytes: Long): String = if (availableBytes < OfficialGemma4E2B.requiredStorageBytes) {
        "Not enough private storage. Free ${formatBytes(OfficialGemma4E2B.requiredStorageBytes - availableBytes)} more, then try again."
    } else {
        "The download is ${formatBytes(OfficialGemma4E2B.modelBytes)}. Pluck reserves ${formatBytes(OfficialGemma4E2B.requiredStorageBytes)} for a safe install and runtime cache."
    }

    private fun deleteQuietly(file: File) {
        if (file.isDirectory) file.listFiles()?.forEach(::deleteQuietly)
        file.delete()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val KEY_RESUME_REQUESTED = "resume_requested"
        const val PROGRESS_INTERVAL_MS = 350L
    }
}

/** Runs the verified multimodal Gemma model locally through Google's LiteRT-LM runtime. */
@Singleton
class GemmaLocalStoryProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val modelManager: LiteRtModelManager
) : StoryProvider {
    override val type: AiProvider = AiProvider.LOCAL_GEMMA

    override suspend fun generateStory(input: StoryGenerationInput, apiKey: String): GeneratedStory {
        val modelPath = modelManager.verifiedModelPath()
        // A single retry handles transient native initialization failures without ever using cloud fallback.
        var lastFailure: Throwable? = null
        repeat(2) { attempt ->
            try {
                return generateOnce(modelPath, input)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                lastFailure = error
                if (attempt == 0) Thread.yield()
            }
        }
        throw IOException("On-device generation could not finish. ${lastFailure?.message ?: "Please try again."}", lastFailure)
    }

    override suspend fun testConnection(apiKey: String): ConnectionResult {
        modelManager.refresh()
        return when (modelManager.modelState.value.status) {
            LocalAiInstallStatus.INSTALLED -> ConnectionResult.Connected
            LocalAiInstallStatus.DOWNLOADING, LocalAiInstallStatus.VERIFYING -> ConnectionResult.Failed("Local Gemma is still being prepared.")
            LocalAiInstallStatus.PAUSED -> ConnectionResult.Failed("Resume the Local Gemma download in Settings first.")
            else -> ConnectionResult.Failed(modelManager.modelState.value.message ?: "Download Local Gemma in Settings first.")
        }
    }

    private suspend fun generateOnce(modelPath: String, input: StoryGenerationInput): GeneratedStory = withContext(Dispatchers.Default) {
        val preparedImages = prepareImages(input)
        val engineCache = File(context.cacheDir, "pluck_litert_cache").apply { mkdirs() }
        var engine: Engine? = null
        try {
            engine = Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(threadCount = 4),
                    visionBackend = Backend.GPU(),
                    maxNumTokens = 4_096,
                    maxNumImages = preparedImages.size,
                    cacheDir = engineCache.absolutePath
                )
            ).also(Engine::initialize)
            engine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(Content.Text("You are Pluck's private, on-device fiction writer. Treat all uncertain visual or location details as inspiration, never fact.")),
                    samplerConfig = SamplerConfig(topK = 48, topP = 0.92, temperature = 0.82)
                )
            ).use { conversation ->
                val contents = buildList {
                    preparedImages.forEach { add(Content.ImageFile(it.absolutePath)) }
                    add(Content.Text(StoryPromptBuilder.build(input)))
                }
                val response = try {
                    withTimeout(GENERATION_TIMEOUT_MS) {
                        streamResponse(conversation, Contents.of(contents))
                    }
                } finally {
                    conversation.cancelProcess()
                }
                StoryPromptBuilder.parse(response, input)
            }
        } finally {
            engine?.close()
            preparedImages.forEach { it.delete() }
        }
    }

    private fun prepareImages(input: StoryGenerationInput): List<File> {
        val outputDir = File(context.cacheDir, "pluck_story_images").apply { mkdirs() }
        return input.photos.mapIndexed { index, photo ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(photo.imagePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("A journey image could not be read.")
            var sample = 1
            while (bounds.outWidth / sample > MAX_IMAGE_EDGE || bounds.outHeight / sample > MAX_IMAGE_EDGE) sample *= 2
            val bitmap = BitmapFactory.decodeFile(photo.imagePath, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: throw IOException("A journey image could not be read.")
            File(outputDir, "scene-$index.jpg").also { destination ->
                try {
                    FileOutputStream(destination).use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output) }
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    private companion object {
        const val MAX_IMAGE_EDGE = 768
        const val JPEG_QUALITY = 82
        const val GENERATION_TIMEOUT_MS = 12 * 60 * 1_000L
    }
}

/**
 * Bridges LiteRT-LM's callback streaming API to a cancellable coroutine.
 *
 * The callback overload is deliberately used instead of LiteRT-LM 0.14's
 * Flow overload: the latter contains a binary-incompatible channel default
 * method call on Android. This keeps token streaming while avoiding that
 * runtime-only crash.
 */
private suspend fun streamResponse(conversation: Conversation, contents: Contents): String =
    suspendCancellableCoroutine { continuation ->
        val response = StringBuilder()
        val callback = object : MessageCallback {
            override fun onMessage(message: Message) {
                val text = message.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString(separator = "") { it.text }
                synchronized(response) { response.append(text) }
            }

            override fun onDone() {
                if (continuation.isActive) {
                    continuation.resume(synchronized(response) { response.toString() })
                }
            }

            override fun onError(throwable: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(throwable)
            }
        }

        continuation.invokeOnCancellation { conversation.cancelProcess() }
        try {
            conversation.sendMessageAsync(contents, callback, emptyMap())
        } catch (error: Throwable) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }

private fun formatBytes(value: Long): String = when {
    value < 1_000_000 -> "${value / 1_000} KB"
    else -> "%.1f GB".format(value / 1_000_000_000.0)
}
