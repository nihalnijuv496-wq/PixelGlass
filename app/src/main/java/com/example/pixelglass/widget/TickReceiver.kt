package com.example.pixelglass.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(TickScheduler.extraAppWidgetId, -1)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                runCatching {
                    val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                    HourglassWidget().update(context, glanceId)
                    TickScheduler.schedule(context, appWidgetId)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
