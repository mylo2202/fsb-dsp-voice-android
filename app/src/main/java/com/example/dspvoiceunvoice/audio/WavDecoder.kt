package com.example.dspvoiceunvoice.audio

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DecodedAudio(
    val pcm16: ShortArray,
    val sampleRate: Int,
    val numChannels: Int,
    val durationSeconds: Float
)

object WavDecoder {

    fun decodeWav(context: Context, uri: Uri): DecodedAudio? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bytes = inputStream.readBytes()
            inputStream.close()
            decodeWavBytes(bytes)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun decodeWavBytes(bytes: ByteArray): DecodedAudio? {
        if (bytes.size < 44) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Check 'RIFF'
        val riff = String(bytes, 0, 4)
        val wave = String(bytes, 8, 4)
        if (riff != "RIFF" || wave != "WAVE") return null

        var offset = 12
        var sampleRate = 16000
        var numChannels = 1
        var bitsPerSample = 16
        var pcmData: ByteArray? = null

        while (offset < bytes.size - 8) {
            val chunkId = String(bytes, offset, 4)
            val chunkSize = buffer.getInt(offset + 4)

            if (chunkId == "fmt ") {
                val audioFormat = buffer.getShort(offset + 8).toInt()
                numChannels = buffer.getShort(offset + 10).toInt()
                sampleRate = buffer.getInt(offset + 12)
                bitsPerSample = buffer.getShort(offset + 22).toInt()
            } else if (chunkId == "data") {
                val dataStart = offset + 8
                val dataLen = chunkSize.coerceAtMost(bytes.size - dataStart)
                pcmData = ByteArray(dataLen)
                System.arraycopy(bytes, dataStart, pcmData, 0, dataLen)
                break
            }
            offset += 8 + chunkSize
        }

        if (pcmData == null) return null

        // Convert PCM bytes to ShortArray
        val shortBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val totalSamples = shortBuffer.remaining()
        val allShorts = ShortArray(totalSamples)
        shortBuffer.get(allShorts)

        // Stereo to Mono if needed
        val monoShorts = if (numChannels > 1) {
            val mono = ShortArray(totalSamples / numChannels)
            for (i in mono.indices) {
                var sum = 0
                for (ch in 0 until numChannels) {
                    sum += allShorts[i * numChannels + ch]
                }
                mono[i] = (sum / numChannels).toShort()
            }
            mono
        } else {
            allShorts
        }

        val duration = monoShorts.size.toFloat() / sampleRate.toFloat()
        return DecodedAudio(monoShorts, sampleRate, 1, duration)
    }
}
