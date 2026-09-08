package com.example.flashalarm.model

/**
 * 常见震动提醒类型 (供手机硬件及华为手环等穿戴设备震动)
 */
enum class VibrationPatternType(
    val id: String,
    val title: String,
    val description: String,
    val pattern: LongArray
) {
    STRONG_ALARM(
        id = "strong",
        title = "持续强震 (警报)",
        description = "长震1000ms/停300ms，最强穿透力与唤醒感",
        pattern = longArrayOf(0, 1000, 300)
    ),
    RAPID_PULSE(
        id = "pulse",
        title = "紧促脉冲 (急促)",
        description = "短震200ms/停200ms，高频快节奏蜂鸣",
        pattern = longArrayOf(0, 200, 200)
    ),
    HEARTBEAT(
        id = "heartbeat",
        title = "心跳双击 (节拍)",
        description = "双击心跳节奏 (150ms/150ms/350ms/600ms)",
        pattern = longArrayOf(0, 150, 150, 350, 600)
    ),
    WAVE(
        id = "wave",
        title = "渐强波浪 (起伏)",
        description = "短震与长震交替起伏 (300ms/200ms/600ms/300ms)",
        pattern = longArrayOf(0, 300, 200, 600, 300)
    );

    companion object {
        fun fromId(id: String): VibrationPatternType =
            values().firstOrNull { it.id == id } ?: STRONG_ALARM
    }
}
