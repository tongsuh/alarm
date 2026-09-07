package com.example.flashalarm.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 闹钟实体类
 *
 * @property id 唯一标识符
 * @property hour 触发小时 (0-23)
 * @property minute 触发分钟 (0-59)
 * @property label 闹钟标签/备注
 * @property isEnabled 是否开启
 * @property repeatDays 重复星期几 (1..7 对应 周一至周日；空集合表示单次)
 * @property autoDismissSec 达到指定时长后自动关闭 (秒，默认 60 秒)
 * @property flashProfileId 绑定的亮屏闪烁模板 ID
 */
data class AlarmItem(
    val id: Long = System.currentTimeMillis(),
    val hour: Int,
    val minute: Int,
    val label: String = "起床闹钟",
    val isEnabled: Boolean = true,
    val repeatDays: Set<Int> = emptySet(),
    val autoDismissSec: Int = 60,
    val flashProfileId: String = FlashProfile.PRESET_SUNRISE.id
) {
    val formattedTime: String
        get() = String.format("%02d:%02d", hour, minute)

    val repeatDaysSummary: String
        get() {
            if (repeatDays.isEmpty()) return "仅响铃一次"
            if (repeatDays.size == 7) return "每天"
            if (repeatDays == setOf(1, 2, 3, 4, 5)) return "工作日 (周一至周五)"
            if (repeatDays == setOf(6, 7)) return "周末 (周六日)"
            val dayNames = mapOf(
                1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四",
                5 to "周五", 6 to "周六", 7 to "周日"
            )
            return repeatDays.sorted().joinToString(" ") { dayNames[it] ?: "" }
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("hour", hour)
        put("minute", minute)
        put("label", label)
        put("isEnabled", isEnabled)
        put("autoDismissSec", autoDismissSec)
        put("flashProfileId", flashProfileId)
        val daysArray = JSONArray()
        repeatDays.forEach { daysArray.put(it) }
        put("repeatDays", daysArray)
    }

    companion object {
        fun fromJson(json: JSONObject): AlarmItem {
            val days = mutableSetOf<Int>()
            val daysArray = json.optJSONArray("repeatDays")
            if (daysArray != null) {
                for (i in 0 until daysArray.length()) {
                    days.add(daysArray.getInt(i))
                }
            }
            return AlarmItem(
                id = json.getLong("id"),
                hour = json.getInt("hour"),
                minute = json.getInt("minute"),
                label = json.optString("label", "闹钟"),
                isEnabled = json.optBoolean("isEnabled", true),
                repeatDays = days,
                autoDismissSec = json.optInt("autoDismissSec", 60),
                flashProfileId = json.optString("flashProfileId", FlashProfile.PRESET_SUNRISE.id)
            )
        }
    }
}
