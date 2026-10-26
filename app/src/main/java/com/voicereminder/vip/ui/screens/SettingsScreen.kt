package com.voicereminder.vip.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicereminder.vip.AppContainer
import com.voicereminder.vip.BuildConfig
import com.voicereminder.vip.core.model.ThemeMode
import com.voicereminder.vip.core.util.AlarmPermissions
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
import com.voicereminder.vip.ui.theme.Gold
import com.voicereminder.vip.ui.theme.Teal
import com.voicereminder.vip.ui.vm.AppViewModel
import kotlinx.coroutines.launch
import java.util.Locale

private enum class ConfirmAction { DELETE_ALARMS, DELETE_RECORDINGS, CLEAR_HISTORY }

@Composable
fun SettingsScreen(app: AppViewModel, container: AppContainer) {
    val settings by app.settings.collectAsStateWithLifecycle()
    val alarms by app.alarms.collectAsStateWithLifecycle()
    val recordings by app.recordings.collectAsStateWithLifecycle()
    val history by app.history.collectAsStateWithLifecycle()
    val ttsStatus by app.tts.status.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var permissionTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val exactAllowed = remember(permissionTick) { AlarmPermissions.canScheduleExact(context) }
    val notificationsAllowed = remember(permissionTick) {
        AlarmPermissions.canPostNotifications(context)
    }
    val fullScreenAllowed = remember(permissionTick) {
        AlarmPermissions.canUseFullScreenIntent(context)
    }
    val batteryIgnored = remember(permissionTick) {
        AlarmPermissions.isIgnoringBatteryOptimizations(context)
    }
    val micAllowed = remember(permissionTick) { AlarmPermissions.hasMicrophone(context) }

    var showLanguagePicker by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<ConfirmAction?>(null) }

    var rate by remember(settings.speechRate) { mutableFloatStateOf(settings.speechRate) }
    var pitch by remember(settings.pitch) { mutableFloatStateOf(settings.pitch) }
    var volume by remember(settings.volume) { mutableFloatStateOf(settings.volume) }

    AppBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { AppTopBar(title = "الإعدادات") }

            item {
                SectionCard {
                    SectionTitle("المظهر")
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(
                            listOf(
                                ThemeMode.SYSTEM to "النظام",
                                ThemeMode.LIGHT to "فاتح",
                                ThemeMode.DARK to "داكن"
                            )
                        ) { (mode, label) ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = {
                                    scope.launch { container.settingsRepository.setThemeMode(mode) }
                                },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Gold.copy(alpha = 0.25f)
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    LabeledSwitch(
                        title = "نظام 24 ساعة",
                        subtitle = "عرض الأوقات بدون ص/م",
                        checked = settings.use24Hour,
                        onCheckedChange = {
                            scope.launch { container.settingsRepository.setUse24Hour(it) }
                        }
                    )
                }
            }

            item {
                SectionCard {
                    SectionTitle(
                        "الصوت الافتراضي",
                        trailing = if (ttsStatus.ready) "المحرك جاهز" else "جارٍ التهيئة"
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLanguagePicker = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Language,
                            contentDescription = null,
                            tint = Teal,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("لغة النطق الافتراضية", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                languageName(settings.defaultLanguage),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("تغيير", style = MaterialTheme.typography.labelMedium, color = Gold)
                    }
                    DividerLine()
                    SliderSetting(
                        label = "سرعة النطق",
                        valueLabel = String.format(Locale.US, "%.2f×", rate),
                        value = rate,
                        range = 0.5f..2f,
                        onChange = { rate = it },
                        onFinished = {
                            scope.launch { container.settingsRepository.setSpeechRate(rate) }
                        }
                    )
                    SliderSetting(
                        label = "حدة الصوت",
                        valueLabel = String.format(Locale.US, "%.2f", pitch),
                        value = pitch,
                        range = 0.5f..2f,
                        onChange = { pitch = it },
                        onFinished = {
                            scope.launch { container.settingsRepository.setPitch(pitch) }
                        }
                    )
                    SliderSetting(
                        label = "مستوى الصوت",
                        valueLabel = "${(volume * 100).toInt()}%",
                        value = volume,
                        range = 0.05f..1f,
                        onChange = { volume = it },
                        onFinished = {
                            scope.launch { container.settingsRepository.setVolume(volume) }
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "تُطبَّق هذه القيم على المنبهات الجديدة عند إنشائها، وتظل قابلة للتعديل لكل منبه.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { app.ttsPreview("هذا اختبار لصوت التذكير.", settings.defaultLanguage) },
                            enabled = ttsStatus.ready,
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("تجربة الصوت") }
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(AlarmPermissions.ttsSettingsIntent())
                                }.onFailure {
                                    app.postEvent("تعذر فتح إعدادات النطق على هذا الجهاز.")
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("إعدادات النطق") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (ttsStatus.ready) {
                            "المحرك: ${ttsStatus.engineLabel.ifBlank { "محرك النظام" }} • ${ttsStatus.languages.size} لغة/صوت متاح"
                        } else {
                            ttsStatus.error ?: "جارٍ تهيئة محرك النطق…"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (ttsStatus.error != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            item {
                SectionCard {
                    SectionTitle("سلوك التنبيه")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ValueStepper(
                            label = "تكرار النطق",
                            value = "${settings.repeatCount}×",
                            onDecrease = if (settings.repeatCount > 1) {
                                {
                                    scope.launch {
                                        container.settingsRepository.setRepeatCount(settings.repeatCount - 1)
                                    }
                                }
                            } else null,
                            onIncrease = if (settings.repeatCount < 10) {
                                {
                                    scope.launch {
                                        container.settingsRepository.setRepeatCount(settings.repeatCount + 1)
                                    }
                                }
                            } else null
                        )
                        ValueStepper(
                            label = "الفاصل",
                            value = "${settings.repeatGapSeconds}ث",
                            onDecrease = if (settings.repeatGapSeconds > 2) {
                                {
                                    scope.launch {
                                        container.settingsRepository.setRepeatGap(settings.repeatGapSeconds - 2)
                                    }
                                }
                            } else null,
                            onIncrease = if (settings.repeatGapSeconds < 60) {
                                {
                                    scope.launch {
                                        container.settingsRepository.setRepeatGap(settings.repeatGapSeconds + 2)
                                    }
                                }
                            } else null
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ValueStepper(
                            label = "مدة التأجيل",
                            value = "${settings.snoozeMinutes}د",
                            onDecrease = if (settings.snoozeMinutes > 1) {
                                {
                                    scope.launch {
                                        container.settingsRepository.setSnoozeMinutes(settings.snoozeMinutes - 1)
                                    }
                                }
                            } else null,
                            onIncrease = if (settings.snoozeMinutes < 60) {
                                {
                                    scope.launch {
                                        container.settingsRepository.setSnoozeMinutes(settings.snoozeMinutes + 1)
                                    }
                                }
                            } else null
                        )
                        ValueStepper(
                            label = "حد التأجيل",
                            value = "${settings.maxSnoozes}×",
                            onDecrease = if (settings.maxSnoozes > 0) {
                                {
                                    scope.launch {
                                        container.settingsRepository.setMaxSnoozes(settings.maxSnoozes - 1)
                                    }
                                }
                            } else null,
                            onIncrease = if (settings.maxSnoozes < 10) {
                                {
                                    scope.launch {
                                        container.settingsRepository.setMaxSnoozes(settings.maxSnoozes + 1)
                                    }
                                }
                            } else null
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LabeledSwitch(
                        title = "الاهتزاز",
                        subtitle = "اهتزاز نمطي أثناء التنبيه (إن دعمه الجهاز)",
                        checked = settings.vibrate,
                        onCheckedChange = {
                            scope.launch { container.settingsRepository.setVibrate(it) }
                        }
                    )
                    LabeledSwitch(
                        title = "نغمة تمهيدية",
                        subtitle = "تنبيه قصير قبل بدء النطق",
                        checked = settings.preTone,
                        onCheckedChange = {
                            scope.launch { container.settingsRepository.setPreTone(it) }
                        }
                    )
                }
            }

            item {
                SectionCard {
                    SectionTitle("الصلاحيات")
                    Spacer(Modifier.height(4.dp))
                    PermissionRow(
                        icon = Icons.Outlined.Alarm,
                        title = "المنبهات الدقيقة",
                        granted = exactAllowed,
                        action = "إدارة",
                        onAction = {
                            runCatching {
                                context.startActivity(AlarmPermissions.exactAlarmIntent(context))
                            }
                        }
                    )
                    PermissionRow(
                        icon = Icons.Outlined.Notifications,
                        title = "الإشعارات",
                        granted = notificationsAllowed,
                        action = "إدارة",
                        onAction = {
                            runCatching {
                                context.startActivity(
                                    AlarmPermissions.notificationSettingsIntent(context)
                                )
                            }
                        }
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        PermissionRow(
                            icon = Icons.Outlined.Warning,
                            title = "شاشة التنبيه الكاملة",
                            granted = fullScreenAllowed,
                            action = "إدارة",
                            onAction = {
                                runCatching {
                                    context.startActivity(
                                        AlarmPermissions.fullScreenIntentSettings(context)
                                    )
                                }
                            }
                        )
                    }
                    PermissionRow(
                        icon = Icons.Outlined.Shield,
                        title = "تحسين البطارية",
                        granted = batteryIgnored,
                        grantedLabel = "مستثنى",
                        missingLabel = "قد يؤخر التنبيه",
                        action = "إدارة",
                        onAction = {
                            runCatching {
                                context.startActivity(
                                    AlarmPermissions.batteryOptimizationIntent(context)
                                )
                            }
                        }
                    )
                    PermissionRow(
                        icon = Icons.Outlined.CheckCircle,
                        title = "الميكروفون",
                        granted = micAllowed,
                        action = "إدارة",
                        onAction = {
                            runCatching {
                                context.startActivity(
                                    AlarmPermissions.microphoneSettingsIntent(context)
                                )
                            }
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    InfoNote(
                        "بعض الشركات (Xiaomi وOppo وVivo وغيرها) تُغلق التطبيقات في الخلفية. اسمح بالتشغيل التلقائي للتطبيق من إعدادات الهاتف لضمان وصول التنبيه."
                    )
                }
            }

            item {
                SectionCard {
                    SectionTitle("الصيانة")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "المنبهات: ${alarms.size} • التسجيلات: ${recordings.size} • السجل: ${history.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = { app.rescheduleAll() },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Gold,
                            contentColor = Color(0xFF251A00)
                        )
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("إعادة جدولة كل المنبهات")
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { confirmAction = ConfirmAction.DELETE_RECORDINGS },
                            enabled = recordings.isNotEmpty()
                        ) { Text("حذف التسجيلات") }
                        TextButton(
                            onClick = { confirmAction = ConfirmAction.CLEAR_HISTORY },
                            enabled = history.isNotEmpty()
                        ) { Text("مسح السجل") }
                        TextButton(
                            onClick = { confirmAction = ConfirmAction.DELETE_ALARMS },
                            enabled = alarms.isNotEmpty()
                        ) { Text("حذف المنبهات") }
                    }
                }
            }

            item {
                SectionCard {
                    SectionTitle("عن التطبيق")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Voice Reminder VIP PRO • الإصدار ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill(text = "يعمل دون إنترنت", color = Teal)
                        Pill(text = "بيانات محلية", color = Gold)
                        Pill(text = "إعلانات: لا", color = Teal)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "لا يطلب التطبيق صلاحية الإنترنت. نصوصك وتسجيلاتك تبقى على هاتفك. النطق يعتمد على محرك TTS وبيانات اللغة المثبتة على الجهاز.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "لا يوجد ضمان مطلق للتذكير على كل الأجهزة؛ جرّب منبهًا قبل الاعتماد عليه.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showLanguagePicker) {
        ChoiceDialog(
            title = "لغة النطق الافتراضية",
            options = listOf(
                "ar" to "العربية",
                "en-US" to "الإنجليزية",
                "id-ID" to "الإندونيسية"
            ),
            selected = settings.defaultLanguage,
            onSelect = {
                scope.launch { container.settingsRepository.setDefaultLanguage(it) }
            },
            onDismiss = { showLanguagePicker = false }
        )
    }

    confirmAction?.let { action ->
        val (title, text) = when (action) {
            ConfirmAction.DELETE_ALARMS ->
                "حذف كل المنبهات؟" to "سيُحذف كل منبه وكل سلسلة نهائيًا. لا يمكن التراجع."
            ConfirmAction.DELETE_RECORDINGS ->
                "حذف كل التسجيلات؟" to "ستُحذف ملفات الصوت نهائيًا وتُزال من كل المنبهات."
            ConfirmAction.CLEAR_HISTORY ->
                "مسح السجل؟" to "سيُحذف سجل التنبيهات نهائيًا. المنبهات لن تتأثر."
        }
        ConfirmDialog(
            title = title,
            text = text,
            confirmLabel = "تأكيد",
            onConfirm = {
                when (action) {
                    ConfirmAction.DELETE_ALARMS -> app.deleteAllAlarms()
                    ConfirmAction.DELETE_RECORDINGS -> app.deleteAllRecordings()
                    ConfirmAction.CLEAR_HISTORY -> app.clearHistory()
                }
                confirmAction = null
            },
            onDismiss = { confirmAction = null }
        )
    }
}

@Composable
private fun SliderSetting(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit
) {
    Column(modifier = Modifier.padding(top = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = Gold)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = range
        )
    }
}

@Composable
private fun PermissionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    granted: Boolean,
    action: String,
    onAction: () -> Unit,
    grantedLabel: String = "مفعّلة",
    missingLabel: String = "غير مفعّلة"
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (granted) Teal else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (granted) grantedLabel else missingLabel,
                style = MaterialTheme.typography.labelMedium,
                color = if (granted) Teal else MaterialTheme.colorScheme.error
            )
        }
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (granted) Teal.copy(alpha = 0.12f) else Gold.copy(alpha = 0.14f),
            border = BorderStroke(
                1.dp,
                if (granted) Teal.copy(alpha = 0.3f) else Gold.copy(alpha = 0.3f)
            )
        ) {
            Text(
                action,
                modifier = Modifier
                    .clickable(onClick = onAction)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = if (granted) Teal else Gold,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun languageName(tag: String): String = when (tag) {
    "ar" -> "العربية"
    "en-US" -> "الإنجليزية"
    "id-ID" -> "الإندونيسية"
    else -> tag
}
