package com.example.pixelglass.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.pixelglass.engine.HourglassEngine
import kotlin.math.min

object TickScheduler {
    const val extraAppWidgetId = "appWidgetId"

    private fun pendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, TickReceiver::class.java).putExtra(extraAppWidgetId, appWidgetId)
        return PendingIntent.getBroadcast(
            context, appWidgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(context: Context, appWidgetId: Int) {
        val duration = TimerStore.durationMillis(context, appWidgetId)
        val start = TimerStore.startMillis(context, appWidgetId)
        val now = System.currentTimeMillis()
        val end = start + duration
        if (now >= end) return

        val interval = HourglassEngine.tickIntervalMillis(duration)
        val nextTick = start + ((now - start) / interval + 1) * interval
        val triggerAt = min(nextTick, end) + 50   // small slack so rounding lands on the new pixel

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context, appWidgetId)
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pending)
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pending)
        }
    }

    fun cancel(context: Context, appWidgetId: Int) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .cancel(pendingIntent(context, appWidgetId))
    }
}
