package com.example.flashalarm.sleep.model

import java.text.SimpleDateFormat
import java.util.*

/**
 * 每晚睡眠总览会话实体
 */
data class SleepSession(
    val id: Long = 0L,
    val dateString: String,              // 例如 "2026-09-12"
    val startTimestamp: Long,            // 睡前开始监测时间
    val endTimestamp: Long,              // 起床结束时间
    val sleepOnsetTimestamp: Long,       // 确认入睡时间
    val totalDurationMin: Int,           // 在床总时长 (分钟)
    val netSleepDurationMin: Int,        // 实际睡眠净时长 (分钟)
    val deepSleepMin: Int,               // 深睡总分钟
    val lightSleepMin: Int,              // 浅睡总分钟
    val remSleepMin: Int,                // REM 做梦总分钟
    val awakeMin: Int,                   // 清醒总分钟
    val cueTriggerCount: Int,            // 当晚触梦触发次数
    val sleepScore: Int,                 // 综合健康评分 (0~100)
    val avgBreathBpm: Float = 0f,        // 平均呼吸频率
    val turnoverCount: Int = 0,          // 翻身微动次数
    val epochs: List<SleepEpoch> = emptyList()
) {
    val formattedDuration: String
        get() {
            val hours = netSleepDurationMin / 60
            val mins = netSleepDurationMin % 60
            return if (hours > 0) "${hours}小时 ${mins}分" else "${mins}分钟"
        }

    val formattedTotalInBed: String
        get() {
            val hours = totalDurationMin / 60
            val mins = totalDurationMin % 60
            return if (hours > 0) "${hours}小时 ${mins}分" else "${mins}分钟"
        }

    val formattedTimeRange: String
        get() {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return "${sdf.format(Date(startTimestamp))} - ${sdf.format(Date(endTimestamp))}"
        }

    val sleepOnsetLatencyMin: Int
        get() = if (sleepOnsetTimestamp > startTimestamp) {
            ((sleepOnsetTimestamp - startTimestamp) / (60 * 1000L)).toInt()
        } else 0

    val sleepEfficiencyPercent: Int
        get() = if (totalDurationMin > 0) {
            ((netSleepDurationMin.toFloat() / totalDurationMin) * 100).toInt().coerceIn(0, 100)
        } else 0

    val deepPercent: Int
        get() = if (netSleepDurationMin > 0) ((deepSleepMin.toFloat() / netSleepDurationMin) * 100).toInt() else 0

    val lightPercent: Int
        get() = if (netSleepDurationMin > 0) ((lightSleepMin.toFloat() / netSleepDurationMin) * 100).toInt() else 0

    val remPercent: Int
        get() = if (netSleepDurationMin > 0) ((remSleepMin.toFloat() / netSleepDurationMin) * 100).toInt() else 0

    val awakePercent: Int
        get() = if (totalDurationMin > 0) ((awakeMin.toFloat() / totalDurationMin) * 100).toInt() else 0
}
