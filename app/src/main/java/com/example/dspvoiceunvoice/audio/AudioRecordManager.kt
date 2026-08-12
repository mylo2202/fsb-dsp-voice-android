package com.example.dspvoiceunvoice.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.concurrent.thread

class AudioRecordManager(private val context: Context) {

    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    var currentOutputFile: File? = null
        private set

    fun isRecording(): Boolean = isRecording

    @SuppressLint("MissingPermission")
    fun startRecording(outputFile: File, sampleRate: Int = 16000, onVolumeUpdate: ((Float) -> Unit)? = null) {
        if (isRecording) return

        currentOutputFile = outputFile

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord?.startRecording()
        isRecording = true

        recordingThread = thread(start = true) {
            writeAudioDataToWav(outputFile, sampleRate, bufferSize, onVolumeUpdate)
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) return currentOutputFile

        isRecording = false
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
        recordingThread?.join(1000)
        recordingThread = null

        currentOutputFile?.let { file ->
            updateWavHeader(file)
        }

        return currentOutputFile
    }

    private fun writeAudioDataToWav(
        outputFile: File,
        sampleRate: Int,
        bufferSize: Int,
        onVolumeUpdate: ((Float) -> Unit)?
    ) {
        val buffer = ShortArray(bufferSize / 2)
        var outputStream: FileOutputStream? = null

        try {
            outputStream = FileOutputStream(outputFile)
            // Write placeholder 44-byte WAV header
            outputStream.write(ByteArray(44))

            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    val byteBuffer = ByteArray(read * 2)
                    var maxAmp = 0
                    for (i in 0 until read) {
                        val sample = buffer[i]
                        val absSample = kotlin.math.abs(sample.toInt())
                        if (absSample > maxAmp) maxAmp = absSample

                        byteBuffer[i * 2] = (sample.toInt() and 0x00FF).toByte()
                        byteBuffer[i * 2 + 1] = (sample.toInt() shr 8 and 0x00FF).toByte()
                    }
                    outputStream.write(byteBuffer)

                    val normVolume = (maxAmp.toFloat() / 32767.0f).coerceIn(0f, 1f)
                    onVolumeUpdate?.invoke(normVolume)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                outputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateWavHeader(wavFile: File) {
        if (!wavFile.exists()) return

        val totalAudioLen = wavFile.length() - 44
        val totalDataLen = totalAudioLen + 36
        val longSampleRate = 16000L
        val channels = 1
        val byteRate = longSampleRate * channels * 16 / 8

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 16 for PCM
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // Format: PCM = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = (longSampleRate shr 8 and 0xff).toByte()
        header[26] = (longSampleRate shr 16 and 0xff).toByte()
        header[27] = (longSampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * 16 / 8).toByte() // block align
        header[33] = 0
        header[34] = 16 // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()

        try {
            val raf = RandomAccessFile(wavFile, "rw")
            raf.seek(0)
            raf.write(header)
            raf.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
