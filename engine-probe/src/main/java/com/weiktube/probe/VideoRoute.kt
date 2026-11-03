package com.weiktube.probe

import java.net.URI
import java.net.URLDecoder

data class VideoRoute(val id: String, val seconds: Int = 0, val playlist: String? = null) {
    val url: String get() = "https://m.youtube.com/watch?v=$id" +
        (if (seconds > 0) "&t=$seconds" else "") + (playlist?.let { "&list=$it" } ?: "")
    companion object {
        fun youtube(url: String): Boolean = runCatching {
            val u = URI(url)
            u.scheme == "https" && u.rawUserInfo == null && u.port == -1 &&
                u.host in setOf("m.youtube.com", "www.youtube.com", "youtube.com", "youtu.be") && url.length <= 8192
        }.getOrDefault(false)
        fun parse(url: String): VideoRoute? = runCatching {
            if (!youtube(url)) return null
            val u = URI(url)
            val q = u.rawQuery.orEmpty().split('&').associate {
                URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
            }
            val parts = u.path.orEmpty().split('/').filter { it.isNotEmpty() }
            val id = when {
                u.host == "youtu.be" && parts.size == 1 -> parts[0]
                u.path == "/watch" -> q["v"]
                parts.size == 2 && parts[0] in setOf("shorts", "live", "embed") -> parts[1]
                else -> null
            }?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) } ?: return null
            val t = (q["t"] ?: q["start"]).orEmpty()
            val seconds = t.toLongOrNull() ?: Regex("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?").matchEntire(t)?.let {
                fun bounded(part: String) = if (part.isEmpty()) 0L else (part.toLongOrNull() ?: 86400L).coerceIn(0, 86400)
                bounded(it.groupValues[1]) * 3600 + bounded(it.groupValues[2]) * 60 + bounded(it.groupValues[3])
            } ?: 0
            VideoRoute(id, seconds.coerceIn(0, 86400).toInt(), q["list"]?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,128}")) })
        }.getOrNull()
    }
}
