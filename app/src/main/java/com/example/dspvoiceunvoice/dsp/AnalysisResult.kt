package com.example.dspvoiceunvoice.dsp

data class Segment(
    val index: Int,
    val label: Int, // 0 = Silence, 1 = Unvoice, 2 = Voice
    val startTime: Float,
    val endTime: Float,
    val duration: Float
) {
    val labelName: String
        get() = when (label) {
            2 -> "Voice (Hữu thanh)"
            1 -> "Unvoice (Vô thanh)"
            else -> "Silence (Khoảng lặng)"
        }
}

data class LabelCounts(
    val voice: Int,
    val unvoice: Int,
    val silence: Int
) {
    val total: Int get() = voice + unvoice + silence
    val voicePct: Float get() = if (total > 0) (voice.toFloat() / total) * 100f else 0f
    val unvoicePct: Float get() = if (total > 0) (unvoice.toFloat() / total) * 100f else 0f
    val silencePct: Float get() = if (total > 0) (silence.toFloat() / total) * 100f else 0f
}

data class Thresholds(
    val tE: Float,
    val tZcr: Float,
    val tR: Float,
    val tSf: Float,
    val tC: Float,
    val tP: Float
)

data class AnalysisResult(
    val sampleRate: Int,
    val totalFrames: Int,
    val frameTimes: FloatArray,
    val timeAxis: FloatArray,
    val rawWaveform: FloatArray,
    val labels: IntArray,
    val ste: FloatArray,
    val zcr: FloatArray,
    val rMax: FloatArray,
    val spectralFlatness: FloatArray,
    val labelCounts: LabelCounts,
    val thresholds: Thresholds,
    val segments: List<Segment>
)
