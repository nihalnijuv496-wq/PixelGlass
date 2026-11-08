package com.example.pixelglass.config

/** Every tunable lives here. */
object HourglassConfig {
    // Grid
    const val gridSize = 8                 // n x n pixels per bulb
    const val pixelFill = 0.80f            // 0..1, how much of a cell the pixel covers
    const val pixelCornerRadius = 0f    // fraction of cell size
    val plateColor = 0xFF000000.toInt()
    const val platePadding = 0.15f         // plate margin around the grid, as a fraction of cell size
    const val plateCornerRadius = 0f       // 0 = sharp corners

    // Bitmap / layout
    const val bitmapWidthPx = 300
    const val bitmapHeightPx = 600
    const val paddingFraction = 0.06f
    const val backgroundCornerRadiusPx = 48f

    // Colors
    val backgroundColor = 0xFFF4EFEA.toInt()
    val emptyColor = 0xFF3A3A3C.toInt()
    val sandColor = 0xFFFFD60A.toInt()

    // Sand physics
    const val sandSeed = 7L                // decides left/right when a grain has both ways open

    // Falling animation (a grain visibly drops cell by cell before settling)
    const val animateFall = true
    const val fallFrameMillis = 140L       // time per cell step of a falling grain
    const val minPixelIntervalForAnimationMillis = 3000L  // faster timers skip the animation

    // Timers
    val presetMinutes = listOf(1, 5, 10, 15, 25, 60)
}