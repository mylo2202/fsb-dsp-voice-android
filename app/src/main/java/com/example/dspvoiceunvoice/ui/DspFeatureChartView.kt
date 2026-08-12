package com.example.dspvoiceunvoice.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.dspvoiceunvoice.dsp.AnalysisResult

class DspFeatureChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var analysisResult: AnalysisResult? = null
    private var playheadTimeSec: Float = 0f

    // Colors
    private val colorSte = Color.parseColor("#EF4444")     // Red for STE
    private val colorZcr = Color.parseColor("#10B981")     // Emerald for ZCR
    private val colorRmax = Color.parseColor("#8B5CF6")    // Purple for ACF R_max

    private val colorThreshold = Color.parseColor("#F59E0B") // Amber dash line for Thresholds
    private val colorPlayhead = Color.parseColor("#06B6D4")  // Cyan line for playhead
    private val colorGrid = Color.parseColor("#1E293B")
    private val colorText = Color.parseColor("#94A3B8")

    // Paints
    private val stePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorSte
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val zcrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorZcr
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val rmaxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorRmax
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val thresholdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorThreshold
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }
    private val playheadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorPlayhead
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorGrid
        strokeWidth = 1.5f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorText
        textSize = 24f
    }

    private val featurePath = Path()

    var onSeekListener: ((Float) -> Unit)? = null

    fun setAnalysisResult(result: AnalysisResult?) {
        this.analysisResult = result
        this.playheadTimeSec = 0f
        invalidate()
    }

    fun setPlayheadPosition(timeSec: Float) {
        this.playheadTimeSec = timeSec
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val result = analysisResult ?: return
        if (result.frameTimes.isEmpty()) return

        val totalTime = result.timeAxis.lastOrNull()?.coerceAtLeast(0.1f) ?: 1f

        // Draw 3 sub-panels for 1) STE, 2) ZCR, 3) ACF R_max
        val panelH = h / 3f

        // Panel 1: Short-Time Energy (STE)
        drawFeaturePanel(
            canvas, 0f, panelH, w,
            title = "1. Short-Time Energy (STE)",
            data = result.ste,
            threshold = result.thresholds.tE,
            thresholdLabel = String.format("T_E = %.4f", result.thresholds.tE),
            paint = stePaint,
            totalTime = totalTime,
            frameTimes = result.frameTimes
        )

        // Panel 2: Zero-Crossing Rate (ZCR)
        drawFeaturePanel(
            canvas, panelH, panelH, w,
            title = "2. Zero-Crossing Rate (ZCR)",
            data = result.zcr,
            threshold = result.thresholds.tZcr,
            thresholdLabel = String.format("T_ZCR = %.3f", result.thresholds.tZcr),
            paint = zcrPaint,
            totalTime = totalTime,
            frameTimes = result.frameTimes
        )

        // Panel 3: Autocorrelation Peak (ACF R_max)
        drawFeaturePanel(
            canvas, panelH * 2f, panelH, w,
            title = "3. Autocorrelation Peak (ACF R_max)",
            data = result.rMax,
            threshold = result.thresholds.tR,
            thresholdLabel = String.format("T_R = %.2f", result.thresholds.tR),
            paint = rmaxPaint,
            totalTime = totalTime,
            frameTimes = result.frameTimes,
            maxFixedVal = 1.0f
        )

        // Draw Playhead across all 3 panels
        val playheadX = (playheadTimeSec / totalTime).coerceIn(0f, 1f) * w
        canvas.drawLine(playheadX, 0f, playheadX, h, playheadPaint)
    }

    private fun drawFeaturePanel(
        canvas: Canvas,
        topY: Float,
        panelH: Float,
        w: Float,
        title: String,
        data: FloatArray,
        threshold: Float,
        thresholdLabel: String,
        paint: Paint,
        totalTime: Float,
        frameTimes: FloatArray,
        maxFixedVal: Float? = null
    ) {
        val bottomY = topY + panelH
        // Draw grid outline
        canvas.drawLine(0f, bottomY - 1f, w, bottomY - 1f, gridPaint)

        textPaint.color = colorText
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(title, 12f, topY + 30f, textPaint)

        if (data.isEmpty()) return

        val maxVal = maxFixedVal ?: (data.maxOrNull()?.coerceAtLeast(threshold * 1.2f) ?: 1f).coerceAtLeast(1e-5f)
        val minVal = 0f

        // Draw curve
        featurePath.reset()
        for (i in data.indices) {
            val t = frameTimes.getOrElse(i) { 0f }
            val x = (t / totalTime) * w
            val valNorm = ((data[i] - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val y = bottomY - 10f - (valNorm * (panelH - 40f))

            if (i == 0) featurePath.moveTo(x, y)
            else featurePath.lineTo(x, y)
        }
        canvas.drawPath(featurePath, paint)

        // Draw Threshold Line
        if (threshold in minVal..maxVal) {
            val threshNorm = ((threshold - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val threshY = bottomY - 10f - (threshNorm * (panelH - 40f))

            canvas.drawLine(0f, threshY, w, threshY, thresholdPaint)
            textPaint.color = colorThreshold
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(thresholdLabel, w - 16f, threshY - 6f, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val result = analysisResult ?: return super.onTouchEvent(event)
        val totalTime = result.timeAxis.lastOrNull() ?: return super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val seekTime = ((event.x / width.toFloat()) * totalTime).coerceIn(0f, totalTime)
                playheadTimeSec = seekTime
                invalidate()
                onSeekListener?.invoke(seekTime)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
