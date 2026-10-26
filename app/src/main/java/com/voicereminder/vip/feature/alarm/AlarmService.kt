package com.voicereminder.vip.feature.alarm

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import com.voicereminder.vip.AppContainer
import com.voicereminder.vip.core.model.HistoryStatus
import com.voicereminder.vip.core.model.RepeatType
import com.voicereminder.vip.core.notifications.Notifications
import com.voicereminder.vip.core.schedule.NextTriggerCalculator
import com.voicereminder.vip.data.db.AlarmEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId

class AlarmService : Service() {

    companion object {
        const val ACTION_FIRE = "com.voicereminder.vip.action.SERVICE_FIRE"
        const val ACTION_STOP = "com.voicereminder.vip.action.SERVICE_STOP"
        const val ACTION_SNOOZE = "com.voicereminder.vip.action.SERVICE_SNOOZE"
        const val ACTION_CANCEL_SEQUENCE = "com.voicereminder.vip.action.SERVICE_CANCEL_SEQUENCE"
        const val ACTION_RING_FINISHED = "com.voicereminder.vip.action.RING_FINISHED"

        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_SNOOZE_COUNT = "extra_snooze_count"

        private const val SNOOZE_NOTIFICATION_BASE = 5_500
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var container: AppContainer

    private var current: AlarmEntity? = null
    private var currentSnoozeCount = 0
    private var repeatsDone = 0
    private var stopped = false
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var tone: ToneGenerator? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val queue = ArrayDeque<Pair<Long, Int>>()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer.from(this)
        Notifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: -1L
        val snoozeCount = intent?.getIntExtra(EXTRA_SNOOZE_COUNT, 0) ?: 0

        when (action) {
            ACTION_FIRE -> {
                if (current == null) {
                    startForegroundSafely(null)
                    if (alarmId > 0) fire(alarmId, snoozeCount) else stopSelf()
                } else if (alarmId > 0) {
                    queue.addLast(alarmId to snoozeCount)
                }
            }
            ACTION_STOP -> {
                if (current != null) stopCurrent() else stopSelfSafely()
            }
            ACTION_SNOOZE -> {
                if (current != null) snoozeCurrent() else stopSelfSafely()
            }
            ACTION_CANCEL_SEQUENCE -> {
                if (current != null) cancelSequence() else stopSelfSafely()
            }
            else -> if (current == null) stopSelfSafely()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        cleanupAudio()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Firing

    private fun fire(alarmId: Long, snoozeCount: Int) {
        scope.launch {
            if (current != null) {
                queue.addLast(alarmId to snoozeCount)
                return@launch
            }
            val alarm = withContext(Dispatchers.IO) {
                runCatching { container.alarmRepository.get(alarmId) }.getOrNull()
            }
            if (alarm == null) {
                stopIfIdle()
                return@launch
            }
            if (!alarm.enabled) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        container.historyRepository.log(
                            alarm,
                            HistoryStatus.MISSED,
                            alarm.nextTriggerAt ?: System.currentTimeMillis(),
                            detail = "المنبه معطّل"
                        )
                    }
                }
                stopIfIdle()
                return@launch
            }

            current = alarm
            currentSnoozeCount = snoozeCount
            repeatsDone = 0
            stopped = false

            startForegroundSafely(alarm)
            acquireWakeLock()
            requestAudioFocus()

            val firedAt = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                runCatching {
                    val record = container.alarmRepository
                    record.setLastTriggered(alarm.id, firedAt)
                    container.historyRepository.log(
                        alarm,
                        HistoryStatus.FIRED,
                        alarm.nextTriggerAt ?: firedAt,
                        snoozeCount = snoozeCount
                    )
                    if (alarm.repeatType != RepeatType.ONCE) {
                        val updated = alarm.copy(lastTriggeredAt = firedAt)
                        val next = NextTriggerCalculator.calculate(
                            updated,
                            firedAt + 1_000L,
                            ZoneId.systemDefault()
                        )
                        record.setNextTrigger(alarm.id, next)
                        if (next != null) {
                            container.scheduler.scheduleAt(alarm.id, next, alarm.title, 0)
                        }
                    }
                }
            }

