package com.example.flashalarm.data

import android.content.Context
import com.example.flashalarm.model.AlarmItem
import org.json.JSONArray

class AlarmRepository(context: Context) {
    private val prefs = context.getSharedPreferences("alarms_prefs", Context.MODE_PRIVATE)

    fun getAllAlarms(): List<AlarmItem> {
        val jsonStr = prefs.getString("saved_alarms", null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<AlarmItem>()
            for (i in 0 until jsonArray.length()) {
                list.add(AlarmItem.fromJson(jsonArray.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getAlarmById(id: Long): AlarmItem? {
        return getAllAlarms().find { it.id == id }
    }

    fun saveAlarm(alarm: AlarmItem) {
        val current = getAllAlarms().toMutableList()
        val index = current.indexOfFirst { it.id == alarm.id }
        if (index >= 0) {
            current[index] = alarm
        } else {
            current.add(alarm)
        }
        persistAlarms(current)
    }

    fun updateAlarmEnabled(id: Long, enabled: Boolean) {
        val current = getAllAlarms().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            current[index] = current[index].copy(isEnabled = enabled)
            persistAlarms(current)
        }
    }

    fun deleteAlarm(id: Long) {
        val current = getAllAlarms().filterNot { it.id == id }
        persistAlarms(current)
    }

    private fun persistAlarms(alarms: List<AlarmItem>) {
        val array = JSONArray()
        alarms.forEach { array.put(it.toJson()) }
        prefs.edit().putString("saved_alarms", array.toString()).apply()
    }
}
