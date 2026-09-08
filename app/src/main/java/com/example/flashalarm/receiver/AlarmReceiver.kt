package com.example.flashalarm.receiver

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
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
            putExtra(AlarmAlertActivity.EXTRA_AUTO_DISMISS_SEC, alarm.autoDismissSec)
            putExtra(AlarmAlertActivity.EXTRA_IS_VIBRATION_ENABLED, alarm.isVibrationEnabled)
            putExtra(AlarmAlertActivity.EXTRA_VIBRATION_DURATION_SEC, alarm.vibrationDurationSec)
            putExtra(AlarmAlertActivity.EXTRA_VIBRATION_PATTERN_ID, alarm.vibrationPatternId)
        }

        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. 尝试直接通过 PendingIntent.send 启动全屏界面 (附带 Android 14+ 豁免)
        val bundleOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }.toBundle()
        } else {
            null
        }

        val alertIntent = Intent(context, AlarmAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AlarmAlertActivity.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmAlertActivity.EXTRA_ALARM_LABEL, alarm.label)
            putExtra(AlarmAlertActivity.EXTRA_IS_SOUND_ENABLED, alarm.isSoundEnabled)
            putExtra(AlarmAlertActivity.EXTRA_IS_FLASH_ENABLED, alarm.isFlashEnabled)
            putExtra(AlarmAlertActivity.EXTRA_RINGTONE_URI, alarm.ringtoneUri)
            putExtra(AlarmAlertActivity.EXTRA_TARGET_COLOR_HEX, profile.targetColorHex)
            putExtra(AlarmAlertActivity.EXTRA_TARGET_BRIGHTNESS, profile.targetBrightness)
            putExtra(AlarmAlertActivity.EXTRA_ON_DURATION_MS, profile.onDurationMs)
            putExtra(AlarmAlertActivity.EXTRA_OFF_DURATION_MS, profile.offDurationMs)
            putExtra(AlarmAlertActivity.EXTRA_AUTO_DISMISS_SEC, alarm.autoDismissSec)
            putExtra(AlarmAlertActivity.EXTRA_IS_VIBRATION_ENABLED, alarm.isVibrationEnabled)
            putExtra(AlarmAlertActivity.EXTRA_VIBRATION_DURATION_SEC, alarm.vibrationDurationSec)
            putExtra(AlarmAlertActivity.EXTRA_VIBRATION_PATTERN_ID, alarm.vibrationPatternId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            alarm.id.toInt(),
            alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            bundleOptions
        )

        try {
            pendingIntent.send(context, 0, null, null, null, null, bundleOptions)
        } catch (e: Exception) {
            try {
                context.startActivity(alertIntent, bundleOptions)
            } catch (ex: Exception) {
                // 若系统限制后台弹窗，由 AlarmService 的 WindowManager 悬浮窗遮罩兜底执行闪烁
            }
        }

        // 3. 调度间隔重响机制
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
