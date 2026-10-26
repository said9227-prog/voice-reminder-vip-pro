package com.voicereminder.vip.ui.screens

import android.os.Build
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicereminder.vip.AppContainer
import com.voicereminder.vip.core.util.AlarmPermissions
import com.voicereminder.vip.core.util.TimeUtils
import com.voicereminder.vip.data.db.AlarmEntity
import com.voicereminder.vip.data.db.CategoryEntity
import com.voicereminder.vip.ui.components.AlarmCard
import com.voicereminder.vip.ui.components.AppBackground
import com.voicereminder.vip.ui.components.ConfirmDialog
import com.voicereminder.vip.ui.components.EmptyState
import com.voicereminder.vip.ui.components.Pill
import com.voicereminder.vip.ui.components.SectionCard
import com.voicereminder.vip.ui.components.parseColor
import com.voicereminder.vip.ui.components.repeatLabel
import com.voicereminder.vip.ui.theme.Gold
import com.voicereminder.vip.ui.theme.Teal
import com.voicereminder.vip.ui.vm.AppViewModel

@Composable
fun HomeScreen(
    app: AppViewModel,
    container: AppContainer,
    onAddAlarm: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val alarms by app.alarms.collectAsStateWithLifecycle()
    val categories by app.categories.collectAsStateWithLifecycle()
    val settings by app.settings.collectAsStateWithLifecycle()
    val now by app.now.collectAsStateWithLifecycle()
    val nextAlarm by app.nextAlarm.collectAsStateWithLifecycle()
    val todayTriggered by app.todayTriggered.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val exactAllowed = AlarmPermissions.canScheduleExact(context)
    val notificationsAllowed = AlarmPermissions.canPostNotifications(context)
    val fullScreenAllowed = AlarmPermissions.canUseFullScreenIntent(context)

    var selectedCategory by remember { mutableStateOf<Long?>(null) }
    var pendingDelete by remember { mutableStateOf<AlarmEntity?>(null) }

    val categoryById: Map<Long, CategoryEntity> = categories.associateBy { it.id }
    val filtered = alarms
        .filter { selectedCategory == null || it.categoryId == selectedCategory }
        .sortedWith(compareByDescending<AlarmEntity> { it.enabled }.thenBy { it.hour * 60 + it.minute })

    Box(modifier = Modifier.fillMaxSize()) {
        AppBackground {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Header(
                        now = now,
                        use24Hour = false,
                        onOpenSettings = onOpenSettings
                    )
                }

                item {
                    HeroCard(
                        now = now,
                        todayTriggered = todayTriggered,
                        greeting = TimeUtils.greeting(
                            TimeUtils.fromMillis(now).hour
                        )
                    )
                }

                item {
                    NextAlarmCard(
                        alarm = nextAlarm,
                        category = nextAlarm?.categoryId?.let { categoryById[it] },
                        use24Hour = settings.use24Hour,
                        now = now,
                        onEdit = { nextAlarm?.let { onEditAlarm(it.id) } },
                        onAdd = onAddAlarm
                    )
                }

                if (!exactAllowed || !notificationsAllowed || !fullScreenAllowed) {
                    item {
                        PermissionCard(
                            exactAllowed = exactAllowed,
                            notificationsAllowed = notificationsAllowed,
                            fullScreenAllowed = fullScreenAllowed,
                            onOpenExact = {
                                runCatching {
                                    context.startActivity(AlarmPermissions.exactAlarmIntent(context))
                                }
                            },
                            onOpenNotifications = {
                                runCatching {
                                    context.startActivity(AlarmPermissions.notificationSettingsIntent(context))
                                }
                            },
                            onOpenFullScreen = {
                                runCatching {
                                    context.startActivity(AlarmPermissions.fullScreenIntentSettings(context))
                                }
                            }
                        )
                    }
                }

                item {
                    QuickActions(
                        onOpenAssistant = onOpenAssistant,
                        onOpenTemplates = onOpenTemplates,
                        onOpenCategories = onOpenCategories,
                        onOpenStudio = onOpenStudio
                    )
                }

                if (alarms.isNotEmpty()) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedCategory == null,
                                    onClick = { selectedCategory = null },
                                    label = { Text("الكل") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Gold.copy(alpha = 0.25f)
                                    )
                                )
                            }
                            items(categories) { category ->
                                val color = parseColor(category.colorHex)
                                FilterChip(
                                    selected = selectedCategory == category.id,
                                    onClick = {
                                        selectedCategory =
                                            if (selectedCategory == category.id) null else category.id
                                    },
                                    label = { Text(category.name) },
                                    leadingIcon = {
                                        Box(
                                            Modifier
                                                .size(8.dp)
                                                .background(color, CircleShape)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = color.copy(alpha = 0.22f)
                                    )
                                )
                            }
                        }
                    }
                }

                if (alarms.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.Notifications,
                            title = "لا توجد منبهات بعد",
                            message = "أنشئ تذكيرًا صوتيًا، أو ابدأ من قالب جاهز. كل شيء يعمل محليًا ودون إنترنت.",
                            actionLabel = "إضافة منبه",
                            onAction = onAddAlarm
                        )
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "منبهاتك",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                "${filtered.size} منبه",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(filtered, key = { it.id }) { alarm ->
                        AlarmCard(
                            alarm = alarm,
                            category = alarm.categoryId?.let { categoryById[it] },
                            use24Hour = settings.use24Hour,
                            now = now,
                            onToggle = { app.toggleAlarm(alarm) },
                            onClick = { onEditAlarm(alarm.id) },
                            onDelete = { pendingDelete = alarm }
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "هاتفك يتذكر، ويتحدث في الوقت المناسب — محليًا وبدون إنترنت.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onAddAlarm,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = Gold,
            contentColor = Color(0xFF251A00),
            shape = CircleShape
        ) {
            Icon(Icons.Filled.Add, contentDescription = "إضافة منبه")
        }
    }

    pendingDelete?.let { alarm ->
        ConfirmDialog(
            title = "حذف المنبه؟",
            text = "سيُحذف «${alarm.title}» نهائيًا مع إعداده المحلي. السجل السابق لن يُحذف.",
            onConfirm = {
                app.deleteAlarm(alarm)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun Header(now: Long, use24Hour: Boolean, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = TimeUtils.greeting(TimeUtils.fromMillis(now).hour),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = TimeUtils.formatDate(TimeUtils.fromMillis(now).toLocalDate().toEpochDay()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = "الإعدادات",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun HeroCard(now: Long, todayTriggered: Int, greeting: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.25f))
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            Gold.copy(alpha = 0.18f),
                            Teal.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
                .padding(22.dp)
        ) {
            Column {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.labelLarge,
                    color = Gold
                )
                Text(
                    text = TimeUtils.formatClock(now, use24 = false),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(
                        text = "بدأت $todayTriggered تذكيرات اليوم",
                        color = Teal
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "هاتفك يتذكر، ويتحدث في الوقت المناسب.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun NextAlarmCard(
    alarm: AlarmEntity?,
    category: CategoryEntity?,
    use24Hour: Boolean,
    now: Long,
    onEdit: () -> Unit,
    onAdd: () -> Unit
) {
    SectionCard(
        modifier = Modifier.clickable(enabled = alarm != null, onClick = onEdit)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "أقرب منبه قادم",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (alarm == null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "لا يوجد منبه مجدول",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        "أضف تذكيرًا صوتيًا ليبدأ اليوم بشكل أفضل.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = alarm.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${repeatLabel(alarm)} • ${alarm.phrase}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    alarm.nextTriggerAt?.let { next ->
                        Text(
                            text = "${TimeUtils.relative(next, now)} • ${TimeUtils.formatDateTime(next, use24Hour)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Gold
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (alarm != null) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = (category?.let { parseColor(it.colorHex) } ?: Gold)
                            .copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = TimeUtils.formatTime(alarm.hour, alarm.minute, use24Hour),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.headlineMedium,
                            color = category?.let { parseColor(it.colorHex) } ?: Gold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    TextButton(onClick = onAdd) { Text("إضافة") }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    exactAllowed: Boolean,
    notificationsAllowed: Boolean,
    fullScreenAllowed: Boolean,
    onOpenExact: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenFullScreen: () -> Unit
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "لضمان عمل المنبهات بدقة",
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(Modifier.height(8.dp))
        if (!exactAllowed) {
            Text(
                "صلاحية المنبهات الدقيقة غير مفعّلة؛ قد يتأخر التنبيه.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onOpenExact) { Text("فتح إعداد المنبهات الدقيقة") }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsAllowed) {
            Text(
                "الإشعارات غير مفعّلة، وقد لا تظهر شاشة التنبيه.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onOpenNotifications) { Text("فتح إعدادات الإشعارات") }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !fullScreenAllowed) {
            Text(
                "صلاحية الشاشة الكاملة غير مفعّلة؛ قد لا تظهر شاشة التنبيه فوق القفل.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onOpenFullScreen) { Text("فتح إعداد الشاشة الكاملة") }
        }
    }
}

@Composable
private fun QuickActions(
    onOpenAssistant: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenStudio: () -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            QuickAction(
                icon = Icons.Outlined.SmartToy,
                title = "المساعد الذكي",
                subtitle = "أنشئ من جملة",
                color = Gold,
                onClick = onOpenAssistant
            )
        }
        item {
            QuickAction(
                icon = Icons.Outlined.Layers,
                title = "القوالب",
                subtitle = "روتين جاهز",
                color = Teal,
                onClick = onOpenTemplates
            )
        }
        item {
            QuickAction(
                icon = Icons.Outlined.GraphicEq,
                title = "الأصوات",
                subtitle = "نطق وتسجيل",
                color = MaterialTheme.colorScheme.tertiary,
                onClick = onOpenStudio
            )
        }
        item {
            QuickAction(
                icon = Icons.Outlined.Category,
                title = "التصنيفات",
                subtitle = "تنظيم التذكيرات",
                color = MaterialTheme.colorScheme.secondary,
                onClick = onOpenCategories
            )
        }
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(142.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(color.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
