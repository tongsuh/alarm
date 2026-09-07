package com.example.flashalarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.flashalarm.data.AlarmRepository
import com.example.flashalarm.data.FlashProfileRepository
import com.example.flashalarm.scheduler.AlarmScheduler
import com.example.flashalarm.service.AlarmService
import com.example.flashalarm.ui.AlarmAlertActivity

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra("EXTRA_ALARM_ID", -1L)
        if (alarmId == -1L) return

        val alarmRepo = AlarmRepository(context)
        val alarm = alarmRepo.getAlarmById(alarmId)
        if (alarm == null || !alarm.isEnabled) return

        val profileRepo = FlashProfileRepository(context)
        val profile = profileRepo.getProfileById(alarm.flashProfileId)

        // 1. 启动前台服务 AlarmService (核心保活：即使屏幕锁死或在使用其他 App，也 100% 立即响铃和振动)
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_START_ALARM
            putExtra(AlarmAlertActivity.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmAlertActivity.EXTRA_ALARM_LABEL, alarm.label)
            putExtra(AlarmAlertActivity.EXTRA_IS_SOUND_ENABLED, alarm.isSoundEnabled)
            putExtra(AlarmAlertActivity.EXTRA_IS_FLASH_ENABLED, alarm.isFlashEnabled)
            putExtra(AlarmAlertActivity.EXTRA_RINGTONE_URI, alarm.ringtoneUri)
            putExtra(AlarmAlertActivity.EXTRA_TARGET_COLOR_HEX, profile.targetColorHex)
            putExtra(AlarmAlertActivity.EXTRA_TARGET_BRIGHTNESS, profile.targetBrightness)
            putExtra(AlarmAlertActivity.EXTRA_ON_DURATION_MS, profile.onDurationMs)
            putExtra(AlarmAlertActivity.EXTRA_OFF_DURATION_MS, profile.offDurationMs)
            putExtra(AlarmAlertActivity.EXTRA_TOTAL_DURATION_CIRCLE, profile.totalDurationCircle)
            putExtra(AlarmAlertActivity.EXTRA_AUTO_DISMISS_SEC, alarm.autoDismissSec)
        }

        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. 调度间隔重响机制
        val isIntervalTrigger = intent.getBooleanExtra("EXTRA_IS_INTERVAL_REPEAT", false)
        val currentRepeatIndex = if (isIntervalTrigger) alarm.currentIntervalIndex + 1 else 0

        if (alarm.isIntervalRepeatEnabled && currentRepeatIndex < alarm.intervalRepeatTimes) {
            val updatedAlarm = alarm.copy(currentIntervalIndex = currentRepeatIndex)
            alarmRepo.saveAlarm(updatedAlarm)
            AlarmScheduler.scheduleIntervalRepeat(context, updatedAlarm, alarm.intervalRepeatMinutes)
        } else {
            val resetAlarm = alarm.copy(currentIntervalIndex = 0)
            if (resetAlarm.repeatDays.isNotEmpty()) {
                alarmRepo.saveAlarm(resetAlarm)
                AlarmScheduler.scheduleAlarm(context, resetAlarm)
            } else {
                alarmRepo.saveAlarm(resetAlarm.copy(isEnabled = false))
            }
        }
    }
}
