package com.voicereminder.vip.feature.alarm

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicereminder.vip.AppContainer
import com.voicereminder.vip.core.model.AppSettings
import com.voicereminder.vip.core.util.TimeUtils
import com.voicereminder.vip.data.db.AlarmEntity
import com.voicereminder.vip.ui.theme.Gold
import com.voicereminder.vip.ui.theme.Ink
import com.voicereminder.vip.ui.theme.MutedLight
import com.voicereminder.vip.ui.theme.Teal
import com.voicereminder.vip.ui.theme.TextLight
import com.voicereminder.vip.ui.theme.VoiceReminderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class AlarmActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ALARM_ID = "extra_ring_alarm_id"
    }

    private lateinit var container: AppContainer
    private var alarmId: Long = -1L

    private val finishedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AlarmService.ACTION_RING_FINISHED) {
                finishAndRemoveTask()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = AppContainer.from(this)
        alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        runCatching {
            (getSystemService(KeyguardManager::class.java))
                ?.requestDismissKeyguard(this, null)
        }
        volumeControlStream = AudioManager.STREAM_ALARM
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false
        ContextCompat.registerReceiver(
            this,
            finishedReceiver,
            IntentFilter(AlarmService.ACTION_RING_FINISHED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            VoiceReminderTheme(darkTheme = true) {
                RingScreen(
                    alarmId = alarmId,
                    container = container,
                    onStop = { sendAction(AlarmService.ACTION_STOP) },
                    onSnooze = { sendAction(AlarmService.ACTION_SNOOZE) },
                    onCancelSequence = { sendAction(AlarmService.ACTION_CANCEL_SEQUENCE) },
                    onClose = { finishAndRemoveTask() }
                )
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(finishedReceiver) }
        super.onDestroy()
    }

    private fun sendAction(action: String) {
        runCatching {
            startService(
                Intent(this, AlarmService::class.java).setAction(action)
            )
        }
        finishAndRemoveTask()
    }
}

@Composable
private fun RingScreen(
    alarmId: Long,
    container: AppContainer,
    onStop: () -> Unit,
    onSnooze: () -> Unit,
    onCancelSequence: () -> Unit,
    onClose: () -> Unit
) {
    var loaded by remember { mutableStateOf(false) }
    val alarm by produceState<AlarmEntity?>(initialValue = null, alarmId) {
        value = withContext(Dispatchers.IO) {
            runCatching { container.alarmRepository.get(alarmId) }.getOrNull()
        }
        loaded = true
    }
    val settings by container.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val groupSteps by produceState<List<AlarmEntity>>(initialValue = emptyList(), alarm?.groupId) {
        val groupId = alarm?.groupId
        value = if (groupId == null) emptyList() else withContext(Dispatchers.IO) {
            runCatching { container.alarmRepository.group(groupId) }.getOrDefault(emptyList())
        }
    }

    BackHandler(enabled = true) {
        // Ringing screen stays until the user explicitly stops or snoozes.
    }

    val gradient = Brush.verticalGradient(
        colors = listOf(Ink, Color(0xFF121A2B), Color(0xFF0B0F1A))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        if (!loaded) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Gold)
                Spacer(Modifier.height(16.dp))
                Text("جارٍ تجهيز التذكير…", color = TextLight)
            }
            return@Box
        }

        val currentAlarm = alarm
        if (currentAlarm == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.Alarm,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(54.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "انتهى هذا التنبيه أو لم يعد متاحًا.",
                    color = TextLight,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onClose) { Text("إغلاق") }
            }
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = if (currentAlarm.groupId != null && currentAlarm.stepCount > 1) {
                            "رسالة متسلسلة • ${currentAlarm.stepIndex + 1} من ${currentAlarm.stepCount}"
                        } else {
                            "منبه صوتي"
                        },
                        color = Gold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "الوقت الآن",
                    color = MutedLight,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Box(contentAlignment = Alignment.Center) {
                val phase = rememberPulsePhase()
                Canvas(modifier = Modifier.size(310.dp)) {
                    val base = size.minDimension / 2f
                    repeat(3) { index ->
                        val p = (phase + index / 3f) % 1f
                        drawCircle(
                            color = Gold.copy(alpha = (1f - p) * 0.15f),
                            radius = base * (0.30f + 0.64f * p),
                            center = Offset(size.width / 2f, size.height / 2f)
                        )
                    }
                    drawCircle(
                        color = Gold.copy(alpha = 0.10f),
                        radius = base * 0.42f,
                        center = Offset(size.width / 2f, size.height / 2f)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = TimeUtils.formatClock(now, settings.use24Hour),
                        color = TextLight,
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = TimeUtils.formatSeconds(now, settings.use24Hour).takeLast(2),
                        color = Gold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "المنبه: ${TimeUtils.formatTime(currentAlarm.hour, currentAlarm.minute, settings.use24Hour)}",
                        color = MutedLight,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = Color.White.copy(alpha = 0.06f)
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text(
                        text = currentAlarm.title,
                        color = Gold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = currentAlarm.phrase,
                        color = TextLight,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Start
                    )
                    if (currentAlarm.groupId != null && currentAlarm.stepCount > 1) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "تقدم التسلسل: ${groupSteps.count { it.lastTriggeredAt != null }} / ${currentAlarm.stepCount}",
                            color = MutedLight,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = ((currentAlarm.stepIndex + 1f) / currentAlarm.stepCount)
                                .coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth(),
                            color = Teal,
                            trackColor = Color.White.copy(alpha = 0.08f)
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (currentAlarm.maxSnoozes > 0) {
                    OutlinedButton(
                        onClick = onSnooze,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(22.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp, Teal.copy(alpha = 0.7f)
                        )
                    ) {
                        Icon(
                            Icons.Filled.Snooze,
                            contentDescription = null,
                            tint = Teal
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            "تأجيل ${currentAlarm.snoozeMinutes} دقائق",
                            color = Teal,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Button(
                    onClick = onStop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        contentColor = Color(0xFF251A00)
                    )
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "إيقاف المنبه",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (currentAlarm.groupId != null && currentAlarm.stepCount > 1) {
                    TextButton(onClick = onCancelSequence) {
                        Text("إيقاف بقية الرسائل المتسلسلة", color = MutedLight)
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "سيظل التنبيه حتى تختار إيقاف أو تأجيل.",
                        color = MutedLight,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberPulsePhase(): Float {
    val transition = rememberInfiniteTransition(label = "pulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing)
        ),
        label = "phase"
    )
    return phase
}
