package com.example.pluck.domain.repository

import com.example.pluck.domain.model.AiProvider
import com.example.pluck.domain.model.Journey
import com.example.pluck.domain.model.JourneyPhoto
import com.example.pluck.domain.model.Story
import com.example.pluck.domain.model.LocalAiModelState
import kotlinx.coroutines.flow.Flow

interface JourneyRepository {
    fun observeToday(): Flow<Journey?>
    fun observePhotos(journeyId: Long): Flow<List<JourneyPhoto>>
    suspend fun getOrCreateToday(): Journey
    suspend fun addPhoto(journeyId: Long, imagePath: String, timestamp: Long, latitude: Double?, longitude: Double?, address: String?)
    suspend fun deletePhoto(photo: JourneyPhoto)
}

interface StoryRepository {
    fun observeLatest(journeyId: Long): Flow<Story?>
    suspend fun save(story: Story): Long
}

interface SettingsRepository {
    fun observeProvider(): Flow<AiProvider>
    suspend fun setProvider(provider: AiProvider)
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
