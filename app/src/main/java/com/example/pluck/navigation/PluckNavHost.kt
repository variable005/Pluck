package com.example.pluck.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.pluck.ui.components.EmptyState
import com.example.pluck.ui.components.FloatingNavigationBar
import com.example.pluck.ui.components.MainDestination
import com.example.pluck.ui.components.FloatingBarState
import com.example.pluck.ui.components.LocalFloatingBarState
import com.example.pluck.ui.components.LocalHapticMode
import com.example.pluck.ui.screen.CaptureScreen
import com.example.pluck.ui.screen.HomeScreen
import com.example.pluck.ui.screen.LibraryScreen
import com.example.pluck.ui.screen.SettingsScreen
import com.example.pluck.ui.screen.LocalAiScreen
import com.example.pluck.ui.screen.StoryScreen
import com.example.pluck.ui.screen.TimelineScreen
import com.example.pluck.viewmodel.HomeViewModel
import com.example.pluck.viewmodel.HapticSettingsViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories

private const val JOURNEY_ID = "journeyId"

@Composable
fun PluckNavHost() {
    val navController = rememberNavController()
    val hapticSettings: HapticSettingsViewModel = hiltViewModel()
    val hapticMode by hapticSettings.mode.collectAsState()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    val selected = when {
        route.startsWith("library") -> MainDestination.LIBRARY
        route.startsWith("timeline") || route == "journey" -> MainDestination.JOURNEY
        route == "settings" -> MainDestination.SETTINGS
        else -> MainDestination.HOME
    }
    val barVisible = route !in setOf("capture/{$JOURNEY_ID}", "story/{$JOURNEY_ID}")
    val floatingBarState = remember { FloatingBarState() }
    LaunchedEffect(route) { floatingBarState.visible = barVisible }
    CompositionLocalProvider(
        LocalFloatingBarState provides floatingBarState,
        LocalHapticMode provides hapticMode
    ) {
    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize(),
            // A restrained shared-axis motion gives destinations spatial continuity while
            // still respecting the system animator-duration scale.
            enterTransition = {
                fadeIn(animationSpec = tween(220)) +
                    slideInHorizontally(animationSpec = tween(280)) { it / 12 }
            },
            exitTransition = {
                fadeOut(animationSpec = tween(160)) +
                    slideOutHorizontally(animationSpec = tween(220)) { -it / 18 }
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(220)) +
                    slideInHorizontally(animationSpec = tween(280)) { -it / 12 }
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(160)) +
                    slideOutHorizontally(animationSpec = tween(220)) { it / 18 }
            }
        ) {
            composable("home") { HomeScreen(onJourney = { navController.navigate("timeline/$it") }, onSettings = { navController.navigate("settings") }) }
            composable("journey") { JourneyGateway(onJourney = { navController.navigate("timeline/$it") }) }
            composable("library") {
                LibraryScreen(
                    onOpenJourney = { journeyId -> navController.navigate("library/timeline/$journeyId") },
                    onOpenStory = { journeyId -> navController.navigate("story/$journeyId") },
                    onStartJourney = {
                        navController.navigate("journey") {
                            launchSingleTop = true
                            popUpTo("home") { saveState = true }
                        }
                    }
                )
            }
            composable("timeline/{$JOURNEY_ID}", arguments = listOf(navArgument(JOURNEY_ID) { type = NavType.LongType })) {
                TimelineScreen(onCapture = { navController.navigate("capture/$it") }, onStory = { navController.navigate("story/$it") }, onBack = { navController.navigate("home") { launchSingleTop = true } })
            }
            composable("library/timeline/{$JOURNEY_ID}", arguments = listOf(navArgument(JOURNEY_ID) { type = NavType.LongType })) {
                TimelineScreen(
                    onCapture = { },
                    onStory = { navController.navigate("story/$it") },
                    onBack = { navController.popBackStack() },
                    readOnly = true
                )
            }
            composable("capture/{$JOURNEY_ID}", arguments = listOf(navArgument(JOURNEY_ID) { type = NavType.LongType })) {
                CaptureScreen(onDone = { navController.popBackStack() }, onBack = { navController.popBackStack() })
            }
            composable("story/{$JOURNEY_ID}", arguments = listOf(navArgument(JOURNEY_ID) { type = NavType.LongType })) {
                StoryScreen(onBack = { navController.popBackStack() })
            }
            composable("settings") { SettingsScreen(onBack = { navController.navigate("home") { launchSingleTop = true } }, onLocalAi = { navController.navigate("local-ai") }) }
            composable("local-ai") { LocalAiScreen(onBack = { navController.popBackStack() }) }
        }
        FloatingNavigationBar(
            selected = selected,
            onDestinationSelected = { destination ->
                if (destination.route != route) navController.navigate(destination.route) {
                    launchSingleTop = true
                    restoreState = true
                    popUpTo("home") { saveState = true }
                }
            },
            visible = barVisible && floatingBarState.visible,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
    }
}

@Composable
private fun JourneyGateway(onJourney: (Long) -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val journey = state.journey
    if (journey == null) {
        EmptyState(Icons.Rounded.AutoStories, "A fresh page awaits", "Begin today’s journey and Pluck will remember each place you choose.", "Start journey", onAction = { viewModel.start(onJourney) }, modifier = Modifier.fillMaxSize())
    } else {
        LaunchedEffect(journey.id) { onJourney(journey.id) }
    }
}
