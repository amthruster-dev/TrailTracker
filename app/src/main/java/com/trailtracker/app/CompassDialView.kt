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
 * Simple rotating compass dial: a fixed circular face with N/E/S/W labels
 * around the rim, and a needle (red tip = north) that rotates to the
 * device's current heading. Replaces the earlier scrolling-strip compass,
 * which had a stubborn text-visibility bug — this design has far fewer
 * moving parts (one rotation transform, four static labels) and is easy
 * to verify correct by eye.
 */
class CompassDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var headingDeg: Float = 0f

    private fun sp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private fun dp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = sp(16f)
    }
    private val northLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = sp(18f)
        color = Color.parseColor("#FF5252")
    }
    private val needleNorthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF5252")
    }
    private val needleSouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val headingTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = sp(14f)
    }

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
            ringPaint.color = Color.parseColor("#CCCCCC")
            labelPaint.color = Color.parseColor("#FFFFFF")
            needleSouthPaint.color = Color.parseColor("#777777")
            headingTextPaint.color = Color.parseColor("#FFFFFF")
        } else {
            bgPaint.color = Color.parseColor("#FFFFFF")
            ringPaint.color = Color.parseColor("#555555")
            labelPaint.color = Color.parseColor("#000000")
            needleSouthPaint.color = Color.parseColor("#AAAAAA")
            headingTextPaint.color = Color.parseColor("#000000")
        }
    }

    fun setHeading(newHeading: Float) {
        headingDeg = (newHeading + 360) % 360
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val radius = (minOf(w, h) / 2f) - dp(18f)
        if (radius <= 0f) return

        // Dial face
        canvas.drawCircle(cx, cy, radius, bgPaint)
        canvas.drawCircle(cx, cy, radius, ringPaint)

        // Cardinal labels around the rim, fixed in place (dial face doesn't
        // rotate — only the needle does, matching most hiking GPS compasses).
        val labelRadius = radius + dp(14f)
        drawLabelAt(canvas, cx, cy, labelRadius, 0f, "N", northLabelPaint)
        drawLabelAt(canvas, cx, cy, labelRadius, 90f, "E", labelPaint)
        drawLabelAt(canvas, cx, cy, labelRadius, 180f, "S", labelPaint)
        drawLabelAt(canvas, cx, cy, labelRadius, 270f, "W", labelPaint)

        // Needle: rotates opposite the heading so its red tip always points
        // toward true north on screen as the device turns.
        canvas.save()
        canvas.rotate(-headingDeg, cx, cy)
        val needleLen = radius - dp(6f)
        val needleWidth = dp(7f)

        // North half (red)
        val northPath = android.graphics.Path().apply {
            moveTo(cx, cy - needleLen)
            lineTo(cx - needleWidth, cy)
            lineTo(cx + needleWidth, cy)
            close()
        }
        canvas.drawPath(northPath, needleNorthPaint)

        // South half (neutral)
        val southPath = android.graphics.Path().apply {
            moveTo(cx, cy + needleLen)
            lineTo(cx - needleWidth, cy)
            lineTo(cx + needleWidth, cy)
            close()
        }
        canvas.drawPath(southPath, needleSouthPaint)
        canvas.restore()

        // Center hub
        canvas.drawCircle(cx, cy, dp(4f), ringPaint)

        // Numeric heading readout below the dial center
        canvas.drawText(
            "${headingDeg.toInt()}°",
            cx, cy + radius + dp(32f),
            headingTextPaint
        )
    }

    private fun drawLabelAt(
        canvas: Canvas, cx: Float, cy: Float, r: Float, angleDeg: Float, text: String, paint: Paint
    ) {
        val rad = Math.toRadians((angleDeg - 90).toDouble())
        val x = cx + (r * Math.cos(rad)).toFloat()
        val y = cy + (r * Math.sin(rad)).toFloat()
        val metrics = paint.fontMetrics
        val baselineY = y - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, x, baselineY, paint)
    }
}
