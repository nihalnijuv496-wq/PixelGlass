package com.example.pixelglass.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.example.pixelglass.engine.HourglassEngine
import com.example.pixelglass.render.HourglassRenderer
import com.example.pixelglass.ui.ConfigActivity

class HourglassWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)

        val pickerIntent = Intent(context, ConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("pixelglass://picker/$appWidgetId")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        provideContent {
            currentState<Preferences>()[tickKey]   // read so every tick recomposes

            val state = if (TimerStore.isIdle(context, appWidgetId)) {
                HourglassEngine.idleState()
            } else {
                val elapsed = System.currentTimeMillis() - TimerStore.startMillis(context, appWidgetId)
                HourglassEngine.stateAt(elapsed, TimerStore.durationMillis(context, appWidgetId))
            }
            val bitmap = HourglassRenderer.render(state)

            Image(
                provider = ImageProvider(bitmap),
                contentDescription = "Hourglass timer. Tap to choose a timer.",
                contentScale = ContentScale.Fit,
                modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(pickerIntent))
            )
        }
    }

    suspend fun refresh(context: Context, glanceId: GlanceId) {
        updateAppWidgetState(context, glanceId) { it[tickKey] = System.currentTimeMillis() }
        update(context, glanceId)
    }

    companion object {
        val tickKey = longPreferencesKey("tick")
    }
}