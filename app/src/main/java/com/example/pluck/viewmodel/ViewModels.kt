package com.example.pluck.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pluck.data.repository.OnboardingStore
import com.example.pluck.data.files.JourneyPhotoStore
import com.example.pluck.data.location.LocationCapture
import com.example.pluck.domain.model.AiProvider
import com.example.pluck.domain.model.ConnectionResult
import com.example.pluck.domain.model.Journey
import com.example.pluck.domain.model.HapticMode
import com.example.pluck.domain.model.ThemeMode
import com.example.pluck.domain.model.JourneyLibraryItem
import com.example.pluck.domain.model.JourneyPhoto
import com.example.pluck.domain.model.Story
import com.example.pluck.domain.model.StoryMood
import com.example.pluck.domain.model.StoryVariation
import com.example.pluck.domain.model.StoryCreativeSettings
import com.example.pluck.domain.model.StoryDetail
import com.example.pluck.domain.model.StorySceneReference
import com.example.pluck.domain.model.NovellaArc
import com.example.pluck.domain.model.NovellaArcDetail
import com.example.pluck.domain.model.NovellaChapter
import com.example.pluck.domain.model.LocalAiModelState
import com.example.pluck.domain.repository.JourneyRepository
import com.example.pluck.domain.repository.SettingsRepository
import com.example.pluck.domain.repository.StoryRepository
import com.example.pluck.domain.repository.LocalAiRepository
import com.example.pluck.domain.repository.NovellaRepository
import com.example.pluck.domain.usecase.GenerateStoryUseCase
import com.example.pluck.domain.usecase.StoryProviderRegistry
import com.example.pluck.data.export.StoryBookExporter
import com.example.pluck.data.narration.AndroidStoryNarrator
import com.example.pluck.domain.export.BookExportFormat
import com.example.pluck.domain.export.BookExportResult
import com.example.pluck.domain.export.StoryBook
import com.example.pluck.domain.export.StoryBookChapter
import com.example.pluck.domain.export.StoryBookPhoto
import com.example.pluck.domain.export.StoryBookRoutePoint
import com.example.pluck.domain.narration.NarrationState
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val journey: Journey? = null,
    val preferredName: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val journeys: JourneyRepository,
    onboardingStore: OnboardingStore
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(
        journeys.observeToday(),
        onboardingStore.observePreferredName()
    ) { journey, preferredName ->
        HomeUiState(journey = journey, preferredName = preferredName)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
    fun start(onReady: (Long) -> Unit) = viewModelScope.launch { onReady(journeys.getOrCreateToday().id) }
    /** Opens twenty bundled demo images; a story is still generated live by the selected provider. */
    fun openDemo(onReady: (Long) -> Unit) = viewModelScope.launch { onReady(journeys.seedDemoJourney().id) }
}

data class LibraryUiState(
    val journeys: List<JourneyLibraryItem> = emptyList(),
    val arcs: List<NovellaArc> = emptyList(),
    val export: BookExportUiState = BookExportUiState(),
    val deletionError: String? = null,
    val isLoading: Boolean = true
)

/** State for a user-initiated local PDF or EPUB export. */
data class BookExportUiState(
    val isExporting: Boolean = false,
    val progressLabel: String? = null,
    val completedMessage: String? = null,
    val errorMessage: String? = null
)

