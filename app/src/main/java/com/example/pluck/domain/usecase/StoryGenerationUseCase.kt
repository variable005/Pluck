package com.example.pluck.domain.usecase

import com.example.pluck.domain.model.GeneratedStory
import com.example.pluck.domain.model.StoryGenerationInput
import com.example.pluck.domain.model.StoryMood
import com.example.pluck.domain.model.StoryVariation
import com.example.pluck.domain.model.StoryCreativeSettings
import com.example.pluck.domain.provider.StoryProvider
import com.example.pluck.domain.repository.JourneyRepository
import com.example.pluck.domain.repository.SettingsRepository
import com.example.pluck.domain.repository.StoryRepository
import com.example.pluck.domain.repository.NovellaRepository
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
    private val storyRepository: StoryRepository,
    private val novellaRepository: NovellaRepository
) {
    suspend operator fun invoke(
        journeyId: Long,
        locale: String,
        mood: StoryMood = StoryMood.CINEMATIC,
        variation: StoryVariation? = null,
        creativeSettings: StoryCreativeSettings = StoryCreativeSettings()
    ): GeneratedStory {
        val providerType = settingsRepository.observeProvider().first()
        val provider = providerRegistry.selected(providerType)
        val key = if (providerType.requiresApiKey) requireNotNull(settingsRepository.apiKey(providerType)) { "Add an API key for ${providerType.displayName} in Settings." } else ""
        val photos = journeyRepository.observePhotos(journeyId).first()
        require(photos.size >= 2) { "Capture at least two places before generating a story." }
        val arc = novellaRepository.observeArcForJourney(journeyId).first()
        val arcContext = novellaRepository.generationContextForJourney(journeyId)
        val effectiveMood = arc?.mood ?: mood
        val effectiveCreativeSettings = (arc?.creativeSettings ?: creativeSettings).normalized()
        val story = provider.generateStory(
            StoryGenerationInput(
                photos = photos,
                locale = locale,
                mood = effectiveMood,
                variation = variation,
                creativeSettings = effectiveCreativeSettings,
                arcContext = arcContext
            ),
            key
        )
        val storyId = storyRepository.save(
            com.example.pluck.domain.model.Story(
                id = 0,
                journeyId = journeyId,
                title = story.title,
                content = story.content,
                provider = providerType,
                createdAt = System.currentTimeMillis(),
                mood = effectiveMood,
                creativeSettings = effectiveCreativeSettings
            ),
            scenes = story.sceneReferences
        )
        if (arcContext != null) novellaRepository.saveGeneratedChapter(journeyId, storyId, story.continuitySummary)
        return story
    }
}
