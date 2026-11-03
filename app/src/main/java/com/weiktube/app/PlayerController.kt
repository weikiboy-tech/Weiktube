package com.weiktube.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.*
import androidx.compose.runtime.*
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import org.json.JSONTokener

/** One watch document owns playback throughout expansion, minimization and navigation. */
@SuppressLint("SetJavaScriptEnabled")
class PlayerController(private val activity: MainActivity) {
    var source by mutableStateOf<String?>(null); private set
    var isYoutube by mutableStateOf(true); private set
    var expanded by mutableStateOf(true)
    var playing by mutableStateOf(false); private set
    var position by mutableDoubleStateOf(0.0); private set
    var duration by mutableDoubleStateOf(0.0); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var title by mutableStateOf("Video"); private set
    private val navigationHooks = java.util.IdentityHashMap<WebView, WatchNavigationScript.Hooks>()
    private var playbackAllowed = true
    private var explicitStartRequested = false
    var view: WebView by mutableStateOf(createView()); private set
    private var rendererAlive = true
    private var generation = 0L
    private var mini = false
    private var dark = true
    private var lastRecordedTitle = ""

    private fun createView(): WebView = WebView(activity).apply {
        setBackgroundColor(android.graphics.Color.BLACK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        // A source load is an explicit video selection, never a background resume.
        settings.mediaPlaybackRequiresUserGesture = false
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        setOnTouchListener { _, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN && activity.foreground && (activity.tab != 1 || expanded)) setPlaybackAllowed(true)
            false
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(this, MEDIA_GUARD + WATCH_CHROME, setOf("https://m.youtube.com", "https://www.youtube.com", "https://youtube.com"))
        }
        navigationHooks[this] = WatchNavigationScript.install(this) { url ->
            if (Links.host(url) == "accounts.google.com") { pause(); expanded = false; activity.openAccountScreen() }
            else activity.openAccountUrl(url)
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(web: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val url = request.url.toString()
                if (Links.host(url) == "accounts.google.com") { expanded = false; activity.openAccountScreen(); return true }
                if (!Links.https(url)) { activity.external(url); return true }
                if (Links.youtube(url) && Links.videoId(url) == null) { expanded = false; activity.openAccountUrl(url); return true }
                if (!Links.youtube(url)) { activity.external(url); return true }
                return false
            }
            override fun onPageStarted(web: WebView, url: String, favicon: Bitmap?) {
                loading = source != null; error = null; updateVideoIdentity(url)
            }
            override fun doUpdateVisitedHistory(web: WebView, url: String?, isReload: Boolean) {
                if (url != null) updateVideoIdentity(url)
                applyPresentation()
            }
            override fun onPageFinished(web: WebView, url: String) {
                loading = false; navigationHooks[web]?.apply(); applyPresentation(); applyPlayback(explicitStartRequested); explicitStartRequested = false
            }
            override fun onReceivedError(web: WebView, request: WebResourceRequest, failure: WebResourceError) {
                if (request.isForMainFrame) { loading = false; playing = false; error = "Video nicht erreichbar. Prüfe die Verbindung und versuche es erneut." }
            }
            override fun onReceivedHttpError(web: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame && response.statusCode >= 400) { loading = false; error = "YouTube ist momentan nicht erreichbar (${response.statusCode})." }
            }
            override fun onRenderProcessGone(web: WebView, detail: RenderProcessGoneDetail): Boolean {
                rendererAlive = false; loading = false; playing = false
                error = "Die Wiedergabe wurde beendet. Du kannst das Video an der letzten Position erneut laden."
                web.post { if (view === web) {
                    (web.parent as? android.view.ViewGroup)?.removeView(web)
                    navigationHooks.remove(web)?.close(); view = createView(); web.destroy()
                } }
                return true
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(web: android.view.View, callback: CustomViewCallback) { activity.showFullscreen(web, callback) }
            override fun onHideCustomView() { activity.hideFullscreen() }
        }
    }
    private fun updateVideoIdentity(url: String) {
        if (!isYoutube || source == null) return
        val id = Links.videoId(url) ?: return
        if (id != Links.videoId(source!!)) { generation++; position = 0.0; duration = 0.0; title = "Video"; lastRecordedTitle = "" }
        source = "https://www.youtube.com/watch?v=$id"
    }
    fun open(url: String, userInitiated: Boolean = true) {
        val id = Links.videoId(url)
        if (id == null && !Links.directVideoAllowed(url)) { error = "Keine unterstützte Video-Adresse."; return }
        if (source != null && rendererAlive && ((id != null && id == Links.videoId(source!!)) || url == source)) { expanded = true; return }
        rendererAlive = true
        playbackAllowed = activity.foreground
        explicitStartRequested = userInitiated
        generation++; source = if (id != null) "https://www.youtube.com/watch?v=$id" else url
        isYoutube = id != null; expanded = true; mini = false; error = null
        position = Links.startSeconds(url).toDouble(); duration = 0.0; playing = false; loading = true; title = "Video"; lastRecordedTitle = ""
        if (activity.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) android.util.Log.d("WeiktubePlayback", "document-load session=$generation")
        if (id != null) view.loadUrl("https://m.youtube.com/watch?v=$id&t=${position.toInt()}")
        else {
            view.loadDataWithBaseURL("https://com.weiktube.app/", """
                <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
                <style>html,body{margin:0;height:100%;background:#000}video{width:100%;height:100%;object-fit:contain}</style></head>
                <body><script>$MEDIA_GUARD</script><video id="video" controls playsinline></video><script>document.getElementById('video').src=${JSONObject.quote(url)};</script></body></html>
            """.trimIndent(), "text/html", "UTF-8", null)
            title = "HTTPS-Video"
        }
    }
    fun setPresentation(minimized: Boolean, darkTheme: Boolean) {
        if (mini == minimized && dark == darkTheme) return
        mini = minimized; dark = darkTheme; applyPresentation()
        if (activity.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) android.util.Log.d("WeiktubePlayback", "presentation session=$generation mini=$mini")
    }
    private fun applyPresentation() {
        if (!rendererAlive || source == null || !isYoutube) return
        view.evaluateJavascript(WATCH_CHROME + ";window.weiktubeWatchPresentation($mini,$dark);", null)
    }
    fun poll() {
        if (!rendererAlive || source == null) return
        val requestGeneration = generation
        view.evaluateJavascript("""
            (function(){var v=document.querySelector('video');var h=document.querySelector('.ytVideoMetadataViewModelTitle,h1,.slim-video-information-title');
              return JSON.stringify({playing:!!v&&!v.paused&&!v.ended,position:v&&isFinite(v.currentTime)?v.currentTime:0,
              duration:v&&isFinite(v.duration)?v.duration:0,title:h?h.textContent:(document.title||''),url:location.href});})()
        """.trimIndent()) { raw ->
            if (requestGeneration != generation || !rendererAlive || source == null) return@evaluateJavascript
            runCatching {
                val decoded = JSONTokener(raw).nextValue() as? String ?: return@runCatching
                val value = JSONObject(decoded)
                if (isYoutube) updateVideoIdentity(value.optString("url"))
                playing = value.optBoolean("playing")
                position = value.optDouble("position", position).coerceAtLeast(0.0)
                duration = value.optDouble("duration", 0.0).coerceAtLeast(0.0)
                value.optString("title").removeSuffix(" - YouTube").trim().takeIf { it.isNotBlank() && it != "YouTube" }?.let { title = it.take(300) }
                if (title != "Video" && title != lastRecordedTitle) { lastRecordedTitle = title; source?.let { activity.model.record(it, title) } }
            }
        }
    }
    fun toggle() {
        if (!rendererAlive || source == null) return
        playbackAllowed = activity.foreground || activity.pip
        applyPlayback(false)
        view.evaluateJavascript("(function(){var v=document.querySelector('video');if(v){if(v.paused)v.play().catch(function(){});else v.pause();}})()", null)
    }
    fun setPlaybackAllowed(allowed: Boolean) { playbackAllowed = allowed; if (!allowed) explicitStartRequested = false; applyPlayback(false) }
    private fun applyPlayback(start: Boolean) {
        if (!rendererAlive || source == null) return
        val allowed = playbackAllowed && (activity.foreground || activity.pip)
        view.evaluateJavascript(MEDIA_GUARD + ";window.weiktubeSetPlayback($allowed,${start && allowed});", null)
    }
    fun pause() {
        explicitStartRequested = false
        if (rendererAlive && source != null) view.evaluateJavascript("if(window.weiktubeSetPlayback)window.weiktubeSetPlayback(false,false)", null)
        if (rendererAlive && source != null) view.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(v){v.pause()})", null)
        playing = false
    }
    fun resumeView() { if (rendererAlive) view.onResume() }
    fun pauseView() { playbackAllowed = false; pause(); if (rendererAlive) view.onPause() }
    fun retry() {
        val url = source ?: return
        val wasExpanded = expanded; val seconds = position.toInt(); source = null
        open(if (isYoutube) "$url&t=$seconds" else url); expanded = wasExpanded
    }
    fun close() {
        pause(); generation++; source = null; error = null; loading = false; position = 0.0; duration = 0.0
        if (rendererAlive) view.loadUrl("about:blank")
    }
    fun destroy() { navigationHooks.remove(view)?.close(); if (rendererAlive) view.stopLoading(); view.destroy() }
    companion object {
        internal val MEDIA_GUARD = """
            (function(){
              if(window.weiktubeSetPlayback)return;
              var allowed=false,pending=false;
              function start(v){if(allowed&&pending&&v){pending=false;v.play().catch(function(){});}}
              document.addEventListener('play',function(e){if(!allowed&&e.target&&e.target.pause)e.target.pause();},true);
              document.addEventListener('loadedmetadata',function(e){if(e.target&&e.target.tagName==='VIDEO')start(e.target);},true);
              window.weiktubeSetPlayback=function(next,requestStart){
                allowed=next;pending=next&&requestStart;
                var v=document.querySelector('video');
                if(!allowed)document.querySelectorAll('video,audio').forEach(function(m){m.pause();});
                else if(v&&v.readyState>0)start(v);
              };
            })();
        """.trimIndent()
        // Remove duplicate navigation and size the intact player; its controls and advertising remain intact.
        internal val WATCH_CHROME = """
            (function(){
              if(window.top!==window||!['m.youtube.com','www.youtube.com','youtube.com'].includes(location.hostname)||location.pathname.startsWith('/embed/'))return;
              if(window.weiktubeWatchPresentation)return;
              var savedScroll=0,wasMini=false;
              function fitWatch(){var root=document.documentElement;if(root){var width=window.innerWidth||root.clientWidth;var height=window.innerHeight||width*9/16;root.style.setProperty('--weiktube-watch-height',Math.max(1,Math.min(width*9/16,height))+'px');}}
              window.addEventListener('resize',fitWatch);
              window.weiktubeWatchPresentation=function(mini,dark){
                var root=document.documentElement;if(!root)return;
                fitWatch();
                if(mini&&!wasMini){savedScroll=window.scrollY;window.scrollTo(0,0);}
                root.setAttribute('data-weiktube-mini',mini?'1':'0');root.style.colorScheme=dark?'dark':'light';
                if(!document.getElementById('weiktube-watch-style')){
                  var style=document.createElement('style');style.id='weiktube-watch-style';
                  style.textContent='ytm-mobile-topbar-renderer,ytm-pivot-bar-renderer{display:none!important}'+
                    'ytm-app{padding-top:0!important;padding-bottom:0!important}'+
                    '#player-container-id{top:0!important}'+
                    '.player-container .player-size,ytm-watch .player-size{height:var(--weiktube-watch-height)!important;max-height:none!important;padding-bottom:0!important;aspect-ratio:auto!important}'+
                    'html[data-weiktube-mini="1"],html[data-weiktube-mini="1"] body{overflow:hidden!important}';
                  (document.head||root).appendChild(style);
                }
                if(!mini&&wasMini)window.scrollTo(0,savedScroll);wasMini=mini;
              };
              window.weiktubeWatchPresentation(false,true);
            })();
        """.trimIndent()
    }
}

