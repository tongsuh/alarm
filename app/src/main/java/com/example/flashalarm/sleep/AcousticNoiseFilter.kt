package com.example.flashalarm.sleep

import kotlin.math.*

/**
 * 麦克风声学抗噪分析与呼吸生物特征提取管道 (DSP Pipeline)
 *
 * 核心功能：
 * 1. 180Hz ~ 800Hz 人体呼吸带通滤波：削减 <120Hz 窗外汽车引擎/排气管低频轰鸣与高频尖锐杂音。
 * 2. 动态最低底噪对消 (Min-Statistics)：自动跟踪对消空调、风扇等恒定背景噪音。
 * 3. 短时自相关函数 (ACF) 周期性检验：只认准 3~5 秒/次的周期性呼吸波形，过滤孤立偶发杂音。
 * 4. 清醒交谈/清嗓检测 (Conscious Vocalization)：高能量瞬态语音识别，用于“一票否决”防误判。
 * 5. REM 呼吸变异率 (CV_RR) 计算：对比深睡眠稳态呼吸，捕获快速眼动期的自主神经风暴。
 */
class AcousticNoiseFilter(
    private val sampleRate: Int = 8000
) {
    // 带通滤波系数 (双二阶 Biquad IIR 滤波器：180Hz ~ 800Hz)
    // 级联 Highpass (fc ~ 160Hz) + Lowpass (fc ~ 850Hz)
    private var hpX1 = 0f; private var hpX2 = 0f; private var hpY1 = 0f; private var hpY2 = 0f
    private var lpX1 = 0f; private var lpX2 = 0f; private var lpY1 = 0f; private var lpY2 = 0f

    // 动态最低底噪跟踪 (Min-Statistics over 15s)
    private val noiseFloorBuffer = FloatArray(30) { 40f }
    private var noiseFloorIndex = 0
    var currentNoiseFloorDb: Float = 35f
        private set

    // 呼吸包络时间序列 (采样率约 10Hz，存储 30 秒数据 = 300 个样本)
    private val envelopeHistory = FloatArray(300)
    private var envelopeIndex = 0
    private var envelopeCount = 0

    // 呼吸周期记录队列 (用于计算 REM 变异率 CV_RR)
    private val breathIntervalHistory = ArrayList<Float>()
    private val maxIntervalHistorySize = 16

    // 实时状态输出
    var currentSignalDb: Float = 0f
        private set
    var currentSnrDb: Float = 0f
        private set
    var isAcousticReliable: Boolean = true
        private set
    var isConsciousVocalization: Boolean = false
        private set
    var currentBreathRateBpm: Float = 0f
        private set
    var currentRespiratoryCv: Float = 0f
        private set
    var isRemRespiratoryPatternDetected: Boolean = false
        private set

    /**
     * 处理单帧 16-bit PCM 音频数据 (建议每帧 0.1s ~ 0.5s)
     */
    fun processFrame(pcmBuffer: ShortArray, readSize: Int) {
        if (readSize <= 0) return

        var sumSquare = 0.0
        var peakValue = 0

        // 1. 带通滤波与能量统计
        for (i in 0 until readSize) {
            val rawSample = pcmBuffer[i].toFloat()
            val absVal = abs(pcmBuffer[i].toInt())
            if (absVal > peakValue) peakValue = absVal

            // 一阶高通滤波 (截止频率 ~160Hz, 阻断车流超低频)
            val hpAlpha = 0.88f
            val hpOut = hpAlpha * (hpY1 + rawSample - hpX1)
            hpX1 = rawSample
            hpY1 = hpOut

            // 一阶低通滤波 (截止频率 ~850Hz, 滤除尖锐高频)
            val lpAlpha = 0.45f
            val lpOut = lpY1 + lpAlpha * (hpOut - lpY1)
            lpY1 = lpOut

            sumSquare += (lpOut * lpOut).toDouble()
        }

        val rms = sqrt(sumSquare / readSize).toFloat()
        // 转换为相对于满量程的分贝值 (-96dB ~ 0dB) 偏移为正数显示 (0 ~ 96dB)
        val frameDb = 20f * log10((rms.coerceAtLeast(1f) / 32767f)).coerceIn(-96f, 0f) + 96f
        currentSignalDb = frameDb

        // 2. 动态更新最低底噪 (寻找 15 秒内的能量洼地，代表持续环境风扇/空调底噪)
        noiseFloorBuffer[noiseFloorIndex] = frameDb
        noiseFloorIndex = (noiseFloorIndex + 1) % noiseFloorBuffer.size
        currentNoiseFloorDb = noiseFloorBuffer.minOrNull() ?: 35f

        // 计算当前信噪比
        currentSnrDb = (frameDb - currentNoiseFloorDb).coerceAtLeast(0f)

        // 3. 评估声学可靠性 (优雅降级判断)
        // 底噪过高 (>65dB 过于嘈杂) 或 信号完全沉寂 (<15dB 可能是麦克风硬件被堵严实)，判定声学不可靠
        isAcousticReliable = currentNoiseFloorDb in 15f..65f

        // 4. 清醒说话/咳嗽/清嗓检测 (一票否决依据)
        // 说话特征：振幅峰均比 (Crest Factor) 极大且瞬时能量显著高于底噪 (>22dB)
        val crestFactor = peakValue.toFloat() / rms.coerceAtLeast(1f)
        isConsciousVocalization = (frameDb > currentNoiseFloorDb + 22f) && (crestFactor > 4.5f) && (frameDb > 55f)

        // 5. 存入包络序列，用于自相关周期呼吸分析 (以有效振幅为包络)
        val effectiveEnvelope = (frameDb - currentNoiseFloorDb).coerceAtLeast(0f)
        envelopeHistory[envelopeIndex] = effectiveEnvelope
        envelopeIndex = (envelopeIndex + 1) % envelopeHistory.size
        if (envelopeCount < envelopeHistory.size) envelopeCount++

        // 每秒运行一次自相关分析 (当积累足够 15 秒以上包络后)
        if (envelopeCount >= 150) {
            analyzeBreathingAutocorrelation()
        }
    }

    /**
     * 短时自相关函数 (ACF) 分析呼吸节律与周期
     * 正常成人睡眠呼吸周期为 3.5 ~ 5.5 秒 (约每分钟 11~17 次)
     * 在 10Hz 包络采样率下，滞后 lag 对应 35 ~ 55 个采样点
     */
    private fun analyzeBreathingAutocorrelation() {
        val n = envelopeCount
        // 计算均值
        var sum = 0f
        for (i in 0 until n) sum += envelopeHistory[i]
        val mean = sum / n

        // 计算零滞后自相关方差 R(0)
        var var0 = 0f
        for (i in 0 until n) {
            val diff = envelopeHistory[i] - mean
            var0 += diff * diff
        }
        if (var0 <= 0.001f) {
            currentBreathRateBpm = 0f
            return
        }

        // 搜索 3.0秒 ~ 6.0秒 (lag 30 ~ 60) 之间的自相关峰值
        var maxR = -1f
        var bestLag = 0
        for (lag in 30..60) {
            var sumCross = 0f
            for (i in 0 until (n - lag)) {
                val idx1 = (envelopeIndex - n + i + envelopeHistory.size) % envelopeHistory.size
                val idx2 = (envelopeIndex - n + i + lag + envelopeHistory.size) % envelopeHistory.size
                sumCross += (envelopeHistory[idx1] - mean) * (envelopeHistory[idx2] - mean)
            }
            val rLag = sumCross / var0
            if (rLag > maxR) {
                maxR = rLag
                bestLag = lag
            }
        }

        // 周期性判定准则：自相关系数 > 0.38 说明存在强周期性呼吸波形
        if (maxR > 0.38f && bestLag > 0) {
            val periodSec = bestLag * 0.1f // 10Hz 包络周期
            val bpm = 60f / periodSec
            if (bpm in 9f..24f) {
                currentBreathRateBpm = bpm
                updateRespiratoryVariability(periodSec)
            }
        } else {
            // 非周期杂音 (车流、突发响动) 或呼吸过于微弱
            currentBreathRateBpm = 0f
        }
    }

    /**
     * 更新呼吸周期变异率 (CV_RR) 以识别 REM 阶段
     */
    private fun updateRespiratoryVariability(intervalSec: Float) {
        breathIntervalHistory.add(intervalSec)
        if (breathIntervalHistory.size > maxIntervalHistorySize) {
            breathIntervalHistory.removeAt(0)
        }

        if (breathIntervalHistory.size >= 8) {
            val mean = breathIntervalHistory.average().toFloat()
            var sumSqDiff = 0f
            for (v in breathIntervalHistory) {
                sumSqDiff += (v - mean) * (v - mean)
            }
            val stdDev = sqrt(sumSqDiff / breathIntervalHistory.size)
            // 变异系数 CV_RR = (标准差 / 均值) * 100%
            currentRespiratoryCv = if (mean > 0f) (stdDev / mean) * 100f else 0f

            // REM 判定特征：呼吸变异率在 14% ~ 30% 之间，显著脱离深睡眠稳态 (<6%)
            isRemRespiratoryPatternDetected = currentRespiratoryCv in 14f..32f
        }
    }

    fun reset() {
        hpX1 = 0f; hpX2 = 0f; hpY1 = 0f; hpY2 = 0f
        lpX1 = 0f; lpX2 = 0f; lpY1 = 0f; lpY2 = 0f
        noiseFloorBuffer.fill(40f)
        envelopeHistory.fill(0f)
        envelopeCount = 0
        envelopeIndex = 0
        breathIntervalHistory.clear()
        currentBreathRateBpm = 0f
        currentRespiratoryCv = 0f
        isRemRespiratoryPatternDetected = false
        isConsciousVocalization = false
    }
}
