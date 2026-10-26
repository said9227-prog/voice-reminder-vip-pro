package com.voicereminder.vip.core.schedule

import com.voicereminder.vip.core.model.RepeatType
import com.voicereminder.vip.core.model.Weekdays
import com.voicereminder.vip.data.db.AlarmEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure, testable calculation of the next trigger for an alarm.
 * All times are computed in the supplied time zone so that timezone
 * changes and daylight-saving transitions remain correct.
 */
object NextTriggerCalculator {

    private const val GUARD_MILLIS = 500L

    fun calculate(
        alarm: AlarmEntity,
        fromMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long? {
        if (!alarm.enabled) return null
        val time = LocalTime.of(alarm.hour, alarm.minute)
        val from = Instant.ofEpochMilli(fromMillis).atZone(zone)

        return when (alarm.repeatType) {
            RepeatType.ONCE -> calculateOnce(alarm, time, fromMillis, zone)
            RepeatType.DAILY -> calculateDaily(time, fromMillis, zone)
            RepeatType.WEEKLY -> calculateWeekly(alarm, time, fromMillis, zone)
            RepeatType.EVERY_INTERVAL -> calculateInterval(alarm, fromMillis)
        }.also { /* keep `from` referenced for clarity of time-zone semantics */ }
    }

    private fun calculateOnce(
        alarm: AlarmEntity,
        time: LocalTime,
        fromMillis: Long,
        zone: ZoneId
    ): Long? {
        val date = alarm.dateEpochDay?.let { LocalDate.ofEpochDay(it) }
            ?: Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
        val millis = ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli()
        return if (millis > fromMillis + GUARD_MILLIS) millis else null
    }

    private fun calculateDaily(
        time: LocalTime,
        fromMillis: Long,
        zone: ZoneId
    ): Long? {
        val from = Instant.ofEpochMilli(fromMillis).atZone(zone)
        for (offset in 0..2) {
            val candidate = ZonedDateTime.of(
                from.toLocalDate().plusDays(offset.toLong()), time, zone
            ).toInstant().toEpochMilli()
            if (candidate > fromMillis + GUARD_MILLIS) return candidate
        }
        return null
    }

    private fun calculateWeekly(
        alarm: AlarmEntity,
        time: LocalTime,
        fromMillis: Long,
        zone: ZoneId
    ): Long? {
        if (alarm.daysMask == 0) return null
        val from = Instant.ofEpochMilli(fromMillis).atZone(zone)
        for (offset in 0..7) {
            val date = from.toLocalDate().plusDays(offset.toLong())
            if (!Weekdays.isEnabled(alarm.daysMask, date.dayOfWeek.value)) continue
            val candidate = ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli()
            if (candidate > fromMillis + GUARD_MILLIS) return candidate
        }
        return null
    }

    private fun calculateInterval(alarm: AlarmEntity, fromMillis: Long): Long? {
        val interval = (alarm.intervalMinutes.coerceAtLeast(1)) * 60_000L
        val storedNext = alarm.nextTriggerAt
        if (storedNext != null && storedNext > fromMillis + GUARD_MILLIS) return storedNext
        val base = alarm.lastTriggeredAt ?: alarm.createdAt
        if (base <= 0L) return fromMillis + interval
        var candidate = base + interval
        var guard = 0
        while (candidate <= fromMillis + GUARD_MILLIS && guard < 100_000) {
            candidate += interval
            guard++
        }
        return candidate
    }

    fun isExpiredOnce(alarm: AlarmEntity, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (alarm.repeatType != RepeatType.ONCE) return false
        val date = alarm.dateEpochDay ?: return false
        val millis = java.time.LocalDateTime.of(
            LocalDate.ofEpochDay(date),
            LocalTime.of(alarm.hour, alarm.minute)
        ).atZone(zone).toInstant().toEpochMilli()
        return millis <= nowMillis
    }
}
