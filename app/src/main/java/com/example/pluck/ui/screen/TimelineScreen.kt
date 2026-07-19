package com.example.pluck.ui.screen

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.pluck.domain.model.JourneyPhoto
import com.example.pluck.ui.components.AnimatedPrimaryButton
import com.example.pluck.ui.components.EmptyState
import com.example.pluck.ui.components.ExpressiveCard
import com.example.pluck.ui.components.PluckHapticEvent
import com.example.pluck.ui.components.PluckTopAppBar
import com.example.pluck.ui.components.LocalFloatingBarState
import com.example.pluck.ui.components.StatusPill
import com.example.pluck.ui.components.rememberPluckHaptics
import com.example.pluck.viewmodel.TimelineViewModel
import java.io.File
import java.time.LocalDate
import java.util.Date

@Composable
fun TimelineScreen(
    onCapture: (Long) -> Unit,
    onStory: (Long) -> Unit,
    onBack: () -> Unit,
    readOnly: Boolean = false,
    viewModel: TimelineViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val floatingBar = LocalFloatingBarState.current
    val haptics = rememberPluckHaptics()
    val journey = state.journey
    // The normal route only represents today's active journey. While Room is loading it,
    // keep that route actionable instead of briefly presenting an archive empty state.
    val canAddPlaces = !readOnly && (journey == null || journey.date == LocalDate.now().toString())
    LaunchedEffect(listState.isScrollInProgress) { floatingBar.visible = !listState.isScrollInProgress }
    Scaffold(
        topBar = {
            PluckTopAppBar(
                title = if (readOnly) "Saved journey" else "Today’s journey",
                subtitle = if (readOnly) "A day worth revisiting" else "A place at a time",
                onBack = onBack
            )
        },
        floatingActionButton = {
            if (canAddPlaces) {
                ExtendedFloatingActionButton(
                    text = { Text("Add place") },
                    icon = { Icon(Icons.Rounded.AddAPhoto, contentDescription = null) },
                    onClick = {
                        haptics.perform(PluckHapticEvent.PrimaryAction)
                        onCapture(viewModel.journeyId)
                    },
                    expanded = !listState.isScrollInProgress,
                    shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(state.story != null || state.photos.size >= 2, enter = fadeIn() + slideInVertically { it / 2 }) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    AnimatedPrimaryButton(
                        text = if (state.story == null) "Generate your story" else "Read your story",
                        onClick = { onStory(viewModel.journeyId) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(Icons.Rounded.AutoStories, contentDescription = null) }
                    )
                }
            }
        }
    ) { padding ->
        if (state.photos.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Place,
                title = if (canAddPlaces) "Where will the story begin?" else "No places were saved",
                body = if (canAddPlaces) "Capture one photo at each place you visit. Pluck will hold the sequence for later." else "This journey did not keep any captured places.",
                action = if (canAddPlaces) "Capture first place" else "Back to library",
                onAction = if (canAddPlaces) ({ onCapture(viewModel.journeyId) }) else onBack,
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                stickyHeader {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (readOnly) "Captured places" else "Today", style = MaterialTheme.typography.headlineLarge)
                        StatusPill("${state.photos.size} ${if (state.photos.size == 1) "place" else "places"}")
                    }
                }
                items(state.photos, key = { it.id }) { photo -> TimelineItem(photo, canDelete = canAddPlaces, onDelete = { viewModel.delete(photo) }) }
                item { if (state.photos.size == 1 && state.story == null) Text("One more place unlocks story generation.", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)) }
            }
        }
    }
}

@Composable
private fun TimelineItem(photo: JourneyPhoto, canDelete: Boolean, onDelete: () -> Unit) {
    val haptics = rememberPluckHaptics()
    AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically { it / 5 }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 12.dp)) {
                androidx.compose.material3.Surface(Modifier.size(14.dp), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primary) {}
                androidx.compose.foundation.layout.Box(Modifier.width(2.dp).height(112.dp).padding(top = 8.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.outlineVariant))
            }
            ExpressiveCard(modifier = Modifier.weight(1f)) {
                Column {
                    AsyncImage(
                        model = File(photo.imagePath),
                        contentDescription = "Journey place captured at ${DateFormat.getTimeFormat(androidx.compose.ui.platform.LocalContext.current).format(Date(photo.timestamp))}",
                        modifier = Modifier.fillMaxWidth().height(176.dp).clip(MaterialTheme.shapes.large),
                        contentScale = ContentScale.Crop
                    )
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(DateFormat.getTimeFormat(androidx.compose.ui.platform.LocalContext.current).format(Date(photo.timestamp)), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(photo.address ?: "A place without a label", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                        if (canDelete) {
                            IconButton(
                                onClick = {
                                    haptics.perform(PluckHapticEvent.DestructiveAction)
                                    onDelete()
                                }
                            ) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete this place") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TextButtonBack(onBack: () -> Unit) { IconButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineLarge) } }
