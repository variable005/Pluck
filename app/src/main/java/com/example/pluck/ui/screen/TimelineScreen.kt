package com.example.pluck.ui.screen

import android.graphics.Paint
import android.graphics.Typeface
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.pluck.domain.model.JourneyPhoto
import com.example.pluck.domain.model.Story
import com.example.pluck.ui.components.AnimatedPrimaryButton
import com.example.pluck.ui.components.ExpressiveCard
import com.example.pluck.ui.components.LocalFloatingNavigationBarClearance
import com.example.pluck.ui.components.ObserveFloatingNavigationScroll
import com.example.pluck.ui.components.PluckHapticEvent
import com.example.pluck.ui.components.PluckTopAppBar
import com.example.pluck.ui.components.StatusPill
import com.example.pluck.ui.components.rememberPluckHaptics
import com.example.pluck.viewmodel.TimelineViewModel
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private val JourneyActionDockHeight = 132.dp

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
    val floatingBarClearance = LocalFloatingNavigationBarClearance.current
    val haptics = rememberPluckHaptics()
    val journey = state.journey
    var photoPendingDeletion by remember { mutableStateOf<JourneyPhoto?>(null) }
    // The normal route only represents today's active journey. While Room is loading it,
    // keep that route actionable instead of briefly presenting an archive empty state.
    val canAddPlaces = !readOnly && (journey == null || journey.date == LocalDate.now().toString())
    val showActionDock = (!readOnly && state.photos.isNotEmpty()) || (readOnly && state.story != null)
    val hasPrivateRoute = remember(state.photos) { state.photos.toRoutePoints().size >= 2 }
    val showStickyPlacesHeader by remember(hasPrivateRoute) {
        derivedStateOf {
            val placesHeaderIndex = if (hasPrivateRoute) 3 else 2
            listState.firstVisibleItemIndex > placesHeaderIndex ||
                (listState.firstVisibleItemIndex == placesHeaderIndex && listState.firstVisibleItemScrollOffset > 52)
        }
    }
    if (!readOnly) ObserveFloatingNavigationScroll(listState)
    Scaffold(
        topBar = {
            PluckTopAppBar(
                title = if (readOnly) "Saved journey" else "Journey",
                subtitle = if (readOnly) "A day worth revisiting" else "One place at a time",
                onBack = onBack
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.photos.isEmpty()) {
                JourneyEmptyState(
                    canAddPlaces = canAddPlaces,
                    onAction = if (canAddPlaces) ({ onCapture(viewModel.journeyId) }) else onBack,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = floatingBarClearance + 24.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxSize()
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 12.dp,
                        bottom = floatingBarClearance + if (showActionDock) JourneyActionDockHeight + 24.dp else 28.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(key = "journey_hero") {
                        JourneyHero(
                            date = journey?.date ?: LocalDate.now().toString(),
                            photos = state.photos,
                            story = state.story,
                            readOnly = readOnly
                        )
                    }
                    item(key = "journey_progress") {
                        JourneyProgressCard(
                            photoCount = state.photos.size,
                            story = state.story,
                            hasPrivateRoute = hasPrivateRoute
                        )
                    }
                    if (hasPrivateRoute) {
                        item(key = "private_route") {
                            JourneyRouteCard(photos = state.photos)
                        }
                    }
                    item(key = "places_heading") {
                        JourneyPlacesHeading(state.photos.size)
                    }
                    stickyHeader {
                        JourneyStickyPlacesHeader(
                            visible = showStickyPlacesHeader,
                            photoCount = state.photos.size
                        )
                    }
                    itemsIndexed(state.photos, key = { _, photo -> photo.id }) { index, photo ->
                        TimelineItem(
                            photo = photo,
                            order = index + 1,
                            total = state.photos.size,
                            isLast = index == state.photos.lastIndex,
                            canDelete = true,
                            showDeleteAction = !readOnly,
                            onDelete = { photoPendingDeletion = photo },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }

            if (canAddPlaces) {
                ExtendedFloatingActionButton(
                    text = { Text("Add place") },
                    icon = { Icon(Icons.Rounded.AddAPhoto, contentDescription = null) },
                    onClick = {
                        haptics.perform(PluckHapticEvent.PrimaryAction)
                        onCapture(viewModel.journeyId)
                    },
                    expanded = !listState.isScrollInProgress,
                    shape = MaterialTheme.shapes.large,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 20.dp,
                            bottom = floatingBarClearance + if (showActionDock) JourneyActionDockHeight + 16.dp else 16.dp
                        )
                )
            }

            AnimatedVisibility(
                visible = showActionDock,
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(start = 20.dp, end = 20.dp, bottom = floatingBarClearance),
                enter = fadeIn(tween(220)) + slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it / 2 } + scaleIn(initialScale = 0.96f),
                exit = fadeOut(tween(150)) + slideOutVertically(tween(180)) { it / 2 } + scaleOut(targetScale = 0.96f)
            ) {
                JourneyActionDock(
                    photoCount = state.photos.size,
                    story = state.story,
                    readOnly = readOnly,
                    onGenerateOrRead = { onStory(viewModel.journeyId) }
                )
            }

            photoPendingDeletion?.let { photo ->
                AlertDialog(
                    onDismissRequest = { photoPendingDeletion = null },
                    title = { Text("Remove this photo?") },
                    text = {
                        Text(
                            if (readOnly && state.story != null) {
                                "This permanently removes the photo and its saved location details. Your saved story text will remain unchanged."
                            } else {
                                "This permanently removes the photo and its saved location details from this journey. This can’t be undone."
                            }
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                haptics.perform(PluckHapticEvent.DestructiveAction)
                                viewModel.delete(photo)
                                photoPendingDeletion = null
                            }
                        ) {
                            Text("Remove")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { photoPendingDeletion = null }) {
                            Text("Keep photo")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun JourneyEmptyState(
    canAddPlaces: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = true,
        modifier = modifier,
        enter = fadeIn(tween(420)) + scaleIn(initialScale = 0.94f, animationSpec = tween(420, easing = FastOutSlowInEasing))
    ) {
        Column(
            modifier = Modifier.widthIn(max = 440.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(104.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Place,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = if (canAddPlaces) "Where will the story begin?" else "No places were saved",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (canAddPlaces) {
                    "Capture one meaningful place at a time. Pluck will keep the order, then turn it into fiction."
                } else {
                    "This saved journey did not keep any captured places."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            AnimatedPrimaryButton(
                text = if (canAddPlaces) "Capture first place" else "Back to library",
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                icon = { Icon(if (canAddPlaces) Icons.Rounded.AddAPhoto else Icons.Rounded.AutoStories, contentDescription = null) }
            )
        }
    }
}

@Composable
private fun JourneyHero(
    date: String,
    photos: List<JourneyPhoto>,
    story: Story?,
    readOnly: Boolean
) {
    val photoCount = photos.size
    val dayLabel = remember(date) { formatJourneyDate(date) }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 1.dp
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (readOnly) "A day, retold" else "Your story is taking shape",
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        text = dayLabel,
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                    )
                }
                Surface(
                    modifier = Modifier.size(60.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(photoCount.toString(), style = MaterialTheme.typography.headlineMedium)
                        Text(
                            if (photoCount == 1) "place" else "places",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
            Text(
                text = when {
                    story != null -> "Your fictional story is ready to read."
                    photoCount >= 2 -> "Every captured place now has a role to play."
                    else -> "One more place unlocks your first story."
                },
                modifier = Modifier.padding(top = 18.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.84f)
            )
            JourneyPhotoStrip(photos, modifier = Modifier.padding(top = 20.dp))
            if (story != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Story ready", style = MaterialTheme.typography.labelLarge)
                        Text(
                            story.title,
                            modifier = Modifier.padding(top = 3.dp),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JourneyPhotoStrip(photos: List<JourneyPhoto>, modifier: Modifier = Modifier) {
    if (photos.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        photos.take(4).forEach { photo ->
            AsyncImage(
                model = File(photo.imagePath),
                contentDescription = null,
                modifier = Modifier
                    .size(60.dp)
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop
            )
        }
        if (photos.size > 4) {
            Surface(
                modifier = Modifier.size(60.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("+${photos.size - 4}", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun JourneyProgressCard(photoCount: Int, story: Story?, hasPrivateRoute: Boolean) {
    val ready = photoCount >= 2
    val title = when {
        story != null -> "Story saved"
        ready -> "Ready for the reveal"
        else -> "One more place unlocks a story"
    }
    val body = when {
        story != null -> "You can return to this version anytime from your library."
        ready -> "Pluck can now connect every place into one continuous fictional story."
        else -> "Keep moving. Your next capture completes the minimum story sequence."
    }
    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = MaterialTheme.shapes.large,
                    color = if (ready || story != null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (ready || story != null) Icons.Rounded.AutoStories else Icons.Rounded.Place,
                            contentDescription = null,
                            tint = if (ready || story != null) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (story == null) {
                LinearProgressIndicator(
                    progress = { (photoCount / 2f).coerceAtMost(1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                Text(
                    text = "$photoCount of 2 places needed to generate",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!hasPrivateRoute && photoCount >= 2) {
                HorizontalDivider()
                Text(
                    "Location data stays private. Capture two places with location enabled to see a local route sketch.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun JourneyPlacesHeading(photoCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("Captured places", style = MaterialTheme.typography.titleLarge)
            Text("The order becomes the story", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StatusPill("$photoCount ${if (photoCount == 1) "place" else "places"}")
    }
}

@Composable
private fun JourneyStickyPlacesHeader(visible: Boolean, photoCount: Int) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 3 },
        exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 3 }
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Captured places", style = MaterialTheme.typography.titleMedium)
                StatusPill("$photoCount")
            }
        }
    }
}

@Composable
private fun JourneyActionDock(
    photoCount: Int,
    story: Story?,
    readOnly: Boolean,
    onGenerateOrRead: () -> Unit
) {
    val ready = story != null || photoCount >= 2
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 14.dp
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AnimatedContent(targetState = Triple(story != null, ready, readOnly), label = "journeyDockCopy") { (hasStory, isReady, savedJourney) ->
                Column {
                    Text(
                        text = when {
                            hasStory -> "Your story is ready"
                            isReady -> "Your journey is ready to become fiction"
                            else -> "One more place unlocks story generation"
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = when {
                            hasStory -> "Open the saved story whenever you are ready."
                            isReady -> "Choose a mood on the next screen, then let Pluck connect the day."
                            savedJourney -> "This journey needs more captured places before it can become a story."
                            else -> "The Generate Story button will come alive after your next capture."
                        },
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            AnimatedPrimaryButton(
                text = if (story != null) "Read your story" else "Generate your story",
                onClick = onGenerateOrRead,
                enabled = ready,
                modifier = Modifier.fillMaxWidth(),
                icon = { Icon(Icons.Rounded.AutoStories, contentDescription = null) }
            )
        }
    }
}

/**
 * A deliberately provider-free route sketch. Coordinates are normalized into the available
 * canvas rather than sent to a mapping service, so this view works offline and never reveals
 * journey data to a third party.
 */
@Composable
private fun JourneyRouteCard(photos: List<JourneyPhoto>) {
    val routePoints = remember(photos) { photos.toRoutePoints() }
    val missingLocationCount = photos.size - routePoints.size
    if (routePoints.size < 2) return
    val haptics = rememberPluckHaptics()
    var expanded by rememberSaveable(routePoints.size) { mutableStateOf(false) }

    ExpressiveCard(
        onClick = {
            haptics.perform(PluckHapticEvent.Navigation)
            expanded = !expanded
        },
        modifier = Modifier.animateContentSize()
    ) {
            Column(Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Route,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("Private route", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${routePoints.size} located places · only on this device",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    StatusPill(if (expanded) "Collapse" else "Expand")
                }

                AnimatedContent(targetState = expanded, label = "routePreviewSize") { showFullRoute ->
                    Column {
                        LocalRouteCanvas(
                            points = routePoints,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (showFullRoute) 208.dp else 128.dp)
                                .padding(top = 16.dp)
                        )
                        if (showFullRoute) {
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                RouteLegend(label = "Start", order = routePoints.first().order)
                                RouteLegend(label = "Finish", order = routePoints.last().order, alignEnd = true)
                            }
                            if (missingLocationCount > 0) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "$missingLocationCount ${if (missingLocationCount == 1) "place was" else "places were"} captured without location and is not shown here.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun RouteLegend(label: String, order: Int, alignEnd: Boolean = false) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Place $order", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun RouteUnavailableState(hasOneLocatedPlace: Boolean, totalPlaces: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Route will appear here", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    hasOneLocatedPlace -> "Capture one more place with location enabled to connect your route."
                    totalPlaces <= 1 -> "Capture places with location enabled and Pluck will draw their order privately on this device."
                    else -> "Location was unavailable when these places were captured. Future captures with location enabled can draw a private route."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LocalRouteCanvas(points: List<RoutePoint>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val description = "Route visits ${points.joinToString { "place ${it.order}" }} in capture order."
    Canvas(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(colors.secondaryContainer.copy(alpha = 0.48f))
            .semantics { contentDescription = description }
    ) {
        val horizontalInset = 30.dp.toPx()
        val verticalInset = 28.dp.toPx()
        val drawableWidth = (size.width - horizontalInset * 2).coerceAtLeast(1f)
        val drawableHeight = (size.height - verticalInset * 2).coerceAtLeast(1f)
        val minLongitude = points.minOf { it.longitude }
        val maxLongitude = points.maxOf { it.longitude }
        val minLatitude = points.minOf { it.latitude }
        val maxLatitude = points.maxOf { it.latitude }
        val longitudeSpan = (maxLongitude - minLongitude).takeIf { it > 0.000_001 } ?: 0.0
        val latitudeSpan = (maxLatitude - minLatitude).takeIf { it > 0.000_001 } ?: 0.0
        val pinRadius = 15.dp.toPx()

        val canvasPoints = points.mapIndexed { index, point ->
            val x = if (longitudeSpan == 0.0) {
                size.width / 2f
            } else {
                horizontalInset + (((point.longitude - minLongitude) / longitudeSpan) * drawableWidth).toFloat()
            }
            val y = if (latitudeSpan == 0.0) {
                // Preserve capture order even when places have the same latitude.
                verticalInset + (drawableHeight * index / (points.lastIndex.coerceAtLeast(1))).toFloat()
            } else {
                verticalInset + (((maxLatitude - point.latitude) / latitudeSpan) * drawableHeight).toFloat()
            }
            RouteCanvasPoint(point = point, x = x, y = y)
        }

        canvasPoints.zipWithNext().forEach { (from, to) ->
            drawLine(
                color = colors.primary.copy(alpha = 0.52f),
                start = androidx.compose.ui.geometry.Offset(from.x, from.y),
                end = androidx.compose.ui.geometry.Offset(to.x, to.y),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        canvasPoints.forEachIndexed { index, point ->
            val isStart = index == 0
            val isEnd = index == canvasPoints.lastIndex
            val fill = when {
                isStart -> colors.tertiary
                isEnd -> colors.primary
                else -> colors.secondary
            }
            val contentColor = when {
                isStart -> colors.onTertiary
                isEnd -> colors.onPrimary
                else -> colors.onSecondary
            }
            drawCircle(
                color = colors.surface.copy(alpha = 0.84f),
                radius = pinRadius + 4.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(point.x, point.y)
            )
            drawCircle(
                color = fill,
                radius = pinRadius,
                center = androidx.compose.ui.geometry.Offset(point.x, point.y)
            )
            val numberPaint = routeNumberPaint(pinRadius, contentColor.toArgb())
            drawContext.canvas.nativeCanvas.drawText(
                point.point.order.toString(),
                point.x,
                point.y - ((numberPaint.ascent() + numberPaint.descent()) / 2f),
                numberPaint
            )
        }
    }
}

private fun routeNumberPaint(radius: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = Paint.Align.CENTER
    textSize = radius * 1.12f
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    this.color = color
}

private fun List<JourneyPhoto>.toRoutePoints(): List<RoutePoint> =
    sortedWith(compareBy<JourneyPhoto> { it.timestamp }.thenBy { it.id })
        .mapIndexedNotNull { index, photo ->
            val latitude = photo.latitude ?: return@mapIndexedNotNull null
            val longitude = photo.longitude ?: return@mapIndexedNotNull null
            if (latitude.isValidLatitude() && longitude.isValidLongitude()) {
                RoutePoint(order = index + 1, latitude = latitude, longitude = longitude)
            } else {
                null
            }
        }

private fun Double.isValidLatitude(): Boolean = isFinite() && this in -90.0..90.0

private fun Double.isValidLongitude(): Boolean = isFinite() && this in -180.0..180.0

private data class RoutePoint(
    val order: Int,
    val latitude: Double,
    val longitude: Double
)

private data class RouteCanvasPoint(
    val point: RoutePoint,
    val x: Float,
    val y: Float
)

@Composable
private fun TimelineItem(
    photo: JourneyPhoto,
    order: Int,
    total: Int,
    isLast: Boolean,
    canDelete: Boolean,
    showDeleteAction: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val time = remember(photo.timestamp) { DateFormat.getTimeFormat(context).format(Date(photo.timestamp)) }
    val nodeColor = when {
        order == 1 -> MaterialTheme.colorScheme.tertiary
        isLast -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
    val nodeContent = when {
        order == 1 -> MaterialTheme.colorScheme.onTertiary
        isLast -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSecondary
    }
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(280)) + scaleIn(initialScale = 0.97f) + slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it / 8 }
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .semantics {
                    contentDescription = buildString {
                        append("Place $order of $total, captured at $time. ${photo.address ?: "Place name unavailable"}")
                        if (canDelete) append(". Long press to remove this photo.")
                    }
                },
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier
                    .width(38.dp)
                    .fillMaxHeight()
                    .padding(end = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = nodeColor
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(order.toString(), style = MaterialTheme.typography.labelLarge, color = nodeContent)
                    }
                }
                if (!isLast) {
                    Box(
                        Modifier
                            .padding(top = 8.dp)
                            .width(2.dp)
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }
            ExpressiveCard(
                onLongClick = if (canDelete) onDelete else null,
                modifier = Modifier.weight(1f)
            ) {
                Column {
                    Box(Modifier.fillMaxWidth().height(178.dp)) {
                        AsyncImage(
                            model = File(photo.imagePath),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.extraLarge),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill("Place ${order.toString().padStart(2, '0')}")
                            Text(
                                text = "Captured at $time",
                                modifier = Modifier.padding(start = 10.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            photo.address ?: "A place without a label",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    AnimatedVisibility(showDeleteAction) {
                        Column {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = onDelete) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Remove photo")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatJourneyDate(date: String): String = runCatching {
    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
}.getOrDefault(date)

@Composable
internal fun TextButtonBack(onBack: () -> Unit) { IconButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineLarge) } }
