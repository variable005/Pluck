package com.example.pluck.viewmodel

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
import com.example.pluck.domain.model.LocalAiModelState
import com.example.pluck.domain.repository.JourneyRepository
import com.example.pluck.domain.repository.SettingsRepository
import com.example.pluck.domain.repository.StoryRepository
import com.example.pluck.domain.repository.LocalAiRepository
import com.example.pluck.domain.usecase.GenerateStoryUseCase
import com.example.pluck.domain.usecase.StoryProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
}

data class LibraryUiState(
    val journeys: List<JourneyLibraryItem> = emptyList(),
    val isLoading: Boolean = true
)

/** Exposes the user's locally stored journeys and their latest saved story previews. */
@HiltViewModel
class LibraryViewModel @Inject constructor(journeys: JourneyRepository) : ViewModel() {
    val uiState: StateFlow<LibraryUiState> = journeys.observeLibrary()
        .map { LibraryUiState(journeys = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())
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

data class CaptureUiState(val saving: Boolean = false, val error: String? = null)

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
        _uiState.value = CaptureUiState(saving = true)
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
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}

data class StoryUiState(val story: Story? = null, val generating: Boolean = false, val error: String? = null)

@HiltViewModel
class StoryViewModel @Inject constructor(
    private val stories: StoryRepository,
    private val generateStory: GenerateStoryUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val journeyId: Long = checkNotNull(savedStateHandle["journeyId"])
    private var selectedMood: StoryMood = StoryMood.CINEMATIC
    private val _generation = MutableStateFlow(StoryUiState())
    val uiState: StateFlow<StoryUiState> = combine(stories.observeLatest(journeyId), _generation) { story, generation -> generation.copy(story = story) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoryUiState())
    fun generate(mood: StoryMood = selectedMood) = viewModelScope.launch {
        selectedMood = mood
        _generation.value = _generation.value.copy(generating = true, error = null)
        runCatching { generateStory(journeyId, Locale.getDefault().displayLanguage, mood) }
            .onSuccess { _generation.value = StoryUiState() }
            .onFailure { _generation.value = _generation.value.copy(generating = false, error = it.message ?: "Story generation failed.") }
    }
    fun clearError() { _generation.value = _generation.value.copy(error = null) }
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
