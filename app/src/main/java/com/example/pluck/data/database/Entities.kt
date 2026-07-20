package com.example.pluck.data.database

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Embedded
import androidx.room.Relation
import com.example.pluck.domain.model.AiProvider
import com.example.pluck.domain.model.StoryMood

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
    val createdAt: Long,
    @ColumnInfo(defaultValue = "'CINEMATIC'") val mood: StoryMood = StoryMood.CINEMATIC,
    @ColumnInfo(defaultValue = "NULL") val genre: String? = null,
    @ColumnInfo(defaultValue = "NULL") val protagonistName: String? = null,
    @ColumnInfo(defaultValue = "'[]'") val companionsJson: String = "[]"
)

/** Explicit provenance for Reality vs Fiction; photos are never copied into this table. */
@Entity(
    tableName = "story_scenes",
    foreignKeys = [ForeignKey(entity = StoryEntity::class, parentColumns = ["id"], childColumns = ["storyId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("storyId"), Index(value = ["storyId", "paragraphIndex"])]
)
data class StorySceneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storyId: Long,
    val photoId: Long,
    val paragraphIndex: Int
)

data class StoryWithScenesEntity(
    @Embedded val story: StoryEntity,
    @Relation(parentColumn = "id", entityColumn = "storyId") val scenes: List<StorySceneEntity>
)

@Entity(tableName = "novella_arcs", indices = [Index(value = ["startDate", "endDate"])])
data class NovellaArcEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startDate: String,
    val endDate: String,
    val mood: StoryMood,
    val genre: String?,
    val protagonistName: String?,
    val companionsJson: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "novella_chapters",
    primaryKeys = ["arcId", "journeyId"],
    foreignKeys = [
        ForeignKey(entity = NovellaArcEntity::class, parentColumns = ["id"], childColumns = ["arcId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = JourneyEntity::class, parentColumns = ["id"], childColumns = ["journeyId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["arcId", "chapterIndex"], unique = true), Index(value = ["journeyId"], unique = true)]
)
data class NovellaChapterEntity(
    val arcId: Long,
    val journeyId: Long,
    val chapterIndex: Int,
    val storyId: Long? = null,
    val continuitySummary: String? = null,
    val isStale: Boolean = false,
    val updatedAt: Long
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
    val storyCreatedAt: Long?,
    val storyMood: StoryMood?,
    val storyGenre: String?
)
