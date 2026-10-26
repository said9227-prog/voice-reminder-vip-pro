package com.voicereminder.vip.core.schedule

import com.voicereminder.vip.core.model.RepeatType
import com.voicereminder.vip.core.model.SchedulingResult
import com.voicereminder.vip.data.AlarmRepository
import java.time.ZoneId

data class RescheduleReport(
    val scheduled: Int = 0,
    val exact: Int = 0,
    val inexact: Int = 0,
    val disabledExpired: Int = 0,
    val failed: Int = 0
)

class AlarmRescheduler(
    private val alarms: AlarmRepository,
    private val scheduler: AlarmScheduler
) {
    suspend fun rescheduleAll(now: Long = System.currentTimeMillis()): RescheduleReport {
        var scheduled = 0
        var exact = 0
        var inexact = 0
        var disabled = 0
        var failed = 0

        alarms.allEnabled().forEach { alarm ->
            val next = NextTriggerCalculator.calculate(alarm, now, ZoneId.systemDefault())
            if (next == null) {
                if (alarm.repeatType == RepeatType.ONCE) {
                    alarms.setEnabled(alarm.id, false)
                    alarms.setNextTrigger(alarm.id, null)
                    disabled++
                }
                return@forEach
            }
            alarms.setNextTrigger(alarm.id, next)
            when (scheduler.scheduleAt(alarm.id, next, alarm.title, 0)) {
                SchedulingResult.EXACT -> { exact++; scheduled++ }
                SchedulingResult.INEXACT -> { inexact++; scheduled++ }
                SchedulingResult.FAILED -> failed++
            }
        }
        return RescheduleReport(scheduled, exact, inexact, disabled, failed)
    }

    suspend fun scheduleOne(alarmId: Long): SchedulingResult {
        val alarm = alarms.get(alarmId) ?: return SchedulingResult.FAILED
        if (!alarm.enabled) return SchedulingResult.FAILED
        val next = NextTriggerCalculator.calculate(alarm, System.currentTimeMillis(), ZoneId.systemDefault())
            ?: run {
                if (alarm.repeatType == RepeatType.ONCE) {
                    alarms.setEnabled(alarmId, false)
                    alarms.setNextTrigger(alarmId, null)
                }
                return SchedulingResult.FAILED
            }
        alarms.setNextTrigger(alarmId, next)
        return scheduler.scheduleAt(alarmId, next, alarm.title, 0)
    }
}
