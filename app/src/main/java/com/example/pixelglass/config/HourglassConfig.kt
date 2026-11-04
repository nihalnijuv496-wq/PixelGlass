package com.example.pixelglass.config

/** Every tunable lives here. */
object HourglassConfig {
    // Grid
    const val gridSize = 8                 // n x n pixels per bulb
    const val pixelFill = 0.80f            // 0..1, how much of a cell the pixel covers
    const val pixelCornerRadius = 0.20f    // fraction of cell size

    // Bitmap / layout
    const val bitmapWidthPx = 300
    const val bitmapHeightPx = 600
    const val paddingFraction = 0.06f
    const val backgroundCornerRadiusPx = 48f

    // Colors
    val backgroundColor = 0xFFC9C9CB.toInt()
    val emptyColor = 0xFF3A3A3C.toInt()
    val sandColor = 0xFFFFD60A.toInt()

    // Behaviour
    const val showStreamPixel = true       // single falling pixel at the neck
    const val minTickMillis = 1000L        // never refresh faster than this
    val presetMinutes = listOf(1, 5, 10, 25, 60)
}
