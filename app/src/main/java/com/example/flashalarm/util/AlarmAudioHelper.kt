package com.example.flashalarm.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object AlarmAudioHelper {

    /**
     * 将用户选中的系统铃声或本地歌曲拷贝到应用私有专属目录
     * 彻底解决 Android 10+ 权限在后台/重启后失效导致静默变回默认铃声的 Bug
     */
    fun saveAudioToInternalStorage(context: Context, sourceUri: Uri, alarmId: Long): String? {
        return try {
            val audioDir = File(context.filesDir, "alarm_tones")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }
            val destinationFile = File(audioDir, "alarm_audio_${alarmId}.mp3")

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (destinationFile.exists() && destinationFile.length() > 0) {
                destinationFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 删除废弃闹钟关联的私有音频文件
     */
    fun deleteInternalAudio(context: Context, alarmId: Long) {
        try {
            val file = File(context.filesDir, "alarm_tones/alarm_audio_${alarmId}.mp3")
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
