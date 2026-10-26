package com.voicereminder.vip

import android.content.Context
import com.voicereminder.vip.core.audio.RecordingManager
import com.voicereminder.vip.core.notifications.Notifications
import com.voicereminder.vip.core.schedule.AlarmRescheduler
import com.voicereminder.vip.core.schedule.AlarmScheduler
import com.voicereminder.vip.core.tts.TtsManager
import com.voicereminder.vip.data.AlarmRepository
import com.voicereminder.vip.data.CategoryRepository
import com.voicereminder.vip.data.DataSeeder
import com.voicereminder.vip.data.HistoryRepository
import com.voicereminder.vip.data.RecordingRepository
import com.voicereminder.vip.data.SettingsRepository
import com.voicereminder.vip.data.TemplateRepository
import com.voicereminder.vip.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext
    val database: AppDatabase = AppDatabase.get(appContext)

    val alarmRepository = AlarmRepository(database.alarmDao())
    val recordingRepository = RecordingRepository(appContext, database.recordingDao())
    val categoryRepository = CategoryRepository(database.categoryDao())
    val templateRepository = TemplateRepository(database.templateDao())
    val historyRepository = HistoryRepository(database.historyDao())

    val settingsRepository = SettingsRepository(appContext)
    val scheduler = AlarmScheduler(appContext)
    val rescheduler = AlarmRescheduler(alarmRepository, scheduler)
    val tts = TtsManager(appContext)
    val recorder = RecordingManager(appContext)

    private val seeder = DataSeeder(database.templateDao(), database.categoryDao())

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun bootstrap() {
        Notifications.ensureChannels(appContext)
        appScope.launch {
            runCatching { seeder.seedIfNeeded() }
            runCatching { rescheduler.rescheduleAll() }
        }
    }

    companion object {
        fun from(context: Context): AppContainer =
            (context.applicationContext as VoiceReminderApp).container
    }
}
