package com.example.pluck.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.pluck.domain.model.AiProvider

@Entity(tableName = "journeys", indices = [Index(value = ["date"], unique = true)])
data class JourneyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val timeZoneId: String,
    val createdAt: Long
)

@Entity(
    tableName = "journey_photos",
    foreignKeys = [ForeignKey(entity = JourneyEntity::class, parentColumns = ["id"], childColumns = ["journeyId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("journeyId"), Index(value = ["journeyId", "timestamp"])]
)
data class JourneyPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val journeyId: Long,
    val imagePath: String,
    val latitude: Double?,
    val longitude: Double?,
    val timestamp: Long,
    val address: String?
)

@Entity(
    tableName = "stories",
    foreignKeys = [ForeignKey(entity = JourneyEntity::class, parentColumns = ["id"], childColumns = ["journeyId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("journeyId")]
)
data class StoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val journeyId: Long,
    val title: String,
    val content: String,
    val provider: AiProvider,
    val createdAt: Long
)

/**
 * A Room projection used by the Library. It intentionally contains only story metadata;
 * reading prose remains the responsibility of [StoryDao.observeLatest].
 */
data class JourneyLibraryRow(
    val journeyId: Long,
    val journeyDate: String,
    val journeyTimeZoneId: String,
    val photoCount: Int,
    val coverImagePath: String?,
    val storyId: Long?,
    val storyTitle: String?,
    val storyProvider: AiProvider?,
    val storyCreatedAt: Long?
)
