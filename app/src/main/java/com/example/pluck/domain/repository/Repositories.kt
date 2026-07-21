package com.example.pluck.domain.repository

import com.example.pluck.domain.model.AiProvider
import com.example.pluck.domain.model.HapticMode
import com.example.pluck.domain.model.ThemeMode
import com.example.pluck.domain.model.Journey
import com.example.pluck.domain.model.JourneyLibraryItem
import com.example.pluck.domain.model.JourneyPhoto
import com.example.pluck.domain.model.Story
import com.example.pluck.domain.model.StoryDetail
import com.example.pluck.domain.model.StorySceneReference
import com.example.pluck.domain.model.StoryCreativeSettings
import com.example.pluck.domain.model.StoryMood
import com.example.pluck.domain.model.NovellaArc
import com.example.pluck.domain.model.NovellaArcDetail
import com.example.pluck.domain.model.LocalAiModelState
import kotlinx.coroutines.flow.Flow

interface JourneyRepository {
    fun observeToday(): Flow<Journey?>
    fun observeJourney(journeyId: Long): Flow<Journey?>
    fun observeLibrary(): Flow<List<JourneyLibraryItem>>
    fun observePhotos(journeyId: Long): Flow<List<JourneyPhoto>>
    suspend fun getOrCreateToday(): Journey
    /** Creates Pluck's asset-backed 20-place demo journey without creating a story. */
    suspend fun seedDemoJourney(): Journey
    suspend fun addPhoto(journeyId: Long, imagePath: String, timestamp: Long, latitude: Double?, longitude: Double?, address: String?)
    suspend fun deletePhoto(photo: JourneyPhoto)
    /** Permanently removes a journey and its locally stored image files. */
    suspend fun deleteJourney(journeyId: Long)
}

interface StoryRepository {
    fun observeLatest(journeyId: Long): Flow<Story?>
    fun observeLatestDetail(journeyId: Long): Flow<StoryDetail?>
    suspend fun save(story: Story, scenes: List<StorySceneReference> = emptyList()): Long
    /** Permanently removes every generated version belonging to one journey. */
    suspend fun deleteStoriesForJourney(journeyId: Long)
}

/** Stores the membership and continuity state for private, multi-day fictional novellas. */
interface NovellaRepository {
    fun observeArcs(): Flow<List<NovellaArc>>
    fun observeArcDetail(arcId: Long): Flow<NovellaArcDetail?>
    fun observeArcForJourney(journeyId: Long): Flow<NovellaArc?>
    suspend fun createArc(
        title: String,
        journeys: List<Journey>,
        mood: StoryMood,
        creativeSettings: StoryCreativeSettings
    ): Long

    /** Returns a context only when the prior chapter has a stable continuity hand-off. */
    suspend fun generationContextForJourney(journeyId: Long): com.example.pluck.domain.model.ArcGenerationContext?
    suspend fun saveGeneratedChapter(journeyId: Long, storyId: Long, continuitySummary: String?)
}

interface SettingsRepository {
    fun observeProvider(): Flow<AiProvider>
    fun observeHapticMode(): Flow<HapticMode>
    fun observeThemeMode(): Flow<ThemeMode>
    fun observeDynamicColor(): Flow<Boolean>
    suspend fun setProvider(provider: AiProvider)
    suspend fun setHapticMode(mode: HapticMode)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun apiKey(provider: AiProvider): String?
    suspend fun saveApiKey(provider: AiProvider, key: String)
}

/** Controls Pluck's private, integrity-verified Google AI Edge model installation. */
interface LocalAiRepository {
    val modelState: Flow<LocalAiModelState>
    suspend fun refresh()
    suspend fun download()
    suspend fun pause()
    suspend fun delete()
    suspend fun verify()
    suspend fun checkForUpdates()
}
