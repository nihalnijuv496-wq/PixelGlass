package com.example.pixelglass.widget

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.example.pixelglass.engine.HourglassEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Keeps the widgets ticking while any timer is running. It sleeps until the exact moment the
 * next pixel (or the next frame of a falling grain) is due, redraws the widgets that need it,
 * and stops itself once nothing is left to animate.
 */
class TimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private val due = ConcurrentHashMap<Int, Long>()   // widget id -> wall-clock time of next redraw

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifications.ensureChannels(this)
        ServiceCompat.startForeground(
            this, Notifications.ongoingId, Notifications.ongoing(this),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        // Every start means a timer was just started or changed: forget old schedules and redraw.
        loop?.cancel()
        due.clear()
        loop = scope.launch { runLoop() }
        return START_NOT_STICKY
    }

    private suspend fun runLoop() {
        val glanceManager = GlanceAppWidgetManager(this)
        while (true) {
            val now = System.currentTimeMillis()
            val ids = TimerStore.timedWidgetIds(this)
            due.keys.retainAll(ids.toSet())

            for (id in ids) {
                val dueAt = due[id]
                if (dueAt != null && dueAt > now) continue
                runCatching { HourglassWidget().refresh(this, glanceManager.getGlanceIdBy(id)) }
                    .onFailure { Log.e("PixelGlass", "update failed", it) }
                val start = TimerStore.startMillis(this, id)
                val duration = TimerStore.durationMillis(this, id)
                val next = HourglassEngine.nextChangeMillis(System.currentTimeMillis() - start, duration)
                due[id] = if (next == null) Long.MAX_VALUE else start + next + 10   // +10ms: land just after the boundary
            }

            var nextWake = Long.MAX_VALUE
            for (id in ids) nextWake = min(nextWake, due[id] ?: Long.MAX_VALUE)
            if (nextWake == Long.MAX_VALUE) break
            delay((nextWake - System.currentTimeMillis()).coerceAtLeast(20L))
        }
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Call after starting, changing or stopping a timer, from a foreground screen. */
        fun sync(context: Context) {
            val now = System.currentTimeMillis()
            val running = TimerStore.timedWidgetIds(context).any {
                TimerStore.startMillis(context, it) + TimerStore.durationMillis(context, it) > now
            }
            val intent = Intent(context, TimerService::class.java)
            if (running) runCatching { ContextCompat.startForegroundService(context, intent) }
            else context.stopService(intent)
        }
    }
}

object Notifications {
    const val ongoingId = 1
    private const val doneIdBase = 1000
    private const val runningChannel = "timer_running"
    private const val doneChannel = "timer_done"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(runningChannel, "Timer running", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(doneChannel, "Timer finished", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
            }
        )
    }

    fun ongoing(context: Context): Notification =
        NotificationCompat.Builder(context, runningChannel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Hourglass running")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    fun alert(context: Context, appWidgetId: Int) {
        ensureChannels(context)
        val allowed = Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        val notification = NotificationCompat.Builder(context, doneChannel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Time's up")
            .setContentText("Your hourglass has run out.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(doneIdBase + appWidgetId, notification)
    }
}