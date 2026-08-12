package com.example.dspvoiceunvoice

import com.example.dspvoiceunvoice.audio.WavDecoder
import com.example.dspvoiceunvoice.dsp.LabelCounts
import com.example.dspvoiceunvoice.dsp.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DspUnitTest {

    @Test
    fun testLabelCountsPercentage() {
        val counts = LabelCounts(voice = 50, unvoice = 30, silence = 20)
        assertEquals(100, counts.total)
        assertEquals(50.0f, counts.voicePct, 0.01f)
        assertEquals(30.0f, counts.unvoicePct, 0.01f)
        assertEquals(20.0f, counts.silencePct, 0.01f)
    }

    @Test
    fun testSegmentProperties() {
        val seg = Segment(
            index = 1,
            label = 2,
            startTime = 0.30f,
            endTime = 1.10f,
            duration = 0.80f
        )
        assertEquals("Voice (Hữu thanh)", seg.labelName)
        assertEquals(0.80f, seg.duration, 0.001f)
    }

    @Test
    fun testWavDecoderDummyHeader() {
        val sampleRate = 16000
        val numSamples = 1600
        val pcmBytes = ByteArray(numSamples * 2)

        val out = ByteArrayOutputStream()
        val totalAudioLen = pcmBytes.size
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * 1 * 16 / 8

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(4, totalDataLen)
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        buffer.putInt(16, 16)
        buffer.putShort(20, 1) // PCM
        buffer.putShort(22, 1) // 1 channel
        buffer.putInt(24, sampleRate)
        buffer.putInt(28, byteRate)
        buffer.putShort(32, 2) // block align
        buffer.putShort(34, 16) // bits per sample
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        buffer.putInt(40, totalAudioLen)

        out.write(header)
        out.write(pcmBytes)

        val decoded = WavDecoder.decodeWavBytes(out.toByteArray())
        assertNotNull(decoded)
        assertEquals(16000, decoded?.sampleRate)
        assertEquals(1600, decoded?.pcm16?.size)
        assertEquals(0.1f, decoded?.durationSeconds ?: 0f, 0.001f)
    }
}
