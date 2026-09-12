package com.example.flashalarm.sleep.model

/**
 * 1 分钟时序切片实体 (用于绘制 Hypnogram 催眠图)
 */
data class SleepEpoch(
    val id: Long = 0L,
    val sessionId: Long,
    val timestamp: Long,
    val stage: SleepStage,
    val breathBpm: Float,
    val respiratoryCv: Float,
    val movementScore: Float,
    val isCueTriggered: Boolean
)
