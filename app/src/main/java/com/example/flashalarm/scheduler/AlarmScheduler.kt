package com.example.flashalarm.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.flashalarm.model.AlarmItem
import com.example.flashalarm.receiver.AlarmReceiver
import com.example.flashalarm.ui.MainActivity
import java.util.Calendar

object AlarmScheduler {

    /**
     * 注册闹钟到 AlarmManager
     */
    fun scheduleAlarm(context: Context, alarm: AlarmItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 计算下次触发时间
        val triggerTime = calculateNextTriggerMillis(alarm)

        // 1. 点击系统状态栏的闹钟图标时触发的 PendingIntent (打开主页面)
        val showIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_ALARM_ID", alarm.id)
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            alarm.id.toInt(),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. 闹钟到点触发时接收广播的 PendingIntent
        val broadcastIntent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("EXTRA_ALARM_ID", alarm.id)
        }
        val broadcastPendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            broadcastIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. 构造 AlarmClockInfo 并使用 setAlarmClock 精准唤醒系统
        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
        alarmManager.setAlarmClock(alarmClockInfo, broadcastPendingIntent)
    }

    /**
     * 取消闹钟
     */
    fun cancelAlarm(context: Context, alarmId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /**
     * 计算下一个触发时间的毫秒戳
     */
    fun calculateNextTriggerMillis(alarm: AlarmItem): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // 若没有重复日配置（单次响铃）
        if (alarm.repeatDays.isEmpty()) {
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1) // 顺延到明天
            }
            return target.timeInMillis
        }

        // 若有重复星期配置 (1:周一 ~ 7:周日)
        // Calendar 中: 周日=1, 周一=2, 周二=3, ..., 周六=7
        for (dayOffset in 0..7) {
            val candidate = Calendar.getInstance().apply {
                timeInMillis = target.timeInMillis
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }
            if (candidate.timeInMillis > now.timeInMillis) {
                val candidateDayOfWeek = when (candidate.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> 1
                    Calendar.TUESDAY -> 2
                    Calendar.WEDNESDAY -> 3
                    Calendar.THURSDAY -> 4
                    Calendar.FRIDAY -> 5
                    Calendar.SATURDAY -> 6
                    Calendar.SUNDAY -> 7
                    else -> 1
                }
                if (alarm.repeatDays.contains(candidateDayOfWeek)) {
                    return candidate.timeInMillis
                }
            }
        }

        return target.timeInMillis
    }
}
