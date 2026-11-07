package com.example.pixelglass.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * One exact, device-waking alarm per widget, set for the moment its timer ends.
 * The per-pixel ticks come from TimerService; this alarm is what makes sure you get alerted
 * even if the phone is asleep.
 */
object TickScheduler {
    const val extraAppWidgetId = "appWidgetId"

    private fun pendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, TickReceiver::class.java).putExtra(extraAppWidgetId, appWidgetId)
        return PendingIntent.getBroadcast(
            context, appWidgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun scheduleFinish(context: Context, appWidgetId: Int) {
        if (TimerStore.isIdle(context, appWidgetId)) {
            cancel(context, appWidgetId)
            return
        }
        val end = TimerStore.startMillis(context, appWidgetId) +
                TimerStore.durationMillis(context, appWidgetId)
        if (end <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context, appWidgetId)
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pending)
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pending)
        }
    }

    fun cancel(context: Context, appWidgetId: Int) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .cancel(pendingIntent(context, appWidgetId))
    }
}