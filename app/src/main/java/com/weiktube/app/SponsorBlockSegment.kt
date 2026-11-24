package com.weiktube.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest

enum class SponsorMode(val label: String) { AUTO("Automatisch"), MANUAL("Manuell"), OFF("Deaktiviert") }
internal enum class SponsorCategory(val key: String, val label: String, val color: String, val defaultMode: SponsorMode) {
    SPONSOR("sponsor", "Sponsor", "#00d400", SponsorMode.AUTO),
    SELFPROMO("selfpromo", "Eigenwerbung", "#ffff00", SponsorMode.AUTO),
    INTERACTION("interaction", "Like-/Abo-Aufruf", "#cc00ff", SponsorMode.AUTO),
    INTRO("intro", "Intro", "#00ffff", SponsorMode.MANUAL),
    OUTRO("outro", "Outro", "#0202ed", SponsorMode.MANUAL),
    PREVIEW("preview", "Vorschau/Rückblick", "#008fd6", SponsorMode.OFF),
    MUSIC("music_offtopic", "Musikfremder Abschnitt", "#ff9900", SponsorMode.OFF),
    FILLER("filler", "Füllmaterial", "#7300ff", SponsorMode.OFF);
    companion object { fun from(key: String) = entries.firstOrNull { it.key == key } }
}
internal data class SponsorBlockSegment(val start: Double, val end: Double, val category: SponsorCategory,
    val uuid: String, val videoDuration: Double, val votes: Int = 0, val locked: Boolean = false) {
    fun json() = JSONObject().put("start", start).put("end", end).put("category", category.key)
        .put("uuid", uuid).put("videoDuration", videoDuration).put("votes", votes).put("locked", locked)
        .put("color", category.color).put("actionType", "skip")
}
internal object SponsorBlockCodec {
    fun validId(id: String) = id.matches(Regex("[A-Za-z0-9_-]{11}"))
    fun prefix(id: String): String {
        require(validId(id))
        return MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
            .take(2).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    fun url(id: String): String {
        val categories = JSONArray(SponsorCategory.entries.map { it.key }).toString()
        return "https://sponsor.ajay.app/api/skipSegments/${prefix(id)}?categories=${URLEncoder.encode(categories, "UTF-8")}&actionType=skip&service=YouTube"
    }
    /** Hash bucket members are filtered locally; only exact requested video IDs are retained. */
    fun response(raw: String, id: String): List<SponsorBlockSegment> {
        require(validId(id) && raw.length <= 2_097_152)
        val bucket = JSONArray(raw)
        require(bucket.length() <= 2048)
        val result = mutableListOf<SponsorBlockSegment>()
        for (index in 0 until bucket.length()) {
            val member = bucket.optJSONObject(index) ?: continue
            if (member.optString("videoID") != id) continue
            val segments = member.optJSONArray("segments") ?: continue
            require(segments.length() <= 512)
            for (i in 0 until segments.length()) {
                val value = segments.optJSONObject(i) ?: continue
                if (value.optString("actionType") != "skip") continue
                val category = SponsorCategory.from(value.optString("category")) ?: continue
                val times = value.optJSONArray("segment") ?: continue
                if (times.length() != 2) continue
                val start = (times.opt(0) as? Number)?.toDouble() ?: continue
                val end = (times.opt(1) as? Number)?.toDouble() ?: continue
                val duration = (value.opt("videoDuration") as? Number)?.toDouble() ?: continue
                val uuid = value.optString("UUID")
                if (!start.isFinite() || !end.isFinite() || !duration.isFinite() || start < 0 || end <= start ||
                    end > 604800 || duration < 0 || duration > 604800 || (duration > 0 && end > duration + 1) ||
                    !uuid.matches(Regex("[A-Za-z0-9_-]{1,128}"))) continue
                result += SponsorBlockSegment(start, end, category, uuid, duration,
                    value.optInt("votes", 0), value.optInt("locked", 0) > 0)
            }
        }
        return result.distinctBy { it.uuid }.sortedWith(compareBy({ it.start }, { it.end })).take(512)
    }
    fun cache(id: String, segments: List<SponsorBlockSegment>): String = JSONArray().put(JSONObject()
        .put("videoID", id).put("segments", JSONArray().also { array -> segments.forEach {
            array.put(JSONObject().put("segment", JSONArray().put(it.start).put(it.end)).put("category", it.category.key)
                .put("UUID", it.uuid).put("videoDuration", it.videoDuration).put("votes", it.votes)
                .put("locked", if(it.locked) 1 else 0).put("actionType", "skip"))
        } })).toString()
}
