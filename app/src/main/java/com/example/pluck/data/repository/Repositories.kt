package com.example.pluck.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.room.withTransaction
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.pluck.data.database.JourneyDao
import com.example.pluck.data.database.JourneyEntity
import com.example.pluck.data.database.JourneyLibraryRow
import com.example.pluck.data.database.JourneyPhotoDao
import com.example.pluck.data.database.JourneyPhotoEntity
import com.example.pluck.data.database.PluckDatabase
import com.example.pluck.data.database.StoryDao
import com.example.pluck.data.database.StoryEntity
import com.example.pluck.data.database.StoryWithScenesEntity
import com.example.pluck.data.database.StorySceneEntity
import com.example.pluck.data.database.NovellaDao
import com.example.pluck.data.database.NovellaArcEntity
import com.example.pluck.data.database.NovellaChapterEntity
import com.example.pluck.domain.model.AiProvider
import com.example.pluck.domain.model.HapticMode
import com.example.pluck.domain.model.ThemeMode
import com.example.pluck.domain.model.Journey
import com.example.pluck.domain.model.JourneyLibraryItem
import com.example.pluck.domain.model.JourneyPhoto
import com.example.pluck.domain.model.Story
import com.example.pluck.domain.model.StoryPreview
import com.example.pluck.domain.model.StoryMood
import com.example.pluck.domain.model.StoryCreativeSettings
import com.example.pluck.domain.model.StoryDetail
import com.example.pluck.domain.model.StorySceneReference
import com.example.pluck.domain.model.NovellaArc
import com.example.pluck.domain.model.NovellaArcDetail
import com.example.pluck.domain.model.NovellaChapter
import com.example.pluck.domain.model.ArcGenerationContext
import com.example.pluck.domain.repository.JourneyRepository
import com.example.pluck.domain.repository.SettingsRepository
import com.example.pluck.domain.repository.StoryRepository
import com.example.pluck.domain.repository.NovellaRepository
import com.example.pluck.widget.LatestStoryWidgetProvider
import com.example.pluck.widget.TodayJourneyWidgetProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

private fun JourneyEntity.toDomain() = Journey(id, date, timeZoneId)
private fun JourneyPhotoEntity.toDomain() = JourneyPhoto(id, journeyId, imagePath, timestamp, latitude, longitude, address)
private val creativeSettingsJson = Json { ignoreUnknownKeys = true }

private fun String.toCompanions(): List<String> = runCatching {
    creativeSettingsJson.decodeFromString<List<String>>(this)
}.getOrDefault(emptyList())

private fun StoryEntity.toDomain() = Story(
    id = id,
    journeyId = journeyId,
    title = title,
    content = content,
    provider = provider,
    createdAt = createdAt,
    mood = mood,
    creativeSettings = StoryCreativeSettings(
        genre = genre,
        protagonistName = protagonistName,
        companions = companionsJson.toCompanions()
    )
)
private fun StoryWithScenesEntity.toDomain() = StoryDetail(
    story = story.toDomain(),
    scenes = scenes.sortedBy { it.paragraphIndex }.map { StorySceneReference(it.photoId, it.paragraphIndex) }
)
private fun JourneyLibraryRow.toDomain() = JourneyLibraryItem(
    journey = Journey(journeyId, journeyDate, journeyTimeZoneId),
    photoCount = photoCount,
    coverImagePath = coverImagePath,
    story = if (storyId != null && storyTitle != null && storyProvider != null && storyCreatedAt != null) {
        StoryPreview(storyId, storyTitle, storyProvider, storyCreatedAt, storyMood ?: StoryMood.CINEMATIC, storyGenre)
    } else {
        null
    }
)

