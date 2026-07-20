package com.example.pluck.data.database

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.pluck.domain.model.AiProvider
import com.example.pluck.domain.model.StoryMood

class DatabaseConverters {
    @TypeConverter fun fromProvider(value: AiProvider): String = value.name
    @TypeConverter fun toProvider(value: String): AiProvider = AiProvider.valueOf(value)
    @TypeConverter fun fromMood(value: StoryMood): String = value.name
    @TypeConverter fun toMood(value: String): StoryMood = StoryMood.entries.firstOrNull { it.name == value } ?: StoryMood.CINEMATIC
}

@Database(
    entities = [
        JourneyEntity::class,
        JourneyPhotoEntity::class,
        StoryEntity::class,
        StorySceneEntity::class,
        NovellaArcEntity::class,
        NovellaChapterEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(DatabaseConverters::class)
abstract class PluckDatabase : RoomDatabase() {
    abstract fun journeyDao(): JourneyDao
    abstract fun journeyPhotoDao(): JourneyPhotoDao
    abstract fun storyDao(): StoryDao
    abstract fun novellaDao(): NovellaDao

    companion object {
        /** Preserves existing stories and assigns the original cinematic default on upgrade. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stories ADD COLUMN mood TEXT NOT NULL DEFAULT 'CINEMATIC'")
            }
        }

        /** Adds per-story creative direction and structured reality-to-fiction scene anchors. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Room's entity schema declares an explicit NULL default for both nullable
                // creative fields. Keep the migration byte-for-byte compatible so existing
                // version 2 installations validate without rebuilding (or losing) stories.
                db.execSQL("ALTER TABLE stories ADD COLUMN genre TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE stories ADD COLUMN protagonistName TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE stories ADD COLUMN companionsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS story_scenes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        storyId INTEGER NOT NULL,
                        photoId INTEGER NOT NULL,
                        paragraphIndex INTEGER NOT NULL,
                        FOREIGN KEY(storyId) REFERENCES stories(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_story_scenes_storyId ON story_scenes(storyId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_story_scenes_storyId_paragraphIndex ON story_scenes(storyId, paragraphIndex)")
            }
        }

        /** Adds local-only multi-day novella membership without changing daily journeys. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS novella_arcs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        startDate TEXT NOT NULL,
                        endDate TEXT NOT NULL,
                        mood TEXT NOT NULL,
                        genre TEXT,
                        protagonistName TEXT,
                        companionsJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_novella_arcs_startDate_endDate ON novella_arcs(startDate, endDate)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS novella_chapters (
                        arcId INTEGER NOT NULL,
                        journeyId INTEGER NOT NULL,
                        chapterIndex INTEGER NOT NULL,
                        storyId INTEGER,
                        continuitySummary TEXT,
                        isStale INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(arcId, journeyId),
                        FOREIGN KEY(arcId) REFERENCES novella_arcs(id) ON DELETE CASCADE,
                        FOREIGN KEY(journeyId) REFERENCES journeys(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_novella_chapters_arcId_chapterIndex ON novella_chapters(arcId, chapterIndex)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_novella_chapters_journeyId ON novella_chapters(journeyId)")
            }
        }
    }
}
