package com.example.flashalarm.sleep

import android.content.Context
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 本机音频播放状态监听器 (白噪音与媒体避让核心)
 *
 * 核心设计：
 * 当检测到手机扬声器/耳机正在播放白噪音、助眠音乐或电台时，
 * 自动向入睡引擎发送信号，触发“麦克风优雅弃权”，将入睡判定 100% 切换为床垫加速度计接管。
 * 待音乐停止播放后无缝唤醒麦克风，避免扬声器自身声音对呼吸拾音造成误判。
 */
class AudioPlaybackMonitor(
    private val context: Context,
    private val onPlaybackStateChanged: ((Boolean) -> Unit)? = null
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())

    private val _isMusicActive = MutableStateFlow(false)
    val isMusicActive: StateFlow<Boolean> = _isMusicActive.asStateFlow()

    private var playbackCallback: Any? = null
    private var isPolling = false

    // 轮询检查任务 (双重保险兜底)
    private val pollRunnable = object : Runnable {
        override fun run() {
            checkCurrentPlayback()
            if (isPolling) {
                handler.postDelayed(this, 3000L)
            }
        }
    }

    init {
        registerCallback()
        checkCurrentPlayback()
    }

    fun startMonitoring() {
        isPolling = true
        handler.post(pollRunnable)
    }

    fun stopMonitoring() {
        isPolling = false
        handler.removeCallbacks(pollRunnable)
        unregisterCallback()
    }

    fun checkCurrentPlayback(): Boolean {
        val active = try {
            audioManager.isMusicActive
        } catch (e: Exception) {
            false
        }
        updateState(active)
        return active
    }

    private fun updateState(isActive: Boolean) {
        if (_isMusicActive.value != isActive) {
            _isMusicActive.value = isActive
            onPlaybackStateChanged?.invoke(isActive)
        }
    }

    private fun registerCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val callback = object : AudioManager.AudioPlaybackCallback() {
                    override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>?) {
                        super.onPlaybackConfigChanged(configs)
                        val isPlaying = configs?.any { config ->
                            config.audioAttributes.usage == android.media.AudioAttributes.USAGE_MEDIA ||
                                    config.audioAttributes.usage == android.media.AudioAttributes.USAGE_GAME ||
                                    config.audioAttributes.usage == android.media.AudioAttributes.USAGE_UNKNOWN
                        } ?: false
                        updateState(isPlaying || audioManager.isMusicActive)
                    }
                }
                audioManager.registerAudioPlaybackCallback(callback, handler)
                playbackCallback = callback
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun unregisterCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            playbackCallback?.let {
                try {
                    audioManager.unregisterAudioPlaybackCallback(it as AudioManager.AudioPlaybackCallback)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            playbackCallback = null
        }
    }
}