@Singleton
class RoomJourneyRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: PluckDatabase,
    private val journeyDao: JourneyDao,
    private val photoDao: JourneyPhotoDao,
    private val novellaDao: NovellaDao
) : JourneyRepository {
    private val demoSeedMutex = Mutex()
    private fun today() = LocalDate.now().toString()

    override fun observeToday(): Flow<Journey?> = journeyDao.observeByDate(today()).map { it?.toDomain() }
    override fun observeJourney(journeyId: Long): Flow<Journey?> = journeyDao.observeById(journeyId).map { it?.toDomain() }
    override fun observeLibrary(): Flow<List<JourneyLibraryItem>> = journeyDao.observeLibrary().map { rows -> rows.map { it.toDomain() } }
    override fun observePhotos(journeyId: Long): Flow<List<JourneyPhoto>> = photoDao.observeForJourney(journeyId).map { list -> list.map { it.toDomain() } }

    override suspend fun getOrCreateToday(): Journey {
        val date = today()
        val existing = journeyDao.findByDate(date)
        val journey = (existing ?: JourneyEntity(date = date, timeZoneId = ZoneId.systemDefault().id, createdAt = System.currentTimeMillis()).let {
            val id = journeyDao.insert(it)
            it.copy(id = id)
        }).toDomain()
        if (existing == null) refreshTodayJourneyWidget()
        return journey
    }

    /**
     * Seeds original, bundled illustration crops as a journey only. No story entity is inserted,
     * so a presenter always demonstrates the real selected-provider generation flow.
     */
    override suspend fun seedDemoJourney(): Journey = demoSeedMutex.withLock {
        val existing = journeyDao.findByDate(DEMO_JOURNEY_DATE)
        if (existing != null) return@withLock existing.toDomain()

        val journey = JourneyEntity(
            date = DEMO_JOURNEY_DATE,
            timeZoneId = ZoneId.systemDefault().id,
            createdAt = System.currentTimeMillis()
        ).let { entity -> entity.copy(id = journeyDao.insert(entity)) }

        val grid = context.assets.open(DEMO_GRID_ASSET).use(BitmapFactory::decodeStream)
            ?: error("The bundled demo journey image could not be read.")
        try {
            require(grid.width >= DEMO_COLUMNS && grid.height >= DEMO_ROWS) {
                "The bundled demo journey image has an invalid grid."
            }
            val directory = File(context.filesDir, "journey_photos/demo_journey").apply { mkdirs() }
            val cellWidth = grid.width / DEMO_COLUMNS
            val cellHeight = grid.height / DEMO_ROWS
            val inset = minOf(8, cellWidth / 16, cellHeight / 16)
            val startTime = LocalDate.now()
                .atTime(7, 30)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            val photos = DEMO_PLACES.mapIndexed { index, place ->
                val row = index / DEMO_COLUMNS
                val column = index % DEMO_COLUMNS
                val tile = Bitmap.createBitmap(
                    grid,
                    column * cellWidth + inset,
                    row * cellHeight + inset,
                    cellWidth - inset * 2,
                    cellHeight - inset * 2
                )
                val image = File(directory, "demo_${(index + 1).toString().padStart(2, '0')}.jpg")
                try {
                    FileOutputStream(image).use { output ->
                        check(tile.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                            "The demo image could not be written."
                        }
                    }
                } finally {
                    tile.recycle()
                }
                JourneyPhotoEntity(
                    journeyId = journey.id,
                    imagePath = image.absolutePath,
                    timestamp = startTime + index * DEMO_INTERVAL_MILLIS,
                    latitude = DEMO_ROUTE_ORIGIN_LATITUDE + index * 0.0017,
                    longitude = DEMO_ROUTE_ORIGIN_LONGITUDE + index * 0.0012,
                    address = place
                )
            }
            for (photo in photos) {
                photoDao.insert(photo)
            }
        } finally {
            grid.recycle()
        }
        journey.toDomain()
    }

    override suspend fun addPhoto(journeyId: Long, imagePath: String, timestamp: Long, latitude: Double?, longitude: Double?, address: String?) {
        photoDao.insert(JourneyPhotoEntity(journeyId = journeyId, imagePath = imagePath, timestamp = timestamp, latitude = latitude, longitude = longitude, address = address))
        refreshTodayJourneyWidget()
    }

    override suspend fun deletePhoto(photo: JourneyPhoto) {
        photoDao.deleteWithSceneReferences(
            JourneyPhotoEntity(
                photo.id,
                photo.journeyId,
                photo.imagePath,
                photo.latitude,
                photo.longitude,
                photo.timestamp,
                photo.address
            )
        )
        // Only remove the private capture after Room committed its scene cleanup and photo row.
        File(photo.imagePath).delete()
        refreshTodayJourneyWidget()
    }

    override suspend fun deleteJourney(journeyId: Long) {
        val imageFiles = database.withTransaction {
            val files = photoDao.forJourney(journeyId).map { File(it.imagePath) }
            novellaDao.removeJourneyAndRepairArc(journeyId, System.currentTimeMillis())
            // Room cascades this to photos, daily stories, and their scene references.
            journeyDao.deleteById(journeyId)
            files
        }
        // The database mutation won, so it is now safe to remove the corresponding files.
        imageFiles.forEach(File::delete)
        refreshTodayJourneyWidget()
    }

    /** Widget rendering must never make an otherwise successful local data write fail. */
    private fun refreshTodayJourneyWidget() {
        runCatching { TodayJourneyWidgetProvider.refreshInstalledWidgets(context) }
    }

    private companion object {
        const val DEMO_JOURNEY_DATE = "2026-01-01"
        const val DEMO_GRID_ASSET = "pluck_demo_journey_grid.png"
        const val DEMO_COLUMNS = 5
        const val DEMO_ROWS = 4
        const val DEMO_INTERVAL_MILLIS = 42L * 60L * 1_000L
        const val DEMO_ROUTE_ORIGIN_LATITUDE = 37.7700
        const val DEMO_ROUTE_ORIGIN_LONGITUDE = -122.4400
        val DEMO_PLACES = listOf(
            "Sunrise apartment window", "Maple Street", "Cedar subway entrance", "Golden Crust bakery", "Juniper Park path",
            "Morning flower stall", "The Lantern Bookshop", "Corner coffee table", "Riverside footbridge", "Northlight Gallery",
            "Skyline rooftop garden", "The old cinema", "Needle & Groove records", "Lantern Alley", "Greenmarket storefront",
            "Blue Hour tram stop", "Hilltop overlook", "The quiet dinner table", "Night train platform", "Home at last"
        )
    }
}

