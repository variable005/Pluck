package com.example.pluck.ui.screen

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pluck.ui.components.AnimatedPrimaryButton
import com.example.pluck.ui.components.PluckHapticEvent
import com.example.pluck.ui.components.PluckTopAppBar
import com.example.pluck.ui.components.rememberPluckHaptics
import com.example.pluck.domain.model.StoryMood
import com.example.pluck.viewmodel.StoryViewModel
import kotlin.math.ceil

private val ReaderMaxWidth = 720.dp
private val ReaderActionDockHeight = 104.dp

/**
 * Presents a generated Pluck story in a calm, distraction-free reading surface.
 *
 * Story generation and persistence remain owned by [StoryViewModel]. This screen only maps its
 * state into the appropriate reader, generation, and recovery experiences.
 */
@Composable
fun StoryScreen(onBack: () -> Unit, viewModel: StoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptics = rememberPluckHaptics()
    val saveStory = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val story = state.story ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write("${story.title}\n\n${story.content}")
            }
        }
    }

    val story = state.story
    when {
        // Keep a previous story visible while a new version is being written. It makes
        // regeneration feel deliberate instead of replacing the reader with a blank wait state.
        story != null -> StoryReader(
            title = story.title,
            content = story.content,
            provider = story.provider.displayName,
            isRefreshing = state.generating,
            generationError = state.error,
            onBack = onBack,
            onRefresh = {
                haptics.perform(PluckHapticEvent.PrimaryAction)
                viewModel.generate()
            },
            onShare = {
                haptics.perform(PluckHapticEvent.PrimaryAction)
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${story.title}\n\n${story.content}")
                        },
                        "Share story"
                    )
                )
            },
            onSave = {
                haptics.perform(PluckHapticEvent.PrimaryAction)
                saveStory.launch("${story.title}.txt")
            }
        )

        state.generating -> StoryGenerationLoading(onBack = onBack)

        else -> StoryEmpty(
            onBack = onBack,
            // AnimatedPrimaryButton provides the primary-action haptic itself.
            onGenerate = viewModel::generate,
            error = state.error
        )
    }
}

@Composable
private fun StoryGenerationLoading(onBack: () -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Scaffold(topBar = { PluckTopAppBar("Creating your story", onBack = onBack) }) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(450)) + scaleIn(initialScale = 0.94f, animationSpec = tween(450, easing = FastOutSlowInEasing))
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 440.dp)
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier.size(112.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 2.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.AutoStories,
                                contentDescription = null,
                                modifier = Modifier.size(52.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(
                        text = "Turning today into a world of its own",
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Following the thread between every place you chose.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(28.dp))
                    StoryLoadingLine()
                }
            }
        }
    }
}

@Composable
private fun StoryLoadingLine() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "storyLoading")
    val progress by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.82f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(1_250, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "storyLoadingProgress"
    )
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    )
}

@Composable
private fun StoryEmpty(onBack: () -> Unit, onGenerate: (StoryMood) -> Unit, error: String?) {
    var visible by rememberSaveable(error) { mutableStateOf(false) }
    var mood by rememberSaveable { mutableStateOf(StoryMood.CINEMATIC) }
    LaunchedEffect(error) { visible = true }
    Scaffold(topBar = { PluckTopAppBar("Your story", onBack = onBack) }) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400)) + slideInVertically(animationSpec = tween(400, easing = FastOutSlowInEasing)) { it / 10 }
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .padding(horizontal = 28.dp, vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier.size(100.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = if (error == null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (error == null) Icons.Rounded.AutoStories else Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(46.dp),
                                tint = if (error == null) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(
                        text = if (error == null) "Ready for the reveal?" else "The story paused",
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = error ?: "Pluck will turn the places you captured into one continuous fictional story.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "Choose the mood",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "This shapes the voice and atmosphere of your story.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MoodPicker(
                        selected = mood,
                        onSelect = { mood = it },
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                    AnimatedPrimaryButton(
                        text = if (error == null) "Generate ${mood.displayName.lowercase()} story" else "Try again",
                        onClick = { onGenerate(mood) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(Icons.Rounded.AutoStories, contentDescription = null) }
                    )
                    androidx.compose.material3.TextButton(
                        onClick = onBack,
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        Text("Back to journey")
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodPicker(
    selected: StoryMood,
    onSelect: (StoryMood) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(StoryMood.entries, key = { it.name }) { mood ->
            FilterChip(
                selected = mood == selected,
                onClick = { onSelect(mood) },
                label = { Text(mood.displayName) }
            )
        }
    }
}

@Composable
private fun StoryReader(
    title: String,
    content: String,
    provider: String,
    isRefreshing: Boolean,
    generationError: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit
) {
    val scroll = rememberScrollState()
    val readingProgress by remember {
        derivedStateOf {
            if (scroll.maxValue == 0) 0f else (scroll.value.toFloat() / scroll.maxValue).coerceIn(0f, 1f)
        }
    }
    val displayedProgress by animateFloatAsState(
        targetValue = readingProgress,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "readingProgress"
    )
    val wordCount = remember(content) { content.wordCount() }
    val readingMinutes = remember(wordCount) { maxOf(1, ceil(wordCount / 210.0).toInt()) }
    val paragraphs = remember(content) { content.storyParagraphs() }
    val compactActions by remember { derivedStateOf { scroll.value > 84 } }
    var revealed by rememberSaveable(title) { mutableStateOf(false) }
    LaunchedEffect(title) { revealed = true }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val horizontalPadding = if (maxWidth >= 840.dp) 40.dp else 20.dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = horizontalPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(112.dp))
                AnimatedVisibility(
                    visible = revealed,
                    enter = fadeIn(tween(500)) + slideInVertically(animationSpec = tween(500, easing = FastOutSlowInEasing)) { it / 12 }
                ) {
                    StoryHero(
                        title = title,
                        provider = provider,
                        readingMinutes = readingMinutes,
                        modifier = Modifier
                            .widthIn(max = ReaderMaxWidth)
                            .fillMaxWidth()
                            .padding(top = 28.dp)
                    )
                }

                AnimatedVisibility(
                    visible = isRefreshing,
                    enter = fadeIn() + slideInVertically { -it / 3 },
                    exit = fadeOut() + slideOutVertically { -it / 3 },
                    modifier = Modifier
                        .widthIn(max = ReaderMaxWidth)
                        .fillMaxWidth()
                ) {
                    StoryRegeneratingNotice(modifier = Modifier.padding(top = 16.dp))
                }

                AnimatedVisibility(
                    visible = generationError != null,
                    enter = fadeIn() + slideInVertically { -it / 3 },
                    exit = fadeOut() + slideOutVertically { -it / 3 },
                    modifier = Modifier
                        .widthIn(max = ReaderMaxWidth)
                        .fillMaxWidth()
                ) {
                    generationError?.let { message ->
                        StoryReaderError(message, modifier = Modifier.padding(top = 16.dp))
                    }
                }

                StoryBody(
                    paragraphs = paragraphs,
                    modifier = Modifier
                        .widthIn(max = ReaderMaxWidth)
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = ReaderActionDockHeight + 36.dp)
                )
            }
        }

        FloatingReaderHeader(
            progress = displayedProgress,
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        )

        StoryActionDock(
            compact = compactActions,
            refreshing = isRefreshing,
            onRefresh = onRefresh,
            onShare = onShare,
            onSave = onSave,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun FloatingReaderHeader(progress: Float, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberPluckHaptics()
    val percentage = (progress * 100).toInt()
    Row(
        modifier = modifier
            .widthIn(max = ReaderMaxWidth)
            .fillMaxWidth()
            .semantics { contentDescription = "Your story, reading progress: $percentage percent" },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            tonalElevation = 6.dp,
            shadowElevation = 12.dp
        ) {
            IconButton(onClick = {
                haptics.perform(PluckHapticEvent.Navigation)
                onBack()
            }) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Navigate back")
            }
        }
        Surface(
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            tonalElevation = 6.dp,
            shadowElevation = 12.dp
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Your story", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (percentage == 0) "Begin reading" else "$percentage% read",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "$percentage%",
                        modifier = Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }
        }
    }
}

