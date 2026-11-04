package com.example.pixelglass.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.example.pixelglass.config.HourglassConfig
import com.example.pixelglass.widget.HourglassWidget
import com.example.pixelglass.widget.TickScheduler
import com.example.pixelglass.widget.TimerStore
import kotlinx.coroutines.launch

/** Shown when a widget is added (or reconfigured): pick the timer length. */
class ConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            var minutes by remember { mutableIntStateOf(HourglassConfig.presetMinutes.first()) }
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, androidx.compose.ui.Alignment.CenterVertically)
            ) {
                Text("Timer length (min)", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HourglassConfig.presetMinutes.forEach { preset ->
                        FilterChip(
                            selected = minutes == preset,
                            onClick = { minutes = preset },
                            label = { Text("$preset") }
                        )
                    }
                }
                Button(onClick = { startTimer(appWidgetId, minutes) }) { Text("Start") }
            }
        }
    }

    private fun startTimer(appWidgetId: Int, minutes: Int) {
        TimerStore.start(this, appWidgetId, minutes * 60_000L)
        TickScheduler.schedule(this, appWidgetId)
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@ConfigActivity).getGlanceIdBy(appWidgetId)
            HourglassWidget().update(this@ConfigActivity, glanceId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}
