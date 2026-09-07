package com.example.flashalarm.model

import android.graphics.Color
import org.json.JSONObject

/**
 * 亮屏参数模板实体
 *
 * @property id 唯一 ID
 * @property name 模板名称 (例如: "暖阳日出", "应急爆闪")
 * @property targetColorHex 颜色 Hex (#RRGGBB)
 * @property targetBrightness 目标窗口背光亮度 (0.1f ~ 1.0f)
 * @property onDurationMs 亮状态持续毫秒数
 * @property offDurationMs 暗状态持续毫秒数
 * @property totalDurationCircle 循环次数 (<= 0 表示无限循环)
 */
data class FlashProfile(
    val id: String,
    val name: String,
    val targetColorHex: String = "#FFFFFF",
    val targetBrightness: Float = 1.0f,
    val onDurationMs: Long = 1500L,
    val offDurationMs: Long = 1000L,
    val totalDurationCircle: Int = 10
) {
    val colorInt: Int
        get() = try {
            Color.parseColor(targetColorHex)
        } catch (e: Exception) {
            Color.WHITE
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("targetColorHex", targetColorHex)
        put("targetBrightness", targetBrightness.toDouble())
        put("onDurationMs", onDurationMs)
        put("offDurationMs", offDurationMs)
        put("totalDurationCircle", totalDurationCircle)
    }

    companion object {
        fun fromJson(json: JSONObject): FlashProfile = FlashProfile(
            id = json.getString("id"),
            name = json.getString("name"),
            targetColorHex = json.optString("targetColorHex", "#FFFFFF"),
            targetBrightness = json.optDouble("targetBrightness", 1.0).toFloat(),
            onDurationMs = json.optLong("onDurationMs", 1500L),
            offDurationMs = json.optLong("offDurationMs", 1000L),
            totalDurationCircle = json.optInt("totalDurationCircle", 10)
        )

        val PRESET_SUNRISE = FlashProfile(
            id = "preset_sunrise",
            name = "暖光日出",
            targetColorHex = "#FFA726", // 暖橙
            targetBrightness = 0.85f,
            onDurationMs = 2500L,
            offDurationMs = 1200L,
            totalDurationCircle = 15
        )

        val PRESET_STROBE = FlashProfile(
            id = "preset_strobe",
            name = "强力白光爆闪",
            targetColorHex = "#FFFFFF", // 冷白
            targetBrightness = 1.0f,
            onDurationMs = 600L,
            offDurationMs = 400L,
            totalDurationCircle = 25
        )

        val PRESET_GENTLE_CYAN = FlashProfile(
            id = "preset_cyan",
            name = "柔和青蓝",
            targetColorHex = "#00E5FF", // 青蓝
            targetBrightness = 0.6f,
            onDurationMs = 2000L,
            offDurationMs = 1500L,
            totalDurationCircle = 12
        )
    }
}
