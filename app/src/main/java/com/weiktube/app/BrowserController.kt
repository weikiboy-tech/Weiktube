package com.weiktube.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.runtime.*
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.ScriptHandler
import java.io.ByteArrayInputStream

/** Long-lived feed browser. Public watch URLs are handed to a separate persistent watch surface. */
@SuppressLint("SetJavaScriptEnabled")
class BrowserController(private val activity: MainActivity, private val shield: ShieldEngine, private val onVideo: (String) -> Unit) {
    var progress by mutableFloatStateOf(0f); private set
    var error by mutableStateOf<String?>(null); private set
    var currentUrl by mutableStateOf(HOME); private set
    var canGoBack by mutableStateOf(false); private set
    var searchActive by mutableStateOf(false); private set
    var mediaPlaying by mutableStateOf(false); private set
    @Volatile private var topLevel = HOME
    private var lastBrowse = HOME
    private var rendererAlive = true
    private var destroyed = false
    private var darkTheme = true
    private var visible = true
    private var forceSearch = false
    private var generation = 0
    private var rescuingSpa = false
    private var lastOpenedUrl = ""
    private var lastOpenedAt = 0L
    private var documentScript: ScriptHandler? = null
    var nativeChrome: Boolean = true
        set(value) { field = value; refreshDocumentScript(); applyPageSettings() }
    var playbackElsewhere: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            refreshDocumentScript()
            applyPageSettings()
            if (value) pausePreviewMedia()
        }
    var view: WebView by mutableStateOf(createWebView())
        private set

    private fun alive(web: WebView) = !destroyed && rendererAlive && web === view

    private fun createWebView(): WebView = WebView(activity).apply {
        generation++
        setBackgroundColor(if (darkTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.mediaPlaybackRequiresUserGesture = true
        settings.setSupportMultipleWindows(false)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.START_SAFE_BROWSING)) WebViewCompat.startSafeBrowsing(activity) {}
        // The page supplies its own theme; never invert rendered video/ad pixels.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(this, "WeiktubeNavigation", YOUTUBE_ORIGINS) { web, message, origin, mainFrame, _ ->
                // No arbitrary native action, token, cookie, credential or page data is exposed.
                if (alive(web) && mainFrame && origin.toString().trimEnd('/') in YOUTUBE_ORIGINS) {
                    val raw = runCatching { message.data }.getOrNull().orEmpty()
                    if (raw.length <= 8192) Links.browseWatchUrl(raw)?.let(::dispatchVideo)
                }
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            documentScript = WebViewCompat.addDocumentStartJavaScript(this, configuredScript(), YOUTUBE_ORIGINS)
        }
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(web: WebView, request: WebResourceRequest): WebResourceResponse? {
                return if (shield.intercept(request.url.toString(), topLevel, request.isForMainFrame))
                    WebResourceResponse("text/plain", "UTF-8", 200, "OK", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0))) else null
            }
            override fun shouldOverrideUrlLoading(web: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame || !alive(web)) return false
                val url = request.url.toString()
                Links.browseWatchUrl(url)?.let { dispatchVideo(it); return true }
                if (!Links.https(url) || Links.host(url) == "accounts.google.com") {
                    val upgraded = Links.canonicalYoutubeUrl(url)
                    if (upgraded != null && !Links.https(url)) navigate(upgraded)
                    else activity.external(url)
                    return true
                }
                return false
            }
            override fun doUpdateVisitedHistory(web: WebView, url: String?, isReload: Boolean) {
                if (!alive(web) || url == null || url == "about:blank") return
                val watch = Links.browseWatchUrl(url)
                if (watch != null && !rescuingSpa) {
                    // Fallback for programmatic SPA navigation without a clicked anchor. Return through
                    // the website's own history, preserving its feed/scroll instead of loading Home.
                    rescuingSpa = true
                    pausePreviewMedia()
                    dispatchVideo(watch)
                    web.post {
                        if (!alive(web)) return@post
                        if (web.canGoBack()) web.goBack() else {
                            currentUrl = url
                            topLevel = url
                            error = "Die vorherige Feedseite konnte nicht wiederhergestellt werden. Zurück oder neu laden."
                            rescuingSpa = false
                        }
                    }
                    return
                }
                if (watch == null) {
                    rescuingSpa = false
                    lastBrowse = url
                    currentUrl = url
                    topLevel = url
                }
                refreshHistory()
                applyPageSettings()
            }
            override fun onPageStarted(web: WebView, url: String, favicon: Bitmap?) {
                if (!alive(web)) return
                generation++
                mediaPlaying = false
                topLevel = url
                if (Links.browseWatchUrl(url) == null) currentUrl = url
                error = null
                this@BrowserController.progress = 0.1f
                refreshHistory()
            }
            override fun onPageFinished(web: WebView, url: String) {
                if (!alive(web)) return
                this@BrowserController.progress = 1f
                refreshHistory()
                applyPageSettings()
                if (playbackElsewhere || !visible) pausePreviewMedia()
            }
            override fun onReceivedError(web: WebView, request: WebResourceRequest, failure: WebResourceError) {
                if (alive(web) && request.isForMainFrame && request.url.toString() == currentUrl) {
                    error = "Seite nicht erreichbar. Verbindung prüfen und erneut laden."
                    this@BrowserController.progress = 1f
                }
            }
            override fun onReceivedHttpError(web: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (alive(web) && request.isForMainFrame && request.url.toString() == currentUrl && response.statusCode >= 400)
                    error = "Die Website meldet Fehler ${response.statusCode}."
            }
            override fun onRenderProcessGone(web: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (web === view) {
                    rendererAlive = false
                    generation++
                    canGoBack = false
                    mediaPlaying = false
                    error = "Die Webansicht wurde beendet. Mit „Erneut versuchen“ wird sie wiederhergestellt."
                    this@BrowserController.progress = 1f
                }
                (web.parent as? ViewGroup)?.removeView(web)
                web.destroy()
                return true
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(web: WebView, newProgress: Int) { if (alive(web)) this@BrowserController.progress = newProgress / 100f }
            override fun onShowCustomView(webView: android.view.View, callback: CustomViewCallback) { activity.showFullscreen(webView, callback) }
            override fun onHideCustomView() { activity.hideFullscreen() }
        }
    }

    private fun dispatchVideo(url: String) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (lastOpenedUrl == url && now - lastOpenedAt < 600) return
        lastOpenedUrl = url; lastOpenedAt = now
        closeSearch()
        pausePreviewMedia()
        onVideo(url)
    }

    @Suppress("UNUSED_PARAMETER")
    fun navigate(url: String, allowWebPlayback: Boolean = false) {
        if (destroyed) return
        Links.browseWatchUrl(url)?.let { dispatchVideo(it); return }
        val target = if (Links.youtube(url) && !Links.https(url)) Links.canonicalYoutubeUrl(url) ?: url else url.trim()
        if (!Links.https(target)) { error = "Nur sichere HTTPS-Adressen werden unterstützt."; return }
        forceSearch = false; searchActive = false
        if (!rendererAlive) recreate()
        rescuingSpa = false
        error = null; currentUrl = target; topLevel = target
        view.loadUrl(target)
    }
    fun reload() {
        if (destroyed) return
        if (!rendererAlive) { recover(); return }
        error = null
        view.reload()
    }
    private fun recreate() {
        rendererAlive = true
        view = createWebView()
        if (!visible) view.onPause()
    }
    fun recover() {
        if (destroyed) return
        if (!rendererAlive) recreate()
        val target = currentUrl.takeIf { Links.https(it) && Links.browseWatchUrl(it) == null }
            ?: lastBrowse.takeIf(Links::https) ?: HOME
        navigate(target)
    }
    fun goBack(): Boolean {
        if (!rendererAlive || destroyed) return false
        if (closeSearch()) return true
        if (!view.canGoBack()) { refreshHistory(); return false }
        view.goBack(); return true
    }
    fun resume() {
        visible = true
        if (!rendererAlive || destroyed) return
        refreshDocumentScript()
        view.onResume()
        applyPageSettings()
    }
    fun pauseMedia() {
        visible = false
        mediaPlaying = false
        if (!rendererAlive || destroyed) return
        refreshDocumentScript()
        applyPageSettings()
        pausePreviewMedia()
        view.onPause()
    }
    private fun pausePreviewMedia() {
        if (!rendererAlive || destroyed) return
        view.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(v){v.pause()})", null)
    }
    fun setTheme(dark: Boolean) {
        darkTheme = dark
        if (!rendererAlive || destroyed) return
        refreshDocumentScript()
        view.setBackgroundColor(if (dark) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        applyPageSettings()
    }
    private fun applyPageSettings() {
        if (!rendererAlive || destroyed || !Links.youtube(currentUrl)) return
        view.evaluateJavascript(configuredScript(), null)
    }
    private fun configuredScript() = "window.weiktubeChromeEnabled=$nativeChrome;window.weiktubePausePreview=${playbackElsewhere || !visible};window.weiktubeDark=$darkTheme;window.weiktubeForceSearch=$forceSearch;" + BROWSER_SCRIPT
    private fun refreshDocumentScript() {
        if (!rendererAlive || destroyed || !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        documentScript?.remove()
        documentScript = WebViewCompat.addDocumentStartJavaScript(view, configuredScript(), YOUTUBE_ORIGINS)
    }
    private fun refreshHistory() { if (rendererAlive && !destroyed) canGoBack = view.canGoBack() }
    /** Called from the visible app's existing small status tick; no background timer. */
    fun poll() {
        if (!rendererAlive || destroyed) return
        refreshHistory()
        if (!visible || !Links.youtube(currentUrl)) return
        val expectedGeneration = generation
        val expectedUrl = currentUrl
        view.evaluateJavascript("JSON.stringify({active:!!(window.weiktubeSearchActive&&window.weiktubeSearchActive()),forced:!!window.weiktubeForceSearch,playing:Array.from(document.querySelectorAll('video,audio')).some(function(v){return !v.paused&&!v.ended&&v.readyState>2})})") { value ->
            if (!destroyed && rendererAlive && generation == expectedGeneration && currentUrl == expectedUrl) runCatching {
                val data = org.json.JSONObject(org.json.JSONTokener(value).nextValue() as String)
                searchActive = data.optBoolean("active")
                forceSearch = data.optBoolean("forced")
                mediaPlaying = visible && !playbackElsewhere && data.optBoolean("playing")
            }
        }
    }
    /** Show the real website search instead of implementing an unofficial suggestion API. */
    fun openYoutubeSearch() {
        if (destroyed) return
        if (!rendererAlive) recover()
        forceSearch = true; searchActive = true
        if (!Links.youtube(currentUrl)) {
            currentUrl = HOME; topLevel = HOME; view.loadUrl(HOME)
            return
        }
        applyPageSettings()
        view.evaluateJavascript("""
            (function(){
              var topbar=document.querySelector('ytm-mobile-topbar-renderer');
              var input=topbar&&topbar.querySelector('input');
              if(input&&input.getClientRects().length){input.focus();return;}
              var controls=topbar?Array.from(topbar.querySelectorAll('button,[role="button"]')):[];
              var search=controls.find(function(b){return /search|such/i.test(b.getAttribute('aria-label')||b.getAttribute('title')||'');});
              if(search)search.click();
              setTimeout(function(){var i=document.querySelector('ytm-mobile-topbar-renderer input');if(i)i.focus();},100);
            })()
        """.trimIndent(), null)
    }
    fun closeSearch(): Boolean {
        if (!searchActive && !forceSearch) return false
        forceSearch = false; searchActive = false
        if (rendererAlive && !destroyed) {
            view.evaluateJavascript("(function(){var t=document.querySelector('ytm-mobile-topbar-renderer');var b=t&&Array.from(t.querySelectorAll('button')).find(function(x){return /back|zurück|cancel|abbrechen/i.test(x.getAttribute('aria-label')||'')});if(b)b.click();if(document.activeElement)document.activeElement.blur();})()", null)
            applyPageSettings()
        }
        return true
    }
    fun openAccount() { navigate("https://m.youtube.com/feed/you") }
    fun testShield() {
        if (destroyed) return
        if (!rendererAlive) recreate()
        forceSearch = false; searchActive = false
        topLevel = "https://shield.weiktube.invalid/"; currentUrl = topLevel
        view.loadDataWithBaseURL(topLevel, """
            <html><meta name="viewport" content="width=device-width,initial-scale=1">
            <body style="background:#10151d;color:#f0f4fa;font:18px sans-serif;padding:24px">
            <h1>Weiktube Shields</h1><p>Diese lokale Prüfseite fordert genau eine Test-Werberessource an.</p>
            <p>Bei aktivem Werbefilter erhöht sich der Sitzungszähler um eins. Bei deaktiviertem Filter bleibt er unverändert.</p>
            <p>Es werden keine echten Werbeanbieter kontaktiert. Die Testadresse verwendet die reservierte Endung .invalid.</p>
            <img src="https://ads.weiktube.invalid/banner.gif?${System.nanoTime()}" alt="Testressource">
            </body></html>
        """.trimIndent(), "text/html", "UTF-8", null)
    }
    fun destroy() {
        if (destroyed) return
        destroyed = true; generation++
        if (rendererAlive) { view.stopLoading(); view.destroy() }
    }

    companion object {
        private const val HOME = "https://m.youtube.com/"
        private val YOUTUBE_ORIGINS = setOf("https://m.youtube.com", "https://www.youtube.com", "https://youtube.com")
        /** Origin-restricted navigation bridge; sends only an already public watch URL. */
        internal val BROWSER_SCRIPT = """
            (function(){
              if(window.top!==window||!['m.youtube.com','www.youtube.com','youtube.com'].includes(location.hostname)||location.pathname.startsWith('/embed/'))return;
              if(window.weiktubeBrowserInstalled){if(window.weiktubeApplyChrome)window.weiktubeApplyChrome();return;}
              window.weiktubeBrowserInstalled=true;
              if(typeof window.weiktubeChromeEnabled!=='boolean')window.weiktubeChromeEnabled=true;
              if(typeof window.weiktubePausePreview!=='boolean')window.weiktubePausePreview=false;
              if(typeof window.weiktubeForceSearch!=='boolean')window.weiktubeForceSearch=false;
              if(typeof window.weiktubeDark!=='boolean')window.weiktubeDark=true;
              document.addEventListener('click',function(e){
                if(!e.isTrusted||e.defaultPrevented||e.button>0||e.ctrlKey||e.metaKey||e.shiftKey||e.altKey)return;
                var button=e.target&&e.target.closest?e.target.closest('ytm-mobile-topbar-renderer button'):null;
                if(button&&/back|zurück|cancel|abbrechen/i.test(button.getAttribute('aria-label')||''))window.weiktubeForceSearch=false;
                var anchor=e.target&&e.target.closest?e.target.closest('a[href]'):null;
                if(!anchor||!window.WeiktubeNavigation)return;
                var u;try{u=new URL(anchor.href,location.href);}catch(ignore){return;}
                if(!['youtube.com','www.youtube.com','m.youtube.com','youtu.be'].includes(u.hostname)||u.username||u.password||!['https:','http:'].includes(u.protocol)||u.port)return;
                var pieces=u.pathname.split('/');var id=u.hostname==='youtu.be'?u.pathname.slice(1):u.pathname==='/watch'?u.searchParams.get('v'):(pieces.length===3&&['live','embed'].includes(pieces[1])?pieces[2]:null);
                if(!/^[A-Za-z0-9_-]{11}$/.test(id||''))return;
                e.preventDefault();e.stopImmediatePropagation();window.WeiktubeNavigation.postMessage(u.href);
              },true);
              document.addEventListener('play',function(e){if(window.weiktubePausePreview&&e.target&&e.target.pause)e.target.pause();},true);
              window.weiktubeSearchActive=function(){
                var t=document.querySelector('ytm-mobile-topbar-renderer');
                var i=t&&t.querySelector('input');
                return !!(window.weiktubeForceSearch||(i&&i.getClientRects().length&&i.type!=='hidden'));
              };
              var css='html[data-weiktube-chrome="1"] ytm-pivot-bar-renderer{display:none!important}'+
                'html[data-weiktube-chrome="1"]:not([data-weiktube-search="1"]) ytm-mobile-topbar-renderer{display:none!important}'+
                'html[data-weiktube-chrome="1"] ytm-app{padding-bottom:0!important}'+
                'html[data-weiktube-chrome="1"]:not([data-weiktube-search="1"]) ytm-app{padding-top:0!important}'+
                'html[data-weiktube-theme="dark"]{color-scheme:dark;--yt-spec-base-background:#0f0f0f;--yt-spec-general-background-a:#0f0f0f;--yt-spec-text-primary:#f1f1f1}'+
                'html[data-weiktube-theme="light"]{color-scheme:light;--yt-spec-base-background:#fff;--yt-spec-general-background-a:#fff;--yt-spec-text-primary:#0f0f0f}';
              window.weiktubeApplyChrome=function(){
                var root=document.documentElement;if(!root)return;
                root.setAttribute('data-weiktube-chrome',window.weiktubeChromeEnabled===false?'0':'1');
                root.setAttribute('data-weiktube-search',window.weiktubeSearchActive()?'1':'0');
                root.setAttribute('data-weiktube-theme',window.weiktubeDark?'dark':'light');
                if(!document.getElementById('weiktube-navigation-style')){
                  var style=document.createElement('style');style.id='weiktube-navigation-style';style.textContent=css;(document.head||root).appendChild(style);
                }
              };
              var pending=false;
              new MutationObserver(function(){if(pending)return;pending=true;requestAnimationFrame(function(){pending=false;window.weiktubeApplyChrome();});}).observe(document,{childList:true,subtree:true});
              window.addEventListener('popstate',function(){window.weiktubeForceSearch=false;window.weiktubeApplyChrome();});
              document.addEventListener('focusin',function(){window.weiktubeApplyChrome();});
              document.addEventListener('focusout',function(){setTimeout(window.weiktubeApplyChrome,0);});
              window.weiktubeApplyChrome();
            })();
        """.trimIndent()
    }
}

