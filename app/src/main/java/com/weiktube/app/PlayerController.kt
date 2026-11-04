package com.weiktube.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.*
import androidx.compose.runtime.*
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewRenderProcess
import androidx.webkit.WebViewRenderProcessClient
import org.json.JSONObject
import org.json.JSONTokener

/** One watch document owns playback throughout expansion, minimization and navigation. */
@SuppressLint("SetJavaScriptEnabled")
class PlayerController(
    private val activity: MainActivity,
    private val webViewFactory: (android.content.Context) -> WebView = { WebView(it) },
) {
    var source by mutableStateOf<String?>(null); private set
    var isYoutube by mutableStateOf(true); private set
    var expanded by mutableStateOf(true)
    var playing by mutableStateOf(false); private set
    var position by mutableDoubleStateOf(0.0); private set
    var duration by mutableDoubleStateOf(0.0); private set
    var videoWidth by mutableIntStateOf(0); private set
    var videoHeight by mutableIntStateOf(0); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var title by mutableStateOf("Video"); private set
    private val navigationHooks = java.util.IdentityHashMap<WebView, WatchNavigationScript.Hooks>()
    private val adHooks = java.util.IdentityHashMap<WebView, WebViewAdBlock>()
    private val playbackHooks = java.util.IdentityHashMap<WebView, PlayerPlaybackObserver.Hooks>()
    private val diagnosticHooks = java.util.IdentityHashMap<WebView, PlayerPlaybackDiagnostics.Hooks>()
    private var lastPlaybackGate: Boolean? = null
    private var playbackObservationRevision = 0L
    private var adRecoveryVideo: String? = null
    private var adRecoveryAttempts = 0
    private var playbackAllowed = true
    private var explicitStartRequested = false
    private var destroyed = false
    private var rendererAlive = true
    private var viewPaused = false
    private var rendererUnresponsive = false
    private var generation = 0L
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private data class Recovery(val url: String, val seconds: Double, val play: Boolean, val expanded: Boolean)
    private var pendingRecovery: Recovery? = null
    private var automaticRecoveries = 0
    var view: WebView by mutableStateOf(createView()); private set
    private var mini = false
    private var dark = true
    private var videoOnly = false
    private var systemPip = false
    private var lastRecordedTitle = ""
    val pipReady: Boolean
        get() = !destroyed && rendererAlive && !rendererUnresponsive && source != null && !loading && error == null &&
            (!isYoutube || adHooks[view]?.canPlay == true)

    private fun alive(web: WebView) = !destroyed && rendererAlive && web === view
    private fun trace(event: PlayerDiagnostics.Event) = PlayerDiagnostics.record(event, generation,
        when { destroyed -> "DESTROYED"; !rendererAlive -> "RENDERER_GONE"; rendererUnresponsive -> "UNRESPONSIVE";
            viewPaused -> "PAUSED"; loading -> "LOADING"; source == null -> "IDLE"; else -> "READY" })

    private fun createView(): WebView {
        val candidate = webViewFactory(activity)
        try {
        return candidate.apply {
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
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN && activity.playerMayRun && (activity.tab != 1 || expanded)) setPlaybackAllowed(true)
            false
        }
        val playerWeb = this
        diagnosticHooks[this] = PlayerPlaybackDiagnostics.install(this)
        playbackHooks[this] = PlayerPlaybackObserver.install(this) { state ->
            if (alive(playerWeb) && source != null) {
                playbackObservationRevision++
                playing = state.playing && playbackAllowed && activity.playerMayRun &&
                    (!isYoutube || adHooks[playerWeb]?.canPlay == true)
                videoWidth = state.width; videoHeight = state.height
                activity.syncPipState()
            }
        }
        val ads = WebViewAdBlock(this,
            onReady = {
                if (alive(playerWeb) && source != null && isYoutube) {
                    applyPlayback(explicitStartRequested)
                    if (!loading) explicitStartRequested = false
                    activity.syncPipState()
                }
            },
            onBreakthrough = { wasPlaying -> if (alive(playerWeb) && source != null && isYoutube) recoverFromAd(wasPlaying) },
            onFailure = { message -> if (alive(playerWeb) && source != null && isYoutube) {
                pauseFor(PlayerDiagnostics.PauseReason.FILTER_FAILURE); loading = false; error = message
            } })
        adHooks[this] = ads
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(this, MEDIA_GUARD + WATCH_CHROME, setOf("https://m.youtube.com", "https://www.youtube.com", "https://youtube.com"))
        }
        navigationHooks[this] = WatchNavigationScript.install(this) { url ->
            if (!alive(playerWeb)) return@install
            if (Links.host(url) == "accounts.google.com") { pauseFor(PlayerDiagnostics.PauseReason.ACCOUNT_ACTION); expanded = false; activity.openAccountScreen() }
            else activity.openAccountUrl(url)
        }
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(web: WebView, request: WebResourceRequest): WebResourceResponse? =
                ads.intercept(request)
            override fun shouldOverrideUrlLoading(web: WebView, request: WebResourceRequest): Boolean {
                if (!alive(web)) return true
                if (!request.isForMainFrame) return false
                val url = request.url.toString()
                if (Links.host(url) == "accounts.google.com") { expanded = false; activity.openAccountScreen(); return true }
                if (!Links.https(url)) { activity.external(url); return true }
                if (Links.youtube(url) && Links.videoId(url) == null) { expanded = false; activity.openAccountUrl(url); return true }
                if (!Links.youtube(url)) { activity.external(url); return true }
                return false
            }
            override fun onPageStarted(web: WebView, url: String, favicon: Bitmap?) {
                if (!alive(web) || source == null) return
                ads.pageStarted(url)
                loading = source != null; error = null; updateVideoIdentity(url)
                trace(PlayerDiagnostics.Event.NAVIGATION_STARTED)
            }
            override fun doUpdateVisitedHistory(web: WebView, url: String?, isReload: Boolean) {
                if (!alive(web) || source == null) return
                if (url != null) updateVideoIdentity(url)
                applyPresentation()
            }
            override fun onPageFinished(web: WebView, url: String) {
                if (!alive(web) || source == null) return
                loading = false; navigationHooks[web]?.apply(); applyPresentation(); ads.pageFinished()
                playbackHooks[web]?.apply()
                diagnosticHooks[web]?.apply()
                applyPlayback(explicitStartRequested)
                if (!isYoutube || ads.canPlay) explicitStartRequested = false
                trace(PlayerDiagnostics.Event.NAVIGATION_FINISHED)
            }
            override fun onReceivedError(web: WebView, request: WebResourceRequest, failure: WebResourceError) {
                if (alive(web) && source != null && request.isForMainFrame) {
                    loading = false; playing = false; error = "Video nicht erreichbar. Prüfe die Verbindung und versuche es erneut."
                    trace(PlayerDiagnostics.Event.NAVIGATION_FAILED)
                }
            }
            override fun onReceivedHttpError(web: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (alive(web) && source != null && request.isForMainFrame && response.statusCode >= 400) {
                    loading = false; error = "YouTube ist momentan nicht erreichbar (${response.statusCode})."
                    trace(PlayerDiagnostics.Event.NAVIGATION_FAILED)
                }
            }
            override fun onRenderProcessGone(web: WebView, detail: RenderProcessGoneDetail): Boolean {
                rendererGone(web, detail.didCrash())
                return true
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(web: android.view.View, callback: CustomViewCallback) {
                if (alive(playerWeb)) activity.showFullscreen(web, callback, playerWeb)
                else runCatching { callback.onCustomViewHidden() }
            }
            override fun onHideCustomView() { if (alive(playerWeb)) activity.hideFullscreen() }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE)) {
            WebViewCompat.setWebViewRenderProcessClient(this, object : WebViewRenderProcessClient() {
                override fun onRenderProcessUnresponsive(web: WebView, renderer: WebViewRenderProcess?) {
                    if (!alive(web) || rendererUnresponsive) return
                    rendererUnresponsive = true
                    trace(PlayerDiagnostics.Event.RENDERER_UNRESPONSIVE)
                    // A slow renderer is not a dead renderer. Never kill/reload it on a timeout.
                    activity.syncPipState()
                }
                override fun onRenderProcessResponsive(web: WebView, renderer: WebViewRenderProcess?) {
                    if (!alive(web) || !rendererUnresponsive) return
                    rendererUnresponsive = false
                    trace(PlayerDiagnostics.Event.RENDERER_RESPONSIVE)
                    activity.syncPipState()
                }
            })
        }
        trace(PlayerDiagnostics.Event.VIEW_CREATED)
        }
        } catch (failure: Exception) {
            disposeView(candidate, force = true)
            throw failure
        }
    }

    private fun disposeView(web: WebView, force: Boolean = false) {
        val nav = navigationHooks.remove(web)
        val ads = adHooks.remove(web)
        val playback = playbackHooks.remove(web)
        val diagnostics = diagnosticHooks.remove(web)
        if (!force && nav == null && ads == null && playback == null && diagnostics == null) return
        // Cleanup against a dead renderer may throw. One failed bridge must not prevent destruction.
        listOf<() -> Unit>(
            { (web.parent as? android.view.ViewGroup)?.removeView(web) },
            { nav?.close() }, { ads?.close() }, { playback?.close() }, { diagnostics?.close() }, { web.destroy() },
        ).forEach { cleanup -> if (runCatching(cleanup).isFailure) trace(PlayerDiagnostics.Event.CLEANUP_FAILED) }
        trace(PlayerDiagnostics.Event.VIEW_DESTROYED)
    }

    private fun rendererGone(web: WebView, crashed: Boolean) {
        if (!alive(web)) { disposeView(web); return }
        val saved = source?.let { Recovery(it, position, (playing || explicitStartRequested) && playbackAllowed && activity.playerMayRun, expanded) }
        generation++; playbackObservationRevision++
        rendererAlive = false; rendererUnresponsive = false; viewPaused = false
        loading = false; playing = false; explicitStartRequested = false
        trace(if (crashed) PlayerDiagnostics.Event.RENDERER_CRASHED else PlayerDiagnostics.Event.RENDERER_KILLED)
        activity.releasePlayerFullscreen(web)
        disposeView(web)
        if (saved != null && automaticRecoveries < 1) {
            automaticRecoveries++
            pendingRecovery = saved
            error = "Der Videoprozess wurde beendet. Die Wiedergabe wird wiederhergestellt …"
            trace(PlayerDiagnostics.Event.RECOVERY_PENDING)
            val attempt = generation
            // Never enqueue recovery on the destroyed WebView. New selection/close wins this race.
            mainHandler.post { if (!destroyed && generation == attempt && activity.playerMayRun) restoreRenderer() }
        } else {
            pendingRecovery = null
            error = "Der Videoprozess wurde erneut beendet. Bitte erneut laden oder ein anderes Video wählen."
            trace(PlayerDiagnostics.Event.RECOVERY_LIMIT)
        }
        activity.syncPipState()
    }

    private fun ensureRenderer(): Boolean {
        if (destroyed) return false
        if (rendererAlive) return true
        var candidate: WebView? = null
        return try {
            candidate = createView()
            // Publish only a fully configured, lifecycle-ready replacement.
            if (activity.playerMayRun) candidate.onResume() else candidate.onPause()
            view = candidate
            rendererAlive = true; rendererUnresponsive = false; viewPaused = false
            viewPaused = !activity.playerMayRun
            trace(if (viewPaused) PlayerDiagnostics.Event.VIEW_PAUSED else PlayerDiagnostics.Event.VIEW_RESUMED)
            true
        } catch (_: Exception) {
            rendererAlive = false
            candidate?.let { disposeView(it) }
            error = "Die Videoansicht konnte nicht wiederhergestellt werden. Bitte erneut versuchen."
            loading = false
            trace(PlayerDiagnostics.Event.VIEW_CREATE_FAILED)
            false
        }
    }

    private fun restoreRenderer() {
        val saved = pendingRecovery ?: return
        if (destroyed || !activity.playerMayRun || source != saved.url) return
        pendingRecovery = null
        trace(PlayerDiagnostics.Event.RECOVERY_STARTED)
        val wasMini = mini
        openSelected(saved.url, saved.play, recovery = true, startAt = saved.seconds)
        expanded = saved.expanded
        mini = wasMini
        applyPresentation()
    }
    private fun updateVideoIdentity(url: String) {
        if (!isYoutube || source == null) return
        val id = Links.videoId(url) ?: return
        if (id != Links.videoId(source!!)) {
            generation++; position = 0.0; duration = 0.0; title = "Video"; lastRecordedTitle = ""
            videoWidth = 0; videoHeight = 0
            adRecoveryVideo = id; adRecoveryAttempts = 0
        }
        source = "https://www.youtube.com/watch?v=$id"
    }
    fun open(url: String, userInitiated: Boolean = true) {
        openSelected(url, userInitiated)
    }
    /** Process recreation is intentionally paused; no saved Play state can start background audio. */
    fun restore(url: String, seconds: Double) {
        openSelected(url, userInitiated = false, startAt = seconds)
        expanded = false
    }
    private fun openSelected(url: String, userInitiated: Boolean, recovery: Boolean = false, startAt: Double? = null) {
        if (destroyed) return
        val id = Links.videoId(url)
        if (id == null && !Links.directVideoAllowed(url)) { error = "Keine unterstützte Video-Adresse."; return }
        if (!recovery) {
            if (pendingRecovery != null) trace(PlayerDiagnostics.Event.RECOVERY_CANCELLED)
            pendingRecovery = null; automaticRecoveries = 0
        }
        val requiresLoad = !rendererAlive || error != null || startAt != null
        if (!ensureRenderer()) return
        resumeLiveView()
        if (id != adRecoveryVideo) { adRecoveryVideo = id; adRecoveryAttempts = 0 }
        if (!requiresLoad && source != null && rendererAlive && ((id != null && id == Links.videoId(source!!)) || url == source)) {
            expanded = true
            if (userInitiated) {
                playbackAllowed = activity.playerMayRun
                // Selecting the same video again must resume the retained, possibly paused document.
                explicitStartRequested = loading
                applyPlayback(true)
            }
            return
        }
        playbackAllowed = activity.playerMayRun
        explicitStartRequested = userInitiated
        generation++; source = if (id != null) "https://www.youtube.com/watch?v=$id" else url
        isYoutube = id != null; expanded = true; mini = false; error = null
        position = (startAt ?: Links.startSeconds(url).toDouble()).takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        duration = 0.0; playing = false; loading = true; title = "Video"; lastRecordedTitle = ""
        videoWidth = 0; videoHeight = 0
        playbackHooks[view]?.prepare()
        diagnosticHooks[view]?.prepare()
        lastPlaybackGate = null
        if (activity.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) android.util.Log.d("WeiktubePlayback", "document-load session=$generation")
        if (id != null) {
            val watchUrl = "https://m.youtube.com/watch?v=$id&t=${position.toInt()}"
            if (adHooks[view]?.prepare(watchUrl) != true) { loading = false; return }
            view.loadUrl(watchUrl)
        }
        else {
            view.loadDataWithBaseURL("https://com.weiktube.app/", """
                <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
                <style>html,body{margin:0;height:100%;background:#000}video{width:100%;height:100%;object-fit:contain}</style></head>
                <body><script>$MEDIA_GUARD</script><video id="video" controls playsinline></video><script>
                var v=document.getElementById('video');v.addEventListener('loadedmetadata',function(){if(${position}>0)v.currentTime=${position};},{once:true});
                v.src=${JSONObject.quote(url)};</script></body></html>
            """.trimIndent(), "text/html", "UTF-8", null)
            title = "HTTPS-Video"
        }
    }
    fun setPresentation(minimized: Boolean, darkTheme: Boolean, videoOnly: Boolean = false, systemPip: Boolean = false) {
        if (destroyed) return
        if (mini == minimized && dark == darkTheme && this.videoOnly == videoOnly && this.systemPip == systemPip) return
        mini = minimized; dark = darkTheme; this.videoOnly = videoOnly; this.systemPip = systemPip; applyPresentation()
        trace(if (systemPip) PlayerDiagnostics.Event.PRESENTATION_PIP else if (mini) PlayerDiagnostics.Event.PRESENTATION_MINI else PlayerDiagnostics.Event.PRESENTATION_NORMAL)
        if (activity.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) android.util.Log.d("WeiktubePlayback", "presentation session=$generation mini=$mini")
    }
    private fun applyPresentation() {
        if (!rendererAlive || source == null) return
        if (!isYoutube) {
            view.evaluateJavascript("(function(){var v=document.querySelector('video');if(v)v.controls=${!systemPip};})()", null)
            return
        }
        view.evaluateJavascript(WATCH_CHROME + ";window.weiktubeWatchPresentation($mini,$dark,$videoOnly,$systemPip);", null)
    }
    fun poll() {
        if (destroyed || !rendererAlive || source == null) return
        val polledView = view
        val requestGeneration = generation
        val observationRevision = playbackObservationRevision
        view.evaluateJavascript("""
            (function(){var v=document.querySelector('video');var h=document.querySelector('.ytVideoMetadataViewModelTitle,h1,.slim-video-information-title');
              return JSON.stringify({playing:!!v&&!v.paused&&!v.ended,position:v&&isFinite(v.currentTime)?v.currentTime:0,
              duration:v&&isFinite(v.duration)?v.duration:0,width:v?v.videoWidth:0,height:v?v.videoHeight:0,
              title:h?h.textContent:(document.title||''),url:location.href});})()
        """.trimIndent()) { raw ->
            if (requestGeneration != generation || !alive(polledView) || source == null) return@evaluateJavascript
            runCatching {
                val decoded = JSONTokener(raw).nextValue() as? String ?: return@runCatching
                val value = JSONObject(decoded)
                if (isYoutube) updateVideoIdentity(value.optString("url"))
                // A snapshot requested before a newer play/pause event must not overwrite it.
                if (observationRevision == playbackObservationRevision) {
                    playing = value.optBoolean("playing") && playbackAllowed && activity.playerMayRun &&
                        (!isYoutube || adHooks[view]?.canPlay == true)
                    videoWidth = value.optInt("width").coerceIn(0, 16384)
                    videoHeight = value.optInt("height").coerceIn(0, 16384)
                }
                // A recognized advertisement must not replace the last content timestamp.
                if (!isYoutube || adHooks[view]?.blocked != true) {
                    position = value.optDouble("position", position).coerceAtLeast(0.0)
                    duration = value.optDouble("duration", 0.0).coerceAtLeast(0.0)
                }
                value.optString("title").removeSuffix(" - YouTube").trim().takeIf { it.isNotBlank() && it != "YouTube" }?.let { title = it.take(300) }
                if (title != "Video" && title != lastRecordedTitle) { lastRecordedTitle = title; source?.let { activity.model.record(it, title) } }
            }
        }
    }
    fun toggle() {
        if (!rendererAlive || source == null || (isYoutube && adHooks[view]?.canPlay != true)) return
        playbackAllowed = activity.playerMayRun
        applyPlayback(false)
        view.evaluateJavascript("(function(){var v=document.querySelector('video');if(v){if(v.paused)v.play().catch(function(){});else v.pause();}})()", null)
    }
    /** Native PiP Play is a request to play, never a toggle or a new document load. */
    fun play() {
        if (!pipReady) return
        trace(PlayerDiagnostics.Event.USER_PLAY)
        playbackAllowed = activity.playerMayRun
        applyPlayback(true)
    }
    fun setPlaybackAllowed(allowed: Boolean) {
        if (destroyed) return
        playbackAllowed = allowed
        if (!allowed) {
            explicitStartRequested = false
            playbackObservationRevision++; playing = false
            activity.syncPipState()
        }
        applyPlayback(false)
    }
    private fun applyPlayback(start: Boolean) {
        if (!rendererAlive || source == null) return
        val allowed = playbackAllowed && activity.playerMayRun &&
            (!isYoutube || adHooks[view]?.canPlay == true)
        if (lastPlaybackGate != allowed) {
            lastPlaybackGate = allowed
            trace(if (allowed) PlayerDiagnostics.Event.GATE_ALLOWED else when {
                !activity.playerMayRun -> PlayerDiagnostics.Event.GATE_HOST_BLOCKED
                !playbackAllowed -> PlayerDiagnostics.Event.GATE_POLICY_BLOCKED
                else -> PlayerDiagnostics.Event.GATE_FILTER_BLOCKED
            })
        }
        view.evaluateJavascript(MEDIA_GUARD + ";window.weiktubeSetPlayback($allowed,${start && allowed});", null)
    }
    fun pause() = pauseFor(PlayerDiagnostics.PauseReason.USER)
    internal fun pauseFor(reason: PlayerDiagnostics.PauseReason) {
        PlayerDiagnostics.pause(reason, generation)
        pendingRecovery = pendingRecovery?.copy(play = false)
        explicitStartRequested = false
        playbackAllowed = false
        playbackObservationRevision++
        if (rendererAlive && source != null) view.evaluateJavascript("""
            (function(){function pause(){try{if(window.weiktubeSetPlayback)window.weiktubeSetPlayback(false,false);}
              finally{document.querySelectorAll('video,audio').forEach(function(v){if(!v.paused)v.pause();});}}
              if(window.weiktubePlaybackScope)window.weiktubePlaybackScope('APP_COMMAND',pause);else pause();})();
        """.trimIndent(), null)
        playing = false
        activity.syncPipState()
    }
    fun resumeView() {
        if (destroyed || !activity.playerMayRun) return
        restoreRenderer()
        resumeLiveView()
    }
    private fun resumeLiveView() {
        if (destroyed || !rendererAlive || !activity.playerMayRun) return
        if (viewPaused) {
            view.onResume(); viewPaused = false
            trace(PlayerDiagnostics.Event.VIEW_RESUMED)
            // Refresh the retained native surface after visibility changes, without navigation.
            view.requestLayout(); view.invalidate()
        }
    }
    internal fun pauseView(reason: PlayerDiagnostics.PauseReason = PlayerDiagnostics.PauseReason.HOST_STOP) {
        if (destroyed) return
        pauseFor(reason)
        if (rendererAlive && !viewPaused) {
            view.onPause(); viewPaused = true
            trace(PlayerDiagnostics.Event.VIEW_PAUSED)
        }
    }
    private fun recoverFromAd(wasPlaying: Boolean) {
        val url = source ?: return
        val resume = playing || explicitStartRequested || wasPlaying
        val wasExpanded = expanded
        val seconds = position.toInt().coerceAtLeast(0)
        pauseFor(PlayerDiagnostics.PauseReason.FILTER_RECOVERY)
        if (adRecoveryAttempts >= 1) {
            loading = false
            error = "YouTube-Werbung erkannt. Der geschützte Neustart hat nicht geholfen. Bitte Filter aktualisieren und erneut laden."
            return
        }
        adRecoveryAttempts++
        val attempt = generation
        error = "Werbung erkannt. Das Video wird einmal mit aktivem Schutz neu geladen."
        view.post {
            if (!destroyed && source == url && generation == attempt && rendererAlive) {
                source = null
                open("$url&t=$seconds", userInitiated = resume)
                expanded = wasExpanded
            }
        }
    }
    fun retry() {
        if (destroyed) return
        val url = source ?: return
        adRecoveryAttempts = 0
        val wasExpanded = expanded; val seconds = position.toInt(); source = null
        open(if (isYoutube) "$url&t=$seconds" else url); expanded = wasExpanded
    }
    fun close() {
        if (destroyed) return
        pendingRecovery = null
        pauseFor(PlayerDiagnostics.PauseReason.CLOSE); generation++; source = null; error = null; loading = false; position = 0.0; duration = 0.0
        videoWidth = 0; videoHeight = 0
        adRecoveryVideo = null; adRecoveryAttempts = 0
        if (rendererAlive) view.loadUrl("about:blank")
    }
    fun destroy() {
        if (destroyed) return
        destroyed = true; pendingRecovery = null; generation++; playbackObservationRevision++
        rendererAlive = false; playing = false; loading = false
        disposeView(view)
    }
    companion object {
        internal val MEDIA_GUARD = """
            (function(){
              if(window.weiktubeSetPlayback)return;
              var allowed=false,pending=false;
              function pause(m){if(window.weiktubePlaybackScope)window.weiktubePlaybackScope('APP_GUARD',function(){m.pause();});else m.pause();}
              function start(v){if(allowed&&pending&&v){pending=false;v.play().catch(function(){});}}
              document.addEventListener('play',function(e){if(!allowed&&e.target&&e.target.pause)pause(e.target);},true);
              document.addEventListener('loadedmetadata',function(e){if(e.target&&e.target.tagName==='VIDEO')start(e.target);},true);
              window.weiktubeSetPlayback=function(next,requestStart){
                allowed=next;if(!allowed)pending=false;else if(requestStart)pending=true;
                var v=document.querySelector('video');
                if(!allowed)document.querySelectorAll('video,audio').forEach(pause);
                else if(v&&v.readyState>0)start(v);
              };
            })();
        """.trimIndent()
        // Presentation only. Advertising is handled independently by WebViewAdBlock.
        internal val WATCH_CHROME = """
            (function(){
              if(window.top!==window||!['m.youtube.com','www.youtube.com','youtube.com'].includes(location.hostname)||location.pathname.startsWith('/embed/'))return;
              if(window.weiktubeWatchPresentation)return;
              var savedScroll=0,wasIsolated=false,videoOnlyMode=false;
              function fitWatch(){var root=document.documentElement;if(root){var width=window.innerWidth||root.clientWidth;var height=window.innerHeight||width*9/16;root.style.setProperty('--weiktube-watch-height',Math.max(1,videoOnlyMode?height:Math.min(width*9/16,height))+'px');}}
              window.addEventListener('resize',fitWatch);
              window.weiktubeWatchPresentation=function(mini,dark,videoOnly,systemPip){
                var root=document.documentElement;if(!root)return;
                systemPip=!!systemPip;
                if(systemPip){mini=false;videoOnly=true;}
                // Mini and system PiP both show only this document's existing video.
                videoOnly=!!videoOnly||mini;
                var isolated=mini||videoOnly;
                if(isolated&&!wasIsolated){savedScroll=window.scrollY;window.scrollTo(0,0);}
                videoOnlyMode=videoOnly;
                root.setAttribute('data-weiktube-mini',mini?'1':'0');
                root.setAttribute('data-weiktube-system-pip',systemPip?'1':'0');
                root.setAttribute('data-weiktube-video-only',videoOnly?'1':'0');root.style.colorScheme=dark?'dark':'light';
                fitWatch();
                if(!document.getElementById('weiktube-watch-style')){
                  var style=document.createElement('style');style.id='weiktube-watch-style';
                  style.textContent='ytm-mobile-topbar-renderer,ytm-pivot-bar-renderer{display:none!important}'+
                    'ytm-app{padding-top:0!important;padding-bottom:0!important}'+
                    '#player-container-id{top:0!important}'+
                    '.player-container .player-size,ytm-watch .player-size{height:var(--weiktube-watch-height)!important;max-height:none!important;padding-bottom:0!important;aspect-ratio:auto!important}'+
                    // YouTube can retain inline centering offsets from the previous viewport.
                    // Normalize only the retained player's media box, including the portrait return.
                    '#player-container-id .html5-video-container{width:100%!important;height:100%!important}'+
                    '#player-container-id video{left:0!important;top:0!important;width:100%!important;height:100%!important;object-fit:contain!important}'+
                    // The closed mobile drawer is 360px wide but sits at left:-100vw.
                    // Below 360px it covers the video; compact modes use native controls.
                    'html:is([data-weiktube-mini="1"],[data-weiktube-system-pip="1"]) .ytmDrawerLayoutContainerDrawerLayout{display:none!important}'+
                    'html[data-weiktube-video-only="1"],html[data-weiktube-video-only="1"] body{overflow:hidden!important;height:100%!important}'+
                    'html[data-weiktube-video-only="1"] #player-container-id{position:fixed!important;inset:0!important;width:100%!important;height:100%!important;max-width:none!important;max-height:none!important;background:#000!important}'+
                    'html[data-weiktube-video-only="1"] #player-container-id :is(#player,.html5-video-player,.html5-video-container,video){width:100%!important;height:100%!important;max-width:none!important;max-height:none!important;aspect-ratio:auto!important}'+
                    'html[data-weiktube-video-only="1"] #player-container-id video{left:0!important;top:0!important;object-fit:contain!important}'+
                    // Android provides the PiP controls. Keep captions and the original video node.
                    'html[data-weiktube-system-pip="1"] :is(.ytp-chrome-top,.ytp-chrome-bottom,.ytp-large-play-button,.ytp-pause-overlay,ytm-player-controls,.player-controls-content,.player-controls-top,.player-controls-middle,.player-controls-bottom){display:none!important}'+
                    'html[data-weiktube-mini="1"],html[data-weiktube-mini="1"] body{overflow:hidden!important}'+
                    // The compact card has accessible native controls; keep website controls for expansion.
                    'html[data-weiktube-mini="1"] :is(.ytp-chrome-top,.ytp-chrome-bottom,.ytp-large-play-button,.ytp-pause-overlay,ytm-player-controls,.player-controls-content,.player-controls-top,.player-controls-middle,.player-controls-bottom,.caption-window){display:none!important}';
                  (document.head||root).appendChild(style);
                }
                if(!isolated&&wasIsolated)window.scrollTo(0,savedScroll);wasIsolated=isolated;
              };
              window.weiktubeWatchPresentation(false,true,false);
            })();
        """.trimIndent()
    }
}

