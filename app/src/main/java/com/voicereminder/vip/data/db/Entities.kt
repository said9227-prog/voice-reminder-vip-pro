package com.voicereminder.vip.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Embedded
import androidx.room.Relation
import com.voicereminder.vip.core.model.HistoryStatus
import com.voicereminder.vip.core.model.RepeatType

@Entity(
    tableName = "alarms",
    indices = [
        Index("groupId"),
        Index("categoryId"),
        Index("recordingId"),
        Index("hour", "minute")
    ]
)
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val phrase: String,
    val recordingId: Long? = null,
    val hour: Int,
    val minute: Int,
    val dateEpochDay: Long? = null,
    val repeatType: RepeatType = RepeatType.ONCE,
    val daysMask: Int = 0,
    val intervalMinutes: Int = 0,
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
    val stepCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastTriggeredAt: Long? = null,
    val nextTriggerAt: Long? = null
)

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val filePath: String,
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String,
    val iconKey: String,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "templates",
    indices = [Index(value = ["key"], unique = true)]
)
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val title: String,
    val description: String,
    val iconKey: String,
    val colorHex: String,
    val categoryName: String,
    val languageTag: String = "ar"
)

@Entity(tableName = "template_items", indices = [Index("templateId")])
data class TemplateItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val offsetMinutes: Int,
    val phrase: String,
    val sortOrder: Int = 0
)

data class TemplateWithItems(
    @Embedded val template: TemplateEntity,
    @Relation(parentColumn = "id", entityColumn = "templateId")
    val items: List<TemplateItemEntity>
)

@Entity(
    tableName = "history",
    indices = [Index("alarmId"), Index("firedAt"), Index("groupId")]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alarmId: Long,
    val title: String,
    val phrase: String,
    val groupId: Long? = null,
    val groupTitle: String? = null,
    val stepIndex: Int = 0,
    val scheduledAt: Long,
    val firedAt: Long,
    val status: HistoryStatus,
    val snoozeCount: Int = 0,
    val detail: String? = null
)
