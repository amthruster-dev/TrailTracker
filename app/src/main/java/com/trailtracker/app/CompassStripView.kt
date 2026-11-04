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
 * screen instead of shrinking to near-invisible on high-density phones.
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
        textSize = sp(22f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val northTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(26f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        color = Color.parseColor("#E53935") // red N, like a real compass
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
        // Make sure the view actually repaints once its size is known,
        // in case the first onDraw happens before layout settles.
        setWillNotDraw(false)
    }

    private fun applyColors() {
        if (isDarkMode) {
            bgPaint.color = Color.parseColor("#1B1B1B")
            tickPaint.color = Color.parseColor("#CCCCCC")
            textPaint.color = Color.WHITE
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
        // pixels per degree — tune so ~180 degrees span the visible width
        val pxPerDeg = w / 180f

        // Layout bands within the strip, top to bottom:
        // labels sit in the upper ~55%, ticks run through the middle,
        // the pointer owns the bottom ~20% — kept clear of all text.
        val labelBaselineY = h * 0.42f
        val tickTop = h * 0.50f
        val tickBottom = h * 0.72f
        val pointerTop = h * 0.78f

        // draw tick marks and labels every 15 degrees across a wide window
        var deg = -100
        while (deg <= 460) {
            val displayDeg = ((deg % 360) + 360) % 360
            val x = centerX + (deg - heading) * pxPerDeg
            if (x in -20f..(w + 20f)) {
                val isMajor = displayDeg % 45 == 0
                val tickTopY = if (isMajor) tickTop - (tickBottom - tickTop) * 0.6f else tickTop
                canvas.drawLine(x, tickBottom, x, tickTopY, tickPaint)

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

        // Fixed center pointer (current heading) drawn at the bottom of the
        // strip, well below the label baseline, so it never obscures text.
        val pointerPath = android.graphics.Path().apply {
            moveTo(centerX - dp(8f), pointerTop)
            lineTo(centerX + dp(8f), pointerTop)
            lineTo(centerX, h - dp(2f))
            close()
        }
        canvas.drawPath(pointerPath, pointerPaint)
    }
}
