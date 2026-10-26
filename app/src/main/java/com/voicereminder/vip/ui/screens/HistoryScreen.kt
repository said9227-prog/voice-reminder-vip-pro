package com.voicereminder.vip.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicereminder.vip.core.model.HistoryStatus
import com.voicereminder.vip.core.util.TimeUtils
import com.voicereminder.vip.data.db.HistoryEntity
import com.voicereminder.vip.ui.components.AppBackground
import com.voicereminder.vip.ui.components.AppTopBar
import com.voicereminder.vip.ui.components.ConfirmDialog
import com.voicereminder.vip.ui.components.EmptyState
import com.voicereminder.vip.ui.components.InfoNote
import com.voicereminder.vip.ui.components.Pill
import com.voicereminder.vip.ui.theme.Gold
import com.voicereminder.vip.ui.theme.Teal
import com.voicereminder.vip.ui.vm.AppViewModel

@Composable
fun HistoryScreen(app: AppViewModel) {
    val history by app.history.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf<HistoryStatus?>(null) }
    var showClear by remember { mutableStateOf(false) }

    val filtered = history.filter { filter == null || it.status == filter }

    AppBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(
                title = "السجل",
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClear = true }) {
                            Icon(
                                Icons.Outlined.DeleteSweep,
                                contentDescription = "مسح السجل",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )

            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Outlined.History,
                        title = "السجل فارغ",
                        message = "سيظهر هنا كل تنبيه بدأ أو أُغلق أو أُجّل، مع الوقت والحالة."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            "${history.size} حدث محفوظ على هذا الهاتف",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    selected = filter == null,
                                    onClick = { filter = null },
                                    label = { Text("الكل") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Gold.copy(alpha = 0.25f)
                                    )
                                )
                            }
                            items(HistoryStatus.entries.toList()) { status ->
                                FilterChip(
                                    selected = filter == status,
                                    onClick = {
                                        filter = if (filter == status) null else status
                                    },
                                    label = { Text(statusLabel(status)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = statusColor(status).copy(alpha = 0.22f)
                                    )
                                )
                            }
                        }
                    }

                    if (filtered.isEmpty()) {
                        item {
                            InfoNote("لا توجد أحداث بهذه الحالة.")
                        }
                    } else {
                        items(filtered, key = { it.id }) { entry ->
                            HistoryRow(entry)
                        }
                    }

                    item {
                        InfoNote(
                            "يُحفظ السجل محليًا فقط. لا تُسجل نصوص التسجيلات الصوتية في السجل، بل العبارة المكتوبة."
                        )
                    }
                }
            }
        }
    }

    if (showClear) {
        ConfirmDialog(
            title = "مسح السجل؟",
            text = "سيُحذف كل سجل التنبيهات نهائيًا. المنبهات نفسها لن تتأثر.",
            confirmLabel = "مسح",
            onConfirm = {
                app.clearHistory()
                showClear = false
            },
            onDismiss = { showClear = false }
        )
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntity) {
    val color = statusColor(entry.status)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, color.copy(alpha = 0.24f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(color.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    statusIcon(entry.status),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    entry.phrase.ifBlank { "بدون عبارة" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        TimeUtils.formatDateTime(entry.firedAt, use24 = false),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (entry.snoozeCount > 0) {
                        Text(
                            " • تأجيل ${entry.snoozeCount}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Gold
                        )
                    }
                }
                if (!entry.groupTitle.isNullOrBlank()) {
                    Text(
                        "${entry.groupTitle} • خطوة ${entry.stepIndex + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Teal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Pill(text = statusLabel(entry.status), color = color)
        }
    }
}

private fun statusLabel(status: HistoryStatus): String = when (status) {
    HistoryStatus.FIRED -> "بدأ"
    HistoryStatus.DISMISSED -> "أُغلق"
    HistoryStatus.SNOOZED -> "أُجّل"
    HistoryStatus.MISSED -> "فات"
    HistoryStatus.CANCELLED -> "أُلغي"
}

private fun statusColor(status: HistoryStatus): Color = when (status) {
    HistoryStatus.FIRED -> Teal
    HistoryStatus.DISMISSED -> Gold
    HistoryStatus.SNOOZED -> Color(0xFF7FB2FF)
    HistoryStatus.MISSED -> Color(0xFFFF8A9B)
    HistoryStatus.CANCELLED -> Color(0xFFB0A8B9)
}

private fun statusIcon(status: HistoryStatus): ImageVector = when (status) {
    HistoryStatus.FIRED -> Icons.Outlined.Notifications
    HistoryStatus.DISMISSED -> Icons.Outlined.CheckCircle
    HistoryStatus.SNOOZED -> Icons.Outlined.Snooze
    HistoryStatus.MISSED -> Icons.Outlined.Warning
    HistoryStatus.CANCELLED -> Icons.Outlined.Info
}
