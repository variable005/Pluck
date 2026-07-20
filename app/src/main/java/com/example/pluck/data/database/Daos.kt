package com.example.pluck.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JourneyDao {
    @Query("SELECT * FROM journeys WHERE date = :date LIMIT 1") fun observeByDate(date: String): Flow<JourneyEntity?>
    @Query("SELECT * FROM journeys WHERE id = :journeyId LIMIT 1") fun observeById(journeyId: Long): Flow<JourneyEntity?>
    @Query("SELECT * FROM journeys WHERE date = :date LIMIT 1") suspend fun findByDate(date: String): JourneyEntity?
    @Query(
        """
        SELECT
            journeys.id AS journeyId,
            journeys.date AS journeyDate,
            journeys.timeZoneId AS journeyTimeZoneId,
            COUNT(journey_photos.id) AS photoCount,
            (
                SELECT imagePath
                FROM journey_photos AS cover_photo
                WHERE cover_photo.journeyId = journeys.id
                ORDER BY cover_photo.timestamp ASC, cover_photo.id ASC
                LIMIT 1
            ) AS coverImagePath,
            stories.id AS storyId,
            stories.title AS storyTitle,
            stories.provider AS storyProvider,
            stories.createdAt AS storyCreatedAt,
            stories.mood AS storyMood
        FROM journeys
        LEFT JOIN journey_photos ON journey_photos.journeyId = journeys.id
        LEFT JOIN stories ON stories.id = (
            SELECT id
            FROM stories AS latest_story
            WHERE latest_story.journeyId = journeys.id
            ORDER BY latest_story.createdAt DESC, latest_story.id DESC
            LIMIT 1
        )
        GROUP BY journeys.id
        ORDER BY journeys.date DESC, journeys.createdAt DESC
        """
    )
    fun observeLibrary(): Flow<List<JourneyLibraryRow>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(journey: JourneyEntity): Long
}

@Dao
interface JourneyPhotoDao {
    @Query("SELECT * FROM journey_photos WHERE journeyId = :journeyId ORDER BY timestamp ASC") fun observeForJourney(journeyId: Long): Flow<List<JourneyPhotoEntity>>
    @Insert suspend fun insert(photo: JourneyPhotoEntity): Long
    @Delete suspend fun delete(photo: JourneyPhotoEntity)
}

@Dao
interface StoryDao {
    @Query("SELECT * FROM stories WHERE journeyId = :journeyId ORDER BY createdAt DESC LIMIT 1") fun observeLatest(journeyId: Long): Flow<StoryEntity?>
    @Insert suspend fun insert(story: StoryEntity): Long
}
