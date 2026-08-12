package com.example.dspvoiceunvoice.dsp

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PythonDspEngine(private val context: Context) {

    init {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context.applicationContext))
        }
    }

    suspend fun analyzePcm16(pcm16: ShortArray, sampleRate: Int = 16000): AnalysisResult = withContext(Dispatchers.Default) {
        val py = Python.getInstance()
        val dspModule = py.getModule("main")

        // Call python wrapper function analyze_pcm16
        val resultPy: PyObject = dspModule.callAttr("analyze_pcm16", pcm16, sampleRate)
        return@withContext parsePyResult(resultPy, pcm16.map { it.toFloat() / 32768.0f }.toFloatArray())
    }

    suspend fun generateAndAnalyzeDemoSignal(sampleRate: Int = 16000): AnalysisResult = withContext(Dispatchers.Default) {
        val py = Python.getInstance()
        val dspModule = py.getModule("main")

        val signalPy: PyObject = dspModule.callAttr("generate_test_signal", sampleRate)
        val rawWaveform = signalPy.toJava(DoubleArray::class.java).map { it.toFloat() }.toFloatArray()

        val resultPy: PyObject = dspModule.callAttr("analyze_float", signalPy, sampleRate)
        return@withContext parsePyResult(resultPy, rawWaveform)
    }

    private fun parsePyResult(resultPy: PyObject, rawWaveform: FloatArray): AnalysisResult {
        val resMap = resultPy.asMap()

        val sampleRate = (resMap[pyKey("sample_rate")] ?: resultPy.get("sample_rate"))?.toInt() ?: 16000
        val totalFrames = (resMap[pyKey("total_frames")] ?: resultPy.get("total_frames"))?.toInt() ?: 0

        val labels = toIntArray(resMap[pyKey("labels")] ?: resultPy.get("labels"))
        val frameTimes = toFloatArray(resMap[pyKey("frame_times")] ?: resultPy.get("frame_times"))
        val timeAxis = toFloatArray(resMap[pyKey("time_axis")] ?: resultPy.get("time_axis"))

        val ste = toFloatArray(resMap[pyKey("ste")] ?: resultPy.get("ste"))
        val zcr = toFloatArray(resMap[pyKey("zcr")] ?: resultPy.get("zcr"))
        val rMax = toFloatArray(resMap[pyKey("r_max")] ?: resultPy.get("r_max"))
        val spectralFlatness = toFloatArray(resMap[pyKey("spectral_flatness")] ?: resultPy.get("spectral_flatness"))

        val countsPy = resMap[pyKey("label_counts")] ?: resultPy.get("label_counts")
        val countsMap = countsPy?.asMap()
        val voiceCount = countsMap?.get(pyKey("voice"))?.toInt() ?: labels.count { it == 2 }
        val unvoiceCount = countsMap?.get(pyKey("unvoice"))?.toInt() ?: labels.count { it == 1 }
        val silenceCount = countsMap?.get(pyKey("silence"))?.toInt() ?: labels.count { it == 0 }

        val labelCounts = LabelCounts(voiceCount, unvoiceCount, silenceCount)

        val tE = (resMap[pyKey("T_E")] ?: resultPy.get("T_E"))?.toFloat() ?: 0f
        val tZcr = (resMap[pyKey("T_ZCR")] ?: resultPy.get("T_ZCR"))?.toFloat() ?: 0f
        val tR = (resMap[pyKey("T_R")] ?: resultPy.get("T_R"))?.toFloat() ?: 0f
        val tSf = (resMap[pyKey("T_SF")] ?: resultPy.get("T_SF"))?.toFloat() ?: 0f
        val tC = (resMap[pyKey("T_C")] ?: resultPy.get("T_C"))?.toFloat() ?: 0f
        val tP = (resMap[pyKey("T_P")] ?: resultPy.get("T_P"))?.toFloat() ?: 0f

        val thresholds = Thresholds(tE, tZcr, tR, tSf, tC, tP)
        val segments = computeSegments(labels, frameTimes)

        return AnalysisResult(
            sampleRate = sampleRate,
            totalFrames = totalFrames,
            frameTimes = frameTimes,
            timeAxis = timeAxis,
            rawWaveform = rawWaveform,
            labels = labels,
            ste = ste,
            zcr = zcr,
            rMax = rMax,
            spectralFlatness = spectralFlatness,
            labelCounts = labelCounts,
            thresholds = thresholds,
            segments = segments
        )
    }

    private fun computeSegments(labels: IntArray, frameTimes: FloatArray): List<Segment> {
        if (labels.isEmpty() || frameTimes.isEmpty()) return emptyList()
        val list = mutableListOf<Segment>()

        var currentLabel = labels[0]
        var startIdx = 0
        var segIndex = 1

        val frameStepSec = if (frameTimes.size > 1) (frameTimes[1] - frameTimes[0]) else 0.01f

        for (i in 1 until labels.size) {
            if (labels[i] != currentLabel) {
                val startTime = (frameTimes[startIdx] - frameStepSec / 2f).coerceAtLeast(0f)
                val endTime = frameTimes[i - 1] + frameStepSec / 2f
                val duration = (endTime - startTime).coerceAtLeast(0f)

                list.add(Segment(segIndex++, currentLabel, startTime, endTime, duration))

                currentLabel = labels[i]
                startIdx = i
            }
        }

        // Add last segment
        val startTime = (frameTimes[startIdx] - frameStepSec / 2f).coerceAtLeast(0f)
        val endTime = frameTimes.last() + frameStepSec / 2f
        val duration = (endTime - startTime).coerceAtLeast(0f)
        list.add(Segment(segIndex, currentLabel, startTime, endTime, duration))

        return list
    }

    private fun pyKey(key: String): PyObject {
        return PyObject.fromJava(key)
    }

    private fun toIntArray(pyObj: PyObject?): IntArray {
        if (pyObj == null) return intArrayOf()
        return try {
            pyObj.toJava(IntArray::class.java)
        } catch (e: Exception) {
            val list = pyObj.asList()
            IntArray(list.size) { list[it].toInt() }
        }
    }

    private fun toFloatArray(pyObj: PyObject?): FloatArray {
        if (pyObj == null) return floatArrayOf()
        return try {
            pyObj.toJava(FloatArray::class.java)
        } catch (e: Exception) {
            try {
                pyObj.toJava(DoubleArray::class.java).map { it.toFloat() }.toFloatArray()
            } catch (e2: Exception) {
                val list = pyObj.asList()
                FloatArray(list.size) { list[it].toFloat() }
            }
        }
    }
}
