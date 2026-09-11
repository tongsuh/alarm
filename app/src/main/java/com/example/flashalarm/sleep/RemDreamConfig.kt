package com.example.flashalarm.sleep

import android.content.Context
import android.content.SharedPreferences

/**
 * 后半夜 REM 清醒梦触梦模式配置实体
 */
data class RemDreamConfig(
    val isEnabled: Boolean = false,
    val cycleMode: String = "both",             // "cycle_4_5h", "cycle_6_0h", "both"
    val flashColorHex: String = "#990000",      // 暗红微光 (不抑制褪黑素，透过眼皮刺激视网膜)
    val flashBrightness: Float = 0.25f,         // 15%~40% 亮度，不惊醒肉体
    val isWhisperEnabled: Boolean = true,
    val whisperVolume: Float = 0.25f,           // 15%~40% 音量，耳边低语
    val isVibrationEnabled: Boolean = true,
    val vibrationPatternId: String = "heartbeat", // 心跳节拍微震
    val cueDurationSec: Int = 25                // 触梦持续时长 (秒)，结束后自动归于静默
)

/**
 * REM 清醒梦设置持久化仓库
 */
class RemDreamRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("rem_dream_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_ENABLED = "rem_is_enabled"
        private const val KEY_CYCLE_MODE = "rem_cycle_mode"
        private const val KEY_FLASH_COLOR = "rem_flash_color"
        private const val KEY_FLASH_BRIGHTNESS = "rem_flash_brightness"
        private const val KEY_IS_WHISPER_ENABLED = "rem_is_whisper_enabled"
        private const val KEY_WHISPER_VOLUME = "rem_whisper_volume"
        private const val KEY_IS_VIBRATION_ENABLED = "rem_is_vibration_enabled"
        private const val KEY_VIBRATION_PATTERN_ID = "rem_vibration_pattern_id"
        private const val KEY_CUE_DURATION_SEC = "rem_cue_duration_sec"
    }

    fun getConfig(): RemDreamConfig {
        return RemDreamConfig(
            isEnabled = prefs.getBoolean(KEY_IS_ENABLED, false),
            cycleMode = prefs.getString(KEY_CYCLE_MODE, "both") ?: "both",
            flashColorHex = prefs.getString(KEY_FLASH_COLOR, "#990000") ?: "#990000",
            flashBrightness = prefs.getFloat(KEY_FLASH_BRIGHTNESS, 0.25f),
            isWhisperEnabled = prefs.getBoolean(KEY_IS_WHISPER_ENABLED, true),
            whisperVolume = prefs.getFloat(KEY_WHISPER_VOLUME, 0.25f),
            isVibrationEnabled = prefs.getBoolean(KEY_IS_VIBRATION_ENABLED, true),
            vibrationPatternId = prefs.getString(KEY_VIBRATION_PATTERN_ID, "heartbeat") ?: "heartbeat",
            cueDurationSec = prefs.getInt(KEY_CUE_DURATION_SEC, 25)
        )
    }

    fun saveConfig(config: RemDreamConfig) {
        prefs.edit().apply {
            putBoolean(KEY_IS_ENABLED, config.isEnabled)
            putString(KEY_CYCLE_MODE, config.cycleMode)
            putString(KEY_FLASH_COLOR, config.flashColorHex)
            putFloat(KEY_FLASH_BRIGHTNESS, config.flashBrightness)
            putBoolean(KEY_IS_WHISPER_ENABLED, config.isWhisperEnabled)
            putFloat(KEY_WHISPER_VOLUME, config.whisperVolume)
            putBoolean(KEY_IS_VIBRATION_ENABLED, config.isVibrationEnabled)
            putString(KEY_VIBRATION_PATTERN_ID, config.vibrationPatternId)
            putInt(KEY_CUE_DURATION_SEC, config.cueDurationSec)
            apply()
        }
    }
}
