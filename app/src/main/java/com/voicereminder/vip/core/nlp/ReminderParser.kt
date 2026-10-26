package com.voicereminder.vip.core.nlp

import com.voicereminder.vip.core.model.ParsedReminder
import com.voicereminder.vip.core.model.RepeatType
import com.voicereminder.vip.core.model.Weekdays
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * Offline, rule-based Arabic / English / Indonesian reminder parser.
 * No network, no AI model. It extracts a best-effort suggestion and always
 * returns notes explaining every assumption so the user can confirm or edit.
 */
object ReminderParser {

    private enum class Period { AM, PM, MIDDAY, NIGHT }

    private data class RelativeMatch(val minutes: Long, val range: IntRange)

    private data class ClockMatch(
        val hour: Int,
        val minute: Int,
        val period: Period?,
        val ranges: List<IntRange>
    )

    private val periodToken =
        "(صباحا|صباح|الصباح|مساء|المساء|ظهرا|ظهر|الظهر|عصرا|عصر|العصر|ليلا|ليل|الليل|ص|م|am|pm|a\\.m\\.|p\\.m\\.|morning|afternoon|evening|night|pagi|siang|sore|malam)"

    private val periodRegex = Regex(
        "(?<![\\p{L}])$periodToken(?![\\p{L}])",
        RegexOption.IGNORE_CASE
    )

    private val arabicWeekdays = listOf(
        "احد" to 7,
        "اثنين" to 1,
        "ثلاثاء" to 2,
        "اربعاء" to 3,
        "خميس" to 4,
        "جمعه" to 5,
        "سبت" to 6
    )

    private val englishWeekdays = listOf(
        "monday" to 1,
        "tuesday" to 2,
        "wednesday" to 3,
        "thursday" to 4,
        "friday" to 5,
        "saturday" to 6,
        "sunday" to 7
    )

    private val indonesianWeekdays = listOf(
        "senin" to 1,
        "selasa" to 2,
        "rabu" to 3,
        "kamis" to 4,
        "jumat" to 5,
        "sabtu" to 6,
        "minggu" to 7
    )

    private val hourWords = linkedMapOf(
        "الحاديه عشره" to 11,
        "الحادية عشرة" to 11,
        "الثانيه عشره" to 12,
        "الثانية عشرة" to 12,
        "الواحده" to 1,
        "الواحدة" to 1,
        "واحده" to 1,
        "واحدة" to 1,
        "الثانيه" to 2,
        "الثانية" to 2,
        "الثالثه" to 3,
        "الثالثة" to 3,
        "الرابعه" to 4,
        "الرابعة" to 4,
        "الخامسه" to 5,
        "الخامسة" to 5,
        "السادسه" to 6,
        "السادسة" to 6,
        "السابعه" to 7,
        "السابعة" to 7,
        "الثامنه" to 8,
        "الثامنة" to 8,
        "التاسعه" to 9,
        "التاسعة" to 9,
        "العاشره" to 10,
        "العاشرة" to 10
    )

    private val englishHourWords = linkedMapOf(
        "twelve" to 12,
        "eleven" to 11,
        "ten" to 10,
        "nine" to 9,
        "eight" to 8,
        "seven" to 7,
        "six" to 6,
        "five" to 5,
        "four" to 4,
        "three" to 3,
        "two" to 2,
        "one" to 1
    )

    private val indonesianHourWords = linkedMapOf(
        "dua belas" to 12,
        "sebelas" to 11,
        "sepuluh" to 10,
        "sembilan" to 9,
        "delapan" to 8,
        "tujuh" to 7,
        "enam" to 6,
        "lima" to 5,
        "empat" to 4,
        "tiga" to 3,
        "dua" to 2,
        "satu" to 1
    )

