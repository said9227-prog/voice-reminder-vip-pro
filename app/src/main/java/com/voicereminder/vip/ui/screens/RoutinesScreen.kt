package com.voicereminder.vip.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicereminder.vip.core.util.TimeUtils
import com.voicereminder.vip.ui.components.AppBackground
import com.voicereminder.vip.ui.components.ConfirmDialog
import com.voicereminder.vip.ui.components.EmptyState
import com.voicereminder.vip.ui.components.InfoNote
import com.voicereminder.vip.ui.components.Pill
import com.voicereminder.vip.ui.components.SectionCard
import com.voicereminder.vip.ui.components.repeatLabel
import com.voicereminder.vip.ui.theme.Gold
import com.voicereminder.vip.ui.theme.Teal
import com.voicereminder.vip.ui.vm.AppViewModel
import com.voicereminder.vip.ui.vm.RoutineUi

@Composable
fun RoutinesScreen(
    app: AppViewModel,
    onAddRoutine: () -> Unit,
    onEditRoutine: (Long) -> Unit
) {
    val routines by app.routines.collectAsStateWithLifecycle()
    val settings by app.settings.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<RoutineUi?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AppBackground {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(top = 16.dp)
                    ) {
                        Text("الرسائل المتسلسلة", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "سلسلة تذكيرات متتابعة: استيقظ، ثم تذكّر الدواء، ثم اخرج.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    InfoNote(
                        "كل خطوة تُجدول كمنبه مستقل. إذا أغلقت التطبيق أو أعدت تشغيل الهاتف، تعود الجدولة تلقائيًا عبر BootReceiver."
                    )
                }

                if (routines.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.Repeat,
                            title = "لا توجد سلاسل بعد",
                            message = "أنشئ سلسلة من رسالتين أو أكثر بأوقات مختلفة، مثل روتين الصباح أو خطة المذاكرة.",
                            actionLabel = "إنشاء سلسلة",
                            onAction = onAddRoutine
                        )
                    }
                } else {
                    items(routines, key = { it.groupId }) { routine ->
                        RoutineCard(
                            routine = routine,
                            use24Hour = settings.use24Hour,
                            onToggle = { enabled -> app.setRoutineEnabled(routine, enabled) },
                            onClick = { onEditRoutine(routine.groupId) },
                            onDelete = { pendingDelete = routine }
                        )
                    }
                }

                item {
                    Text(
                        "نصيحة: اجعل بين الرسائل دقيقتين على الأقل حتى لا تتداخل.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onAddRoutine,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = Gold,
            contentColor = Color(0xFF251A00),
            shape = CircleShape
        ) {
            Icon(Icons.Filled.Add, contentDescription = "إضافة سلسلة")
        }
    }

    pendingDelete?.let { routine ->
        ConfirmDialog(
            title = "حذف السلسلة؟",
            text = "ستُحذف «${routine.title}» وكل خطواتها (${routine.stepCount}) نهائيًا.",
            onConfirm = {
                app.deleteRoutine(routine)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun RoutineCard(
    routine: RoutineUi,
    use24Hour: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = Teal.copy(alpha = 0.16f)
            ) {
                Icon(
                    Icons.Outlined.Notifications,
                    contentDescription = null,
                    tint = Teal,
                    modifier = Modifier
                        .padding(9.dp)
                        .size(20.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    routine.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${routine.stepCount} رسائل • ${TimeUtils.formatTime(routine.firstHour, routine.firstMinute, use24Hour)} ← ${TimeUtils.formatTime(routine.lastHour, routine.lastMinute, use24Hour)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = routine.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "حذف",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(routine.steps, key = { it.id }) { step ->
                Pill(
                    text = "${TimeUtils.formatTime(step.hour, step.minute, use24Hour)} • ${step.phrase}",
                    color = if (step.enabled) Teal else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                repeatLabel(routine.steps.first()),
                style = MaterialTheme.typography.labelMedium,
                color = Gold
            )
            Spacer(Modifier.weight(1f))
            Text(
                "تعديل",
                style = MaterialTheme.typography.labelMedium,
                color = Gold,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
