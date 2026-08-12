package com.example.dspvoiceunvoice

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dspvoiceunvoice.audio.AudioPlayerManager
import com.example.dspvoiceunvoice.audio.AudioRecordManager
import com.example.dspvoiceunvoice.audio.WavDecoder
import com.example.dspvoiceunvoice.databinding.ActivityMainBinding
import com.example.dspvoiceunvoice.dsp.AnalysisResult
import com.example.dspvoiceunvoice.dsp.PythonDspEngine
import com.example.dspvoiceunvoice.ui.SegmentAdapter
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var dspEngine: PythonDspEngine
    private lateinit var recorder: AudioRecordManager
    private lateinit var player: AudioPlayerManager
    private lateinit var segmentAdapter: SegmentAdapter

    private var currentAnalysisResult: AnalysisResult? = null
    private var currentAudioUri: Uri? = null

    private var recordingStartTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (micGranted) {
            toggleRecording()
        } else {
            Toast.makeText(this, "Cần cấp quyền Microphone để thu âm!", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processWavUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dspEngine = PythonDspEngine(this)
        recorder = AudioRecordManager(this)
        player = AudioPlayerManager(this)

        setupUI()
        setupListeners()

        // Auto run demo signal on first startup for immediate visualization
        runDemoSignal()
    }

    private fun setupUI() {
        segmentAdapter = SegmentAdapter { segment ->
            player.seekTo(segment.startTime)
            if (!player.isPlaying()) {
                player.play()
                binding.fabPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            }
        }
        binding.rvSegments.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = segmentAdapter
        }

        player.onProgressUpdate = { currentSec, totalSec ->
            binding.viewWaveform.setPlayheadPosition(currentSec)
            binding.viewDspFeatures.setPlayheadPosition(currentSec)

            binding.tvAudioTime.text = String.format(
                "%02d:%04.1f / %02d:%04.1f",
                (currentSec / 60).toInt(), currentSec % 60,
                (totalSec / 60).toInt(), totalSec % 60
            )

            currentAnalysisResult?.let { res ->
                val frameStepSec = if (res.frameTimes.size > 1) (res.frameTimes[1] - res.frameTimes[0]) else 0.01f
                val currFrameIdx = ((currentSec / frameStepSec).toInt()).coerceIn(0, res.totalFrames)
                binding.tvFrameInfo.text = "Khung: $currFrameIdx / ${res.totalFrames}"
            }
        }

        player.onCompletion = {
            binding.fabPlayPause.setImageResource(android.R.drawable.ic_media_play)
        }

        binding.viewWaveform.onSeekListener = { timeSec ->
            player.seekTo(timeSec)
        }

        binding.viewDspFeatures.onSeekListener = { timeSec ->
            player.seekTo(timeSec)
        }
    }

    private fun setupListeners() {
        binding.btnRecord.setOnClickListener {
            checkAndStartRecording()
        }

        binding.btnPickFile.setOnClickListener {
            pickFileLauncher.launch("audio/*")
        }

        binding.btnDemo.setOnClickListener {
            runDemoSignal()
        }

        binding.fabPlayPause.setOnClickListener {
            if (player.isPlaying()) {
                player.pause()
                binding.fabPlayPause.setImageResource(android.R.drawable.ic_media_play)
            } else {
                player.play()
                binding.fabPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            }
        }
    }

    private fun checkAndStartRecording() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        } else {
            toggleRecording()
        }
    }

    private fun toggleRecording() {
        if (recorder.isRecording()) {
            // Stop recording
            val wavFile = recorder.stopRecording()
            stopRecordingTimer()
            binding.btnRecord.text = "Thu âm"
            binding.btnRecord.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            binding.layoutRecordingStatus.visibility = View.GONE

            if (wavFile != null && wavFile.exists()) {
                processWavFile(wavFile)
            }
        } else {
            // Start recording
            val outputFile = File(cacheDir, "recorded_speech_${System.currentTimeMillis()}.wav")
            recorder.startRecording(outputFile, sampleRate = 16000) { volume ->
                handler.post {
                    binding.progressMic.progress = (volume * 100).toInt()
                }
            }

            recordingStartTime = System.currentTimeMillis()
            startRecordingTimer()

            binding.btnRecord.text = "Dừng thu"
            binding.btnRecord.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
            binding.layoutRecordingStatus.visibility = View.VISIBLE
            binding.chipStatus.text = "Đang thu âm..."
        }
    }

    private fun startRecordingTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                val elapsedSec = (System.currentTimeMillis() - recordingStartTime) / 1000
                binding.tvRecordingTimer.text = String.format("Đang thu âm... %02d:%02d", elapsedSec / 60, elapsedSec % 60)
                handler.postDelayed(this, 500)
            }
        }
        handler.post(timerRunnable!!)
    }

    private fun stopRecordingTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null
    }

    private fun runDemoSignal() {
        player.pause()
        showLoading(true)
        binding.chipStatus.text = "Đang tạo tín hiệu Demo..."
        binding.tvAudioFileName.text = "Audio: Demo Speech Signal (3.0s)"

        lifecycleScope.launch {
            try {
                val result = dspEngine.generateAndAnalyzeDemoSignal(16000)
                showLoading(false)
                displayAnalysisResult(result, null)
                binding.chipStatus.text = "Hoàn tất (Demo)"
            } catch (e: Exception) {
                showLoading(false)
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "Lỗi khi tạo demo signal: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun processWavUri(uri: Uri) {
        player.pause()
        showLoading(true)
        binding.chipStatus.text = "Đang giải mã WAV..."
        binding.tvAudioFileName.text = "Audio: ${uri.lastPathSegment ?: "WAV File"}"

        lifecycleScope.launch {
            try {
                val decoded = WavDecoder.decodeWav(this@MainActivity, uri)
                if (decoded == null) {
                    showLoading(false)
                    Toast.makeText(this@MainActivity, "Không thể giải mã file WAV!", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                binding.chipStatus.text = "Chaquopy đang phân tích..."
                val result = dspEngine.analyzePcm16(decoded.pcm16, decoded.sampleRate)
                showLoading(false)
                displayAnalysisResult(result, uri)
                binding.chipStatus.text = "Hoàn tất phân tích"
            } catch (e: Exception) {
                showLoading(false)
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "Lỗi phân tích WAV: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun processWavFile(file: File) {
        val uri = Uri.fromFile(file)
        processWavUri(uri)
    }

    private fun displayAnalysisResult(result: AnalysisResult, audioUri: Uri?) {
        currentAnalysisResult = result
        currentAudioUri = audioUri

        binding.viewWaveform.setAnalysisResult(result)
        binding.viewDspFeatures.setAnalysisResult(result)

        val counts = result.labelCounts
        binding.tvVoicePct.text = String.format("%.1f%%", counts.voicePct)
        binding.tvUnvoicePct.text = String.format("%.1f%%", counts.unvoicePct)
        binding.tvSilencePct.text = String.format("%.1f%%", counts.silencePct)

        val th = result.thresholds
        binding.tvThresholdDetails.text = String.format(
            "T_E: %.4f | T_ZCR: %.3f | T_R: %.2f | T_SF: %.3f | T_C: %.0fHz | T_P: %.1f",
            th.tE, th.tZcr, th.tR, th.tSf, th.tC, th.tP
        )

        binding.tvSegmentsHeader.text = "Danh sách đoạn phân loại (${result.segments.size} đoạn)"
        segmentAdapter.submitList(result.segments)

        audioUri?.let {
            player.loadAudio(it) { dur ->
                binding.tvAudioTime.text = String.format("00:00.0 / %02d:%04.1f", (dur / 60).toInt(), dur % 60)
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.layoutLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnRecord.isEnabled = !isLoading
        binding.btnPickFile.isEnabled = !isLoading
        binding.btnDemo.isEnabled = !isLoading
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
        stopRecordingTimer()
    }
}