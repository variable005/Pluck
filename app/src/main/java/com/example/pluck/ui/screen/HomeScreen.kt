package com.example.pluck.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pluck.ui.components.AnimatedPrimaryButton
import com.example.pluck.ui.components.LocalFloatingNavigationBarClearance
import com.example.pluck.ui.components.ObserveFloatingNavigationScroll
import com.example.pluck.ui.components.StatusPill
import com.example.pluck.viewmodel.HomeViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A minimal, expressive entry point for beginning or continuing today's journey. */
@Composable
fun HomeScreen(
    onJourney: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val hasJourney = state.journey != null
    val scrollState = rememberScrollState()
    val floatingBarClearance = LocalFloatingNavigationBarClearance.current
    var contentVisible by remember { mutableStateOf(false) }

    ObserveFloatingNavigationScroll(scrollState)
    LaunchedEffect(Unit) { contentVisible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = floatingBarClearance + 28.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            MinimalHomeHeader()

            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn() + slideInVertically(
                    initialOffsetY = { it / 10 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    HomeHeading(
                        preferredName = state.preferredName,
                        hasJourney = hasJourney
                    )
                    MinimalJourneyCard(
                        hasJourney = hasJourney,
                        onJourney = { viewModel.start(onJourney) }
                    )
                    TextButton(
                        onClick = { viewModel.openDemo(onJourney) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open demo journey")
                    }
                }
            }
        }
    }
}

@Composable
private fun MinimalHomeHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("PLUCK", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(
                text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HomeHeading(preferredName: String?, hasJourney: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedContent(
            targetState = Triple(preferredName, hasJourney, preferredName.isNullOrBlank()),
            label = "homeHeadline"
        ) { (name, inProgress, nameMissing) ->
            Text(
                text = when {
                    !nameMissing -> "Hello, $name."
                    inProgress -> "Your story is\nwaiting."
                    else -> "A place can be\nthe beginning."
                },
                style = MaterialTheme.typography.displaySmall
            )
        }
        Text(
            text = if (hasJourney) {
                "Add another place when you are ready."
            } else {
                "Collect the places that matter, then turn their path into fiction."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 460.dp)
        )
    }
}

@Composable
private fun MinimalJourneyCard(hasJourney: Boolean, onJourney: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = if (hasJourney) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (hasJourney) Icons.Rounded.AutoStories else Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = if (hasJourney) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Text(
                        text = if (hasJourney) "Today's journey" else "Today",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                StatusPill(if (hasJourney) "In progress" else "Ready", active = hasJourney)
            }

            Text(
                text = if (hasJourney) "The next photo\nbecomes a scene." else "Start with one\nplace you notice.",
                style = MaterialTheme.typography.headlineMedium
            )

            AnimatedPrimaryButton(
                text = if (hasJourney) "Add next place" else "Start journey",
                onClick = onJourney,
                modifier = Modifier.fillMaxWidth(),
                icon = {
                    Icon(
                        imageVector = if (hasJourney) Icons.Rounded.LocationOn else Icons.Rounded.TravelExplore,
                        contentDescription = null
                    )
                }
            )
        }
    }
}
