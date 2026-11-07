package com.example.pixelglass.widget

import android.content.Context

/**
 * Timer state per widget instance. A widget with no start time is idle (full top bulb).
 * Tapping a widget opens the picker, so one widget can run any timer.
 */
object TimerStore {
    private const val keyDuration = "duration_"
    private const val keyStart = "start_"

    private fun prefs(context: Context) =
        context.getSharedPreferences("hourglassTimers", Context.MODE_PRIVATE)

    fun start(context: Context, appWidgetId: Int, durationMillis: Long) {
        prefs(context).edit()
            .putLong(keyDuration + appWidgetId, durationMillis)
            .putLong(keyStart + appWidgetId, System.currentTimeMillis())
            .apply()
    }

    /** Back to idle: no timer, full top bulb. */
    fun stop(context: Context, appWidgetId: Int) = remove(context, appWidgetId)

    fun durationMillis(context: Context, appWidgetId: Int): Long =
        prefs(context).getLong(keyDuration + appWidgetId, 0L)

    /** 0 means no timer has been started on this widget. */
    fun startMillis(context: Context, appWidgetId: Int): Long =
        prefs(context).getLong(keyStart + appWidgetId, 0L)

    fun isIdle(context: Context, appWidgetId: Int): Boolean =
        startMillis(context, appWidgetId) == 0L || durationMillis(context, appWidgetId) <= 0L

    /** Widgets that have a timer set (running, or finished and still showing the result). */
    fun timedWidgetIds(context: Context): List<Int> =
        prefs(context).all.keys
            .filter { it.startsWith(keyStart) }
            .mapNotNull { it.removePrefix(keyStart).toIntOrNull() }
            .filter { !isIdle(context, it) }

    fun remove(context: Context, appWidgetId: Int) {
        prefs(context).edit()
            .remove(keyDuration + appWidgetId)
            .remove(keyStart + appWidgetId)
            .apply()
    }
}