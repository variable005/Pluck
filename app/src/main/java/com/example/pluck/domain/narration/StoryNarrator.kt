package com.example.pluck.domain.narration

import kotlinx.coroutines.flow.StateFlow

/**
 * Local, reader-scoped narration for a generated story.
 *
 * Implementations must never send story text to a remote service. The reader can observe [state]
 * to expose a compact play/pause control and [voices] to offer only voices that are usable
 * offline on the current device.
 */
interface StoryNarrator {
    /** The current availability and playback state. */
    val state: StateFlow<NarrationState>

    /** Installed voices that explicitly support speech without a network connection. */
    val voices: StateFlow<List<NarrationVoice>>

    /** Starts the local speech engine and discovers offline voices. Safe to call repeatedly. */
    suspend fun initialize()

    /** Re-reads the device's installed offline voices after the user changes system TTS settings. */
    suspend fun refreshVoices()

    /**
     * Selects an installed offline voice by [NarrationVoice.id].
     *
     * Returns false when that voice is no longer installed or cannot be activated.
     */
    suspend fun selectVoice(voiceId: String): Boolean

    /** Queues a story for local playback, replacing any story that is currently playing. */
    suspend fun play(text: String)

    /** Pauses at the current safe sentence boundary. Resume repeats at most the active segment. */
    fun pause()

    /** Continues a paused story from its current safe segment. */
    fun resume()

    /** Stops playback and clears the in-memory story queue. */
    fun stop()

    /** Releases the Android TTS engine. A later [initialize] creates a fresh engine. */
    fun shutdown()
}

/** A locally installed, non-network Android text-to-speech voice. */
data class NarrationVoice(
    /** Stable Android engine voice name, suitable for [StoryNarrator.selectVoice]. */
    val id: String,
    /** Friendly system-localized label for display in Settings or the reader. */
    val label: String,
    /** BCP-47 language tag, for example `en-US`. */
    val languageTag: String
)

/** Immutable narration status intended for a reader ViewModel and Compose UI. */
sealed interface NarrationState {
    /** The engine has not been needed yet. */
    data object Idle : NarrationState

    /** Android is connecting to the device's text-to-speech engine. */
    data object Initializing : NarrationState

    /** A local voice is ready and no story is currently playing. */
    data class Ready(
        val selectedVoiceId: String,
        val offlineVoiceCount: Int
    ) : NarrationState

    /** A story is playing locally. Segment values are one-based for direct UI display. */
    data class Speaking(
        val segment: Int,
        val segmentCount: Int,
        val selectedVoiceId: String
    ) : NarrationState

    /** Playback is paused. Resuming restarts only the current safe segment. */
    data class Paused(
        val segment: Int,
        val segmentCount: Int,
        val selectedVoiceId: String
    ) : NarrationState

    /** The device cannot provide a private, offline voice at the moment. */
    data class Unavailable(
        val reason: NarrationUnavailableReason,
        val message: String
    ) : NarrationState

    /** A recoverable local engine or playback problem. */
    data class Error(
        val message: String,
        val canRetry: Boolean = true
    ) : NarrationState
}

/** Reasons presented when local audiobook playback cannot begin. */
enum class NarrationUnavailableReason {
    /** Android could not start any text-to-speech engine. */
    NO_TTS_ENGINE,

    /** An engine exists, but it has no installed voice that supports offline speech. */
    NO_OFFLINE_VOICE
}
