package com.example.pluck.data.narration

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.pluck.domain.narration.NarrationState
import com.example.pluck.domain.narration.NarrationUnavailableReason
import com.example.pluck.domain.narration.NarrationVoice
import com.example.pluck.domain.narration.StoryNarrator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Android's built-in, offline-only text-to-speech implementation for the story reader.
 *
 * The class deliberately filters out every voice that requires a network connection. Story text
 * is held only in a short-lived in-memory sentence queue, and Android's engine is always asked to
 * speak one segment at a time. That makes pause/resume predictable even on engines which do not
 * natively expose a pause API.
 */
@Singleton
class AndroidStoryNarrator @Inject constructor(
    @param:ApplicationContext private val context: Context
) : StoryNarrator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()

    private val _state = MutableStateFlow<NarrationState>(NarrationState.Idle)
    override val state: StateFlow<NarrationState> = _state.asStateFlow()

    private val _voices = MutableStateFlow<List<NarrationVoice>>(emptyList())
    override val voices: StateFlow<List<NarrationVoice>> = _voices.asStateFlow()

    private val preferences = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
    private var engine: TextToSpeech? = null
    private var initializing: CompletableDeferred<EngineInitialization>? = null
    private var selectedVoiceId: String? = preferences.getString(SELECTED_VOICE_KEY, null)
    private var queuedSegments: List<String> = emptyList()
    private var currentSegmentIndex = 0
    private var activeUtteranceId: String? = null
    private var isPaused = false
    private var playbackGeneration = 0L

    override suspend fun initialize() {
        if (ensureEngine()) refreshVoicesInternal()
    }

    override suspend fun refreshVoices() {
        if (ensureEngine()) refreshVoicesInternal()
    }

    override suspend fun selectVoice(voiceId: String): Boolean {
        if (!ensureEngine()) return false

        stopInternal(publishReadyState = false)
        val selected = withContext(Dispatchers.Main.immediate) {
            val activeEngine = engine ?: return@withContext null
            val voice = activeEngine.offlineVoices().firstOrNull { it.name == voiceId }
                ?: return@withContext null
            val result = activeEngine.setVoice(voice)
            if (result == TextToSpeech.SUCCESS && activeEngine.voice?.name == voice.name) voice else null
        }

        if (selected == null) {
            refreshVoicesInternal()
            _state.value = NarrationState.Error(
                message = "That offline voice is no longer available. Choose another installed voice.",
                canRetry = true
            )
            return false
        }

        val available = withContext(Dispatchers.Main.immediate) { engine?.offlineVoices().orEmpty() }
        mutex.withLock { selectedVoiceId = selected.name }
        persistSelectedVoice(selected.name)
        publishAvailability(available, selected.name)
        return true
    }

    override suspend fun play(text: String) {
        val segments = StoryNarrationSegments.split(text)
        if (segments.isEmpty()) {
            _state.value = NarrationState.Error(
                message = "There is no story text to read aloud.",
                canRetry = false
            )
            return
        }

        if (!ensureEngine()) return
        refreshVoicesInternal()
        val voiceId = mutex.withLock { selectedVoiceId }
        if (voiceId == null || _voices.value.none { it.id == voiceId }) {
            _state.value = NarrationState.Unavailable(
                reason = NarrationUnavailableReason.NO_OFFLINE_VOICE,
                message = "Install an offline text-to-speech voice in Android settings to listen privately."
            )
            return
        }

        mutex.withLock {
            queuedSegments = segments
            currentSegmentIndex = 0
            activeUtteranceId = null
            isPaused = false
            playbackGeneration += 1
        }
        withContext(Dispatchers.Main.immediate) { engine?.stop() }
        speakCurrentSegment()
    }

    override fun pause() {
        scope.launch { pauseInternal() }
    }

    override fun resume() {
        scope.launch {
            val shouldResume = mutex.withLock {
                if (!isPaused || currentSegmentIndex !in queuedSegments.indices) return@withLock false
                isPaused = false
                true
            }
            if (shouldResume) speakCurrentSegment()
        }
    }

    override fun stop() {
        scope.launch { stopInternal(publishReadyState = true) }
    }

    override fun shutdown() {
        scope.launch {
            stopInternal(publishReadyState = false)
            val shutdown = mutex.withLock {
                val previous = engine
                val pendingInitialization = initializing
                engine = null
                initializing = null
                previous to pendingInitialization
            }
            shutdown.second?.complete(EngineInitialization.Failure("Narration engine was released."))
            withContext(Dispatchers.Main.immediate) { shutdown.first?.shutdown() }
            _voices.value = emptyList()
            _state.value = NarrationState.Idle
        }
    }

    private suspend fun ensureEngine(): Boolean {
        val existing = mutex.withLock { engine }
        if (existing != null) return true

        val initialization = mutex.withLock {
            engine?.let { return@withLock null }
            initializing?.let { return@withLock it }
            CompletableDeferred<EngineInitialization>().also { deferred ->
                initializing = deferred
                _state.value = NarrationState.Initializing
                createEngine(deferred)
            }
        }

        if (initialization == null) return true
        return initialization.await() is EngineInitialization.Success
    }

    /** Creates TTS on the main thread, which avoids device-specific engine threading issues. */
    private fun createEngine(initialization: CompletableDeferred<EngineInitialization>) {
        scope.launch {
            val candidateReference = CompletableDeferred<TextToSpeech>()
            val candidate = runCatching {
                TextToSpeech(context.applicationContext) { status ->
                    scope.launch {
                        val initializedEngine = candidateReference.await()
                        completeInitialization(initialization, initializedEngine, status)
                    }
                }
            }.getOrElse { throwable ->
                completeInitialization(initialization, null, TextToSpeech.ERROR, throwable)
                return@launch
            }
            candidateReference.complete(candidate)
        }
    }

    private suspend fun completeInitialization(
        initialization: CompletableDeferred<EngineInitialization>,
        candidate: TextToSpeech?,
        status: Int,
        failure: Throwable? = null
    ) {
        if (status != TextToSpeech.SUCCESS || candidate == null) {
            withContext(Dispatchers.Main.immediate) { candidate?.shutdown() }
            val result = EngineInitialization.Failure(
                failure?.message ?: "Android could not start a text-to-speech engine."
            )
            val accepted = mutex.withLock {
                if (initializing === initialization) {
                    initializing = null
                    engine = null
                    initialization.complete(result)
                    true
                } else {
                    false
                }
            }
            if (accepted) {
                _state.value = NarrationState.Unavailable(
                    reason = NarrationUnavailableReason.NO_TTS_ENGINE,
                    message = "No Android text-to-speech engine is available on this device."
                )
            }
            return
        }

        candidate.setOnUtteranceProgressListener(progressListener)
        val accepted = mutex.withLock {
            if (initializing !== initialization) {
                false
            } else {
                engine = candidate
                initializing = null
                initialization.complete(EngineInitialization.Success)
                true
            }
        }
        if (!accepted) candidate.shutdown()
    }

    private suspend fun refreshVoicesInternal() {
        val available = withContext(Dispatchers.Main.immediate) { engine?.offlineVoices().orEmpty() }
        val rememberedVoiceId = mutex.withLock { selectedVoiceId }
        val selected = withContext(Dispatchers.Main.immediate) {
            val activeEngine = engine ?: return@withContext null
            val preferred = rememberedVoiceId
                ?.let { selectedId -> available.firstOrNull { it.name == selectedId } }
                ?: activeEngine.voice?.takeIf { current -> available.any { it.name == current.name } }
                ?: available.preferredFor(Locale.getDefault())
            if (preferred != null && activeEngine.voice?.name != preferred.name) {
                val result = activeEngine.setVoice(preferred)
                if (result != TextToSpeech.SUCCESS) return@withContext null
            }
            preferred
        }
        mutex.withLock { selectedVoiceId = selected?.name }
        persistSelectedVoice(selected?.name)
        publishAvailability(available, selected?.name)
    }

    private fun publishAvailability(available: List<Voice>, selectedId: String?) {
        val visibleVoices = available.map { it.toNarrationVoice() }
            .sortedWith(compareBy<NarrationVoice> { it.languageTag }.thenBy { it.label })
        _voices.value = visibleVoices
        _state.value = when {
            visibleVoices.isEmpty() -> NarrationState.Unavailable(
                reason = NarrationUnavailableReason.NO_OFFLINE_VOICE,
                message = "No offline Android text-to-speech voice is installed. Add one in Android settings to keep narration private."
            )
            selectedId == null -> NarrationState.Unavailable(
                reason = NarrationUnavailableReason.NO_OFFLINE_VOICE,
                message = "An offline voice could not be activated. Choose another installed voice."
            )
            else -> NarrationState.Ready(
                selectedVoiceId = selectedId,
                offlineVoiceCount = visibleVoices.size
            )
        }
    }

    private suspend fun speakCurrentSegment() {
        val request = mutex.withLock {
            if (isPaused || currentSegmentIndex !in queuedSegments.indices) return@withLock null
            PlaybackRequest(
                utteranceId = "pluck-story-$playbackGeneration-$currentSegmentIndex",
                text = queuedSegments[currentSegmentIndex],
                segment = currentSegmentIndex + 1,
                segmentCount = queuedSegments.size,
                voiceId = selectedVoiceId ?: return@withLock null
            ).also { activeUtteranceId = it.utteranceId }
        } ?: return

        val result = withContext(Dispatchers.Main.immediate) {
            val activeEngine = engine ?: return@withContext TextToSpeech.ERROR
            activeEngine.speak(request.text, TextToSpeech.QUEUE_FLUSH, null, request.utteranceId)
        }
        if (result != TextToSpeech.SUCCESS) {
            mutex.withLock {
                if (activeUtteranceId == request.utteranceId) {
                    activeUtteranceId = null
                    queuedSegments = emptyList()
                    currentSegmentIndex = 0
                    isPaused = false
                }
            }
            _state.value = NarrationState.Error(
                message = "Local narration could not start. Try another offline voice.",
                canRetry = true
            )
            return
        }
        _state.value = NarrationState.Speaking(
            segment = request.segment,
            segmentCount = request.segmentCount,
            selectedVoiceId = request.voiceId
        )
    }

    private suspend fun pauseInternal() {
        val snapshot = mutex.withLock {
            val voiceId = selectedVoiceId ?: return@withLock null
            if (isPaused || activeUtteranceId == null || currentSegmentIndex !in queuedSegments.indices) {
                return@withLock null
            }
            isPaused = true
            activeUtteranceId = null
            PlaybackSnapshot(currentSegmentIndex + 1, queuedSegments.size, voiceId)
        } ?: return
        withContext(Dispatchers.Main.immediate) { engine?.stop() }
        _state.value = NarrationState.Paused(
            segment = snapshot.segment,
            segmentCount = snapshot.segmentCount,
            selectedVoiceId = snapshot.voiceId
        )
    }

    private suspend fun stopInternal(publishReadyState: Boolean) {
        val nextState = mutex.withLock {
            val hadQueue = queuedSegments.isNotEmpty() || activeUtteranceId != null || isPaused
            queuedSegments = emptyList()
            currentSegmentIndex = 0
            activeUtteranceId = null
            isPaused = false
            playbackGeneration += 1
            if (publishReadyState && hadQueue) readyState() else null
        }
        withContext(Dispatchers.Main.immediate) { engine?.stop() }
        if (nextState != null) _state.value = nextState
    }

    private fun readyState(): NarrationState {
        val voiceId = selectedVoiceId
        return if (voiceId != null && _voices.value.any { it.id == voiceId }) {
            NarrationState.Ready(voiceId, _voices.value.size)
        } else {
            NarrationState.Unavailable(
                reason = NarrationUnavailableReason.NO_OFFLINE_VOICE,
                message = "Install an offline Android text-to-speech voice to listen privately."
            )
        }
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) = Unit

        override fun onDone(utteranceId: String) {
            scope.launch { handleUtteranceDone(utteranceId) }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) {
            scope.launch { handleUtteranceError(utteranceId) }
        }
    }

    private suspend fun handleUtteranceDone(utteranceId: String) {
        val shouldContinue = mutex.withLock {
            if (activeUtteranceId != utteranceId || isPaused) return@withLock false
            activeUtteranceId = null
            currentSegmentIndex += 1
            if (currentSegmentIndex in queuedSegments.indices) {
                true
            } else {
                queuedSegments = emptyList()
                currentSegmentIndex = 0
                _state.value = readyState()
                false
            }
        }
        if (shouldContinue) speakCurrentSegment()
    }

    private suspend fun handleUtteranceError(utteranceId: String) {
        val wasActive = mutex.withLock {
            if (activeUtteranceId != utteranceId) return@withLock false
            activeUtteranceId = null
            queuedSegments = emptyList()
            currentSegmentIndex = 0
            isPaused = false
            true
        }
        if (wasActive) {
            _state.value = NarrationState.Error(
                message = "Local narration stopped unexpectedly. You can try again.",
                canRetry = true
            )
        }
    }

    private fun TextToSpeech.offlineVoices(): List<Voice> = runCatching {
        voices.orEmpty()
            .filterNot { it.isNetworkConnectionRequired }
            .filterNot { voice -> voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true }
            .sortedBy { it.name }
    }.getOrDefault(emptyList())

    private fun List<Voice>.preferredFor(locale: Locale): Voice? =
        firstOrNull { it.locale == locale }
            ?: firstOrNull { it.locale.language == locale.language }
            ?: firstOrNull()

    private fun Voice.toNarrationVoice(): NarrationVoice {
        val systemLocale = Locale.getDefault()
        val language = locale.getDisplayName(systemLocale).ifBlank { locale.toLanguageTag() }
        return NarrationVoice(
            id = name,
            label = "$language · $name",
            languageTag = locale.toLanguageTag()
        )
    }

    private fun persistSelectedVoice(voiceId: String?) {
        preferences.edit().apply {
            if (voiceId == null) remove(SELECTED_VOICE_KEY) else putString(SELECTED_VOICE_KEY, voiceId)
        }.apply()
    }

    private sealed interface EngineInitialization {
        data object Success : EngineInitialization
        data class Failure(val message: String) : EngineInitialization
    }

    private data class PlaybackRequest(
        val utteranceId: String,
        val text: String,
        val segment: Int,
        val segmentCount: Int,
        val voiceId: String
    )

    private data class PlaybackSnapshot(
        val segment: Int,
        val segmentCount: Int,
        val voiceId: String
    )

    private companion object {
        const val PREFERENCES_FILE = "pluck_narration"
        const val SELECTED_VOICE_KEY = "selected_offline_voice"
    }
}

