package com.example.pluck.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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
            stories.mood AS storyMood,
            stories.genre AS storyGenre
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
    @Query("SELECT * FROM journeys WHERE date LIKE :monthPrefix || '%' ORDER BY date ASC, createdAt ASC")
    suspend fun journeysForMonth(monthPrefix: String): List<JourneyEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(journey: JourneyEntity): Long
    @Query("DELETE FROM journeys WHERE id = :journeyId") suspend fun deleteById(journeyId: Long)
}

@Dao
interface JourneyPhotoDao {
    @Query("SELECT * FROM journey_photos WHERE journeyId = :journeyId ORDER BY timestamp ASC") fun observeForJourney(journeyId: Long): Flow<List<JourneyPhotoEntity>>
    @Query("SELECT * FROM journey_photos WHERE journeyId = :journeyId ORDER BY timestamp ASC, id ASC") suspend fun forJourney(journeyId: Long): List<JourneyPhotoEntity>
    @Insert suspend fun insert(photo: JourneyPhotoEntity): Long
    @Query("DELETE FROM story_scenes WHERE photoId = :photoId") suspend fun deleteSceneReferences(photoId: Long)
    @Delete suspend fun delete(photo: JourneyPhotoEntity)

    /**
     * Removes a photo's optional Reality vs Fiction references before deleting the photo row.
     * Story scenes intentionally do not hold a foreign key to photos so that a saved story can
     * remain readable after an individual capture is removed.
     */
    @Transaction
    suspend fun deleteWithSceneReferences(photo: JourneyPhotoEntity) {
        deleteSceneReferences(photo.id)
        delete(photo)
    }
}

