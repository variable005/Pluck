package com.example.pluck.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pluck.domain.model.NovellaArc
import com.example.pluck.domain.model.StoryMood
import com.example.pluck.ui.components.AnimatedPrimaryButton
import com.example.pluck.ui.components.ExpressiveCard
import com.example.pluck.ui.components.LoadingView
import com.example.pluck.ui.components.PluckTopAppBar
import com.example.pluck.ui.components.StatusPill
import com.example.pluck.viewmodel.NovellaChapterDisplay
import com.example.pluck.viewmodel.NovellaComposerViewModel
import com.example.pluck.viewmodel.NovellaViewModel

/** A focused, no-chat editor for grouping consecutive days into a continuing fictional novella. */
@Composable
fun NovellaComposerScreen(
    onBack: () -> Unit,
    onCreated: (Long) -> Unit,
    viewModel: NovellaComposerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(topBar = { PluckTopAppBar("New novella", "Choose consecutive days for one continuing world", onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item("novella_intro") {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Column(Modifier.padding(22.dp)) {
                        Text("A story with memory", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(
                            "Generate each chapter in order and Pluck carries a compact fictional continuity hand-off forward. Your photos, route, and prompts remain private unless you choose a cloud provider.",
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.86f)
                        )
                    }
                }
            }
            item("novella_details") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Arc details", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::updateTitle,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Novella title") },
                        placeholder = { Text("For example, Seven Nights in Kyoto") },
                        singleLine = true
                    )
                    Text("Mood", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StoryMood.entries.take(3).forEach { mood ->
                            FilterChip(selected = state.mood == mood, onClick = { viewModel.updateMood(mood) }, label = { Text(mood.displayName) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StoryMood.entries.drop(3).forEach { mood ->
                            FilterChip(selected = state.mood == mood, onClick = { viewModel.updateMood(mood) }, label = { Text(mood.displayName) })
                        }
                    }
                    OutlinedTextField(
                        value = state.genre,
                        onValueChange = viewModel::updateGenre,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Genre") },
                        placeholder = { Text("Cyberpunk, noir, cozy mystery…") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = state.protagonistName,
                        onValueChange = viewModel::updateProtagonist,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Fictional protagonist") },
                        placeholder = { Text("For example, Mira") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = state.companionsText,
                        onValueChange = viewModel::updateCompanions,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Companions") },
                        placeholder = { Text("Max the dog, Ishan") },
                        supportingText = { Text("Comma-separated fictional companions") }
                    )
                }
            }
            item("novella_journey_heading") {
                Column {
                    Text("Choose consecutive daily journeys", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${state.selectedJourneyIds.size} selected · at least two are needed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(state.journeys, key = { it.journey.id }) { journey ->
                val selected = journey.journey.id in state.selectedJourneyIds
                ExpressiveCard(
                    onClick = { viewModel.toggleJourney(journey.journey.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.AutoStories,
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text(journey.journey.date, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${journey.photoCount} places · ${journey.story?.title ?: "Story not generated yet"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        StatusPill(if (selected) "Selected" else "Add")
                    }
                }
            }
            item("novella_create") {
                Column {
                    state.error?.let { error ->
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                Text(error, Modifier.padding(start = 10.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    AnimatedPrimaryButton(
                        text = if (state.isSaving) "Creating novella…" else "Create novella",
                        onClick = { viewModel.create(onCreated) },
                        enabled = state.canCreate,
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(Icons.Rounded.AutoStories, contentDescription = null) }
                    )
                }
            }
        }
    }
}

/** Shows each daily chapter and makes stale continuity explicit instead of quietly misleading. */
@Composable
fun NovellaScreen(
    onBack: () -> Unit,
    onOpenJourney: (Long) -> Unit,
    onOpenStory: (Long) -> Unit,
    viewModel: NovellaViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val detail = state.detail
    Scaffold(topBar = { PluckTopAppBar("Travelogue novella", "A fictional world carried across days", onBack) }) { padding ->
        when {
            state.isLoading -> LoadingView("Opening your novella…", Modifier.fillMaxSize().padding(padding))
            detail == null -> LoadingView("This novella is no longer available.", Modifier.fillMaxSize().padding(padding))
            else -> NovellaReader(
                arc = detail.arc,
                chapters = state.chapters,
                onOpenJourney = onOpenJourney,
                onOpenStory = onOpenStory,
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        }
    }
}

@Composable
private fun NovellaReader(
    arc: NovellaArc,
    chapters: List<NovellaChapterDisplay>,
    onOpenJourney: (Long) -> Unit,
    onOpenStory: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.widthIn(max = 760.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item("arc_hero") {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.padding(24.dp)) {
                    Text(arc.title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        "${arc.startDate} — ${arc.endDate} · ${chapters.size} chapters",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                    )
                    arc.creativeSettings.genre?.let { genre ->
                        StatusPill(genre)
                    }
                    Text(
                        "Generate chapters in order. If an earlier chapter changes, later chapters are marked stale until their continuity is refreshed.",
                        modifier = Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                    )
                }
            }
        }
        items(chapters, key = { it.chapter.journeyId }) { display ->
            val chapter = display.chapter
            val title = display.journey?.story?.title ?: display.journey?.journey?.date ?: "Daily journey"
            val status = when {
                chapter.isStale -> "Needs continuity refresh"
                chapter.storyId != null -> "Chapter ready"
                else -> "Ready to generate"
            }
            ExpressiveCard(
                onClick = {
                    if (chapter.storyId != null && !chapter.isStale) onOpenStory(chapter.journeyId)
                    else onOpenJourney(chapter.journeyId)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = if (chapter.isStale) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Text(chapter.chapterIndex.toString(), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text("Chapter ${chapter.chapterIndex}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        StatusPill(status, active = chapter.storyId != null && !chapter.isStale)
                    }
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Open chapter", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