            launchRingActivity(alarm)
            updateNotification()
            startVibration(alarm)
            playNext()
        }
    }

    private fun launchRingActivity(alarm: AlarmEntity) {
        runCatching {
            startActivity(
                Intent(this, AlarmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(AlarmActivity.EXTRA_ALARM_ID, alarm.id)
                    putExtra(EXTRA_SNOOZE_COUNT, currentSnoozeCount)
                }
            )
        }
    }

    // ------------------------------------------------------------------
    // Playback

    private fun playNext() {
        val alarm = current ?: return
        if (stopped) return
        if (alarm.preTone && repeatsDone == 0) {
            playBeep()
            handler.postDelayed({ speakOrPlay(alarm) }, 450L)
        } else {
            speakOrPlay(alarm)
        }
    }

    private fun speakOrPlay(alarm: AlarmEntity) {
        if (stopped) return
        val recordingId = alarm.recordingId
        if (recordingId != null) {
            scope.launch {
                val recording = withContext(Dispatchers.IO) {
                    runCatching { container.recordingRepository.get(recordingId) }.getOrNull()
                }
                if (stopped) return@launch
                if (recording != null && File(recording.filePath).isFile) {
                    playRecording(File(recording.filePath), alarm)
                } else {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            container.historyRepository.log(
                                alarm,
                                HistoryStatus.FIRED,
                                alarm.nextTriggerAt ?: System.currentTimeMillis(),
                                detail = "التسجيل مفقود؛ تم استخدام النطق"
                            )
                        }
                    }
                    speakWithRetry(alarm, 0)
                }
            }
        } else {
            speakWithRetry(alarm, 0)
        }
    }

    private fun playRecording(file: File, alarm: AlarmEntity) {
        if (stopped) return
        releaseMediaPlayer()
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            player.setDataSource(file.absolutePath)
            player.setVolume(alarm.volume, alarm.volume)
            player.setOnCompletionListener { onRepeatFinished() }
            player.setOnErrorListener { _, _, _ ->
                onSpeechFailed("تعذر تشغيل التسجيل الصوتي.")
                true
            }
            player.prepare()
            player.start()
            mediaPlayer = player
        } catch (_: Exception) {
            runCatching { player.release() }
            speakWithRetry(alarm, 0)
        }
    }

    private fun speakWithRetry(alarm: AlarmEntity, attempt: Int) {
        if (stopped) return
        if (!container.tts.status.value.ready) {
            if (attempt < 24) {
                handler.postDelayed({ speakWithRetry(alarm, attempt + 1) }, 250L)
            } else {
                onSpeechFailed("محرك النطق غير متاح على هذا الجهاز.")
            }
            return
        }
        val started = container.tts.speak(
            text = alarm.phrase,
            languageTag = alarm.languageTag,
            rate = alarm.speechRate,
            pitch = alarm.pitch,
            volume = alarm.volume,
            utterancePrefix = "alarm-${alarm.id}",
            onDone = { onRepeatFinished() },
            onError = { message ->
                if (attempt < 3 && !stopped) {
                    handler.postDelayed({ speakWithRetry(alarm, attempt + 1) }, 700L)
                } else {
                    onSpeechFailed(message)
                }
            }
        )
        if (!started && attempt >= 24) {
            onSpeechFailed("تعذر بدء النطق.")
        }
    }

    private fun onRepeatFinished() {
        if (stopped) return
        repeatsDone++
        val alarm = current ?: return
        if (alarm.repeatCount == 0 || repeatsDone < alarm.repeatCount) {
            handler.postDelayed({ playNext() }, (alarm.repeatGapSeconds.coerceAtLeast(1) * 1000L))
        } else {
            stopVibration()
            updateNotification(statusText = "انتهت مرات التكرار — اضغط إيقاف")
        }
    }

    private fun onSpeechFailed(message: String) {
        if (stopped) return
        stopVibration()
        updateNotification(statusText = message)
    }

    private fun playBeep() {
        runCatching {
            if (tone == null) {
                tone = ToneGenerator(AudioManager.STREAM_ALARM, 85)
            }
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
        }
    }

    private fun releaseMediaPlayer() {
        val player = mediaPlayer
        mediaPlayer = null
        if (player != null) {
            runCatching { if (player.isPlaying) player.stop() }
            runCatching { player.release() }
        }
    }

    private fun startVibration(alarm: AlarmEntity) {
        if (!alarm.vibrate) return
        val deviceVibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator = deviceVibrator
        runCatching {
            val pattern = longArrayOf(0L, 800L, 700L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                deviceVibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                deviceVibrator?.vibrate(pattern, 0)
            }
        }
    }

    private fun stopVibration() {
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    // ------------------------------------------------------------------
    // Stop / snooze / sequence

    private fun stopCurrent() {
        val alarm = current ?: run {
            stopSelfSafely()
            return
        }
        stopped = true
        handler.removeCallbacksAndMessages(null)
        cleanupAudio()
        val now = System.currentTimeMillis()

        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    container.historyRepository.log(
                        alarm,
                        HistoryStatus.DISMISSED,
                        alarm.nextTriggerAt ?: now,
                        snoozeCount = currentSnoozeCount
                    )
                    if (alarm.repeatType == RepeatType.ONCE) {
                        container.alarmRepository.setEnabled(alarm.id, false)
                        container.alarmRepository.setNextTrigger(alarm.id, null)
                        container.scheduler.cancel(alarm.id)
                    } else {
                        val updated = alarm.copy(lastTriggeredAt = now)
                        val next = NextTriggerCalculator.calculate(
                            updated,
                            now,
                            ZoneId.systemDefault()
                        )
                        container.alarmRepository.setNextTrigger(alarm.id, next)
                        if (next != null) {
                            container.scheduler.scheduleAt(alarm.id, next, alarm.title, 0)
                        } else {
                            container.alarmRepository.setEnabled(alarm.id, false)
                        }
                    }
                }
            }
            finishActive()
        }
    }

    private fun snoozeCurrent() {
        val alarm = current ?: return
        val nextCount = currentSnoozeCount + 1
        if (nextCount > alarm.maxSnoozes) {
            updateNotification(statusText = "بلغت الحد الأقصى لعدد التأجيلات")
            return
        }
        val triggerAt = System.currentTimeMillis() + alarm.snoozeMinutes * 60_000L
        val result = container.scheduler.scheduleAt(
            alarmId = alarm.id,
            triggerAtMillis = triggerAt,
            label = alarm.title,
            snoozeCount = nextCount
        )
        if (result == com.voicereminder.vip.core.model.SchedulingResult.FAILED) {
            updateNotification(statusText = "تعذر جدولة التأجيل، حاول مجددًا")
            return
        }

        stopped = true
        handler.removeCallbacksAndMessages(null)
        cleanupAudio()

        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    container.historyRepository.log(
                        alarm,
                        HistoryStatus.SNOOZED,
                        triggerAt,
                        snoozeCount = nextCount
                    )
                }
            }
            runCatching {
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(
                    SNOOZE_NOTIFICATION_BASE + (alarm.id % 100).toInt(),
                    Notifications.snoozedNotification(this@AlarmService, alarm, nextCount)
                )
            }
            finishActive()
        }
    }

    private fun cancelSequence() {
        val alarm = current ?: return
        val groupId = alarm.groupId ?: run {
            stopCurrent()
            return
        }
        stopped = true
        handler.removeCallbacksAndMessages(null)
        cleanupAudio()

        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    container.alarmRepository.group(groupId).forEach {
                        container.scheduler.cancel(it.id)
                    }
                    container.alarmRepository.disableGroup(groupId)
                    container.historyRepository.log(
                        alarm,
                        HistoryStatus.CANCELLED,
                        alarm.nextTriggerAt ?: System.currentTimeMillis(),
                        detail = "أوقفت بقية رسائل التسلسل"
                    )
                }
            }
            finishActive()
        }
    }

    private fun finishActive() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        sendBroadcast(
            Intent(ACTION_RING_FINISHED).setPackage(packageName)
        )
        current = null
        currentSnoozeCount = 0
        repeatsDone = 0
        if (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            if (current == null) startForegroundSafely(null)
            fire(next.first, next.second)
        } else {
            stopSelf()
        }
    }

    private fun stopIfIdle() {
        if (current == null && queue.isEmpty()) {
            stopSelfSafely()
        } else if (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            fire(next.first, next.second)
        }
    }

    private fun stopSelfSafely() {
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    // ------------------------------------------------------------------
    // Foreground notification

    private fun startForegroundSafely(alarm: AlarmEntity?) {
        val notification = if (alarm != null) {
            Notifications.activeNotification(
                this,
                alarm,
                currentSnoozeCount,
                progress = progressText(alarm)
            )
        } else {
            Notifications.serviceNotification(this)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    Notifications.ACTIVE_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(Notifications.ACTIVE_NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            // If foreground promotion fails, the alarm activity may still be shown.
        }
    }

    private fun updateNotification(statusText: String? = null) {
        val alarm = current ?: return
        val notification = Notifications.activeNotification(
            this,
            alarm,
            currentSnoozeCount,
            progress = progressText(alarm),
            statusText = statusText
        )
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    Notifications.ACTIVE_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(Notifications.ACTIVE_NOTIFICATION_ID, notification)
            }
        }
    }

    private fun progressText(alarm: AlarmEntity): String? {
        return if (alarm.groupId != null && alarm.stepCount > 1) {
            "الرسالة ${alarm.stepIndex + 1} من ${alarm.stepCount}"
        } else {
            null
        }
    }

    // ------------------------------------------------------------------
    // Power / audio focus

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java) ?: return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VoiceReminder:alarm")
            .apply {
                setReferenceCounted(false)
                runCatching { acquire(15 * 60 * 1000L) }
            }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun requestAudioFocus() {
        runCatching {
            val manager = getSystemService(AudioManager::class.java) ?: return
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setWillPauseWhenDucked(false)
                .build()
            audioFocusRequest = request
            manager.requestAudioFocus(request)
        }
    }

    private fun releaseAudioFocus() {
        runCatching {
            val manager = getSystemService(AudioManager::class.java)
            val request = audioFocusRequest
            if (manager != null && request != null) manager.abandonAudioFocusRequest(request)
        }
        audioFocusRequest = null
    }

    private fun cleanupAudio() {
        releaseMediaPlayer()
        runCatching { container.tts.stop() }
        stopVibration()
        runCatching { tone?.release() }
        tone = null
        releaseAudioFocus()
        releaseWakeLock()
    }
}
