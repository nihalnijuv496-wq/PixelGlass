package com.example.pixelglass.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.example.pixelglass.config.HourglassConfig
import com.example.pixelglass.widget.HourglassWidget
import com.example.pixelglass.widget.TickScheduler
import com.example.pixelglass.widget.TimerService
import com.example.pixelglass.widget.TimerStore
import kotlinx.coroutines.launch

/**
 * The timer picker. Opens when a widget is added and every time you tap a widget:
 * tap a length to start it on that widget, or stop the widget's timer.
 */
class ConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingMinutes: Int? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Start either way; without permission you just won't get the "Time's up" alert.
            pendingMinutes?.let { startTimer(it) }
            pendingMinutes = null
        }

    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val runningMinutes = if (TimerStore.isIdle(this, appWidgetId)) null
        else (TimerStore.durationMillis(this, appWidgetId) / 60_000L).toInt()

        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Choose a timer", style = MaterialTheme.typography.titleLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HourglassConfig.presetMinutes.forEach { preset ->
                            FilterChip(
                                selected = runningMinutes == preset,
                                onClick = { chooseTimer(preset) },
                                label = { Text(if (preset == 60) "1 hr" else "$preset min") }
                            )
                        }
                    }
                    if (runningMinutes != null) {
                        StopButton()
                    }
                }
            }
        }
    }

    @Composable
    private fun StopButton() {
        TextButton(onClick = { stopTimer() }) { Text("Stop and reset") }
    }

    private fun chooseTimer(minutes: Int) {
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingMinutes = minutes
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startTimer(minutes)
        }
    }

    private fun startTimer(minutes: Int) {
        TimerStore.start(this, appWidgetId, minutes * 60_000L)
        TickScheduler.scheduleFinish(this, appWidgetId)   // replaces any earlier alarm for this widget
        TimerService.sync(this)
        refreshWidgetAndClose()
    }

    private fun stopTimer() {
        TickScheduler.cancel(this, appWidgetId)
        TimerStore.stop(this, appWidgetId)
        TimerService.sync(this)
        refreshWidgetAndClose()
    }

    private fun refreshWidgetAndClose() {
        lifecycleScope.launch {
            runCatching {
                val glanceId = GlanceAppWidgetManager(this@ConfigActivity).getGlanceIdBy(appWidgetId)
                HourglassWidget().refresh(this@ConfigActivity, glanceId)
            }
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}