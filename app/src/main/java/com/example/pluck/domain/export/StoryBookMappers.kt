package com.example.pluck.domain.export

import com.example.pluck.domain.model.JourneyPhoto
import com.example.pluck.domain.model.Story

/** Converts a stored Pluck photo into the safe, local input shape used by the exporter. */
fun JourneyPhoto.toStoryBookPhoto(caption: String? = null): StoryBookPhoto = StoryBookPhoto(
    source = imagePath,
    caption = caption ?: address,
    address = address,
    capturedAt = timestamp,
    latitude = latitude,
    longitude = longitude
)

/**
 * Creates one export chapter from a saved story and its ordered source journey photos.
 *
 * [chapterDate] defaults to the story creation time. A month or novella exporter can pass the
 * journey's local-day instant instead, while retaining the exact saved prose and selected mood.
 */
fun Story.toStoryBookChapter(
    photos: List<JourneyPhoto>,
    chapterDate: Long = createdAt,
    chapterNumber: Int? = null
): StoryBookChapter = StoryBookChapter(
    title = title,
    story = content,
    date = chapterDate,
    photos = photos.map(JourneyPhoto::toStoryBookPhoto),
    chapterNumber = chapterNumber,
    mood = mood
)

/** A convenience factory for the existing single-story share/export flow. */
fun Story.toSingleStoryBook(
    photos: List<JourneyPhoto>,
    chapterDate: Long = createdAt,
    subtitle: String? = null,
    author: String = "Pluck"
): StoryBook = StoryBook(
    title = title,
    subtitle = subtitle,
    author = author,
    mood = mood,
    createdAt = createdAt,
    chapters = listOf(toStoryBookChapter(photos, chapterDate, chapterNumber = 1))
)
