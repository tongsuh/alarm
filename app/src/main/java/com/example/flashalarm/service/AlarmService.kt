package com.example.flashalarm.service

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.flashalarm.FlashAlarmApp
import com.example.flashalarm.model.VibrationPatternType
import com.example.flashalarm.ui.AlarmAlertActivity
import com.example.flashalarm.util.VibrationHelper
import kotlinx.coroutines.*
import java.io.File

/**
 * 核心前台守护与全屏闪烁服务 (Foreground Service + WindowManager Overlay)
 *
 * 彻底攻克“在其他 App 界面时屏幕闪烁不执行”的核心原因：
 * 1. Android 10+ 严格禁止后台应用直接抢占前台 Activity（打断用户玩游戏/聊天）。
 * 2. 解决方案：借助系统级悬浮窗（TYPE_APPLICATION_OVERLAY）直接在其他应用上方挂载全屏闪烁遮罩！
 * 3. 无论用户在微信、抖音还是游戏界面，屏幕直接以设定的颜色与亮度全屏呼吸闪烁！
 * 4. 配合 Android 14 的 MODE_BACKGROUND_ACTIVITY_START_ALLOWED 豁免，双重保障！
 */
class AlarmService : Service() {

    companion object {
        const val ACTION_START_ALARM = "ACTION_START_ALARM"
        const val ACTION_STOP_ALARM = "ACTION_STOP_ALARM"

        var isServiceRunning = false
            private set

        var instance: AlarmService? = null
            private set

        fun stopAlarm(context: Context) {
            val stopIntent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_STOP_ALARM
            }
            context.startService(stopIntent)
        }

        fun dismissOverlay() {
            instance?.dismissOverlayInternal()
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var vibrationStopHandler: Handler? = null
    private var currentWearableNotificationId: Int = -1
    private var wearablePulseJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var autoStopHandler: Handler? = null

    // WindowManager 全屏遮罩相关
    private var windowManager: WindowManager? = null
    private var overlayRootView: FrameLayout? = null
    private var overlayFlashJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_STOP_ALARM) {
            stopAlarmInternal()
            return START_NOT_STICKY
        }

        isServiceRunning = true
        acquireWakeLock()

        val alarmId = intent.getLongExtra(AlarmAlertActivity.EXTRA_ALARM_ID, -1L)
        val alarmLabel = intent.getStringExtra(AlarmAlertActivity.EXTRA_ALARM_LABEL) ?: "闹钟"
        val isSoundEnabled = intent.getBooleanExtra(AlarmAlertActivity.EXTRA_IS_SOUND_ENABLED, true)
        val isFlashEnabled = intent.getBooleanExtra(AlarmAlertActivity.EXTRA_IS_FLASH_ENABLED, true)
        val ringtoneUriStr = intent.getStringExtra(AlarmAlertActivity.EXTRA_RINGTONE_URI)
        val targetColorHex = intent.getStringExtra(AlarmAlertActivity.EXTRA_TARGET_COLOR_HEX) ?: "#FF1A00"
        val targetBrightness = intent.getFloatExtra(AlarmAlertActivity.EXTRA_TARGET_BRIGHTNESS, 0.85f)
        val onDurationMs = intent.getLongExtra(AlarmAlertActivity.EXTRA_ON_DURATION_MS, 1500L)
        val offDurationMs = intent.getLongExtra(AlarmAlertActivity.EXTRA_OFF_DURATION_MS, 1000L)
        val autoDismissSec = intent.getIntExtra(AlarmAlertActivity.EXTRA_AUTO_DISMISS_SEC, 30)
        val isVibrationEnabled = intent.getBooleanExtra(AlarmAlertActivity.EXTRA_IS_VIBRATION_ENABLED, true)
        val vibrationDurationSec = intent.getIntExtra(AlarmAlertActivity.EXTRA_VIBRATION_DURATION_SEC, 15)
        val vibrationPatternId = intent.getStringExtra(AlarmAlertActivity.EXTRA_VIBRATION_PATTERN_ID) ?: "strong"
        val patternType = VibrationPatternType.fromId(vibrationPatternId)