/** Exposes the user's locally stored journeys and their latest saved story previews. */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val journeys: JourneyRepository,
    private val stories: StoryRepository,
    private val novellas: NovellaRepository,
    private val exporter: StoryBookExporter,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    private val exportState = MutableStateFlow(BookExportUiState())
    private val deletionError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LibraryUiState> = combine(
        journeys.observeLibrary(),
        novellas.observeArcs(),
        exportState,
        deletionError
    ) { items, arcs, export, deleteError ->
        LibraryUiState(
            journeys = items,
            arcs = arcs,
            export = export,
            deletionError = deleteError,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    /** Builds a month book from the latest saved version of each daily story in that month. */
    fun exportMonth(monthPrefix: String, format: BookExportFormat, destination: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val selected = uiState.value.journeys
            .filter { it.journey.date.startsWith(monthPrefix) && it.story != null }
            .sortedBy { it.journey.date }
        if (selected.isEmpty()) {
            exportState.value = BookExportUiState(errorMessage = "There are no saved stories in this month yet.")
            return@launch
        }
        exportState.value = BookExportUiState(isExporting = true, progressLabel = "Preparing your monthly book…")
        val chapters = selected.mapIndexedNotNull { index, item ->
            val story = stories.observeLatest(item.journey.id).first() ?: return@mapIndexedNotNull null
            val photos = journeys.observePhotos(item.journey.id).first()
            story.toBookChapter(photos, index + 1, item.journey.date)
        }
        if (chapters.isEmpty()) {
            exportState.value = BookExportUiState(errorMessage = "The stories for this month could not be loaded.")
            return@launch
        }
        val title = monthPrefix.toMonthBookTitle()
        val result = exporter.exportToUri(
            context = context,
            book = StoryBook(
                title = title,
                subtitle = "A month of fictional journeys, created privately in Pluck",
                chapters = chapters,
                createdAt = System.currentTimeMillis(),
                mood = chapters.first().mood ?: StoryMood.CINEMATIC,
                coverPhoto = chapters.firstOrNull()?.photos?.firstOrNull()
            ),
            format = format,
            destination = destination,
            onProgress = { progress ->
                exportState.value = BookExportUiState(isExporting = true, progressLabel = progress.message)
            }
        )
        exportState.value = when (result) {
            is BookExportResult.Success -> BookExportUiState(completedMessage = "${result.format.name} book saved with ${result.chapterCount} chapters.")
            is BookExportResult.Failure -> BookExportUiState(errorMessage = result.message)
        }
    }

    fun clearExportMessage() { exportState.value = BookExportUiState() }

    /** Removes all saved story versions for a journey while keeping its captured places. */
    fun deleteStories(journeyId: Long) = runDeletion { stories.deleteStoriesForJourney(journeyId) }

    /** Removes a journey, its private images, generated stories, and any associated chapter. */
    fun deleteJourney(journeyId: Long) = runDeletion { journeys.deleteJourney(journeyId) }

    fun clearDeletionError() {
        deletionError.value = null
    }

    private fun runDeletion(operation: suspend () -> Unit) = viewModelScope.launch {
        deletionError.value = null
        try {
            withContext(Dispatchers.IO) { operation() }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            deletionError.value = error.message ?: "This saved item could not be deleted. Please try again."
        }
    }
}

data class NovellaComposerUiState(
    val journeys: List<JourneyLibraryItem> = emptyList(),
    val selectedJourneyIds: Set<Long> = emptySet(),
    val title: String = "",
    val mood: StoryMood = StoryMood.CINEMATIC,
    val genre: String = "",
    val protagonistName: String = "",
    val companionsText: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
) {
    val selectedJourneys: List<JourneyLibraryItem>
        get() = journeys.filter { it.journey.id in selectedJourneyIds }.sortedBy { it.journey.date }
    val canCreate: Boolean get() = selectedJourneyIds.size >= 2 && title.trim().isNotBlank() && !isSaving
}

/** Coordinates the intentionally small, no-chat setup for a multi-day fictional novella. */
@HiltViewModel
class NovellaComposerViewModel @Inject constructor(
    private val journeys: JourneyRepository,
    private val novellas: NovellaRepository
) : ViewModel() {
    private val draft = MutableStateFlow(NovellaComposerUiState())
    val uiState: StateFlow<NovellaComposerUiState> = combine(journeys.observeLibrary(), draft) { library, current ->
        current.copy(journeys = library)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NovellaComposerUiState())

    fun toggleJourney(journeyId: Long) = draft.update { current ->
        current.copy(
            selectedJourneyIds = current.selectedJourneyIds.toMutableSet().apply {
                if (!add(journeyId)) remove(journeyId)
            },
            error = null
        )
    }

    fun updateTitle(value: String) = draft.update { it.copy(title = value.take(100), error = null) }
    fun updateMood(value: StoryMood) = draft.update { it.copy(mood = value, error = null) }
    fun updateGenre(value: String) = draft.update { it.copy(genre = value.take(48), error = null) }
    fun updateProtagonist(value: String) = draft.update { it.copy(protagonistName = value.take(48), error = null) }
    fun updateCompanions(value: String) = draft.update { it.copy(companionsText = value.take(280), error = null) }

    fun create(onCreated: (Long) -> Unit) = viewModelScope.launch {
        val snapshot = uiState.value
        val selected = snapshot.selectedJourneys
        if (selected.size < 2) {
            draft.update { it.copy(error = "Choose at least two daily journeys.") }
            return@launch
        }
        if (!selected.isConsecutive()) {
            draft.update { it.copy(error = "Choose consecutive days so the plot can carry forward naturally.") }
            return@launch
        }
        if (snapshot.title.isBlank()) {
            draft.update { it.copy(error = "Give this novella a title first.") }
            return@launch
        }
        draft.update { it.copy(isSaving = true, error = null) }
        runCatching {
            novellas.createArc(
                title = snapshot.title,
                journeys = selected.map { it.journey },
                mood = snapshot.mood,
                creativeSettings = StoryCreativeSettings(
                    genre = snapshot.genre,
                    protagonistName = snapshot.protagonistName,
                    companions = snapshot.companionsText.split(',', '\n')
                ).normalized()
            )
        }.onSuccess { id ->
            draft.update { it.copy(isSaving = false) }
            onCreated(id)
        }.onFailure { error ->
            draft.update { it.copy(isSaving = false, error = error.message ?: "This novella could not be created.") }
        }
    }
}

data class NovellaChapterDisplay(
    val chapter: NovellaChapter,
    val journey: JourneyLibraryItem?
)

data class NovellaUiState(
    val detail: NovellaArcDetail? = null,
    val chapters: List<NovellaChapterDisplay> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class NovellaViewModel @Inject constructor(
    novellas: NovellaRepository,
    journeys: JourneyRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val arcId: Long = checkNotNull(savedStateHandle["arcId"])
    val uiState: StateFlow<NovellaUiState> = combine(
        novellas.observeArcDetail(arcId),
        journeys.observeLibrary()
    ) { detail, library ->
        val byJourney = library.associateBy { it.journey.id }
        NovellaUiState(
            detail = detail,
            chapters = detail?.chapters.orEmpty().map { NovellaChapterDisplay(it, byJourney[it.journeyId]) },
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NovellaUiState())
}

data class TimelineUiState(
    val journey: Journey? = null,
    val photos: List<JourneyPhoto> = emptyList(),
    val story: Story? = null
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val journeys: JourneyRepository,
    private val stories: StoryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val journeyId: Long = checkNotNull(savedStateHandle["journeyId"])
    val uiState: StateFlow<TimelineUiState> = combine(
        journeys.observeJourney(journeyId),
        journeys.observePhotos(journeyId),
        stories.observeLatest(journeyId)
    ) { journey, photos, story ->
        TimelineUiState(journey = journey, photos = photos, story = story)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineUiState())
    fun delete(photo: JourneyPhoto) = viewModelScope.launch { journeys.deletePhoto(photo) }
}

data class CaptureUiState(
    val saving: Boolean = false,
    val savingLabel: String = "Saving this place…",
    val error: String? = null
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val photoStore: JourneyPhotoStore,
    private val locations: LocationCapture,
    private val journeys: JourneyRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val journeyId: Long = checkNotNull(savedStateHandle["journeyId"])
    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState = _uiState.asStateFlow()
    fun outputFile(): File = photoStore.newPhotoFile()
    fun save(file: File, hasLocationPermission: Boolean, onSaved: () -> Unit) = viewModelScope.launch {
        _uiState.value = CaptureUiState(saving = true, savingLabel = "Saving this place…")
        runCatching {
            val location = if (hasLocationPermission) locations.current() else null
            journeys.addPhoto(journeyId, file.absolutePath, System.currentTimeMillis(), location?.latitude, location?.longitude, location?.address)
        }.onSuccess {
            _uiState.value = CaptureUiState()
            onSaved()
        }.onFailure {
            file.delete()
            _uiState.value = CaptureUiState(error = it.message ?: "Photo could not be saved.")
        }
    }

    /**
     * Imports selected images in picker order. The timeline's timestamp is deliberately assigned
     * at import time, rather than from EXIF, so a current journey keeps the sequence the user
     * chose. Embedded GPS is retained as an optional place hint without treating the device's
     * current location as the location of an older image.
     */
    fun importPhotos(uris: List<Uri>, onSaved: () -> Unit) = viewModelScope.launch {
        val selection = uris.distinct()
        if (selection.isEmpty()) return@launch

        _uiState.value = CaptureUiState(
            saving = true,
            savingLabel = if (selection.size == 1) "Adding this place…" else "Adding ${selection.size} places…"
        )
        val startTimestamp = System.currentTimeMillis()
        var importedCount = 0
        withContext(Dispatchers.IO) {
            selection.forEachIndexed { index, uri ->
                runCatching {
                    val imported = photoStore.importPhoto(uri)
                    try {
                        journeys.addPhoto(
                            journeyId = journeyId,
                            imagePath = imported.file.absolutePath,
                            timestamp = startTimestamp + index,
                            latitude = imported.latitude,
                            longitude = imported.longitude,
                            address = null
                        )
                    } catch (error: Throwable) {
                        imported.file.delete()
                        throw error
                    }
                }.onSuccess {
                    importedCount++
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                }
            }
        }

        when {
            importedCount == selection.size -> {
                _uiState.value = CaptureUiState()
                onSaved()
            }
            importedCount == 0 -> {
                _uiState.value = CaptureUiState(
                    error = "No photos were added. Choose another image and try again."
                )
            }
            else -> {
                _uiState.value = CaptureUiState(
                    error = "$importedCount of ${selection.size} photos were added. You can try the rest again."
                )
            }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}

data class StoryUiState(
    val story: Story? = null,
    val scenes: List<StorySceneReference> = emptyList(),
    val journey: Journey? = null,
    val photos: List<JourneyPhoto> = emptyList(),
    val arc: NovellaArc? = null,
    val generating: Boolean = false,
    val error: String? = null,
    val export: BookExportUiState = BookExportUiState(),
    val narration: NarrationState = NarrationState.Idle
)

private data class StoryWorkState(
    val generating: Boolean = false,
    val error: String? = null,
    val export: BookExportUiState = BookExportUiState(),
    val narration: NarrationState = NarrationState.Idle
)

@HiltViewModel
class StoryViewModel @Inject constructor(
    private val stories: StoryRepository,
    private val journeys: JourneyRepository,
    private val novellas: NovellaRepository,
    private val generateStory: GenerateStoryUseCase,
    private val exporter: StoryBookExporter,
    private val narrator: AndroidStoryNarrator,
    @param:ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val journeyId: Long = checkNotNull(savedStateHandle["journeyId"])
    private var selectedMood: StoryMood = StoryMood.CINEMATIC
    private val work = MutableStateFlow(StoryWorkState())
    val uiState: StateFlow<StoryUiState> = combine(
        stories.observeLatestDetail(journeyId),
        journeys.observeJourney(journeyId),
        journeys.observePhotos(journeyId),
        novellas.observeArcForJourney(journeyId),
        work
    ) { detail, journey, photos, arc, workState ->
        StoryUiState(
            story = detail?.story,
            scenes = detail?.scenes.orEmpty(),
            journey = journey,
            photos = photos,
            arc = arc,
            generating = workState.generating,
            error = workState.error,
            export = workState.export,
            narration = workState.narration
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoryUiState())

    init {
        viewModelScope.launch {
            narrator.state.collect { narration ->
                work.update { it.copy(narration = narration) }
            }
        }
    }

    fun generate(
        mood: StoryMood = selectedMood,
        variation: StoryVariation? = null,
        creativeSettings: StoryCreativeSettings = StoryCreativeSettings()
    ) = viewModelScope.launch {
        selectedMood = mood
        work.update { it.copy(generating = true, error = null) }
        runCatching { generateStory(journeyId, Locale.getDefault().displayLanguage, mood, variation, creativeSettings) }
            .onSuccess { work.update { it.copy(generating = false, error = null) } }
            .onFailure { error -> work.update { it.copy(generating = false, error = error.message ?: "Story generation failed.") } }
    }

    fun export(format: BookExportFormat, destination: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val state = uiState.value
        val story = state.story ?: return@launch
        work.update { it.copy(export = BookExportUiState(isExporting = true, progressLabel = "Preparing your story book…")) }
        val chapter = story.toBookChapter(state.photos, 1, state.journey?.date)
        val result = exporter.exportToUri(
            context = context,
            book = StoryBook(
                title = story.title,
                subtitle = "A fictional tale shaped by one real day",
                chapters = listOf(chapter),
                createdAt = story.createdAt,
                mood = story.mood,
                coverPhoto = chapter.photos.firstOrNull()
            ),
            format = format,
            destination = destination,
            onProgress = { progress ->
                work.update { current -> current.copy(export = BookExportUiState(isExporting = true, progressLabel = progress.message)) }
            }
        )
        work.update { current ->
            current.copy(
                export = when (result) {
                    is BookExportResult.Success -> BookExportUiState(completedMessage = "${result.format.name} book saved.")
                    is BookExportResult.Failure -> BookExportUiState(errorMessage = result.message)
                }
            )
        }
    }

    fun clearError() { work.update { it.copy(error = null) } }
    fun clearExportMessage() { work.update { it.copy(export = BookExportUiState()) } }

    fun initializeNarration() = viewModelScope.launch { narrator.initialize() }
    fun playNarration() = viewModelScope.launch {
        uiState.value.story?.content?.let { content -> narrator.play(content) }
    }
    fun pauseNarration() = narrator.pause()
    fun resumeNarration() = narrator.resume()
    fun stopNarration() = narrator.stop()

    override fun onCleared() {
        narrator.stop()
        super.onCleared()
    }
}

data class SettingsUiState(
    val provider: AiProvider = AiProvider.GEMINI,
    val hapticMode: HapticMode = HapticMode.ESSENTIAL,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val keys: Map<AiProvider, String> = emptyMap(),
    val testing: AiProvider? = null,
    val result: Pair<AiProvider, ConnectionResult>? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val providers: StoryProviderRegistry
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadSavedApiKeys()
        viewModelScope.launch {
            settings.observeProvider().collect { provider ->
                _uiState.update { it.copy(provider = provider) }
            }
        }
        viewModelScope.launch {
            settings.observeHapticMode().collect { mode ->
                _uiState.update { it.copy(hapticMode = mode) }
            }
        }
        viewModelScope.launch {
            settings.observeThemeMode().collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            settings.observeDynamicColor().collect { enabled ->
                _uiState.update { it.copy(dynamicColor = enabled) }
            }
        }
    }

    private fun loadSavedApiKeys() = viewModelScope.launch {
        val savedKeys = withContext(Dispatchers.IO) {
            buildMap {
                AiProvider.entries.filter { it.requiresApiKey }.forEach { provider ->
                    val key = try {
                        settings.apiKey(provider)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        // A key that cannot be decrypted is treated as unavailable; never crash Settings.
                        null
                    }
                    key?.let { put(provider, it) }
                }
            }
        }
        _uiState.update { current ->
            // Preserve edits made while encrypted preferences were being read.
            current.copy(keys = savedKeys + current.keys)
        }
    }

    fun select(provider: AiProvider) = viewModelScope.launch { settings.setProvider(provider) }
    fun setHapticMode(mode: HapticMode) = viewModelScope.launch { settings.setHapticMode(mode) }
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settings.setDynamicColor(enabled) }
    fun updateKey(provider: AiProvider, value: String) { _uiState.value = _uiState.value.copy(keys = _uiState.value.keys + (provider to value)) }
    fun saveKey(provider: AiProvider) = viewModelScope.launch { settings.saveApiKey(provider, _uiState.value.keys[provider].orEmpty()) }
    fun test(provider: AiProvider) = viewModelScope.launch {
        val key = _uiState.value.keys[provider] ?: settings.apiKey(provider).orEmpty()
        _uiState.value = _uiState.value.copy(testing = provider, result = null)
        val result = if (provider.requiresApiKey && key.isBlank()) ConnectionResult.InvalidKey else providers.selected(provider).testConnection(key)
        _uiState.value = _uiState.value.copy(testing = null, result = provider to result)
    }
}

/** Keeps app-wide haptic behavior synchronized with the persisted Settings preference. */
@HiltViewModel
class HapticSettingsViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val mode: StateFlow<HapticMode> = settings.observeHapticMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HapticMode.ESSENTIAL)
}

data class ThemePreferences(val mode: ThemeMode = ThemeMode.SYSTEM, val dynamicColor: Boolean = true)

/** Keeps the process-wide Compose theme synchronized with the saved appearance preference. */
@HiltViewModel
class ThemeSettingsViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val preferences: StateFlow<ThemePreferences> = combine(
        settings.observeThemeMode(),
        settings.observeDynamicColor()
    ) { mode, dynamicColor -> ThemePreferences(mode, dynamicColor) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemePreferences())
}

@HiltViewModel
class LocalAiViewModel @Inject constructor(private val localAi: LocalAiRepository) : ViewModel() {
    val uiState: StateFlow<LocalAiModelState> = localAi.modelState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalAiModelState())
    init { refresh() }
    fun refresh() = viewModelScope.launch { localAi.refresh() }
    fun download() = viewModelScope.launch { localAi.download() }
    fun pause() = viewModelScope.launch { localAi.pause() }
    fun delete() = viewModelScope.launch { localAi.delete() }
    fun verify() = viewModelScope.launch { localAi.verify() }
    fun checkForUpdates() = viewModelScope.launch { localAi.checkForUpdates() }
}

