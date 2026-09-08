package com.example.flashalarm.model

import android.graphics.Color
import org.json.JSONObject

/**
 * 亮屏参数模板实体
 * 仅保留闪和灭的时间（单位：秒），去除总循环参数，总闪烁时长由响铃自动停止时长统一决定
 *
 * @property id 唯一 ID
 * @property name 模板名称
 * @property targetColorHex 颜色 Hex (#RRGGBB)
 * @property targetBrightness 目标窗口背光亮度 (0.1f ~ 1.0f)
 * @property onDurationSec 亮状态持续秒数 (例如 1.5 秒、1.0 秒)
 * @property offDurationSec 暗状态持续秒数 (例如 1.0 秒、0.5 秒)
 */
data class FlashProfile(
    val id: String,
    val name: String,
    val targetColorHex: String = "#FF1A00",
    val targetBrightness: Float = 1.0f,
    val onDurationSec: Float = 1.5f,
    val offDurationSec: Float = 1.0f
) {
    val colorInt: Int
        get() = try {
            Color.parseColor(targetColorHex)
        } catch (e: Exception) {
            Color.parseColor("#FF1A00")
        }

    val onDurationMs: Long
        get() = (onDurationSec * 1000L).toLong().coerceAtLeast(100L)

    val offDurationMs: Long
        get() = (offDurationSec * 1000L).toLong().coerceAtLeast(100L)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("targetColorHex", targetColorHex)
        put("targetBrightness", targetBrightness.toDouble())
        put("onDurationSec", onDurationSec.toDouble())
        put("offDurationSec", offDurationSec.toDouble())
    }

    companion object {
        fun fromJson(json: JSONObject): FlashProfile {
            // 兼容旧版本 onDurationMs / offDurationMs
            val onSec = if (json.has("onDurationSec")) {
                json.optDouble("onDurationSec", 1.5).toFloat()
            } else {
                (json.optLong("onDurationMs", 1500L) / 1000.0).toFloat()
            }
            val offSec = if (json.has("offDurationSec")) {
                json.optDouble("offDurationSec", 1.0).toFloat()
            } else {
                (json.optLong("offDurationMs", 1000L) / 1000.0).toFloat()
            }

            return FlashProfile(
                id = json.getString("id"),
                name = json.getString("name"),
                targetColorHex = json.optString("targetColorHex", "#FF1A00"),
                targetBrightness = json.optDouble("targetBrightness", 1.0).toFloat(),
                onDurationSec = onSec.coerceAtLeast(0.1f),
                offDurationSec = offSec.coerceAtLeast(0.1f)
            )
        }

        // Apple Watch 夜间照明专用深红（波长保护夜间暗视力）
        val PRESET_APPLE_WATCH_RED = FlashProfile(
            id = "preset_apple_watch_red",
            name = "Apple Watch 夜间深红",
            targetColorHex = "#FF1A00",
            targetBrightness = 0.85f,
            onDurationSec = 1.5f,
            offDurationSec = 1.0f
        )

        val PRESET_SUNRISE = FlashProfile(
            id = "preset_sunrise",
            name = "暖光日出",
            targetColorHex = "#FFA726", // 暖橙
            targetBrightness = 0.85f,
            onDurationSec = 2.0f,
            offDurationSec = 1.0f
        )

        val PRESET_STROBE = FlashProfile(
            id = "preset_strobe",
            name = "强力白光爆闪",
            targetColorHex = "#FFFFFF", // 冷白
            targetBrightness = 1.0f,
            onDurationSec = 0.5f,
            offDurationSec = 0.5f
        )
    }
}
