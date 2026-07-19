package com.example.pluck.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.pluck.domain.model.AiProvider

class DatabaseConverters {
    @TypeConverter fun fromProvider(value: AiProvider): String = value.name
    @TypeConverter fun toProvider(value: String): AiProvider = AiProvider.valueOf(value)
}

@Database(entities = [JourneyEntity::class, JourneyPhotoEntity::class, StoryEntity::class], version = 1, exportSchema = false)
@TypeConverters(DatabaseConverters::class)
abstract class PluckDatabase : RoomDatabase() {
    abstract fun journeyDao(): JourneyDao
    abstract fun journeyPhotoDao(): JourneyPhotoDao
    abstract fun storyDao(): StoryDao
}
