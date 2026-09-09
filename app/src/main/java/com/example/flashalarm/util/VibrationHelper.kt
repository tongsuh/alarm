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
import kotlinx.coroutines.*

/**
 * 震动同步调度与穿戴手环试震辅助器
 * 核心设计：解决智能手环无法解析微观波形、只能被动响应消息到达的特点，
 * 创新采用【宏观消息到达节拍引擎 (Macro Cadence Engine)】，通过不同模式专属的
 * 消息下发时间间隔与组合停顿，使手环产生极其清晰可辨的物理震动区分。
 */
object VibrationHelper {

    private const val PREVIEW_NOTIFICATION_ID = 99998
    // 升级至 v6 版本通道，彻底清除旧版系统与穿戴健康 App 缓存
    private const val CHANNEL_PREFIX = "flash_alarm_vibe_v6_"

    private var activeVibrator: Vibrator? = null
    private var previewJob: Job? = null
    private val helperScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * 获取指定震动类型对应的系统通知渠道 ID
     */
    fun getChannelId(patternType: VibrationPatternType): String {
        return "$CHANNEL_PREFIX${patternType.id}"
    }

    /**
     * 智能手环宏观节拍调度表 (Macro Cadence)
     * 手环底层固件在收到每条蓝牙通知时，均会触发一次内置电机震动。
     * 通过控制消息发送的时间间隔与停顿，在手腕上创造出截然不同的体感：
     *
     * 1. 持续强震：每 1400ms 稳重连发，强力厚重
     * 2. 紧促脉冲：每 900ms 快速高频敲击，急促蜂鸣
     * 3. 心跳双击：先发第 1 下 -> 间隔 450ms 紧随第 2 下 -> 停顿 1800ms，在手腕上形成无可挑剔的“咚-咚......咚-咚”双击节拍
     * 4. 渐强波浪：800ms -> 1200ms -> 2000ms 变速波浪起伏
     */
    fun getWearablePulseCadence(patternType: VibrationPatternType): List<Long> {
        return when (patternType) {
            VibrationPatternType.STRONG_ALARM -> listOf(1400L)
            VibrationPatternType.RAPID_PULSE -> listOf(900L)
            VibrationPatternType.HEARTBEAT -> listOf(450L, 1800L)
            VibrationPatternType.WAVE -> listOf(800L, 1200L, 2000L)
        }
    }

    /**
     * 获取供手机本地马达驱动的微观物理波形
     */
    fun getPreviewPattern(patternType: VibrationPatternType): LongArray {
        return when (patternType) {
            VibrationPatternType.STRONG_ALARM -> longArrayOf(0, 1000, 400, 1000, 400)
            VibrationPatternType.RAPID_PULSE -> longArrayOf(0, 150, 100, 150, 100, 150, 100, 150, 100, 150, 100, 150, 100)
            VibrationPatternType.HEARTBEAT -> longArrayOf(0, 120, 100, 250, 550, 120, 100, 250, 550)
            VibrationPatternType.WAVE -> longArrayOf(0, 200, 150, 450, 200, 800, 350)
        }
    }

    /**
     * 初始化各震动类型的专用通知渠道 (Android 8.0+)
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
                val fullPattern = getPreviewPattern(vType)
                val channel = NotificationChannel(
                    channelId,
                    "闹钟震动 - ${vType.title}",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "用于联动智能手环与手机硬件以【${vType.title}】节拍同步震动"
                    setBypassDnd(true)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    enableVibration(true)
                    vibrationPattern = fullPattern
                    setSound(null, audioAttributes)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * 在手环与手机上同步触发对应类型的试震：
     * 1. 手机马达：直接播放专属波形
     * 2. 智能手环：按该模式专属的【宏观消息节拍】连续发送多波次脉冲通知，让手环在 4.5 秒内清晰感知节奏差异！
     */
    fun playPreview(
        context: Context,
        patternType: VibrationPatternType,
        onFinished: (() -> Unit)? = null
    ) {
        stopPreview(context)
        createVibrationChannels(context)

        val pattern = getPreviewPattern(patternType)

        // 1. 手机硬件马达驱动波形震动
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

        // 2. 联动华为手环/小米手环：基于宏观节奏循环发报
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = getChannelId(patternType)
        val cadence = getWearablePulseCadence(patternType)

        previewJob = helperScope.launch {
            val startTime = System.currentTimeMillis()
            val totalDurationMs = 4500L
            var cadenceIndex = 0
            var pulseCount = 0

            while (isActive && (System.currentTimeMillis() - startTime) < totalDurationMs) {
                // 关键点：交替 ID (PREVIEW_NOTIFICATION_ID 与 PREVIEW_NOTIFICATION_ID + 1)，
                // 完美规避华为健康/小米穿戴 App 的短时间重复通知合并过滤机制，确保手环每一击都震出来！
                val notifyId = PREVIEW_NOTIFICATION_ID + (pulseCount % 2)
                pulseCount++

                try {
                    val notification = NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                        .setContentTitle("📳 ${patternType.title} · 试震中")
                        .setContentText("手环正在以【${patternType.title}】专属节奏感知律动...")
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setVibrate(pattern)
                        .setWhen(System.currentTimeMillis())
                        .setOngoing(false)
                        .setAutoCancel(true)
                        .setOnlyAlertOnce(false)
                        .build()

                    nm.notify(notifyId, notification)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val waitTime = cadence[cadenceIndex % cadence.size]
                cadenceIndex++
                delay(waitTime)
            }

            stopPreview(context)
            onFinished?.invoke()
        }
    }

    /**
     * 停止所有试震（手机停止马达、清除手环所有试震通知）
     */
    fun stopPreview(context: Context) {
        previewJob?.cancel()
        previewJob = null

        try {
            activeVibrator?.cancel()
            activeVibrator = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(PREVIEW_NOTIFICATION_ID)
            nm.cancel(PREVIEW_NOTIFICATION_ID + 1)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
