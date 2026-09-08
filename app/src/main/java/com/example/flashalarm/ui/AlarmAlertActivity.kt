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
import com.example.flashalarm.service.AlarmService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * 闹钟触发时全屏视觉唤醒 Activity
 * 支持：全屏规律闪烁、平滑渐黑过渡、测试模式按总循环时长运行、联动前台服务停止
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
        const val EXTRA_AUTO_DISMISS_SEC = "EXTRA_AUTO_DISMISS_SEC"
        const val EXTRA_IS_PREVIEW_MODE = "EXTRA_IS_PREVIEW_MODE"
        const val EXTRA_IS_VIBRATION_ENABLED = "EXTRA_IS_VIBRATION_ENABLED"
        const val EXTRA_VIBRATION_DURATION_SEC = "EXTRA_VIBRATION_DURATION_SEC"
        const val EXTRA_VIBRATION_PATTERN_ID = "EXTRA_VIBRATION_PATTERN_ID"
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
    private var autoDismissSec: Int = 30
    private var effectiveTotalDurationSec: Int = 30

    private lateinit var rootContainer: FrameLayout
    private lateinit var tvLabel: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvHint: TextView
    private lateinit var tvAutoDismiss: TextView
    private lateinit var infoPanel: LinearLayout

    private var flashJob: Job? = null
    private var autoDismissJob: Job? = null

    // 本地备份播放器（若前台服务未启动则兜底播放）
    private var backupMediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setupLockScreenFlags()
        super.onCreate(savedInstanceState)

        parseParameters()
        buildViewHierarchy()
        hideSystemUI()

        // 如果不是预览模式，且 AlarmService 尚未运行，则启动兜底播放
        if (!isPreviewMode && isSoundEnabled && !AlarmService.isServiceRunning) {
            startBackupAudio()
        }

        // 亮屏控制：整个有效时长内全程规律闪烁，绝不中途常亮
        if (isFlashEnabled) {
            startContinuousFlashingLoop()
        } else {
            rootContainer.setBackgroundColor(Color.BLACK)
            infoPanel.visibility = View.VISIBLE
        }

        // 自动停止计时器
        startAutoDismissTimer()
    }

    override fun onResume() {
        super.onResume()
        AlarmService.dismissOverlay()
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
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
        autoDismissSec = intent.getIntExtra(EXTRA_AUTO_DISMISS_SEC, 30)

        // 闪烁总时长由响铃自动停止时长统一决定，测试模式固定 10 秒
        effectiveTotalDurationSec = if (isPreviewMode) {
            10
        } else {
            autoDismissSec.coerceAtLeast(5)
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
            text = if (isPreviewMode) "效果测试 (10秒)" else alarmLabel
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
            text = if (effectiveTotalDurationSec > 0) {
                if (isPreviewMode) "测试将在 ${effectiveTotalDurationSec} 秒后自动结束"
                else "将在 ${effectiveTotalDurationSec} 秒后自动停止"
            } else ""
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
     * 持续规律闪烁循环（含平滑渐黑）
     * 循环直至自动停止时间到达或用户点击屏幕关闭
     */
    private fun startContinuousFlashingLoop() {
        flashJob = lifecycleScope.launch {
            while (isActive) {
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
                tvAutoDismiss.text = if (isPreviewMode) "测试将在 ${remain} 秒后自动结束"
                                     else "将在 ${remain} 秒后自动停止"
            }
            if (isActive) {
                dismissAlarm(if (isPreviewMode) "测试结束" else "自动停止")
            }
        }
    }

    private fun startBackupAudio() {
        try {
            if (!ringtoneUriStr.isNullOrBlank()) {
                val internalFile = File(ringtoneUriStr!!)
                if (internalFile.exists() && internalFile.length() > 0) {
                    backupMediaPlayer = MediaPlayer().apply {
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
        } catch (e: Exception) {
            e.printStackTrace()
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

        // 联动前台服务：彻底停止音频播放与前台通知
        AlarmService.stopAlarm(this)

        try {
            backupMediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            backupMediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (alarmId != -1L) {
            nm.cancel(alarmId.toInt())
            nm.cancel((alarmId.toInt() and 0x7FFFFFFF) + 88888)
        }

        finishAndRemoveTask()
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissAlarm("onDestroy")
    }
}
