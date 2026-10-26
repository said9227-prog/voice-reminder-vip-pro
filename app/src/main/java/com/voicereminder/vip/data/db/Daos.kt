package com.voicereminder.vip.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute, id")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE enabled = 1 ORDER BY hour, minute")
    fun observeEnabled(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE enabled = 1")
    suspend fun enabledAlarms(): List<AlarmEntity>

    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    suspend fun allAlarms(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun byId(id: Long): AlarmEntity?

    @Query("SELECT * FROM alarms WHERE groupId = :groupId ORDER BY stepIndex, hour, minute")
    suspend fun group(groupId: Long): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE groupId = :groupId ORDER BY stepIndex, hour, minute")
    fun observeGroup(groupId: Long): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE groupId IS NOT NULL ORDER BY groupId, stepIndex")
    fun observeGrouped(): Flow<List<AlarmEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alarm: AlarmEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(alarms: List<AlarmEntity>): List<Long>

    @Delete
    suspend fun delete(alarm: AlarmEntity)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM alarms WHERE groupId = :groupId")
    suspend fun deleteGroup(groupId: Long)

    @Query("DELETE FROM alarms")
    suspend fun deleteAll()

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE alarms SET enabled = 0 WHERE groupId = :groupId")
    suspend fun disableGroup(groupId: Long)

    @Query("UPDATE alarms SET enabled = 1 WHERE groupId = :groupId")
    suspend fun enableGroup(groupId: Long)

    @Query("UPDATE alarms SET nextTriggerAt = :next WHERE id = :id")
    suspend fun setNextTrigger(id: Long, next: Long?)

    @Query("UPDATE alarms SET nextTriggerAt = :next, lastTriggeredAt = :triggeredAt WHERE id = :id")
    suspend fun markTriggered(id: Long, triggeredAt: Long, next: Long?)

    @Query("UPDATE alarms SET lastTriggeredAt = :triggeredAt WHERE id = :id")
    suspend fun setLastTriggered(id: Long, triggeredAt: Long)

    @Query("SELECT COUNT(*) FROM alarms")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM alarms WHERE recordingId = :recordingId")
    suspend fun countByRecording(recordingId: Long): Int
}

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun byId(id: Long): RecordingEntity?

    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    suspend fun all(): List<RecordingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recording: RecordingEntity): Long

    @Delete
    suspend fun delete(recording: RecordingEntity)

    @Query("SELECT COUNT(*) FROM recordings")
    suspend fun count(): Int
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY id")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun byId(id: Long): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int
}

@Dao
interface TemplateDao {
    @Transaction
    @Query("SELECT * FROM templates ORDER BY id")
    fun observeWithItems(): Flow<List<TemplateWithItems>>

    @Transaction
    @Query("SELECT * FROM templates ORDER BY id")
    suspend fun withItems(): List<TemplateWithItems>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(template: TemplateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<TemplateItemEntity>)

    @Query("SELECT COUNT(*) FROM templates")
    suspend fun count(): Int
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY firedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 300): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE firedAt >= :since ORDER BY firedAt DESC")
    suspend fun since(since: Long): List<HistoryEntity>

    @Query("SELECT COUNT(*) FROM history WHERE alarmId = :alarmId AND firedAt >= :since AND status = :status")
    suspend fun countByAlarm(alarmId: Long, since: Long, status: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntity): Long

    @Query("DELETE FROM history")
    suspend fun deleteAll()

    @Query("DELETE FROM history WHERE firedAt < :before")
    suspend fun deleteOlderThan(before: Long): Int

    @Query("SELECT COUNT(*) FROM history WHERE firedAt >= :since")
    suspend fun countSince(since: Long): Int
}
