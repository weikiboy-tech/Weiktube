package com.weiktube.app

import java.util.concurrent.atomic.AtomicLong

data class ShieldConfig(
    val enabled: Boolean = true,
    val ads: Boolean = true,
    val trackers: Boolean = true,
    val custom: Set<String> = emptySet(),
    val exceptions: Set<String> = emptySet()
)

/** Domain-only filter. No playback URL rewriting, response modification or cosmetic filtering. */
class ShieldEngine {
    @Volatile var config = ShieldConfig()
    val blocked = AtomicLong(0)
    val ads = setOf("doubleclick.net", "googlesyndication.com", "adnxs.com", "ads.weiktube.invalid")
    val trackers = setOf("google-analytics.com", "analytics.google.com", "connect.facebook.net", "scorecardresearch.com")

    fun shouldBlock(url: String, topLevelUrl: String, mainFrame: Boolean = false): Boolean {
        val c = config
        if (!c.enabled || mainFrame) return false
        val top = Links.host(topLevelUrl)
        val host = Links.host(url)
        if (top.isEmpty() || host.isEmpty()) return false
        // YouTube and its embedded player are outside Shields, including third-party ad requests.
        if (youtubeInfrastructure(top) || youtubeInfrastructure(host)) return false
        if (matches(top, c.exceptions) || matches(host, c.exceptions)) return false
        return matches(host, c.custom) || (c.ads && matches(host, ads)) || (c.trackers && matches(host, trackers))
    }

    fun intercept(url: String, topLevelUrl: String, mainFrame: Boolean): Boolean =
        shouldBlock(url, topLevelUrl, mainFrame).also { if (it) blocked.incrementAndGet() }

    private fun youtubeInfrastructure(host: String) = matches(host, setOf(
        "youtube.com", "youtu.be", "youtube-nocookie.com", "googlevideo.com", "ytimg.com", "com.weiktube.app"
    ))

    companion object {
        fun matches(host: String, domains: Set<String>): Boolean {
            var candidate = host.lowercase().trimEnd('.')
            while (candidate.isNotEmpty()) {
                if (candidate in domains) return true
                val dot = candidate.indexOf('.')
                if (dot < 0) break
                candidate = candidate.substring(dot + 1)
            }
            return false
        }

        /** Accept plain domains and hosts-file rows, ignore comments. Reject unsupported filter syntax. */
        fun parse(text: String): Set<String> {
            require(text.length <= 262144) { "Die Liste darf höchstens 256 KiB enthalten." }
            val result = linkedSetOf<String>()
            for (raw in text.lineSequence()) {
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) continue
                val parts = line.split(Regex("\\s+"))
                val names = if (parts.first() in listOf("0.0.0.0", "127.0.0.1", "::1")) parts.drop(1) else parts
                for (name in names) {
                    val domain = name.lowercase().trimEnd('.')
                    if (domain == "localhost") continue
                    require(domain.length <= 253 && domain.contains('.') && domain.split('.').all {
                        it.matches(Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"))
                    }) { "Ungültige Domain: $name. Nur Domains oder Hosts-Format, kein EasyList-Format." }
                    result.add(domain)
                }
            }
            return result
        }
    }
}
