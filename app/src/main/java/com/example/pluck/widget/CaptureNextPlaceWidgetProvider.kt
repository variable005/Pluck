package com.example.pluck.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.pluck.MainActivity
import com.example.pluck.R
import com.example.pluck.data.localai.LocalGemmaWidgetStatus

/**
 * Home-screen entry point for capturing the next place in today's journey.
 *
 * App widgets use [RemoteViews], rather than Compose, because their process and rendering are
 * owned by the launcher. The pending intent is explicit, immutable, and only opens Pluck's
 * capture flow; it does not expose any journey data to the launcher.
 */
class CaptureNextPlaceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, createRemoteViews(context))
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        // Re-apply the compact layout after a launcher resize so every touch target remains live.
        appWidgetManager.updateAppWidget(appWidgetId, createRemoteViews(context))
    }

    private fun createRemoteViews(context: Context): RemoteViews {
        val model = LocalGemmaWidgetStatus.snapshot(context)
        val openCapture = PendingIntent.getActivity(
            context,
            CAPTURE_PENDING_INTENT_REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_CAPTURE_NEXT_PLACE
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return RemoteViews(context.packageName, R.layout.widget_capture_next_place).apply {
            setTextViewText(
                R.id.widget_local_readiness,
                context.getString(
                    if (model.isReady) R.string.widget_local_ready else R.string.widget_local_setup
                )
            )
            setTextViewText(
                R.id.widget_local_details,
                if (model.isReady) {
                    context.getString(R.string.widget_local_details_ready, formatBytes(model.installedBytes))
                } else {
                    context.getString(R.string.widget_local_details_setup)
                }
            )
            setOnClickPendingIntent(R.id.widget_capture_root, openCapture)
            setOnClickPendingIntent(R.id.widget_capture_action, openCapture)
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= GIB -> String.format(java.util.Locale.getDefault(), "%.2f GB", bytes / GIB.toDouble())
        bytes >= MIB -> String.format(java.util.Locale.getDefault(), "%.0f MB", bytes / MIB.toDouble())
        else -> "0 MB"
    }

    companion object {
        /** Action understood by [MainActivity] when the user taps the home-screen widget. */
        const val ACTION_CAPTURE_NEXT_PLACE = "com.example.pluck.action.CAPTURE_NEXT_PLACE"

        /** Refreshes installed Pluck widgets after Local Gemma changes readiness. */
        fun refreshInstalledWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CaptureNextPlaceWidgetProvider::class.java)
            val widgetIds = manager.getAppWidgetIds(component)
            if (widgetIds.isNotEmpty()) {
                val provider = CaptureNextPlaceWidgetProvider()
                widgetIds.forEach { widgetId ->
                    manager.updateAppWidget(widgetId, provider.createRemoteViews(context))
                }
            }
        }

        private const val CAPTURE_PENDING_INTENT_REQUEST_CODE = 4_201
        private const val MIB = 1_024L * 1_024L
        private const val GIB = MIB * 1_024L
    }
}
