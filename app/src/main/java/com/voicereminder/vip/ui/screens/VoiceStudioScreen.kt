package com.voicereminder.vip.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicereminder.vip.AppContainer
import com.voicereminder.vip.data.db.RecordingEntity
import com.voicereminder.vip.ui.components.AppBackground
import com.voicereminder.vip.ui.components.ConfirmDialog
import com.voicereminder.vip.ui.components.EmptyState
import com.voicereminder.vip.ui.components.InfoNote
import com.voicereminder.vip.ui.components.Pill
import com.voicereminder.vip.ui.components.SectionCard
import com.voicereminder.vip.ui.components.SectionTitle
import com.voicereminder.vip.ui.theme.Gold
import com.voicereminder.vip.ui.theme.Teal
import com.voicereminder.vip.ui.vm.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun VoiceStudioScreen(app: AppViewModel, container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.collectAsStateWithLifecycle()
    val recordings by app.recordings.collectAsStateWithLifecycle()
    val alarms by app.alarms.collectAsStateWithLifecycle()
    val ttsStatus by app.tts.status.collectAsStateWithLifecycle()
    val isRecording by container.recorder.isRecording.collectAsStateWithLifecycle()
    val isPlaying by container.recorder.isPlaying.collectAsStateWithLifecycle()

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var recordStartedAt by remember { mutableLongStateOf(0L) }
    var playingId by remember { mutableStateOf<Long?>(null) }
    var pendingDelete by remember { mutableStateOf<RecordingEntity?>(null) }
    var renameTarget by remember { mutableStateOf<RecordingEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var previewText by remember {
        mutableStateOf("حان وقت تناول دواء الضغط. لا تنسَ كوب الماء.")
    }
    var previewLanguage by remember { mutableStateOf(settings.defaultLanguage) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (!granted) app.postEvent("يلزم منح صلاحية الميكروفون للتسجيل.")
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordStartedAt = System.currentTimeMillis()
            elapsedMs = 0
            while (true) {
                elapsedMs = System.currentTimeMillis() - recordStartedAt
                delay(200)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (container.recorder.isRecording.value) container.recorder.cancel()
            container.recorder.stopPlayback()
        }
    }

    fun togglePlayback(recording: RecordingEntity) {
        if (playingId == recording.id) {
            container.recorder.stopPlayback()
            playingId = null
            return
        }
        app.stopPreview()
        playingId = recording.id
        val started = container.recorder.play(recording.filePath, settings.volume) {
            playingId = null
        }
        if (!started) {
            playingId = null
            app.postEvent("تعذر تشغيل التسجيل.")
        }
    }

    AppBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 16.dp)
                ) {
                    Text("استوديو الصوت", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "سجّل صوتك أو جرّب النطق الآلي. كل الملفات تبقى على هاتفك.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Teal.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Language,
                                contentDescription = null,
                                tint = Teal,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("محرك النطق", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (ttsStatus.ready) {
                                    ttsStatus.engineLabel.ifBlank { "محرك النظام" }
                                } else {
                                    ttsStatus.error ?: "جارٍ التهيئة…"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (ttsStatus.error != null) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (ttsStatus.ready) {
                            Pill(text = "جاهز", color = Teal)
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Gold
                            )
                        }
                    }

                    val coreLanguages = ttsStatus.languages
                        .filter {
                            it.tag.startsWith("ar") ||
                                it.tag.startsWith("en") ||
                                it.tag.startsWith("id")
                        }
                        .take(14)
                    if (coreLanguages.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(coreLanguages, key = { it.tag }) { language ->
                                Pill(
                                    text = "${language.label}${if (language.offline) " • دون إنترنت" else ""}",
                                    color = when {
                                        !language.available -> MaterialTheme.colorScheme.error
                                        language.offline -> Teal
                                        else -> Gold
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "توفّر الأصوات و«العمل دون إنترنت» يعتمدان على محرك TTS وبيانات اللغة المثبتة على هاتفك. قد يحتاج صوت جديد إلى تنزيل بيانات اللغة مرة واحدة.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = previewText,
                        onValueChange = { previewText = it.take(240) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("جرّب النطق") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(
                            listOf(
                                "ar" to "العربية",
                                "en-US" to "الإنجليزية",
                                "id-ID" to "الإندونيسية"
                            )
                        ) { (tag, label) ->
                            FilterChip(
                                selected = previewLanguage == tag,
                                onClick = { previewLanguage = tag },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Gold.copy(alpha = 0.25f)
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (ttsStatus.speaking) app.stopPreview()
                                else app.ttsPreview(previewText, previewLanguage)
                            },
                            enabled = ttsStatus.ready && previewText.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Gold,
                                contentColor = Color(0xFF251A00)
                            )
                        ) {
                            Icon(
                                if (ttsStatus.speaking) Icons.Outlined.Stop
                                else Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (ttsStatus.speaking) "إيقاف" else "تشغيل")
                        }
                        OutlinedButton(
                            onClick = { app.ttsPreview(previewText, previewLanguage) },
                            enabled = ttsStatus.ready && previewText.isNotBlank(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("تكرار")
                        }
                    }
                }
            }

            item {
                SectionCard {
                    SectionTitle("تسجيل صوتي", trailing = "${recordings.size} تسجيل")
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier
                                .size(52.dp)
                                .clickable {
                                    if (isRecording) {
                                        val result = container.recorder.stop()
                                        if (result == null) {
                                            app.postEvent("لم يُسجل صوت. جرّب مرة أخرى.")
                                        } else {
                                            val (file, duration) = result
                                            scope.launch {
                                                runCatching {
                                                    container.recordingRepository.save(
                                                        RecordingEntity(
                                                            name = "تسجيل ${recordings.size + 1}",
                                                            filePath = file.absolutePath,
                                                            durationMs = duration,
                                                            sizeBytes = file.length()
                                                        )
                                                    )
                                                }.onSuccess {
                                                    app.postEvent("تم حفظ التسجيل")
                                                }.onFailure {
                                                    app.postEvent("تعذر حفظ التسجيل.")
                                                }
                                            }
                                        }
                                    } else {
                                        app.stopPreview()
                                        if (!hasMicPermission) {
                                            micPermissionLauncher.launch(
                                                Manifest.permission.RECORD_AUDIO
                                            )
                                        } else {
                                            if (container.recorder.start() == null) {
                                                app.postEvent("تعذر بدء التسجيل. تحقق من الميكروفون.")
                                            }
                                        }
                                    }
                                },
                            shape = CircleShape,
                            color = if (isRecording) {
                                MaterialTheme.colorScheme.error
                            } else {
                                Gold
                            }
                        ) {
                            Icon(
                                if (isRecording) Icons.Outlined.Stop else Icons.Outlined.Mic,
                                contentDescription = if (isRecording) "إيقاف التسجيل" else "بدء التسجيل",
                                tint = if (isRecording) MaterialTheme.colorScheme.onError
                                else Color(0xFF251A00),
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                if (isRecording) "جارٍ التسجيل…" else "اضغط للتسجيل",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                if (isRecording) formatDuration(elapsedMs)
                                else "الصوت يُحفظ في ذاكرة التطبيق الخاصة",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isRecording) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (isRecording) {
                            TextButton(
                                onClick = {
                                    container.recorder.cancel()
                                    elapsedMs = 0
                                }
                            ) { Text("إلغاء") }
                        }
                    }
                    if (!hasMicPermission) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "لم تُمنح صلاحية الميكروفون بعد.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (recordings.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.GraphicEq,
                        title = "لا توجد تسجيلات",
                        message = "سجّل رسالة بصوتك ليشغّلها المنبه بدل النطق الآلي. التسجيلات لا تُرفع لأي خادم."
                    )
                }
            } else {
                items(recordings, key = { it.id }) { recording ->
                    val usedCount = alarms.count { it.recordingId == recording.id }
                    RecordingCard(
                        recording = recording,
                        usedCount = usedCount,
                        isPlaying = isPlaying && playingId == recording.id,
                        onPlay = { togglePlayback(recording) },
                        onRename = {
                            renameTarget = recording
                            renameText = recording.name
                        },
                        onDelete = { pendingDelete = recording }
                    )
                }
            }

            item {
                InfoNote(
                    "التسجيلات تُشغَّل عبر مسار الصوت Alarm، وتبقى داخل مساحة التطبيق. حذف التسجيل يزيله من أي منبه مرتبط."
                )
            }
        }
    }

    pendingDelete?.let { recording ->
        ConfirmDialog(
            title = "حذف التسجيل؟",
            text = "سيُحذف «${recording.name}» من الهاتف ويُزال من المنبهات المرتبطة.",
            onConfirm = {
                if (playingId == recording.id) {
                    container.recorder.stopPlayback()
                    playingId = null
                }
                app.deleteRecording(recording)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }

    renameTarget?.let { recording ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("إعادة تسمية التسجيل") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it.take(50) },
                    singleLine = true,
                    label = { Text("الاسم") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        app.renameRecording(recording, renameText)
                        renameTarget = null
                    },
                    enabled = renameText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        contentColor = Color(0xFF251A00)
                    )
                ) { Text("حفظ") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun RecordingCard(
    recording: RecordingEntity,
    usedCount: Int,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
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
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .clickable(onClick = onPlay),
                shape = CircleShape,
                color = if (isPlaying) MaterialTheme.colorScheme.error
                else Teal.copy(alpha = 0.16f)
            ) {
                Icon(
                    if (isPlaying) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                    contentDescription = if (isPlaying) "إيقاف" else "تشغيل",
                    tint = if (isPlaying) MaterialTheme.colorScheme.onError else Teal,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    recording.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${formatDuration(recording.durationMs)} • ${formatSize(recording.sizeBytes)}" +
                        if (usedCount > 0) " • في $usedCount منبه" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRename, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "إعادة تسمية",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "حذف",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "0 ك.ب"
    bytes < 1024 * 1024 -> "${bytes / 1024} ك.ب"
    else -> String.format(Locale.US, "%.1f م.ب", bytes / 1024f / 1024f)
}
