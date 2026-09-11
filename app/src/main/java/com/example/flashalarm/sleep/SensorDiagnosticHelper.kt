package com.example.flashalarm.sleep

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * 传感器硬件实时诊断与交互自检助手 (Sensor Diagnostic Helper)
 *
 * 核心功能：
 * 1. 实时水平倾角仪：实时获取 3 轴重力矢量，计算手机与绝对水平面的实时夹角 (0°~90°)。
 *    手机拿在手里显示倾角 40°~80°，平放床面显示 0°~15°，数值随手部动作实时跳变！
 * 2. 麦克风实时动态分贝计 (VU Meter)：实时捕获音频计算瞬时分贝 (20dB~90dB)。
 *    对着手机轻声说话或吹气，分贝条与状态指示灯即时跳跃反应，直观确认麦克风硬件完好。
 * 3. 仅在自检面板打开时临时启动，退出面板立即彻底释放，零功耗消耗。
 */
class SensorDiagnosticHelper(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _tiltAngle = MutableStateFlow(0f)
    val tiltAngle: StateFlow<Float> = _tiltAngle.asStateFlow()

    private val _isFlat = MutableStateFlow(true)
    val isFlat: StateFlow<Boolean> = _isFlat.asStateFlow()

    private val _liveDb = MutableStateFlow(25f)
    val liveDb: StateFlow<Float> = _liveDb.asStateFlow()

    private val _noiseFloorDb = MutableStateFlow(25f)
    val noiseFloorDb: StateFlow<Float> = _noiseFloorDb.asStateFlow()

    private val _isMicResponsive = MutableStateFlow(false)
    val isMicResponsive: StateFlow<Boolean> = _isMicResponsive.asStateFlow()

    private var diagnosticJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var audioRecord: AudioRecord? = null
    private var isRunning = false

    fun startDiagnostics() {
        if (isRunning) return
        isRunning = true

        // 1. 注册加速度计 (SENSOR_DELAY_UI 高灵敏度实时刷新角度)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        // 2. 启动音频实时分贝采样线程
        startAudioMeter()
    }

    fun stopDiagnostics() {
        isRunning = false
        sensorManager.unregisterListener(this)
        diagnosticJob?.cancel()
        diagnosticJob = null
        stopAudioMeter()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isRunning) return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val totalAccel = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            // 计算手机与绝对水平面的夹角 (度数)
            val angle = if (totalAccel > 0.1f) {
                acos((abs(z) / totalAccel).coerceIn(0f, 1f)) * (180f / Math.PI.toFloat())
            } else {
                0f
            }
            _tiltAngle.value = angle
            // 倾角在 15 度以内视为平放良好
            _isFlat.value = angle < 15.0f
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startAudioMeter() {
        diagnosticJob = scope.launch {
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
                val buffer = ShortArray(400) // 50ms 快速刷新帧
                var minEnergy = 60f

                while (isActive && isRunning) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        var sumSq = 0.0
                        for (i in 0 until read) {
                            sumSq += (buffer[i] * buffer[i]).toDouble()
                        }
                        val rms = sqrt(sumSq / read).toFloat()
                        val db = (20f * log10((rms.coerceAtLeast(1f) / 32767f)) + 96f).coerceIn(10f, 96f)

                        _liveDb.value = db

                        // 跟踪最低底噪
                        if (db < minEnergy) minEnergy = db
                        _noiseFloorDb.value = minEnergy

                        // 超过底噪 15dB 或超过 50dB 认定为麦克风收到有效吹气/人声
                        if (db > minEnergy + 15f || db > 52f) {
                            _isMicResponsive.value = true
                        }
                    }
                    delay(50L) // 20 FPS 丝滑刷新
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                stopAudioMeter()
            }
        }
    }

    private fun stopAudioMeter() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
    }
}