@Singleton
class RoomStoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: PluckDatabase,
    private val storyDao: StoryDao,
    private val novellaDao: NovellaDao
) : StoryRepository {
    override fun observeLatest(journeyId: Long): Flow<Story?> = storyDao.observeLatest(journeyId).map { it?.toDomain() }
    override fun observeLatestDetail(journeyId: Long): Flow<StoryDetail?> = storyDao.observeLatestWithScenes(journeyId).map { it?.toDomain() }
    override suspend fun save(story: Story, scenes: List<StorySceneReference>): Long {
        val storyId = storyDao.insertWithScenes(
            StoryEntity(
                id = story.id,
                journeyId = story.journeyId,
                title = story.title,
                content = story.content,
                provider = story.provider,
                createdAt = story.createdAt,
                mood = story.mood,
                genre = story.creativeSettings.normalized().genre,
                protagonistName = story.creativeSettings.normalized().protagonistName,
                companionsJson = creativeSettingsJson.encodeToString(story.creativeSettings.normalized().companions)
            ),
            scenes = scenes.map { StorySceneEntity(storyId = 0, photoId = it.photoId, paragraphIndex = it.paragraphIndex) }
        )
        refreshLatestStoryWidget()
        return storyId
    }

    override suspend fun deleteStoriesForJourney(journeyId: Long) {
        database.withTransaction {
            val now = System.currentTimeMillis()
            novellaDao.removeStoryForJourney(journeyId, now)
            // story_scenes references stories with ON DELETE CASCADE, so no scene can survive.
            storyDao.deleteForJourney(journeyId)
        }
        refreshLatestStoryWidget()
    }

    /** Widget rendering must never make an otherwise successful local data write fail. */
    private fun refreshLatestStoryWidget() {
        runCatching { LatestStoryWidgetProvider.refreshInstalledWidgets(context) }
    }
}

