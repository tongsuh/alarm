package com.example.flashalarm.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * 机械齿轮点击音效合成器
 * 在内存中生成高品质的 12ms 快速衰减机械齿轮咔哒声 (Click) 并通过 AudioTrack 低延迟播放
 * 彻底解决国产安卓系统默认关闭“触摸提示音”导致滚轮滑动无声音的问题
 */
object MechanicalTickSound {

    private var audioTrack: AudioTrack? = null
    private var isReady = false

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        try {
            val sampleRate = 44100
            val durationMs = 22
            val numSamples = (sampleRate * durationMs / 1000.0).toInt()
            val pcmData = ShortArray(numSamples)

            // 合成双频机械撞击波形：2200Hz 尖锐机械齿轮碰撞触点 + 750Hz 金属腔体微弱共振
            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val clickEnvelope = Math.exp(-t * 1100.0)
                val bodyEnvelope = Math.exp(-t * 320.0)
                val click = Math.sin(2.0 * Math.PI * 2200.0 * t) * clickEnvelope
                val body = Math.sin(2.0 * Math.PI * 750.0 * t) * bodyEnvelope
                val combined = (click * 0.65 + body * 0.35)
                pcmData[i] = (combined * Short.MAX_VALUE * 0.95).toInt().coerceIn(-32768, 32767).toShort()
            }

            val bufferSize = pcmData.size * 2
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(pcmData, 0, pcmData.size)
            audioTrack = track
            isReady = true
        } catch (e: Exception) {
            e.printStackTrace()
            isReady = false
        }
    }

    fun play() {
        try {
            val track = audioTrack ?: return
            if (!isReady) return

            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause()
                track.flush()
            }
            track.reloadStaticData()
            track.setVolume(1.0f)
            track.play()
        } catch (e: Exception) {
            try {
                initAudioTrack()
                audioTrack?.reloadStaticData()
                audioTrack?.setVolume(1.0f)
                audioTrack?.play()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }
}
