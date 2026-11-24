package com.weiktube.app

import androidx.datastore.preferences.core.*

/** Stored in the existing DataStore; unknown values safely fall back on upgrade. */
data class PlayerPreferences(
    val brightness: Boolean = true, val volume: Boolean = true,
    val doubleTap: Boolean = true, val seekSeconds: Int = 10,
    val hold: Boolean = true, val holdSpeed: Float = 2f,
    val wifiQuality: Int = 1080, val mobileQuality: Int = 720,
    val dislikes: Boolean = false,
) {
    companion object {
        val steps = listOf(5, 10, 15, 20, 30)
        val speeds = listOf(1.5f, 2f, 2.5f, 3f)
        val qualities = listOf(0, 144, 240, 360, 480, 720, 1080, 1440, 2160)
        fun read(p: androidx.datastore.preferences.core.Preferences) = PlayerPreferences(
            p[booleanPreferencesKey("playerBrightness")] ?: true,
            p[booleanPreferencesKey("playerVolume")] ?: true,
            p[booleanPreferencesKey("playerDoubleTap")] ?: true,
            p[stringPreferencesKey("playerSeek")]?.toIntOrNull()?.takeIf { it in steps } ?: 10,
            p[booleanPreferencesKey("playerHold")] ?: true,
            p[stringPreferencesKey("playerHoldSpeed")]?.toFloatOrNull()?.takeIf { it in speeds } ?: 2f,
            p[stringPreferencesKey("playerWifiQuality")]?.toIntOrNull()?.takeIf { it in qualities } ?: 1080,
            p[stringPreferencesKey("playerMobileQuality")]?.toIntOrNull()?.takeIf { it in qualities } ?: 720,
            p[booleanPreferencesKey("playerDislikes")] ?: false,
        )
    }
}
