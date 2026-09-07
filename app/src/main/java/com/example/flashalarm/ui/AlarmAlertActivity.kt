package com.example.flashalarm.ui

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * 闹钟触发时全屏视觉与声音唤醒 Activity
 * 解决需求：持续规律闪烁与声音同步对齐、平滑渐黑、长效音频播放无权限丢失
 */
class AlarmAlertActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ALARM_ID = "EXTRA_ALARM_ID"
        const val EXTRA_ALARM_LABEL = "EXTRA_ALARM_LABEL"
        const val EXTRA_IS_SOUND_ENABLED = "EXTRA_IS_SOUND_ENABLED"
        const val EXTRA_IS_FLASH_ENABLED = "EXTRA_IS_FLASH_ENABLED"
        const val EXTRA_RINGTONE_URI = "EXTRA_RINGTONE_URI"
        const val EXTRA_TARGET_COLOR_HEX = "EXTRA_TARGET_COLOR_HEX"
        const val EXTRA_TARGET_BRIGHTNESS = "EXTRA_TARGET_BRIGHTNESS"
        const val EXTRA_ON_DURATION_MS = "EXTRA_ON_DURATION_MS"
        const val EXTRA_OFF_DURATION_MS = "EXTRA_OFF_DURATION_MS"
        const val EXTRA_TOTAL_DURATION_CIRCLE = "EXTRA_TOTAL_DURATION_CIRCLE"
        const val EXTRA_AUTO_DISMISS_SEC = "EXTRA_AUTO_DISMISS_SEC"
        const val EXTRA_IS_PREVIEW_MODE = "EXTRA_IS_PREVIEW_MODE"
    }

    private var alarmId: Long = -1L
    private var alarmLabel: String = "闹钟"
    private var isSoundEnabled: Boolean = true
    private var isFlashEnabled: Boolean = true
    private var isPreviewMode: Boolean = false
    private var ringtoneUriStr: String? = null
    private var targetColor: Int = Color.parseColor("#FF1A00")
    private var targetBrightness: Float = 0.85f
    private var onDurationMs: Long = 1500L
    private var offDurationMs: Long = 1000L
    private var totalDurationCircle: Int = 15
    private var autoDismissSec: Int = 60
    private var effectiveTotalDurationSec: Int = 60

    private lateinit var rootContainer: FrameLayout
    private lateinit var tvLabel: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvHint: TextView
    private lateinit var tvAutoDismiss: TextView
    private lateinit var infoPanel: LinearLayout

    private var flashJob: Job? = null
    private var autoDismissJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setupLockScreenFlags()
        super.onCreate(savedInstanceState)

        parseParameters()
        buildViewHierarchy()
        hideSystemUI()

        // 1. 声音控制
        if (isSoundEnabled && !isPreviewMode) {
            startAudio()
            startVibration()
        }

        // 2. 亮屏控制：整个响铃生命周期内全程持续规律闪烁，绝不会中途停闪常亮
        if (isFlashEnabled) {
            startContinuousFlashingLoop()
        } else {
            rootContainer.setBackgroundColor(Color.BLACK)
            infoPanel.visibility = View.VISIBLE
        }

        // 3. 自动停止倒计时 (以音频与闪烁两者的最大时长为准)
        startAutoDismissTimer()
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun parseParameters() {
        alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        alarmLabel = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: "闹钟"
        isSoundEnabled = intent.getBooleanExtra(EXTRA_IS_SOUND_ENABLED, true)
        isFlashEnabled = intent.getBooleanExtra(EXTRA_IS_FLASH_ENABLED, true)
        isPreviewMode = intent.getBooleanExtra(EXTRA_IS_PREVIEW_MODE, false)
        ringtoneUriStr = intent.getStringExtra(EXTRA_RINGTONE_URI)

        val hex = intent.getStringExtra(EXTRA_TARGET_COLOR_HEX) ?: "#FF1A00"
        targetColor = try {
            Color.parseColor(hex)
        } catch (e: Exception) {
            Color.parseColor("#FF1A00")
        }

        targetBrightness = intent.getFloatExtra(EXTRA_TARGET_BRIGHTNESS, 0.85f).coerceIn(0.1f, 1.0f)
        onDurationMs = intent.getLongExtra(EXTRA_ON_DURATION_MS, 1500L).coerceAtLeast(100L)
        offDurationMs = intent.getLongExtra(EXTRA_OFF_DURATION_MS, 1000L).coerceAtLeast(100L)
        totalDurationCircle = intent.getIntExtra(EXTRA_TOTAL_DURATION_CIRCLE, 15)
        autoDismissSec = intent.getIntExtra(EXTRA_AUTO_DISMISS_SEC, 60)

        // 关键改动 (特性 1): 计算有效总时长，以闪烁总时长和音频自动停止时长中较长的一个为准
        val flashCycleTotalSec = if (totalDurationCircle > 0) {
            ((totalDurationCircle * (onDurationMs + offDurationMs)) / 1000L).toInt()
        } else {
            autoDismissSec
        }

        effectiveTotalDurationSec = if (isPreviewMode) {
            ((onDurationMs + offDurationMs + 500L) / 1000L).toInt().coerceAtLeast(3)
        } else if (isSoundEnabled && isFlashEnabled) {
            maxOf(autoDismissSec, flashCycleTotalSec)
        } else if (isFlashEnabled) {
            maxOf(autoDismissSec, flashCycleTotalSec)
        } else {
            autoDismissSec
        }
    }

    private fun buildViewHierarchy() {
        rootContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            setOnClickListener {
                dismissAlarm("轻触屏幕关闭")
            }
        }

        infoPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }

        tvLabel = TextView(this).apply {
            text = if (isPreviewMode) "效果测试预览" else alarmLabel
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        tvTime = TextView(this).apply {
            val now = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            text = now
            textSize = 72f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 12)
        }

        tvAutoDismiss = TextView(this).apply {
            text = if (effectiveTotalDurationSec > 0 && !isPreviewMode) "将在 ${effectiveTotalDurationSec} 秒后自动停止" else ""
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 36)
        }

        tvHint = TextView(this).apply {
            text = "轻触屏幕任意位置关闭"
            textSize = 18f
            setTextColor(Color.argb(180, 255, 255, 255))
            gravity = Gravity.CENTER
        }

        infoPanel.addView(tvLabel)
        infoPanel.addView(tvTime)
        infoPanel.addView(tvAutoDismiss)
        infoPanel.addView(tvHint)

        rootContainer.addView(infoPanel)
        setContentView(rootContainer)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            dismissAlarm("轻触屏幕关闭")
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    /**
     * 全程持续规律闪烁协程（特性 1: 不会在音频未结束前中途停闪常亮，全程循环闪烁到底）
     */
    private fun startContinuousFlashingLoop() {
        flashJob = lifecycleScope.launch {
            var cycle = 0
            val maxCycles = if (isPreviewMode) 1 else Int.MAX_VALUE

            while (isActive && cycle < maxCycles) {
                cycle++

                // ====== 亮状态 ======
                applyScreenState(
                    color = targetColor,
                    brightness = targetBrightness,
                    contentAlpha = 1f
                )
                delay(onDurationMs)

                if (!isActive) break

                // ====== 渐黑过渡 ======
                val transitionDuration = 450L.coerceAtMost(onDurationMs / 2).coerceAtLeast(150L)
                smoothFadeToBlack(durationMs = transitionDuration)

                if (!isActive) break

                // ====== 灭状态保持 ======
                delay(offDurationMs)
            }

            if (isActive && isPreviewMode) {
                dismissAlarm("预览结束")
            }
        }
    }

    /**
     * 平滑渐黑
     */
    private suspend fun smoothFadeToBlack(durationMs: Long) {
        val steps = 18
        val stepInterval = durationMs / steps

        val r = Color.red(targetColor)
        val g = Color.green(targetColor)
        val b = Color.blue(targetColor)
        val startBrightness = targetBrightness
        val endBrightness = 0.01f

        for (i in 1..steps) {
            if (!lifecycleScope.coroutineContext.isActive) break
            val fraction = i.toFloat() / steps
            val factor = 1f - (fraction * fraction)

            val currentR = (r * factor).toInt().coerceIn(0, 255)
            val currentG = (g * factor).toInt().coerceIn(0, 255)
            val currentB = (b * factor).toInt().coerceIn(0, 255)
            val currentColor = Color.rgb(currentR, currentG, currentB)
            val currentBrightness = endBrightness + (startBrightness - endBrightness) * factor

            applyScreenState(
                color = currentColor,
                brightness = currentBrightness,
                contentAlpha = factor
            )
            delay(stepInterval)
        }

        applyScreenState(Color.BLACK, endBrightness, contentAlpha = 0f)
    }

    private fun applyScreenState(color: Int, brightness: Float, contentAlpha: Float) {
        rootContainer.setBackgroundColor(color)

        val lp = window.attributes
        lp.screenBrightness = brightness.coerceIn(0.01f, 1.0f)
        window.attributes = lp

        infoPanel.alpha = contentAlpha
        infoPanel.visibility = if (contentAlpha > 0.05f) View.VISIBLE else View.INVISIBLE
    }

    private fun startAutoDismissTimer() {
        if (effectiveTotalDurationSec <= 0) return
        autoDismissJob = lifecycleScope.launch {
            var remain = effectiveTotalDurationSec
            while (isActive && remain > 0) {
                delay(1000L)
                remain--
                tvAutoDismiss.text = "将在 ${remain} 秒后自动停止"
            }
            if (isActive) {
                dismissAlarm("倒计时结束自动关闭")
            }
        }
    }

    /**
     * 播放自定义或系统音频 (特性 4: 优先读取内部存储私有持久化音乐文件，彻底杜绝权限丢失)
     */
    private fun startAudio() {
        try {
            if (!ringtoneUriStr.isNullOrBlank()) {
                val internalFile = File(ringtoneUriStr!!)
                if (internalFile.exists() && internalFile.length() > 0) {
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(internalFile.absolutePath)
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

            val audioUri = if (!ringtoneUriStr.isNullOrBlank()) {
                Uri.parse(ringtoneUriStr)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmAlertActivity, audioUri)
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
                    setDataSource(this@AlarmAlertActivity, fallbackUri)
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

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 500), 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 800, 500), 0)
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun dismissAlarm(reason: String) {
        flashJob?.cancel()
        flashJob = null
        autoDismissJob?.cancel()
        autoDismissJob = null

        val lp = window.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp

        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        vibrator?.cancel()
        vibrator = null

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (alarmId != -1L) {
            nm.cancel(alarmId.toInt())
        }

        finishAndRemoveTask()
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissAlarm("onDestroy")
    }
}