        // 1. Android 14+ 关键适配：给 PendingIntent 设置允许后台弹窗的豁免参数
        val bundleOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }.toBundle()
        } else {
            null
        }

        val alertIntent = Intent(this, AlarmAlertActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AlarmAlertActivity.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmAlertActivity.EXTRA_ALARM_LABEL, alarmLabel)
            putExtra(AlarmAlertActivity.EXTRA_IS_SOUND_ENABLED, isSoundEnabled)
            putExtra(AlarmAlertActivity.EXTRA_IS_FLASH_ENABLED, isFlashEnabled)
            putExtra(AlarmAlertActivity.EXTRA_RINGTONE_URI, ringtoneUriStr)
            putExtra(AlarmAlertActivity.EXTRA_TARGET_COLOR_HEX, targetColorHex)
            putExtra(AlarmAlertActivity.EXTRA_TARGET_BRIGHTNESS, targetBrightness)
            putExtra(AlarmAlertActivity.EXTRA_ON_DURATION_MS, onDurationMs)
            putExtra(AlarmAlertActivity.EXTRA_OFF_DURATION_MS, offDurationMs)
            putExtra(AlarmAlertActivity.EXTRA_AUTO_DISMISS_SEC, autoDismissSec)
            putExtra(AlarmAlertActivity.EXTRA_IS_VIBRATION_ENABLED, isVibrationEnabled)
            putExtra(AlarmAlertActivity.EXTRA_VIBRATION_DURATION_SEC, vibrationDurationSec)
            putExtra(AlarmAlertActivity.EXTRA_VIBRATION_PATTERN_ID, vibrationPatternId)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            alarmId.toInt(),
            alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            bundleOptions
        )

        val stopServiceIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            alarmId.toInt() + 1000,
            stopServiceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. 前台服务保活常驻通知 (静默常驻，防止系统杀后台，手环不感知)
        val keepaliveNotification = NotificationCompat.Builder(this, FlashAlarmApp.SERVICE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("闪烁闹钟")
            .setContentText("闹钟正在运行中")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                alarmId.toInt().coerceAtLeast(1),
                keepaliveNotification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(alarmId.toInt().coerceAtLeast(1), keepaliveNotification)
        }

        // 3. 穿戴手环与高优先级告警通知 (核心关键：setOngoing(false) 非常驻通知，确保华为手环/智能手表 100% 捕获并震动)
        val wearableNotificationId = (alarmId.toInt() and 0x7FFFFFFF) + 88888
        currentWearableNotificationId = wearableNotificationId

        val alertChannelId = if (isVibrationEnabled) {
            VibrationHelper.getChannelId(patternType)
        } else {
            FlashAlarmApp.WEARABLE_ALERT_CHANNEL_ID
        }

        val wearableNotification = NotificationCompat.Builder(this, alertChannelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(alarmLabel)
            .setContentText("闹钟正在响铃，点击查看或关闭")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭闹钟", stopPendingIntent)
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .apply {
                if (isVibrationEnabled) {
                    setVibrate(patternType.pattern)
                } else {
                    setVibrate(longArrayOf(0))
                }
            }
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            notificationManager.notify(wearableNotificationId, wearableNotification)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. 播放音乐与独立时长震动 (联动手机硬件马达与手环脉冲)
        if (isSoundEnabled) {
            playRingtone(ringtoneUriStr)
        }
        if (isVibrationEnabled && vibrationDurationSec > 0) {
            startVibration(
                pattern = patternType.pattern,
                durationSec = vibrationDurationSec,
                wearableNotificationId = wearableNotificationId,
                wearableNotification = wearableNotification
            )
        }

        // 4. 关键突破：如果在其他应用界面，直接通过 WindowManager 在屏幕最顶层挂载全屏遮罩闪烁！
        if (isFlashEnabled) {
            val targetColor = try {
                Color.parseColor(targetColorHex)
            } catch (e: Exception) {
                Color.parseColor("#FF1A00")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                showFullscreenOverlay(
                    targetColor = targetColor,
                    targetBrightness = targetBrightness,
                    onDurationMs = onDurationMs,
                    offDurationMs = offDurationMs,
                    alarmLabel = alarmLabel
                )
            }
        }

        // 5. 尝试通过 PendingIntent 与 ActivityOptions 启动 Activity (Android 14 规范)
        try {
            fullScreenPendingIntent.send(this, 0, null, null, null, null, bundleOptions)
        } catch (e: Exception) {
            try {
                startActivity(alertIntent, bundleOptions)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }

        // 6. 自动停止保护
        if (autoDismissSec > 0) {
            autoStopHandler = Handler(Looper.getMainLooper()).apply {
                postDelayed({
                    stopAlarmInternal()
                }, autoDismissSec * 1000L)
            }
        }

        return START_NOT_STICKY
    }

    /**
     * 系统级全屏遮罩：无视任何前台第三方 App（微信/游戏/抖音），直接在最上层执行规律闪烁与渐黑
     */
    private fun showFullscreenOverlay(
        targetColor: Int,
        targetBrightness: Float,
        onDurationMs: Long,
        offDurationMs: Long,
        alarmLabel: String
    ) {
        if (overlayRootView != null) return

        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.FILL
            }

            overlayRootView = FrameLayout(this).apply {
                setBackgroundColor(targetColor)
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        stopAlarmInternal()
                        true
                    } else {
                        false
                    }
                }
                setOnClickListener {
                    stopAlarmInternal()
                }
            }

            val centerPanel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER
                }
            }

            val tvLabel = TextView(this).apply {
                text = alarmLabel
                textSize = 28f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }

            val tvTime = TextView(this).apply {
                val now = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                text = now
                textSize = 72f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 14, 0, 14)
            }

            val tvHint = TextView(this).apply {
                text = "轻触屏幕任意位置关闭闹钟"
                textSize = 18f
                setTextColor(Color.argb(180, 255, 255, 255))
                gravity = Gravity.CENTER
            }

            centerPanel.addView(tvLabel)
            centerPanel.addView(tvTime)
            centerPanel.addView(tvHint)
            overlayRootView?.addView(centerPanel)

            windowManager?.addView(overlayRootView, params)

            // 启动全屏遮罩的持续呼吸闪烁与平滑渐黑 (纯本地 GPU 渲染，零 IPC 开销)
            overlayFlashJob = serviceScope.launch {
                val steps = 18
                val stepTime = 450L / steps
                val r = Color.red(targetColor)
                val g = Color.green(targetColor)
                val b = Color.blue(targetColor)

                while (isActive) {
                    // 亮状态
                    overlayRootView?.setBackgroundColor(targetColor)
                    centerPanel.visibility = View.VISIBLE
                    centerPanel.alpha = 1f
                    delay(onDurationMs)

                    if (!isActive) break

                    // 平滑渐黑过渡 (450ms)
                    for (i in 1..steps) {
                        if (!isActive) break
                        val factor = 1f - ((i.toFloat() / steps) * (i.toFloat() / steps))
                        val curColor = Color.rgb((r * factor).toInt(), (g * factor).toInt(), (b * factor).toInt())
                        overlayRootView?.setBackgroundColor(curColor)
                        centerPanel.alpha = factor
                        delay(stepTime)
                    }

                    if (!isActive) break

                    // 暗状态保持
                    overlayRootView?.setBackgroundColor(Color.BLACK)
                    centerPanel.visibility = View.INVISIBLE
                    delay(offDurationMs)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "FlashAlarm:AlarmServiceWakeLock"
            )
            wakeLock?.acquire(15 * 60 * 1000L)
        }
    }

    private fun playRingtone(path: String?) {
        try {
            if (!path.isNullOrBlank()) {
                val file = File(path)
                if (file.exists() && file.length() > 0) {
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        isLooping = true
                        prepare()
                        start()
                    }
                    return
                }
            }

            val uri = if (!path.isNullOrBlank()) {
                Uri.parse(path)
            } else {
                val rawResId = resources.getIdentifier("alarm_custom", "raw", packageName)
                if (rawResId != 0) {
                    Uri.parse("android.resource://$packageName/$rawResId")
                } else {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                }
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmService, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            try {
                val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@AlarmService, fallbackUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    private fun startVibration(
        pattern: LongArray,
        durationSec: Int,
        wearableNotificationId: Int,
        wearableNotification: Notification
    ) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 穿戴设备持续震动脉冲：每 2.5 秒重发一次高优先级告警通知，确保手环持续响应设定的震动时长
        wearablePulseJob?.cancel()
        wearablePulseJob = serviceScope.launch {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val startTime = System.currentTimeMillis()
            val totalMs = durationSec * 1000L

            while (isActive && (System.currentTimeMillis() - startTime) < totalMs) {
                delay(2500L)
                if (!isActive) break
                try {
                    nm.notify(wearableNotificationId, wearableNotification)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 达到设定震动时长后，自动停止震动与手环脉冲
            stopVibrationOnly()
        }

        // 独立震动停止超时保护
        vibrationStopHandler?.removeCallbacksAndMessages(null)
        vibrationStopHandler = Handler(Looper.getMainLooper()).apply {
            postDelayed({
                stopVibrationOnly()
            }, durationSec * 1000L)
        }
    }

    private fun stopVibrationOnly() {
        wearablePulseJob?.cancel()
        wearablePulseJob = null

        vibrationStopHandler?.removeCallbacksAndMessages(null)
        vibrationStopHandler = null

        try {
            vibrator?.cancel()
            vibrator = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 移除手环告警通知
        if (currentWearableNotificationId != -1) {
            try {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(currentWearableNotificationId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun dismissOverlayInternal() {
        overlayFlashJob?.cancel()
        overlayFlashJob = null

        if (overlayRootView != null && windowManager != null) {
            try {
                windowManager?.removeView(overlayRootView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayRootView = null
            windowManager = null
        }
    }

    private fun stopAlarmInternal() {
        autoStopHandler?.removeCallbacksAndMessages(null)
        autoStopHandler = null

        stopVibrationOnly()
        dismissOverlayInternal()

        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null

        isServiceRunning = false
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        serviceScope.cancel()
        stopAlarmInternal()
    }
}
