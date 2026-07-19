package com.example.pluck.domain.provider

import com.example.pluck.domain.model.AiProvider
import com.example.pluck.domain.model.ConnectionResult
import com.example.pluck.domain.model.GeneratedStory
import com.example.pluck.domain.model.StoryGenerationInput

/** A model-specific story generator. UI and use cases only depend on this contract. */
interface StoryProvider {
    val type: AiProvider
    suspend fun generateStory(input: StoryGenerationInput, apiKey: String): GeneratedStory
    suspend fun testConnection(apiKey: String): ConnectionResult
}
