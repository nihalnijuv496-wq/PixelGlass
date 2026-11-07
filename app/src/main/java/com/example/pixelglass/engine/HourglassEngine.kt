package com.example.pixelglass.engine

import com.example.pixelglass.config.HourglassConfig as Cfg
import kotlin.random.Random

/**
 * What the widget draws at one instant. Arrays are n*n, index = row * n + col.
 * They are shared with the engine's cache: read them, never modify them.
 */
class SandState(
    val top: BooleanArray,
    val bottom: BooleanArray,
    val flightCell: Int,      // bottom-bulb cell of the grain currently falling, or -1
    val finished: Boolean,
    val idle: Boolean         // no timer running: full top bulb, empty bottom bulb
)

object HourglassEngine {
    private val n = Cfg.gridSize
    val totalPixels = n * n

    private class Frame(val top: BooleanArray, val bottom: BooleanArray, val path: IntArray)

    /** frames[k] = both bulbs after k grains have fallen; path = route the k-th grain took. */
    private val frames: List<Frame> by lazy { buildFrames() }

    // ---------------------------------------------------------------- sand physics

    /**
     * The grid is drawn rotated 45deg, so the two cells directly below (row, col) are
     * (row+1, col) = down-left and (row, col+1) = down-right. A grain rests only when both
     * are filled or off the grid; otherwise it slides into a free one (random pick if both are free).
     */
    private fun nextCell(filled: BooleanArray, r: Int, c: Int, rng: Random): Int {
        val left = if (r + 1 < n && !filled[(r + 1) * n + c]) (r + 1) * n + c else -1
        val right = if (c + 1 < n && !filled[r * n + c + 1]) r * n + c + 1 else -1
        return when {
            left >= 0 && right >= 0 -> if (rng.nextBoolean()) left else right
            left >= 0 -> left
            else -> right
        }
    }

    /** Drops one grain from the top corner (cell 0) of the bottom bulb. Returns its path. */
    private fun dropGrain(bottom: BooleanArray, rng: Random): IntArray {
        if (bottom[0]) return IntArray(0)
        val path = ArrayList<Int>()
        var cell = 0
        path += cell
        while (true) {
            val next = nextCell(bottom, cell / n, cell % n, rng)
            if (next < 0) break
            cell = next
            path += cell
        }
        bottom[cell] = true
        return path.toIntArray()
    }

    /** After the neck cell is emptied, the sand above slides down until everything rests. */
    private fun settleTop(top: BooleanArray, rng: Random) {
        var moved = true
        while (moved) {
            moved = false
            for (sum in 2 * n - 3 downTo 0) {            // lowest rows on screen first
                for (r in maxOf(0, sum - (n - 1))..minOf(n - 1, sum)) {
                    val idx = r * n + (sum - r)
                    if (!top[idx]) continue
                    val next = nextCell(top, r, sum - r, rng)
                    if (next >= 0) {
                        top[idx] = false
                        top[next] = true
                        moved = true
                    }
                }
            }
        }
    }

    private fun buildFrames(): List<Frame> {
        val topRng = Random(Cfg.sandSeed)
        val bottomRng = Random(Cfg.sandSeed + 1)
        val top = BooleanArray(totalPixels) { true }
        val bottom = BooleanArray(totalPixels)
        val list = ArrayList<Frame>()
        list += Frame(top.copyOf(), bottom.copyOf(), IntArray(0))
        repeat(totalPixels) {
            top[totalPixels - 1] = false                 // lowest top cell = the neck
            settleTop(top, topRng)
            val path = dropGrain(bottom, bottomRng)
            list += Frame(top.copyOf(), bottom.copyOf(), path)
        }
        return list
    }

    // ---------------------------------------------------------------- timing

    /** Elapsed millis at which grain k has fallen. One formula, used everywhere, so nothing drifts. */
    private fun boundaryMillis(k: Int, duration: Long): Long =
        (duration * k + totalPixels - 1) / totalPixels

    private fun fallenAt(elapsed: Long, duration: Long): Int =
        ((elapsed.coerceAtLeast(0L) * totalPixels) / duration).toInt().coerceIn(0, totalPixels)

    /** The fall animation only runs when a grain has room to finish before the next one starts. */
    private fun animated(duration: Long): Boolean =
        Cfg.animateFall && duration / totalPixels >= Cfg.minPixelIntervalForAnimationMillis

    fun idleState(): SandState {
        val f = frames[0]
        return SandState(f.top, f.bottom, -1, finished = false, idle = true)
    }

    fun stateAt(elapsedMillis: Long, durationMillis: Long): SandState {
        if (durationMillis <= 0L) return idleState()
        val k = fallenAt(elapsedMillis, durationMillis)
        val frame = frames[k]

        if (k >= 1 && animated(durationMillis)) {
            val step = ((elapsedMillis - boundaryMillis(k, durationMillis)) / Cfg.fallFrameMillis).toInt()
            if (step < frame.path.size) {
                // Grain k is still in the air: bottom bulb shows the pile from before it landed.
                return SandState(frame.top, frames[k - 1].bottom, frame.path[step], finished = false, idle = false)
            }
        }
        return SandState(frame.top, frame.bottom, -1, finished = k >= totalPixels, idle = false)
    }

    /** Elapsed millis at which the picture next changes, or null once the timer is completely done. */
    fun nextChangeMillis(elapsedMillis: Long, durationMillis: Long): Long? {
        if (durationMillis <= 0L) return null
        val k = fallenAt(elapsedMillis, durationMillis)
        if (k >= 1 && animated(durationMillis)) {
            val start = boundaryMillis(k, durationMillis)
            val step = ((elapsedMillis - start) / Cfg.fallFrameMillis).toInt()
            if (step < frames[k].path.size) return start + (step + 1) * Cfg.fallFrameMillis
        }
        return if (k >= totalPixels) null else boundaryMillis(k + 1, durationMillis)
    }
}