package com.weiktube.home

import org.json.JSONObject
import java.net.URI

/** Diagnostic output is an allowlist of categories/booleans, never raw page text or auth URLs. */
internal class HomeConnectionDiagnostics {
    data class SafeLocation(val host: String, val address: String, val kind: String)
    var enabled = false
        set(value) { field = value; if (!value) history.clear() }
    var current = locationOf(""); private set
    var titleKind = "unknown"; private set
    var bridgeConnected = false; private set
    private var failure = "none"
    private var blocked: SafeLocation? = null
    private var errorCode: Int? = null
    private var errorCategory: Int? = null
    private var bridgeData = emptyMap<String, String>()
    private val history = ArrayDeque<String>()

    fun location(raw: String, phase: String) {
        val safe = locationOf(raw)
        if (phase == "blocked") blocked = safe else { current = safe; blocked = null; failure = "none" }
        titleKind = "unknown"; errorCode = null; errorCategory = null
        bridgeData = emptyMap()
        if (enabled) {
            val entry = "${if (phase in setOf("start", "location", "blocked", "requested")) phase else "navigation"}: ${safe.address} [${safe.kind}]"
            if (history.lastOrNull() != entry) history.addLast(entry)
            while (history.size > 12) history.removeFirst()
        }
    }
    fun title(raw: String?) { titleKind = titleOf(raw) }
    fun bridge(connected: Boolean) { bridgeConnected = connected; if (!connected) bridgeData = emptyMap() }
    fun error(code: Int, category: Int) { errorCode = code; errorCategory = category; failure = "GECKO_ERROR" }
    fun failure(code: String) { failure = code.takeIf { it in FAILURES } ?: "UNKNOWN_FAILURE" }
    fun details(data: JSONObject?) {
        if (data == null) return
        val values = linkedMapOf<String, String>()
        for (key in listOf("domReady", "bridgeReady", "configPresent", "contextPresent", "clientIdPresent", "apiKeyPresent", "initialDataPresent", "loggedIn")) {
            values[key] = when (data.opt(key)) { true -> "true"; false -> "false"; else -> "unknown" }
        }
        fun enum(key: String, allowed: Set<String>) { values[key] = data.optString(key).takeIf { it in allowed } ?: "unknown" }
        enum("pageKind", setOf("home", "youtube_other", "consent", "signin", "error", "other"))
        enum("titleKind", setOf("youtube", "consent", "google_login", "error", "other"))
        enum("source", setOf("none", "data", "direct", "inline", "combined", "get_only"))
        enum("readyState", setOf("loading", "interactive", "complete", "unknown"))
        enum("step", setOf("ready", "config", "session", "identity", "resolve", "home", "continuation", "verify", "done"))
        for (key in listOf("pageHttpStatus", "httpStatus")) {
            val number = data.opt(key) as? Number
            values[key] = number?.toDouble()?.takeIf { it in 100.0..599.0 && it % 1.0 == 0.0 }?.toInt()?.toString() ?: "unknown"
        }
        val missing = data.optJSONArray("missing")
        values["missing"] = (0 until (missing?.length() ?: 0).coerceAtMost(16))
            .map { missing!!.optString(it) }.filter { it in MISSING }.distinct().joinToString(",").ifBlank { "none" }
        bridgeData = values
    }
    fun missingBridgeCode(): String = when {
        failure == "SIGNIN_EXTERNAL_REQUIRED" -> "SIGNIN_EXTERNAL_REQUIRED"
        current.kind == "PAGE_ERROR" || titleKind == "error" -> "PAGE_ERROR"
        current.kind == "GOOGLE_AUTH" || current.kind == "YOUTUBE_SIGNIN" -> "SIGNIN_EXTERNAL_REQUIRED"
        current.kind == "CONSENT" || titleKind == "consent" -> "CONSENT_REQUIRED"
        errorCode != null -> "GECKO_ERROR"
        current.kind == "YOUTUBE_HOME" -> "BRIDGE_UNAVAILABLE"
        else -> "WRONG_PAGE"
    }
    fun render(): String = buildString {
        appendLine("Engine: GeckoView 159 (kein Android WebView)")
        appendLine("Letzte Dokument-URL: ${current.address}")
        blocked?.let { appendLine("Blockiertes Ziel: ${it.address} [${it.kind}]") }
        appendLine("Host: ${current.host}")
        appendLine("Seitentyp: ${current.kind}; Titelklasse: $titleKind")
        appendLine("Native JS-Verbindung: $bridgeConnected")
        appendLine("Dokument-HTTP: ${bridgeData["pageHttpStatus"] ?: "unknown"} (nur Navigation-Timing; sonst unbekannt)")
        appendLine("API-HTTP: ${bridgeData["httpStatus"] ?: "unknown"} (kein Dokumentstatus)")
        appendLine("Gecko-Fehler: ${errorCode ?: "none"}; Kategorie: ${errorCategory ?: "none"}")
        appendLine("Ergebnis: $failure")
        bridgeData.filterKeys { it !in setOf("pageHttpStatus", "httpStatus") }.forEach { (key, value) -> appendLine("$key: $value") }
        if (enabled) { appendLine("Navigationen (bereinigt, keine Querywerte):"); history.forEach(::appendLine) }
        append("Google-API-Freigabe, Gecko-Websitzung und externer Browser sind getrennte Sitzungen.")
    }