/** Splits prose conservatively so Android TTS never receives an oversized or abrupt utterance. */
internal object StoryNarrationSegments {
    private const val MAX_SEGMENT_CHARACTERS = 2_800
    private val paragraphBreak = Regex("\\r?\\n\\s*\\r?\\n+")
    private val sentenceBreak = Regex("(?<=[.!?…])\\s+")
    private val whitespace = Regex("\\s+")

    fun split(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        text.split(paragraphBreak)
            .asSequence()
            .map { it.trim().replace(whitespace, " ") }
            .filter { it.isNotEmpty() }
            .forEach { paragraph ->
                val paragraphSegments = mutableListOf<String>()
                paragraph.split(sentenceBreak)
                    .filter { it.isNotBlank() }
                    .forEach { sentence -> appendSentence(paragraphSegments, sentence.trim()) }
                result += paragraphSegments
            }
        return result
    }

    private fun appendSentence(target: MutableList<String>, sentence: String) {
        if (sentence.length <= MAX_SEGMENT_CHARACTERS) {
            appendChunk(target, sentence)
            return
        }

        var remaining = sentence
        while (remaining.isNotEmpty()) {
            val requestedBoundary = minOf(MAX_SEGMENT_CHARACTERS, remaining.lastIndex)
            val boundary = remaining.lastIndexOf(' ', startIndex = requestedBoundary)
                .takeIf { it > 0 }
                ?: safeCharacterBoundary(remaining, minOf(MAX_SEGMENT_CHARACTERS, remaining.length))
            appendChunk(target, remaining.substring(0, boundary).trim())
            remaining = remaining.substring(boundary).trimStart()
        }
    }

    /** Avoids splitting a surrogate pair when a single word is longer than one safe segment. */
    private fun safeCharacterBoundary(value: String, requested: Int): Int {
        if (requested <= 0 || requested >= value.length) return requested
        return if (value[requested - 1].isHighSurrogate() && value[requested].isLowSurrogate()) {
            requested - 1
        } else {
            requested
        }
    }

    private fun appendChunk(target: MutableList<String>, chunk: String) {
        val previous = target.lastOrNull()
        if (previous != null && previous.length + chunk.length + 1 <= MAX_SEGMENT_CHARACTERS) {
            target[target.lastIndex] = "$previous $chunk"
        } else {
            target += chunk
        }
    }
}
