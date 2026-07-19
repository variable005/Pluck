package com.example.pluck.domain.usecase

import com.example.pluck.domain.model.GeneratedStory
import com.example.pluck.domain.model.StoryGenerationInput
import com.example.pluck.domain.provider.StoryProvider
import com.example.pluck.domain.repository.JourneyRepository
import com.example.pluck.domain.repository.SettingsRepository
import com.example.pluck.domain.repository.StoryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class StoryProviderRegistry @Inject constructor(providers: Set<@JvmSuppressWildcards StoryProvider>) {
    private val byType = providers.associateBy { it.type }
    fun selected(type: com.example.pluck.domain.model.AiProvider): StoryProvider = requireNotNull(byType[type]) { "Provider $type is not configured." }
}

class GenerateStoryUseCase @Inject constructor(
    private val journeyRepository: JourneyRepository,
    private val settingsRepository: SettingsRepository,
    private val providerRegistry: StoryProviderRegistry,
    private val storyRepository: StoryRepository
) {
    suspend operator fun invoke(journeyId: Long, locale: String): GeneratedStory {
        val providerType = settingsRepository.observeProvider().first()
        val provider = providerRegistry.selected(providerType)
        val key = if (providerType.requiresApiKey) requireNotNull(settingsRepository.apiKey(providerType)) { "Add an API key for ${providerType.displayName} in Settings." } else ""
        val photos = journeyRepository.observePhotos(journeyId).first()
        require(photos.size >= 2) { "Capture at least two places before generating a story." }
        val story = provider.generateStory(StoryGenerationInput(photos = photos, locale = locale), key)
        storyRepository.save(com.example.pluck.domain.model.Story(0, journeyId, story.title, story.content, providerType, System.currentTimeMillis()))
        return story
    }
}
