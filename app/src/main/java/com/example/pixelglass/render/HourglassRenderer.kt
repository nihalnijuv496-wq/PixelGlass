package com.example.pixelglass.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.example.pixelglass.config.HourglassConfig as Cfg
import com.example.pixelglass.engine.SandState
import kotlin.math.min
import kotlin.math.sqrt

object HourglassRenderer {
    private val gridSize = Cfg.gridSize
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun render(state: SandState): Bitmap {
        val width = Cfg.bitmapWidthPx
        val height = Cfg.bitmapHeightPx
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        paint.color = Cfg.backgroundColor
        canvas.drawRoundRect(
            0f, 0f, width.toFloat(), height.toFloat(),
            Cfg.backgroundCornerRadiusPx, Cfg.backgroundCornerRadiusPx, paint
        )

        val availableWidth = width * (1 - 2 * Cfg.paddingFraction)
        val availableHeight = height * (1 - 2 * Cfg.paddingFraction)
        val root2 = sqrt(2f)
        val step = min(availableWidth, availableHeight / 2f) / (gridSize * root2)
        val diamondHeight = gridSize * step * root2

        val centerX = width / 2f
        val topCenterY = height / 2f - diamondHeight / 2f
        val bottomCenterY = height / 2f + diamondHeight / 2f

        drawBulb(canvas, centerX, topCenterY, step, state.top, flightCell = -1)
        drawBulb(canvas, centerX, bottomCenterY, step, state.bottom, state.flightCell)
        return bitmap
    }

    private fun drawBulb(
        canvas: Canvas, centerX: Float, centerY: Float, step: Float,
        filled: BooleanArray, flightCell: Int
    ) {
        val half = gridSize * step / 2f
        val inset = step * (1 - Cfg.pixelFill) / 2f
        val radius = step * Cfg.pixelCornerRadius

        canvas.save()
        canvas.rotate(45f, centerX, centerY)
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val index = row * gridSize + col
                paint.color = if (filled[index] || index == flightCell) Cfg.sandColor else Cfg.emptyColor
                val left = centerX - half + col * step
                val top = centerY - half + row * step
                canvas.drawRoundRect(
                    left + inset, top + inset, left + step - inset, top + step - inset,
                    radius, radius, paint
                )
            }
        }
        canvas.restore()
    }
}