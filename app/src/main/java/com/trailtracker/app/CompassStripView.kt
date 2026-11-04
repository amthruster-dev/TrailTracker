package com.trailtracker.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * Horizontal compass strip showing the top half of a compass: a scrolling
 * band of cardinal/intercardinal headings centered on current device bearing,
 * with a fixed pointer in the middle marking current heading.
 * Call setHeading() on every sensor update to animate live.
 */
class CompassStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var heading: Float = 0f

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 42f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val northTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 46f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        color = Color.parseColor("#E53935") // red N, like a real compass
    }
    private val degreeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val bgPaint = Paint()

    private val labels = listOf(
        0f to "N", 45f to "NE", 90f to "E", 135f to "SE",
        180f to "S", 225f to "SW", 270f to "W", 315f to "NW"
    )

    var isDarkMode: Boolean = false
        set(value) {
            field = value
            applyColors()
            invalidate()
        }

    init {
        applyColors()
    }

    private fun applyColors() {
        if (isDarkMode) {
            bgPaint.color = Color.parseColor("#1B1B1B")
            tickPaint.color = Color.parseColor("#AAAAAA")
            textPaint.color = Color.WHITE
            degreeTextPaint.color = Color.parseColor("#CCCCCC")
            pointerPaint.color = Color.parseColor("#4CAF50")
        } else {
            bgPaint.color = Color.parseColor("#F5F5F5")
            tickPaint.color = Color.parseColor("#666666")
            textPaint.color = Color.BLACK
            degreeTextPaint.color = Color.parseColor("#444444")
            pointerPaint.color = Color.parseColor("#2E7D32")
        }
    }

    fun setHeading(newHeading: Float) {
        heading = (newHeading + 360) % 360
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        val centerX = w / 2f
        // pixels per degree — tune so ~180 degrees span the visible width
        val pxPerDeg = w / 180f

        // Reserve the bottom of the strip for the pointer so it never covers labels
        val labelBaselineY = h * 0.48f
        val tickBottom = h * 0.78f

        // draw tick marks and labels every 15 degrees across a wide window
        var deg = -100
        while (deg <= 460) {
            val displayDeg = ((deg % 360) + 360) % 360
            val x = centerX + (deg - heading) * pxPerDeg
            if (x in -20f..(w + 20f)) {
                val isMajor = displayDeg % 45 == 0
                val tickHeight = if (isMajor) h * 0.22f else h * 0.12f
                canvas.drawLine(x, tickBottom, x, tickBottom - tickHeight, tickPaint)

                val label = labels.find { it.first.toInt() == displayDeg }?.second
                if (label != null) {
                    val paint = if (label == "N") northTextPaint else textPaint
                    canvas.drawText(label, x, labelBaselineY, paint)
                } else if (displayDeg % 30 == 0) {
                    canvas.drawText(displayDeg.toString(), x, labelBaselineY, degreeTextPaint)
                }
            }
            deg += 15
        }

        // Fixed center pointer (current heading) drawn at the very bottom edge,
        // below the label baseline, so it never obscures N/NE/etc. text.
        val pointerTop = h * 0.82f
        val pointerPath = android.graphics.Path().apply {
            moveTo(centerX - 14f, pointerTop)
            lineTo(centerX + 14f, pointerTop)
            lineTo(centerX, h - 2f)
            close()
        }
        canvas.drawPath(pointerPath, pointerPaint)
    }
}
