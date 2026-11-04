package com.weiktube.app

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.weiktube.filter.FilterSnapshot
import com.weiktube.filter.YoutubeAdBlock
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.UUID

/** One origin-restricted adapter per retained WebView; each document pins a verified policy. */
internal class WebViewAdBlock(
    private val web: WebView,
    private val onReady: () -> Unit,
    private val onBreakthrough: (Boolean) -> Unit,
    private val onFailure: (String) -> Unit,
) {
    private val supported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
        WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
    private var script: ScriptHandler? = null
    @Volatile private var snapshot: FilterSnapshot = YoutubeAdBlock.currentSnapshot()
    @Volatile private var active = false
    @Volatile private var excluded: Set<String> = emptySet()
    @Volatile private var topLevel = "https://m.youtube.com/"
    private var protectedDocument = true
    private var token = ""
    private var epoch = 0L
    private var disposed = false
    var ready = false; private set
    var blocked = false; private set
    val canPlay get() = !active || !protectedDocument || (ready && !blocked)

    init {
        if (supported) WebViewCompat.addWebMessageListener(web, "WeiktubeAdEvents", ORIGINS) { _, message, origin, mainFrame, _ ->
            if (disposed || !mainFrame || origin.toString().trimEnd('/') !in ORIGINS) return@addWebMessageListener
            val raw = runCatching { message.data }.getOrNull() ?: return@addWebMessageListener
            if (raw.length > 2048) return@addWebMessageListener
            runCatching {
                val event = JSONObject(raw)
                if (event.optString("token") != token || event.optInt("version") != snapshot.version) return@runCatching
                when (event.optString("type")) {
                    "ready" -> if (!blocked) { ready = true; YoutubeAdBlock.engineReady("webview"); onReady() }
                    "data", "cosmetic" -> if (active) {
                        val count = event.optInt("count", 0)
                        if (count in 1..10000) YoutubeAdBlock.report(event.getString("type"), count)
                    }
                    "breakthrough" -> if (active && !blocked) {
                        blocked = true
                        YoutubeAdBlock.report("breakthrough")
                        onBreakthrough(event.optBoolean("wasPlaying", false))
                    }
                    "error" -> if (active && !blocked) fail("YouTube-Werbeschutz konnte nicht vollständig gestartet werden. Bitte Filter aktualisieren und erneut versuchen.")
                }
            }
        }
    }

    /** Must run before loadUrl, never after the site's first script. */
    fun prepare(url: String): Boolean {
        if (disposed) return false
        epoch++
        topLevel = url
        protectedDocument = Links.https(url) && Links.host(url) in HOSTS
        snapshot = YoutubeAdBlock.currentSnapshot()
        excluded = YoutubeAdBlock.exceptions.toSet()
        active = YoutubeAdBlock.enabled && !ShieldEngine.matches(Links.host(url), excluded)
        ready = !active || !protectedDocument
        blocked = false
        token = UUID.randomUUID().toString()
        script?.remove(); script = null
        if (active && protectedDocument && !YoutubeAdBlock.state.value.initialized) {
            fail("Das YouTube-Filterpaket ist nicht verfügbar. Bitte die App aktualisieren.")
            return false
        }
        if (active && protectedDocument && !supported) {
            fail("YouTube-Werbeschutz benötigt eine aktuelle Android System WebView. Bitte im Play Store aktualisieren.")
            return false
        }
        if (supported) script = WebViewCompat.addDocumentStartJavaScript(web,
            YoutubeAdBlock.script(snapshot, token, active, excluded), ORIGINS)
        return true
    }

    fun pageStarted(url: String) {
        topLevel = url
        protectedDocument = Links.https(url) && Links.host(url) in HOSTS
        epoch++
        ready = !active || !protectedDocument
        blocked = false
    }

    fun pageFinished() {
        val attempt = epoch
        if (active && protectedDocument && !blocked) {
            // A document-start message can race the platform's onPageStarted callback.
            // Ask the already-installed runtime for status; never reinstall its hooks here.
            web.evaluateJavascript("window.dispatchEvent(new CustomEvent('weiktube-ad-status-request'));", null)
            web.postDelayed({
                if (!disposed && epoch == attempt && !ready && !blocked)
                    fail("YouTube-Werbeschutz ist nicht bereit. Bitte Filter aktualisieren und das Video erneut laden.")
            }, 3000)
        }
    }

    fun intercept(request: WebResourceRequest): WebResourceResponse? =
        if (YoutubeAdBlock.shouldBlock(snapshot, request.url.toString(), topLevel, request.isForMainFrame, active, excluded))
            WebResourceResponse("text/plain", "UTF-8", 200, "OK", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))
        else null

    private fun fail(message: String) {
        blocked = true; ready = false
        YoutubeAdBlock.engineError("webview", if (supported) "FILTER_RUNTIME_ERROR" else "DOCUMENT_START_UNSUPPORTED")
        onFailure(message)
    }
    fun close() {
        disposed = true; epoch++
        script?.remove(); script = null
        if (supported) WebViewCompat.removeWebMessageListener(web, "WeiktubeAdEvents")
    }

    companion object {
        private val HOSTS = setOf("m.youtube.com", "www.youtube.com", "youtube.com")
        private val ORIGINS = setOf("https://m.youtube.com", "https://www.youtube.com", "https://youtube.com")
    }
}
