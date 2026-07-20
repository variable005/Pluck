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

@Database(entities = [JourneyEntity::class, JourneyPhotoEntity::class, StoryEntity::class], version = 2, exportSchema = false)
@TypeConverters(DatabaseConverters::class)
abstract class PluckDatabase : RoomDatabase() {
    abstract fun journeyDao(): JourneyDao
    abstract fun journeyPhotoDao(): JourneyPhotoDao
    abstract fun storyDao(): StoryDao

    companion object {
        /** Preserves existing stories and assigns the original cinematic default on upgrade. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stories ADD COLUMN mood TEXT NOT NULL DEFAULT 'CINEMATIC'")
            }
        }
    }
}
