package com.example.pluck.ui.screen

import android.content.Intent
import android.text.format.DateFormat
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.pluck.domain.export.BookExportFormat
import com.example.pluck.domain.model.JourneyPhoto
import com.example.pluck.domain.model.NovellaArc
import com.example.pluck.domain.model.StoryCreativeSettings
import com.example.pluck.ui.components.AnimatedPrimaryButton
import com.example.pluck.ui.components.PluckHapticEvent
import com.example.pluck.ui.components.PluckTopAppBar
import com.example.pluck.ui.components.rememberPluckHaptics
import com.example.pluck.domain.model.StoryMood
import com.example.pluck.domain.model.StorySceneReference
import com.example.pluck.domain.model.StoryVariation
import com.example.pluck.domain.narration.NarrationState
import com.example.pluck.ui.story.StoryShareRenderer
import com.example.pluck.ui.story.StoryShareRenderer.SocialCardFormat
import com.example.pluck.ui.story.StoryShareRenderer.SocialCardRequest
import com.example.pluck.viewmodel.StoryViewModel
import java.io.File
import java.util.Date
import kotlin.math.ceil
import kotlinx.coroutines.launch

private val ReaderMaxWidth = 720.dp
private val ReaderActionDockHeight = 104.dp

private enum class ReaderMode { FICTION, REALITY }

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
    val scope = rememberCoroutineScope()
    var showShareOptions by rememberSaveable { mutableStateOf(false) }
    var showExportOptions by rememberSaveable { mutableStateOf(false) }
    var renderingSocialCard by rememberSaveable { mutableStateOf(false) }
    val saveStory = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val story = state.story ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write("${story.title}\n\n${story.content}")
            }
        }
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BookExportFormat.PDF.mimeType)) { uri ->
        if (uri != null) viewModel.export(BookExportFormat.PDF, uri)
    }
    val saveEpub = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BookExportFormat.EPUB.mimeType)) { uri ->
        if (uri != null) viewModel.export(BookExportFormat.EPUB, uri)
    }

    val story = state.story
    when {
        // Keep a previous story visible while a new version is being written. It makes
        // regeneration feel deliberate instead of replacing the reader with a blank wait state.
        story != null -> StoryReader(
            title = story.title,
            content = story.content,
            provider = story.provider.displayName,
            mood = story.mood,
            createdAt = story.createdAt,
            photos = state.photos,
            scenes = state.scenes,
            creativeSettings = story.creativeSettings,
            arc = state.arc,
            narration = state.narration,
            export = state.export,
            isRefreshing = state.generating,
            generationError = state.error,
            onBack = onBack,
            onRefresh = { mood, variation, creativeSettings ->
                haptics.perform(PluckHapticEvent.PrimaryAction)
                viewModel.generate(mood, variation, creativeSettings)
            },
            onShare = {
                haptics.perform(PluckHapticEvent.PrimaryAction)
                showShareOptions = true
            },
            onExport = {
                haptics.perform(PluckHapticEvent.PrimaryAction)
                showExportOptions = true
            },
            onNarrationOpen = viewModel::initializeNarration,
            onNarrationPlay = viewModel::playNarration,
            onNarrationPause = viewModel::pauseNarration,
            onNarrationResume = viewModel::resumeNarration,
            onNarrationStop = viewModel::stopNarration,
            onDismissExportMessage = viewModel::clearExportMessage
        )

        state.generating -> StoryGenerationLoading(onBack = onBack)

        else -> StoryEmpty(
            onBack = onBack,
            // AnimatedPrimaryButton provides the primary-action haptic itself.
            onGenerate = { mood, creativeSettings -> viewModel.generate(mood = mood, creativeSettings = creativeSettings) },
            error = state.error,
            arc = state.arc
        )
    }

    if (showShareOptions && story != null) {
        StoryShareDialog(
            onDismiss = { showShareOptions = false },
            onShareText = {
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${story.title}\n\n${story.content}")
                        },
                        "Share story"
                    )
                )
                showShareOptions = false
            },
            socialRendering = renderingSocialCard,
            onShareSocialCard = { format ->
                renderingSocialCard = true
                scope.launch {
                    runCatching {
                        StoryShareRenderer.socialCardIntent(
                            context = context,
                            request = SocialCardRequest(
                                title = story.title,
                                content = story.content,
                                mood = story.mood,
                                createdAt = story.createdAt,
                                photos = state.photos,
                                format = format
                            )
                        )
                    }.onSuccess(context::startActivity)
                        .onFailure {
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "${story.title}\n\n${story.content}")
                                    },
                                    "Share story"
                                )
                            )
                        }
                    renderingSocialCard = false
                    showShareOptions = false
                }
            }
        )
    }

    if (showExportOptions && story != null) {
        StoryExportDialog(
            onDismiss = { showExportOptions = false },
            onSaveText = {
                saveStory.launch("${story.title}.txt")
                showExportOptions = false
            },
            onExportPdf = {
                savePdf.launch("${story.title}.pdf")
                showExportOptions = false
            },
            onExportEpub = {
                saveEpub.launch("${story.title}.epub")
                showExportOptions = false
            }
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
private fun StoryEmpty(
    onBack: () -> Unit,
    onGenerate: (StoryMood, StoryCreativeSettings) -> Unit,
    error: String?,
    arc: NovellaArc?
) {
    var visible by rememberSaveable(error) { mutableStateOf(false) }
    var mood by rememberSaveable { mutableStateOf(StoryMood.CINEMATIC) }
    var genre by rememberSaveable { mutableStateOf("") }
    var protagonist by rememberSaveable { mutableStateOf("") }
    var companions by rememberSaveable { mutableStateOf("") }
    var showCreativeDetails by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(error) { visible = true }
    LaunchedEffect(arc?.id) {
        arc?.let { novella ->
            mood = novella.mood
            genre = novella.creativeSettings.genre.orEmpty()
            protagonist = novella.creativeSettings.protagonistName.orEmpty()
            companions = novella.creativeSettings.companions.joinToString(", ")
        }
    }
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
                        text = if (arc == null) "Choose the mood" else "Novella direction",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (arc == null) {
                            "This shapes the voice and atmosphere of your story."
                        } else {
                            "Chapter ${arc.title} keeps its shared mood and fictional cast."
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MoodPicker(
                        selected = mood,
                        onSelect = { if (arc == null) mood = it },
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    if (arc == null) {
                        androidx.compose.material3.TextButton(
                            onClick = { showCreativeDetails = !showCreativeDetails },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (showCreativeDetails) "Hide character & genre" else "Add character & genre")
                        }
                        AnimatedVisibility(showCreativeDetails) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = genre,
                                    onValueChange = { genre = it.take(48) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Genre") },
                                    placeholder = { Text("Noir, cozy mystery, high fantasy…") },
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = protagonist,
                                    onValueChange = { protagonist = it.take(48) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Fictional protagonist") },
                                    placeholder = { Text("For example, Mira") },
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = companions,
                                    onValueChange = { companions = it.take(280) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Companions") },
                                    placeholder = { Text("For example, Max the dog, Ishan") },
                                    supportingText = { Text("Comma-separated. These are fictional writing instructions.") }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    AnimatedPrimaryButton(
                        text = if (error == null) "Generate ${mood.displayName.lowercase()} story" else "Try again",
                        onClick = {
                            onGenerate(
                                mood,
                                StoryCreativeSettings(
                                    genre = genre,
                                    protagonistName = protagonist,
                                    companions = companions.split(',', '\n')
                                ).normalized()
                            )
                        },
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
private fun StoryVariationDialog(
    currentMood: StoryMood,
    creativeSettings: StoryCreativeSettings,
    onDismiss: () -> Unit,
    onGenerate: (StoryMood, StoryVariation?, StoryCreativeSettings) -> Unit
) {
    var selectedMood by rememberSaveable { mutableStateOf(currentMood) }
    var choosingMood by rememberSaveable { mutableStateOf(false) }
    var editingCreative by rememberSaveable { mutableStateOf(false) }
    var genre by rememberSaveable { mutableStateOf(creativeSettings.genre.orEmpty()) }
    var protagonist by rememberSaveable { mutableStateOf(creativeSettings.protagonistName.orEmpty()) }
    var companions by rememberSaveable { mutableStateOf(creativeSettings.companions.joinToString(", ")) }
    fun currentCreativeSettings() = StoryCreativeSettings(
        genre = genre,
        protagonistName = protagonist,
        companions = companions.split(',', '\n')
    ).normalized()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create another version") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Choose one direction. Pluck creates a fresh story from the same journey—there is no chat to manage.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                androidx.compose.material3.TextButton(
                    onClick = { onGenerate(StoryMood.MYSTERIOUS, StoryVariation.MORE_MYSTERIOUS, currentCreativeSettings()) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Make it more mysterious") }
                androidx.compose.material3.TextButton(
                    onClick = { onGenerate(currentMood, StoryVariation.SHORTER, currentCreativeSettings()) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Make it shorter") }
                androidx.compose.material3.TextButton(
                    onClick = { onGenerate(currentMood, StoryVariation.MORE_EMOTIONAL, currentCreativeSettings()) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Make it more emotional") }
                androidx.compose.material3.TextButton(
                    onClick = { choosingMood = !choosingMood },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Change mood") }
                androidx.compose.material3.TextButton(
                    onClick = { editingCreative = !editingCreative },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (editingCreative) "Hide character & genre" else "Edit character & genre") }
                AnimatedVisibility(editingCreative) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = genre,
                            onValueChange = { genre = it.take(48) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Genre") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = protagonist,
                            onValueChange = { protagonist = it.take(48) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Fictional protagonist") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = companions,
                            onValueChange = { companions = it.take(280) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Companions") }
                        )
                    }
                }
                AnimatedVisibility(choosingMood) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        MoodPicker(selectedMood, onSelect = { selectedMood = it })
                        Button(
                            onClick = { onGenerate(selectedMood, null, currentCreativeSettings()) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) { Text("Create ${selectedMood.displayName.lowercase()} version") }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun StoryShareDialog(
    onDismiss: () -> Unit,
    onShareText: () -> Unit,
    socialRendering: Boolean,
    onShareSocialCard: (SocialCardFormat) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share your story") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Cards are rendered privately on this device from your captured photos and story excerpt. GPS coordinates and addresses are never printed on a card.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                androidx.compose.material3.TextButton(onClick = onShareText, modifier = Modifier.fillMaxWidth()) {
                    Text("Share story text")
                }
                androidx.compose.material3.TextButton(
                    onClick = { onShareSocialCard(SocialCardFormat.INSTAGRAM_STORY) },
                    enabled = !socialRendering,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (socialRendering) "Rendering card…" else "Instagram Story card")
                }
                androidx.compose.material3.TextButton(
                    onClick = { onShareSocialCard(SocialCardFormat.X_LANDSCAPE) },
                    enabled = !socialRendering,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("X landscape card")
                }
                androidx.compose.material3.TextButton(
                    onClick = { onShareSocialCard(SocialCardFormat.PORTRAIT) },
                    enabled = !socialRendering,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Portrait story card")
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun StoryExportDialog(
    onDismiss: () -> Unit,
    onSaveText: () -> Unit,
    onExportPdf: () -> Unit,
    onExportEpub: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save or export") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Books include a deterministic cover, chapter header, your selected photos, and a private on-device route sketch. Photo metadata is stripped from exported images.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                androidx.compose.material3.TextButton(onClick = onSaveText, modifier = Modifier.fillMaxWidth()) {
                    Text("Save plain text")
                }
                androidx.compose.material3.TextButton(onClick = onExportPdf, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Export illustrated PDF", modifier = Modifier.padding(start = 8.dp))
                }
                androidx.compose.material3.TextButton(onClick = onExportEpub, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.AutoStories, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Export EPUB book", modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun StoryReader(
    title: String,
    content: String,
    provider: String,
    mood: StoryMood,
    createdAt: Long,
    photos: List<JourneyPhoto>,
    scenes: List<StorySceneReference>,
    creativeSettings: StoryCreativeSettings,
    arc: NovellaArc?,
    narration: NarrationState,
    export: com.example.pluck.viewmodel.BookExportUiState,
    isRefreshing: Boolean,
    generationError: String?,
    onBack: () -> Unit,
    onRefresh: (StoryMood, StoryVariation?, StoryCreativeSettings) -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onNarrationOpen: () -> Unit,
    onNarrationPlay: () -> Unit,
    onNarrationPause: () -> Unit,
    onNarrationResume: () -> Unit,
    onNarrationStop: () -> Unit,
    onDismissExportMessage: () -> Unit
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
    var showVariationOptions by rememberSaveable { mutableStateOf(false) }
    var readerMode by rememberSaveable(title) { mutableStateOf(ReaderMode.FICTION) }
    var showAudiobook by rememberSaveable(title) { mutableStateOf(false) }
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
                                mood = mood,
                                createdAt = createdAt,
                                readingMinutes = readingMinutes,
                                creativeSettings = creativeSettings,
                                arc = arc,
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

                ReaderModePicker(
                    selected = readerMode,
                    onSelect = { readerMode = it },
                    hasSceneAnchors = scenes.isNotEmpty(),
                    modifier = Modifier
                        .widthIn(max = ReaderMaxWidth)
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                )

                AnimatedVisibility(
                    visible = showAudiobook,
                    modifier = Modifier
                        .widthIn(max = ReaderMaxWidth)
                        .fillMaxWidth()
                ) {
                    AudiobookPanel(
                        state = narration,
                        onPlay = onNarrationPlay,
                        onPause = onNarrationPause,
                        onResume = onNarrationResume,
                        onStop = onNarrationStop,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                AnimatedVisibility(
                    visible = export.isExporting || export.completedMessage != null || export.errorMessage != null,
                    modifier = Modifier
                        .widthIn(max = ReaderMaxWidth)
                        .fillMaxWidth()
                ) {
                    BookExportNotice(
                        export = export,
                        onDismiss = onDismissExportMessage,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                StoryBody(
                    paragraphs = paragraphs,
                    photos = photos,
                    scenes = scenes,
                    readerMode = readerMode,
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
            onRefresh = { showVariationOptions = true },
            onShare = onShare,
            onExport = onExport,
            narrationOpen = showAudiobook,
            onNarration = {
                showAudiobook = !showAudiobook
                if (!showAudiobook) onNarrationStop() else onNarrationOpen()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        )

        if (showVariationOptions) {
            StoryVariationDialog(
                currentMood = mood,
                creativeSettings = creativeSettings,
                onDismiss = { showVariationOptions = false },
                onGenerate = { selectedMood, variation, settings ->
                    showVariationOptions = false
                    onRefresh(selectedMood, variation, settings)
                }
            )
        }
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
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Navigate back")
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
private fun StoryHero(
    title: String,
    provider: String,
    mood: StoryMood,
    createdAt: Long,
    readingMinutes: Int,
    creativeSettings: StoryCreativeSettings,
    arc: NovellaArc?,
    modifier: Modifier = Modifier
) {
    val palette = mood.readerPalette()
    val coverDate = DateFormat.getMediumDateFormat(LocalContext.current).format(Date(createdAt))
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = palette.container,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = palette.content.copy(alpha = 0.11f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.AutoStories,
                            contentDescription = null,
                            modifier = Modifier.size(21.dp),
                            tint = palette.content
                        )
                    }
                }
                Text(
                    text = "${mood.displayName.uppercase()} EDITION",
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.content.copy(alpha = 0.8f)
                )
            }
            Text(
                text = title,
                modifier = Modifier.padding(top = 22.dp),
                style = mood.coverTitleStyle(),
                color = palette.content,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "A fictional tale, shaped by the places you chose on $coverDate.",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = palette.content.copy(alpha = 0.82f)
            )
            if (arc != null || !creativeSettings.isEmpty) {
                Row(
                    modifier = Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    arc?.let { novella ->
                        StoryMetaPill(
                            icon = Icons.Rounded.AutoStories,
                            text = novella.title,
                            contentDescription = "Part of the novella ${novella.title}",
                            contentColor = palette.content
                        )
                    }
                    creativeSettings.genre?.let { genre ->
                        StoryMetaPill(
                            icon = Icons.Rounded.AutoStories,
                            text = genre,
                            contentDescription = "Genre $genre",
                            contentColor = palette.content
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.padding(top = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StoryMetaPill(
                    icon = Icons.Rounded.Schedule,
                    text = "$readingMinutes min read",
                    contentDescription = "$readingMinutes minute reading time",
                    contentColor = palette.content
                )
                StoryMetaPill(
                    icon = Icons.Rounded.AutoStories,
                    text = provider,
                    contentDescription = "Generated with $provider",
                    contentColor = palette.content
                )
            }
        }
    }
}

@Composable
private fun StoryMetaPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    contentDescription: String,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = contentColor.copy(alpha = 0.1f),
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
                tint = contentColor
            )
            Text(
                text = text,
                modifier = Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor
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
private fun ReaderModePicker(
    selected: ReaderMode,
    onSelect: (ReaderMode) -> Unit,
    hasSceneAnchors: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Reader", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selected == ReaderMode.FICTION,
                    onClick = { onSelect(ReaderMode.FICTION) },
                    label = { Text("Fiction") }
                )
                FilterChip(
                    selected = selected == ReaderMode.REALITY,
                    onClick = { onSelect(ReaderMode.REALITY) },
                    label = { Text("Reality vs fiction") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
            Text(
                text = if (hasSceneAnchors) {
                    "Each paired scene uses a private image anchor saved when this story was generated."
                } else {
                    "This older story uses an approximate order match because it was created before scene anchors were available."
                },
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AudiobookPanel(
    state: NarrationState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("Audiobook mode", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        text = state.narrationDescription(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f)
                    )
                }
                when (state) {
                    is NarrationState.Speaking -> ReaderIconAction(
                        icon = Icons.Rounded.Pause,
                        label = "Pause audiobook",
                        onClick = onPause
                    )
                    is NarrationState.Paused -> ReaderIconAction(
                        icon = Icons.Rounded.PlayArrow,
                        label = "Resume audiobook",
                        onClick = onResume
                    )
                    else -> ReaderIconAction(
                        icon = Icons.Rounded.PlayArrow,
                        label = "Play audiobook",
                        onClick = onPlay
                    )
                }
            }
            if (state is NarrationState.Speaking || state is NarrationState.Paused) {
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Private playback on this device",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                    androidx.compose.material3.TextButton(onClick = onStop) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Stop", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

private fun NarrationState.narrationDescription(): String = when (this) {
    NarrationState.Idle -> "Use an installed offline Android voice. Your story text stays on-device."
    NarrationState.Initializing -> "Looking for an installed offline Android voice…"
    is NarrationState.Ready -> "Offline voice ready. ${offlineVoiceCount} private voice${if (offlineVoiceCount == 1) "" else "s"} available."
    is NarrationState.Speaking -> "Reading segment $segment of $segmentCount locally."
    is NarrationState.Paused -> "Paused at segment $segment of $segmentCount."
    is NarrationState.Unavailable -> message
    is NarrationState.Error -> message
}

@Composable
private fun BookExportNotice(
    export: com.example.pluck.viewmodel.BookExportUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isError = export.errorMessage != null
    val message = export.progressLabel ?: export.completedMessage ?: export.errorMessage.orEmpty()
    val contentColor = when {
        isError -> MaterialTheme.colorScheme.onErrorContainer
        export.isExporting -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = when {
            isError -> MaterialTheme.colorScheme.errorContainer
            export.isExporting -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.secondaryContainer
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.PictureAsPdf,
                contentDescription = null,
                tint = contentColor
            )
            Text(
                message,
                modifier = Modifier.padding(start = 12.dp).weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
            if (!export.isExporting) {
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun StoryBody(
    paragraphs: List<String>,
    photos: List<JourneyPhoto>,
    scenes: List<StorySceneReference>,
    readerMode: ReaderMode,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp)) {
            Text(
                text = if (readerMode == ReaderMode.FICTION) "THE STORY" else "REALITY VS FICTION",
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
            } else if (readerMode == ReaderMode.FICTION) {
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
            } else {
                RealityFictionReader(
                    paragraphs = paragraphs,
                    photos = photos,
                    scenes = scenes,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun RealityFictionReader(
    paragraphs: List<String>,
    photos: List<JourneyPhoto>,
    scenes: List<StorySceneReference>,
    modifier: Modifier = Modifier
) {
    if (photos.isEmpty()) {
        Text(
            "The original photos are no longer available on this device.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val photoById = remember(photos) { photos.associateBy { it.id } }
    val sceneByParagraph = remember(scenes) { scenes.associateBy { it.paragraphIndex } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        paragraphs.forEachIndexed { index, paragraph ->
            val scene = sceneByParagraph[index]
            val photo = scene?.let { photoById[it.photoId] }
                ?: photos.getOrNull(index % photos.size)
            RealityFictionPair(
                paragraph = paragraph,
                photo = photo,
                index = index,
                isAnchored = scene != null && photo != null
            )
        }
    }
}

@Composable
private fun RealityFictionPair(
    paragraph: String,
    photo: JourneyPhoto?,
    index: Int,
    isAnchored: Boolean
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val isWide = maxWidth >= 620.dp
        val reality: @Composable () -> Unit = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Column {
                    if (photo != null) {
                        AsyncImage(
                            model = File(photo.imagePath),
                            contentDescription = "Real captured place for scene ${index + 1}",
                            modifier = Modifier.fillMaxWidth().height(154.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Column(Modifier.padding(14.dp)) {
                        Text("REAL PLACE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            text = photo?.realityLabel() ?: "Original place unavailable",
                            modifier = Modifier.padding(top = 5.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = if (isAnchored) "Saved scene anchor" else "Approximate order match for an older story",
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
        val fiction: @Composable () -> Unit = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("FICTION", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        paragraph,
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp)
                    )
                }
            }
        }
        if (isWide) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { reality() }
                Box(Modifier.weight(1f)) { fiction() }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                reality()
                fiction()
            }
        }
    }
}

private fun JourneyPhoto.realityLabel(): String = address
    ?: listOfNotNull(
        latitude?.takeIf { it in -90.0..90.0 }?.let { "%.4f".format(it) },
        longitude?.takeIf { it in -180.0..180.0 }?.let { "%.4f".format(it) }
    ).takeIf { it.isNotEmpty() }?.joinToString(prefix = "Coordinates: ")
    ?: "Address unavailable"

@Composable
private fun StoryActionDock(
    compact: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    narrationOpen: Boolean,
    onNarration: () -> Unit,
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
                label = "Save or export story",
                onClick = onExport
            )
            ReaderIconAction(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                label = if (narrationOpen) "Close audiobook controls" else "Open audiobook controls",
                onClick = onNarration
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

private data class StoryReaderPalette(
    val container: androidx.compose.ui.graphics.Color,
    val content: androidx.compose.ui.graphics.Color
)

@Composable
private fun StoryMood.readerPalette(): StoryReaderPalette = when (this) {
    StoryMood.CINEMATIC -> StoryReaderPalette(
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.onSecondaryContainer
    )
    StoryMood.MYSTERIOUS, StoryMood.DARK -> StoryReaderPalette(
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer
    )
    StoryMood.WHIMSICAL -> StoryReaderPalette(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.onPrimaryContainer
    )
    StoryMood.WARM -> StoryReaderPalette(
        MaterialTheme.colorScheme.surfaceContainerHighest,
        MaterialTheme.colorScheme.onSurface
    )
    StoryMood.ADVENTUROUS -> StoryReaderPalette(
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.onSecondaryContainer
    )
}

@Composable
private fun StoryMood.coverTitleStyle() = when (this) {
    StoryMood.MYSTERIOUS, StoryMood.DARK -> MaterialTheme.typography.displaySmall.copy(letterSpacing = 0.45.sp)
    StoryMood.WHIMSICAL -> MaterialTheme.typography.displaySmall.copy(letterSpacing = 0.1.sp)
    else -> MaterialTheme.typography.displaySmall
}

private fun String.storyParagraphs(): List<String> =
    split(Regex("\\n\\s*\\n"))
        .map(String::trim)
        .filter(String::isNotBlank)

private fun String.wordCount(): Int =
    trim().split(Regex("\\s+")).count(String::isNotBlank)
