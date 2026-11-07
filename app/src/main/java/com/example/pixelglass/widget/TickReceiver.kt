package com.example.pixelglass.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.example.pixelglass.config.HourglassConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Fires when a timer ends: alerts you and makes sure the widget shows the finished glass. */
class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(TickScheduler.extraAppWidgetId, -1)
        if (appWidgetId == -1 || TimerStore.isIdle(context, appWidgetId)) return

        // Ignore a stale alarm left over from a timer that was since replaced.
        val end = TimerStore.startMillis(context, appWidgetId) + TimerStore.durationMillis(context, appWidgetId)
        if (end - System.currentTimeMillis() > 1000L) return

        Notifications.alert(context, appWidgetId)

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                HourglassWidget().refresh(context, glanceId)
                // If the service wasn't running (phone was asleep), draw the final grain's landing too.
                delay(2L * HourglassConfig.gridSize * HourglassConfig.fallFrameMillis + 300L)
                HourglassWidget().refresh(context, glanceId)
            } catch (e: Exception) {
                // widget was removed meanwhile
            } finally {
                pending.finish()
            }
        }
    }
}