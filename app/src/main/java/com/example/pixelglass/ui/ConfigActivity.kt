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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.example.pixelglass.widget.HourglassWidget
import com.example.pixelglass.widget.TickScheduler
import com.example.pixelglass.widget.TimerService
import com.example.pixelglass.widget.TimerStore
import kotlinx.coroutines.launch

/** Opens when a widget is added and every time you tap a widget. */
class ConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingMillis: Long? = null
    private var runningTimer by mutableStateOf<RunningTimer?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Start either way; without permission you just won't get the "Time's up" alert.
            pendingMillis?.let { startTimer(it) }
            pendingMillis = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableLightEdgeToEdge()
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        var initialSeconds = 5 * 60
        if (!TimerStore.isIdle(this, appWidgetId)) {
            val duration = TimerStore.durationMillis(this, appWidgetId)
            runningTimer = RunningTimer(TimerStore.startMillis(this, appWidgetId) + duration, duration)
            initialSeconds = (duration / 1000L).toInt()
        }

        setContent {
            MaterialTheme {
                TimerScreen(
                    initialSeconds = initialSeconds,
                    runningTimer = runningTimer,
                    onStart = { chooseTimer(it) },
                    onReset = { resetTimer() }
                )
            }
        }
    }

    private fun chooseTimer(millis: Long) {
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingMillis = millis
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startTimer(millis)
        }
    }

    /** Starts the timer and returns to the home screen, where the widget is now running. */
    private fun startTimer(millis: Long) {
        TimerStore.start(this, appWidgetId, millis)
        TickScheduler.scheduleFinish(this, appWidgetId)
        TimerService.sync(this)
        lifecycleScope.launch {
            refreshWidget()
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }

    /** Stops the timer and shows the wheels again so a new one can be set. */
    private fun resetTimer() {
        TickScheduler.cancel(this, appWidgetId)
        TimerStore.stop(this, appWidgetId)
        TimerService.sync(this)
        runningTimer = null
        lifecycleScope.launch { refreshWidget() }
    }

    private suspend fun refreshWidget() {
        runCatching {
            val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
            HourglassWidget().refresh(this, glanceId)
        }
    }
}