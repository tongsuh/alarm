package com.example.flashalarm.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.flashalarm.FlashAlarmApp
import com.example.flashalarm.data.AlarmRepository
import com.example.flashalarm.data.FlashProfileRepository
import com.example.flashalarm.scheduler.AlarmScheduler
import com.example.flashalarm.ui.AlarmAlertActivity

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra("EXTRA_ALARM_ID", -1L)
        if (alarmId == -1L) return

        val alarmRepo = AlarmRepository(context)
        val alarm = alarmRepo.getAlarmById(alarmId) ?: return
        if (!alarm.isEnabled) return

        val profileRepo = FlashProfileRepository(context)
        val profile = profileRepo.getProfileById(alarm.flashProfileId)

        // 1. 构造全屏 AlarmAlertActivity 的 Intent 并塞入声音、亮屏、模板与自动停止配置
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

        // 2. 构造高优先级通知，使用 fullScreenIntent 保证锁屏唤醒
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

        // 3. 尝试直接启动 Activity
        try {
            context.startActivity(alertIntent)
        } catch (e: Exception) {
            // Android 10+ 后台限制，依靠 fullScreenIntent
        }

        // 4. 重复闹钟自动调度下一次，单次闹钟则更新为关闭状态
        if (alarm.repeatDays.isNotEmpty()) {
            AlarmScheduler.scheduleAlarm(context, alarm)
        } else {
            alarmRepo.updateAlarmEnabled(alarm.id, false)
        }
    }
}
