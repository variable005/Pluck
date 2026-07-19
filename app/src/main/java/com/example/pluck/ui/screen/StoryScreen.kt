package com.example.pluck.ui.screen

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pluck.ui.components.AnimatedPrimaryButton
import com.example.pluck.ui.components.ExpressiveCard
import com.example.pluck.ui.components.LoadingView
import com.example.pluck.ui.components.PluckTopAppBar
import com.example.pluck.ui.components.StatusPill
import com.example.pluck.viewmodel.StoryViewModel
import kotlin.math.max

@Composable
fun StoryScreen(onBack: () -> Unit, viewModel: StoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val saveStory = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val story = state.story ?: return@rememberLauncherForActivityResult
        if (uri != null) context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write("${story.title}\n\n${story.content}") }
    }
    val story = state.story
    when {
        state.generating -> LoadingView("Weaving your journey into a story…", Modifier.fillMaxSize())
        story != null -> StoryReader(
            title = story.title,
            content = story.content,
            provider = story.provider.displayName,
            onBack = onBack,
            onRefresh = viewModel::generate,
            onShare = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${story.title}\n\n${story.content}") }, "Share Story")) },
            onSave = { saveStory.launch("${story.title}.txt") }
        )
        else -> StoryEmpty(onBack, viewModel::generate, state.error)
    }
}

@Composable
private fun StoryEmpty(onBack: () -> Unit, onGenerate: () -> Unit, error: String?) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.AutoStories, null, Modifier.size(52.dp), MaterialTheme.colorScheme.primary)
        Text("Ready for the reveal?", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 20.dp))
        Text(error ?: "Pluck will turn the places you captured into one continuous fictional story.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 28.dp))
        AnimatedPrimaryButton(if (error == null) "Generate story" else "Try again", onGenerate, Modifier.fillMaxWidth())
        androidx.compose.material3.TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Back to journey") }
    }
}

@Composable
private fun StoryReader(title: String, content: String, provider: String, onBack: () -> Unit, onRefresh: () -> Unit, onShare: () -> Unit, onSave: () -> Unit) {
    val scroll = rememberScrollState()
    val progress by remember { derivedStateOf { if (scroll.maxValue == 0) 0f else scroll.value.toFloat() / scroll.maxValue } }
    Column(Modifier.fillMaxSize()) {
        PluckTopAppBar("Your story", onBack = onBack)
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            AnimatedVisibility(true, enter = fadeIn() + slideInVertically { it / 8 }) {
                Column(Modifier.padding(top = 24.dp)) {
                    StatusPill("${max(1, content.trim().split(Regex("\\s+")).size / 220)} min read · $provider")
                    Text(title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 18.dp))
                    Text("Inspired by the places you chose today.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                }
            }
            ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    content.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }.forEach { paragraph -> Text(paragraph.trim(), style = MaterialTheme.typography.bodyLarge) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ReaderAction(Icons.Rounded.Refresh, "Generate again", onRefresh, Modifier.weight(1f))
                ReaderAction(Icons.Rounded.IosShare, "Share", onShare, Modifier.weight(1f))
                ReaderAction(Icons.Rounded.SaveAlt, "Save", onSave, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ReaderAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    FloatingActionButton(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.medium, containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null); Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge) }
    }
}
