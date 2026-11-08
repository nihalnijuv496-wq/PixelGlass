package com.example.pixelglass.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.example.pixelglass.config.HourglassConfig as Cfg
import com.example.pixelglass.engine.SandState
import kotlin.math.min
import kotlin.math.sqrt
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader

object HourglassRenderer {
    private val gridSize = Cfg.gridSize
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun render(state: SandState): Bitmap {
        val width = Cfg.bitmapWidthPx
        val height = Cfg.bitmapHeightPx
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawPorcelain(canvas, width.toFloat(), height.toFloat())

        val availableWidth = width * (1 - 2 * Cfg.paddingFraction)
        val availableHeight = height * (1 - 2 * Cfg.paddingFraction)
        val root2 = sqrt(2f)
        val cellsWithPlate = gridSize + 2 * Cfg.platePadding
        val step = min(availableWidth, availableHeight / 2f) / (cellsWithPlate * root2)
        val diamondHeight = cellsWithPlate * step * root2

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
        val plateMargin = step * Cfg.platePadding
        val plateRadius = step * Cfg.plateCornerRadius

        canvas.save()
        canvas.rotate(45f, centerX, centerY)

        paint.color = Cfg.plateColor
        canvas.drawRoundRect(
            centerX - half - plateMargin, centerY - half - plateMargin,
            centerX + half + plateMargin, centerY + half + plateMargin,
            plateRadius, plateRadius, paint
        )

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

    private fun drawPorcelain(canvas: Canvas, width: Float, height: Float) {
        val radius = Cfg.backgroundCornerRadiusPx
        val shape = RectF(0f, 0f, width, height)
        val white = Color.WHITE
        val clear = Color.argb(0, 255, 255, 255)
        val strength = Cfg.glossStrength

        // Body: near-white to soft grey, diagonal
        backgroundPaint.style = Paint.Style.FILL
        backgroundPaint.shader = LinearGradient(
            0f, 0f, width, height, Cfg.porcelainLight, Cfg.porcelainDark, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(shape, radius, radius, backgroundPaint)

        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(shape, radius, radius, Path.Direction.CW) })

        // Soft glare in the top-left
        backgroundPaint.shader = RadialGradient(
            width * 0.25f, height * 0.10f, width * 0.95f,
            Color.argb((255 * strength).toInt(), 255, 255, 255), clear, Shader.TileMode.CLAMP
        )
        canvas.drawRect(shape, backgroundPaint)

        // Glossy band across the upper part, with a curved lower edge
        val band = Path().apply {
            moveTo(0f, 0f)
            lineTo(width, 0f)
            lineTo(width, height * 0.20f)
            quadTo(width * 0.5f, height * 0.36f, 0f, height * 0.20f)
            close()
        }
        backgroundPaint.shader = LinearGradient(
            0f, 0f, 0f, height * 0.34f,
            Color.argb((255 * strength * 0.55f).toInt(), 255, 255, 255), clear, Shader.TileMode.CLAMP
        )
        canvas.drawPath(band, backgroundPaint)
        canvas.restore()

        // Edge bevel: bright on top, grey underneath
        val edge = 4f
        backgroundPaint.style = Paint.Style.STROKE
        backgroundPaint.strokeWidth = edge
        backgroundPaint.shader = LinearGradient(
            0f, 0f, 0f, height, white, Color.argb(0, 120, 120, 128), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            RectF(edge / 2, edge / 2, width - edge / 2, height - edge / 2),
            radius, radius, backgroundPaint
        )
        backgroundPaint.shader = null
        backgroundPaint.style = Paint.Style.FILL
    }
}