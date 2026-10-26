package com.voicereminder.vip.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class RecordingManager(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startedAt: Long = 0L
    private var player: MediaPlayer? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    fun recordingsDir(): File = File(context.filesDir, "recordings").apply { mkdirs() }

    fun newRecordingFile(): File =
        File(recordingsDir(), "rec_${System.currentTimeMillis()}.m4a")

    fun start(): File? {
        stopPlayback()
        if (_isRecording.value) return currentFile
        val file = newRecordingFile()
        val mediaRecorder = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
        } catch (_: Exception) {
            return null
        }

        return try {
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            currentFile = file
            startedAt = System.currentTimeMillis()
            _isRecording.value = true
            file
        } catch (_: Exception) {
            runCatching { mediaRecorder.release() }
            runCatching { file.delete() }
            recorder = null
            currentFile = null
            _isRecording.value = false
            null
        }
    }

    fun stop(): Pair<File, Long>? {
        val mediaRecorder = recorder ?: return null
        val file = currentFile
        val duration = (System.currentTimeMillis() - startedAt).coerceAtLeast(0)
        recorder = null
        currentFile = null
        _isRecording.value = false
        return try {
            runCatching { mediaRecorder.stop() }
            mediaRecorder.release()
            if (file != null && file.exists() && file.length() > 0) file to duration else null
        } catch (_: Exception) {
            runCatching { mediaRecorder.release() }
            if (file != null) runCatching { file.delete() }
            null
        }
    }

    fun cancel() {
        val mediaRecorder = recorder
        val file = currentFile
        recorder = null
        currentFile = null
        _isRecording.value = false
        runCatching { mediaRecorder?.stop() }
        runCatching { mediaRecorder?.release() }
        if (file != null) runCatching { file.delete() }
    }

    fun play(path: String, volume: Float = 1f, onComplete: () -> Unit = {}): Boolean {
        stopPlayback()
        if (!File(path).isFile) return false
        val mediaPlayer = MediaPlayer()
        return try {
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mediaPlayer.setDataSource(path)
            mediaPlayer.setVolume(volume.coerceIn(0f, 1f), volume.coerceIn(0f, 1f))
            mediaPlayer.setOnCompletionListener {
                _isPlaying.value = false
                runCatching { it.release() }
                if (player === it) player = null
                onComplete()
            }
            mediaPlayer.setOnErrorListener { mp, _, _ ->
                _isPlaying.value = false
                runCatching { mp.release() }
                if (player === mp) player = null
                onComplete()
                true
            }
            mediaPlayer.prepare()
            mediaPlayer.start()
            player = mediaPlayer
            _isPlaying.value = true
            true
        } catch (_: Exception) {
            runCatching { mediaPlayer.release() }
            _isPlaying.value = false
            false
        }
    }

    fun stopPlayback() {
        val current = player
        player = null
        _isPlaying.value = false
        if (current != null) {
            runCatching { current.stop() }
            runCatching { current.release() }
        }
    }

    fun durationMs(path: String): Long {
        if (!File(path).isFile) return 0
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun release() {
        cancel()
        stopPlayback()
    }
}