    fun parse(
        rawText: String,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        defaultLanguage: String = "ar"
    ): ParsedReminder? {
        val display = prepareDisplay(rawText)
        if (display.isBlank()) return null
        val search = prepareSearch(display)
        val nowZ = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val language = detectLanguage(search, defaultLanguage)
        val notes = mutableListOf<String>()
        val spans = mutableListOf<IntRange>()

        var repeat = RepeatType.ONCE
        var daysMask = 0
        var hasWeekday = false
        var rangeDetected = false
        var explicitDate: LocalDate? = null
        var explicitDateWord = false
        var targetWeekday: Int? = null
        var relative: RelativeMatch? = null
        var clock: ClockMatch? = null
        var relativeBase: LocalDateTime? = null

        // 1) Trigger prefix.
        findFirst(
            search,
            Regex(
                "^(?:لو\\s+سمحت\\s+|من\\s+فضلك\\s+)?(?:ذكرني|ذكرنى|ذكّرني|فكرني|نبهني|نبهنى)\\s*(?:ب|في|على|عن)?\\s*",
                RegexOption.IGNORE_CASE
            )
        )?.let { spans += it.range }

        findFirst(
            search,
            Regex(
                "^(?:please\\s+)?remind\\s+me\\s*(?:to|about|that)?\\s*",
                RegexOption.IGNORE_CASE
            )
        )?.let { spans += it.range }

        findFirst(
            search,
            Regex(
                "^(?:tolong\\s+)?ingatkan\\s+(?:saya|aku)\\s*(?:untuk|supaya|tentang|bahwa)?\\s*",
                RegexOption.IGNORE_CASE
            )
        )?.let { spans += it.range }

        // 2) Relative durations such as "بعد 10 دقائق".
        relative = parseRelative(search)
        relative?.let {
            spans += it.range
            val base = nowZ.plusMinutes(it.minutes)
            relativeBase = base.toLocalDateTime()
            notes += "موعد نسبي: بعد ${it.minutes} دقيقة من الآن."
        }

        // 3) Daily repeat.
        findFirst(
            search,
            Regex("كل\\s+يوم|يوميا|يومياً|daily|every\\s+day|setiap\\s+hari", RegexOption.IGNORE_CASE)
        )?.let {
            repeat = RepeatType.DAILY
            spans += it.range
            notes += "التكرار اليومي مفعّل."
        }

        // 4) Weekly ranges: "من الأحد إلى الخميس".
        parseWeeklyRange(search)?.let { range ->
            daysMask = range.first
            rangeDetected = true
            repeat = RepeatType.WEEKLY
            spans += range.second
            notes += "تكرار أسبوعي: ${Weekdays.describe(daysMask)}."
        }

        // 5) Individual weekdays.
        arabicWeekdays.forEach { (word, iso) ->
            Regex("(?:ال)?$word").findAll(search).forEach { match ->
                hasWeekday = true
                daysMask = daysMask or (1 shl (iso - 1))
                spans += match.range
            }
        }
        englishWeekdays.forEach { (word, iso) ->
            Regex("\\b$word\\b", RegexOption.IGNORE_CASE).findAll(search).forEach { match ->
                hasWeekday = true
                daysMask = daysMask or (1 shl (iso - 1))
                if (targetWeekday == null) targetWeekday = iso
                spans += match.range
            }
        }
        indonesianWeekdays.forEach { (word, iso) ->
            Regex("\\b$word\\b", RegexOption.IGNORE_CASE).findAll(search).forEach { match ->
                hasWeekday = true
                daysMask = daysMask or (1 shl (iso - 1))
                if (targetWeekday == null) targetWeekday = iso
                spans += match.range
            }
        }

        val everyWord = Regex("\\b(?:كل|every|setiap)\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(search)
        if (hasWeekday) {
            if (rangeDetected || everyWord) {
                repeat = RepeatType.WEEKLY
                Regex("\\b(?:كل|every|setiap)\\b", RegexOption.IGNORE_CASE)
                    .findAll(search)
                    .forEach { spans += it.range }
                if (!rangeDetected) {
                    notes += "تكرار أسبوعي: ${Weekdays.describe(daysMask)}."
                }
            } else {
                targetWeekday = targetWeekday
                    ?: arabicWeekdays.firstOrNull { (_, iso) ->
                        Weekdays.isEnabled(daysMask, iso)
                    }?.second
                notes += "سيُجدول لأقرب يوم مطابق: ${Weekdays.nameAr(targetWeekday ?: 1)}."
            }
        }

        // Weekdays ranges in English/Indonesian.
        parseForeignRange(search)?.let { range ->
            daysMask = daysMask or range.first
            rangeDetected = true
            repeat = RepeatType.WEEKLY
            spans += range.second
            notes += "تكرار أسبوعي: ${Weekdays.describe(daysMask)}."
        }

        Regex("(?:أيام\\s+العمل|ايام\\s+العمل|weekdays|hari\\s+kerja)", RegexOption.IGNORE_CASE)
            .find(search)?.let {
                daysMask = daysMask or Weekdays.maskOf(setOf(1, 2, 3, 4, 5))
                repeat = RepeatType.WEEKLY
                rangeDetected = true
                spans += it.range
                notes += "تكرار في أيام العمل (الاثنين إلى الجمعة)."
            }

        Regex("(?:عطلة\\s+نهاية\\s+الأسبوع|نهاية\\s+الأسبوع|weekends?|akhir\\s+pekan)", RegexOption.IGNORE_CASE)
            .find(search)?.let {
                daysMask = daysMask or Weekdays.maskOf(setOf(6, 7))
                repeat = RepeatType.WEEKLY
                rangeDetected = true
                spans += it.range
                notes += "تكرار في عطلة نهاية الأسبوع (السبت والأحد)."
            }

        // 6) Date words.
        Regex("بعد\\s+غد|بعد\\s+بكره|day\\s+after\\s+tomorrow|besok\\s+lusa", RegexOption.IGNORE_CASE)
            .find(search)?.let {
                explicitDate = nowZ.toLocalDate().plusDays(2)
                explicitDateWord = true
                spans += it.range
            }
        if (explicitDate == null) {
            Regex("غدا|بكره|tomorrow|besok|\\btoday\\b|اليوم|hari\\s+ini", RegexOption.IGNORE_CASE)
                .find(search)?.let { match ->
                    val token = match.value.lowercase(Locale.ROOT)
                    explicitDate = when {
                        token.contains("today") || token.contains("اليوم") || token.contains("hari") ->
                            nowZ.toLocalDate()
                        else -> nowZ.toLocalDate().plusDays(1)
                    }
                    explicitDateWord = true
                    spans += match.range
                }
        }

        // 7) Clock time.
        clock = parseClock(search)
        clock?.let { c ->
            spans += c.ranges
            notes += "الوقت: ${"%02d:%02d".format(Locale.US, c.hour, c.minute)}."
        }

        // 8) Resolve the time.
        var hour: Int
        var minute: Int
        var date: LocalDate
        var timeAssumed = false

        val relativeTime = relativeBase
        if (relativeTime != null) {
            hour = relativeTime.hour
            minute = relativeTime.minute
            date = relativeTime.toLocalDate()
            repeat = RepeatType.ONCE
        } else {
            if (clock != null) {
                hour = applyPeriod(clock.hour, clock.period)
                minute = clock.minute
            } else {
                val nextHour = nowZ.plusHours(1).withMinute(0).withSecond(0).withNano(0)
                hour = nextHour.hour
                minute = 0
                timeAssumed = true
                notes += "لم نجد وقتًا واضحًا؛ تم اقتراح أقرب ساعة (${"%02d:%02d".format(Locale.US, hour, minute)})."
            }

            date = when {
                explicitDate != null -> explicitDate!!
                repeat == RepeatType.WEEKLY && daysMask != 0 -> nextMaskDate(
                    daysMask, hour, minute, nowZ, zone
                )
                targetWeekday != null -> nextWeekdayDate(
                    targetWeekday!!, hour, minute, nowZ, zone
                )
                else -> nowZ.toLocalDate()
            }

            val candidateMillis = LocalDateTime.of(date, LocalTime.of(hour, minute))
                .atZone(zone).toInstant().toEpochMilli()
            if (repeat == RepeatType.ONCE && candidateMillis <= nowMillis) {
                if (explicitDateWord) {
                    notes += "الوقت المحدد مضى؛ تم اقتراح اليوم التالي."
                }
                date = date.plusDays(1)
            } else if (repeat == RepeatType.DAILY && candidateMillis <= nowMillis) {
                date = date.plusDays(1)
            }

            if (timeAssumed) {
                date = nowZ.toLocalDate()
            }
        }

        // 9) Activity and phrase.
        val activity = deriveActivity(display, spans)
        val title = deriveTitle(activity, language)
        val phrase = derivePhrase(activity, language)

        if (activity.isBlank()) {
            notes += "لم نحدد نص التذكير؛ يمكنك كتابته قبل الحفظ."
        }
        if (defaultLanguage.isNotBlank() && language != defaultLanguage) {
            notes += "لغة النطق المقترحة: ${languageLabel(language)}."
        }

        var confidence = 0.9f
        if (timeAssumed) confidence = 0.45f
        else if (clock == null) confidence = 0.5f
        else if (explicitDate == null && repeat == RepeatType.ONCE) confidence = 0.8f
        if (relativeBase != null) confidence = 0.95f
        if (activity.isBlank()) confidence = minOf(confidence, 0.5f)

        return ParsedReminder(
            activity = activity,
            title = title,
            phrase = phrase,
            hour = hour.coerceIn(0, 23),
            minute = minute.coerceIn(0, 59),
            epochDay = date.toEpochDay(),
            repeatType = repeat,
            daysMask = if (repeat == RepeatType.WEEKLY) daysMask else 0,
            languageTag = language,
            confidence = confidence,
            notes = notes
        )
    }

    // ---------------------------------------------------------------------
    // Normalisation

    private fun prepareDisplay(raw: String): String {
        val noDiacritics = raw
            .replace(Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]"), "")
            .replace('\u00A0', ' ')
        val digits = buildString {
            noDiacritics.forEach { ch ->
                append(
                    when (ch) {
                        in '\u0660'..'\u0669' -> ('0' + (ch - '\u0660'))
                        in '\u06F0'..'\u06F9' -> ('0' + (ch - '\u06F0'))
                        else -> ch
                    }
                )
            }
        }
        return digits.replace(Regex("\\s+"), " ").trim()
    }

    private fun prepareSearch(display: String): String {
        return display.lowercase(Locale.ROOT)
            .replace(Regex("[أإآٱ]"), "ا")
            .replace('ى', 'ي')
            .replace('ة', 'ه')
    }

    private fun detectLanguage(search: String, fallback: String): String {
        if (Regex("[\\u0600-\\u06FF]").containsMatchIn(search)) return "ar"
        val indonesianHints = listOf(
            "ingatkan", "besok", "jam", "pagi", "siang", "sore", "malam",
            "sekolah", "untuk", "saya", "hari", "menit", "minum"
        )
        val hintCount = indonesianHints.count { search.contains(it) }
        return when {
            hintCount >= 2 -> "id"
            search.any { it.isLetter() } -> if (fallback == "id" && hintCount >= 1) "id" else "en"
            else -> fallback
        }
    }

    private fun languageLabel(tag: String): String = when (tag) {
        "ar" -> "العربية"
        "en" -> "الإنجليزية"
        "id" -> "الإندونيسية"
        else -> tag
    }

    // ---------------------------------------------------------------------
    // Relative time

    private fun parseRelative(search: String): RelativeMatch? {
        val arabic = Regex(
            "(?:بعد|خلال)\\s+(نصف\\s+ساعه|نصف\\s+ساعة|ربع\\s+ساعه|ربع\\s+ساعة|ساعتين|ساعتان|ساعه|ساعة|(\\d+)\\s*(دقيقه|دقيقة|دقائق|دقيقتين|ساعه|ساعة|ساعات|ساعتين|يوم|ايام|أيام))"
        )
        arabic.find(search)?.let { match ->
            val phrase = match.value
            val digits = match.groupValues.getOrNull(2)?.toLongOrNull()
            val minutes = when {
                phrase.contains("نصف") -> 30L
                phrase.contains("ربع") -> 15L
                digits != null -> {
                    val unit = match.groupValues.getOrNull(3) ?: ""
                    when {
                        unit.contains("دقيق") -> digits
                        unit.contains("ساع") -> digits * 60
                        unit.contains("يوم") || unit.contains("ايام") -> digits * 24 * 60
                        else -> digits
                    }
                }
                phrase.contains("ساعتين") || phrase.contains("ساعتان") -> 120L
                else -> 60L
            }
            return RelativeMatch(minutes, match.range)
        }

        val english = Regex(
            "\\b(?:in|after)\\s+(half\\s+an?\\s+hour|a\\s+day|an\\s+hour|a\\s+minute|(\\d+)\\s*(minutes?|mins?|hours?|hrs?|days?))\\b",
            RegexOption.IGNORE_CASE
        )
        english.find(search)?.let { match ->
            val phrase = match.value.lowercase(Locale.ROOT)
            val digits = match.groupValues.getOrNull(2)?.toLongOrNull() ?: 1L
            val minutes = when {
                phrase.contains("half") -> 30L
                phrase.contains("day") -> digits * 24 * 60
                phrase.contains("hour") || phrase.contains("hr") -> digits * 60
                else -> digits
            }
            return RelativeMatch(minutes, match.range)
        }

        val indonesian = Regex(
            "\\b(?:dalam|setelah)\\s+(setengah\\s+jam|(\\d+)\\s*(menit|jam|hari))\\b",
            RegexOption.IGNORE_CASE
        )
        indonesian.find(search)?.let { match ->
            val phrase = match.value.lowercase(Locale.ROOT)
            val digits = match.groupValues.getOrNull(2)?.toLongOrNull() ?: 1L
            val minutes = when {
                phrase.contains("setengah") -> 30L
                phrase.contains("hari") -> digits * 24 * 60
                phrase.contains("jam") -> digits * 60
                else -> digits
            }
            return RelativeMatch(minutes, match.range)
        }
        return null
    }

    // ---------------------------------------------------------------------
    // Weekdays

    private fun parseWeeklyRange(search: String): Pair<Int, IntRange>? {
        val names = "احد|اثنين|ثلاثاء|اربعاء|خميس|جمعه|سبت"
        val regex = Regex(
            "(?:من\\s+)?(?:ال)?($names)\\s+(?:الى|الي|إلى|إلي|حتى|ـ|-)\\s+(?:ال)?($names)"
        )
        val match = regex.find(search) ?: return null
        val start = arabicWeekdays.firstOrNull { match.groupValues[1].endsWith(it.first) }?.second
        val end = arabicWeekdays.firstOrNull { match.groupValues[2].endsWith(it.first) }?.second
        if (start == null || end == null) return null
        return maskRange(start, end) to match.range
    }

    private fun parseForeignRange(search: String): Pair<Int, IntRange>? {
        val english = Regex(
            "\\b(?:from\\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\s+(?:to|through|until|till)\\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b",
            RegexOption.IGNORE_CASE
        )
        english.find(search)?.let { match ->
            val start = englishWeekdays.firstOrNull { it.first == match.groupValues[1].lowercase() }?.second
            val end = englishWeekdays.firstOrNull { it.first == match.groupValues[2].lowercase() }?.second
            if (start != null && end != null) return maskRange(start, end) to match.range
        }
        val indonesian = Regex(
            "\\b(?:dari\\s+)?(senin|selasa|rabu|kamis|jumat|sabtu|minggu)\\s+(?:sampai|hingga|sampai\\s+dengan)\\s+(senin|selasa|rabu|kamis|jumat|sabtu|minggu)\\b",
            RegexOption.IGNORE_CASE
        )
        indonesian.find(search)?.let { match ->
            val start = indonesianWeekdays.firstOrNull {
                it.first == match.groupValues[1].lowercase()
            }?.second
            val end = indonesianWeekdays.firstOrNull {
                it.first == match.groupValues[2].lowercase()
            }?.second
            if (start != null && end != null) return maskRange(start, end) to match.range
        }
        return null
    }

    private fun maskRange(startIso: Int, endIso: Int): Int {
        val days = mutableSetOf<Int>()
        var current = startIso
        var guard = 0
        while (guard < 7) {
            days += current
            if (current == endIso) break
            current = if (current == 7) 1 else current + 1
            guard++
        }
        return Weekdays.maskOf(days)
    }

    private fun nextMaskDate(
        mask: Int,
        hour: Int,
        minute: Int,
        nowZ: java.time.ZonedDateTime,
        zone: ZoneId
    ): LocalDate {
        for (offset in 0..7) {
            val date = nowZ.toLocalDate().plusDays(offset.toLong())
            if (!Weekdays.isEnabled(mask, date.dayOfWeek.value)) continue
            val candidate = LocalDateTime.of(date, LocalTime.of(hour, minute))
                .atZone(zone).toInstant().toEpochMilli()
            if (candidate > nowZ.toInstant().toEpochMilli()) return date
        }
        return nextWeekdayDate(Weekdays.setOf(mask).firstOrNull() ?: 1, hour, minute, nowZ, zone)
    }

    private fun nextWeekdayDate(
        isoDay: Int,
        hour: Int,
        minute: Int,
        nowZ: java.time.ZonedDateTime,
        zone: ZoneId
    ): LocalDate {
        for (offset in 0..7) {
            val date = nowZ.toLocalDate().plusDays(offset.toLong())
            if (date.dayOfWeek.value != isoDay) continue
            val candidate = LocalDateTime.of(date, LocalTime.of(hour, minute))
                .atZone(zone).toInstant().toEpochMilli()
            if (candidate > nowZ.toInstant().toEpochMilli()) return date
        }
        return nowZ.toLocalDate().plusDays(1)
    }

    // ---------------------------------------------------------------------
    // Clock

    private fun parseClock(search: String): ClockMatch? {
        // 1) Numeric HH:mm (with an optional nearby period marker).
        Regex("(?<![\\d:])(\\d{1,2})\\s*:\\s*(\\d{2})(?![\\d:])").findAll(search).forEach { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return@forEach
            val minute = match.groupValues[2].toIntOrNull() ?: return@forEach
            if (hour > 23 || minute > 59) return@forEach
            val ranges = mutableListOf(match.range)
            prefixRange(search, match.range.first)?.let { ranges += it }
            val periodInfo = periodNear(search, match.range)
            periodInfo?.let {
                ranges += it.second
                return ClockMatch(hour, minute, it.first, ranges)
            }
            return ClockMatch(hour, minute, null, ranges)
        }

        // 2) Numeric with الساعة / jam / at.
        val numeric = Regex(
            "(?:الساعه|الساعة|jam|pukul|at)\\s+(?:الساعة\\s+)?(\\d{1,2})(?:\\s*:\\s*(\\d{2}))?(?:\\s*($periodToken))?",
            RegexOption.IGNORE_CASE
        ).findAll(search)
        for (match in numeric) {
            val hour = match.groupValues[1].toIntOrNull() ?: continue
            val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            if (hour > 23 || minute > 59) continue
            val ranges = mutableListOf(match.range)
            val period = periodFromToken(match.groupValues.getOrNull(3) ?: "")
            periodNear(search, match.range)?.let {
                if (period == null) ranges += it.second
                return ClockMatch(hour, minute, period ?: it.first, ranges)
            }
            return ClockMatch(hour, minute, period, ranges)
        }

        // 3) Arabic hour words, with or without "الساعة".
        val arabicWordsPattern = hourWords.keys.joinToString("|") { Regex.escape(it) }
        val arabicWord = Regex(
            "(?:(?:الساعه|الساعة)\\s+)?($arabicWordsPattern)(?:\\s*($periodToken))?",
            RegexOption.IGNORE_CASE
        ).findAll(search)
        for (match in arabicWord) {
            if (match.value.isBlank()) continue
            val hasPrefix = Regex("^(?:الساعه|الساعة|jam|pukul|at)", RegexOption.IGNORE_CASE)
                .containsMatchIn(match.value)
            val periodTokenValue = match.groupValues.getOrNull(2) ?: ""
            val period = periodFromToken(periodTokenValue)
            if (!hasPrefix && period == null) continue
            val word = match.groupValues[1]
            val hour = hourWords.entries.firstOrNull { word.endsWith(it.key) }?.value ?: continue
            return ClockMatch(hour % 24, 0, period, listOf(match.range))
        }

        // 4) English hour words.
        val englishWordsPattern = englishHourWords.keys.joinToString("|") { Regex.escape(it) }
        val englishWord = Regex(
            "\\bat\\s+($englishWordsPattern)\\b(?:\\s*($periodToken))?",
            RegexOption.IGNORE_CASE
        ).find(search)
        if (englishWord != null) {
            val hour = englishHourWords[englishWord.groupValues[1].lowercase()] ?: 0
            val period = periodFromToken(englishWord.groupValues.getOrNull(2) ?: "")
            return ClockMatch(hour, 0, period, listOf(englishWord.range))
        }

        // 5) Indonesian hour words.
        val indonesianWordsPattern = indonesianHourWords.keys.joinToString("|") { Regex.escape(it) }
        val indonesianWord = Regex(
            "\\b(?:jam|pukul)\\s+($indonesianWordsPattern)\\b(?:\\s*($periodToken))?",
            RegexOption.IGNORE_CASE
        ).find(search)
        if (indonesianWord != null) {
            val hour = indonesianHourWords[indonesianWord.groupValues[1].lowercase()] ?: 0
            val period = periodFromToken(indonesianWord.groupValues.getOrNull(2) ?: "")
            return ClockMatch(hour, 0, period, listOf(indonesianWord.range))
        }

        // 6) Bare numeric hour followed by a period marker (e.g. "6 صباحا").
        val bareNumeric = Regex(
            "(?<![\\d:])(\\d{1,2})\\s*($periodToken)",
            RegexOption.IGNORE_CASE
        ).find(search)
        if (bareNumeric != null) {
            val hour = bareNumeric.groupValues[1].toIntOrNull() ?: return null
            if (hour !in 0..23) return null
            val period = periodFromToken(bareNumeric.groupValues[2])
            return ClockMatch(hour, 0, period, listOf(bareNumeric.range))
        }

        return null
    }

    private fun prefixAt(search: String, index: Int): Boolean {
        if (index <= 0) return false
        val before = search.substring(0, index)
        return Regex("(?:الساعه|الساعة|jam|pukul|at)\\s*$", RegexOption.IGNORE_CASE)
            .containsMatchIn(before)
    }

    private fun prefixRange(search: String, index: Int): IntRange? {
        if (index <= 0) return null
        val before = search.substring(0, index)
        val match = Regex("(?:الساعه|الساعة|jam|pukul|at)\\s*$", RegexOption.IGNORE_CASE)
            .find(before) ?: return null
        return match.range
    }

    private fun periodNear(search: String, range: IntRange): Pair<Period, IntRange>? {
        val windowStart = (range.first - 10).coerceAtLeast(0)
        val windowEnd = (range.last + 18).coerceAtMost(search.length)
        if (windowStart >= windowEnd) return null
        val window = search.substring(windowStart, windowEnd)
        val match = periodRegex.find(window) ?: return null
        val period = periodFromToken(match.value) ?: return null
        val globalRange = (windowStart + match.range.first)..(windowStart + match.range.last)
        return period to globalRange
    }

    private fun periodFromToken(token: String): Period? {
        val value = token.lowercase(Locale.ROOT).trim()
        return when {
            value in setOf(
                "صباحا", "صباح", "الصباح", "ص", "am", "a.m.", "morning", "pagi"
            ) -> Period.AM
            value in setOf(
                "مساء", "المساء", "م", "pm", "p.m.", "afternoon", "evening",
                "عصرا", "عصر", "العصر", "ظهرا", "ظهر", "الظهر", "sore"
            ) -> Period.PM
            value in setOf("ليلا", "ليل", "الليل", "night", "malam") -> Period.NIGHT
            value == "siang" -> Period.MIDDAY
            else -> null
        }
    }

    private fun applyPeriod(hour: Int, period: Period?): Int {
        return when (period) {
            null -> hour
            Period.AM -> if (hour == 12) 0 else hour
            Period.PM -> if (hour in 1..11) hour + 12 else hour
            Period.MIDDAY -> hour
            Period.NIGHT -> when {
                hour == 12 -> 0
                hour in 1..11 -> hour + 12
                else -> hour
            }
        }
    }

    // ---------------------------------------------------------------------
    // Text helpers

    private fun findFirst(text: String, regex: Regex): MatchResult? = regex.find(text)

    private fun deriveActivity(display: String, spans: List<IntRange>): String {
        val merged = mutableListOf<IntRange>()
        spans.filter { it.first in display.indices && it.last in display.indices }
            .sortedBy { it.first }
            .forEach { range ->
                if (merged.isEmpty()) {
                    merged += range
                } else {
                    val last = merged.last()
                    if (range.first <= last.last + 1) {
                        merged[merged.size - 1] = last.first..maxOf(last.last, range.last)
                    } else {
                        merged += range
                    }
                }
            }
        val builder = StringBuilder(display)
        merged.asReversed().forEach { range ->
            val end = (range.last + 1).coerceAtMost(builder.length)
            if (range.first < end) builder.delete(range.first, end)
        }
        var result = builder.toString()
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('،', ',', '.', '!', '?', '؟', ';', ':', '-', 'ـ')

        result = result
            .replace(Regex("^(?:أن|ان|إن|في|عند|على|عن)\\s+"), "")
            .replace(Regex("\\s+(?:في|عند|على|عن|من|إلى|الى)$"), "")
            .trim()
            .trim('،', ',', '.', '!', '?', '؟', ';', ':', '-')

        return result
    }

    private fun deriveTitle(activity: String, language: String): String {
        val base = activity.ifBlank {
            when (language) {
                "en" -> "Voice reminder"
                "id" -> "Pengingat suara"
                else -> "تذكير صوتي"
            }
        }
        val trimmed = base.replace(Regex("\\s+"), " ").trim()
        return if (trimmed.length <= 70) trimmed else trimmed.take(67).trimEnd() + "…"
    }

    private fun derivePhrase(activity: String, language: String): String {
        if (activity.isBlank()) {
            return when (language) {
                "en" -> "This is your reminder."
                "id" -> "Ini pengingat Anda."
                else -> "حان وقت التذكير."
            }
        }
        return when (language) {
            "en" -> {
                val lower = activity.lowercase(Locale.ROOT)
                val verbStart = listOf(
                    "go", "buy", "call", "take", "drink", "prepare", "check",
                    "review", "send", "finish", "start", "wake", "study", "pray"
                ).any { lower.startsWith("$it ") }
                if (verbStart) "It is time to $activity." else "Reminder: $activity."
            }
            "id" -> "Saatnya $activity."
            else -> when {
                activity.startsWith("ال") -> "حان وقت $activity."
                activity.startsWith("أن ") -> "حان وقت ${activity.removePrefix("أن ")}."
                else -> "تذكير: $activity."
            }
        }
    }
}
