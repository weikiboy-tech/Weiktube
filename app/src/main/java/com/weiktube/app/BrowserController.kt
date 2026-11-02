package com.weiktube.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.*
import androidx.compose.runtime.*
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
class BrowserController(private val activity: MainActivity, private val shield: ShieldEngine, private val onVideo: (String) -> Unit) {
    var progress by mutableFloatStateOf(0f)
    var error by mutableStateOf<String?>(null)
    var currentUrl by mutableStateOf("https://m.youtube.com/")
    @Volatile private var topLevel = "https://m.youtube.com/"
    private var lastBrowse = topLevel
    var webPlayback = true
    private var rendererAlive = true
    private var searchRequested = false
    /** Only remove duplicate website navigation; embedded video controls and ads are untouched. */
    var nativeChrome: Boolean = true
        set(value) {
            field = value
            applyChrome()
        }
    val view = WebView(activity).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.mediaPlaybackRequiresUserGesture = true
        settings.setSupportMultipleWindows(false)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.START_SAFE_BROWSING)) WebViewCompat.startSafeBrowsing(activity) {}
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(this, CHROME_SCRIPT, setOf(
                "https://m.youtube.com", "https://www.youtube.com", "https://youtube.com"
            ))
        }
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return if (shield.intercept(request.url.toString(), topLevel, request.isForMainFrame))
                    WebResourceResponse("text/plain", "UTF-8", 200, "OK", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0))) else null
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val url = request.url.toString()
                if (!Links.https(url) || Links.host(url) == "accounts.google.com") { activity.external(url); return true }
                if (!webPlayback && Links.videoId(url) != null && !url.contains("/shorts/")) { onVideo(url); return true }
                return false
            }
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                if (url == null || url == "about:blank") return
                currentUrl = url; topLevel = url
                // The mobile site can use History API navigation, bypassing shouldOverrideUrlLoading.
                if (!webPlayback && Links.videoId(url) != null && !url.contains("/shorts/")) {
                    onVideo(url)
                    view.post { view.loadUrl(lastBrowse) }
                } else if (Links.videoId(url) == null) lastBrowse = url
                applyChrome()
            }
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                topLevel = url; currentUrl = url; error = null; this@BrowserController.progress = 0.1f
            }
            override fun onPageFinished(view: WebView, url: String) {
                this@BrowserController.progress = 1f
                applyChrome()
                if (searchRequested && Links.youtube(url)) {
                    searchRequested = false
                    openYoutubeSearch()
                }
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                if (request.isForMainFrame) { error = "Seite nicht erreichbar. Verbindung prüfen und erneut laden."; this@BrowserController.progress = 1f }
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame && response.statusCode >= 400) error = "Die Website meldet Fehler ${response.statusCode}."
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                rendererAlive = false
                error = "WebView wurde beendet. Bitte Weiktube neu starten."; return true
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { this@BrowserController.progress = newProgress / 100f }
            override fun onShowCustomView(view: android.view.View, callback: CustomViewCallback) { activity.showFullscreen(view, callback) }
            override fun onHideCustomView() { activity.hideFullscreen() }
        }
    }
    fun navigate(url: String, allowWebPlayback: Boolean = true) {
        if (!rendererAlive) return
        if (!Links.https(url)) { error = "Nur sichere HTTPS-Adressen werden unterstützt."; return }
        searchRequested = false
        view.evaluateJavascript("document.documentElement.removeAttribute('data-weiktube-search')", null)
        webPlayback = allowWebPlayback
        topLevel = url; currentUrl = url
        view.loadUrl(url)
    }
    private fun applyChrome() {
        if (!rendererAlive || !Links.youtube(currentUrl)) return
        view.evaluateJavascript(CHROME_SCRIPT + ";window.weiktubeChromeEnabled=" + nativeChrome + ";if(window.weiktubeApplyChrome)window.weiktubeApplyChrome();", null)
    }

    /** Uses the real website search so its own search suggestions and accessibility remain available. */
    fun openYoutubeSearch() {
        if (!rendererAlive) return
        if (!Links.youtube(currentUrl)) {
            navigate("https://m.youtube.com/")
            searchRequested = true
            return
        }
        view.onResume()
        view.evaluateJavascript("""
            (function(){
              if(!document.documentElement)return false;
              document.documentElement.setAttribute('data-weiktube-search','1');
              if(window.weiktubeApplyChrome)window.weiktubeApplyChrome();
              var topbar=document.querySelector('ytm-mobile-topbar-renderer');
              var input=topbar&&topbar.querySelector('input');
              if(input&&input.getClientRects().length){input.focus();return true;}
              var buttons=topbar?Array.from(topbar.querySelectorAll('button')):[];
              var button=buttons.find(function(b){return /^(Search|Suchen|Suche)$/i.test(b.getAttribute('aria-label')||'');});
              if(!button)button=document.querySelector('ytm-mobile-topbar-renderer .topbar-menu-button-avatar-button+button,ytm-mobile-topbar-renderer button[aria-label*="Search"],ytm-mobile-topbar-renderer button[aria-label*="Suchen"]');
              if(button){button.click();setTimeout(function(){var i=document.querySelector('ytm-mobile-topbar-renderer input');if(i)i.focus();},100);return true;}
              return false;
            })()
        """.trimIndent()) { result ->
            if (result != "true" && rendererAlive) {
                // The live website may change its selectors; its results route still exposes search.
                navigate("https://m.youtube.com/results?search_query=")
                view.evaluateJavascript("document.documentElement.setAttribute('data-weiktube-search','1')", null)
            }
        }
    }

    fun openAccount() { navigate("https://m.youtube.com/feed/you") }

    fun pauseMedia() {
        if (!rendererAlive) return
        view.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(v){v.pause()})", null)
        view.onPause()
    }
    fun testShield() {
        webPlayback = false
        topLevel = "https://shield.weiktube.invalid/"
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
    fun destroy() { view.stopLoading(); view.destroy() }

    companion object {
        /** Deliberately excludes player elements, advert elements and all embedded frames. */
        private val CHROME_SCRIPT = """
            (function(){
              if(window.top!==window||!['m.youtube.com','www.youtube.com','youtube.com'].includes(location.hostname)||location.pathname.startsWith('/embed/'))return;
              if(window.weiktubeChromeInstalled){if(window.weiktubeApplyChrome)window.weiktubeApplyChrome();return;}
              window.weiktubeChromeInstalled=true;
              window.weiktubeChromeEnabled=true;
              var css='html[data-weiktube-chrome="1"] ytm-pivot-bar-renderer{display:none!important}'+
                'html[data-weiktube-chrome="1"]:not([data-weiktube-search="1"]):not([data-weiktube-watch="1"]) ytm-mobile-topbar-renderer{display:none!important}'+
                'html[data-weiktube-chrome="1"] ytm-app{padding-bottom:0!important}'+
                'html[data-weiktube-chrome="1"]:not([data-weiktube-search="1"]):not([data-weiktube-watch="1"]) ytm-app{padding-top:0!important}';
              window.weiktubeApplyChrome=function(){
                var root=document.documentElement;if(!root)return;
                var enabled=window.weiktubeChromeEnabled!==false;
                var watching=/^\/(watch|shorts|live)(\/|$)/.test(location.pathname);
                if(root.getAttribute('data-weiktube-watch')!==(watching?'1':'0'))root.setAttribute('data-weiktube-watch',watching?'1':'0');
                if(root.getAttribute('data-weiktube-chrome')!==(enabled?'1':'0'))root.setAttribute('data-weiktube-chrome',enabled?'1':'0');
                if(!document.getElementById('weiktube-navigation-style')){
                  var style=document.createElement('style');style.id='weiktube-navigation-style';style.textContent=css;(document.head||root).appendChild(style);
                }
                // Keep YouTube's search field and suggestions visible on its real results route.
                // Native search controls handle result-page queries without duplicate headers.
              };
              var pending=false;
              var observer=new MutationObserver(function(){
                if(pending)return;pending=true;
                requestAnimationFrame(function(){pending=false;window.weiktubeApplyChrome();});
              });
              observer.observe(document,{childList:true,subtree:true});
              window.addEventListener('popstate',window.weiktubeApplyChrome);
              window.weiktubeApplyChrome();
            })();
        """.trimIndent()
    }
}


