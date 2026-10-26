package com.voicereminder.vip.data

import android.content.Context
import com.voicereminder.vip.core.model.AlarmDraft
import com.voicereminder.vip.core.model.HistoryStatus
import com.voicereminder.vip.core.model.RepeatType
import com.voicereminder.vip.data.db.AlarmDao
import com.voicereminder.vip.data.db.AlarmEntity
import com.voicereminder.vip.data.db.CategoryDao
import com.voicereminder.vip.data.db.CategoryEntity
import com.voicereminder.vip.data.db.HistoryDao
import com.voicereminder.vip.data.db.HistoryEntity
import com.voicereminder.vip.data.db.RecordingDao
import com.voicereminder.vip.data.db.RecordingEntity
import com.voicereminder.vip.data.db.SeedData
import com.voicereminder.vip.data.db.TemplateDao
import com.voicereminder.vip.data.db.TemplateEntity
import com.voicereminder.vip.data.db.TemplateItemEntity
import com.voicereminder.vip.data.db.TemplateWithItems
import kotlinx.coroutines.flow.Flow
import java.io.File

class AlarmRepository(
    private val dao: AlarmDao
) {
    fun observeAll(): Flow<List<AlarmEntity>> = dao.observeAll()
    fun observeEnabled(): Flow<List<AlarmEntity>> = dao.observeEnabled()
    fun observeGrouped(): Flow<List<AlarmEntity>> = dao.observeGrouped()

    suspend fun get(id: Long): AlarmEntity? = dao.byId(id)
    suspend fun group(groupId: Long): List<AlarmEntity> = dao.group(groupId)
    fun observeGroup(groupId: Long): Flow<List<AlarmEntity>> = dao.observeGroup(groupId)
    suspend fun allEnabled(): List<AlarmEntity> = dao.enabledAlarms()
    suspend fun all(): List<AlarmEntity> = dao.allAlarms()

    suspend fun save(alarm: AlarmEntity): Long = dao.upsert(alarm)
    suspend fun saveAll(alarms: List<AlarmEntity>): List<Long> = dao.upsertAll(alarms)
    suspend fun delete(id: Long) = dao.deleteById(id)
    suspend fun deleteGroup(groupId: Long) = dao.deleteGroup(groupId)
    suspend fun deleteAll() = dao.deleteAll()
    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)
    suspend fun disableGroup(groupId: Long) = dao.disableGroup(groupId)
    suspend fun enableGroup(groupId: Long) = dao.enableGroup(groupId)
    suspend fun setNextTrigger(id: Long, next: Long?) = dao.setNextTrigger(id, next)
    suspend fun markTriggered(id: Long, at: Long, next: Long?) = dao.markTriggered(id, at, next)
    suspend fun setLastTriggered(id: Long, at: Long) = dao.setLastTriggered(id, at)
    suspend fun count(): Int = dao.count()
    suspend fun countByRecording(id: Long): Int = dao.countByRecording(id)

    suspend fun saveDraft(draft: AlarmDraft): Long {
        val existing = if (draft.id > 0) dao.byId(draft.id) else null
        val entity = AlarmEntity(
            id = draft.id,
            title = draft.title.trim(),
            phrase = draft.phrase.trim(),
            recordingId = draft.recordingId,
            hour = draft.hour,
            minute = draft.minute,
            dateEpochDay = if (draft.repeatType == RepeatType.ONCE) draft.epochDay else null,
            repeatType = draft.repeatType,
            daysMask = if (draft.repeatType == RepeatType.WEEKLY) draft.daysMask else 0,
            intervalMinutes = if (draft.repeatType == RepeatType.EVERY_INTERVAL) draft.intervalMinutes else 0,
            enabled = draft.enabled,
            languageTag = draft.languageTag,
            speechRate = draft.speechRate,
            pitch = draft.pitch,
            volume = draft.volume,
            preTone = draft.preTone,
            repeatCount = draft.repeatCount,
            repeatGapSeconds = draft.repeatGapSeconds,
            vibrate = draft.vibrate,
            snoozeMinutes = draft.snoozeMinutes,
            maxSnoozes = draft.maxSnoozes,
            categoryId = draft.categoryId,
            groupId = draft.groupId,
            groupTitle = draft.groupTitle,
            stepIndex = draft.stepIndex,
            stepCount = draft.stepCount,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            lastTriggeredAt = existing?.lastTriggeredAt,
            nextTriggerAt = existing?.nextTriggerAt
        )
        return dao.upsert(entity)
    }
}

