package com.example.flashalarm.sleep.model

import androidx.compose.ui.graphics.Color

/**
 * 国际标准四阶段睡眠分期枚举
 */
enum class SleepStage(val code: Int, val label: String, val colorHex: Long) {
    AWAKE(0, "清醒", 0xFF636366),     // 暗灰暖白
    REM(1, "快速眼动", 0xFF9B51E0),    // 梦境雅紫 (与触梦联动)
    LIGHT(2, "浅睡", 0xFF3A6073),     // 晨雾灰蓝
    DEEP(3, "深睡", 0xFF1B2A4A);     // 深谧幽蓝

    val composeColor: Color
        get() = Color(colorHex)

    companion object {
        fun fromCode(code: Int): SleepStage {
            return entries.find { it.code == code } ?: LIGHT
        }
    }
}
