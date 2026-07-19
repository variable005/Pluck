package com.example.pluck.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.pluck.data.database.JourneyDao
import com.example.pluck.data.database.JourneyEntity
import com.example.pluck.data.database.JourneyLibraryRow
import com.example.pluck.data.database.JourneyPhotoDao
import com.example.pluck.data.database.JourneyPhotoEntity
import com.example.pluck.data.database.StoryDao
import com.example.pluck.data.database.StoryEntity
import com.example.pluck.domain.model.AiProvider
import com.example.pluck.domain.model.HapticMode
import com.example.pluck.domain.model.ThemeMode
import com.example.pluck.domain.model.Journey
import com.example.pluck.domain.model.JourneyLibraryItem
import com.example.pluck.domain.model.JourneyPhoto
import com.example.pluck.domain.model.Story
import com.example.pluck.domain.model.StoryPreview
import com.example.pluck.domain.repository.JourneyRepository
import com.example.pluck.domain.repository.SettingsRepository
import com.example.pluck.domain.repository.StoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

private fun JourneyEntity.toDomain() = Journey(id, date, timeZoneId)
private fun JourneyPhotoEntity.toDomain() = JourneyPhoto(id, journeyId, imagePath, timestamp, latitude, longitude, address)
private fun StoryEntity.toDomain() = Story(id, journeyId, title, content, provider, createdAt)
private fun JourneyLibraryRow.toDomain() = JourneyLibraryItem(
    journey = Journey(journeyId, journeyDate, journeyTimeZoneId),
    photoCount = photoCount,
    coverImagePath = coverImagePath,
    story = if (storyId != null && storyTitle != null && storyProvider != null && storyCreatedAt != null) {
        StoryPreview(storyId, storyTitle, storyProvider, storyCreatedAt)
    } else {
        null
    }
)

@Singleton
class RoomJourneyRepository @Inject constructor(
    private val journeyDao: JourneyDao,
    private val photoDao: JourneyPhotoDao
) : JourneyRepository {
    private fun today() = LocalDate.now().toString()

    override fun observeToday(): Flow<Journey?> = journeyDao.observeByDate(today()).map { it?.toDomain() }
    override fun observeJourney(journeyId: Long): Flow<Journey?> = journeyDao.observeById(journeyId).map { it?.toDomain() }
    override fun observeLibrary(): Flow<List<JourneyLibraryItem>> = journeyDao.observeLibrary().map { rows -> rows.map { it.toDomain() } }
    override fun observePhotos(journeyId: Long): Flow<List<JourneyPhoto>> = photoDao.observeForJourney(journeyId).map { list -> list.map { it.toDomain() } }

    override suspend fun getOrCreateToday(): Journey {
        val date = today()
        return (journeyDao.findByDate(date) ?: JourneyEntity(date = date, timeZoneId = ZoneId.systemDefault().id, createdAt = System.currentTimeMillis()).let {
            val id = journeyDao.insert(it)
            it.copy(id = id)
        }).toDomain()
    }

    override suspend fun addPhoto(journeyId: Long, imagePath: String, timestamp: Long, latitude: Double?, longitude: Double?, address: String?) {
        photoDao.insert(JourneyPhotoEntity(journeyId = journeyId, imagePath = imagePath, timestamp = timestamp, latitude = latitude, longitude = longitude, address = address))
    }

    override suspend fun deletePhoto(photo: JourneyPhoto) {
        File(photo.imagePath).delete()
        photoDao.delete(JourneyPhotoEntity(photo.id, photo.journeyId, photo.imagePath, photo.latitude, photo.longitude, photo.timestamp, photo.address))
    }
}

@Singleton
class RoomStoryRepository @Inject constructor(private val storyDao: StoryDao) : StoryRepository {
    override fun observeLatest(journeyId: Long): Flow<Story?> = storyDao.observeLatest(journeyId).map { it?.toDomain() }
    override suspend fun save(story: Story): Long = storyDao.insert(StoryEntity(story.id, story.journeyId, story.title, story.content, story.provider, story.createdAt))
}

/** Stores configuration and API secrets in an Android Keystore-backed encrypted preference file. */
@Singleton
class SecureSettingsRepository @Inject constructor(@ApplicationContext context: Context) : SettingsRepository {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "pluck_secure_settings",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun observeProvider(): Flow<AiProvider> = callbackFlow {
        fun current() = AiProvider.entries.firstOrNull { it.name == preferences.getString(PROVIDER_KEY, AiProvider.GEMINI.name) } ?: AiProvider.GEMINI
        trySend(current())
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == PROVIDER_KEY) trySend(current()) }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override fun observeHapticMode(): Flow<HapticMode> = callbackFlow {
        fun current() = HapticMode.entries.firstOrNull {
            it.name == preferences.getString(HAPTIC_MODE_KEY, HapticMode.ESSENTIAL.name)
        } ?: HapticMode.ESSENTIAL
        trySend(current())
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == HAPTIC_MODE_KEY) trySend(current())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override fun observeThemeMode(): Flow<ThemeMode> = callbackFlow {
        fun current() = ThemeMode.entries.firstOrNull {
            it.name == preferences.getString(THEME_MODE_KEY, ThemeMode.SYSTEM.name)
        } ?: ThemeMode.SYSTEM
        trySend(current())
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == THEME_MODE_KEY) trySend(current())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override fun observeDynamicColor(): Flow<Boolean> = callbackFlow {
        fun current() = preferences.getBoolean(DYNAMIC_COLOR_KEY, true)
        trySend(current())
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == DYNAMIC_COLOR_KEY) trySend(current())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override suspend fun setProvider(provider: AiProvider) { preferences.edit().putString(PROVIDER_KEY, provider.name).apply() }
    override suspend fun setHapticMode(mode: HapticMode) { preferences.edit().putString(HAPTIC_MODE_KEY, mode.name).apply() }
    override suspend fun setThemeMode(mode: ThemeMode) { preferences.edit().putString(THEME_MODE_KEY, mode.name).apply() }
    override suspend fun setDynamicColor(enabled: Boolean) { preferences.edit().putBoolean(DYNAMIC_COLOR_KEY, enabled).apply() }
    override suspend fun apiKey(provider: AiProvider): String? = preferences.getString(keyFor(provider), null)?.trim()?.takeIf { it.isNotEmpty() }
    override suspend fun saveApiKey(provider: AiProvider, key: String) { preferences.edit().putString(keyFor(provider), key.trim()).apply() }

    private fun keyFor(provider: AiProvider) = "api_key_${provider.name.lowercase()}"
    private companion object {
        const val PROVIDER_KEY = "preferred_provider"
        const val HAPTIC_MODE_KEY = "haptic_mode"
        const val THEME_MODE_KEY = "theme_mode"
        const val DYNAMIC_COLOR_KEY = "dynamic_color"
    }
}
