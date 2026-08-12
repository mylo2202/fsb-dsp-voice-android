package com.example.dspvoiceunvoice.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper

class AudioPlayerManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

    var onProgressUpdate: ((currentTimeSec: Float, totalDurationSec: Float) -> Unit)? = null
    var onCompletion: (() -> Unit)? = null

    fun loadAudio(uri: Uri, onLoaded: ((durationSec: Float) -> Unit)? = null) {
        release()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                prepare()
                setOnCompletionListener {
                    stopProgressTracking()
                    onCompletion?.invoke()
                }
            }
            val duration = (mediaPlayer?.duration ?: 0) / 1000f
            onLoaded?.invoke(duration)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun play() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                startProgressTracking()
            }
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                stopProgressTracking()
            }
        }
    }

    fun seekTo(seconds: Float) {
        mediaPlayer?.let {
            val ms = (seconds * 1000f).toInt().coerceIn(0, it.duration)
            it.seekTo(ms)
            val currentSec = ms / 1000f
            val totalSec = it.duration / 1000f
            onProgressUpdate?.invoke(currentSec, totalSec)
        }
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    fun getCurrentPositionSeconds(): Float {
        return (mediaPlayer?.currentPosition ?: 0) / 1000f
    }

    fun getDurationSeconds(): Float {
        return (mediaPlayer?.duration ?: 0) / 1000f
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        updateRunnable = object : Runnable {
            override fun run() {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        val currSec = player.currentPosition / 1000f
                        val durSec = player.duration / 1000f
                        onProgressUpdate?.invoke(currSec, durSec)
                        handler.postDelayed(this, 30) // 30ms for smooth 30fps playhead updates
                    }
                }
            }
        }
        handler.post(updateRunnable!!)
    }

    private fun stopProgressTracking() {
        updateRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable = null
    }

    fun release() {
        stopProgressTracking()
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }
}
