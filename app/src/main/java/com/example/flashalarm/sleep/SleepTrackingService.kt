package com.example.flashalarm.sleep

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.flashalarm.FlashAlarmApp
import com.example.flashalarm.R
import com.example.flashalarm.model.VibrationPatternType
import com.example.flashalarm.ui.MainActivity
import com.example.flashalarm.ui.SleepModeActivity
import com.example.flashalarm.util.VibrationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * 智能就寝与后半夜清醒梦触梦前台守护服务 (Foreground Service)
 *
 * 核心特性：
 * 1. 纯净低功耗前台守护：保证整夜锁屏状态下 CPU 低频感知，电量损耗控制在 3%~5%。
 * 2. 状态栏极简徽章通知：实时展示“校准中 / 正在监测 / 已沉睡 / 黄金梦境搜索带”。
 * 3. 三合一温和触梦引擎：
 *    - 暗红全屏呼吸遮罩 (15%~30% 亮度，不惊醒肉体)
 *    - 柔和耳边低语 (25% 音量，循环提示现实检验)
 *    - 手环心跳微震 (宏节奏 Heartbeat 弱双击)
 *    - 25秒后全自动静默退出，无需用户手动操作关闹钟。
 */
class SleepTrackingService : Service() {

    companion object {
        const val ACTION_START_TRACKING = "ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "ACTION_STOP_TRACKING"
        const val ACTION_SIMULATE_CUE = "ACTION_SIMULATE_CUE"
        const val ACTION_DISMISS_CUE = "ACTION_DISMISS_CUE"

        private const val NOTIFICATION_ID = 77001
        private const val CUE_WEARABLE_NOTIFY_ID = 77002

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _isSimulatingCue = MutableStateFlow(false)
        val isSimulatingCue: StateFlow<Boolean> = _isSimulatingCue.asStateFlow()

        private val _liveStatusText = MutableStateFlow("未开启")
        val liveStatusText: StateFlow<String> = _liveStatusText.asStateFlow()

        private val _liveDetailText = MutableStateFlow("点击开始今夜监测")
        val liveDetailText: StateFlow<String> = _liveDetailText.asStateFlow()

        private val _isPhoneFlat = MutableStateFlow(true)
        val isPhoneFlat: StateFlow<Boolean> = _isPhoneFlat.asStateFlow()

        private val _isWhiteNoiseActive = MutableStateFlow(false)
        val isWhiteNoiseActive: StateFlow<Boolean> = _isWhiteNoiseActive.asStateFlow()

        private val _phoneTiltAngle = MutableStateFlow(0f)
        val phoneTiltAngle: StateFlow<Float> = _phoneTiltAngle.asStateFlow()

        fun startTracking(context: Context) {
            _isServiceRunning.value = true
            val intent = Intent(context, SleepTrackingService::class.java).apply {
                action = ACTION_START_TRACKING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopTracking(context: Context) {
            _isServiceRunning.value = false
            val intent = Intent(context, SleepTrackingService::class.java).apply {
                action = ACTION_STOP_TRACKING
            }
            context.startService(intent)
        }

        fun simulateCue(context: Context) {
            val intent = Intent(context, SleepTrackingService::class.java).apply {
                action = ACTION_SIMULATE_CUE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopSimulateCue(context: Context) {
            val intent = Intent(context, SleepTrackingService::class.java).apply {
                action = ACTION_DISMISS_CUE
            }
            context.startService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var sleepEngine: SmartSleepEngine? = null
    private lateinit var configRepo: RemDreamRepository
    private lateinit var sleepRecordRepo: com.example.flashalarm.sleep.data.SleepRecordRepository
    private var serviceJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 触梦执行资源
    private var mediaPlayer: MediaPlayer? = null
    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var cueJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        configRepo = RemDreamRepository(this)
        sleepRecordRepo = com.example.flashalarm.sleep.data.SleepRecordRepository(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_TRACKING -> {
                stopTrackingInternal()
                return START_NOT_STICKY
            }
            ACTION_DISMISS_CUE -> {
                cueJob?.cancel()
                cueJob = null
                stopCueExecution()
                _isSimulatingCue.value = false
                if (!_isServiceRunning.value) {
                    stopForeground(true)
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            ACTION_SIMULATE_CUE -> {
                _isSimulatingCue.value = true
                val notification = buildKeepaliveNotification("触梦模拟试听中 · 轻触屏幕任意位置可立即退出")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                executeGentleCue("模拟试听体验", 15)
                return START_NOT_STICKY
            }
            ACTION_START_TRACKING, null -> {
                startTrackingInternal()
            }
        }
        return START_STICKY
    }

    private fun startTrackingInternal() {
        if (sleepEngine != null) return
        _isServiceRunning.value = true

        acquireWakeLock()
        val notification = buildKeepaliveNotification("就寝放置中 · 正在准备校准...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val config = configRepo.getConfig()
        sleepEngine = SmartSleepEngine(
            context = this,
            config = config,
            onTriggerRemCue = { cycleReason ->
                executeGentleCue(cycleReason, config.cueDurationSec)
            },
            onEpochGenerated = { epoch ->
                serviceScope.launch(Dispatchers.IO) {
                    sleepRecordRepo.insertEpochs(listOf(epoch))
                }
            }
        )
        sleepEngine?.startEngine()

        // 监听状态流并更新状态栏通知与 UI
        serviceJob?.cancel()
        serviceJob = serviceScope.launch {
            launch {
                sleepEngine?.engineState?.collect { state ->
                    _liveStatusText.value = state.displayText
                    updateNotification()
                }
            }
            launch {
                sleepEngine?.statusDetail?.collect { detail ->
                    _liveDetailText.value = detail
                    updateNotification()
                }
            }
            launch {
                sleepEngine?.isPhoneFlat?.collect { _isPhoneFlat.value = it }
            }
            launch {
                sleepEngine?.isWhiteNoiseActive?.collect { _isWhiteNoiseActive.value = it }
            }
            launch {
                sleepEngine?.phoneTiltAngle?.collect { _phoneTiltAngle.value = it }
            }
        }
    }

    private fun stopTrackingInternal() {
        cueJob?.cancel()
        cueJob = null
        stopCueExecution()

        val finalSession = sleepEngine?.stopEngine()
        sleepEngine = null

        if (finalSession != null) {
            serviceScope.launch(Dispatchers.IO) {
                sleepRecordRepo.saveSession(finalSession)
            }
        }

        serviceJob?.cancel()
        serviceJob = null

        releaseWakeLock()
        _isServiceRunning.value = false
        _liveStatusText.value = "已停止"
        _liveDetailText.value = "点击开始今夜监测"

        stopForeground(true)
        stopSelf()
    }

    /**
     * 执行三合一温和触梦提醒 (屏幕暗红呼吸 + 耳边轻语 + 手环微震)
     */
    private fun executeGentleCue(reason: String, durationSec: Int) {
        cueJob?.cancel()
        val config = configRepo.getConfig()

        cueJob = serviceScope.launch {
            try {
                // 1. 启动全屏暗红呼吸微光 (通过 WindowManager 悬浮遮罩)
                startOverlayBreathing(config.flashColorHex, config.flashBrightness)

                // 2. 播放耳边低语 (20%~30% 低音量)
                if (config.isWhisperEnabled) {
                    startWhisperAudio(config.whisperVolume)
                }

                // 3. 联动智能手环心跳微震 (Macro Cadence Heartbeat 节拍)
                if (config.isVibrationEnabled) {
                    startWearableVibrationPulse(config.vibrationPatternId, reason, durationSec)
                }

                // 4. 关键修复：持续执行 durationSec 秒，随后全自动静默退出！
                delay(durationSec * 1000L)
            } finally {
                stopCueExecution()
                _isSimulatingCue.value = false
                if (!_isServiceRunning.value) {
                    stopForeground(true)
                    stopSelf()
                }
            }
        }
    }

    /**
     * 屏幕暗红慢速呼吸微光 (15%~30% 亮度，不惊醒肉体)
     */
    private fun startOverlayBreathing(colorHex: String, targetBrightness: Float) {
        if (!Settings.canDrawOverlays(this)) return

        val baseColor = try {
            Color.parseColor(colorHex)
        } catch (e: Exception) {
            Color.parseColor("#990000")
        }

        serviceScope.launch(Dispatchers.Main) {
            if (overlayView == null) {
                val layoutParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else
                        @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.FILL
                    screenBrightness = targetBrightness
                }

                val createTime = System.currentTimeMillis()
                val frame = FrameLayout(this@SleepTrackingService).apply {
                    setBackgroundColor(baseColor)
                    isClickable = true
                    isFocusable = true

                    // 轻触屏幕任意位置立即退出 (加 300ms 保护，避免点击触发按钮的手指误直接触发退出)
                    setOnClickListener {
                        if (System.currentTimeMillis() - createTime > 300L) {
                            cueJob?.cancel()
                            cueJob = null
                            stopCueExecution()
                            _isSimulatingCue.value = false
                            if (!_isServiceRunning.value) {
                                stopForeground(true)
                                stopSelf()
                            }
                        }
                    }
                    setOnTouchListener { _, event ->
                        if (System.currentTimeMillis() - createTime > 300L && event.action == MotionEvent.ACTION_UP) {
                            cueJob?.cancel()
                            cueJob = null
                            stopCueExecution()
                            _isSimulatingCue.value = false
                            if (!_isServiceRunning.value) {
                                stopForeground(true)
                                stopSelf()
                            }
                            true
                        } else {
                            false
                        }
                    }

                    val tv = TextView(this@SleepTrackingService).apply {
                        text = "✨ 正在做梦吗？看下手表时间..."
                        setTextColor(Color.argb(180, 255, 255, 255))
                        textSize = 18f
                        gravity = Gravity.CENTER
                    }
                    addView(tv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

                    // 底部醒目半透明退出提示胶囊
                    val exitBadge = TextView(this@SleepTrackingService).apply {
                        text = "✕ 轻触屏幕任意位置立即退出"
                        setTextColor(Color.argb(240, 255, 159, 10))
                        textSize = 14f
                        gravity = Gravity.CENTER
                        setPadding(40, 20, 40, 20)
                        setBackgroundColor(Color.argb(190, 18, 18, 22))
                    }
                    val badgeParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    ).apply {
                        bottomMargin = 180
                    }
                    addView(exitBadge, badgeParams)
                }
                overlayView = frame
                try {
                    windowManager?.addView(frame, layoutParams)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * 柔和耳边低语 (播放内置自定义语音)
     */
    private fun startWhisperAudio(volume: Float) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                val afd = resources.openRawResourceFd(R.raw.alarm_custom)
                if (afd != null) {
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                    prepare()
                    setVolume(volume, volume)
                    isLooping = true
                    start()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 降级直接创建播放
            try {
                mediaPlayer = MediaPlayer.create(this, R.raw.alarm_custom)?.apply {
                    setVolume(volume, volume)
                    isLooping = true
                    start()
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    /**
     * 触发手环心跳微震
     */
    private fun startWearableVibrationPulse(patternId: String, reason: String, durationSec: Int) {
        val patternType = VibrationPatternType.fromId(patternId)
        VibrationHelper.playPreview(this, patternType, durationMs = durationSec * 1000L)
    }

    private fun stopCueExecution() {
        // 1. 关闭悬浮微光
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }

        // 2. 停止音频
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. 停止手环震动
        VibrationHelper.stopPreview(this)
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FlashAlarm:SleepTrackingLock").apply {
                acquire(10 * 3600 * 1000L) // 10小时最长保底释放
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildKeepaliveNotification(_liveDetailText.value))
    }

    private fun buildKeepaliveNotification(detail: String): Notification {
        val intent = Intent(this, SleepModeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, FlashAlarmApp.SERVICE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("💤 清醒梦感知守护中 · ${_liveStatusText.value}")
            .setContentText(detail)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTrackingInternal()
    }
}