@Dao
interface StoryDao {
    @Query("SELECT * FROM stories WHERE journeyId = :journeyId ORDER BY createdAt DESC LIMIT 1") fun observeLatest(journeyId: Long): Flow<StoryEntity?>
    @Transaction
    @Query("SELECT * FROM stories WHERE journeyId = :journeyId ORDER BY createdAt DESC, id DESC LIMIT 1")
    fun observeLatestWithScenes(journeyId: Long): Flow<StoryWithScenesEntity?>
    @Query("SELECT * FROM stories WHERE journeyId = :journeyId ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun latestForJourney(journeyId: Long): StoryEntity?
    @Insert suspend fun insert(story: StoryEntity): Long
    @Insert suspend fun insertScenes(scenes: List<StorySceneEntity>)
    @Query("DELETE FROM stories WHERE journeyId = :journeyId") suspend fun deleteForJourney(journeyId: Long)

    @Transaction
    suspend fun insertWithScenes(story: StoryEntity, scenes: List<StorySceneEntity>): Long {
        val storyId = insert(story)
        if (scenes.isNotEmpty()) insertScenes(scenes.map { it.copy(storyId = storyId) })
        return storyId
    }
}

@Dao
interface NovellaDao {
    @Query("SELECT * FROM novella_arcs ORDER BY updatedAt DESC, id DESC") fun observeArcs(): Flow<List<NovellaArcEntity>>
    @Query("SELECT * FROM novella_arcs WHERE id = :arcId LIMIT 1") fun observeArc(arcId: Long): Flow<NovellaArcEntity?>
    @Query("SELECT * FROM novella_chapters WHERE arcId = :arcId ORDER BY chapterIndex ASC") fun observeChapters(arcId: Long): Flow<List<NovellaChapterEntity>>
    @Query("SELECT novella_arcs.* FROM novella_arcs INNER JOIN novella_chapters ON novella_chapters.arcId = novella_arcs.id WHERE novella_chapters.journeyId = :journeyId LIMIT 1")
    fun observeArcForJourney(journeyId: Long): Flow<NovellaArcEntity?>
    @Query("SELECT * FROM novella_chapters WHERE journeyId = :journeyId LIMIT 1") suspend fun chapterForJourney(journeyId: Long): NovellaChapterEntity?
    @Query("SELECT * FROM novella_arcs WHERE id = :arcId LIMIT 1") suspend fun arcById(arcId: Long): NovellaArcEntity?
    @Query("SELECT * FROM novella_chapters WHERE arcId = :arcId ORDER BY chapterIndex ASC") suspend fun chaptersForArc(arcId: Long): List<NovellaChapterEntity>
    @Insert suspend fun insertArc(arc: NovellaArcEntity): Long
    @Insert suspend fun insertChapters(chapters: List<NovellaChapterEntity>)
    @Query("UPDATE novella_chapters SET storyId = :storyId, continuitySummary = :continuitySummary, isStale = 0, updatedAt = :updatedAt WHERE arcId = :arcId AND journeyId = :journeyId")
    suspend fun saveChapterStory(arcId: Long, journeyId: Long, storyId: Long, continuitySummary: String?, updatedAt: Long)
    @Query("UPDATE novella_chapters SET isStale = 1, updatedAt = :updatedAt WHERE arcId = :arcId AND chapterIndex > :chapterIndex")
    suspend fun markLaterChaptersStale(arcId: Long, chapterIndex: Int, updatedAt: Long)
    @Query("UPDATE novella_arcs SET updatedAt = :updatedAt WHERE id = :arcId") suspend fun touchArc(arcId: Long, updatedAt: Long)
    @Query("UPDATE novella_chapters SET storyId = NULL, continuitySummary = NULL, isStale = 1, updatedAt = :updatedAt WHERE journeyId = :journeyId")
    suspend fun clearChapterStory(journeyId: Long, updatedAt: Long)
    @Query("DELETE FROM novella_chapters WHERE arcId = :arcId AND journeyId = :journeyId")
    suspend fun deleteChapter(arcId: Long, journeyId: Long)
    @Query("UPDATE novella_chapters SET chapterIndex = -chapterIndex WHERE arcId = :arcId AND chapterIndex > :chapterIndex")
    suspend fun temporarilyNegateLaterChapterIndexes(arcId: Long, chapterIndex: Int)
    @Query("UPDATE novella_chapters SET chapterIndex = -chapterIndex - 1 WHERE arcId = :arcId AND chapterIndex < 0")
    suspend fun compactNegatedChapterIndexes(arcId: Long)
    @Query("SELECT COUNT(*) FROM novella_chapters WHERE arcId = :arcId")
    suspend fun chapterCount(arcId: Long): Int
    @Query("DELETE FROM novella_arcs WHERE id = :arcId")
    suspend fun deleteArc(arcId: Long)
    @Query(
        """
        UPDATE novella_arcs
        SET
            startDate = (
                SELECT MIN(journeys.date)
                FROM journeys
                INNER JOIN novella_chapters ON novella_chapters.journeyId = journeys.id
                WHERE novella_chapters.arcId = :arcId
            ),
            endDate = (
                SELECT MAX(journeys.date)
                FROM journeys
                INNER JOIN novella_chapters ON novella_chapters.journeyId = journeys.id
                WHERE novella_chapters.arcId = :arcId
            ),
            updatedAt = :updatedAt
        WHERE id = :arcId
        """
    )
    suspend fun refreshArcDateBounds(arcId: Long, updatedAt: Long)

    /** Removes a deleted daily story from its arc and invalidates later continuity. */
    @Transaction
    suspend fun removeStoryForJourney(journeyId: Long, updatedAt: Long) {
        val chapter = chapterForJourney(journeyId) ?: return
        clearChapterStory(journeyId, updatedAt)
        markLaterChaptersStale(chapter.arcId, chapter.chapterIndex, updatedAt)
        touchArc(chapter.arcId, updatedAt)
    }

    /**
     * Detaches a journey from its novella before the journey itself is removed. Later chapters
     * are marked stale, their indexes are compacted, and a one-chapter remnant is dissolved.
     * Call this from the same database transaction as deleting the journey.
     */
    @Transaction
    suspend fun removeJourneyAndRepairArc(journeyId: Long, updatedAt: Long) {
        val chapter = chapterForJourney(journeyId) ?: return
        markLaterChaptersStale(chapter.arcId, chapter.chapterIndex, updatedAt)
        deleteChapter(chapter.arcId, journeyId)
        temporarilyNegateLaterChapterIndexes(chapter.arcId, chapter.chapterIndex)
        compactNegatedChapterIndexes(chapter.arcId)
        if (chapterCount(chapter.arcId) < 2) {
            deleteArc(chapter.arcId)
        } else {
            refreshArcDateBounds(chapter.arcId, updatedAt)
        }
    }

    @Transaction
    suspend fun createArc(arc: NovellaArcEntity, chapters: List<NovellaChapterEntity>): Long {
        val id = insertArc(arc)
        insertChapters(chapters.map { it.copy(arcId = id) })
        return id
    }
}
