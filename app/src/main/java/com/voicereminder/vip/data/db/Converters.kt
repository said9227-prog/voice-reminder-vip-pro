package com.voicereminder.vip.data.db

import androidx.room.TypeConverter
import com.voicereminder.vip.core.model.HistoryStatus
import com.voicereminder.vip.core.model.RepeatType

class Converters {
    @TypeConverter
    fun repeatToString(value: RepeatType): String = value.name

    @TypeConverter
    fun stringToRepeat(value: String): RepeatType =
        runCatching { RepeatType.valueOf(value) }.getOrDefault(RepeatType.ONCE)

    @TypeConverter
    fun statusToString(value: HistoryStatus): String = value.name

    @TypeConverter
    fun stringToStatus(value: String): HistoryStatus =
        runCatching { HistoryStatus.valueOf(value) }.getOrDefault(HistoryStatus.FIRED)
}