private fun Story.toBookChapter(
    photos: List<JourneyPhoto>,
    chapterNumber: Int,
    journeyDate: String?
): StoryBookChapter = StoryBookChapter(
    title = title,
    story = content,
    date = journeyDate.toBookTimestamp(createdAt),
    photos = photos.map { photo ->
        StoryBookPhoto(
            source = photo.imagePath,
            caption = photo.address,
            address = photo.address,
            capturedAt = photo.timestamp,
            latitude = photo.latitude,
            longitude = photo.longitude
        )
    },
    routePoints = photos.mapNotNull { photo ->
        val latitude = photo.latitude ?: return@mapNotNull null
        val longitude = photo.longitude ?: return@mapNotNull null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return@mapNotNull null
        StoryBookRoutePoint(latitude = latitude, longitude = longitude, label = photo.address)
    },
    chapterNumber = chapterNumber,
    mood = mood
)

private fun String?.toBookTimestamp(fallback: Long): Long = runCatching {
    requireNotNull(this)
    LocalDate.parse(this).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrDefault(fallback)

private fun String.toMonthBookTitle(): String = runCatching {
    val date = LocalDate.parse("${this}-01")
    date.month.name.lowercase().replaceFirstChar(Char::titlecase) + " ${date.year} · Pluck Stories"
}.getOrDefault("Pluck Stories")

private fun List<JourneyLibraryItem>.isConsecutive(): Boolean {
    val dates = mapNotNull { item -> runCatching { LocalDate.parse(item.journey.date) }.getOrNull() }.sorted()
    return dates.size == size && dates.zipWithNext().all { (first, second) ->
        ChronoUnit.DAYS.between(first, second) == 1L
    }
}
