package com.weiktube.app

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject

/** Local public video IDs only; no URLs, account tokens or browsing cookies. */
class PlaybackProgressStore(context: Context) {
    data class Entry(val seconds: Double, val duration: Double) {
        val label: String get() {
            val total = seconds.toInt()
            return if (total >= 3600) "%d:%02d:%02d".format(total / 3600, total / 60 % 60, total % 60)
            else "%d:%02d".format(total / 60, total % 60)
        }
    }
    private val prefs = context.applicationContext.getSharedPreferences("video_progress", Context.MODE_PRIVATE)
    private val revision = mutableStateOf(0)
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) { prefs.edit().putBoolean("enabled", value).apply(); revision.value++ }

    fun get(url: String): Entry? {
        revision.value
        if (!enabled || Links.isShorts(url)) return null
        val id = Links.videoId(url) ?: return null
        return runCatching {
            val data = JSONObject(prefs.getString(id, "")!!)
            val seconds = data.getDouble("seconds")
            val duration = data.getDouble("duration")
            if (seconds.isFinite() && duration.isFinite() && seconds > 0 && seconds < duration)
                Entry(seconds, duration) else null
        }.getOrNull()
    }

    fun save(url: String, seconds: Double, duration: Double, ended: Boolean = false, force: Boolean = false) {
        if (!enabled || Links.isShorts(url) || !seconds.isFinite() || !duration.isFinite() || duration <= 0) return
        val id = Links.videoId(url) ?: return
        if (ended) { remove(url); return }
        // Loading/empty media snapshots must never replace a useful position with zero.
        if (seconds < 1 || seconds >= duration) return
        val old = get(url)
        if (!force && old != null && kotlin.math.abs(old.seconds - seconds) < 5 && old.duration == duration) return
        val edit = prefs.edit().putString(id, JSONObject().put("seconds", seconds).put("duration", duration)
            .put("updated", System.currentTimeMillis()).toString())
        val entries = prefs.all.filterKeys { it.length == 11 && it != id }.entries.sortedBy {
            runCatching { JSONObject(it.value as String).optLong("updated") }.getOrDefault(0)
        }
        entries.take((entries.size - 99).coerceAtLeast(0)).forEach { edit.remove(it.key) }
        edit.apply(); revision.value++
    }
    fun remove(url: String) { Links.videoId(url)?.let { prefs.edit().remove(it).apply(); revision.value++ } }
    fun clear() { val keepEnabled = enabled; prefs.edit().clear().putBoolean("enabled", keepEnabled).apply(); revision.value++ }
}
