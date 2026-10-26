package com.voicereminder.vip.core.nlp

import com.voicereminder.vip.core.model.RepeatType
import com.voicereminder.vip.core.model.Weekdays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderParserTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val mondayTen = LocalDateTime.of(2024, 1, 1, 10, 0)
        .atZone(zone).toInstant().toEpochMilli()

    @Test
    fun arabicEveningTime_parses21() {
        val result = ReminderParser.parse(
            "ذكرني بتناول الدواء الساعة 9 مساءً",
            mondayTen,
            zone
        )!!
        assertNotNull(result)
        assertEquals("ar", result.languageTag)
        assertEquals(21, result.hour)
        assertEquals(0, result.minute)
        assertEquals(RepeatType.ONCE, result.repeatType)
        assertEquals(LocalDate.of(2024, 1, 1).toEpochDay(), result.epochDay)
        assertTrue(result.title.isNotBlank())
        assertTrue(result.phrase.contains("الدواء"))
    }

    @Test
    fun arabicIndicDigits_areNormalised() {
        val result = ReminderParser.parse(
            "نبهني الساعة ٩ مساءً",
            mondayTen,
            zone
        )!!
        assertNotNull(result)
        assertEquals(21, result.hour)
    }

    @Test
    fun dailyRepeatWithMinutes_rollsToTomorrowWhenPassed() {
        val result = ReminderParser.parse(
            "نبهني كل يوم الساعة 6:30 صباحًا",
            mondayTen,
            zone
        )!!
        assertNotNull(result)
        assertEquals(RepeatType.DAILY, result.repeatType)
        assertEquals(6, result.hour)
        assertEquals(30, result.minute)
        assertEquals(LocalDate.of(2024, 1, 2).toEpochDay(), result.epochDay)
    }

    @Test
    fun arabicWeekdayRange_buildsMaskSundayToThursday() {
        val result = ReminderParser.parse(
            "من الأحد إلى الخميس الساعة 7 صباحًا ذكرني بالدوام",
            mondayTen,
            zone
        )!!
        assertNotNull(result)
        assertEquals(RepeatType.WEEKLY, result.repeatType)
        assertEquals(setOf(7, 1, 2, 3, 4), Weekdays.setOf(result.daysMask))
        assertEquals(7, result.hour)
    }

    @Test
    fun workdaysExpression_isWeeklyMondayToFriday() {
        val result = ReminderParser.parse(
            "أيام العمل الساعة 9 صباحًا",
            mondayTen,
            zone
        )!!
        assertNotNull(result)
        assertEquals(RepeatType.WEEKLY, result.repeatType)
        assertEquals(setOf(1, 2, 3, 4, 5), Weekdays.setOf(result.daysMask))
    }

    @Test
    fun relativeDuration_usesNowPlusMinutes() {
        val result = ReminderParser.parse(
            "ذكرني بعد 10 دقائق بشرب الماء",
            mondayTen,
            zone
        )!!
        assertNotNull(result)
        assertEquals(10, result.hour)
        assertEquals(10, result.minute)
        assertEquals(LocalDate.of(2024, 1, 1).toEpochDay(), result.epochDay)
        assertEquals(RepeatType.ONCE, result.repeatType)
        assertTrue(result.confidence >= 0.9f)
    }

    @Test
    fun englishTomorrowAt5pm_parses() {
        val result = ReminderParser.parse(
            "Remind me to call mom tomorrow at 5 pm",
            mondayTen,
            zone
        )!!
        assertNotNull(result)
        assertEquals("en", result.languageTag)
        assertEquals(17, result.hour)
        assertEquals(LocalDate.of(2024, 1, 2).toEpochDay(), result.epochDay)
        assertTrue(result.phrase.contains("call mom"))
    }

    @Test
    fun englishRelativeMinutes_parses() {
        val result = ReminderParser.parse(
            "Remind me to stretch in 20 minutes",
            mondayTen,
            zone
        )!!
        assertNotNull(result)
        assertEquals(10, result.hour)
        assertEquals(20, result.minute)
    }

    @Test
    fun indonesianDailyAt8pm_parses() {
        val result = ReminderParser.parse(
            "Ingatkan saya minum obat setiap hari jam 8 malam",
            mondayTen,
            zone
        )!!
        assertNotNull(result)
        assertEquals("id", result.languageTag)
        assertEquals(RepeatType.DAILY, result.repeatType)
        assertEquals(20, result.hour)
    }

    @Test
    fun missingTime_isMarkedLowConfidence() {
        val result = ReminderParser.parse(
            "ذكرني بشراء الحليب",
            mondayTen,
            zone
        )!!
        assertNotNull(result)
        assertTrue(result.confidence <= 0.5f)
        assertTrue(result.notes.any { it.contains("لم نجد وقتًا") })
    }

    @Test
    fun blankInput_returnsNull() {
        assertEquals(null, ReminderParser.parse("   ", mondayTen, zone))
    }

    @Test
    fun singleWeekdayWithoutEvery_isOnceNotWeekly() {
        val result = ReminderParser.parse(
            "ذكرني يوم الثلاثاء الساعة 8 صباحًا بالاجتماع",
            mondayTen,
            zone
        )!!
        assertNotNull(result)
        assertEquals(RepeatType.ONCE, result.repeatType)
        assertEquals(LocalDate.of(2024, 1, 2).toEpochDay(), result.epochDay)
        assertFalse(result.title.isBlank())
    }
}
