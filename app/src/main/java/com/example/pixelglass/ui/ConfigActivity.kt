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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.example.pixelglass.widget.HourglassWidget
import com.example.pixelglass.widget.TickScheduler
import com.example.pixelglass.widget.TimerService
import com.example.pixelglass.widget.TimerStore
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The timer picker. Opens when a widget is added and every time you tap a widget:
 * scroll to a time and start it on that widget, or stop the widget's timer.
 */
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
            5 * 60   // default: 00:05:00
        }

        setContent {
            MaterialTheme {
                var hours by remember { mutableIntStateOf((initialSeconds / 3600) % 24) }
                var minutes by remember { mutableIntStateOf((initialSeconds / 60) % 60) }
                var seconds by remember { mutableIntStateOf(initialSeconds % 60) }
                val totalMillis = (hours * 3600L + minutes * 60L + seconds) * 1000L

                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WheelPicker(count = 24, initial = hours, onSelected = { hours = it })
                        Colon()
                        WheelPicker(count = 60, initial = minutes, onSelected = { minutes = it })
                        Colon()
                        WheelPicker(count = 60, initial = seconds, onSelected = { seconds = it })
                    }
                    Button(
                        onClick = { chooseTimer(totalMillis) },
                        enabled = totalMillis > 0L
                    ) { Text("Start") }
                    if (isRunning) {
                        TextButton(onClick = { stopTimer() }) { Text("Stop and reset") }
                    }
                }
            }
        }
    }

    @Composable
    private fun Colon() {
        Text(":", fontSize = 40.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(horizontal = 4.dp))
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun WheelPicker(count: Int, initial: Int, onSelected: (Int) -> Unit) {
        val itemHeight = 52.dp
        val visibleItems = 5
        val loopCount = 10_000
        val startIndex = (loopCount / 2) / count * count + initial

        val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex - visibleItems / 2)
        val flingBehavior = rememberSnapFlingBehavior(listState)

        val centerIndex by remember {
            derivedStateOf {
                val info = listState.layoutInfo
                val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
                info.visibleItemsInfo
                    .minByOrNull { abs(it.offset + it.size / 2 - viewportCenter) }?.index ?: startIndex
            }
        }
        LaunchedEffect(centerIndex) { onSelected(centerIndex % count) }

        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier.height(itemHeight * visibleItems).width(80.dp)
        ) {
            items(loopCount) { index ->
                val distance = abs(index - centerIndex)
                Box(
                    modifier = Modifier.height(itemHeight).width(80.dp)
                        .alpha(if (distance == 0) 1f else if (distance == 1) 0.3f else 0.12f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "%02d".format(index % count),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Light
                    )
                }
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