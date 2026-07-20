package com.example.pluck.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pluck.domain.repository.JourneyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Resolves today's journey before the widget hands control to the regular CameraX capture route.
 *
 * Keeping this work here means the widget and the navigation UI never need direct database access.
 */
@HiltViewModel
class WidgetCaptureViewModel @Inject constructor(
    private val journeys: JourneyRepository
) : ViewModel() {

    /** Creates today's journey if needed, then supplies its stable identifier to [onReady]. */
    fun openCapture(onReady: (Long) -> Unit) {
        viewModelScope.launch {
            onReady(journeys.getOrCreateToday().id)
        }
    }
}
