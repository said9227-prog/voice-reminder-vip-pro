package com.voicereminder.vip.core.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.voicereminder.vip.MainActivity
import com.voicereminder.vip.core.model.SchedulingResult
import com.voicereminder.vip.data.db.AlarmEntity
import com.voicereminder.vip.feature.alarm.AlarmReceiver
import java.time.ZoneId

class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun canScheduleExact(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return runCatching { alarmManager?.canScheduleExactAlarms() == true }.getOrDefault(false)
    }

    fun schedule(alarm: AlarmEntity, from: Long = System.currentTimeMillis()): SchedulingResult {
        val next = NextTriggerCalculator.calculate(alarm, from, ZoneId.systemDefault())
            ?: return SchedulingResult.FAILED
        return scheduleAt(alarm.id, next, alarm.title, 0)
    }

    fun scheduleAt(
        alarmId: Long,
        triggerAtMillis: Long,
        label: String,
        snoozeCount: Int = 0
    ): SchedulingResult {
        val manager = alarmManager ?: return SchedulingResult.FAILED
        if (triggerAtMillis <= System.currentTimeMillis() - 1_000L) return SchedulingResult.FAILED
        val pending = firePendingIntent(alarmId, snoozeCount)
        val showPending = showIntent(alarmId)

        if (canScheduleExact()) {
            try {
                manager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAtMillis, showPending),
                    pending
                )
                return SchedulingResult.EXACT
            } catch (_: SecurityException) {
                // Fall through to an inexact alarm.
            }
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            } else {
                manager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            }
            SchedulingResult.INEXACT
        } catch (_: SecurityException) {
            SchedulingResult.FAILED
        }
    }

    fun cancel(alarmId: Long) {
        val manager = alarmManager ?: return
        runCatching { manager.cancel(firePendingIntent(alarmId, 0)) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = PendingIntent.getBroadcast(
                context,
                requestCode(alarmId),
                Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (existing != null) {
                manager.cancel(existing)
                existing.cancel()
            }
        }
    }

    fun cancelAll(alarms: List<AlarmEntity>) {
        alarms.forEach { cancel(it.id) }
    }

    fun exactAlarmSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }

    private fun firePendingIntent(alarmId: Long, snoozeCount: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_SNOOZE_COUNT, snoozeCount)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(alarmId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showIntent(alarmId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("from_alarm", alarmId)
        }
        return PendingIntent.getActivity(
            context,
            90_001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCode(alarmId: Long): Int =
        (alarmId and 0x7FFFFFFF).toInt().takeIf { it != 0 } ?: 1
}
