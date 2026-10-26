package com.voicereminder.vip.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.voicereminder.vip.AppContainer
import com.voicereminder.vip.core.model.AlarmDraft
import com.voicereminder.vip.core.model.AppSettings
import com.voicereminder.vip.core.model.ParsedReminder
import com.voicereminder.vip.core.model.RepeatType
import com.voicereminder.vip.core.model.SchedulingResult
import com.voicereminder.vip.core.model.Weekdays
import com.voicereminder.vip.core.schedule.NextTriggerCalculator
import com.voicereminder.vip.core.util.GroupIds
import com.voicereminder.vip.core.util.TimeUtils
import com.voicereminder.vip.data.db.AlarmEntity
import com.voicereminder.vip.data.db.CategoryEntity
import com.voicereminder.vip.data.db.HistoryEntity
import com.voicereminder.vip.data.db.RecordingEntity
import com.voicereminder.vip.data.db.TemplateWithItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

data class RoutineUi(
    val groupId: Long,
    val title: String,
    val steps: List<AlarmEntity>,
    val enabled: Boolean
) {
    val stepCount: Int get() = steps.size
    val firstHour: Int get() = steps.minOfOrNull { it.hour * 60 + it.minute }?.div(60) ?: 0
    val firstMinute: Int get() = steps.minOfOrNull { it.hour * 60 + it.minute }?.rem(60) ?: 0
    val lastHour: Int get() = steps.maxOfOrNull { it.hour * 60 + it.minute }?.div(60) ?: 0
    val lastMinute: Int get() = steps.maxOfOrNull { it.hour * 60 + it.minute }?.rem(60) ?: 0
}

data class RoutineStepInput(
    val hour: Int,
    val minute: Int,
    val phrase: String
)

class AppViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val alarms: StateFlow<List<AlarmEntity>> = container.alarmRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = container.categoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recordings: StateFlow<List<RecordingEntity>> = container.recordingRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val templates: StateFlow<List<TemplateWithItems>> = container.templateRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<HistoryEntity>> = container.historyRepository.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val now: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    val nextAlarm: StateFlow<AlarmEntity?> = combine(alarms, now) { list, current ->
        list.filter { it.enabled && (it.nextTriggerAt ?: 0L) > current }
            .minByOrNull { it.nextTriggerAt ?: Long.MAX_VALUE }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val routines: StateFlow<List<RoutineUi>> = alarms.map { list ->
        list.filter { it.groupId != null }
            .groupBy { it.groupId!! }
            .map { (groupId, steps) ->
                val ordered = steps.sortedBy { it.stepIndex }
                RoutineUi(
                    groupId = groupId,
                    title = ordered.firstOrNull()?.groupTitle ?: ordered.firstOrNull()?.title ?: "تسلسل",
                    steps = ordered,
                    enabled = ordered.any { it.enabled }
                )
            }
            .sortedBy { it.firstHour * 60 + it.firstMinute }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todayTriggered: StateFlow<Int> = combine(alarms, now) { list, current ->
        val zone = ZoneId.systemDefault()
        list.count {
            it.lastTriggeredAt != null &&
                TimeUtils.sameDay(it.lastTriggeredAt ?: 0L, current, zone)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 12)
    val events = _events.asSharedFlow()

    private val _pendingDraft = MutableStateFlow<AlarmDraft?>(null)

    val tts = container.tts
    val recorder = container.recorder

    fun postEvent(message: String) {
        _events.tryEmit(message)
    }

    fun toggleAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            runCatching {
                if (alarm.enabled) {
                    container.scheduler.cancel(alarm.id)
                    container.alarmRepository.setEnabled(alarm.id, false)
                    container.alarmRepository.setNextTrigger(alarm.id, null)
                    postEvent("تم إيقاف «${alarm.title}»")
                } else {
                    container.alarmRepository.setEnabled(alarm.id, true)
                    when (container.rescheduler.scheduleOne(alarm.id)) {
                        SchedulingResult.EXACT -> postEvent("تم تفعيل «${alarm.title}»")
                        SchedulingResult.INEXACT -> {
                            postEvent("تم التفعيل، لكن بدون دقة كاملة. امنح صلاحية المنبهات الدقيقة.")
                        }
                        SchedulingResult.FAILED -> {
                            container.alarmRepository.setEnabled(alarm.id, false)
                            postEvent("تعذر تفعيل المنبه؛ تحقق من الوقت والصلاحيات.")
                        }
                    }
                }
            }.onFailure {
                postEvent("تعذر تحديث المنبه: ${it.message ?: "خطأ غير معروف"}")
            }
        }
    }

