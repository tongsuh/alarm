package com.example.flashalarm.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import androidx.core.app.NotificationCompat
import com.example.flashalarm.FlashAlarmApp
import com.example.flashalarm.ui.AlarmAlertActivity
import java.io.File

/**
 * 核心前台守护与音频服务 (Foreground Service)
 * 彻底解决“使用其他应用时闹钟不响、只有打开本应用才响”的底层根因：
 * 1. 即使退到后台被系统限制拉起 Activity，音频和震动也由前台服务在到点那一刻立刻全音量轰鸣播放！
 * 2. 持有硬件级 PARTIAL_WAKE_LOCK，防止 CPU 在后台休眠。
 * 3. 发送高优先级全屏通知，强力唤醒屏幕。
 */
class AlarmService : Service() {

    companion object {
        const val ACTION_START_ALARM = "ACTION_START_ALARM"
        const val ACTION_STOP_ALARM = "ACTION_STOP_ALARM"

        var isServiceRunning = false
            private set

        fun stopAlarm(context: Context) {
            val stopIntent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_STOP_ALARM
            }
            context.startService(stopIntent)
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var autoStopHandler: Handler? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_STOP_ALARM) {
            stopAlarmInternal()
            return START_NOT_STICKY
        }

        isServiceRunning = true

        // 1. 获取 WakeLock，锁定 CPU 不休眠
        acquireWakeLock()

        // 2. 解析参数
        val alarmId = intent.getLongExtra(AlarmAlertActivity.EXTRA_ALARM_ID, -1L)
        val alarmLabel = intent.getStringExtra(AlarmAlertActivity.EXTRA_ALARM_LABEL) ?: "闹钟"
        val isSoundEnabled = intent.getBooleanExtra(AlarmAlertActivity.EXTRA_IS_SOUND_ENABLED, true)
        val isFlashEnabled = intent.getBooleanExtra(AlarmAlertActivity.EXTRA_IS_FLASH_ENABLED, true)
        val ringtoneUriStr = intent.getStringExtra(AlarmAlertActivity.EXTRA_RINGTONE_URI)
        val targetColorHex = intent.getStringExtra(AlarmAlertActivity.EXTRA_TARGET_COLOR_HEX) ?: "#FF1A00"
        val targetBrightness = intent.getFloatExtra(AlarmAlertActivity.EXTRA_TARGET_BRIGHTNESS, 0.85f)
        val onDurationMs = intent.getLongExtra(AlarmAlertActivity.EXTRA_ON_DURATION_MS, 1500L)
        val offDurationMs = intent.getLongExtra(AlarmAlertActivity.EXTRA_OFF_DURATION_MS, 1000L)
        val totalDurationCircle = intent.getIntExtra(AlarmAlertActivity.EXTRA_TOTAL_DURATION_CIRCLE, 15)
        val autoDismissSec = intent.getIntExtra(AlarmAlertActivity.EXTRA_AUTO_DISMISS_SEC, 60)

        // 3. 构建点击与全屏 Intent
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
            putExtra(AlarmAlertActivity.EXTRA_TOTAL_DURATION_CIRCLE, totalDurationCircle)
            putExtra(AlarmAlertActivity.EXTRA_AUTO_DISMISS_SEC, autoDismissSec)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            alarmId.toInt(),
            alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 停止通知 PendingIntent
        val stopServiceIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            alarmId.toInt() + 1000,
            stopServiceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 4. 构建前台通知并立即以高优先级常驻前台
        val notification = NotificationCompat.Builder(this, FlashAlarmApp.ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(alarmLabel)
            .setContentText("正在响铃，点击进入全屏或点击关闭")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭闹钟", stopPendingIntent)
            .setOngoing(true)
            .build()

        startForeground(alarmId.toInt().coerceAtLeast(1), notification)

        // 5. 由前台服务直接播放音频与震动 (即使 Activity 还在后台被限制，声音也立即炸响)
        if (isSoundEnabled) {
            playRingtone(ringtoneUriStr)
        }
        startVibration()

        // 6. 强力拉起全屏界面
        try {
            startActivity(alertIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 7. 超时自动停止保护
        if (autoDismissSec > 0) {
            autoStopHandler = Handler(Looper.getMainLooper()).apply {
                postDelayed({
                    stopAlarmInternal()
                }, autoDismissSec * 1000L)
            }
        }

        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "FlashAlarm:AlarmServiceWakeLock"
            )
            wakeLock?.acquire(15 * 60 * 1000L) // 最多持有 15 分钟
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

            val uri = if (!path.isNullOrBlank()) Uri.parse(path)
            else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

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

    private fun stopAlarmInternal() {
        autoStopHandler?.removeCallbacksAndMessages(null)
        autoStopHandler = null

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
        stopAlarmInternal()
    }
}
