package com.example.pixelglass.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.action.ActionParameters
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import com.example.pixelglass.engine.HourglassEngine
import com.example.pixelglass.render.HourglassRenderer

class HourglassWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val duration = TimerStore.durationMillis(context, appWidgetId)
        val elapsed = System.currentTimeMillis() - TimerStore.startMillis(context, appWidgetId)
        val bitmap = HourglassRenderer.render(HourglassEngine.stateAt(elapsed, duration))

        provideContent {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = "Hourglass timer",
                contentScale = ContentScale.Fit,
                modifier = GlanceModifier.fillMaxSize()
                    .clickable(actionRunCallback<RestartAction>(actionParametersOf()))
            )
        }
    }
}

/** Tap the widget to restart its timer. */
class RestartAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        TimerStore.restart(context, appWidgetId)
        TickScheduler.schedule(context, appWidgetId)
        HourglassWidget().update(context, glanceId)
    }
}
