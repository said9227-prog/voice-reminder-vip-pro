package com.voicereminder.vip.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicereminder.vip.AppContainer
import com.voicereminder.vip.core.model.RepeatType
import com.voicereminder.vip.core.model.Weekdays
import com.voicereminder.vip.core.util.TimeUtils
import com.voicereminder.vip.ui.components.AppBackground
import com.voicereminder.vip.ui.components.AppTopBar
import com.voicereminder.vip.ui.components.InfoNote
import com.voicereminder.vip.ui.components.SectionCard
import com.voicereminder.vip.ui.components.SectionTitle
import com.voicereminder.vip.ui.components.ValueStepper
import com.voicereminder.vip.ui.theme.Gold
import com.voicereminder.vip.ui.theme.Teal
import com.voicereminder.vip.ui.vm.AppViewModel
import com.voicereminder.vip.ui.vm.RoutineStepInput
import java.time.LocalDate

@Composable
fun RoutineEditorScreen(
    app: AppViewModel,
    container: AppContainer,
    groupId: Long,
    onBack: () -> Unit
) {
    val settings by app.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var title by remember { mutableStateOf("") }
    var epochDay by remember { mutableStateOf(TimeUtils.todayEpochDay()) }
    var repeatType by remember { mutableStateOf(RepeatType.DAILY) }
    var daysMask by remember { mutableStateOf(0) }
    val steps = remember { mutableStateListOf<RoutineStepInput>() }
    var initialized by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(groupId > 0L) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(groupId) {
        if (groupId > 0L) {
            val group = runCatching { container.alarmRepository.group(groupId) }.getOrDefault(emptyList())
            if (group.isEmpty()) {
                error = "لم يُعثر على السلسلة المطلوبة."
            } else {
                val ordered = group.sortedBy { it.stepIndex }
                val first = ordered.first()
                title = first.groupTitle ?: first.title.substringBefore(" •")
                repeatType = first.repeatType
                daysMask = first.daysMask
                epochDay = first.dateEpochDay ?: TimeUtils.todayEpochDay()
                steps.clear()
                ordered.forEach {
                    steps.add(RoutineStepInput(it.hour, it.minute, it.phrase))
                }
            }
        } else {
            repeatType = RepeatType.DAILY
            val now = TimeUtils.fromMillis(System.currentTimeMillis())
            val base = now.hour * 60 + now.minute + 30
            steps.add(
                RoutineStepInput(
                    hour = (base / 60).coerceAtMost(23),
                    minute = (base % 60).coerceAtMost(59),
                    phrase = ""
                )
            )
            steps.add(
                RoutineStepInput(
                    hour = ((base + 30) / 60).coerceAtMost(23),
                    minute = ((base + 30) % 60).coerceAtMost(59),
                    phrase = ""
                )
            )
        }
        loading = false
        initialized = true
    }

    fun save() {
        if (saving) return
        saving = true
        error = null
        app.saveRoutine(
            groupId = groupId.takeIf { it > 0L },
            title = title,
            epochDay = epochDay,
            repeatType = repeatType,
            daysMask = daysMask,
            steps = steps.toList()
        ) { message ->
            saving = false
            if (message == null) {
                onBack()
            } else {
                error = message
            }
        }
    }

    AppBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(
                title = if (groupId > 0L) "تعديل السلسلة" else "سلسلة جديدة",
                onBack = onBack
            )

            if (loading || !initialized) {
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
                        SectionCard {
                            SectionTitle("عنوان السلسلة")
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it.take(60) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("مثال: روتين الصباح") },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }

                    item {
                        SectionCard {
                            SectionTitle("التكرار")
                            Spacer(Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(
                                    listOf(
                                        RepeatType.ONCE,
                                        RepeatType.DAILY,
                                        RepeatType.WEEKLY
                                    )
                                ) { type ->
                                    FilterChip(
                                        selected = repeatType == type,
                                        onClick = {
                                            repeatType = type
                                            if (type == RepeatType.WEEKLY && daysMask == 0) {
                                                daysMask = Weekdays.maskOf(setOf(1, 2, 3, 4, 5))
                                            }
                                        },
                                        label = { Text(routineRepeatTitle(type)) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Gold.copy(alpha = 0.25f)
                                        )
                                    )
                                }
                            }

                            if (repeatType == RepeatType.ONCE) {
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val date = LocalDate.ofEpochDay(epochDay)
                                            DatePickerDialog(
                                                context,
                                                { _, year, month, day ->
                                                    epochDay = LocalDate.of(
                                                        year, month + 1, day
                                                    ).toEpochDay()
                                                },
                                                date.year,
                                                date.monthValue - 1,
                                                date.dayOfMonth
                                            ).apply {
                                                datePicker.minDate = LocalDate.now()
                                                    .atStartOfDay(TimeUtils.zone())
                                                    .toInstant().toEpochMilli()
                                            }.show()
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.DateRange,
                                        contentDescription = null,
                                        tint = Gold,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        TimeUtils.formatDate(epochDay),
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text("تغيير", color = Gold)
                                }
                            }

                            if (repeatType == RepeatType.WEEKLY) {
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Weekdays.displayOrder.forEach { day ->
                                        val selected = Weekdays.isEnabled(daysMask, day)
                                        Surface(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clickable {
                                                    val set = Weekdays
                                                        .setOf(daysMask)
                                                        .toMutableSet()
                                                    if (selected) set.remove(day) else set.add(day)
                                                    daysMask = Weekdays.maskOf(set)
                                                },
                                            shape = CircleShape,
                                            color = if (selected) Teal else Color.Transparent,
                                            border = BorderStroke(
                                                1.dp,
                                                if (selected) Teal
                                                else MaterialTheme.colorScheme.outlineVariant
                                            )
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    Weekdays.shortNameAr(day),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = if (selected) Color(0xFF00201C)
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    Weekdays.describe(daysMask),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (repeatType == RepeatType.DAILY) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "تتكرر كل يوم في الأوقات المحددة أدناه.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("الرسائل", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${steps.size} رسائل",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    itemsIndexed(steps) { index, step ->
                        StepCard(
                            index = index,
                            step = step,
                            use24Hour = settings.use24Hour,
                            canRemove = steps.size > 2,
                            onTimeClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        steps[index] = step.copy(hour = hour, minute = minute)
                                    },
                                    step.hour,
                                    step.minute,
                                    settings.use24Hour
                                ).show()
                            },
                            onPhraseChange = { phrase ->
                                steps[index] = step.copy(phrase = phrase.take(300))
                            },
                            onRemove = { steps.removeAt(index) }
                        )
                    }

                    item {
                        TextButton(
                            onClick = {
                                val last = steps.lastOrNull()
                                val next = ((last?.let { it.hour * 60 + it.minute } ?: 420) + 30) % (24 * 60)
                                steps.add(
                                    RoutineStepInput(
                                        hour = next / 60,
                                        minute = next % 60,
                                        phrase = ""
                                    )
                                )
                            }
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("إضافة رسالة")
                        }
                    }

                    item {
                        InfoNote(
                            "كل رسالة منبه مستقل. لا تُشغّل السلسلة تلقائيًا بعد إغلاق التنبيه؛ كل خطوة تعمل في وقتها المحدد."
                        )
                    }

                    error?.let { message ->
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    message,
                                    modifier = Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
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
                Button(
                    onClick = ::save,
                    enabled = !saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        contentColor = Color(0xFF251A00)
                    )
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF251A00)
                        )
                    } else {
                        Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (groupId > 0L) "حفظ التعديلات" else "إنشاء السلسلة",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    index: Int,
    step: RoutineStepInput,
    use24Hour: Boolean,
    canRemove: Boolean,
    onTimeClick: () -> Unit,
    onPhraseChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Gold.copy(alpha = 0.18f)
                ) {
                    Text(
                        "${index + 1}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = Gold
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    modifier = Modifier.clickable(onClick = onTimeClick),
                    shape = RoundedCornerShape(12.dp),
                    color = Teal.copy(alpha = 0.14f)
                ) {
                    Text(
                        TimeUtils.formatTime(step.hour, step.minute, use24Hour),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = Teal
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRemove, enabled = canRemove) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "حذف الرسالة",
                        tint = if (canRemove) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = step.phrase,
                onValueChange = onPhraseChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("ماذا يقول الهاتف في هذه الخطوة؟") },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp)
            )
        }
    }
}

private fun routineRepeatTitle(type: RepeatType): String = when (type) {
    RepeatType.ONCE -> "مرة واحدة"
    RepeatType.DAILY -> "كل يوم"
    RepeatType.WEEKLY -> "أيام محددة"
    RepeatType.EVERY_INTERVAL -> "كل فترة"
}
