package com.example.pluck.ui.screen

import android.content.Context
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.pluck.domain.model.JourneyLibraryItem
import com.example.pluck.ui.components.EmptyState
import com.example.pluck.ui.components.ExpressiveCard
import com.example.pluck.ui.components.LoadingView
import com.example.pluck.ui.components.PluckTopAppBar
import com.example.pluck.ui.components.StatusPill
import com.example.pluck.viewmodel.LibraryViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private enum class LibraryGrouping(val label: String) {
    WEEK("Week"),
    MONTH("Month"),
    MOOD("Mood"),
    SEASON("Season")
}

private data class LibrarySection(val label: String, val items: List<JourneyLibraryItem>)

/**
 * A local, offline library of every journey Pluck has saved on this device.
 *
 * Selecting an entry opens its reader when a story exists, otherwise it opens the captured
 * journey so the user can revisit its places before generating a story.
 */
@Composable
fun LibraryScreen(
    onOpenJourney: (Long) -> Unit,
    onOpenStory: (Long) -> Unit,
    onStartJourney: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = { PluckTopAppBar("Your library", "Journeys and stories, kept on this device") }
    ) { padding ->
        when {
            state.isLoading -> LoadingView(
                label = "Finding your saved journeys…",
                modifier = Modifier.fillMaxSize().padding(padding)
            )

            state.journeys.isEmpty() -> EmptyState(
                icon = Icons.Rounded.AutoStories,
                title = "Your library is waiting",
                body = "Journeys and the stories they inspire will appear here for you to revisit.",
                action = "Start today’s journey",
                onAction = onStartJourney,
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)
            )

            else -> JourneyLibrary(
                journeys = state.journeys,
                onOpenJourney = onOpenJourney,
                onOpenStory = onOpenStory,
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        }
    }
}

@Composable
private fun JourneyLibrary(
    journeys: List<JourneyLibraryItem>,
    onOpenJourney: (Long) -> Unit,
    onOpenStory: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var grouping by rememberSaveable { mutableStateOf(LibraryGrouping.WEEK) }
    val sections = remember(journeys, grouping) { journeys.groupedBy(grouping) }
    Box(modifier) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "library_heading") {
                Column(Modifier.padding(bottom = 8.dp)) {
                    Text("Saved stories", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${journeys.size} ${if (journeys.size == 1) "journey" else "journeys"} saved locally",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(
                        modifier = Modifier.padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(LibraryGrouping.entries, key = { it.name }) { option ->
                            FilterChip(
                                selected = grouping == option,
                                onClick = { grouping = option },
                                label = { Text(option.label) }
                            )
                        }
                    }
                }
            }
            sections.forEach { section ->
                item(key = "section_${section.label}") {
                    Text(
                        section.label,
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(section.items, key = { it.journey.id }) { journey ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically { it / 8 }
                    ) {
                        JourneyLibraryCard(
                            item = journey,
                            onOpen = {
                                if (journey.story != null) onOpenStory(journey.journey.id)
                                else onOpenJourney(journey.journey.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JourneyLibraryCard(item: JourneyLibraryItem, onOpen: () -> Unit) {
    val context = LocalContext.current
    val story = item.story
    val actionLabel = if (story == null) "View captured places" else "Read story"
    val accessibilityLabel = buildString {
        append(formatJourneyDate(context, item.journey.date))
        append(", ${item.photoCount} ${if (item.photoCount == 1) "place" else "places"}")
        if (story != null) append(", story: ${story.title}")
        append(", $actionLabel")
    }
    ExpressiveCard(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = accessibilityLabel }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            JourneyCover(
                imagePath = item.coverImagePath,
                contentDescription = "First place from ${formatJourneyDate(context, item.journey.date)}"
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(formatJourneyDate(context, item.journey.date), style = MaterialTheme.typography.titleLarge)
                StatusPill(
                    text = if (story == null) "${item.photoCount} ${if (item.photoCount == 1) "place" else "places"}" else "Story saved",
                    active = story != null
                )
                Text(
                    text = story?.title ?: "A journey waiting for its story",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2
                )
                if (story != null) {
                    Text(
                        text = "Made with ${story.provider.displayName}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StatusPill(story.mood.displayName)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.Rounded.ArrowOutward,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 4.dp).size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private fun List<JourneyLibraryItem>.groupedBy(grouping: LibraryGrouping): List<LibrarySection> =
    groupBy { item ->
        when (grouping) {
            LibraryGrouping.WEEK -> item.journey.date.weekLabel()
            LibraryGrouping.MONTH -> item.journey.date.monthLabel()
            LibraryGrouping.MOOD -> item.story?.mood?.displayName ?: "Waiting for a story"
            LibraryGrouping.SEASON -> item.journey.date.seasonLabel()
        }
    }.map { (label, items) -> LibrarySection(label, items) }

private fun String.asLocalDate(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

private fun String.weekLabel(): String {
    val date = asLocalDate() ?: return this
    val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return "Week of ${weekStart.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))}"
}

private fun String.monthLabel(): String = asLocalDate()
    ?.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
    ?: this

private fun String.seasonLabel(): String {
    val date = asLocalDate() ?: return this
    val season = when (date.monthValue) {
        12, 1, 2 -> "Winter"
        3, 4, 5 -> "Spring"
        6, 7, 8 -> "Summer"
        else -> "Autumn"
    }
    return "$season ${date.year}"
}

@Composable
private fun JourneyCover(imagePath: String?, contentDescription: String) {
    Box(
        modifier = Modifier
            .size(104.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.PhotoLibrary,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
        imagePath?.let { path ->
            AsyncImage(
                model = File(path),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

private fun formatJourneyDate(context: Context, date: String): String {
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val parsedDate = runCatching { parser.parse(date) }.getOrNull() ?: return date
    return DateFormat.getMediumDateFormat(context).format(parsedDate)
}
