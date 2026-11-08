package com.example.pixelglass

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.example.pixelglass.ui.RunningTimer
import com.example.pixelglass.ui.TimerScreen
import com.example.pixelglass.ui.enableLightEdgeToEdge
import com.example.pixelglass.widget.HourglassWidget
import com.example.pixelglass.widget.TickScheduler
import com.example.pixelglass.widget.TimerService
import com.example.pixelglass.widget.TimerStore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var pendingMillis: Long? = null
    private var runningTimer by mutableStateOf<RunningTimer?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            pendingMillis?.let { startOnAllWidgets(it) }
            pendingMillis = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableLightEdgeToEdge()
        runningTimer = readRunningTimer()
        val initialSeconds = runningTimer?.let { (it.durationMillis / 1000L).toInt() } ?: (5 * 60)
        setContent {
            MaterialTheme {
                TimerScreen(
                    initialSeconds = initialSeconds,
                    runningTimer = runningTimer,
                    onStart = { chooseTimer(it) },
                    onReset = { stopOnAllWidgets() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        runningTimer = readRunningTimer()
    }

    /** The timer that ends soonest; if every timer has finished, the latest one. */
    private fun readRunningTimer(): RunningTimer? {
        val now = System.currentTimeMillis()
        val timers = TimerStore.timedWidgetIds(this).map {
            val duration = TimerStore.durationMillis(this, it)
            RunningTimer(TimerStore.startMillis(this, it) + duration, duration)
        }
        return timers.filter { it.endMillis > now }.minByOrNull { it.endMillis }
            ?: timers.maxByOrNull { it.endMillis }
    }

    private fun chooseTimer(millis: Long) {
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingMillis = millis
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startOnAllWidgets(millis)
        }
    }

    private fun startOnAllWidgets(millis: Long) {
        lifecycleScope.launch {
            val manager = GlanceAppWidgetManager(this@MainActivity)
            val glanceIds = manager.getGlanceIds(HourglassWidget::class.java)
            if (glanceIds.isEmpty()) {
                Toast.makeText(
                    this@MainActivity, "Add the PixelGlass widget to your home screen first",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            glanceIds.forEach { glanceId ->
                val appWidgetId = manager.getAppWidgetId(glanceId)
                TimerStore.start(this@MainActivity, appWidgetId, millis)
                TickScheduler.scheduleFinish(this@MainActivity, appWidgetId)
            }
            TimerService.sync(this@MainActivity)
            glanceIds.forEach { runCatching { HourglassWidget().refresh(this@MainActivity, it) } }
            runningTimer = readRunningTimer()
        }
    }

    private fun stopOnAllWidgets() {
        lifecycleScope.launch {
            val manager = GlanceAppWidgetManager(this@MainActivity)
            val glanceIds = manager.getGlanceIds(HourglassWidget::class.java)
            glanceIds.forEach { glanceId ->
                val appWidgetId = manager.getAppWidgetId(glanceId)
                TickScheduler.cancel(this@MainActivity, appWidgetId)
                TimerStore.stop(this@MainActivity, appWidgetId)
            }
            TimerService.sync(this@MainActivity)
            glanceIds.forEach { runCatching { HourglassWidget().refresh(this@MainActivity, it) } }
            runningTimer = null
        }
    }
}