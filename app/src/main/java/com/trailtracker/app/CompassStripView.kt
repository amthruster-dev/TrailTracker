package com.trailtracker.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

/**
 * Horizontal compass strip showing the top half of a compass: a scrolling
 * band of cardinal/intercardinal headings centered on current device bearing,
 * with a fixed pointer in the middle marking current heading.
 * Call setHeading() on every sensor update to animate live.
 *
 * All sizes are derived from the device's display density (sp/dp) rather
 * than raw pixels, so the cardinal letters stay clearly legible on every
 * screen. Label vertical position is computed from Paint.FontMetrics so
 * the text is guaranteed to sit fully inside the view, never clipped.
 */
class CompassStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var heading: Float = 0f

    private fun sp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private fun dp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = dp(2f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(24f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val northTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(28f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        color = Color.parseColor("#FF5252") // red N, like a real compass
    }
    private val degreeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(13f)
        textAlign = Paint.Align.CENTER
    }
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val bgPaint = Paint()

    private val labels = listOf(
        0 to "N", 45 to "NE", 90 to "E", 135 to "SE",
        180 to "S", 225 to "SW", 270 to "W", 315 to "NW"
    )

    var isDarkMode: Boolean = false
        set(value) {
            field = value
            applyColors()
            invalidate()
        }

    init {
        applyColors()
        setWillNotDraw(false)
    }

    private fun applyColors() {
        if (isDarkMode) {
            bgPaint.color = Color.parseColor("#1B1B1B")
            tickPaint.color = Color.parseColor("#CCCCCC")
            textPaint.color = Color.parseColor("#FFFFFF")
            degreeTextPaint.color = Color.parseColor("#DDDDDD")
            pointerPaint.color = Color.parseColor("#66BB6A")
        } else {
            bgPaint.color = Color.parseColor("#FFFFFF")
            tickPaint.color = Color.parseColor("#555555")
            textPaint.color = Color.parseColor("#000000")
            degreeTextPaint.color = Color.parseColor("#333333")
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
        if (w <= 0f || h <= 0f) return
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        val centerX = w / 2f
        val pxPerDeg = w / 180f

        // Reserve a top band for label text, sized to the tallest label
        // font's actual ascent/descent so nothing is ever clipped, no
        // matter the device density.
        val northMetrics = northTextPaint.fontMetrics
        val labelTextHeight = northMetrics.descent - northMetrics.ascent
        val topPadding = dp(6f)
        // Baseline = top padding + distance from top of text to baseline (-ascent)
        val labelBaselineY = topPadding - northMetrics.ascent

        val tickTop = labelBaselineY + northMetrics.descent + dp(6f)
        val tickBottom = h - dp(18f)
        val pointerTop = h - dp(16f)

        var deg = -100
        while (deg <= 460) {
            val displayDeg = ((deg % 360) + 360) % 360
            val x = centerX + (deg - heading) * pxPerDeg
            if (x in -40f..(w + 40f)) {
                val isMajor = displayDeg % 45 == 0
                val tickTopY = if (isMajor) tickTop else tickTop + (tickBottom - tickTop) * 0.35f
                canvas.drawLine(x, tickBottom, x, tickTopY, tickPaint)

                val label = labels.find { it.first == displayDeg }?.second
                if (label != null) {
                    val paint = if (label == "N") northTextPaint else textPaint
                    canvas.drawText(label, x, labelBaselineY, paint)
                } else if (displayDeg % 30 == 0) {
                    canvas.drawText(displayDeg.toString(), x, labelBaselineY, degreeTextPaint)
                }
            }
            deg += 15
        }

        // Fixed center pointer (current heading) at the very bottom edge.
        val pointerPath = android.graphics.Path().apply {
            moveTo(centerX - dp(8f), pointerTop)
            lineTo(centerX + dp(8f), pointerTop)
            lineTo(centerX, h - dp(2f))
            close()
        }
        canvas.drawPath(pointerPath, pointerPaint)

        // --- DIAGNOSTIC ONLY: temporary, to isolate why label text isn't
        // appearing. Draws a large, high-contrast label at a fixed,
        // hardcoded position with no computed metrics at all. If this does
        // not show up either, drawText itself is failing on this device/
        // build; if it DOES show up, the bug is in the computed position
        // or color logic above, not in text drawing itself. Remove once
        // the real bug is found.
        val debugPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.MAGENTA
            textSize = 60f
            textAlign = Paint.Align.LEFT
            isFakeBoldText = true
        }
        canvas.drawText("TEST-N", 10f, 60f, debugPaint)
    }
}
