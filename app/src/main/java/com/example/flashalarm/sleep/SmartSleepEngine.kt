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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 核心智能睡眠感知与 REM 触梦判决引擎 (多模态软融合 + 双轨中度平衡态)
 *
 * 核心设计：
 * 1. 30秒就寝放置缓冲：避免用户拿放手机过程中的颠簸被误算为翻身。
 * 2. 入睡阶段 (Phase 1)：5Hz 床垫微动 (Cole-Kripke Actigraphy) + 麦克风 180~800Hz 呼吸带通软融合。
 *    - 白噪音播放时麦克风主动优雅弃权，100% 由床垫加速度计接管。
 *    - 麦克风检测到清晰说话/清嗓时，一票否决重置计时器。
 * 3. 做梦阶段 (Phase 2 - Balanced Dual Track)：
 *    - 严格遵循中度平衡态：每个周期最多仅触发 1 次，触发后强制 35~45 分钟长冷却。
 *    - 轨道1 (生理捕获)：窗口期内 + 身体静止满 8 分钟 + 呼吸变异率 CV_RR >= 12% -> 立即击发。
 *    - 轨道2 (巡航保底)：窗口最饱满节点 (4.6h / 6.0h) + 身体稳定满 12 分钟 -> 仅触发 1 次保底。
 *    - 翻身安全保护：检测到大幅翻动立即推迟 10 分钟。
 */
