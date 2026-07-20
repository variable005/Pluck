package com.example.pluck.data.prompt

import com.example.pluck.domain.model.GeneratedStory
import com.example.pluck.domain.model.StoryGenerationInput
import com.example.pluck.domain.model.StorySceneReference

/**
 * Provider-neutral instructions and response parsing for Pluck stories.
 *
 * Keeping the contract here makes cloud providers and on-device Gemma produce the same scene
 * anchors, continuity hand-off, and creative-direction behavior.
 */
object StoryPromptBuilder {
    fun build(input: StoryGenerationInput): String = buildString {
        append(
            "You are Pluck's invisible fiction writer. The attached images are an ordered journey. " +
                "Analyze EVERY image and its accompanying metadata. Create one continuous fictional narrative inspired by the places, not a diary, journal, travelogue, or image-by-image description. " +
                "Each place must matter naturally to the plot. Keep characters, stakes, chronology, and details consistent. " +
                "Never present uncertain location or identity guesses as facts. Write 700 to 1200 words in ${input.locale}. "
        )
        append("Narrative mood: ${input.mood.promptDirection}. Let this guide the voice, imagery, pacing, and atmosphere while keeping the story cohesive. ")
        input.variation?.let { append("Revision direction: ${it.promptDirection} ") }
        input.creativeSettings.normalized().let { settings ->
            if (!settings.genre.isNullOrBlank()) append("Genre: ${settings.genre}. ")
            if (!settings.protagonistName.isNullOrBlank()) append("Fictional protagonist name: ${settings.protagonistName}. ")
            if (settings.companions.isNotEmpty()) append("Fictional companions to include: ${settings.companions.joinToString()}. ")
            if (!settings.isEmpty) {
                append("These are fictional writing instructions only; never claim they identify a person or animal in any supplied image. ")
            }
        }
        input.arcContext?.let { arc ->
            append(
                "This is Chapter ${arc.chapterIndex} of ${arc.totalChapters} in the continuing novella “${arc.title}.” " +
                    "Carry the fictional world, character motivations, and unresolved thread forward naturally. "
            )
            arc.previousContinuity?.take(900)?.let { summary ->
                append("Canonical continuity from the previous chapter: $summary ")
            }
        }
        append(
            "Return exactly this structure and no preamble: TITLE: a compelling title; STORY: the narrative; CONTINUITY: a concise fictional recap under 100 words for the next chapter. " +
                "Inside STORY, prefix every narrative paragraph exactly with [SCENE:n], where n is the 1-based image number it is fictionally inspired by. " +
                "Use every supplied scene number at least once. The markers are private provenance, not prose."
        )
        input.photos.forEachIndexed { index, photo ->
            append("\nImage ${index + 1}: captured at ${photo.timestamp}; place hint: ${photo.address ?: "not supplied"}; coordinates: ${photo.latitude ?: "unknown"}, ${photo.longitude ?: "unknown"}.")
        }
    }

    fun parse(text: String, input: StoryGenerationInput): GeneratedStory {
        val title = Regex("(?is)TITLE\\s*:\\s*(.+?)(?:\\n|STORY\\s*:)")
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.take(160)
            .orEmpty()
            .ifBlank { "A Plucked Story" }
        val storySection = Regex("(?is)STORY\\s*:\\s*(.*?)(?:\\n\\s*CONTINUITY\\s*:|\\z)")
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.trim()
            .orEmpty()
            .ifBlank { text.trim() }
        val continuity = Regex("(?is)CONTINUITY\\s*:\\s*(.+)")
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.take(720)
            ?.takeIf { it.isNotBlank() }

        val markerPattern = Regex("(?is)\\[SCENE\\s*:\\s*(\\d+)]\\s*(.*?)(?=\\s*\\[SCENE\\s*:|\\z)")
        val markers = markerPattern.findAll(storySection).toList()
        val (content, scenes) = if (markers.isNotEmpty()) {
            val paragraphs = markers.mapNotNull { match ->
                val sceneNumber = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val paragraph = match.groupValues[2].trim().replace(Regex("\\n\\s*\\n"), "\n\n")
                if (paragraph.isBlank()) return@mapNotNull null
                val photo = input.photos.getOrNull(sceneNumber - 1) ?: return@mapNotNull null
                ParsedScene(paragraph, StorySceneReference(photo.id, 0))
            }
            paragraphs.map { it.paragraph }.joinToString(separator = "\n\n") to
                paragraphs.mapIndexed { index, scene -> scene.reference.copy(paragraphIndex = index) }
        } else {
            storySection to emptyList()
        }
        if (content.length < 80) throw IllegalStateException("The provider returned an incomplete story. Please try again.")
        return GeneratedStory(title = title, content = content, sceneReferences = scenes, continuitySummary = continuity)
    }

    private data class ParsedScene(val paragraph: String, val reference: StorySceneReference)
}
