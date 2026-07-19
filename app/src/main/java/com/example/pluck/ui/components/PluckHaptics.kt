package com.example.pluck.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.pluck.domain.model.HapticMode

/** The current user-selected haptic intensity, supplied at the app root. */
val LocalHapticMode = staticCompositionLocalOf { HapticMode.ESSENTIAL }

/**
 * A semantic, app-wide vocabulary for user-initiated tactile feedback.
 *
 * These events are deliberately limited to meaningful actions. Android's system haptic
 * preferences and device capabilities decide whether a vibration is actually performed, so no
 * permission or custom vibrator access is needed.
 */
enum class PluckHapticEvent {
    /** A light acknowledgement that the user changed destinations or moved back. */
    Navigation,

    /** A primary action has been accepted, such as starting a journey or generating a story. */
    PrimaryAction,

    /** The camera shutter has been pressed. */
    Capture,

    /** A destructive action has been deliberately confirmed. */
    DestructiveAction,

    /** A user-initiated operation completed successfully. */
    Success,

    /** A user-initiated operation could not be completed. */
    Error
}

/**
 * Performs Pluck's semantic haptic events through Compose's platform haptic service.
 *
 * Keep calls inside direct user-event handlers; do not use this for scrolling, loading, or
 * passive animations.
 */
class PluckHaptics internal constructor(
    private val haptics: HapticFeedback,
    private val mode: HapticMode
) {
    /** Performs the appropriate system haptic pattern for [event]. */
    fun perform(event: PluckHapticEvent) {
        if (!mode.allows(event)) return
        haptics.performHapticFeedback(
            when (event) {
                PluckHapticEvent.Navigation -> HapticFeedbackType.VirtualKey
                PluckHapticEvent.PrimaryAction,
                PluckHapticEvent.Success -> HapticFeedbackType.Confirm
                PluckHapticEvent.Capture -> HapticFeedbackType.GestureEnd
                PluckHapticEvent.DestructiveAction -> HapticFeedbackType.LongPress
                PluckHapticEvent.Error -> HapticFeedbackType.Reject
            }
        )
    }
}

private fun HapticMode.allows(event: PluckHapticEvent): Boolean = when (this) {
    HapticMode.OFF -> false
    HapticMode.ESSENTIAL -> event != PluckHapticEvent.Navigation
    HapticMode.FULL -> true
}

/** Returns a stable haptic dispatcher that honors the device's system haptic settings. */
@Composable
fun rememberPluckHaptics(): PluckHaptics {
    val haptics = LocalHapticFeedback.current
    val mode = LocalHapticMode.current
    return remember(haptics, mode) { PluckHaptics(haptics, mode) }
}
