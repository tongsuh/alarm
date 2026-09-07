package com.example.flashalarm.data

import android.content.Context
import com.example.flashalarm.model.FlashProfile
import org.json.JSONArray

class FlashProfileRepository(context: Context) {
    private val prefs = context.getSharedPreferences("flash_profiles_prefs", Context.MODE_PRIVATE)

    fun getAllProfiles(): List<FlashProfile> {
        val jsonStr = prefs.getString("saved_profiles", null)
        if (jsonStr == null) {
            val defaults = listOf(
                FlashProfile.PRESET_APPLE_WATCH_RED,
                FlashProfile.PRESET_SUNRISE,
                FlashProfile.PRESET_STROBE
            )
            saveAll(defaults)
            return defaults
        }
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<FlashProfile>()
            for (i in 0 until jsonArray.length()) {
                list.add(FlashProfile.fromJson(jsonArray.getJSONObject(i)))
            }
            if (list.isEmpty()) {
                listOf(FlashProfile.PRESET_APPLE_WATCH_RED, FlashProfile.PRESET_SUNRISE)
            } else {
                list
            }
        } catch (e: Exception) {
            listOf(FlashProfile.PRESET_APPLE_WATCH_RED, FlashProfile.PRESET_SUNRISE)
        }
    }

    fun getProfileById(id: String): FlashProfile {
        return getAllProfiles().find { it.id == id } ?: FlashProfile.PRESET_APPLE_WATCH_RED
    }

    fun saveProfile(profile: FlashProfile) {
        val current = getAllProfiles().toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            current[index] = profile
        } else {
            current.add(profile)
        }
        saveAll(current)
    }

    fun deleteProfile(id: String) {
        val current = getAllProfiles().filterNot { it.id == id }
        saveAll(current)
    }

    private fun saveAll(profiles: List<FlashProfile>) {
        val array = JSONArray()
        profiles.forEach { array.put(it.toJson()) }
        prefs.edit().putString("saved_profiles", array.toString()).apply()
    }
}
