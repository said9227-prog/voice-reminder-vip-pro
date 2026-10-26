package com.voicereminder.vip.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Locale
import kotlin.math.abs

object TimeUtils {
    private val arabic = Locale("ar")

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun now(): Long = System.currentTimeMillis()

    fun todayEpochDay(zone: ZoneId = zone()): Long = LocalDate.now(zone).toEpochDay()

    fun formatClock(millis: Long, use24: Boolean, zone: ZoneId = zone()): String {
        val time = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
        return formatTime(time.hour, time.minute, use24)
    }

    fun formatSeconds(millis: Long, use24: Boolean, zone: ZoneId = zone()): String {
        val time = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
        val base = formatTime(time.hour, time.minute, use24)
        return "$base:${time.second.toString().padStart(2, '0')}"
    }

    fun formatTime(hour: Int, minute: Int, use24: Boolean): String {
        return if (use24) {
            "%02d:%02d".format(Locale.US, hour, minute)
        } else {
            val suffix = if (hour < 12) "ص" else "م"
            val h = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            "$h:%02d $suffix".format(Locale.US, minute)
        }
    }

    fun weekdayNameAr(epochDay: Long): String {
        val iso = LocalDate.ofEpochDay(epochDay).dayOfWeek.value
        return com.voicereminder.vip.core.model.Weekdays.nameAr(iso)
    }

    fun formatDate(epochDay: Long): String {
        val date = LocalDate.ofEpochDay(epochDay)
        val formatter = DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy", arabic)
            .withDecimalStyle(DecimalStyle.STANDARD)
        return date.format(formatter)
    }

    fun formatShortDate(epochDay: Long): String {
        val date = LocalDate.ofEpochDay(epochDay)
        val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", arabic)
            .withDecimalStyle(DecimalStyle.STANDARD)
        return date.format(formatter)
    }

    fun formatDateTime(millis: Long, use24: Boolean, zone: ZoneId = zone()): String {
        val zdt = Instant.ofEpochMilli(millis).atZone(zone)
        return "${formatDate(zdt.toLocalDate().toEpochDay())} • ${formatTime(zdt.hour, zdt.minute, use24)}"
    }

    fun relative(millis: Long, fromMillis: Long = now()): String {
        val delta = millis - fromMillis
        if (delta <= 0) return "الآن"
        val totalMinutes = delta / 60_000
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val minutes = totalMinutes % 60
        val parts = mutableListOf<String>()
        if (days > 0) parts += if (days == 1L) "يوم" else "$days أيام"
        if (hours > 0) parts += if (hours == 1L) "ساعة" else "$hours ساعات"
        if (minutes > 0 && days == 0L) parts += if (minutes == 1L) "دقيقة" else "$minutes دقيقة"
        if (parts.isEmpty()) parts += "أقل من دقيقة"
        return "بعد " + parts.joinToString(" و")
    }

    fun countdown(millis: Long, fromMillis: Long = now()): String {
        val delta = (millis - fromMillis).coerceAtLeast(0)
        val seconds = delta / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(Locale.US, hours, minutes, secs)
        } else {
            "%02d:%02d".format(Locale.US, minutes, secs)
        }
    }

    fun nextOccurrenceOfTime(hour: Int, minute: Int, fromMillis: Long, zone: ZoneId = zone()): Long {
        val from = Instant.ofEpochMilli(fromMillis).atZone(zone)
        var candidate = from.toLocalDate().atTime(LocalTime.of(hour, minute)).atZone(zone)
        if (candidate.toInstant().toEpochMilli() <= fromMillis + 500) {
            candidate = candidate.plusDays(1)
        }
        return candidate.toInstant().toEpochMilli()
    }

    fun toMillis(epochDay: Long, hour: Int, minute: Int, zone: ZoneId = zone()): Long =
        LocalDateTime.of(LocalDate.ofEpochDay(epochDay), LocalTime.of(hour, minute))
            .atZone(zone).toInstant().toEpochMilli()

    fun fromMillis(millis: Long, zone: ZoneId = zone()): ZonedDateTime =
        Instant.ofEpochMilli(millis).atZone(zone)

    fun sameDay(a: Long, b: Long, zone: ZoneId = zone()): Boolean =
        Instant.ofEpochMilli(a).atZone(zone).toLocalDate() ==
            Instant.ofEpochMilli(b).atZone(zone).toLocalDate()

    fun minutesBetween(a: Long, b: Long): Long = abs(b - a) / 60_000

    fun greeting(hour: Int): String = when (hour) {
        in 4..11 -> "صباح الخير"
        in 12..16 -> "طاب نهارك"
        in 17..21 -> "مساء الخير"
        else -> "ليلة هادئة"
    }

    fun isValidTime(hour: Int, minute: Int): Boolean = hour in 0..23 && minute in 0..59

    fun formatInterval(minutes: Int): String {
        return when {
            minutes < 60 -> "$minutes دقيقة"
            minutes % 60 == 0 -> "${minutes / 60} ساعة"
            else -> "${minutes / 60} ساعة و${minutes % 60} دقيقة"
        }
    }
}
