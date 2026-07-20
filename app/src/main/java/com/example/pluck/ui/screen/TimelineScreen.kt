package com.example.pluck.ui.screen

import android.graphics.Paint
import android.graphics.Typeface
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.pluck.domain.model.JourneyPhoto
import com.example.pluck.ui.components.AnimatedPrimaryButton
import com.example.pluck.ui.components.EmptyState
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
    val floatingBarClearance = LocalFloatingNavigationBarClearance.current
    val haptics = rememberPluckHaptics()
    val journey = state.journey
    // The normal route only represents today's active journey. While Room is loading it,
    // keep that route actionable instead of briefly presenting an archive empty state.
    val canAddPlaces = !readOnly && (journey == null || journey.date == LocalDate.now().toString())
    val hasStoryAction = state.story != null || state.photos.size >= 2
    if (!readOnly) ObserveFloatingNavigationScroll(listState)
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
            AnimatedVisibility(hasStoryAction, enter = fadeIn() + slideInVertically { it / 2 }) {
                // The app-level navigation floats above this action, so root-provided clearance
                // keeps both controls comfortably reachable under gesture navigation.
                Column(Modifier.padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = floatingBarClearance)) {
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = floatingBarClearance + 24.dp)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 8.dp,
                    bottom = if (hasStoryAction) 24.dp else floatingBarClearance + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                stickyHeader {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (readOnly) "Captured places" else "Today", style = MaterialTheme.typography.headlineLarge)
                        StatusPill("${state.photos.size} ${if (state.photos.size == 1) "place" else "places"}")
                    }
                }
                item(key = "private_route") {
                    JourneyRouteCard(photos = state.photos)
                }
                items(state.photos, key = { it.id }) { photo -> TimelineItem(photo, canDelete = canAddPlaces, onDelete = { viewModel.delete(photo) }) }
                item { if (state.photos.size == 1 && state.story == null) Text("One more place unlocks story generation.", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)) }
            }
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
    val hasRoute = routePoints.size >= 2

    ExpressiveCard(
        modifier = Modifier.semantics {
            contentDescription = if (hasRoute) {
                "Private route sketch with ${routePoints.size} located places in capture order."
            } else {
                "Private route unavailable because fewer than two places have location data."
            }
        }
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = MaterialTheme.shapes.medium,
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
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Private route", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "A local sketch, never a live map",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                StatusPill(if (hasRoute) "On device" else "Unavailable", active = hasRoute)
            }

            Spacer(Modifier.height(16.dp))
            if (hasRoute) {
                LocalRouteCanvas(
                    points = routePoints,
                    modifier = Modifier.fillMaxWidth().height(208.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    RouteLegend(label = "Start", order = routePoints.first().order)
                    RouteLegend(label = "Finish", order = routePoints.last().order, alignEnd = true)
                }
                if (missingLocationCount > 0) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "$missingLocationCount ${if (missingLocationCount == 1) "place was" else "places were"} captured without location, so ${if (missingLocationCount == 1) "it is" else "they are"} not shown on this route.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                RouteUnavailableState(
                    hasOneLocatedPlace = routePoints.size == 1,
                    totalPlaces = photos.size
                )
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
