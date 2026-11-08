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

        val isRunning = !TimerStore.isIdle(this, appWidgetId)
        val initialSeconds = if (isRunning) {
            (TimerStore.durationMillis(this, appWidgetId) / 1000L).toInt()
        } else {
            5 * 60
        }

        setContent {
            MaterialTheme {
                TimerScreen(
                    initialSeconds = initialSeconds,
                    isRunning = isRunning,
                    onStart = { chooseTimer(it) },
                    onStop = { stopTimer() }
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

    private fun startTimer(millis: Long) {
        TimerStore.start(this, appWidgetId, millis)
        TickScheduler.scheduleFinish(this, appWidgetId)
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