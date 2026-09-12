package com.example.flashalarm.sleep

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.example.flashalarm.sleep.model.SleepEpoch
import com.example.flashalarm.sleep.model.SleepSession
import com.example.flashalarm.sleep.model.SleepStage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 核心智能睡眠感知与 REM 触梦判决引擎 (多模态软融合 + 整夜连续分期 + 动态自适应 REM 退出)
 *
 * 升级核心：
 * 1. 连续睡眠切片 (Epoch)：每 60 秒根据微动、呼吸率、呼吸变异度划分 AWAKE / LIGHT / DEEP / REM。
 * 2. 动态自适应 REM 闭环：
 *    - 触梦后开启 8 分钟防惊醒沉浸保护期（不连环打扰）；
 *    - 保护期后三轨动态监听退出（翻身动作、呼吸稳态回归、35分钟超时兜底）；
 *    - 精准记录 REM 持续时长并自动解闭锁，迎接下一个周期。
 * 3. 睡眠数据汇总：实时维护每晚深睡、浅睡、REM、清醒耗时及翻身微动次数。
 */
class SmartSleepEngine(
    private val context: Context,
    private val config: RemDreamConfig,
    private val onTriggerRemCue: (cycleName: String) -> Unit,
    private val onEpochGenerated: (SleepEpoch) -> Unit = {}
) : SensorEventListener {

    enum class EngineState(val displayText: String) {
        IDLE("未开启"),
        CALIBRATING_30S("就寝放置校准中 (30秒)"),
        TRACKING_SLEEP_ONSET("入睡感知中 · 平息监测"),
        CONFIRMED_ASLEEP("已确认入睡 · 守护后半夜梦境"),
        REM_WINDOW_ACTIVE("已进入黄金梦境搜索带"),
        CUE_COOLDOWN("触梦沉浸保护中")
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val noiseFilter = AcousticNoiseFilter(sampleRate = 8000)
    private val playbackMonitor = AudioPlaybackMonitor(context)

    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    // 状态流暴露给 UI
    private val _engineState = MutableStateFlow(EngineState.IDLE)
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    private val _statusDetail = MutableStateFlow("请将手机平放床垫边缘")
    val statusDetail: StateFlow<String> = _statusDetail.asStateFlow()

    private val _isPhoneFlat = MutableStateFlow(true)
    val isPhoneFlat: StateFlow<Boolean> = _isPhoneFlat.asStateFlow()

    private val _isWhiteNoiseActive = MutableStateFlow(false)
    val isWhiteNoiseActive: StateFlow<Boolean> = _isWhiteNoiseActive.asStateFlow()

    private val _respirationBpm = MutableStateFlow(0f)
    val respirationBpm: StateFlow<Float> = _respirationBpm.asStateFlow()

    private val _respirationCv = MutableStateFlow(0f)
    val respirationCv: StateFlow<Float> = _respirationCv.asStateFlow()

    private val _phoneTiltAngle = MutableStateFlow(0f)
    val phoneTiltAngle: StateFlow<Float> = _phoneTiltAngle.asStateFlow()

    // 内部运行数据
    private var isEngineRunning = false
    var startTimestamp = 0L
        private set
    var sleepOnsetTimestamp = 0L
        private set
    private var lastMovementTimestamp = 0L
    private var consecutiveStillSeconds = 0

    // REM 动态状态机控制
    private var isRemTrackingActive = false
    private var activeRemStartTimestamp = 0L
    private var activeRemCycleName = ""
    private var steadyBreathConsecutiveSeconds = 0
    private var cycle4Triggered = false
    private var cycle5Triggered = false
    private var remCueTriggerCount = 0

    // 整夜分期累计统计
    private var deepSleepMinutes = 0
    private var lightSleepMinutes = 0
    private var remSleepMinutes = 0
    private var awakeMinutes = 0
    private var turnoverCount = 0

    // 每分钟切片累加器
    private var secondInEpochCount = 0
    private var epochBpmSum = 0f
    private var epochCvSum = 0f
    private var epochValidBreathCount = 0
    private var epochMaxMovement = 0f
    private var epochTurnoverInMinute = 0
    private var epochCueTriggered = false

    // 录音线程控制
    private var audioRecord: AudioRecord? = null
    private var audioJob: Job? = null
    private var logicLoopJob: Job? = null

    fun startEngine() {
        if (isEngineRunning) return
        isEngineRunning = true
        startTimestamp = System.currentTimeMillis()
        lastMovementTimestamp = startTimestamp
        consecutiveStillSeconds = 0
        cycle4Triggered = false
        cycle5Triggered = false
        isRemTrackingActive = false
        remCueTriggerCount = 0

        deepSleepMinutes = 0
        lightSleepMinutes = 0
        remSleepMinutes = 0
        awakeMinutes = 0
        turnoverCount = 0

        secondInEpochCount = 0
        epochBpmSum = 0f
        epochCvSum = 0f
        epochValidBreathCount = 0
        epochMaxMovement = 0f
        epochTurnoverInMinute = 0
        epochCueTriggered = false

        playbackMonitor.startMonitoring()

        // 注册加速度计 (5Hz 采样率)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        startAudioRecording()
        startLogicLoop()
    }

    fun stopEngine(): SleepSession {
        isEngineRunning = false
        val now = System.currentTimeMillis()
        sensorManager.unregisterListener(this)
        playbackMonitor.stopMonitoring()

        logicLoopJob?.cancel()
        logicLoopJob = null

        stopAudioRecording()
        _engineState.value = EngineState.IDLE
        _statusDetail.value = "已停止监测"

        return buildFinalSession(now)
    }

    // ========== 加速度计传感器数据处理 ==========
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isEngineRunning) return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // 1. 物理倾角计算
            val totalAccel = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val tilt = if (totalAccel > 0.1f) {
                kotlin.math.acos((abs(z) / totalAccel).coerceIn(0f, 1f)) * (180f / Math.PI.toFloat())
            } else 0f
            _phoneTiltAngle.value = tilt
            val isFlat = tilt < 15.0f
            _isPhoneFlat.value = isFlat

            // 2. 微动标量
            val deltaA = abs(totalAccel - 9.80665f)
            if (deltaA > epochMaxMovement) {
                epochMaxMovement = deltaA
            }

            // 翻身大幅动作阈值 (振动 > 1.2 m/s^2)
            if (deltaA > 1.2f) {
                lastMovementTimestamp = System.currentTimeMillis()
                consecutiveStillSeconds = 0
                epochTurnoverInMinute++
                turnoverCount++

                // 如果处于 REM 梦境沉浸期检测到翻身，记录日志
                if (_engineState.value == EngineState.REM_WINDOW_ACTIVE && !isRemTrackingActive) {
                    _statusDetail.value = "检测到床垫翻身动作 · 触梦推迟以防惊醒"
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ========== 麦克风 PCM 录音与 DSP ==========
    private fun startAudioRecording() {
        audioJob = engineScope.launch {
            val bufferSize = AudioRecord.getMinBufferSize(
                8000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(1600)

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    8000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    return@launch
                }

                audioRecord?.startRecording()
                val pcmBuffer = ShortArray(800) // 100ms 帧

                while (isActive && isEngineRunning) {
                    val isMusicPlaying = playbackMonitor.checkCurrentPlayback()
                    _isWhiteNoiseActive.value = isMusicPlaying

                    if (!isMusicPlaying) {
                        val readSize = audioRecord?.read(pcmBuffer, 0, pcmBuffer.size) ?: 0
                        if (readSize > 0) {
                            noiseFilter.processFrame(pcmBuffer, readSize)
                            _respirationBpm.value = noiseFilter.currentBreathRateBpm
                            _respirationCv.value = noiseFilter.currentRespiratoryCv
                        }
                    } else {
                        _respirationBpm.value = 0f
                        _respirationCv.value = 0f
                    }

                    delay(100L)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                stopAudioRecording()
            }
        }
    }

    private fun stopAudioRecording() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
    }

    // ========== 核心状态机与 1 分钟切片循环 (每秒执行一次) ==========
    private fun startLogicLoop() {
        logicLoopJob = engineScope.launch {
            while (isActive && isEngineRunning) {
                val now = System.currentTimeMillis()
                val elapsedFromStartSec = ((now - startTimestamp) / 1000L).toInt()

                // 阶段 0: 30 秒就寝放置校准倒计时
                if (elapsedFromStartSec < 30) {
                    _engineState.value = EngineState.CALIBRATING_30S
                    val remain = 30 - elapsedFromStartSec
                    _statusDetail.value = "请将手机平放床垫边缘 · 倒计时 ${remain}s"
                    delay(1000L)
                    continue
                }

                consecutiveStillSeconds = ((now - lastMovementTimestamp) / 1000L).toInt()

                // 收集当前秒的生理指标
                val currentBpm = _respirationBpm.value
                val currentCv = _respirationCv.value
                if (currentBpm > 0f) {
                    epochBpmSum += currentBpm
                    epochCvSum += currentCv
                    epochValidBreathCount++
                }

                // 入睡状态推进
                if (sleepOnsetTimestamp == 0L) {
                    _engineState.value = EngineState.TRACKING_SLEEP_ONSET
                    evaluateSleepOnset(now)
                } else {
                    evaluateRemTracking(now)
                }

                // 60 秒整切片打包处理
                secondInEpochCount++
                if (secondInEpochCount >= 60) {
                    secondInEpochCount = 0
                    processMinuteEpoch(now)
                }

                delay(1000L)
            }
        }
    }

    /**
     * 第一阶段：入睡软融合判定
     */
    private fun evaluateSleepOnset(now: Long) {
        val stillMin = consecutiveStillSeconds / 60

        // 一票否决权：身体虽然没动，但麦克风检测到清晰交谈/清嗓
        if (noiseFilter.isConsciousVocalization && !_isWhiteNoiseActive.value) {
            lastMovementTimestamp = now
            consecutiveStillSeconds = 0
            _statusDetail.value = "检测到清醒人声 · 一票否决入睡判定"
            return
        }

        val motionScore = (stillMin / 12f).coerceIn(0f, 1f)
        val breathBpm = noiseFilter.currentBreathRateBpm
        val isBreathingSteady = breathBpm in 10f..18f
        val acousticScore = if (isBreathingSteady) 1.0f else 0.0f

        val isAcousticDegraded = _isWhiteNoiseActive.value || !noiseFilter.isAcousticReliable
        val fusedScore = if (isAcousticDegraded) {
            motionScore
        } else {
            motionScore * 0.65f + acousticScore * 0.35f
        }

        _statusDetail.value = if (isAcousticDegraded) {
            "白噪音避让中 · 床垫已平息 ${stillMin} 分钟"
        } else {
            "微动与声学融合中 · 已平息 ${stillMin} 分钟"
        }

        val canConfirm = (isBreathingSteady && stillMin >= 7) || (stillMin >= 12)
        if (canConfirm && fusedScore >= 0.65f) {
            sleepOnsetTimestamp = now
            _engineState.value = EngineState.CONFIRMED_ASLEEP
            _statusDetail.value = "已确认入睡 · 锁定入睡点，静默守护后半夜"
        }
    }

    /**
     * 第二阶段：REM 动态自适应捕捉与退出闭环
     */
    private fun evaluateRemTracking(now: Long) {
        val minutesAsleep = ((now - sleepOnsetTimestamp) / (60 * 1000L)).toInt()
        val hoursAsleep = minutesAsleep / 60f

        // ===== 动态 REM 进行中跟踪与退出捕获 =====
        if (isRemTrackingActive) {
            val elapsedMs = now - activeRemStartTimestamp
            val elapsedMin = (elapsedMs / (60 * 1000L)).toInt()

            // 1. 单次梦境防惊醒沉浸保护期 (前 8 分钟)：绝不连环打扰
            if (elapsedMs < 8 * 60 * 1000L) {
                _engineState.value = EngineState.CUE_COOLDOWN
                val remainMin = ((8 * 60 * 1000L - elapsedMs) / 60000L) + 1
                _statusDetail.value = "✨ 触梦已执行 · 梦境沉浸守护中 (${remainMin}m)"
                return
            }

            // 2. 动态退出监听窗口 (保护期过后)
            _engineState.value = EngineState.REM_WINDOW_ACTIVE

            // 退出标志 1：检测到大幅翻身微觉醒 (肌张力恢复)
            val hasTurnedOver = consecutiveStillSeconds < 10 && epochTurnoverInMinute > 0

            // 退出标志 2：呼吸变异率连续稳定回落稳态 (<8% 持续 2 分钟)
            val isBreathSteady = _respirationCv.value in 1f..8f && !_isWhiteNoiseActive.value
            if (isBreathSteady) {
                steadyBreathConsecutiveSeconds++
            } else {
                steadyBreathConsecutiveSeconds = 0
            }
            val hasBreathNormalized = steadyBreathConsecutiveSeconds >= 120

            // 退出标志 3：生理时限兜底 (单次 REM 达 38 分钟)
            val hasTimedOut = elapsedMs >= 38 * 60 * 1000L

            if (hasTurnedOver || hasBreathNormalized || hasTimedOut) {
                val exitReason = when {
                    hasTurnedOver -> "翻身肌张力恢复"
                    hasBreathNormalized -> "呼吸平稳回归稳态"
                    else -> "生理周期自然结束"
                }
                isRemTrackingActive = false
                steadyBreathConsecutiveSeconds = 0
                _engineState.value = EngineState.CONFIRMED_ASLEEP
                _statusDetail.value = "$activeRemCycleName 已结束 (历时${elapsedMin}m · $exitReason) · 待命下一周期"
                return
            }

            _statusDetail.value = "🌙 正在进行 $activeRemCycleName · 已持续 ${elapsedMin}m (动态监听梦境中)"
            return
        }

        // ===== 黄金周期警戒扫描 =====
        val inCycle4 = hoursAsleep in 4.1f..5.0f && !cycle4Triggered
        val inCycle5 = hoursAsleep in 5.5f..6.5f && !cycle5Triggered

        if (!inCycle4 && !inCycle5) {
            _engineState.value = EngineState.CONFIRMED_ASLEEP
            _statusDetail.value = "已沉睡 ${hoursAsleep.format(1)}h · 距下个黄金梦境窗还有 ${calculateTimeUntilNextWindow(hoursAsleep)}"
            return
        }

        _engineState.value = EngineState.REM_WINDOW_ACTIVE
        val currentCycleLabel = if (inCycle4) "第4周期梦境" else "第5周期黄金深梦"
        val stillMinutes = consecutiveStillSeconds / 60

        // 轨道 1：声学呼吸变异精准捕获 (无翻身满 8 分钟 + 呼吸变异率 12%~28%)
        val cv = noiseFilter.currentRespiratoryCv
        val isAtoniaClean = stillMinutes >= 8
        val isDreamBreathing = cv in 12f..28f && !_isWhiteNoiseActive.value

        if (isAtoniaClean && isDreamBreathing) {
            triggerCueNow(currentCycleLabel, "捕获做梦呼吸变异 (CV: ${cv.toInt()}%)", inCycle4)
            return
        }

        // 轨道 2：巡航最饱满点安全保底
        val reachedPeakTime = (inCycle4 && hoursAsleep >= 4.6f) || (inCycle5 && hoursAsleep >= 6.0f)
        if (reachedPeakTime && stillMinutes >= 12) {
            triggerCueNow(currentCycleLabel, "黄金周期最饱满点安全保底", inCycle4)
            return
        }

        _statusDetail.value = "🔍 正在扫描 $currentCycleLabel · 身体静止 ${stillMinutes}m (待命中)"
    }

    private fun triggerCueNow(cycleLabel: String, reason: String, isCycle4: Boolean) {
        val now = System.currentTimeMillis()
        if (isCycle4) cycle4Triggered = true else cycle5Triggered = true

        isRemTrackingActive = true
        activeRemStartTimestamp = now
        activeRemCycleName = cycleLabel
        steadyBreathConsecutiveSeconds = 0
        epochCueTriggered = true
        remCueTriggerCount++

        _engineState.value = EngineState.CUE_COOLDOWN
        _statusDetail.value = "✨ 正在触发清醒梦触梦 ($reason)"

        mainHandler.post {
            onTriggerRemCue.invoke("$cycleLabel ($reason)")
        }
    }

    /**
     * 每 60 秒计算并分发一次睡眠切片 (Epoch)
     */
    private fun processMinuteEpoch(now: Long) {
        val avgBpm = if (epochValidBreathCount > 0) epochBpmSum / epochValidBreathCount else _respirationBpm.value
        val avgCv = if (epochValidBreathCount > 0) epochCvSum / epochValidBreathCount else _respirationCv.value
        val movement = epochMaxMovement

        // 判断该分钟所属的睡眠分期
        val stage: SleepStage = when {
            // 未确认入睡前均为清醒
            sleepOnsetTimestamp == 0L -> SleepStage.AWAKE

            // 处于活跃 REM 梦境中
            isRemTrackingActive -> SleepStage.REM

            // 发生了剧烈翻身或清醒说话
            epochTurnoverInMinute > 0 || consecutiveStillSeconds < 25 -> SleepStage.AWAKE

            else -> {
                val stillMin = consecutiveStillSeconds / 60
                val hoursAsleep = (now - sleepOnsetTimestamp) / (3600 * 1000L).toFloat()

                // 深睡特征：前半夜极度静止满 12 分钟，且呼吸均匀稳态 (CV < 7%)
                val isDeepCondition = stillMin >= 12 && (avgCv < 7f || avgBpm in 10f..14f) && (hoursAsleep < 3.5f || stillMin >= 20)

                if (isDeepCondition) SleepStage.DEEP else SleepStage.LIGHT
            }
        }

        // 累计阶段时长
        when (stage) {
            SleepStage.AWAKE -> awakeMinutes++
            SleepStage.LIGHT -> lightSleepMinutes++
            SleepStage.DEEP -> deepSleepMinutes++
            SleepStage.REM -> remSleepMinutes++
        }

        val epoch = SleepEpoch(
            sessionId = startTimestamp,
            timestamp = now,
            stage = stage,
            breathBpm = avgBpm,
            respiratoryCv = avgCv,
            movementScore = movement,
            isCueTriggered = epochCueTriggered
        )

        // 回调给服务持久化与刷新
        onEpochGenerated.invoke(epoch)

        // 清理当前分钟的累加器
        epochBpmSum = 0f
        epochCvSum = 0f
        epochValidBreathCount = 0
        epochMaxMovement = 0f
        epochTurnoverInMinute = 0
        epochCueTriggered = false
    }

    private fun buildFinalSession(endTime: Long): SleepSession {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateStr = sdf.format(Date(startTimestamp))
        val totalInBedMin = ((endTime - startTimestamp) / (60 * 1000L)).toInt().coerceAtLeast(1)
        val netSleepMin = (deepSleepMinutes + lightSleepMinutes + remSleepMinutes).coerceAtLeast(0)

        // 综合睡眠评分计算 (基于睡眠净时长、深睡比例、REM、连续性等指标加权计算)
        val durationScore = ((netSleepMin / 420f) * 45).coerceIn(0f, 45f) // 7小时满分 45分
        val deepRatio = if (netSleepMin > 0) deepSleepMinutes.toFloat() / netSleepMin else 0f
        val deepScore = (deepRatio / 0.22f * 25).coerceIn(0f, 25f) // 22% 深睡满分 25分
        val remRatio = if (netSleepMin > 0) remSleepMinutes.toFloat() / netSleepMin else 0f
        val remScore = (remRatio / 0.22f * 20).coerceIn(0f, 20f)  // 22% REM 满分 20分
        val awakeDeduction = (awakeMinutes * 0.5f).coerceAtMost(15f)
        val calculatedScore = (durationScore + deepScore + remScore - awakeDeduction + 10).toInt().coerceIn(40, 100)

        return SleepSession(
            id = startTimestamp,
            dateString = dateStr,
            startTimestamp = startTimestamp,
            endTimestamp = endTime,
            sleepOnsetTimestamp = sleepOnsetTimestamp,
            totalDurationMin = totalInBedMin,
            netSleepDurationMin = netSleepMin,
            deepSleepMin = deepSleepMinutes,
            lightSleepMin = lightSleepMinutes,
            remSleepMin = remSleepMinutes,
            awakeMin = awakeMinutes,
            cueTriggerCount = remCueTriggerCount,
            sleepScore = calculatedScore,
            avgBreathBpm = if (epochValidBreathCount > 0) epochBpmSum / epochValidBreathCount else 14.5f,
            turnoverCount = turnoverCount
        )
    }

    private fun calculateTimeUntilNextWindow(hoursAsleep: Float): String {
        return when {
            hoursAsleep < 4.1f -> "${((4.1f - hoursAsleep) * 60).toInt()} 分钟"
            hoursAsleep in 5.0f..5.5f -> "${((5.5f - hoursAsleep) * 60).toInt()} 分钟"
            else -> "已过核心梦境窗口"
        }
    }

    private fun Float.format(digits: Int): String = "%.${digits}f".format(this)
}
