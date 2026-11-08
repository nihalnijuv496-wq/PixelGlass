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
import com.example.pixelglass.ui.TimerScreen
import com.example.pixelglass.ui.enableLightEdgeToEdge
import com.example.pixelglass.widget.HourglassWidget
import com.example.pixelglass.widget.TickScheduler
import com.example.pixelglass.widget.TimerService
import com.example.pixelglass.widget.TimerStore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var pendingMillis: Long? = null
    private var running by mutableStateOf(false)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            pendingMillis?.let { startOnAllWidgets(it) }
            pendingMillis = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableLightEdgeToEdge()
        running = TimerStore.timedWidgetIds(this).isNotEmpty()
        setContent {
            MaterialTheme {
                TimerScreen(
                    initialSeconds = 5 * 60,
                    isRunning = running,
                    onStart = { chooseTimer(it) },
                    onStop = { stopOnAllWidgets() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        running = TimerStore.timedWidgetIds(this).isNotEmpty()
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
            running = true
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
            running = false
        }
    }
}