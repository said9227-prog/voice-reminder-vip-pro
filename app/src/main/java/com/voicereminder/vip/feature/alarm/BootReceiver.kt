package com.voicereminder.vip.feature.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.voicereminder.vip.VoiceReminderApp
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    private val actions = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in actions) return
        val pending = goAsync()
        val application = context.applicationContext as? VoiceReminderApp
        if (application == null) {
            pending.finish()
            return
        }
        application.container.appScope.launch {
            try {
                application.container.rescheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
