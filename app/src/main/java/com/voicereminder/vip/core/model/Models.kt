package com.voicereminder.vip.core.model

import java.time.DayOfWeek

enum class RepeatType {
    ONCE,
    DAILY,
    WEEKLY,
    EVERY_INTERVAL
}

enum class HistoryStatus {
    FIRED,
    DISMISSED,
    SNOOZED,
    MISSED,
    CANCELLED
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class SchedulingResult {
    EXACT,
    INEXACT,
    FAILED
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val volume: Float = 0.9f,
    val repeatCount: Int = 2,
    val repeatGapSeconds: Int = 6,
    val vibrate: Boolean = true,
    val preTone: Boolean = true,
    val snoozeMinutes: Int = 5,
    val maxSnoozes: Int = 3,
    val use24Hour: Boolean = false,
    val defaultLanguage: String = "ar",
    val notificationsAsked: Boolean = false,
    val readyCheckDismissed: Boolean = false
)

data class VoiceLanguage(
    val tag: String,
    val label: String,
    val offline: Boolean,
    val available: Boolean
)

data class ParsedReminder(
    val activity: String,
    val title: String,
    val phrase: String,
    val hour: Int,
    val minute: Int,
    val epochDay: Long,
    val repeatType: RepeatType,
    val daysMask: Int,
    val languageTag: String,
    val confidence: Float,
    val notes: List<String>
)

object Weekdays {
    /** ISO order: Monday = 1 ... Sunday = 7. */
    val displayOrder: List<Int> = listOf(7, 1, 2, 3, 4, 5, 6)

    fun maskOf(days: Set<Int>): Int = days.fold(0) { acc, day -> acc or (1 shl (day - 1)) }

    fun setOf(mask: Int): Set<Int> = (1..7).filter { mask and (1 shl (it - 1)) != 0 }.toSet()

    fun isEnabled(mask: Int, isoDay: Int): Boolean = mask and (1 shl (isoDay - 1)) != 0

    fun nameAr(isoDay: Int): String = when (isoDay) {
        1 -> "الاثنين"
        2 -> "الثلاثاء"
        3 -> "الأربعاء"
        4 -> "الخميس"
        5 -> "الجمعة"
        6 -> "السبت"
        7 -> "الأحد"
        else -> ""
    }

    fun shortNameAr(isoDay: Int): String = when (isoDay) {
        1 -> "ن"
        2 -> "ث"
        3 -> "ر"
        4 -> "خ"
        5 -> "ج"
        6 -> "س"
        7 -> "ح"
        else -> ""
    }

    fun of(date: java.time.LocalDate): Int = date.dayOfWeek.value

    fun dayOfWeek(isoDay: Int): DayOfWeek = DayOfWeek.of(isoDay)

    fun describe(mask: Int): String {
        val days = setOf(mask)
        if (days.isEmpty()) return "لا أيام محددة"
        if (days.size == 7) return "كل الأيام"
        if (days == setOf(7, 1, 2, 3, 4, 5)) return "الأحد إلى الخميس"
        if (days == setOf(1, 2, 3, 4, 5)) return "أيام العمل"
        return displayOrder.filter { it in days }.joinToString("، ") { shortNameAr(it) }
    }
}

data class AlarmDraft(
    val id: Long = 0,
    val title: String = "",
    val phrase: String = "",
    val recordingId: Long? = null,
    val hour: Int = 6,
    val minute: Int = 0,
    val epochDay: Long = 0,
    val repeatType: RepeatType = RepeatType.ONCE,
    val daysMask: Int = 0,
    val intervalMinutes: Int = 60,
    val enabled: Boolean = true,
    val languageTag: String = "ar",
    val speechRate: Float = 1f,
    val pitch: Float = 1f,
    val volume: Float = 0.9f,
    val preTone: Boolean = true,
    val repeatCount: Int = 2,
    val repeatGapSeconds: Int = 6,
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 5,
    val maxSnoozes: Int = 3,
    val categoryId: Long? = null,
    val groupId: Long? = null,
    val groupTitle: String? = null,
    val stepIndex: Int = 0,
    val stepCount: Int = 0
)
