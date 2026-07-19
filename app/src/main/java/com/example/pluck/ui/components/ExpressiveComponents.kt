package com.example.pluck.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloat
import androidx.compose.material3.ExperimentalMaterial3Api

enum class MainDestination(val label: String, val route: String, val icon: ImageVector) {
    HOME("Home", "home", Icons.Rounded.Home),
    JOURNEY("Journey", "journey", Icons.Rounded.AutoStories),
    LIBRARY("Library", "library", Icons.Rounded.History),
    SETTINGS("Settings", "settings", Icons.Rounded.Settings)
}

class FloatingBarState(initiallyVisible: Boolean = true) {
    var visible by mutableStateOf(initiallyVisible)
}

val LocalFloatingBarState = staticCompositionLocalOf<FloatingBarState> { error("FloatingBarState is not provided") }

/** A neutral application canvas used by all destinations. */
@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier.background(colors.background),
        content = content
    )
}

@Composable
private fun glassBorder() = BorderStroke(
    width = 1.dp,
    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.11f)
)

@Composable
fun FloatingNavigationBar(selected: MainDestination, onDestinationSelected: (MainDestination) -> Unit, visible: Boolean, modifier: Modifier = Modifier) {
    val haptics = rememberPluckHaptics()
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + expandVertically(expandFrom = Alignment.Bottom),
        exit = fadeOut(tween(160)) + shrinkVertically(shrinkTowards = Alignment.Bottom),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = glassBorder(),
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                MainDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = selected == destination,
                        onClick = {
                            if (destination != selected) {
                                haptics.perform(PluckHapticEvent.Navigation)
                                onDestinationSelected(destination)
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = null,
                        alwaysShowLabel = false,
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ExpressiveCard(onClick: (() -> Unit)? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, animationSpec = tween(160, easing = FastOutSlowInEasing), label = "cardScale")
    Surface(
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }.then(
            if (onClick == null) Modifier else Modifier.clickable(interactionSource = source, role = Role.Button, onClick = onClick)
        ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = glassBorder(),
        tonalElevation = if (pressed) 4.dp else 1.dp,
        shadowElevation = if (pressed) 8.dp else 2.dp
    ) { Column(content = content) }
}

@Composable
fun AnimatedPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: (@Composable (() -> Unit))? = null) {
    val haptics = rememberPluckHaptics()
    Button(
        onClick = {
            haptics.perform(PluckHapticEvent.PrimaryAction)
            onClick()
        },
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
    ) {
        icon?.invoke()
        if (icon != null) Spacer(Modifier.width(8.dp))
        AnimatedContent(targetState = text, label = "buttonLabel") { Text(it, style = MaterialTheme.typography.labelLarge) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluckTopAppBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets
) {
    val haptics = rememberPluckHaptics()
    TopAppBar(
        modifier = modifier,
        title = {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = glassBorder(),
                tonalElevation = 2.dp,
                shadowElevation = 5.dp
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    AnimatedVisibility(subtitle != null) {
                        subtitle?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                Surface(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(48.dp)
                        .semantics { contentDescription = "Navigate back" },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = glassBorder(),
                    tonalElevation = 4.dp,
                    shadowElevation = 12.dp
                ) {
                    androidx.compose.material3.IconButton(
                        onClick = {
                            haptics.perform(PluckHapticEvent.Navigation)
                            onBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            }
        },
        actions = { actions?.invoke() },
        windowInsets = windowInsets,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(96.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        AnimatedPrimaryButton(action, onAction)
    }
}

@Composable
fun LoadingView(label: String, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "loading")
    val alpha by transition.animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "loadingAlpha")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(Modifier.size(64.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoStories, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
        Spacer(Modifier.height(16.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun StatusPill(text: String, active: Boolean = true) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        border = glassBorder()
    ) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
