package com.example.pluck.ui.screen

import android.content.Context
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
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
import com.example.pluck.domain.model.NovellaArc
import com.example.pluck.domain.export.BookExportFormat
import com.example.pluck.ui.components.EmptyState
import com.example.pluck.ui.components.ExpressiveCard
import com.example.pluck.ui.components.LoadingView
import com.example.pluck.ui.components.LocalFloatingNavigationBarClearance
import com.example.pluck.ui.components.ObserveFloatingNavigationScroll
import com.example.pluck.ui.components.PluckTopAppBar
import com.example.pluck.ui.components.StatusPill
import com.example.pluck.viewmodel.LibraryViewModel
import com.example.pluck.viewmodel.BookExportUiState
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
    onCreateNovella: () -> Unit,
    onOpenNovella: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showMonthExport by rememberSaveable { mutableStateOf(false) }
    var pendingMonth by rememberSaveable { mutableStateOf<String?>(null) }
    val pdfExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BookExportFormat.PDF.mimeType)) { uri ->
        pendingMonth?.let { month -> if (uri != null) viewModel.exportMonth(month, BookExportFormat.PDF, uri) }
    }
    val epubExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BookExportFormat.EPUB.mimeType)) { uri ->
        pendingMonth?.let { month -> if (uri != null) viewModel.exportMonth(month, BookExportFormat.EPUB, uri) }
    }
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
                arcs = state.arcs,
                export = state.export,
                onOpenJourney = onOpenJourney,
                onOpenStory = onOpenStory,
                onCreateNovella = onCreateNovella,
                onOpenNovella = onOpenNovella,
                onShowMonthExport = { showMonthExport = true },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        }
    }

    if (showMonthExport) {
        MonthExportDialog(
            journeys = state.journeys,
            onDismiss = { showMonthExport = false },
            onExport = { month, format ->
                showMonthExport = false
                pendingMonth = month
                when (format) {
                    BookExportFormat.PDF -> pdfExport.launch("Pluck-${month}.pdf")
                    BookExportFormat.EPUB -> epubExport.launch("Pluck-${month}.epub")
                }
            }
        )
    }

    if (state.export.completedMessage != null || state.export.errorMessage != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearExportMessage,
            title = { Text(if (state.export.errorMessage != null) "Export paused" else "Book saved") },
            text = { Text(state.export.errorMessage ?: state.export.completedMessage.orEmpty()) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = viewModel::clearExportMessage) { Text("Done") } }
        )
    }
}

@Composable
private fun JourneyLibrary(
    journeys: List<JourneyLibraryItem>,
    arcs: List<NovellaArc>,
    export: BookExportUiState,
    onOpenJourney: (Long) -> Unit,
    onOpenStory: (Long) -> Unit,
    onCreateNovella: () -> Unit,
    onOpenNovella: (Long) -> Unit,
    onShowMonthExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var grouping by rememberSaveable { mutableStateOf(LibraryGrouping.WEEK) }
    val sections = remember(journeys, grouping) { journeys.groupedBy(grouping) }
    val listState = rememberLazyListState()
    val floatingBarClearance = LocalFloatingNavigationBarClearance.current
    ObserveFloatingNavigationScroll(listState)
    Box(modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = floatingBarClearance + 24.dp),
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
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        androidx.compose.material3.TextButton(onClick = onShowMonthExport) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Export a month", modifier = Modifier.padding(start = 6.dp))
                        }
                        androidx.compose.material3.TextButton(onClick = onCreateNovella) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("New novella", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                    if (export.isExporting) {
                        StatusPill(export.progressLabel ?: "Creating your private book…")
                    }
                }
            }
            if (arcs.isNotEmpty()) {
                item(key = "novella_heading") {
                    Text(
                        "Travelogue novellas",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(arcs, key = { it.id }) { arc ->
                    NovellaLibraryCard(arc = arc, onOpen = { onOpenNovella(arc.id) })
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
                    story.genre?.let { genre -> StatusPill(genre) }
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

@Composable
private fun NovellaLibraryCard(arc: NovellaArc, onOpen: () -> Unit) {
    ExpressiveCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.AutoStories,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(arc.title, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                Text(
                    "${arc.startDate.monthLabel()} · ${arc.mood.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                arc.creativeSettings.genre?.let { genre -> StatusPill(genre) }
            }
            Icon(
                imageVector = Icons.Rounded.ArrowOutward,
                contentDescription = "Open novella",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun MonthExportDialog(
    journeys: List<JourneyLibraryItem>,
    onDismiss: () -> Unit,
    onExport: (String, BookExportFormat) -> Unit
) {
    val months = remember(journeys) {
        journeys
            .filter { it.story != null }
            .groupBy { it.journey.date.take(7) }
            .mapValues { (_, items) -> items.size }
            .toList()
            .sortedByDescending { it.first }
    }
    var selectedMonth by rememberSaveable(months) { mutableStateOf(months.firstOrNull()?.first.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export a month") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Pluck uses the latest saved version of each daily story in the month. The book is created locally; photos are re-encoded without EXIF metadata.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (months.isEmpty()) {
                    Text("Save at least one story before creating a monthly book.")
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(months, key = { it.first }) { (month, count) ->
                            FilterChip(
                                selected = selectedMonth == month,
                                onClick = { selectedMonth = month },
                                label = { Text("${month.toMonthLabel()} · $count") }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                androidx.compose.material3.TextButton(
                    enabled = selectedMonth.isNotBlank(),
                    onClick = { onExport(selectedMonth, BookExportFormat.PDF) }
                ) { Text("PDF") }
                androidx.compose.material3.TextButton(
                    enabled = selectedMonth.isNotBlank(),
                    onClick = { onExport(selectedMonth, BookExportFormat.EPUB) }
                ) { Text("EPUB") }
            }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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

private fun String.toMonthLabel(): String = runCatching {
    LocalDate.parse("${this}-01").format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
}.getOrDefault(this)

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
