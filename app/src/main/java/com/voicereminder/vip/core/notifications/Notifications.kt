package com.voicereminder.vip.core.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.voicereminder.vip.R
import com.voicereminder.vip.data.db.AlarmEntity
import com.voicereminder.vip.feature.alarm.AlarmActivity
import com.voicereminder.vip.feature.alarm.AlarmService

object Notifications {

    const val CHANNEL_ALARM = "vr_alarm_channel"
    const val CHANNEL_SERVICE = "vr_service_channel"
    const val ACTIVE_NOTIFICATION_ID = 5000

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val alarmChannel = NotificationChannel(
            CHANNEL_ALARM,
            context.getString(R.string.channel_alarm_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_alarm_desc)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
            setShowBadge(true)
        }

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            context.getString(R.string.channel_service_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_service_desc)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }

        manager.createNotificationChannel(alarmChannel)
        manager.createNotificationChannel(serviceChannel)
    }

    fun serviceNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("جارٍ تشغيل التذكير الصوتي")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()

    fun activeNotification(
        context: Context,
        alarm: AlarmEntity,
        snoozeCount: Int,
        progress: String? = null,
        statusText: String? = null
    ): Notification {
        val contentIntent = ringPendingIntent(context, alarm.id)
        val stopIntent = servicePendingIntent(context, AlarmService.ACTION_STOP, 4_002)
        val snoozeIntent = servicePendingIntent(context, AlarmService.ACTION_SNOOZE, 4_003)
        val cancelGroupIntent = servicePendingIntent(context, AlarmService.ACTION_CANCEL_SEQUENCE, 4_004)

        val text = buildString {
            if (!progress.isNullOrBlank()) append(progress).append(" • ")
            append(alarm.phrase)
            if (snoozeCount > 0) append(" • تأجيل رقم $snoozeCount")
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(alarm.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setContentIntent(contentIntent)
            .setFullScreenIntent(contentIntent, true)
            .setSound(null)
            .setVibrate(null)
            .setOnlyAlertOnce(false)
            .addAction(0, "إيقاف", stopIntent)
            .addAction(0, "تأجيل ${alarm.snoozeMinutes} د", snoozeIntent)

        if (alarm.groupId != null && alarm.stepCount > 1) {
            builder.addAction(0, "إيقاف الرسائل المتسلسلة", cancelGroupIntent)
        }

        if (!statusText.isNullOrBlank()) {
            builder.setSubText(statusText)
        }

        return builder.build()
    }

    fun snoozedNotification(context: Context, alarm: AlarmEntity, snoozeCount: Int): Notification {
        val contentIntent = ringPendingIntent(context, alarm.id)
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("تم تأجيل ${alarm.title}")
            .setContentText("سيُعاد التنبيه بعد ${alarm.snoozeMinutes} دقائق • التأجيل رقم $snoozeCount")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setSilent(true)
            .build()
    }

    private fun ringPendingIntent(context: Context, alarmId: Long): PendingIntent {
        val intent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmActivity.EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getActivity(
            context,
            4_001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun servicePendingIntent(context: Context, action: String, code: Int): PendingIntent {
        val intent = Intent(context, AlarmService::class.java).setAction(action)
        return PendingIntent.getService(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
