package com.example.pixelglass.widget

import android.content.Context

/** One timer per widget instance: add another widget to get another timer. */
object TimerStore {
    private fun prefs(context: Context) =
        context.getSharedPreferences("hourglassTimers", Context.MODE_PRIVATE)

    fun start(context: Context, appWidgetId: Int, durationMillis: Long) {
        prefs(context).edit()
            .putLong("duration_$appWidgetId", durationMillis)
            .putLong("start_$appWidgetId", System.currentTimeMillis())
            .apply()
    }

    fun restart(context: Context, appWidgetId: Int) =
        start(context, appWidgetId, durationMillis(context, appWidgetId))

    fun durationMillis(context: Context, appWidgetId: Int): Long =
        prefs(context).getLong("duration_$appWidgetId", 5 * 60_000L)

    fun startMillis(context: Context, appWidgetId: Int): Long =
        prefs(context).getLong("start_$appWidgetId", System.currentTimeMillis())

    fun remove(context: Context, appWidgetId: Int) {
        prefs(context).edit()
            .remove("duration_$appWidgetId")
            .remove("start_$appWidgetId")
            .apply()
    }
}
