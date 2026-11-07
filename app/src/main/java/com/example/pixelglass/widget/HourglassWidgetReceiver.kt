package com.example.pixelglass.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class HourglassWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HourglassWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Android sends this after a reboot too: put the finish alarms back.
        appWidgetIds.forEach { TickScheduler.scheduleFinish(context, it) }
        // May be refused when the app is in the background; the picker restarts it on the next timer.
        TimerService.sync(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach {
            TickScheduler.cancel(context, it)
            TimerStore.remove(context, it)
        }
        TimerService.sync(context)   // stops the service if no timer is left
    }
}