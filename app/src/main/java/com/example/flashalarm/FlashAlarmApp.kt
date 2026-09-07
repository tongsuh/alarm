package com.example.flashalarm

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

class FlashAlarmApp : Application() {

    companion object {
        const val ALARM_CHANNEL_ID = "flash_alarm_alert_channel"
        const val ALARM_CHANNEL_NAME = "闪烁闹钟响铃提醒"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALARM_CHANNEL_ID,
                ALARM_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "闹钟触发时的高优先级通知，支持锁屏直接拉起全屏界面"
                setBypassDnd(true) // 绕过勿扰模式
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setSound(null, null) // 声音由 Activity 统一管理，避免重复
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
