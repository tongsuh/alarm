package com.example.flashalarm.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.flashalarm.model.VibrationPatternType

/**
 * 震动同步调度与穿戴手环试震辅助器
 * 支持：手机马达触觉试震 + 华为/小米等智能手环/手表通知脉冲同步试震
 */
object VibrationHelper {

    private const val PREVIEW_NOTIFICATION_ID = 99999
    private const val CHANNEL_PREFIX = "flash_alarm_vibe_channel_"

    private var activeVibrator: Vibrator? = null
    private var stopPreviewRunnable: Runnable? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 获取指定震动类型对应的系统通知渠道 ID
     */
    fun getChannelId(patternType: VibrationPatternType): String {
        return "$CHANNEL_PREFIX${patternType.id}"
    }

    /**
     * 初始化各震动类型的专用通知渠道 (Android 8.0+)
     * 确保华为手环/小米手环捕获通知时，能准确解析对应节拍波形
     */
    fun createVibrationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            VibrationPatternType.values().forEach { vType ->
                val channelId = getChannelId(vType)
                val channel = NotificationChannel(
                    channelId,
                    "闹钟震动 - ${vType.title}",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "用于联动华为手环与手机硬件以【${vType.title}】节拍同步震动"
                    setBypassDnd(true)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    enableVibration(true)
                    vibrationPattern = vType.pattern
                    setSound(null, audioAttributes) // 试震与纯震动通道保持静音
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * 获取供用户试震感知的完整节奏序列（2~2.5秒）
     */
    fun getPreviewPattern(patternType: VibrationPatternType): LongArray {
        return when (patternType) {
            VibrationPatternType.STRONG_ALARM -> longArrayOf(0, 1000, 300, 1000)
            VibrationPatternType.RAPID_PULSE -> longArrayOf(0, 200, 200, 200, 200, 200, 200, 200, 200, 200)
            VibrationPatternType.HEARTBEAT -> longArrayOf(0, 150, 150, 350, 600, 150, 150, 350)
            VibrationPatternType.WAVE -> longArrayOf(0, 300, 200, 600, 300, 300, 200, 600)
        }
    }

    /**
     * 在手环与手机上同步触发对应类型的试震
     * @param context 上下文
     * @param patternType 所选震动类型
     * @param onFinished 试震结束时的回调
     */
    fun playPreview(
        context: Context,
        patternType: VibrationPatternType,
        onFinished: (() -> Unit)? = null
    ) {
        stopPreview(context)

        createVibrationChannels(context)

        val pattern = getPreviewPattern(patternType)
        var totalDurationMs = pattern.sum()
        if (totalDurationMs <= 0) totalDurationMs = 2300L

        // 1. 手机硬件马达驱动震动
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        activeVibrator = vibrator

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. 联动华为手环/小米手环/智能手表：发送高优先级试震通知
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = getChannelId(patternType)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("📳 ${patternType.title} · 试震")
            .setContentText("手环与手机正在同步震动试感...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(pattern)
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .build()

        try {
            nm.notify(PREVIEW_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. 试震结束后自动清理
        val runnable = Runnable {
            stopPreview(context)
            onFinished?.invoke()
        }
        stopPreviewRunnable = runnable
        mainHandler.postDelayed(runnable, totalDurationMs + 100L)
    }

    /**
     * 停止所有试震（手机停止马达、清除手环试震通知）
     */
    fun stopPreview(context: Context) {
        stopPreviewRunnable?.let {
            mainHandler.removeCallbacks(it)
            stopPreviewRunnable = null
        }

        try {
            activeVibrator?.cancel()
            activeVibrator = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(PREVIEW_NOTIFICATION_ID)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
