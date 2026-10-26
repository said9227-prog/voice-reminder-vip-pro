package com.voicereminder.vip.ui.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.voicereminder.vip.AppContainer
import com.voicereminder.vip.core.model.AlarmDraft
import com.voicereminder.vip.core.model.RepeatType
import com.voicereminder.vip.core.model.SchedulingResult
import com.voicereminder.vip.core.schedule.NextTriggerCalculator
import com.voicereminder.vip.core.util.TimeUtils
import com.voicereminder.vip.data.db.AlarmEntity
import com.voicereminder.vip.data.db.RecordingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId

class AlarmEditorViewModel(
    private val container: AppContainer,
    private val alarmId: Long,
    private val prefill: AlarmDraft?
) : ViewModel() {

    var draft by mutableStateOf(
        prefill ?: AlarmDraft(epochDay = TimeUtils.todayEpochDay())
    )
        private set

    var loading by mutableStateOf(alarmId > 0L)
        private set

    var saving by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var warning by mutableStateOf<String?>(null)
        private set

    var done by mutableStateOf(false)
        private set

    private var createdAt: Long? = null
    private var lastTriggeredAt: Long? = null
    private var previousNext: Long? = null

    val recordings: StateFlow<List<RecordingEntity>> = container.recordingRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ttsStatus = container.tts.status
    val isPlaying = container.recorder.isPlaying
    val isRecording = container.recorder.isRecording

    init {
        viewModelScope.launch {
            if (alarmId > 0L) {
                val alarm = withContext(Dispatchers.IO) {
                    runCatching { container.alarmRepository.get(alarmId) }.getOrNull()
                }
                if (alarm == null) {
                    error = "لم يُعثر على المنبه المطلوب."
                } else {
                    draft = alarm.toDraft()
                    createdAt = alarm.createdAt
                    lastTriggeredAt = alarm.lastTriggeredAt
                    previousNext = alarm.nextTriggerAt
                }
                loading = false
            } else {
                if (prefill == null) {
                    val s = container.settingsRepository.settings.first()
                    draft = draft.copy(
                        speechRate = s.speechRate,
                        pitch = s.pitch,
                        volume = s.volume,
                        preTone = s.preTone,
                        repeatCount = s.repeatCount,
                        repeatGapSeconds = s.repeatGapSeconds,
                        vibrate = s.vibrate,
                        snoozeMinutes = s.snoozeMinutes,
                        maxSnoozes = s.maxSnoozes,
                        languageTag = s.defaultLanguage
                    )
                }
                loading = false
            }
        }
    }

    fun update(transform: (AlarmDraft) -> AlarmDraft) {
        draft = transform(draft)
        error = null
    }

    fun clearError() {
        error = null
    }

    fun setRecording(recordingId: Long?) {
        update { it.copy(recordingId = recordingId) }
        container.recorder.stopPlayback()
    }

    fun preview() {
        val current = draft
        error = null
        val recordingId = current.recordingId
        if (recordingId != null) {
            viewModelScope.launch {
                val recording = withContext(Dispatchers.IO) {
                    runCatching { container.recordingRepository.get(recordingId) }.getOrNull()
                }
                if (recording == null || !File(recording.filePath).isFile) {
                    error = "التسجيل المحدد غير موجود. اختر تسجيلًا آخر أو أزل الارتباط."
                    return@launch
                }
                container.recorder.play(recording.filePath, current.volume)
            }
            return
        }
        if (current.phrase.isBlank()) {
            error = "اكتب العبارة أولًا لتجربة النطق."
            return
        }
        container.tts.speak(
            text = current.phrase,
            languageTag = current.languageTag,
            rate = current.speechRate,
            pitch = current.pitch,
            volume = current.volume,
            utterancePrefix = "editor-preview",
            onError = { error = it }
        )
    }

    fun stopPreview() {
        container.tts.stop()
        container.recorder.stopPlayback()
    }

    fun save() {
        if (saving) return
        val current = draft
        if (current.phrase.isBlank() && current.recordingId == null) {
            error = "اكتب العبارة الصوتية أو اربط تسجيلًا."
            return
        }
        if (!TimeUtils.isValidTime(current.hour, current.minute)) {
            error = "الوقت غير صالح."
            return
        }
        if (current.repeatType == RepeatType.WEEKLY && current.daysMask == 0) {
            error = "اختر يومًا واحدًا على الأقل للتكرار الأسبوعي."
            return
        }
        if (current.repeatType == RepeatType.EVERY_INTERVAL && current.intervalMinutes < 1) {
            error = "حدد فاصلًا زمنيًا أكبر من صفر."
            return
        }
        if (current.snoozeMinutes < 1) {
            error = "مدة التأجيل يجب أن تكون دقيقة واحدة على الأقل."
            return
        }

        saving = true
        error = null
        viewModelScope.launch {
            try {
                val currentRecording = current.recordingId?.let {
                    withContext(Dispatchers.IO) {
                        runCatching { container.recordingRepository.get(it) }.getOrNull()
                    }
                }
                if (current.recordingId != null &&
                    (currentRecording == null || !File(currentRecording.filePath).isFile)
                ) {
                    error = "التسجيل المحدد غير موجود. أزل الارتباط أو اختر تسجيلًا آخر."
                    return@launch
                }

                val title = current.title.trim().ifBlank {
                    current.phrase.trim().take(48).ifBlank { "تذكير صوتي" }
                }
                val prepared = current.copy(title = title)
                draft = prepared

                if (prepared.enabled && prepared.repeatType == RepeatType.ONCE) {
                    val target = TimeUtils.toMillis(
                        prepared.epochDay, prepared.hour, prepared.minute
                    )
                    if (target <= System.currentTimeMillis()) {
                        error = "الوقت المحدد مضى. اختر وقتًا أو تاريخًا لاحقًا."
                        return@launch
                    }
                }

                val entity = prepared.toEntity()
                val next = if (prepared.enabled) {
                    NextTriggerCalculator.calculate(
                        entity, System.currentTimeMillis(), ZoneId.systemDefault()
                    )
                } else {
                    null
                }
                if (prepared.enabled && next == null) {
                    error = "تعذر حساب الموعد القادم. تحقق من التكرار والأيام."
                    return@launch
                }

                val id = withContext(Dispatchers.IO) {
                    container.alarmRepository.save(entity.copy(nextTriggerAt = next))
                }
                draft = prepared.copy(id = id)

                if (prepared.enabled && next != null) {
                    container.alarmRepository.setNextTrigger(id, next)
                    warning = when (
                        container.scheduler.scheduleAt(id, next, title, 0)
                    ) {
                        SchedulingResult.EXACT -> null
                        SchedulingResult.INEXACT ->
                            "حُفظ المنبه، لكن الجدولة غير دقيقة. امنح صلاحية المنبهات الدقيقة."
                        SchedulingResult.FAILED ->
                            "حُفظ المنبه، لكن تعذرت جدولته. أعد فتح التطبيق أو افحص الصلاحيات."
                    }
                } else {
                    container.scheduler.cancel(id)
                }
                done = true
            } catch (t: Throwable) {
                error = "تعذر حفظ المنبه: ${t.message ?: "خطأ غير معروف"}"
            } finally {
                saving = false
            }
        }
    }

    private fun AlarmDraft.toEntity(): AlarmEntity {
        return AlarmEntity(
            id = id,
            title = title,
            phrase = phrase.trim(),
            recordingId = recordingId,
            hour = hour,
            minute = minute,
            dateEpochDay = if (repeatType == RepeatType.ONCE) epochDay else null,
            repeatType = repeatType,
            daysMask = if (repeatType == RepeatType.WEEKLY) daysMask else 0,
            intervalMinutes = if (repeatType == RepeatType.EVERY_INTERVAL) intervalMinutes else 0,
            enabled = enabled,
            languageTag = languageTag,
            speechRate = speechRate,
            pitch = pitch,
            volume = volume,
            preTone = preTone,
            repeatCount = repeatCount,
            repeatGapSeconds = repeatGapSeconds,
            vibrate = vibrate,
            snoozeMinutes = snoozeMinutes,
            maxSnoozes = maxSnoozes,
            categoryId = categoryId,
            groupId = groupId,
            groupTitle = groupTitle,
            stepIndex = stepIndex,
            stepCount = stepCount,
            createdAt = createdAt ?: System.currentTimeMillis(),
            lastTriggeredAt = lastTriggeredAt,
            nextTriggerAt = previousNext
        )
    }

    override fun onCleared() {
        container.recorder.stopPlayback()
        super.onCleared()
    }
}

fun AlarmEntity.toDraft(): AlarmDraft = AlarmDraft(
    id = id,
    title = title,
    phrase = phrase,
    recordingId = recordingId,
    hour = hour,
    minute = minute,
    epochDay = dateEpochDay ?: TimeUtils.todayEpochDay(),
    repeatType = repeatType,
    daysMask = daysMask,
    intervalMinutes = intervalMinutes.takeIf { it > 0 } ?: 60,
    enabled = enabled,
    languageTag = languageTag,
    speechRate = speechRate,
    pitch = pitch,
    volume = volume,
    preTone = preTone,
    repeatCount = repeatCount,
    repeatGapSeconds = repeatGapSeconds,
    vibrate = vibrate,
    snoozeMinutes = snoozeMinutes,
    maxSnoozes = maxSnoozes,
    categoryId = categoryId,
    groupId = groupId,
    groupTitle = groupTitle,
    stepIndex = stepIndex,
    stepCount = stepCount
)

class AlarmEditorViewModelFactory(
    private val container: AppContainer,
    private val alarmId: Long,
    private val prefill: AlarmDraft?
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AlarmEditorViewModel::class.java)) {
            return AlarmEditorViewModel(container, alarmId, prefill) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