@Singleton
class RoomNovellaRepository @Inject constructor(
    private val novellaDao: NovellaDao,
    private val storyDao: StoryDao
) : NovellaRepository {
    override fun observeArcs(): Flow<List<NovellaArc>> = novellaDao.observeArcs().map { arcs -> arcs.map { it.toDomain() } }

    override fun observeArcDetail(arcId: Long): Flow<NovellaArcDetail?> = combine(
        novellaDao.observeArc(arcId),
        novellaDao.observeChapters(arcId)
    ) { arc, chapters -> arc?.let { NovellaArcDetail(it.toDomain(), chapters.map { chapter -> chapter.toDomain() }) } }

    override fun observeArcForJourney(journeyId: Long): Flow<NovellaArc?> = novellaDao.observeArcForJourney(journeyId).map { it?.toDomain() }

    override suspend fun createArc(
        title: String,
        journeys: List<Journey>,
        mood: StoryMood,
        creativeSettings: StoryCreativeSettings
    ): Long {
        require(journeys.size >= 2) { "Choose at least two daily journeys for a novella." }
        val ordered = journeys.sortedBy { it.date }
        val alreadyAssigned = ordered.firstOrNull { journey -> novellaDao.chapterForJourney(journey.id) != null }
        require(alreadyAssigned == null) {
            "${alreadyAssigned?.date ?: "One selected day"} already belongs to another novella."
        }
        val normalized = creativeSettings.normalized()
        val now = System.currentTimeMillis()
        return novellaDao.createArc(
            NovellaArcEntity(
                title = title.trim().take(100).ifBlank { "An untitled novella" },
                startDate = ordered.first().date,
                endDate = ordered.last().date,
                mood = mood,
                genre = normalized.genre,
                protagonistName = normalized.protagonistName,
                companionsJson = creativeSettingsJson.encodeToString(normalized.companions),
                createdAt = now,
                updatedAt = now
            ),
            ordered.mapIndexed { index, journey ->
                val existingStory = storyDao.latestForJourney(journey.id)
                NovellaChapterEntity(
                    arcId = 0,
                    journeyId = journey.id,
                    chapterIndex = index + 1,
                    storyId = existingStory?.id,
                    continuitySummary = existingStory?.content?.take(600),
                    updatedAt = now
                )
            }
        )
    }

    override suspend fun generationContextForJourney(journeyId: Long): ArcGenerationContext? {
        val chapter = novellaDao.chapterForJourney(journeyId) ?: return null
        val arc = requireNotNull(novellaDao.arcById(chapter.arcId)) { "This novella no longer exists." }
        val chapters = novellaDao.chaptersForArc(chapter.arcId)
        val previous = chapters.firstOrNull { it.chapterIndex == chapter.chapterIndex - 1 }
        if (previous != null && (previous.storyId == null || previous.isStale)) {
            throw IllegalStateException("Generate Chapter ${previous.chapterIndex} of ${arc.title} before continuing this novella.")
        }
        return ArcGenerationContext(
            arcId = arc.id,
            title = arc.title,
            chapterIndex = chapter.chapterIndex,
            totalChapters = chapters.size,
            previousContinuity = previous?.continuitySummary
        )
    }

    override suspend fun saveGeneratedChapter(journeyId: Long, storyId: Long, continuitySummary: String?) {
        val chapter = novellaDao.chapterForJourney(journeyId) ?: return
        val now = System.currentTimeMillis()
        novellaDao.saveChapterStory(chapter.arcId, journeyId, storyId, continuitySummary, now)
        // Regenerating an existing chapter can invalidate the fictional facts used by later days.
        if (chapter.storyId != null) novellaDao.markLaterChaptersStale(chapter.arcId, chapter.chapterIndex, now)
        novellaDao.touchArc(chapter.arcId, now)
    }
}

private fun NovellaArcEntity.toDomain() = NovellaArc(
    id = id,
    title = title,
    startDate = startDate,
    endDate = endDate,
    mood = mood,
    creativeSettings = StoryCreativeSettings(genre, protagonistName, companionsJson.toCompanions()),
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun NovellaChapterEntity.toDomain() = NovellaChapter(
    arcId = arcId,
    journeyId = journeyId,
    chapterIndex = chapterIndex,
    storyId = storyId,
    continuitySummary = continuitySummary,
    isStale = isStale,
    updatedAt = updatedAt
)

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
