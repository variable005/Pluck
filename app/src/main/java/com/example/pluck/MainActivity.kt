package com.example.pluck

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pluck.navigation.PluckNavHost
import com.example.pluck.ui.theme.PluckTheme
import com.example.pluck.viewmodel.ThemeSettingsViewModel
import com.example.pluck.widget.CaptureNextPlaceWidgetProvider
import com.example.pluck.widget.LatestStoryWidgetProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val widgetCaptureRequest = MutableStateFlow(0L)
    private val widgetLibraryRequest = MutableStateFlow(0L)
    private val widgetStoryRequest = MutableStateFlow(WidgetStoryRequest())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleWidgetIntent(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            val themeSettings: ThemeSettingsViewModel = hiltViewModel()
            val preferences by themeSettings.preferences.collectAsState()
            val captureRequest by widgetCaptureRequest.collectAsState()
            val libraryRequest by widgetLibraryRequest.collectAsState()
            val storyRequest by widgetStoryRequest.collectAsState()
            PluckTheme(themeMode = preferences.mode, dynamicColor = preferences.dynamicColor) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PluckNavHost(
                        widgetCaptureRequest = captureRequest,
                        widgetLibraryRequest = libraryRequest,
                        widgetStoryRequest = storyRequest.token,
                        widgetStoryJourneyId = storyRequest.journeyId
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        val widgetIntent = intent ?: return
        when (widgetIntent.action) {
            CaptureNextPlaceWidgetProvider.ACTION_CAPTURE_NEXT_PLACE -> {
                // A monotonic token guarantees a second tap is handled as a new navigation request.
                widgetCaptureRequest.value = SystemClock.elapsedRealtimeNanos()
            }
            LatestStoryWidgetProvider.ACTION_OPEN_STORY -> {
                val journeyId = widgetIntent.getLongExtra(
                    LatestStoryWidgetProvider.EXTRA_JOURNEY_ID,
                    INVALID_JOURNEY_ID
                )
                val token = SystemClock.elapsedRealtimeNanos()
                if (journeyId > 0L) {
                    widgetStoryRequest.value = WidgetStoryRequest(token, journeyId)
                } else {
                    // The empty state has no story to read, so surface the private Library instead.
                    widgetLibraryRequest.value = token
                }
            }
        }
    }

    private data class WidgetStoryRequest(
        val token: Long = 0L,
        val journeyId: Long = INVALID_JOURNEY_ID
    )

    private companion object {
        const val INVALID_JOURNEY_ID = -1L
    }
}
