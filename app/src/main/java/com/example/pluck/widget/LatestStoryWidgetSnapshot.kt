package com.example.pluck.widget

import android.content.Context
import com.example.pluck.R
import com.example.pluck.data.database.PluckDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The minimal private story data a launcher widget is allowed to render.
 *
 * This deliberately excludes photos, addresses, coordinates, provider credentials, and the
 * complete story. The launcher only receives the title and a short piece of prose requested by
 * the widget layout.
 */
data class LatestStoryWidgetSnapshot(
    val storyId: Long,
    val journeyId: Long,
    val title: String,
    val excerpt: String,
    val createdAt: Long
)

/** Provides the app's existing Room instance to a non-Hilt [android.appwidget.AppWidgetProvider]. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface LatestStoryWidgetDatabaseEntryPoint {
    fun database(): PluckDatabase
}

/**
 * Reads one small, non-sensitive projection for the latest-story widget.
 *
 * App widgets are hosted by the launcher but this lookup always runs inside Pluck's process and
 * reads the app-private Room database. Keeping the SQL here avoids widening the repository API
 * solely for a launcher surface.
 */
object LatestStoryWidgetStatus {
    /** Returns the most recently saved story, or `null` when Pluck has no stories yet. */
    suspend fun snapshot(context: Context): LatestStoryWidgetSnapshot? = withContext(Dispatchers.IO) {
        runCatching {
            val database = EntryPointAccessors.fromApplication(
                context.applicationContext,
                LatestStoryWidgetDatabaseEntryPoint::class.java
            ).database()
            database.openHelper.readableDatabase.query(LATEST_STORY_QUERY).use { cursor ->
                if (!cursor.moveToFirst()) return@withContext null

                LatestStoryWidgetSnapshot(
                    storyId = cursor.getLong(COLUMN_ID),
                    journeyId = cursor.getLong(COLUMN_JOURNEY_ID),
                    title = cursor.getString(COLUMN_TITLE).trim().ifBlank {
                        context.getString(R.string.widget_story_untitled_title)
                    },
                    excerpt = cursor.getString(COLUMN_CONTENT).toWidgetExcerpt(),
                    createdAt = cursor.getLong(COLUMN_CREATED_AT)
                )
            }
        }.getOrNull()
    }

    private fun String?.toWidgetExcerpt(): String {
        val normalized = this.orEmpty().replace(WHITESPACE, " ").trim()
        if (normalized.length <= MAX_EXCERPT_LENGTH) return normalized
        return normalized.take(MAX_EXCERPT_LENGTH)
            .substringBeforeLast(' ', missingDelimiterValue = normalized.take(MAX_EXCERPT_LENGTH))
            .trimEnd() + ELLIPSIS
    }

    private const val LATEST_STORY_QUERY = """
        SELECT id, journeyId, title, SUBSTR(content, 1, 240), createdAt
        FROM stories
        ORDER BY createdAt DESC, id DESC
        LIMIT 1
    """
    private const val COLUMN_ID = 0
    private const val COLUMN_JOURNEY_ID = 1
    private const val COLUMN_TITLE = 2
    private const val COLUMN_CONTENT = 3
    private const val COLUMN_CREATED_AT = 4
    private const val MAX_EXCERPT_LENGTH = 180
    private const val ELLIPSIS = "…"
    private val WHITESPACE = Regex("\\s+")
}
