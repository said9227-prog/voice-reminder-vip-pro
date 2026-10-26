package com.voicereminder.vip.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicereminder.vip.AppContainer
import com.voicereminder.vip.core.model.RepeatType
import com.voicereminder.vip.core.model.Weekdays
import com.voicereminder.vip.core.util.TimeUtils
import com.voicereminder.vip.data.db.CategoryEntity
import com.voicereminder.vip.data.db.RecordingEntity
import com.voicereminder.vip.ui.components.AppBackground
import com.voicereminder.vip.ui.components.AppTopBar
import com.voicereminder.vip.ui.components.ChoiceDialog
import com.voicereminder.vip.ui.components.ConfirmDialog
import com.voicereminder.vip.ui.components.DividerLine
import com.voicereminder.vip.ui.components.InfoNote
import com.voicereminder.vip.ui.components.LabeledSwitch
import com.voicereminder.vip.ui.components.Pill
import com.voicereminder.vip.ui.components.SectionCard
import com.voicereminder.vip.ui.components.SectionTitle
import com.voicereminder.vip.ui.components.ValueStepper
import com.voicereminder.vip.ui.components.categoryIcon
import com.voicereminder.vip.ui.components.parseColor
import com.voicereminder.vip.ui.theme.Gold
import com.voicereminder.vip.ui.theme.Teal
import com.voicereminder.vip.ui.vm.AlarmEditorViewModel
import com.voicereminder.vip.ui.vm.AppViewModel
import java.time.LocalDate
import java.util.Locale

