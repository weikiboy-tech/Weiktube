package com.weiktube.app

import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.net.URI

/** Narrow public-link handoff from the original watch document. Never reads cookies, auth or media URLs. */
object WatchNavigationScript {
    private val origins = setOf("https://m.youtube.com", "https://www.youtube.com", "https://youtube.com")

    fun allowed(url: String): String? {
        if (url.length > 8192 || !Links.https(url)) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.port !in setOf(-1, 443)) return null
        if (Links.host(url) == "accounts.google.com") return url
        if (!Links.youtube(url)) return null
        if (Links.videoId(url) != null) return Links.canonicalYoutubeUrl(url)
        val path = uri.path.orEmpty()
        val publicPage = path == "/" || path == "/results" || path in setOf("/feed/you", "/feed/library", "/feed/subscriptions") ||
            path.matches(Regex("/(?:channel|user|c)/[^/]+/?")) || path.matches(Regex("/@[^/]+/?")) ||
            (path == "/playlist" && uri.rawQuery.orEmpty().split('&').any { it.startsWith("list=") && Links.decode(it.substringAfter('=')).matches(Regex("[A-Za-z0-9_-]{1,128}")) })
        return if (publicPage) Links.canonicalYoutubeUrl(url) else null
    }

    fun install(webView: WebView, onUrl: (String) -> Unit): Hooks {
        val bridgeSupported = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        if (!bridgeSupported) return Hooks(webView, null, false)
        WebViewCompat.addWebMessageListener(webView, "WeiktubeWatchNavigation", origins) { _, message, origin, mainFrame, _ ->
            if (mainFrame && origin.toString().trimEnd('/') in origins) {
                val raw = runCatching { message.data }.getOrNull() ?: return@addWebMessageListener
                allowed(raw)?.let(onUrl)
            }
        }
        val handler = if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
            WebViewCompat.addDocumentStartJavaScript(webView, SCRIPT, origins) else null
        return Hooks(webView, handler, true)
    }

    class Hooks internal constructor(private val view: WebView, private val handler: ScriptHandler?, private val bridge: Boolean) {
        private var closed = false
        /** Fallback for WebViews without DOCUMENT_START_SCRIPT; also safe after every ordinary page finish. */
        fun apply() { if (!closed && bridge) view.evaluateJavascript(SCRIPT, null) }
        fun close() {
            if (closed) return
            closed = true
            handler?.remove()
            if (bridge) WebViewCompat.removeWebMessageListener(view, "WeiktubeWatchNavigation")
        }
    }

    internal val SCRIPT = """
        (function(){
          if(window.top!==window||!['m.youtube.com','www.youtube.com','youtube.com'].includes(location.hostname)||location.pathname.startsWith('/embed/'))return;
          if(window.weiktubeWatchNavigationInstalled)return;
          window.weiktubeWatchNavigationInstalled=true;
          document.addEventListener('click',function(e){
            if(!e.isTrusted||e.defaultPrevented||e.button>0||e.ctrlKey||e.metaKey||e.altKey||e.shiftKey)return;
            var a=e.target&&e.target.closest?e.target.closest('a[href]'):null;
            if(!a||!window.WeiktubeWatchNavigation)return;
            var u;try{u=new URL(a.href,location.href);}catch(ignore){return;}
            if(u.protocol!=='https:'||u.username||u.password||u.port)return;
            var yt=['m.youtube.com','www.youtube.com','youtube.com','youtu.be'].includes(u.hostname);
            var p=u.pathname,parts=p.split('/');
            var id=u.hostname==='youtu.be'?p.slice(1):p==='/watch'?u.searchParams.get('v'):(parts.length===3&&['live','shorts','embed'].includes(parts[1])?parts[2]:null);
            var video=yt&&/^[A-Za-z0-9_-]{11}$/.test(id||'');
            var channel=yt&&(/^\/(channel|user|c)\/[^/]+\/?$/.test(p)||/^\/@[^/]+\/?$/.test(p));
            var playlist=yt&&p==='/playlist'&&/^[A-Za-z0-9_-]{1,128}$/.test(u.searchParams.get('list')||'');
            var browse=yt&&['/','/results','/feed/you','/feed/library','/feed/subscriptions'].includes(p);
            if(!(video||channel||playlist||browse||u.hostname==='accounts.google.com'))return;
            e.preventDefault();e.stopImmediatePropagation();window.WeiktubeWatchNavigation.postMessage(u.href);
          },true);
        })();
    """.trimIndent()
}
