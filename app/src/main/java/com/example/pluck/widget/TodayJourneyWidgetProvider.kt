package com.example.pluck.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.room.withTransaction
import com.example.pluck.MainActivity
import com.example.pluck.R
import com.example.pluck.data.database.PluckDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A small home-screen view of the number of places captured in today's journey.
 *
 * Widgets render through [RemoteViews], so this provider deliberately reads only the count for
 * the current local day. No image, location, address, or story text is given to the launcher.
 * Tapping the widget uses Pluck's existing explicit capture intent.
 */
class TodayJourneyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateAsync(context, appWidgetManager, appWidgetIds, goAsync())
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        // Re-render after resize so the launcher always receives the current journey count.
        updateAsync(context, appWidgetManager, intArrayOf(appWidgetId), goAsync())
    }

    private fun updateAsync(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        pendingResult: BroadcastReceiver.PendingResult? = null
    ) {
        if (appWidgetIds.isEmpty()) {
            pendingResult?.finish()
            return
        }

        // Show a useful, non-blocking placeholder while Room is read on an IO dispatcher.
        appWidgetIds.forEach { widgetId ->
            appWidgetManager.updateAppWidget(widgetId, createRemoteViews(context, null))
        }

        widgetScope.launch {
            try {
                val snapshot = runCatching { TodayJourneyWidgetData.load(context) }.getOrNull()
                appWidgetIds.forEach { widgetId ->
                    appWidgetManager.updateAppWidget(widgetId, createRemoteViews(context, snapshot))
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }

    private fun createRemoteViews(
        context: Context,
        snapshot: TodayJourneyWidgetSnapshot?
    ): RemoteViews {
        val openCapture = PendingIntent.getActivity(
            context,
            CAPTURE_PENDING_INTENT_REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                action = CaptureNextPlaceWidgetProvider.ACTION_CAPTURE_NEXT_PLACE
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return RemoteViews(context.packageName, R.layout.widget_today_journey).apply {
            if (snapshot == null) {
                setTextViewText(R.id.widget_today_count, context.getString(R.string.widget_today_loading))
                setTextViewText(R.id.widget_today_status, context.getString(R.string.widget_today_loading_detail))
            } else {
                setTextViewText(
                    R.id.widget_today_count,
                    context.resources.getQuantityString(
                        R.plurals.widget_today_places_captured,
                        snapshot.photoCount,
                        snapshot.photoCount
                    )
                )
                setTextViewText(
                    R.id.widget_today_status,
                    context.getString(snapshot.statusTextRes)
                )
            }
            setOnClickPendingIntent(R.id.widget_today_root, openCapture)
            setOnClickPendingIntent(R.id.widget_today_action, openCapture)
        }
    }

    companion object {
        /** Refreshes every installed Today Journey widget after a capture is added or removed. */
        fun refreshInstalledWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TodayJourneyWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                TodayJourneyWidgetProvider().updateAsync(context, manager, ids)
            }
        }

        private const val CAPTURE_PENDING_INTENT_REQUEST_CODE = 4_202
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/** A dependency entry point keeps the widget independent of the activity and Compose UI. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TodayJourneyWidgetDependencies {
    fun database(): PluckDatabase
}

/** Read-only data access used only by [TodayJourneyWidgetProvider]. */
private object TodayJourneyWidgetData {
    suspend fun load(context: Context): TodayJourneyWidgetSnapshot = withContext(Dispatchers.IO) {
        val database = EntryPointAccessors.fromApplication(
            context.applicationContext,
            TodayJourneyWidgetDependencies::class.java
        ).database()
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val photoCount = database.withTransaction {
            val journey = database.journeyDao().findByDate(date)
            journey?.let { database.journeyPhotoDao().forJourney(it.id).size } ?: 0
        }
        TodayJourneyWidgetSnapshot(photoCount = photoCount)
    }
}

/** Minimal private state, intentionally limited to the current day's number of captures. */
private data class TodayJourneyWidgetSnapshot(val photoCount: Int) {
    val statusTextRes: Int
        get() = when {
            photoCount == 0 -> R.string.widget_today_status_empty
            photoCount == 1 -> R.string.widget_today_status_one_place
            else -> R.string.widget_today_status_ready
        }
}
