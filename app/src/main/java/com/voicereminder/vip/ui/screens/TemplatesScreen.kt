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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicereminder.vip.core.model.RepeatType
import com.voicereminder.vip.core.util.TimeUtils
import com.voicereminder.vip.data.db.TemplateWithItems
import com.voicereminder.vip.ui.components.AppBackground
import com.voicereminder.vip.ui.components.AppTopBar
import com.voicereminder.vip.ui.components.EmptyState
import com.voicereminder.vip.ui.components.InfoNote
import com.voicereminder.vip.ui.components.Pill
import com.voicereminder.vip.ui.components.categoryIcon
import com.voicereminder.vip.ui.components.parseColor
import com.voicereminder.vip.ui.theme.Gold
import com.voicereminder.vip.ui.theme.Teal
import com.voicereminder.vip.ui.vm.AppViewModel
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun TemplatesScreen(app: AppViewModel, onBack: () -> Unit) {
    val templates by app.templates.collectAsStateWithLifecycle()
    val settings by app.settings.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<TemplateWithItems?>(null) }

    AppBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(title = "القوالب", onBack = onBack)

            if (templates.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Outlined.Layers,
                        title = "القوالب قيد التجهيز",
                        message = "تُزرع القوالب تلقائيًا عند أول تشغيل. أعد فتح التطبيق بعد لحظة."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Text(
                            "ابدأ من سلسلة جاهزة، ثم عدّل الأوقات والصياغة كما تريد.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(templates, key = { it.template.id }) { template ->
                        TemplateCard(
                            template = template,
                            onClick = { selected = template }
                        )
                    }

                    item {
                        InfoNote(
                            "القوالب تُنشئ منبهات مستقلة داخل سلسلة واحدة. يمكنك تعديلها أو حذفها من تبويب «الروتين»."
                        )
                    }
                }
            }
        }
    }

    selected?.let { template ->
        TemplateUseDialog(
            template = template,
            defaultHour = TimeUtils.fromMillis(System.currentTimeMillis()).hour,
            use24Hour = settings.use24Hour,
            onDismiss = { selected = null },
            onCreate = { epochDay, hour, minute, repeat, done ->
                app.createFromTemplate(
                    template = template,
                    epochDay = epochDay,
                    hour = hour,
                    minute = minute,
                    repeat = repeat
                ) { error ->
                    if (error == null) {
                        selected = null
                        onBack()
                    }
                    done(error)
                }
            }
        )
    }
}

@Composable
private fun TemplateCard(template: TemplateWithItems, onClick: () -> Unit) {
    val color = parseColor(template.template.colorHex)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(color.copy(alpha = 0.16f), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        categoryIcon(template.template.iconKey),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        template.template.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${template.items.size} رسائل • ${template.template.categoryName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = color
                    )
                }
                Icon(
                    Icons.Outlined.Layers,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                template.template.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(template.items.sortedBy { it.sortOrder }) { item ->
                    Pill(
                        text = "+${item.offsetMinutes}د • ${item.phrase}",
                        color = color
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateUseDialog(
    template: TemplateWithItems,
    defaultHour: Int,
    use24Hour: Boolean,
    onDismiss: () -> Unit,
    onCreate: (
        epochDay: Long,
        hour: Int,
        minute: Int,
        repeat: RepeatType,
        done: (String?) -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    var epochDay by remember { mutableLongStateOf(TimeUtils.todayEpochDay()) }
    val defaultTime = remember { LocalTime.now().plusMinutes(15) }
    var hour by remember { mutableStateOf(defaultTime.hour) }
    var minute by remember { mutableStateOf(defaultTime.minute) }
    var repeat by remember { mutableStateOf(RepeatType.DAILY) }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!creating) onDismiss() },
        title = { Text(template.template.title) },
        text = {
            Column {
                Text(
                    template.template.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !creating) {
                            TimePickerDialog(
                                context,
                                { _, h, m ->
                                    hour = h
                                    minute = m
                                },
                                hour,
                                minute,
                                use24Hour
                            ).show()
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("وقت البداية", modifier = Modifier.weight(1f))
                    Text(
                        TimeUtils.formatTime(hour, minute, use24Hour),
                        color = Gold,
                        fontWeight = FontWeight.Bold
                    )
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(
                        listOf(
                            RepeatType.ONCE,
                            RepeatType.DAILY,
                            RepeatType.WEEKLY
                        )
                    ) { type ->
                        FilterChip(
                            selected = repeat == type,
                            onClick = {
                                repeat = type
                                if (type == RepeatType.ONCE) {
                                    epochDay = TimeUtils.todayEpochDay()
                                }
                            },
                            label = { Text(templateRepeatTitle(type)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Gold.copy(alpha = 0.25f)
                            )
                        )
                    }
                }
                if (repeat == RepeatType.ONCE) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !creating) {
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
                        Text("اليوم", modifier = Modifier.weight(1f))
                        Text(TimeUtils.formatDate(epochDay), color = Gold)
                    }
                }
                if (repeat == RepeatType.WEEKLY) {
                    Text(
                        "سيُكرر القالب أسبوعيًا في أيام العمل افتراضيًا، ويمكن تغيير الأيام لاحقًا.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                InfoNote(
                    "ستُنشأ ${template.items.size} رسائل تبدأ من الوقت المحدد، بفواصل القالب."
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (creating) return@Button
                    creating = true
                    error = null
                    onCreate(epochDay, hour, minute, repeat) { message ->
                        creating = false
                        if (message != null) error = message
                    }
                },
                enabled = !creating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gold,
                    contentColor = Color(0xFF251A00)
                )
            ) {
                if (creating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF251A00)
                    )
                } else {
                    Text("إنشاء")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !creating) { Text("إلغاء") }
        }
    )
}

private fun templateRepeatTitle(type: RepeatType): String = when (type) {
    RepeatType.ONCE -> "مرة واحدة"
    RepeatType.DAILY -> "كل يوم"
    RepeatType.WEEKLY -> "أسبوعي"
    RepeatType.EVERY_INTERVAL -> "كل فترة"
}
