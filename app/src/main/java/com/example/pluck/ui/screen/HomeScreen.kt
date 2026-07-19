package com.example.pluck.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pluck.ui.components.AnimatedPrimaryButton
import com.example.pluck.ui.components.ExpressiveCard
import com.example.pluck.ui.components.StatusPill
import com.example.pluck.viewmodel.HomeViewModel

/** A quiet, focused entry point for beginning or continuing today's journey. */
@Composable
fun HomeScreen(
    onJourney: (Long) -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val hasJourney = state.journey != null

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 124.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            HomeHeader(onSettings)

            Spacer(Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = when {
                        !state.preferredName.isNullOrBlank() -> "Good to see you,\n${state.preferredName}."
                        hasJourney -> "Keep the story\nmoving."
                        else -> "A story starts\nwith one place."
                    },
                    style = MaterialTheme.typography.displaySmall
                )
                Text(
                    text = if (hasJourney) {
                        "Add the next place when you arrive. Pluck will hold the thread together."
                    } else {
                        "Capture a place, then let Pluck turn the path into fiction."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TodayCard(hasJourney = hasJourney)

            AnimatedPrimaryButton(
                text = if (hasJourney) "Continue today’s journey" else "Start today’s journey",
                onClick = { viewModel.start(onJourney) },
                modifier = Modifier.fillMaxWidth(),
                icon = {
                    Icon(
                        imageVector = if (hasJourney) Icons.Rounded.AutoStories else Icons.Rounded.TravelExplore,
                        contentDescription = null
                    )
                }
            )
        }
    }
}

@Composable
private fun HomeHeader(onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.AutoStories,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text("Pluck", style = MaterialTheme.typography.titleLarge)
            Text(
                "Your day, retold.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "Open settings")
            }
        }
    }
}

@Composable
private fun TodayCard(hasJourney: Boolean) {
    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = if (hasJourney) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (hasJourney) Icons.Rounded.AutoStories else Icons.Rounded.TravelExplore,
                        contentDescription = null,
                        tint = if (hasJourney) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (hasJourney) "Today’s journey is underway" else "Nothing captured yet",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (hasJourney) "Your next photo becomes the next scene." else "Start with a place you want to remember.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusPill(if (hasJourney) "Journey in progress" else "Ready when you are", active = hasJourney)
            }
        }
    }
}
