package com.example.pluck.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pluck.ui.components.AnimatedPrimaryButton
import com.example.pluck.ui.components.ExpressiveCard
import com.example.pluck.ui.components.StatusPill
import com.example.pluck.viewmodel.HomeViewModel

@Composable
fun HomeScreen(onJourney: (Long) -> Unit, onSettings: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            HomeHeader(onSettings)
            AnimatedVisibility(true, enter = fadeIn(spring()) + slideInVertically { it / 6 }) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    HeroIllustration()
                    Text("Make today\nfeel like a story.", style = MaterialTheme.typography.displaySmall)
                    Text("Capture a place. Keep moving. When the day is ready, Pluck turns the path into fiction.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    JourneySummaryCard(hasJourney = state.journey != null)
                    AnimatedPrimaryButton(
                        text = if (state.journey == null) "Start today’s journey" else "Continue today’s journey",
                        onClick = { viewModel.start(onJourney) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(if (state.journey == null) Icons.Rounded.TravelExplore else Icons.Rounded.AutoStories, contentDescription = null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(onSettings: () -> Unit) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text("Pluck", style = MaterialTheme.typography.headlineLarge)
            Text("Your day, retold.", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.Settings, contentDescription = "Open settings") }
    }
}

@Composable
private fun HeroIllustration() {
    Surface(
        modifier = Modifier.fillMaxWidth().height(184.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(108.dp).rotate(-8f)) {}
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(108.dp).rotate(8f)) {}
            Icon(Icons.Rounded.AutoStories, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun JourneySummaryCard(hasJourney: Boolean) {
    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            StatusPill(if (hasJourney) "Journey in progress" else "Ready when you are", active = hasJourney)
            Spacer(Modifier.height(16.dp))
            Text(if (hasJourney) "Today’s story is taking shape." else "Every ordinary place can become a scene.", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(if (hasJourney) "Add the next place when you arrive." else "Start with the first place you want to remember.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
