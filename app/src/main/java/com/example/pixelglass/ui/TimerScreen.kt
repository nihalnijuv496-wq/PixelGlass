package com.example.pixelglass.ui

import android.graphics.Color as AndroidColor
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pixelglass.config.HourglassConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val screenBackground = Color(0xFFF7F7F7)
private val chipBorder = Color(0xFFD6D6D6)
private val chipText = Color(0xFF555555)
private val playBlue = Color(0xFF2F80FF)

/** Light, transparent system bars so the screen's background shows through. */
fun ComponentActivity.enableLightEdgeToEdge() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
    )
}

@Composable
fun TimerScreen(
    initialSeconds: Int,
    isRunning: Boolean,
    onStart: (Long) -> Unit,
    onStop: () -> Unit
) {
    var hours by remember { mutableIntStateOf((initialSeconds / 3600) % 24) }
    var minutes by remember { mutableIntStateOf((initialSeconds / 60) % 60) }
    var seconds by remember { mutableIntStateOf(initialSeconds % 60) }
    val totalSeconds = hours * 3600 + minutes * 60 + seconds

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            CurrentClock()
            Text(
                "Timer",
                fontSize = 40.sp,
                color = Color.Black,
                modifier = Modifier.padding(top = 24.dp)
            )
        }

        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            WheelPicker(count = 24, value = hours, onValueChange = { hours = it })
            Colon()
            WheelPicker(count = 60, value = minutes, onValueChange = { minutes = it })
            Colon()
            WheelPicker(count = 60, value = seconds, onValueChange = { seconds = it })
        }
        Spacer(Modifier.weight(1f))

        PresetChips(HourglassConfig.presetMinutes) { presetMinutes ->
            hours = presetMinutes / 60
            minutes = presetMinutes % 60
            seconds = 0
        }

        Spacer(Modifier.height(32.dp))
        PlayButton(enabled = totalSeconds > 0) { onStart(totalSeconds * 1000L) }
        if (isRunning) {
            TextButton(onClick = onStop) { Text("Stop and reset", color = playBlue) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CurrentClock() {
    val context = LocalContext.current
    val formatter = remember {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm:ss" else "hh:mm:ss a"
        SimpleDateFormat(pattern, Locale.getDefault())
    }
    var text by remember { mutableStateOf(formatter.format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            text = formatter.format(Date())
            delay(1000L - System.currentTimeMillis() % 1000L)   // tick right on the second
        }
    }
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.Light, color = Color(0xFF333333))
}

@Composable
private fun Colon() {
    Text(
        ":", fontSize = 48.sp, fontWeight = FontWeight.Medium, color = Color.Black,
        modifier = Modifier.padding(horizontal = 2.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelPicker(count: Int, value: Int, onValueChange: (Int) -> Unit) {
    val itemHeight = 72.dp
    val visibleItems = 5
    val loopCount = 10_000
    val startIndex = (loopCount / 2) / count * count + value

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

    // Report the selected number once the wheel has come to rest.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .collect { onValueChange(centerIndex % count) }
    }

    // A preset chip changed the value from outside: roll the wheel to it the short way round.
    LaunchedEffect(value) {
        val current = centerIndex % count
        if (!listState.isScrollInProgress && current != value) {
            var delta = ((value - current) % count + count) % count
            if (delta > count / 2) delta -= count
            listState.animateScrollToItem(centerIndex + delta - visibleItems / 2)
        }
    }

    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        modifier = Modifier.height(itemHeight * visibleItems).width(84.dp)
    ) {
        items(loopCount) { index ->
            val distance = abs(index - centerIndex)
            Box(
                modifier = Modifier
                    .height(itemHeight)
                    .width(84.dp)
                    .alpha(
                        when (distance) {
                            0 -> 1f
                            1 -> 0.3f
                            else -> 0.12f
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "%02d".format(index % count),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
private fun PresetChips(presetMinutes: List<Int>, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        presetMinutes.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { minutes ->
                    val shape = RoundedCornerShape(24.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(shape)
                            .border(1.dp, chipBorder, shape)
                            .clickable { onPick(minutes) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "%02d:%02d:00".format(minutes / 60, minutes % 60),
                            fontSize = 18.sp,
                            color = chipText
                        )
                    }
                }
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PlayButton(enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .width(110.dp)
            .height(56.dp)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(Color.White)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(28.dp)
                .semantics { contentDescription = "Start timer" }
        ) {
            val triangle = Path().apply {
                moveTo(size.width * 0.15f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(size.width * 0.15f, size.height)
                close()
            }
            drawPath(triangle, playBlue)
        }
    }
}