    fun deleteAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            runCatching {
                container.scheduler.cancel(alarm.id)
                container.alarmRepository.delete(alarm.id)
                postEvent("تم حذف «${alarm.title}»")
            }.onFailure { postEvent("تعذر حذف المنبه.") }
        }
    }

    fun deleteRoutine(routine: RoutineUi) {
        viewModelScope.launch {
            runCatching {
                routine.steps.forEach { container.scheduler.cancel(it.id) }
                container.alarmRepository.deleteGroup(routine.groupId)
                postEvent("تم حذف التسلسل «${routine.title}»")
            }.onFailure { postEvent("تعذر حذف التسلسل.") }
        }
    }

    fun setRoutineEnabled(routine: RoutineUi, enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (!enabled) {
                    routine.steps.forEach { container.scheduler.cancel(it.id) }
                    container.alarmRepository.disableGroup(routine.groupId)
                    postEvent("تم إيقاف التسلسل")
                } else {
                    container.alarmRepository.enableGroup(routine.groupId)
                    var exact = 0
                    var failed = 0
                    routine.steps.forEach {
                        when (container.rescheduler.scheduleOne(it.id)) {
                            SchedulingResult.EXACT -> exact++
                            SchedulingResult.INEXACT -> exact++
                            SchedulingResult.FAILED -> failed++
                        }
                    }
                    if (failed > 0) {
                        postEvent("تم تفعيل $exact رسالة وتعذر جدولة $failed.")
                    } else {
                        postEvent("تم تفعيل التسلسل ($exact رسالة)")
                    }
                }
            }.onFailure { postEvent("تعذر تحديث التسلسل.") }
        }
    }

    fun deleteRecording(recording: RecordingEntity) {
        viewModelScope.launch {
            runCatching {
                val used = alarms.value.filter { it.recordingId == recording.id }
                used.forEach { alarm ->
                    container.alarmRepository.save(alarm.copy(recordingId = null))
                }
                container.recordingRepository.delete(recording)
                postEvent(
                    if (used.isEmpty()) "تم حذف التسجيل"
                    else "تم حذف التسجيل وإزالته من ${used.size} منبه"
                )
            }.onFailure { postEvent("تعذر حذف التسجيل.") }
        }
    }

    fun renameRecording(recording: RecordingEntity, name: String) {
        viewModelScope.launch {
            runCatching {
                container.recordingRepository.rename(recording, name)
                postEvent("تمت إعادة التسمية")
            }.onFailure { postEvent("تعذر إعادة التسمية.") }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            runCatching {
                container.historyRepository.clear()
                postEvent("تم مسح السجل")
            }.onFailure { postEvent("تعذر مسح السجل.") }
        }
    }

    fun deleteAllAlarms() {
        viewModelScope.launch {
            runCatching {
                alarms.value.forEach { container.scheduler.cancel(it.id) }
                container.alarmRepository.deleteAll()
                postEvent("تم حذف كل المنبهات")
            }.onFailure { postEvent("تعذر حذف المنبهات.") }
        }
    }

    fun deleteAllRecordings() {
        viewModelScope.launch {
            runCatching {
                recordings.value.forEach {
                    container.recordingRepository.delete(it)
                }
                alarms.value.filter { it.recordingId != null }.forEach {
                    container.alarmRepository.save(it.copy(recordingId = null))
                }
                postEvent("تم حذف كل التسجيلات")
            }.onFailure { postEvent("تعذر حذف التسجيلات.") }
        }
    }

    fun rescheduleAll() {
        viewModelScope.launch {
            runCatching {
                val report = container.rescheduler.rescheduleAll()
                postEvent(
                    "أُعيدت الجدولة: ${report.scheduled} منبه" +
                        if (report.failed > 0) "، وفشل ${report.failed}" else ""
                )
            }.onFailure { postEvent("تعذرت إعادة الجدولة.") }
        }
    }

    fun setPendingDraft(draft: AlarmDraft) {
        _pendingDraft.value = draft
    }

    fun takePendingDraft(): AlarmDraft? {
        val value = _pendingDraft.value
        _pendingDraft.value = null
        return value
    }

    fun draftFromParsed(parsed: ParsedReminder): AlarmDraft {
        val s = settings.value
        return AlarmDraft(
            title = parsed.title,
            phrase = parsed.phrase,
            hour = parsed.hour,
            minute = parsed.minute,
            epochDay = parsed.epochDay,
            repeatType = parsed.repeatType,
            daysMask = parsed.daysMask,
            enabled = true,
            languageTag = parsed.languageTag,
            speechRate = s.speechRate,
            pitch = s.pitch,
            volume = s.volume,
            preTone = s.preTone,
            repeatCount = s.repeatCount,
            repeatGapSeconds = s.repeatGapSeconds,
            vibrate = s.vibrate,
            snoozeMinutes = s.snoozeMinutes,
            maxSnoozes = s.maxSnoozes
        )
    }

    fun createFromTemplate(
        template: TemplateWithItems,
        epochDay: Long,
        hour: Int,
        minute: Int,
        repeat: RepeatType,
        onResult: (String?) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                val s = settings.value
                val category = categories.value.firstOrNull {
                    it.name == template.template.categoryName
                }
                val groupId = GroupIds.next()
                val groupTitle = template.template.title
                var baseMillis = TimeUtils.toMillis(epochDay, hour, minute)
                if (repeat == RepeatType.ONCE && baseMillis <= System.currentTimeMillis()) {
                    baseMillis = TimeUtils.toMillis(
                        TimeUtils.todayEpochDay() + 1, hour, minute
                    )
                }
                val ordered = template.items.sortedBy { it.sortOrder }
                val entities = ordered.mapIndexed { index, item ->
                    val stepMillis = baseMillis + item.offsetMinutes * 60_000L
                    val zoned = TimeUtils.fromMillis(stepMillis)
                    AlarmEntity(
                        title = "$groupTitle • ${index + 1}",
                        phrase = item.phrase,
                        hour = zoned.hour,
                        minute = zoned.minute,
                        dateEpochDay = if (repeat == RepeatType.ONCE) {
                            zoned.toLocalDate().toEpochDay()
                        } else {
                            null
                        },
                        repeatType = repeat,
                        daysMask = if (repeat == RepeatType.WEEKLY) {
                            Weekdays.maskOf(setOf(1, 2, 3, 4, 5))
                        } else {
                            0
                        },
                        enabled = true,
                        languageTag = s.defaultLanguage,
                        speechRate = s.speechRate,
                        pitch = s.pitch,
                        volume = s.volume,
                        preTone = s.preTone,
                        repeatCount = s.repeatCount,
                        repeatGapSeconds = s.repeatGapSeconds,
                        vibrate = s.vibrate,
                        snoozeMinutes = s.snoozeMinutes,
                        maxSnoozes = s.maxSnoozes,
                        categoryId = category?.id,
                        groupId = groupId,
                        groupTitle = groupTitle,
                        stepIndex = index,
                        stepCount = ordered.size,
                        createdAt = System.currentTimeMillis()
                    )
                }
                val invalid = entities.any {
                    NextTriggerCalculator.calculate(it, System.currentTimeMillis(), ZoneId.systemDefault()) == null
                }
                if (invalid && repeat == RepeatType.ONCE) {
                    onResult("وقت البداية مضى؛ اختر وقتًا لاحقًا.")
                    return@launch
                }
                val ids = container.alarmRepository.saveAll(entities)
                var exact = 0
                var failed = 0
                ids.forEach { id ->
                    val saved = container.alarmRepository.get(id) ?: return@forEach
                    val next = NextTriggerCalculator.calculate(
                        saved, System.currentTimeMillis(), ZoneId.systemDefault()
                    )
                    container.alarmRepository.setNextTrigger(id, next)
                    if (next != null) {
                        when (container.scheduler.scheduleAt(id, next, saved.title, 0)) {
                            SchedulingResult.EXACT, SchedulingResult.INEXACT -> exact++
                            SchedulingResult.FAILED -> failed++
                        }
                    }
                }
                postEvent("أُضيف القالب: $exact رسالة${if (failed > 0) "، فشل $failed" else ""}")
                onResult(null)
            }.onFailure {
                onResult("تعذر إنشاء القالب: ${it.message ?: "خطأ"}")
            }
        }
    }

    fun saveRoutine(
        groupId: Long?,
        title: String,
        epochDay: Long,
        repeatType: RepeatType,
        daysMask: Int,
        steps: List<RoutineStepInput>,
        onResult: (String?) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                val cleanTitle = title.trim()
                if (cleanTitle.isBlank()) {
                    onResult("اكتب عنوانًا للتسلسل.")
                    return@launch
                }
                val valid = steps.filter { it.phrase.isNotBlank() }
                if (valid.size < 2) {
                    onResult("أضف رسالتين على الأقل.")
                    return@launch
                }
                if (repeatType == RepeatType.WEEKLY && daysMask == 0) {
                    onResult("اختر يومًا واحدًا على الأقل للتكرار الأسبوعي.")
                    return@launch
                }
                val distinctTimes = valid.map { it.hour * 60 + it.minute }.distinct()
                if (distinctTimes.size != valid.size) {
                    onResult("لا يمكن أن تتكرر الرسائل في الوقت نفسه.")
                    return@launch
                }

                val s = settings.value
                val targetGroup = groupId ?: GroupIds.next()
                if (groupId != null) {
                    container.alarmRepository.group(groupId).forEach {
                        container.scheduler.cancel(it.id)
                    }
                    container.alarmRepository.deleteGroup(groupId)
                }

                val entities = valid.mapIndexed { index, step ->
                    AlarmEntity(
                        title = "$cleanTitle • ${index + 1}",
                        phrase = step.phrase.trim(),
                        hour = step.hour,
                        minute = step.minute,
                        dateEpochDay = if (repeatType == RepeatType.ONCE) epochDay else null,
                        repeatType = repeatType,
                        daysMask = if (repeatType == RepeatType.WEEKLY) daysMask else 0,
                        enabled = true,
                        languageTag = s.defaultLanguage,
                        speechRate = s.speechRate,
                        pitch = s.pitch,
                        volume = s.volume,
                        preTone = s.preTone,
                        repeatCount = s.repeatCount,
                        repeatGapSeconds = s.repeatGapSeconds,
                        vibrate = s.vibrate,
                        snoozeMinutes = s.snoozeMinutes,
                        maxSnoozes = s.maxSnoozes,
                        groupId = targetGroup,
                        groupTitle = cleanTitle,
                        stepIndex = index,
                        stepCount = valid.size,
                        createdAt = System.currentTimeMillis()
                    )
                }
                val now = System.currentTimeMillis()
                if (repeatType == RepeatType.ONCE) {
                    val past = entities.any {
                        TimeUtils.toMillis(epochDay, it.hour, it.minute) <= now
                    }
                    if (past) {
                        onResult("إحدى الرسائل مضى وقتها؛ اختر تاريخًا أو أوقاتًا لاحقة.")
                        return@launch
                    }
                }
                val ids = container.alarmRepository.saveAll(entities)
                var scheduled = 0
                var failed = 0
                ids.forEach { id ->
                    val saved = container.alarmRepository.get(id) ?: return@forEach
                    val next = NextTriggerCalculator.calculate(
                        saved, System.currentTimeMillis(), ZoneId.systemDefault()
                    )
                    container.alarmRepository.setNextTrigger(id, next)
                    if (next == null) {
                        container.alarmRepository.setEnabled(id, false)
                        failed++
                    } else {
                        when (container.scheduler.scheduleAt(id, next, saved.title, 0)) {
                            SchedulingResult.EXACT, SchedulingResult.INEXACT -> scheduled++
                            SchedulingResult.FAILED -> failed++
                        }
                    }
                }
                postEvent("تم حفظ التسلسل: $scheduled رسالة${if (failed > 0) "، فشل $failed" else ""}")
                onResult(null)
            }.onFailure {
                onResult("تعذر حفظ التسلسل: ${it.message ?: "خطأ"}")
            }
        }
    }

    fun saveCategory(category: CategoryEntity, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            runCatching {
                if (category.name.isBlank()) {
                    onResult("اكتب اسم التصنيف.")
                    return@launch
                }
                container.categoryRepository.save(category)
                onResult(null)
            }.onFailure { onResult("تعذر حفظ التصنيف.") }
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            runCatching {
                container.categoryRepository.delete(category)
                postEvent("تم حذف التصنيف «${category.name}»")
            }.onFailure { postEvent("تعذر حذف التصنيف.") }
        }
    }

    fun attachRecordingToAlarm(alarm: AlarmEntity, recordingId: Long) {
        viewModelScope.launch {
            runCatching {
                container.alarmRepository.save(alarm.copy(recordingId = recordingId))
                postEvent("تم ربط التسجيل بالمنبه")
            }.onFailure { postEvent("تعذر ربط التسجيل.") }
        }
    }

    fun ttsPreview(text: String, languageTag: String) {
        val s = settings.value
        container.tts.speak(
            text = text,
            languageTag = languageTag,
            rate = s.speechRate,
            pitch = s.pitch,
            volume = s.volume,
            utterancePrefix = "preview",
            onError = { postEvent(it) }
        )
    }

    fun stopPreview() {
        container.tts.stop()
        container.recorder.stopPlayback()
    }
}

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            return AppViewModel(container) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