@Composable
private fun StoryHero(title: String, provider: String, readingMinutes: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.11f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.AutoStories,
                            contentDescription = null,
                            modifier = Modifier.size(21.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Text(
                    text = "A PLUCK ORIGINAL",
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
            Text(
                text = title,
                modifier = Modifier.padding(top = 22.dp),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "A fictional tale, shaped by the places you chose today.",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f)
            )
            Row(
                modifier = Modifier.padding(top = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StoryMetaPill(
                    icon = Icons.Rounded.Schedule,
                    text = "$readingMinutes min read",
                    contentDescription = "$readingMinutes minute reading time"
                )
                StoryMetaPill(
                    icon = Icons.Rounded.AutoStories,
                    text = provider,
                    contentDescription = "Generated with $provider"
                )
            }
        }
    }
}

@Composable
private fun StoryMetaPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    contentDescription: String
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f),
        modifier = Modifier.semantics { this.contentDescription = contentDescription }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = text,
                modifier = Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun StoryRegeneratingNotice(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoStories,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text("Writing a new version", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "Your current story stays here until the next one is ready.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                )
            }
        }
    }
}

@Composable
private fun StoryReaderError(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun StoryBody(paragraphs: List<String>, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp)) {
            Text(
                text = "THE STORY",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            if (paragraphs.isEmpty()) {
                Text(
                    text = "Your story will appear here when it is ready.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                paragraphs.forEachIndexed { index, paragraph ->
                    if (index == 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 24.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        )
                    } else if (index > 1) {
                        Spacer(Modifier.height(22.dp))
                    }
                    Text(
                        text = paragraph,
                        style = if (index == 0) {
                            MaterialTheme.typography.titleLarge.copy(lineHeight = 33.sp)
                        } else {
                            MaterialTheme.typography.bodyLarge.copy(lineHeight = 30.sp)
                        },
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("—", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "  End of this chapter  ",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("—", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun StoryActionDock(
    compact: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .widthIn(max = ReaderMaxWidth)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
        tonalElevation = 6.dp,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onRefresh,
                enabled = !refreshing,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                AnimatedContent(targetState = compact, label = "regenerateLabel") { isCompact ->
                    Text(
                        text = if (refreshing) "Writing…" else if (isCompact) "Again" else "Generate again",
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                }
            }
            ReaderIconAction(
                icon = Icons.Rounded.IosShare,
                label = "Share story",
                onClick = onShare
            )
            ReaderIconAction(
                icon = Icons.Rounded.SaveAlt,
                label = "Save story",
                onClick = onSave
            )
        }
    }
}

@Composable
private fun ReaderIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = label }
    ) {
        Icon(imageVector = icon, contentDescription = null)
    }
}

private fun String.storyParagraphs(): List<String> =
    split(Regex("\\n\\s*\\n"))
        .map(String::trim)
        .filter(String::isNotBlank)

private fun String.wordCount(): Int =
    trim().split(Regex("\\s+")).count(String::isNotBlank)
