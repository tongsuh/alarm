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

/**
 * 闹钟触发时全屏视觉与声音唤醒 Activity (支持声音/亮屏独立勾选、自定义音频、夜间深红)
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
    }

    // 参数配置
    private var alarmId: Long = -1L
    private var alarmLabel: String = "闹钟"
    private var isSoundEnabled: Boolean = true
    private var isFlashEnabled: Boolean = true
    private var ringtoneUriStr: String? = null
    private var targetColor: Int = Color.parseColor("#FF1A00") // 默认 Apple Watch 夜间深红
    private var targetBrightness: Float = 0.85f
    private var onDurationMs: Long = 1500L
    private var offDurationMs: Long = 1000L
    private var totalDurationCircle: Int = 15
    private var autoDismissSec: Int = 60

    // 控件与任务
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

        // 1. 声音控制：勾选声音才播放
        if (isSoundEnabled) {
            startAudio()
        }
        startVibration()

        // 2. 亮屏控制：勾选亮屏才触发循环闪烁；未勾选则保持深黑静止
        if (isFlashEnabled) {
            startFlashingLoop()
        } else {
            // 纯声音模式下，保持全黑暗视力背景，不闪烁
            rootContainer.setBackgroundColor(Color.BLACK)
            infoPanel.visibility = View.VISIBLE
        }

        // 3. 自动停止倒计时
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
            text = alarmLabel
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
            text = if (autoDismissSec > 0) "将在 ${autoDismissSec} 秒后自动停止" else ""
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 36)
        }

        tvHint = TextView(this).apply {
            text = "轻触屏幕任意位置关闭"
            textSize = 18f
            setTextColor(Color.WHITE.copy(0.7f))
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
     * 协程动态切换背光与夜视深红色
     */
    private fun startFlashingLoop() {
        flashJob = lifecycleScope.launch {
            var cycle = 0
            val isInfinite = totalDurationCircle <= 0

            while (isActive && (isInfinite || cycle < totalDurationCircle)) {
                cycle++

                // ====== 亮状态 (设定颜色 + 目标背光) ======
                applyScreenState(
                    color = targetColor,
                    brightness = targetBrightness,
                    showContent = true
                )
                delay(onDurationMs)

                if (!isActive) break

                // ====== 灭状态 (纯黑 + 0.01f 最低物理背光) ======
                applyScreenState(
                    color = Color.BLACK,
                    brightness = 0.01f,
                    showContent = false
                )
                delay(offDurationMs)
            }

            // 循环结束后保持常亮
            if (isActive) {
                applyScreenState(color = targetColor, brightness = targetBrightness, showContent = true)
                tvHint.text = "轻触屏幕任意位置关闭"
            }
        }
    }

    private fun applyScreenState(color: Int, brightness: Float, showContent: Boolean) {
        rootContainer.setBackgroundColor(color)

        val lp = window.attributes
        lp.screenBrightness = brightness.coerceIn(0.01f, 1.0f)
        window.attributes = lp

        infoPanel.visibility = if (showContent) View.VISIBLE else View.INVISIBLE
    }

    private fun startAutoDismissTimer() {
        if (autoDismissSec <= 0) return
        autoDismissJob = lifecycleScope.launch {
            var remain = autoDismissSec
            while (isActive && remain > 0) {
                delay(1000L)
                remain--
                tvAutoDismiss.text = "将在 ${remain} 秒后自动停止"
            }
            if (isActive) {
                dismissAlarm("超时自动关闭")
            }
        }
    }

    /**
     * 播放自定义或系统音频
     */
    private fun startAudio() {
        try {
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
            // 如果自定义文件失效，安全降级回系统默认铃声
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