class SmartSleepEngine(
    private val context: Context,
    private val config: RemDreamConfig,
    private val onTriggerRemCue: (cycleName: String) -> Unit
) : SensorEventListener {

    enum class EngineState(val displayText: String) {
        IDLE("未开启"),
        CALIBRATING_30S("就寝放置校准中 (30秒)"),
        TRACKING_SLEEP_ONSET("入睡感知中 · 平息监测"),
        CONFIRMED_ASLEEP("已确认入睡 · 守护后半夜梦境"),
        REM_WINDOW_ACTIVE("已进入黄金梦境搜索带"),
        CUE_COOLDOWN("触梦已执行 · 梦境冷却守护中")
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
    private var startTimestamp = 0L
    private var sleepOnsetTimestamp = 0L
    private var lastMovementTimestamp = 0L
    private var consecutiveStillSeconds = 0
    private var lastCueTriggerTimestamp = 0L

    private var cycle4Triggered = false
    private var cycle5Triggered = false

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

        playbackMonitor.startMonitoring()

        // 注册加速度计 (5Hz 采样率：SENSOR_DELAY_NORMAL 约 200ms 一次)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        startAudioRecording()
        startLogicLoop()
    }

    fun stopEngine() {
        isEngineRunning = false
        sensorManager.unregisterListener(this)
        playbackMonitor.stopMonitoring()

        logicLoopJob?.cancel()
        logicLoopJob = null

        stopAudioRecording()
        _engineState.value = EngineState.IDLE
        _statusDetail.value = "已停止监测"
    }

    // ========== 加速度计传感器数据处理 ==========
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isEngineRunning) return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // 1. 真实物理倾角计算 (与绝对水平面的夹角 0°~90°)
            val totalAccel = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val tilt = if (totalAccel > 0.1f) {
                kotlin.math.acos((abs(z) / totalAccel).coerceIn(0f, 1f)) * (180f / Math.PI.toFloat())
            } else 0f
            _phoneTiltAngle.value = tilt
            val isFlat = tilt < 15.0f
            _isPhoneFlat.value = isFlat

            // 2. 微动加速度标量变化计算
            val deltaA = abs(totalAccel - 9.80665f)

            // 翻身大幅动作阈值 (振动 > 1.2 m/s^2)
            if (deltaA > 1.2f) {
                lastMovementTimestamp = System.currentTimeMillis()
                consecutiveStillSeconds = 0

                // 如果处于 REM 梦境搜索带内发生大翻身，安全保护推迟 10 分钟
                if (_engineState.value == EngineState.REM_WINDOW_ACTIVE) {
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
                    // 检查本机是否播放白噪音：播放中则跳过 DSP 麦克风分析（优雅弃权）
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
                        // 白噪音播放中，呼吸特征重置为平稳待命
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

    // ========== 核心状态机与中度平衡态判决循环 (每秒执行一次) ==========
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

                // 阶段 1: 入睡锁定 (Sleep Onset Fusion)
                if (sleepOnsetTimestamp == 0L) {
                    _engineState.value = EngineState.TRACKING_SLEEP_ONSET
                    evaluateSleepOnset(now)
                } else {
                    // 阶段 2: REM 做梦期捕捉 (Balanced Dual Track)
                    evaluateRemTracking(now)
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

        // 动作置信度：静止时间越长分越高 (0~15分钟线性映射到 0~1.0)
        val motionScore = (stillMin / 12f).coerceIn(0f, 1f)

        // 声学置信度：检测到标准睡眠呼吸 (10~18 bpm)
        val breathBpm = noiseFilter.currentBreathRateBpm
        val isBreathingSteady = breathBpm in 10f..18f
        val acousticScore = if (isBreathingSteady) 1.0f else 0.0f

        // 软融合权重计算 (有白噪音或声学不可靠时，麦克风弃权降级为 100% 纯动作)
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

        // 入睡确认阈值：
        // 1. 声学极佳时：平息 7 分钟且呼吸稳定 (fusedScore >= 0.70) 即可加速确认入睡
        // 2. 纯动作降级时：连续平息满 12 分钟稳健确认入睡
        val canConfirm = (isBreathingSteady && stillMin >= 7) || (stillMin >= 12)
        if (canConfirm && fusedScore >= 0.65f) {
            sleepOnsetTimestamp = now
            _engineState.value = EngineState.CONFIRMED_ASLEEP
            _statusDetail.value = "已确认入睡 · 锁定入睡点，静默守护后半夜"
        }
    }

    /**
     * 第二阶段：REM 梦境捕获 (双轨制 + 35分钟生理冷却锁)
     */
    private fun evaluateRemTracking(now: Long) {
        val minutesAsleep = ((now - sleepOnsetTimestamp) / (60 * 1000L)).toInt()
        val hoursAsleep = minutesAsleep / 60f

        // 35 分钟冷却期安全检查
        if (lastCueTriggerTimestamp > 0L) {
            val cooldownMinutes = ((now - lastCueTriggerTimestamp) / (60 * 1000L)).toInt()
            if (cooldownMinutes < 35) {
                _engineState.value = EngineState.CUE_COOLDOWN
                _statusDetail.value = "已触梦 · 生理保护冷却中 (${35 - cooldownMinutes}m 剩余)"
                return
            }
        }

        // 定义黄金警戒窗口 (小时)
        // 第 4 周期：4.1h ~ 5.0h (最饱满点约 4.6h)
        // 第 5 周期：5.5h ~ 6.5h (最饱满点约 6.0h)
        val inCycle4 = hoursAsleep in 4.1f..5.0f && !cycle4Triggered
        val inCycle5 = hoursAsleep in 5.5f..6.5f && !cycle5Triggered

        if (!inCycle4 && !inCycle5) {
            _engineState.value = EngineState.CONFIRMED_ASLEEP
            _statusDetail.value = "已沉睡 ${hoursAsleep.format(1)}h · 距下个黄金梦境窗还有 ${calculateTimeUntilNextWindow(hoursAsleep)}"
            return
        }

        // 进入警戒搜索带
        _engineState.value = EngineState.REM_WINDOW_ACTIVE
        val currentCycleLabel = if (inCycle4) "第4周期梦境" else "第5周期黄金深梦"
        val stillMinutes = consecutiveStillSeconds / 60

        // ================= 轨道 1：声学呼吸变异精准捕获 (主轨) =================
        // 条件：无翻身满 8 分钟 + 呼吸变异率在合理做梦区间 (12% ~ 28%)
        val cv = noiseFilter.currentRespiratoryCv
        val isAtoniaClean = stillMinutes >= 8
        val isDreamBreathing = cv in 12f..28f && !_isWhiteNoiseActive.value

        if (isAtoniaClean && isDreamBreathing) {
            triggerCueNow(currentCycleLabel, "捕获做梦呼吸变异 (CV: ${cv.toInt()}%)", inCycle4)
            return
        }

        // ================= 轨道 2：巡航最饱满点保底 (备轨，防被子捂住) =================
        // 条件：到达周期的中心最饱满时间点 (4.6h 或 6.0h)，且过去 12 分钟身体极其安稳
        val reachedPeakTime = (inCycle4 && hoursAsleep >= 4.6f) || (inCycle5 && hoursAsleep >= 6.0f)
        if (reachedPeakTime && stillMinutes >= 12) {
            triggerCueNow(currentCycleLabel, "黄金周期最饱满点安全保底", inCycle4)
            return
        }

        _statusDetail.value = "🔍 正在扫描 $currentCycleLabel · 身体静止 ${stillMinutes}m (待命中)"
    }

    private fun triggerCueNow(cycleLabel: String, reason: String, isCycle4: Boolean) {
        lastCueTriggerTimestamp = System.currentTimeMillis()
        if (isCycle4) cycle4Triggered = true else cycle5Triggered = true

        _engineState.value = EngineState.CUE_COOLDOWN
        _statusDetail.value = "✨ 正在触发清醒梦触梦 ($reason)"

        mainHandler.post {
            onTriggerRemCue.invoke("$cycleLabel ($reason)")
        }
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
