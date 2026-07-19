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
    @Query("SELECT * FROM journeys WHERE date = :date LIMIT 1") suspend fun findByDate(date: String): JourneyEntity?
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
