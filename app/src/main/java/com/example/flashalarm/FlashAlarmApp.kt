package com.example.flashalarm

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

class FlashAlarmApp : Application() {

    companion object {
        // 前台保活服务常驻渠道 (静默后台服务，满足 Android 系统保活规范，手环不感知)
        const val SERVICE_CHANNEL_ID = "flash_alarm_service_keepalive"
        const val SERVICE_CHANNEL_NAME = "闪烁闹钟运行守护"

        // 穿戴设备与手环告警专用通知渠道 (全新 ID 强制清除旧系统缓存，开启震动与铃声供手环捕获)
        const val WEARABLE_ALERT_CHANNEL_ID = "flash_alarm_wearable_alert_v2"
        const val WEARABLE_ALERT_CHANNEL_NAME = "闪烁闹钟穿戴与响铃提醒"

        // 兼容旧引用
        const val ALARM_CHANNEL_ID = WEARABLE_ALERT_CHANNEL_ID
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 删除可能被系统锁死为静音的旧版本渠道缓存
            try {
                manager.deleteNotificationChannel("flash_alarm_alert_channel")
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 1. 服务守护常驻渠道 (LOW 重要度，静音不震动，前台服务保活专用)
            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                SERVICE_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保证闹钟准时唤醒的前台守护服务"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(serviceChannel)

            // 2. 穿戴设备同步与告警通知渠道 (HIGH 重要度，支持绕过免打扰、支持高优先级提示音与自定义波形)
            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val wearableChannel = NotificationChannel(
                WEARABLE_ALERT_CHANNEL_ID,
                WEARABLE_ALERT_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "闹钟响铃时的高优先级通知，用于联动华为手环/智能手表震动与锁屏唤醒"
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 300)
                setSound(defaultSoundUri, audioAttributes)
            }
            manager.createNotificationChannel(wearableChannel)

            // 初始化各震动类型的专用通知渠道，确保手环能准确响应对应节拍
            com.example.flashalarm.util.VibrationHelper.createVibrationChannels(this)
        }
    }
}