class RecordingRepository(
    private val context: Context,
    private val dao: RecordingDao
) {
    fun observeAll(): Flow<List<RecordingEntity>> = dao.observeAll()
    suspend fun get(id: Long): RecordingEntity? = dao.byId(id)
    suspend fun all(): List<RecordingEntity> = dao.all()
    suspend fun save(recording: RecordingEntity): Long = dao.upsert(recording)
    suspend fun rename(recording: RecordingEntity, newName: String) =
        dao.upsert(recording.copy(name = newName.trim().ifBlank { recording.name }))

    suspend fun delete(recording: RecordingEntity) {
        runCatching { File(recording.filePath).delete() }
        dao.delete(recording)
    }

    fun fileExists(recording: RecordingEntity): Boolean = File(recording.filePath).isFile
    suspend fun count(): Int = dao.count()
}

class CategoryRepository(private val dao: CategoryDao) {
    fun observeAll(): Flow<List<CategoryEntity>> = dao.observeAll()
    suspend fun all(): List<CategoryEntity> = dao.all()
    suspend fun get(id: Long): CategoryEntity? = dao.byId(id)
    suspend fun save(category: CategoryEntity): Long = dao.upsert(category)
    suspend fun delete(category: CategoryEntity) = dao.delete(category)
    suspend fun count(): Int = dao.count()
}

class TemplateRepository(private val dao: TemplateDao) {
    fun observeAll(): Flow<List<TemplateWithItems>> = dao.observeWithItems()
    suspend fun all(): List<TemplateWithItems> = dao.withItems()
    suspend fun count(): Int = dao.count()
}

class HistoryRepository(private val dao: HistoryDao) {
    fun observeRecent(): Flow<List<HistoryEntity>> = dao.observeRecent()
    suspend fun since(millis: Long): List<HistoryEntity> = dao.since(millis)
    suspend fun countByAlarm(alarmId: Long, since: Long, status: HistoryStatus): Int =
        dao.countByAlarm(alarmId, since, status.name)
    suspend fun countSince(millis: Long): Int = dao.countSince(millis)
    suspend fun log(
        alarm: AlarmEntity,
        status: HistoryStatus,
        scheduledAt: Long,
        snoozeCount: Int = 0,
        detail: String? = null
    ): Long = dao.insert(
        HistoryEntity(
            alarmId = alarm.id,
            title = alarm.title,
            phrase = alarm.phrase,
            groupId = alarm.groupId,
            groupTitle = alarm.groupTitle,
            stepIndex = alarm.stepIndex,
            scheduledAt = scheduledAt,
            firedAt = System.currentTimeMillis(),
            status = status,
            snoozeCount = snoozeCount,
            detail = detail
        )
    )
    suspend fun logRaw(entry: HistoryEntity): Long = dao.insert(entry)
    suspend fun clear() = dao.deleteAll()
    suspend fun deleteOlderThan(millis: Long): Int = dao.deleteOlderThan(millis)
}

class DataSeeder(
    private val templateDao: TemplateDao,
    private val categoryDao: CategoryDao
) {
    suspend fun seedIfNeeded() {
        if (categoryDao.count() == 0) {
            categoryDao.upsertAll(SeedData.categories)
        }
        if (templateDao.count() == 0) {
            SeedData.templates.forEach { seed ->
                val templateId = templateDao.insert(
                    TemplateEntity(
                        key = seed.key,
                        title = seed.title,
                        description = seed.description,
                        iconKey = seed.iconKey,
                        colorHex = seed.colorHex,
                        categoryName = seed.categoryName
                    )
                )
                if (templateId > 0) {
                    templateDao.insertItems(
                        seed.items.mapIndexed { index, pair ->
                            TemplateItemEntity(
                                templateId = templateId,
                                offsetMinutes = pair.first,
                                phrase = pair.second,
                                sortOrder = index
                            )
                        }
                    )
                }
            }
        }
    }
}
