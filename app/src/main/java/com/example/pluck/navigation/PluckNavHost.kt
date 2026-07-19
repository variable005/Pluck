package com.example.pluck.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
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
import com.example.pluck.ui.screen.CaptureScreen
import com.example.pluck.ui.screen.HomeScreen
import com.example.pluck.ui.screen.SettingsScreen
import com.example.pluck.ui.screen.StoryScreen
import com.example.pluck.ui.screen.TimelineScreen
import com.example.pluck.viewmodel.HomeViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories

private const val JOURNEY_ID = "journeyId"

@Composable
fun PluckNavHost() {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    val selected = when {
        route.startsWith("timeline") || route == "journey" -> MainDestination.JOURNEY
        route == "settings" -> MainDestination.SETTINGS
        else -> MainDestination.HOME
    }
    val barVisible = route !in setOf("capture/{$JOURNEY_ID}", "story/{$JOURNEY_ID}")
    val floatingBarState = remember { FloatingBarState() }
    LaunchedEffect(route) { floatingBarState.visible = barVisible }
    CompositionLocalProvider(LocalFloatingBarState provides floatingBarState) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        bottomBar = {
            FloatingNavigationBar(selected, onDestinationSelected = { destination ->
                if (destination.route != route) navController.navigate(destination.route) {
                    launchSingleTop = true
                    restoreState = true
                    popUpTo("home") { saveState = true }
                }
            }, visible = barVisible && floatingBarState.visible)
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) {
            composable("home") { HomeScreen(onJourney = { navController.navigate("timeline/$it") }, onSettings = { navController.navigate("settings") }) }
            composable("journey") { JourneyGateway(onJourney = { navController.navigate("timeline/$it") }) }
            composable("timeline/{$JOURNEY_ID}", arguments = listOf(navArgument(JOURNEY_ID) { type = NavType.LongType })) {
                TimelineScreen(onCapture = { navController.navigate("capture/$it") }, onStory = { navController.navigate("story/$it") }, onBack = { navController.navigate("home") { launchSingleTop = true } })
            }
            composable("capture/{$JOURNEY_ID}", arguments = listOf(navArgument(JOURNEY_ID) { type = NavType.LongType })) {
                CaptureScreen(onDone = { navController.popBackStack() }, onBack = { navController.popBackStack() })
            }
            composable("story/{$JOURNEY_ID}", arguments = listOf(navArgument(JOURNEY_ID) { type = NavType.LongType })) {
                StoryScreen(onBack = { navController.popBackStack() })
            }
            composable("settings") { SettingsScreen(onBack = { navController.navigate("home") { launchSingleTop = true } }) }
        }
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