    companion object {
        private val YOUTUBE = setOf("www.youtube.com", "m.youtube.com", "youtube.com")
        private val AUTH = setOf("accounts.google.com", "myaccount.google.com", "oauth2.googleapis.com")
        private val CONSENT = setOf("consent.google.com", "consent.youtube.com")
        private val MISSING = setOf("config", "config_read", "context", "client_name", "client_version", "client_id", "session_index")
        private val FAILURES = setOf("none", "SIGNED_OUT", "CONFIG_UNAVAILABLE", "SESSION_UNAVAILABLE", "PROFILE_UNVERIFIED", "ACCOUNT_MISMATCH", "CONTINUATION_INVALID", "ACCESS_DENIED", "NETWORK_ERROR", "RESPONSE_TOO_LARGE", "RESPONSE_INVALID", "TIMEOUT", "TRANSPORT_UNAVAILABLE", "PAGE_ERROR", "SIGNIN_REQUIRED", "SIGNIN_EXTERNAL_REQUIRED", "CONSENT_REQUIRED", "GECKO_ERROR", "BRIDGE_UNAVAILABLE", "BRIDGE_DISCONNECTED", "WRONG_PAGE", "NO_FEED", "FEED_RECEIVED", "EXTERNAL_BROWSER_OPENED", "UNKNOWN_FAILURE")
        fun isExternalSignIn(raw: String): Boolean = locationOf(raw).kind in setOf("GOOGLE_AUTH", "YOUTUBE_SIGNIN")
        fun allowedEmbedded(raw: String): Boolean {
            val safe = locationOf(raw)
            return safe.host in YOUTUBE + CONSENT && safe.kind !in setOf("GOOGLE_AUTH", "YOUTUBE_SIGNIN")
        }
        fun locationOf(raw: String): SafeLocation {
            val u = runCatching { URI(raw).takeIf { raw.length <= 8192 && it.scheme == "https" && it.rawUserInfo == null && it.port == -1 } }.getOrNull()
                ?: return SafeLocation("unknown", "<keine sichere URL>", "UNKNOWN")
            val host = u.host?.lowercase() ?: return SafeLocation("unknown", "<keine sichere URL>", "UNKNOWN")
            val path = u.path.orEmpty()
            val kind: String
            val shownPath: String
            when {
                host in AUTH || (host in setOf("www.googleapis.com", "www.google.com", "google.com") && (path.startsWith("/oauth2") || path.startsWith("/o/oauth2") || path.startsWith("/accounts"))) -> { kind = "GOOGLE_AUTH"; shownPath = "/<Anmeldung>" }
                host in CONSENT -> { kind = "CONSENT"; shownPath = "/<Zustimmung>" }
                host in setOf("www.google.com", "google.com") && path.startsWith("/sorry") -> { kind = "PAGE_ERROR"; shownPath = "/<Fehlerseite>" }
                host !in YOUTUBE -> return SafeLocation("<anderer Host>", "https://<anderer Host>/<ausgeblendet>", "OTHER_ORIGIN")
                path == "/oops" || path.startsWith("/oops/") || path.startsWith("/error") || path.startsWith("/sorry") -> { kind = "PAGE_ERROR"; shownPath = "/<Fehlerseite>" }
                setOf("/signin", "/t/signin", "/ServiceLogin", "/login", "/account_login").any { path == it || path.startsWith("$it/") } -> { kind = "YOUTUBE_SIGNIN"; shownPath = "/<Anmeldung>" }
                path in setOf("/channel_switcher", "/account_switcher", "/select_site") -> { kind = "PROFILE_SELECTION"; shownPath = "/<Profilauswahl>" }
                path.startsWith("/consent") -> { kind = "CONSENT"; shownPath = "/<Zustimmung>" }
                path.isEmpty() || path == "/" || path == "/feed/what_to_watch" -> { kind = "YOUTUBE_HOME"; shownPath = if (path == "/feed/what_to_watch") path else "/" }
                else -> { kind = "YOUTUBE_OTHER"; shownPath = "/<YouTube-Seite>" }
            }
            return SafeLocation(host, "https://$host$shownPath", kind)
        }
        fun titleOf(raw: String?): String {
            val title = raw.orEmpty().take(1024).lowercase()
            return when {
                title.contains("hoppla") || title.contains("oops") || title.contains("something went wrong") || title.contains("disallowed_useragent") || title.contains("nicht sicher") || title.contains("not be secure") -> "error"
                title.contains("before you continue") || title.contains("bevor sie") || title.contains("consent") || title.contains("zustimmung") -> "consent"
                title.contains("sign in") || title.contains("anmelden") || title.contains("google accounts") -> "google_login"
                title.contains("youtube") -> "youtube"
                title.isEmpty() -> "unknown"
                else -> "other"
            }
        }
    }
}
