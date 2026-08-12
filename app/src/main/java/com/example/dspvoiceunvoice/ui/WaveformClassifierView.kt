package com.example.dspvoiceunvoice.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.dspvoiceunvoice.dsp.AnalysisResult
import kotlin.math.max

class WaveformClassifierView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var analysisResult: AnalysisResult? = null
    private var playheadTimeSec: Float = 0f

    // Colors
    private val colorVoice = Color.parseColor("#3322C55E")   // Translucent green
    private val colorUnvoice = Color.parseColor("#33F59E0B") // Translucent orange
    private val colorSilence = Color.parseColor("#1564748B") // Translucent gray

    private val colorWaveform = Color.parseColor("#38BDF8") // Vibrant cyan/blue
    private val colorPlayhead = Color.parseColor("#06B6D4") // Bright cyan cursor
    private val colorGrid = Color.parseColor("#1E293B")
    private val colorText = Color.parseColor("#94A3B8")

    // Paints
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val waveformPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorWaveform
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
    }
    private val playheadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorPlayhead
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    private val playheadDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorPlayhead
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorGrid
        strokeWidth = 1.5f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorText
        textSize = 28f
    }

    private val waveformPath = Path()

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

        val paddingBottom = 40f
        val chartH = h - paddingBottom
        val centerY = chartH / 2f

        val result = analysisResult
        if (result == null || result.timeAxis.isEmpty()) {
            // Placeholder text
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("Chưa có dữ liệu phân tích", w / 2f, h / 2f, textPaint)
            return
        }

        val totalTime = result.timeAxis.last().coerceAtLeast(0.1f)

        // 1. Draw classification background regions (Voice/Unvoice/Silence)
        val frameTimes = result.frameTimes
        val labels = result.labels
        if (frameTimes.isNotEmpty() && labels.isNotEmpty()) {
            val frameStepSec = if (frameTimes.size > 1) (frameTimes[1] - frameTimes[0]) else 0.01f

            for (i in labels.indices) {
                val label = labels[i]
                val tStart = (frameTimes[i] - frameStepSec / 2f).coerceAtLeast(0f)
                val tEnd = frameTimes[i] + frameStepSec / 2f

                val x1 = (tStart / totalTime) * w
                val x2 = (tEnd / totalTime) * w

                bgPaint.color = when (label) {
                    2 -> colorVoice
                    1 -> colorUnvoice
                    else -> colorSilence
                }
                canvas.drawRect(x1, 0f, x2, chartH, bgPaint)
            }
        }

        // 2. Draw Grid & Time ticks
        val numTicks = 5
        textPaint.textAlign = Paint.Align.LEFT
        for (i in 0..numTicks) {
            val t = (totalTime * i) / numTicks
            val x = (i.toFloat() / numTicks) * w
            canvas.drawLine(x, 0f, x, chartH, gridPaint)
            val timeLabel = String.format("%.1fs", t)
            val drawX = if (i == numTicks) x - 60f else x + 6f
            canvas.drawText(timeLabel, drawX, h - 10f, textPaint)
        }

        // Center line
        canvas.drawLine(0f, centerY, w, centerY, gridPaint)

        // 3. Draw Waveform
        val waveform = result.rawWaveform
        if (waveform.isNotEmpty()) {
            waveformPath.reset()

            val step = max(1, waveform.size / w.toInt())
            var isFirst = true

            for (xPixel in 0 until w.toInt()) {
                val sampleIdx = ((xPixel / w) * waveform.size).toInt().coerceIn(0, waveform.size - 1)
                var minVal = waveform[sampleIdx]
                var maxVal = waveform[sampleIdx]

                for (j in 0 until step) {
                    val idx = (sampleIdx + j).coerceAtMost(waveform.size - 1)
                    val valJ = waveform[idx]
                    if (valJ < minVal) minVal = valJ
                    if (valJ > maxVal) maxVal = valJ
                }

                val yMin = centerY - (minVal * (chartH / 2f) * 0.9f)
                val yMax = centerY - (maxVal * (chartH / 2f) * 0.9f)

                if (isFirst) {
                    waveformPath.moveTo(xPixel.toFloat(), yMin)
                    isFirst = false
                } else {
                    waveformPath.lineTo(xPixel.toFloat(), yMin)
                }
                waveformPath.lineTo(xPixel.toFloat(), yMax)
            }

            canvas.drawPath(waveformPath, waveformPaint)
        }

        // 4. Draw Playhead position line
        val playheadX = (playheadTimeSec / totalTime).coerceIn(0f, 1f) * w
        canvas.drawLine(playheadX, 0f, playheadX, chartH, playheadPaint)
        canvas.drawCircle(playheadX, 0f, 10f, playheadDotPaint)
        canvas.drawCircle(playheadX, chartH, 10f, playheadDotPaint)
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
