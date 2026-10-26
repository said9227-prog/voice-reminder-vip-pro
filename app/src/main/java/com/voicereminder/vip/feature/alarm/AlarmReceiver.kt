package com.voicereminder.vip.feature.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.voicereminder.vip.AppContainer
import com.voicereminder.vip.core.notifications.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_FIRE = "com.voicereminder.vip.action.FIRE_ALARM"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_SNOOZE_COUNT = "extra_snooze_count"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        if (alarmId <= 0L) return
        val snoozeCount = intent.getIntExtra(EXTRA_SNOOZE_COUNT, 0)

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_FIRE
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_SNOOZE_COUNT, snoozeCount)
        }

        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (_: Exception) {
            fallbackNotification(context, alarmId, snoozeCount)
        }
    }

    private fun fallbackNotification(context: Context, alarmId: Long, snoozeCount: Int) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = AppContainer.from(context)
                val alarm = container.alarmRepository.get(alarmId) ?: return@launch
                if (!alarm.enabled) return@launch
                Notifications.ensureChannels(context)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    NotificationManagerCompat.from(context).notify(
                        Notifications.ACTIVE_NOTIFICATION_ID,
                        Notifications.activeNotification(context, alarm, snoozeCount)
                    )
                }
                withContext(Dispatchers.Main) {
                    runCatching {
                        context.startActivity(
                            Intent(context, AlarmActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                                putExtra(AlarmActivity.EXTRA_ALARM_ID, alarmId)
                            }
                        )
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