@Composable
fun AlarmEditorScreen(
    viewModel: AlarmEditorViewModel,
    app: AppViewModel,
    container: AppContainer,
    onBack: () -> Unit
) {
    val draft = viewModel.draft
    val categories by app.categories.collectAsStateWithLifecycle()
    val settings by app.settings.collectAsStateWithLifecycle()
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val ttsStatus by viewModel.ttsStatus.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var showVoicePicker by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var categoryPicker by remember { mutableStateOf(false) }

    val languageOptions = remember(ttsStatus.languages) {
        val known = mutableListOf(
            "ar" to "العربية",
            "en-US" to "الإنجليزية",
            "id-ID" to "الإندونيسية"
        )
        ttsStatus.languages.forEach { language ->
            if (known.none { it.first == language.tag }) {
                known += language.tag to language.label
            }
        }
        known
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopPreview() }
    }

    LaunchedEffect(viewModel.done) {
        if (viewModel.done) onBack()
    }

    AppBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(
                title = if (draft.id > 0) "تعديل المنبه" else "منبه جديد",
                onBack = onBack,
                actions = {
                    if (draft.id > 0) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "حذف",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )

            if (viewModel.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Gold)
                }
                return@Column
            }

            Column(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        TimeCard(
                            hour = draft.hour,
                            minute = draft.minute,
                            use24Hour = settings.use24Hour,
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        viewModel.update { it.copy(hour = hour, minute = minute) }
                                    },
                                    draft.hour,
                                    draft.minute,
                                    settings.use24Hour
                                ).show()
                            }
                        )
                    }

                    item {
                        SectionCard {
                            SectionTitle("التذكير")
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = draft.title,
                                onValueChange = { value ->
                                    viewModel.update { it.copy(title = value.take(80)) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("العنوان") },
                                placeholder = { Text("مثال: موعد الدواء") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = draft.phrase,
                                onValueChange = { value ->
                                    viewModel.update { it.copy(phrase = value.take(400)) }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(122.dp),
                                label = { Text("العبارة المنطوقة") },
                                placeholder = { Text("مثال: حان وقت تناول دواء الضغط. لا تنسَ كوب الماء.") },
                                shape = RoundedCornerShape(16.dp),
                                supportingText = {
                                    Text("${draft.phrase.length}/400 • تُنطق بصوت واضح عند التنبيه")
                                }
                            )
                            Spacer(Modifier.height(6.dp))
                            InfoNote(
                                if (draft.recordingId != null) {
                                    "سيُشغَّل التسجيل الصوتي أولًا، والعبارة المكتوبة احتياطًا."
                                } else {
                                    "بدون إنترنت: يعتمد النطق على محرك TTS المثبّت على هاتفك وبيانات اللغة."
                                }
                            )
                        }
                    }

                    item {
                        VoiceCard(
                            recording = recordings.firstOrNull { it.id == draft.recordingId },
                            recordingCount = recordings.size,
                            ttsReady = ttsStatus.ready,
                            ttsEngine = ttsStatus.engineLabel,
                            ttsError = ttsStatus.error,
                            isSpeaking = ttsStatus.speaking,
                            isPlayingRecording = isPlaying,
                            onPickRecording = { showVoicePicker = true },
                            onClearRecording = { viewModel.setRecording(null) },
                            onPreview = {
                                if (ttsStatus.speaking || isPlaying) viewModel.stopPreview()
                                else viewModel.preview()
                            }
                        )
                    }

                    item {
                        SectionCard {
                            SectionTitle("التكرار", trailing = repeatTitle(draft.repeatType))
                            Spacer(Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(RepeatType.entries.toList()) { type ->
                                    FilterChip(
                                        selected = draft.repeatType == type,
                                        onClick = {
                                            viewModel.update {
                                                it.copy(
                                                    repeatType = type,
                                                    daysMask = if (type == RepeatType.WEEKLY &&
                                                        it.daysMask == 0
                                                    ) {
                                                        Weekdays.maskOf(setOf(1, 2, 3, 4, 5))
                                                    } else {
                                                        it.daysMask
                                                    }
                                                )
                                            }
                                        },
                                        label = { Text(repeatTitle(type)) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Gold.copy(alpha = 0.25f)
                                        )
                                    )
                                }
                            }

                            when (draft.repeatType) {
                                RepeatType.ONCE -> {
                                    Spacer(Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val date = LocalDate.ofEpochDay(draft.epochDay)
                                                DatePickerDialog(
                                                    context,
                                                    { _, year, month, day ->
                                                        viewModel.update {
                                                            it.copy(
                                                                epochDay = LocalDate.of(
                                                                    year, month + 1, day
                                                                ).toEpochDay()
                                                            )
                                                        }
                                                    },
                                                    date.year,
                                                    date.monthValue - 1,
                                                    date.dayOfMonth
                                                ).apply {
                                                    datePicker.minDate =
                                                        LocalDate.now()
                                                            .atStartOfDay(TimeUtils.zone())
                                                            .toInstant()
                                                            .toEpochMilli()
                                                }.show()
                                            }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Outlined.DateRange,
                                            contentDescription = null,
                                            tint = Gold,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "التاريخ",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                TimeUtils.formatDate(draft.epochDay),
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                        Text("تغيير", color = Gold)
                                    }
                                }

                                RepeatType.DAILY -> {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "يتكرر كل يوم في ${TimeUtils.formatTime(draft.hour, draft.minute, settings.use24Hour)}.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                RepeatType.WEEKLY -> {
                                    Spacer(Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Weekdays.displayOrder.forEach { day ->
                                            val selected =
                                                Weekdays.isEnabled(draft.daysMask, day)
                                            Surface(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clickable {
                                                        val set = Weekdays
                                                            .setOf(draft.daysMask)
                                                            .toMutableSet()
                                                        if (selected) set.remove(day)
                                                        else set.add(day)
                                                        viewModel.update {
                                                            it.copy(daysMask = Weekdays.maskOf(set))
                                                        }
                                                    },
                                                shape = CircleShape,
                                                color = if (selected) Gold else Color.Transparent,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (selected) Gold
                                                    else MaterialTheme.colorScheme.outlineVariant
                                                )
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        Weekdays.shortNameAr(day),
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = if (selected) Color(0xFF251A00)
                                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        Weekdays.describe(draft.daysMask),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                RepeatType.EVERY_INTERVAL -> {
                                    Spacer(Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ValueStepper(
                                            label = "كل",
                                            value = TimeUtils.formatInterval(draft.intervalMinutes),
                                            onDecrease = if (draft.intervalMinutes > 15) {
                                                {
                                                    viewModel.update {
                                                        it.copy(
                                                            intervalMinutes =
                                                                (it.intervalMinutes - 15).coerceAtLeast(15)
                                                        )
                                                    }
                                                }
                                            } else null,
                                            onIncrease = {
                                                viewModel.update {
                                                    it.copy(
                                                        intervalMinutes =
                                                            (it.intervalMinutes + 15).coerceAtMost(720)
                                                    )
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(15, 30, 60, 120, 240).forEach { preset ->
                                            Pill(
                                                text = TimeUtils.formatInterval(preset),
                                                color = if (draft.intervalMinutes == preset) Gold else Teal,
                                                onClick = {
                                                    viewModel.update {
                                                        it.copy(intervalMinutes = preset)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        SectionCard {
                            SectionTitle("التصنيف")
                            Spacer(Modifier.height(10.dp))
                            val selectedCategory =
                                categories.firstOrNull { it.id == draft.categoryId }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { categoryPicker = true }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val color = selectedCategory?.let { parseColor(it.colorHex) } ?: Teal
                                Surface(
                                    shape = CircleShape,
                                    color = color.copy(alpha = 0.16f)
                                ) {
                                    Icon(
                                        categoryIcon(selectedCategory?.iconKey ?: "list"),
                                        contentDescription = null,
                                        tint = color,
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    selectedCategory?.name ?: "بدون تصنيف",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text("تغيير", color = Gold)
                            }
                        }
                    }

                    item {
                        LanguageCard(
                            languageTag = draft.languageTag,
                            ttsReady = ttsStatus.ready,
                            offline = ttsStatus.languages.firstOrNull {
                                it.tag == draft.languageTag
                            }?.offline ?: false,
                            available = ttsStatus.languages.firstOrNull {
                                it.tag == draft.languageTag
                            }?.available ?: false,
                            onChange = { showLanguagePicker = true }
                        )
                    }

                    item {
                        SectionCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { advanced = !advanced },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Tune,
                                    contentDescription = null,
                                    tint = Gold,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "إعدادات متقدمة",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    if (advanced) "إخفاء" else "عرض",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Gold
                                )
                            }

                            if (advanced) {
                                DividerLine()
                                SliderRow(
                                    label = "سرعة النطق",
                                    value = draft.speechRate,
                                    valueLabel = String.format(Locale.US, "%.2f×", draft.speechRate),
                                    range = 0.5f..2f,
                                    onChange = { value ->
                                        viewModel.update { it.copy(speechRate = value) }
                                    }
                                )
                                SliderRow(
                                    label = "حدة الصوت",
                                    value = draft.pitch,
                                    valueLabel = String.format(Locale.US, "%.2f", draft.pitch),
                                    range = 0.5f..2f,
                                    onChange = { value ->
                                        viewModel.update { it.copy(pitch = value) }
                                    }
                                )
                                SliderRow(
                                    label = "مستوى الصوت",
                                    value = draft.volume,
                                    valueLabel = "${(draft.volume * 100).toInt()}%",
                                    range = 0.05f..1f,
                                    onChange = { value ->
                                        viewModel.update { it.copy(volume = value) }
                                    }
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    ValueStepper(
                                        label = "تكرار النطق",
                                        value = "${draft.repeatCount}×",
                                        onDecrease = if (draft.repeatCount > 1) {
                                            {
                                                viewModel.update {
                                                    it.copy(repeatCount = it.repeatCount - 1)
                                                }
                                            }
                                        } else null,
                                        onIncrease = if (draft.repeatCount < 10) {
                                            {
                                                viewModel.update {
                                                    it.copy(repeatCount = it.repeatCount + 1)
                                                }
                                            }
                                        } else null
                                    )
                                    ValueStepper(
                                        label = "الفاصل بينها",
                                        value = "${draft.repeatGapSeconds}ث",
                                        onDecrease = if (draft.repeatGapSeconds > 2) {
                                            {
                                                viewModel.update {
                                                    it.copy(
                                                        repeatGapSeconds =
                                                            (it.repeatGapSeconds - 2).coerceAtLeast(2)
                                                    )
                                                }
                                            }
                                        } else null,
                                        onIncrease = if (draft.repeatGapSeconds < 60) {
                                            {
                                                viewModel.update {
                                                    it.copy(
                                                        repeatGapSeconds =
                                                            (it.repeatGapSeconds + 2).coerceAtMost(60)
                                                    )
                                                }
                                            }
                                        } else null
                                    )
                                }
                                Spacer(Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    ValueStepper(
                                        label = "مدة التأجيل",
                                        value = "${draft.snoozeMinutes}د",
                                        onDecrease = if (draft.snoozeMinutes > 1) {
                                            {
                                                viewModel.update {
                                                    it.copy(
                                                        snoozeMinutes =
                                                            (it.snoozeMinutes - 1).coerceAtLeast(1)
                                                    )
                                                }
                                            }
                                        } else null,
                                        onIncrease = if (draft.snoozeMinutes < 60) {
                                            {
                                                viewModel.update {
                                                    it.copy(
                                                        snoozeMinutes =
                                                            (it.snoozeMinutes + 1).coerceAtMost(60)
                                                    )
                                                }
                                            }
                                        } else null
                                    )
                                    ValueStepper(
                                        label = "حد التأجيل",
                                        value = "${draft.maxSnoozes}×",
                                        onDecrease = if (draft.maxSnoozes > 1) {
                                            {
                                                viewModel.update {
                                                    it.copy(maxSnoozes = it.maxSnoozes - 1)
                                                }
                                            }
                                        } else null,
                                        onIncrease = if (draft.maxSnoozes < 10) {
                                            {
                                                viewModel.update {
                                                    it.copy(maxSnoozes = it.maxSnoozes + 1)
                                                }
                                            }
                                        } else null
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                LabeledSwitch(
                                    title = "اهتزاز مع التنبيه",
                                    checked = draft.vibrate,
                                    onCheckedChange = { value ->
                                        viewModel.update { it.copy(vibrate = value) }
                                    }
                                )
                                LabeledSwitch(
                                    title = "نغمة تمهيدية قبل الكلام",
                                    subtitle = "تنبيه قصير ثم رسالتك",
                                    checked = draft.preTone,
                                    onCheckedChange = { value ->
                                        viewModel.update { it.copy(preTone = value) }
                                    }
                                )
                                LabeledSwitch(
                                    title = "تفعيل المنبه",
                                    checked = draft.enabled,
                                    onCheckedChange = { value ->
                                        viewModel.update { it.copy(enabled = value) }
                                    }
                                )
                            }
                        }
                    }

                    viewModel.error?.let { message ->
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            ) {
                                Row(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    viewModel.warning?.let { message ->
                        item { InfoNote("تم الحفظ مع ملاحظة: $message") }
                    }

                    item { Spacer(Modifier.height(4.dp)) }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (ttsStatus.speaking || isPlaying) viewModel.stopPreview()
                            else viewModel.preview()
                        },
                        modifier = Modifier.height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            if (ttsStatus.speaking || isPlaying) Icons.Outlined.Stop
                            else Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (ttsStatus.speaking || isPlaying) "إيقاف" else "معاينة")
                    }
                    Button(
                        onClick = viewModel::save,
                        enabled = !viewModel.saving,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Gold,
                            contentColor = Color(0xFF251A00)
                        )
                    ) {
                        if (viewModel.saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF251A00)
                            )
                        } else {
                            Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("حفظ المنبه", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showVoicePicker) {
        ChoiceDialog(
            title = "اختر تسجيلًا صوتيًا",
            options = buildList<Pair<Long?, String>> {
                add(null to "بدون تسجيل — استخدام النطق الآلي")
                recordings.forEach { add(it.id to it.name) }
            },
            selected = null,
            onSelect = { id -> viewModel.setRecording(id) },
            onDismiss = { showVoicePicker = false }
        )
    }

    if (showLanguagePicker) {
        ChoiceDialog(
            title = "لغة النطق",
            options = languageOptions,
            selected = draft.languageTag,
            onSelect = { tag -> viewModel.update { it.copy(languageTag = tag) } },
            onDismiss = { showLanguagePicker = false }
        )
    }

    if (categoryPicker) {
        ChoiceDialog(
            title = "التصنيف",
            options = buildList<Pair<Long?, String>> {
                add(null to "بدون تصنيف")
                categories.forEach { add(it.id to it.name) }
            },
            selected = draft.categoryId,
            onSelect = { id -> viewModel.update { it.copy(categoryId = id) } },
            onDismiss = { categoryPicker = false }
        )
    }

    if (showDelete) {
        ConfirmDialog(
            title = "حذف المنبه؟",
            text = "سيُحذف «${draft.title.ifBlank { "هذا المنبه" }}» نهائيًا.",
            onConfirm = {
                showDelete = false
                app.alarms.value.firstOrNull { it.id == draft.id }?.let { alarm ->
                    app.deleteAlarm(alarm)
                }
                onBack()
            },
            onDismiss = { showDelete = false }
        )
    }
}

@Composable
private fun TimeCard(hour: Int, minute: Int, use24Hour: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.AccessTime,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "وقت التنبيه",
                    style = MaterialTheme.typography.labelLarge,
                    color = Gold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = TimeUtils.formatTime(hour, minute, use24Hour),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "اضغط لتغيير الوقت",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VoiceCard(
    recording: RecordingEntity?,
    recordingCount: Int,
    ttsReady: Boolean,
    ttsEngine: String,
    ttsError: String?,
    isSpeaking: Boolean,
    isPlayingRecording: Boolean,
    onPickRecording: () -> Unit,
    onClearRecording: () -> Unit,
    onPreview: () -> Unit
) {
    SectionCard {
        SectionTitle(
            "الصوت",
            trailing = if (ttsReady) {
                runCatching { ttsEngine.substringAfterLast('.') }.getOrDefault(ttsEngine)
            } else {
                "غير جاهز"
            }
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Teal.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (recording != null) Icons.Outlined.Mic else Icons.Outlined.Language,
                    contentDescription = null,
                    tint = Teal,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    recording?.name ?: "النطق الآلي (TTS)",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (recording != null) {
                        "تسجيل صوتي • ${formatSize(recording.sizeBytes)}"
                    } else {
                        "يُنطق النص باللغة المختارة عند التنبيه"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onPreview) {
                Icon(
                    if (isSpeaking || isPlayingRecording) Icons.Outlined.Stop
                    else Icons.Outlined.PlayArrow,
                    contentDescription = "معاينة",
                    tint = Gold
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onPickRecording,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("اختيار تسجيل ($recordingCount)")
            }
            if (recording != null) {
                TextButton(onClick = onClearRecording) {
                    Text("إزالة")
                }
            }
        }
        if (ttsError != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                ttsError,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
        } else if (!ttsReady) {
            Spacer(Modifier.height(6.dp))
            Text(
                "جارٍ تهيئة محرك النطق…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LanguageCard(
    languageTag: String,
    ttsReady: Boolean,
    offline: Boolean,
    available: Boolean,
    onChange: () -> Unit
) {
    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onChange),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Language,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("لغة النطق", style = MaterialTheme.typography.titleMedium)
                Text(
                    languageLabel(languageTag),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("تغيير", style = MaterialTheme.typography.labelMedium, color = Gold)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(
                text = if (available) "متاحة" else "غير مؤكدة",
                color = if (available) Teal else MaterialTheme.colorScheme.error
            )
            if (ttsReady) {
                Pill(
                    text = if (offline) "تعمل دون إنترنت" else "قد تحتاج بيانات اللغة",
                    color = if (offline) Teal else Gold
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "التوفر يعتمد على محرك النطق وبيانات اللغة المثبتة على هاتفك، وليس على التطبيق.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = Gold
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range
        )
    }
}

private fun repeatTitle(type: RepeatType): String = when (type) {
    RepeatType.ONCE -> "مرة واحدة"
    RepeatType.DAILY -> "كل يوم"
    RepeatType.WEEKLY -> "أيام محددة"
    RepeatType.EVERY_INTERVAL -> "كل فترة"
}

private fun languageLabel(tag: String): String = when (tag) {
    "ar" -> "العربية"
    "en-US" -> "الإنجليزية (الولايات المتحدة)"
    "en-GB" -> "الإنجليزية (المملكة المتحدة)"
    "id-ID" -> "الإندونيسية"
    else -> tag
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "0 ك.ب"
    bytes < 1024 * 1024 -> "${bytes / 1024} ك.ب"
    else -> String.format(Locale.US, "%.1f م.ب", bytes / 1024f / 1024f)
}
