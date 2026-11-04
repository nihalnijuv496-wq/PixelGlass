package com.example.pixelglass.engine

import com.example.pixelglass.config.HourglassConfig
import kotlin.math.abs
import kotlin.math.max

data class SandState(val topCount: Int, val bottomCount: Int, val finished: Boolean)

object HourglassEngine {
    private val gridSize = HourglassConfig.gridSize
    val totalPixels = gridSize * gridSize

    /**
     * Cell indices (row * n + col) in the order sand settles: lowest on screen first
     * (the grid is drawn rotated 45deg, so screen height ~ row + col), centre first on ties.
     */
    val fillOrder: List<Int> = (0 until totalPixels).sortedWith(
        compareBy<Int>({ -((it / gridSize) + (it % gridSize)) }, { abs((it % gridSize) - (it / gridSize)) })
    )

    fun stateAt(elapsedMillis: Long, durationMillis: Long): SandState {
        val progress = (elapsedMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
        val fallen = (progress * totalPixels).toInt()
        return SandState(totalPixels - fallen, fallen, fallen >= totalPixels)
    }

    /** One pixel falls per tick: duration / totalPixels. */
    fun tickIntervalMillis(durationMillis: Long): Long =
        max(HourglassConfig.minTickMillis, durationMillis / totalPixels)
}
