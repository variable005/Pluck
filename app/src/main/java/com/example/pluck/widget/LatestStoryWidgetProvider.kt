package com.example.pluck.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.example.pluck.MainActivity
import com.example.pluck.R
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Home-screen widget that gives a short, private glimpse of the user's newest saved story.
 *
 * The receiver loads only a title and an excerpt from Pluck's private database on an I/O
 * coroutine, then hands the resulting [RemoteViews] to the launcher. Tapping the widget opens
 * Pluck with [ACTION_OPEN_STORY], which the app routes directly to its reader.
 */
class LatestStoryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        updateWidgets(context.applicationContext, appWidgetManager, appWidgetIds) {
            pendingResult.finish()
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        val pendingResult = goAsync()
        updateWidgets(context.applicationContext, appWidgetManager, intArrayOf(appWidgetId)) {
            pendingResult.finish()
        }
    }

    private fun updateWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        onFinished: (() -> Unit)? = null
    ) {
        widgetScope.launch {
            try {
                val snapshot = LatestStoryWidgetStatus.snapshot(context)
                appWidgetIds.forEach { appWidgetId ->
                    appWidgetManager.updateAppWidget(
                        appWidgetId,
                        createRemoteViews(context, appWidgetId, snapshot)
                    )
                }
            } finally {
                onFinished?.invoke()
            }
        }
    }

    private fun createRemoteViews(
        context: Context,
        appWidgetId: Int,
        snapshot: LatestStoryWidgetSnapshot?
    ): RemoteViews {
        val openPluck = PendingIntent.getActivity(
            context,
            LATEST_STORY_REQUEST_CODE + appWidgetId,
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_STORY
                data = Uri.parse("pluck://widget/latest-story/${snapshot?.storyId ?: "empty"}")
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                snapshot?.let {
                    putExtra(EXTRA_STORY_ID, it.storyId)
                    putExtra(EXTRA_JOURNEY_ID, it.journeyId)
                }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return RemoteViews(context.packageName, R.layout.widget_latest_story).apply {
            if (snapshot == null) {
                setTextViewText(R.id.widget_story_title, context.getString(R.string.widget_story_empty_title))
                setTextViewText(R.id.widget_story_excerpt, context.getString(R.string.widget_story_empty_excerpt))
                setTextViewText(R.id.widget_story_date, context.getString(R.string.widget_story_empty_date))
                setTextViewText(R.id.widget_story_action, context.getString(R.string.widget_story_open_app))
                setContentDescription(
                    R.id.widget_story_root,
                    context.getString(R.string.widget_story_empty_content_description)
                )
            } else {
                setTextViewText(R.id.widget_story_title, snapshot.title)
                setTextViewText(
                    R.id.widget_story_excerpt,
                    snapshot.excerpt.ifBlank { context.getString(R.string.widget_story_empty_excerpt) }
                )
                setTextViewText(
                    R.id.widget_story_date,
                    context.getString(
                        R.string.widget_story_saved_on,
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(snapshot.createdAt))
                    )
                )
                setTextViewText(R.id.widget_story_action, context.getString(R.string.widget_story_read_story))
                setContentDescription(
                    R.id.widget_story_root,
                    context.getString(R.string.widget_story_content_description, snapshot.title)
                )
            }
            setOnClickPendingIntent(R.id.widget_story_root, openPluck)
            setOnClickPendingIntent(R.id.widget_story_action, openPluck)
        }
    }

    companion object {
        /** Explicit action sent to Pluck when the user taps the latest-story widget. */
        const val ACTION_OPEN_STORY = "com.example.pluck.action.OPEN_STORY"

        /** Optional deep-link context for the eventual reader destination. */
        const val EXTRA_STORY_ID = "com.example.pluck.extra.STORY_ID"

        /** Optional journey context for the eventual reader destination. */
        const val EXTRA_JOURNEY_ID = "com.example.pluck.extra.JOURNEY_ID"

        /** Refreshes every installed latest-story widget after a story is saved or deleted. */
        fun refreshInstalledWidgets(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val component = ComponentName(appContext, LatestStoryWidgetProvider::class.java)
            val widgetIds = manager.getAppWidgetIds(component)
            if (widgetIds.isNotEmpty()) {
                LatestStoryWidgetProvider().updateWidgets(appContext, manager, widgetIds)
            }
        }

        private const val LATEST_STORY_REQUEST_CODE = 4_300
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
