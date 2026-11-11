package com.example.myapplication.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class GraphicEqView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bandLabels = arrayOf("31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
    val bandLevels = FloatArray(10) { 0.0f }

    private val minDb = -15.0f
    private val maxDb = 15.0f

    var onBandLevelChangedListener: ((bandIndex: Int, dbLevel: Float) -> Unit)? = null

    private var activeBandIndex = -1

    // Paints
    private val axisTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = spToPx(11f)
    }

    private val dbTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = spToPx(11f)
        textAlign = Paint.Align.CENTER
    }

    private val freqTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9CA3AF")
        textSize = spToPx(10f)
        textAlign = Paint.Align.CENTER
    }

    private val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1F2937")
        strokeWidth = dpToPx(1f)
        pathEffect = DashPathEffect(floatArrayOf(dpToPx(4f), dpToPx(4f)), 0f)
    }

    private val trackLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#374151")
        strokeWidth = dpToPx(1.5f)
    }

    private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = dpToPx(3f)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val handleOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val handleInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E5E7EB")
        style = Paint.Style.FILL
    }

    private val curvePath = Path()
    private val fillPath = Path()

    private var customAccentColor: Int? = null

    fun setAccentColor(color: Int) {
        customAccentColor = color
        curvePaint.color = color
        invalidate()
    }

    fun setBandLevels(levels: FloatArray) {
        val count = levels.size.coerceAtMost(bandLevels.size)
        for (i in 0 until count) {
            bandLevels[i] = levels[i].coerceIn(minDb, maxDb)
        }
        invalidate()
    }

    fun setBandLevel(index: Int, levelDb: Float) {
        if (index in bandLevels.indices) {
            bandLevels[index] = levelDb.coerceIn(minDb, maxDb)
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val density = resources.displayMetrics.density
        val leftMargin = 32f * density
        val topMargin = 28f * density
        val bottomMargin = 24f * density
        val rightMargin = 12f * density

        val contentWidth = width - leftMargin - rightMargin
        val contentHeight = height - topMargin - bottomMargin

        if (contentWidth <= 0 || contentHeight <= 0) return

        val bandCount = bandLevels.size
        val colWidth = contentWidth / bandCount

        // 1. Draw Horizontal Grid Lines & Y-Axis Labels
        val ySteps = floatArrayOf(15f, 7.5f, 0f, -7.5f, -15f)
        val yLabels = arrayOf("15", "7.5", "0", "-7.5", "-15")

        for (i in ySteps.indices) {
            val dbVal = ySteps[i]
            val yPos = topMargin + (maxDb - dbVal) / (maxDb - minDb) * contentHeight
            canvas.drawLine(leftMargin, yPos, width - rightMargin, yPos, gridLinePaint)
            canvas.drawText(yLabels[i], 4f * density, yPos + 4f * density, axisTextPaint)
        }

        // Calculate (X, Y) for each band handle
        val pointsX = FloatArray(bandCount)
        val pointsY = FloatArray(bandCount)

        for (i in 0 until bandCount) {
            val cx = leftMargin + i * colWidth + colWidth / 2f
            val db = bandLevels[i]
            val cy = topMargin + (maxDb - db) / (maxDb - minDb) * contentHeight

            pointsX[i] = cx
            pointsY[i] = cy

            // Draw vertical track line
            canvas.drawLine(cx, topMargin, cx, topMargin + contentHeight, trackLinePaint)

            // Draw top dB text above column
            val dbText = String.format("%.1f", db)
            canvas.drawText(dbText, cx, topMargin - 8f * density, dbTextPaint)

            // Draw bottom frequency text
            canvas.drawText(bandLabels[i], cx, height - 6f * density, freqTextPaint)
        }

        // 2. Build Smooth Curve Path & Fill Path
        curvePath.reset()
        fillPath.reset()

        if (bandCount > 0) {
            curvePath.moveTo(pointsX[0], pointsY[0])
            fillPath.moveTo(pointsX[0], topMargin + contentHeight)
            fillPath.lineTo(pointsX[0], pointsY[0])

            for (i in 0 until bandCount - 1) {
                val p1X = pointsX[i]
                val p1Y = pointsY[i]
                val p2X = pointsX[i + 1]
                val p2Y = pointsY[i + 1]

                val controlX1 = p1X + (p2X - p1X) / 2f
                val controlY1 = p1Y
                val controlX2 = p1X + (p2X - p1X) / 2f
                val controlY2 = p2Y

                curvePath.cubicTo(controlX1, controlY1, controlX2, controlY2, p2X, p2Y)
                fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, p2X, p2Y)
            }

            fillPath.lineTo(pointsX[bandCount - 1], topMargin + contentHeight)
            fillPath.close()

            // Draw Gradient Fill under curve
            fillPaint.shader = LinearGradient(
                0f, topMargin, 0f, topMargin + contentHeight,
                Color.parseColor("#66FFFFFF"),
                Color.parseColor("#00121212"),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(fillPath, fillPaint)

            // Draw Curve Line
            canvas.drawPath(curvePath, curvePaint)
        }

        // 3. Draw Handle Circles
        val handleRadius = 10f * density
        val handleInnerRadius = 5f * density

        for (i in 0 until bandCount) {
            val cx = pointsX[i]
            val cy = pointsY[i]

            canvas.drawCircle(cx, cy, handleRadius, handleOuterPaint)
            canvas.drawCircle(cx, cy, handleInnerRadius, handleInnerPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        val density = resources.displayMetrics.density
        val leftMargin = 32f * density
        val topMargin = 28f * density
        val bottomMargin = 24f * density
        val rightMargin = 12f * density

        val contentWidth = width - leftMargin - rightMargin
        val contentHeight = height - topMargin - bottomMargin

        if (contentWidth <= 0 || contentHeight <= 0) return false

        val bandCount = bandLevels.size
        val colWidth = contentWidth / bandCount

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val touchX = event.x
                var closestBand = -1
                var minDistance = Float.MAX_VALUE

                for (i in 0 until bandCount) {
                    val cx = leftMargin + i * colWidth + colWidth / 2f
                    val dist = abs(touchX - cx)
                    if (dist < minDistance) {
                        minDistance = dist
                        closestBand = i
                    }
                }

                if (closestBand != -1 && minDistance < colWidth * 1.5f) {
                    activeBandIndex = closestBand
                    parent?.requestDisallowInterceptTouchEvent(true)
                    updateBandFromTouch(event.y, topMargin, contentHeight)
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeBandIndex in 0 until bandCount) {
                    updateBandFromTouch(event.y, topMargin, contentHeight)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeBandIndex = -1
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun updateBandFromTouch(touchY: Float, topMargin: Float, contentHeight: Float) {
        if (activeBandIndex !in bandLevels.indices) return

        val clampedY = touchY.coerceIn(topMargin, topMargin + contentHeight)
        val normalizedY = (clampedY - topMargin) / contentHeight
        val dbVal = maxDb - normalizedY * (maxDb - minDb)

        // Round to 0.1 dB resolution
        val roundedDb = (Math.round(dbVal * 10f) / 10f).coerceIn(minDb, maxDb)

        if (bandLevels[activeBandIndex] != roundedDb) {
            bandLevels[activeBandIndex] = roundedDb
            invalidate()
            onBandLevelChangedListener?.invoke(activeBandIndex, roundedDb)
        }
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
    private fun spToPx(sp: Float): Float = sp * resources.displayMetrics.scaledDensity
}
