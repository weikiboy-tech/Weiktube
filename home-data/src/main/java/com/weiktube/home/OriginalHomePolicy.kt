package com.weiktube.home

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/** Public navigation policy only. It never receives credentials or extracts media URLs. */
object OriginalHomePolicy {
    private val youtubeHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com")
    private val webAuthHosts = setOf("accounts.google.com", "accounts.youtube.com", "consent.google.com", "consent.youtube.com")
    private val challengeHosts = setOf("www.google.com", "recaptcha.google.com", "www.recaptcha.net")
    private val googleAuthHosts = webAuthHosts + challengeHosts + setOf("www.google.com", "google.com", "oauth2.googleapis.com", "www.googleapis.com")
    private val videoId = Regex("[A-Za-z0-9_-]{11}")
    private val accountPath = Regex("^/(?:signin|login|ServiceLogin|account_login|consent|account|account_switcher|channel_switcher|select_site|t/signin)(?:/|$)", RegexOption.IGNORE_CASE)
    private val oauthPath = Regex("(?:^|/)(?:oauth|oauth2)(?:/|$)", RegexOption.IGNORE_CASE)

    private fun uri(raw: String): URI? {
        if (raw.length !in 1..8192 || raw.any { it <= ' ' || it == '\\' || it == '\u007f' }) return null
        return runCatching { URI(raw) }.getOrNull()?.takeIf {
            it.scheme.equals("https", ignoreCase = true) && it.host != null && it.rawUserInfo == null && it.port in setOf(-1, 443)
        }
    }
    private fun host(uri: URI) = uri.host.lowercase(Locale.ROOT)
    private fun query(uri: URI): List<Pair<String, String>>? = runCatching {
        uri.rawQuery.orEmpty().split('&').filter(String::isNotEmpty).map { part ->
            URLDecoder.decode(part.substringBefore('='), "UTF-8") to URLDecoder.decode(part.substringAfter('=', ""), "UTF-8")
        }
    }.getOrNull()

    /** Only an explicit authorization endpoint/request is OAuth; normal web sign-in remains allowed. */
    fun isExplicitOAuthUrl(raw: String): Boolean {
        val u = uri(raw) ?: return false
        if (host(u) !in googleAuthHosts) return false
        if (host(u) == "oauth2.googleapis.com" || oauthPath.containsMatchIn(u.path.orEmpty())) return true
        val names = query(u)?.map { it.first }?.toSet().orEmpty()
        return "client_id" in names && "response_type" in names
    }

    /** A regular YouTube document, excluding sign-in/profile/consent surfaces. */
    fun isYoutubePage(raw: String): Boolean {
        val u = uri(raw) ?: return false
        return host(u) in youtubeHosts && !accountPath.containsMatchIn(u.path.orEmpty())
    }

    fun isHomePage(raw: String): Boolean {
        val u = uri(raw) ?: return false
        return isYoutubePage(raw) && u.path.orEmpty() in setOf("", "/", "/feed/what_to_watch")
    }

    /** Recognize player routes independently of ID validation, so malformed routes never play in Gecko. */
    fun isVideoNavigation(raw: String): Boolean {
        val u = uri(raw) ?: return false
        if (host(u) == "youtu.be") return true
        if (host(u) !in youtubeHosts) return false
        val path = u.path.orEmpty()
        return path == "/watch" || path.startsWith("/watch/") || path == "/shorts" || path.startsWith("/shorts/") ||
            path == "/live" || path.startsWith("/live/") || path == "/embed" || path.startsWith("/embed/") ||
            path == "/v" || path.startsWith("/v/")
    }

    fun isNormalWebAuthUrl(raw: String): Boolean {
        val u = uri(raw) ?: return false
        return !isExplicitOAuthUrl(raw) && (host(u) in webAuthHosts ||
            host(u) in youtubeHosts && accountPath.containsMatchIn(u.path.orEmpty()))
    }

    fun allowedNavigation(raw: String): Boolean {
        val u = uri(raw) ?: return false
        return !isExplicitOAuthUrl(raw) && (host(u) in youtubeHosts || isNormalWebAuthUrl(raw) || videoUrl(raw) != null)
    }

    /** Challenge frames only; callers must require a normal web-auth parent. Never a top-level allowance. */
    fun allowedAuthSubframe(raw: String): Boolean {
        val u = uri(raw) ?: return false
        val path = u.path.orEmpty()
        return !isExplicitOAuthUrl(raw) && host(u) in challengeHosts && path.startsWith("/recaptcha/") &&
            path.split('/').none { it == "." || it == ".." }
    }

    /** Carry only a public video ID across the player boundary; drop all other query/fragment data. */
    fun videoUrl(raw: String): String? {
        val u = uri(raw) ?: return null
        val name = host(u)
        if (name !in youtubeHosts && name != "youtu.be") return null
        val path = u.rawPath.orEmpty()
        val segments = path.removePrefix("/").split('/')
        val id = when {
            name == "youtu.be" && segments.size == 1 -> segments[0]
            name in youtubeHosts && path == "/watch" -> query(u)?.filter { it.first == "v" }?.singleOrNull()?.second
            name in youtubeHosts && segments.size == 2 && segments[0] in setOf("shorts", "live") -> segments[1]
            else -> null
        }
        return id?.takeIf(videoId::matches)?.let { "https://www.youtube.com/watch?v=$it" }
    }
}
