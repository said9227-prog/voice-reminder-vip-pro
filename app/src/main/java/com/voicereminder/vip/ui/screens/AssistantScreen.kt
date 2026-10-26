package com.voicereminder.vip.ui.screens

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicereminder.vip.core.model.AlarmDraft
import com.voicereminder.vip.core.model.ParsedReminder
import com.voicereminder.vip.core.model.RepeatType
import com.voicereminder.vip.core.model.Weekdays
import com.voicereminder.vip.core.nlp.ReminderParser
import com.voicereminder.vip.core.util.TimeUtils
import com.voicereminder.vip.ui.components.AppBackground
import com.voicereminder.vip.ui.components.AppTopBar
import com.voicereminder.vip.ui.components.InfoNote
import com.voicereminder.vip.ui.components.Pill
import com.voicereminder.vip.ui.components.SectionCard
import com.voicereminder.vip.ui.theme.Gold
import com.voicereminder.vip.ui.theme.Teal
import com.voicereminder.vip.ui.vm.AppViewModel
import kotlinx.coroutines.delay

private val examples = listOf(
    "ذكرني بتناول الدواء الساعة 9 مساءً",
    "نبهني كل يوم الساعة 6:30 صباحًا لتجهيز حقيبة المدرسة",
    "من الأحد إلى الخميس الساعة 7 صباحًا ذكرني بالدوام",
    "Remind me to call mom tomorrow at 5 pm",
    "Ingatkan saya minum obat setiap hari jam 8 malam"
)

@Composable
fun AssistantScreen(
    app: AppViewModel,
    onBack: () -> Unit,
    onOpenEditor: (AlarmDraft) -> Unit
) {
    val settings by app.settings.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<ParsedReminder?>(null) }
    var parseFailed by remember { mutableStateOf(false) }
    var parsing by remember { mutableStateOf(false) }

    fun runParse() {
        if (query.isBlank()) {
            parsed = null
            parseFailed = false
            return
        }
        parsing = true
        val result = runCatching {
            ReminderParser.parse(
                rawText = query,
                defaultLanguage = settings.defaultLanguage
            )
        }.getOrNull()
        parsed = result
        parseFailed = result == null
        parsing = false
    }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            parsed = null
            parseFailed = false
            return@LaunchedEffect
        }
        delay(400)
        parsing = true
        val result = runCatching {
            ReminderParser.parse(
                rawText = query,
                defaultLanguage = settings.defaultLanguage
            )
        }.getOrNull()
        parsed = result
        parseFailed = result == null
        parsing = false
    }

    AppBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(title = "المساعد الذكي", onBack = onBack)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Gold.copy(alpha = 0.16f)
                            ) {
                                Icon(
                                    Icons.Outlined.SmartToy,
                                    contentDescription = null,
                                    tint = Gold,
                                    modifier = Modifier
                                        .padding(9.dp)
                                        .size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "اكتب تذكيرك كما تقوله",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "تحليل محلي بقواعد — لا إنترنت ولا ذكاء اصطناعي.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Pill(text = "محلي", color = Teal)
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = query,
                            onValueChange = {
                                query = it.take(500)
                                parseFailed = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(126.dp),
                            placeholder = {
                                Text("مثال: ذكرني بتناول الدواء الساعة 9 مساءً")
                            },
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { runParse() }),
                            supportingText = {
                                Text("${query.length}/500 • العربية أو الإنجليزية أو الإندونيسية")
                            }
                        )
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(examples) { example ->
                                Surface(
                                    modifier = Modifier.clickable { query = example },
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ) {
                                    Text(
                                        example,
                                        modifier = Modifier.padding(
                                            horizontal = 12.dp,
                                            vertical = 6.dp
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = ::runParse,
                                enabled = query.isNotBlank() && !parsing,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Gold,
                                    contentColor = Color(0xFF251A00)
                                )
                            ) {
                                Icon(
                                    Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("تحليل")
                            }
                            TextButton(
                                onClick = {
                                    query = ""
                                    parsed = null
                                    parseFailed = false
                                }
                            ) { Text("مسح") }
                        }
                    }
                }

                parsed?.let { result ->
                    item {
                        ResultCard(parsed = result)
                    }
                    item {
                        Button(
                            onClick = { onOpenEditor(app.draftFromParsed(result)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Gold,
                                contentColor = Color(0xFF251A00)
                            )
                        ) {
                            Text("فتح في المحرر للمراجعة", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Outlined.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    item {
                        InfoNote(
                            "الاقتراح قابل للخطأ: راجع الوقت والتاريخ والأيام قبل الحفظ. لا يُحفظ أي شيء تلقائيًا."
                        )
                    }
                }

                if (parseFailed) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "لم أستخرج وقتًا واضحًا. جرّب صياغة مثل «ذكرني ... الساعة 7 مساءً» أو أنشئ المنبه يدويًا.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                item {
                    InfoHint()
                }
            }
        }
    }
}

@Composable
private fun ResultCard(parsed: ParsedReminder) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "الاقتراح",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    parsed.title,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Pill(
                text = "${(parsed.confidence * 100).toInt()}% ثقة",
                color = if (parsed.confidence >= 0.7f) Teal else Gold
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Gold.copy(alpha = 0.14f)
            ) {
                Text(
                    TimeUtils.formatTime(parsed.hour, parsed.minute, use24 = false),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Gold,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    when (parsed.repeatType) {
                        RepeatType.ONCE -> TimeUtils.formatDate(parsed.epochDay)
                        RepeatType.DAILY -> "كل يوم"
                        RepeatType.WEEKLY -> Weekdays.describe(parsed.daysMask)
                        RepeatType.EVERY_INTERVAL -> "كل فترة"
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    languageName(parsed.languageTag),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "«${parsed.phrase}»",
            style = MaterialTheme.typography.bodyMedium
        )
        if (parsed.notes.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            parsed.notes.forEach { note ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("•", color = Gold)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        note,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoHint() {
    SectionCard {
        Text("كيف يفهم المساعد؟", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        listOf(
            "الأوقات: «7 صباحًا»، «19:30»، «7 pm»، «jam 8 malam».",
            "التكرار: «كل يوم»، «daily»، «كل اثنين»، «من الأحد إلى الخميس».",
            "التاريخ: «غدًا»، «بعد 10 دقائق»، «tomorrow»، «besok».",
            "اللغة تُكتشف من النص، ويمكنك تغييرها في المحرر."
        ).forEach { line ->
            Row(modifier = Modifier.padding(vertical = 3.dp)) {
                Text("•", color = Teal)
                Spacer(Modifier.width(6.dp))
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun languageName(tag: String): String = when {
    tag.startsWith("ar") -> "العربية"
    tag.startsWith("en") -> "الإنجليزية"
    tag.startsWith("id") -> "الإندونيسية"
    else -> tag
}
