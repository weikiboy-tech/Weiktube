package com.weiktube.app

import android.app.Activity
import android.media.AudioManager
import android.provider.Settings
import kotlin.math.roundToInt

/** Window-only brightness. Audio uses STREAM_MUSIC, never the ring or alarm stream. */
internal class PlayerWindowControls(private val activity: Activity) {
    private val audio = activity.getSystemService(AudioManager::class.java)
    private var originalBrightness: Float? = null
    private var baseline = 0f
    private var left = false
    fun begin(brightness: Boolean) {
        left = brightness
        baseline = if (left) {
            val current = activity.window.attributes.screenBrightness
            if (originalBrightness == null) originalBrightness = current
            if (current >= 0f) current else
                (Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
        } else {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
        }
    }
    fun change(delta: Float): Int {
        val value = (baseline + delta).coerceIn(if (left) .02f else 0f, 1f)
        if (left) {
            activity.window.attributes = activity.window.attributes.apply { screenBrightness = value }
            return (value * 100).roundToInt()
        }
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        // Fixed-volume devices and DND policy restrictions must not crash the gesture.
        if (!audio.isVolumeFixed) runCatching {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, (value * max).roundToInt().coerceIn(0, max), 0)
        }
        return (audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max).roundToInt()
    }
    fun restore() {
        originalBrightness?.let { value ->
            activity.window.attributes = activity.window.attributes.apply { screenBrightness = value }
        }
        originalBrightness = null
    }
}
