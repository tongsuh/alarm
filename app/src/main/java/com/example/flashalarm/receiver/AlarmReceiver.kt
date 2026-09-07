package com.example.flashalarm.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.flashalarm.FlashAlarmApp
import com.example.flashalarm.data.AlarmRepository
import com.example.flashalarm.data.FlashProfileRepository
import com.example.flashalarm.scheduler.AlarmScheduler
import com.example.flashalarm.ui.AlarmAlertActivity

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 1. 立即获取 WakeLock，确保即使在深度休眠中 CPU 也能保持清醒 10 分钟
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "FlashAlarm:AlarmReceiverWakeLock"
        )
        wakeLock.acquire(10 * 60 * 1000L) // 10 分钟兜底

        val alarmId = intent.getLongExtra("EXTRA_ALARM_ID", -1L)
        if (alarmId == -1L) {
            wakeLock.release()
            return
        }

        val alarmRepo = AlarmRepository(context)
        val alarm = alarmRepo.getAlarmById(alarmId)
        if (alarm == null || !alarm.isEnabled) {
            wakeLock.release()
            return
        }

        val profileRepo = FlashProfileRepository(context)
        val profile = profileRepo.getProfileById(alarm.flashProfileId)

        // 2. 构造拉起全屏界面的 Intent
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
            putExtra(AlarmAlertActivity.EXTRA_TOTAL_DURATION_CIRCLE, profile.totalDurationCircle)
            putExtra(AlarmAlertActivity.EXTRA_AUTO_DISMISS_SEC, alarm.autoDismissSec)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            alarm.id.toInt(),
            alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. 构造最高优先级通知，使用 fullScreenIntent 保证在锁屏和使用其他应用时强行弹窗
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, FlashAlarmApp.ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(alarm.label)
            .setContentText("闹钟提醒中，轻触屏幕任意位置关闭")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()

        notificationManager.notify(alarm.id.toInt(), notification)

        // 4. 双重保障直接启动 Activity
        try {
            context.startActivity(alertIntent)
        } catch (e: Exception) {
            // Android 10+ 后台限制，依靠 fullScreenIntent 唤起
        }

        // 5. 调度间隔重响机制 (特性 3)
        val isIntervalTrigger = intent.getBooleanExtra("EXTRA_IS_INTERVAL_REPEAT", false)
        val currentRepeatIndex = if (isIntervalTrigger) alarm.currentIntervalIndex + 1 else 0

        if (alarm.isIntervalRepeatEnabled && currentRepeatIndex < alarm.intervalRepeatTimes) {
            // 还有剩余重响次数，调度下一次间隔重响
            val updatedAlarm = alarm.copy(currentIntervalIndex = currentRepeatIndex)
            alarmRepo.saveAlarm(updatedAlarm)
            AlarmScheduler.scheduleIntervalRepeat(context, updatedAlarm, alarm.intervalRepeatMinutes)
        } else {
            // 间隔重响已全部完成
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
