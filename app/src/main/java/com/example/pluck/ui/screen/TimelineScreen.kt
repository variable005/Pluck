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
import com.example.pluck.ui.components.PluckTopAppBar
import com.example.pluck.ui.components.LocalFloatingBarState
import com.example.pluck.ui.components.StatusPill
import com.example.pluck.viewmodel.TimelineViewModel
import java.io.File
import java.util.Date

@Composable
fun TimelineScreen(onCapture: (Long) -> Unit, onStory: (Long) -> Unit, onBack: () -> Unit, viewModel: TimelineViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val floatingBar = LocalFloatingBarState.current
    LaunchedEffect(listState.isScrollInProgress) { floatingBar.visible = !listState.isScrollInProgress }
    Scaffold(
        topBar = { PluckTopAppBar("Today’s journey", "A place at a time", onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Add place") },
                icon = { Icon(Icons.Rounded.AddAPhoto, contentDescription = null) },
                onClick = { onCapture(viewModel.journeyId) },
                expanded = !listState.isScrollInProgress,
                shape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        },
        bottomBar = {
            AnimatedVisibility(state.photos.size >= 2, enter = fadeIn() + slideInVertically { it / 2 }) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    AnimatedPrimaryButton("Generate your story", { onStory(viewModel.journeyId) }, Modifier.fillMaxWidth(), icon = { Icon(Icons.Rounded.AutoStories, contentDescription = null) })
                }
            }
        }
    ) { padding ->
        if (state.photos.isEmpty()) {
            EmptyState(Icons.Rounded.Place, "Where will the story begin?", "Capture one photo at each place you visit. Pluck will hold the sequence for later.", "Capture first place", { onCapture(viewModel.journeyId) }, Modifier.fillMaxSize().padding(padding).padding(24.dp))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                stickyHeader {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Today", style = MaterialTheme.typography.headlineLarge)
                        StatusPill("${state.photos.size} ${if (state.photos.size == 1) "place" else "places"}")
                    }
                }
                items(state.photos, key = { it.id }) { photo -> TimelineItem(photo, onDelete = { viewModel.delete(photo) }) }
                item { if (state.photos.size == 1) Text("One more place unlocks story generation.", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)) }
            }
        }
    }
}

@Composable
private fun TimelineItem(photo: JourneyPhoto, onDelete: () -> Unit) {
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
                        IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete this place") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TextButtonBack(onBack: () -> Unit) { IconButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineLarge) } }
