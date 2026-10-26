package com.voicereminder.vip.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.voicereminder.vip.core.model.AppSettings
import com.voicereminder.vip.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "voice_reminder_settings"
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val speechRate = floatPreferencesKey("speech_rate")
        val pitch = floatPreferencesKey("pitch")
        val volume = floatPreferencesKey("volume")
        val repeatCount = intPreferencesKey("repeat_count")
        val repeatGap = intPreferencesKey("repeat_gap")
        val vibrate = booleanPreferencesKey("vibrate")
        val preTone = booleanPreferencesKey("pre_tone")
        val snoozeMinutes = intPreferencesKey("snooze_minutes")
        val maxSnoozes = intPreferencesKey("max_snoozes")
        val use24Hour = booleanPreferencesKey("use_24_hour")
        val defaultLanguage = stringPreferencesKey("default_language")
        val notificationsAsked = booleanPreferencesKey("notifications_asked")
        val readyCheckDismissed = booleanPreferencesKey("ready_check_dismissed")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { prefs ->
            val defaults = AppSettings()
            AppSettings(
                themeMode = runCatching {
                    ThemeMode.valueOf(prefs[Keys.themeMode] ?: defaults.themeMode.name)
                }.getOrDefault(defaults.themeMode),
                speechRate = prefs[Keys.speechRate] ?: defaults.speechRate,
                pitch = prefs[Keys.pitch] ?: defaults.pitch,
                volume = prefs[Keys.volume] ?: defaults.volume,
                repeatCount = prefs[Keys.repeatCount] ?: defaults.repeatCount,
                repeatGapSeconds = prefs[Keys.repeatGap] ?: defaults.repeatGapSeconds,
                vibrate = prefs[Keys.vibrate] ?: defaults.vibrate,
                preTone = prefs[Keys.preTone] ?: defaults.preTone,
                snoozeMinutes = prefs[Keys.snoozeMinutes] ?: defaults.snoozeMinutes,
                maxSnoozes = prefs[Keys.maxSnoozes] ?: defaults.maxSnoozes,
                use24Hour = prefs[Keys.use24Hour] ?: defaults.use24Hour,
                defaultLanguage = prefs[Keys.defaultLanguage] ?: defaults.defaultLanguage,
                notificationsAsked = prefs[Keys.notificationsAsked] ?: defaults.notificationsAsked,
                readyCheckDismissed = prefs[Keys.readyCheckDismissed] ?: defaults.readyCheckDismissed
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.themeMode] = mode.name }
    suspend fun setSpeechRate(value: Float) = edit { it[Keys.speechRate] = value.coerceIn(0.5f, 2f) }
    suspend fun setPitch(value: Float) = edit { it[Keys.pitch] = value.coerceIn(0.5f, 2f) }
    suspend fun setVolume(value: Float) = edit { it[Keys.volume] = value.coerceIn(0.1f, 1f) }
    suspend fun setRepeatCount(value: Int) = edit { it[Keys.repeatCount] = value.coerceIn(1, 10) }
    suspend fun setRepeatGap(value: Int) = edit { it[Keys.repeatGap] = value.coerceIn(2, 60) }
    suspend fun setVibrate(value: Boolean) = edit { it[Keys.vibrate] = value }
    suspend fun setPreTone(value: Boolean) = edit { it[Keys.preTone] = value }
    suspend fun setSnoozeMinutes(value: Int) = edit { it[Keys.snoozeMinutes] = value.coerceIn(1, 60) }
    suspend fun setMaxSnoozes(value: Int) = edit { it[Keys.maxSnoozes] = value.coerceIn(0, 10) }
    suspend fun setUse24Hour(value: Boolean) = edit { it[Keys.use24Hour] = value }
    suspend fun setDefaultLanguage(value: String) = edit { it[Keys.defaultLanguage] = value }
    suspend fun setNotificationsAsked(value: Boolean) = edit { it[Keys.notificationsAsked] = value }
    suspend fun setReadyCheckDismissed(value: Boolean) = edit { it[Keys.readyCheckDismissed] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit { block(it) }
    }
}
