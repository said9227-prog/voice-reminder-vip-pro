package com.voicereminder.vip.core.schedule

import com.voicereminder.vip.core.model.RepeatType
import com.voicereminder.vip.core.model.Weekdays
import com.voicereminder.vip.data.db.AlarmEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class NextTriggerCalculatorTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val mondayTen = millis(2024, 1, 1, 10, 0)

    @Test
    fun onceInTheFuture_returnsExactTimestamp() {
        val alarm = alarm(
            hour = 18,
            minute = 30,
            repeat = RepeatType.ONCE,
            dateEpochDay = LocalDate.of(2024, 1, 5).toEpochDay()
        )
        val result = NextTriggerCalculator.calculate(alarm, mondayTen, zone)
        assertEquals(millis(2024, 1, 5, 18, 30), result)
    }

    @Test
    fun onceInThePast_returnsNull() {
        val alarm = alarm(
            hour = 9,
            minute = 0,
            repeat = RepeatType.ONCE,
            dateEpochDay = LocalDate.of(2024, 1, 1).toEpochDay()
        )
        assertNull(NextTriggerCalculator.calculate(alarm, mondayTen, zone))
    }

    @Test
    fun onceWithoutDate_usesTodayWhenStillAhead() {
        val alarm = alarm(hour = 12, minute = 0, repeat = RepeatType.ONCE)
        assertEquals(
            millis(2024, 1, 1, 12, 0),
            NextTriggerCalculator.calculate(alarm, mondayTen, zone)
        )
    }

    @Test
    fun dailyBeforeTime_isLaterToday() {
        val alarm = alarm(hour = 18, minute = 0, repeat = RepeatType.DAILY)
        assertEquals(
            millis(2024, 1, 1, 18, 0),
            NextTriggerCalculator.calculate(alarm, mondayTen, zone)
        )
    }

    @Test
    fun dailyAfterTime_isTomorrow() {
        val alarm = alarm(hour = 7, minute = 0, repeat = RepeatType.DAILY)
        assertEquals(
            millis(2024, 1, 2, 7, 0),
            NextTriggerCalculator.calculate(alarm, mondayTen, zone)
        )
    }

    @Test
    fun weeklyOnSameWeekdayAfterTime_isNextWeek() {
        val alarm = alarm(
            hour = 9,
            minute = 0,
            repeat = RepeatType.WEEKLY,
            daysMask = Weekdays.maskOf(setOf(1))
        )
        assertEquals(
            millis(2024, 1, 8, 9, 0),
            NextTriggerCalculator.calculate(alarm, mondayTen, zone)
        )
    }

    @Test
    fun weeklyOnAnotherDay_findsNearestEnabledDay() {
        val alarm = alarm(
            hour = 9,
            minute = 0,
            repeat = RepeatType.WEEKLY,
            daysMask = Weekdays.maskOf(setOf(7))
        )
        assertEquals(
            millis(2024, 1, 7, 9, 0),
            NextTriggerCalculator.calculate(alarm, mondayTen, zone)
        )
    }

    @Test
    fun weeklyWithoutDays_returnsNull() {
        val alarm = alarm(
            hour = 9,
            minute = 0,
            repeat = RepeatType.WEEKLY,
            daysMask = 0
        )
        assertNull(NextTriggerCalculator.calculate(alarm, mondayTen, zone))
    }

    @Test
    fun intervalUsesLastTriggeredAndSkipsPastOccurrences() {
        val alarm = alarm(
            hour = 0,
            minute = 0,
            repeat = RepeatType.EVERY_INTERVAL,
            intervalMinutes = 30,
            createdAt = millis(2024, 1, 1, 8, 0),
            lastTriggeredAt = millis(2024, 1, 1, 9, 0)
        )
        assertEquals(
            millis(2024, 1, 1, 10, 30),
            NextTriggerCalculator.calculate(alarm, mondayTen, zone)
        )
    }

    @Test
    fun intervalHonoursStoredNextTrigger() {
        val stored = millis(2024, 1, 1, 10, 45)
        val alarm = alarm(
            hour = 0,
            minute = 0,
            repeat = RepeatType.EVERY_INTERVAL,
            intervalMinutes = 15,
            createdAt = millis(2024, 1, 1, 8, 0),
            nextTriggerAt = stored
        )
        assertEquals(stored, NextTriggerCalculator.calculate(alarm, mondayTen, zone))
    }

    @Test
    fun disabledAlarm_returnsNull() {
        val alarm = alarm(hour = 12, minute = 0, repeat = RepeatType.DAILY, enabled = false)
        assertNull(NextTriggerCalculator.calculate(alarm, mondayTen, zone))
    }

    @Test
    fun expiredOnce_isDetected() {
        val past = alarm(
            hour = 9,
            minute = 0,
            repeat = RepeatType.ONCE,
            dateEpochDay = LocalDate.of(2024, 1, 1).toEpochDay()
        )
        val future = past.copy(dateEpochDay = LocalDate.of(2024, 1, 2).toEpochDay())
        assertTrue(NextTriggerCalculator.isExpiredOnce(past, mondayTen, zone))
        assertTrue(!NextTriggerCalculator.isExpiredOnce(future, mondayTen, zone))
    }

    @Test
    fun weeklyMaskRoundTrips() {
        val mask = Weekdays.maskOf(setOf(1, 3, 5, 7))
        assertEquals(setOf(1, 3, 5, 7), Weekdays.setOf(mask))
    }

    private fun alarm(
        hour: Int,
        minute: Int,
        repeat: RepeatType,
        daysMask: Int = 0,
        intervalMinutes: Int = 0,
        enabled: Boolean = true,
        dateEpochDay: Long? = null,
        createdAt: Long = mondayTen,
        lastTriggeredAt: Long? = null,
        nextTriggerAt: Long? = null
    ) = AlarmEntity(
        title = "اختبار",
        phrase = "نص الاختبار",
        hour = hour,
        minute = minute,
        repeatType = repeat,
        daysMask = daysMask,
        intervalMinutes = intervalMinutes,
        enabled = enabled,
        dateEpochDay = dateEpochDay,
        createdAt = createdAt,
        lastTriggeredAt = lastTriggeredAt,
        nextTriggerAt = nextTriggerAt
    )

    private fun millis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Long = LocalDateTime.of(
        LocalDate.of(year, month, day),
        LocalTime.of(hour, minute)
    ).atZone(zone).toInstant().toEpochMilli()

    @Suppress("unused")
    private fun instant(millis: Long): Instant = Instant.ofEpochMilli(millis)
}
