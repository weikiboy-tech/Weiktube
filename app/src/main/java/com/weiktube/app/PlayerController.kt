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
import kotlinx.coroutines.*

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
    // CSS viewport fractions sampled only from the expanded document before isolation.
    internal var watchSlot by mutableStateOf<android.graphics.RectF?>(null)
        private set
    internal var geometryEpoch = 0L
        private set
    internal fun invalidateGeometry() {
        geometryEpoch++
        watchSlot = null
    }
    private var sampledLandscape = false
    internal fun mediaSlot(landscape: Boolean) = watchSlot.takeIf { sampledLandscape == landscape }
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var title by mutableStateOf("Video"); private set
    private val navigationHooks = java.util.IdentityHashMap<WebView, WatchNavigationScript.Hooks>()
    private val adHooks = java.util.IdentityHashMap<WebView, WebViewAdBlock>()
    private val playbackHooks = java.util.IdentityHashMap<WebView, PlayerPlaybackObserver.Hooks>()
    private val diagnosticHooks = java.util.IdentityHashMap<WebView, PlayerPlaybackDiagnostics.Hooks>()
    private val diagnosticControllerId = PlayerDiagnostics.nextControllerId()
    private var currentViewId = 0L
    private val viewIds = java.util.IdentityHashMap<WebView, Long>()
    private val attachListeners = java.util.IdentityHashMap<WebView, android.view.View.OnAttachStateChangeListener>()
    private val layoutListeners = java.util.IdentityHashMap<WebView, android.view.View.OnLayoutChangeListener>()
    private val viewSnapshots = java.util.IdentityHashMap<WebView, PlayerDiagnostics.ViewSnapshot>()
    private var lastPlaybackGate: Boolean? = null
    private var playbackObservationRevision = 0L
    private var adRecoveryVideo: String? = null
    private var adRecoveryAttempts = 0
    private var playbackAllowed = true
    private var explicitStartRequested = false
    private var audibleStartRequested = false
    private var destroyed = false
    private var rendererAlive = true
    private var viewPaused = false
    private var rendererUnresponsive = false
    private var generation = 0L
    // A WebView can outlive several documents. Instance identity alone cannot reject callbacks
    // or JS results belonging to the document replaced by a newer explicit video selection.
    internal var documentCommitted by mutableStateOf(false)
        private set
    internal var pictureReady by mutableStateOf(false)
        private set
    private var selectedAt = 0L
    private val startupEvents = mutableSetOf<String>()
    private fun startup(stage: String) {
        if (selectedAt > 0 && startupEvents.add(stage))
            android.util.Log.i("WeiktubeStart", "session=$selectedAt stage=$stage elapsedMs=${android.os.SystemClock.elapsedRealtime() - selectedAt}")
    }
    private fun beginStartup() {
        selectedAt = android.os.SystemClock.elapsedRealtime()
        startupEvents.clear()
        pictureReady = false
        startup("selected")
    }
    internal fun startupHostLaidOut() = startup("host-laid-out")

    private fun acceptWatchTitle(raw: String?) {
        val text = raw?.removeSuffix(" - YouTube")?.trim().orEmpty()
        if (text.isBlank() || text == "YouTube") return
        title = text.take(300)
        startup("title")
        if (title != lastRecordedTitle) {
            lastRecordedTitle = title
            source?.let { activity.model.updateHistoryTitle(it, title) }
        }
    }
    private fun cachedTitle(id: String?): String {
        if (id == null) return "Video"
        val prefs = activity.model.preferences.value
        if (!prefs.saveHistory) return "Video"
        return prefs.history.firstOrNull { it.url == "https://m.youtube.com/watch?v=$id" }
            ?.title?.takeIf { it.isNotBlank() && !it.startsWith("https://") } ?: "Video"
    }
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private data class Recovery(val url: String, val seconds: Double, val play: Boolean, val expanded: Boolean)
    private var pendingRecovery: Recovery? = null
    private var automaticRecoveries = 0
    private var recoveryDocumentPending = false
    // Visual-only state; no media commands and no new WebView. One update per display frame.
    var zoomRevision by mutableIntStateOf(0); private set
    private var zoomScriptInstalled = false
    private var pendingZoom: Triple<Float, Float, Float>? = null
    private val zoomFrame = Runnable {
        val value = pendingZoom
        pendingZoom = null
        if (value != null && !destroyed && rendererAlive && documentCommitted && source != null) {
            val bootstrap = if (zoomScriptInstalled) "" else VideoZoomScript.SCRIPT
            zoomScriptInstalled = true
            view.evaluateJavascript(bootstrap + ";if(window.weiktubeVideoZoom)window.weiktubeVideoZoom(${value.first},${value.second},${value.third});", null)
        }
    }
    internal fun setVideoZoom(scale: Float, x: Float, y: Float) {
        if (destroyed || !rendererAlive || !scale.isFinite() || !x.isFinite() || !y.isFinite()) return
        val target = if (mini || systemPip) 1f else scale.coerceIn(1f, 8f)
        pendingZoom = Triple(target, x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        view.removeCallbacks(zoomFrame)
        view.postOnAnimation(zoomFrame)
    }
    private fun clearVideoZoom() {
        view.removeCallbacks(zoomFrame)
        pendingZoom = null
        if (!destroyed && rendererAlive && zoomScriptInstalled)
            view.evaluateJavascript("if(window.weiktubeVideoZoom)window.weiktubeVideoZoom(1,.5,.5);", null)
        zoomScriptInstalled = false
        zoomRevision++
    }
    internal val sponsorBlock by lazy { SponsorBlockController(activity.applicationContext) }
    var view: WebView by mutableStateOf(createView()); private set
    private var mini = false
    private var dark = true
    private var videoOnly = false
    private var systemPip = false
    private var lastRecordedTitle = ""
    private var progressSampleValid = false
    private var awaitingResumePosition: Double? = null
    private var historyRevision = 0
    var resumedAt by mutableDoubleStateOf(0.0); private set
    var gestureFeedback by mutableStateOf<PlayerGestureFeedback?>(null); private set
    var supportedHoldRates by mutableStateOf<List<Float>>(emptyList()); private set
    private var heldRequest = 0
    private var seekTotal = 0.0
    private var lastSeekFeedback = 0L
    private val clearFeedback = Runnable { gestureFeedback = null }
    internal fun feedback(kind: String, text: String, percent: Int? = null) {
        mainHandler.removeCallbacks(clearFeedback)
        gestureFeedback = PlayerGestureFeedback(kind, text, percent)
        if (kind == "seek") mainHandler.postDelayed(clearFeedback, 900)
    }
    internal fun finishGesture() { mainHandler.removeCallbacks(clearFeedback); mainHandler.postDelayed(clearFeedback, 600) }
    private fun advanced(command: String, result: (String) -> Unit = {}) {
        if (destroyed || !rendererAlive || !documentCommitted || source == null ||
            (isYoutube && adHooks[view]?.canPlay != true)) { result("null"); return }
        val epoch = generation; val web = view
        web.evaluateJavascript(AdvancedPlayerScript.SCRIPT + ";" + command) { raw ->
            if (epoch == generation && alive(web) && selectedDocument(web.url)) result(raw)
        }
    }
    internal fun gestureHit(x: Float, y: Float, result: (Boolean) -> Unit) {
        if (!x.isFinite() || !y.isFinite()) { result(false); return }
        advanced("window.weiktubeAdvanced.hit($x,$y)") { result(it == "true") }
    }
    internal fun seekGesture(seconds: Int) {
        if (kotlin.math.abs(seconds) !in PlayerPreferences.steps) return
        advanced("window.weiktubeAdvanced.seek($seconds)") { raw ->
            val actual = raw.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return@advanced
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastSeekFeedback > 700 || actual * seekTotal < 0) seekTotal = 0.0
            seekTotal += actual; lastSeekFeedback = now
            val count = kotlin.math.abs(seekTotal).toInt()
            feedback("seek", "${if (seekTotal < 0) "−" else "+"}$count s")
        }
    }
    internal fun holdGesture(active: Boolean) {
        val request = ++heldRequest
        if (!active) {
            if (!destroyed && rendererAlive) view.evaluateJavascript("window.weiktubeAdvanced&&window.weiktubeAdvanced.release();", null)
            finishGesture(); return
        }
        val speed = activity.model.preferences.value.player.holdSpeed
        advanced("window.weiktubeAdvanced.hold($speed)") { raw ->
            if (heldRequest == request && raw == "true") feedback("speed", "${speed.toString().replace('.', ',')}×")
        }
    }
    var supportedQualities by mutableStateOf<List<Int>>(emptyList()); private set
    var qualityStatus by mutableStateOf("Noch kein Video geprüft"); private set
    private fun pollEnhancements() {
        if (isYoutube && adHooks[view]?.canPlay != true) return
        advanced("JSON.stringify(window.weiktubeAdvanced.rates())") { raw ->
            runCatching {
                val json = org.json.JSONArray(JSONTokener(raw).nextValue() as String)
                supportedHoldRates = (0 until json.length()).map { json.getDouble(it).toFloat() }
                    .filter { it in PlayerPreferences.speeds }.distinct()
            }
        }
        if (!isYoutube) return
        val id = source?.let(Links::videoId) ?: return
        val preference = PlayerQuality.preferred(PlayerQuality.network(activity), activity.model.preferences.value.player)
        val epoch = generation; val web = view
        web.evaluateJavascript(PlayerQuality.SCRIPT + ";JSON.stringify(window.weiktubeQuality(${JSONObject.quote(id)},$preference,Date.now()));") { raw ->
            if (epoch != generation || !alive(web)) return@evaluateJavascript
            runCatching {
                val data = JSONObject(JSONTokener(raw).nextValue() as String)
                val levels = data.optJSONArray("levels") ?: org.json.JSONArray()
                supportedQualities = (0 until levels.length()).map { levels.getInt(it) }.filter { it in PlayerPreferences.qualities }.distinct().sorted()
                qualityStatus = when(data.optString("status")) {
                    "confirmed" -> "Vom Player bestätigt: ${data.optInt("target")}p"
                    "manual" -> "Manuelle Auswahl bleibt erhalten"
                    "requested" -> "Qualität angefordert; Bestätigung ausstehend"
                    "waiting" -> "Qualitätssteuerung wird geprüft"
                    "automatic" -> "Automatische Qualität"
                    else -> "Automatische Qualität – programmatische Auswahl nicht bestätigt"
                }
            }
        }
    }
    private val enhancementScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dislikeRepository by lazy { ReturnDislikeRepository(activity.applicationContext) }
    private var dislikeJob: Job? = null
    private var dislikeId: String? = null
    private var dislikeLabel: String? = null
    private var dislikeEpoch = -1L
    var dislikeStatus by mutableStateOf("Ausgeschaltet"); private set
    private fun pollDislikes() {
        val enabled = activity.model.preferences.value.player.dislikes
        val id = if (enabled && isYoutube) source?.let(Links::videoId) else null
        if (id == null && dislikeId == null) return
        if (id != dislikeId || dislikeEpoch != generation) {
            dislikeJob?.cancel(); dislikeId = id; dislikeLabel = null; dislikeEpoch = generation
            dislikeStatus = if (id == null) "Ausgeschaltet" else "Daten werden abgefragt"
            if (id != null) {
                val epoch = generation; val web = view
                dislikeJob = enhancementScope.launch {
                    val count = dislikeRepository.get(id)
                    if (epoch != generation || !alive(web) || dislikeId != id || !activity.model.preferences.value.player.dislikes) return@launch
                    dislikeLabel = count?.let { java.text.NumberFormat.getIntegerInstance(java.util.Locale.GERMANY).format(it) }
                    dislikeStatus = if (count == null) "Keine Dislike-Daten verfügbar" else "RYD-Daten verfügbar (teilweise geschätzt)"
                }
            }
        }
        val label = dislikeLabel
        view.evaluateJavascript(ReturnDislikeScript.SCRIPT + ";window.weiktubeDislikes(${JSONObject.quote(id.orEmpty())},${label?.let(JSONObject::quote) ?: "null"});", null)
    }
    internal fun refreshSponsorBlock() {
        if (destroyed) return
        if (!rendererAlive || !documentCommitted || source == null) { sponsorBlock.reset(rendererAlive); return }
        sponsorBlock.sync(view, generation, if (isYoutube) source?.let(Links::videoId) else null,
            activity.playerMayRun && (!isYoutube || adHooks[view]?.canPlay == true),
            activity.model.preferences.value.sponsor)
    }
    private fun saveProgress() {
        if (progressSampleValid && activity.model.preferencesReady && historyRevision == activity.model.historyRevision)
            source?.let { activity.model.progress.save(it, position, duration, force = true) }
    }
    fun restartFromBeginning() {
        val url = source ?: return
        activity.model.progress.remove(url)
        progressSampleValid = false
        openSelected(url, userInitiated = true, startAt = 0.0)
    }
    val pipReady: Boolean
        get() = !destroyed && rendererAlive && !rendererUnresponsive && documentCommitted && source != null && !loading && error == null &&
            (!isYoutube || adHooks[view]?.canPlay == true)

    private fun alive(web: WebView) = !destroyed && rendererAlive && web === view
    private fun selectedDocument(url: String?): Boolean {
        val selected = source ?: return false
        if (url == null) return false
        return if (isYoutube) Links.https(url) && Links.youtube(url) && Links.videoId(url) == Links.videoId(selected)
        else url == "https://com.weiktube.app/"
    }
    private fun acceptsDocument(web: WebView, url: String?): Boolean {
        if (!alive(web) || source == null) return false
        if (selectedDocument(url)) return true
        trace(PlayerDiagnostics.Event.STALE_DOCUMENT_IGNORED)
        return false
    }
    private fun trace(event: PlayerDiagnostics.Event, web: WebView? = null) = PlayerDiagnostics.record(event, generation,
        when { destroyed -> "DESTROYED"; !rendererAlive -> "RENDERER_GONE"; rendererUnresponsive -> "UNRESPONSIVE";
            viewPaused -> "PAUSED"; loading -> "LOADING"; source == null -> "IDLE"; else -> "READY" },
        activity.diagnosticActivityId, diagnosticControllerId, web?.let { viewIds[it] } ?: currentViewId,
        web?.let { PlayerDiagnostics.captureView(it) })

    private fun traceViewIfChanged(web: WebView) {
        if (web !in viewIds) return
        val snapshot = PlayerDiagnostics.captureView(web)
        if (viewSnapshots.put(web, snapshot) != snapshot) trace(PlayerDiagnostics.Event.VIEW_HOST_UPDATE, web)
    }

    private fun bindFullscreenCallbacks(web: WebView) {
        // The browser view survives video changes. A callback object belongs to one selection.
        val callbacks = object : WebChromeClient() {
            private fun current() = alive(web) && web.webChromeClient === this && source != null
            override fun onReceivedTitle(view: WebView, value: String?) {
                if (view !== web || !current() || !documentCommitted || !selectedDocument(web.url)) return
                if (isYoutube && value == web.title) acceptWatchTitle(value)
            }
            override fun onShowCustomView(surface: android.view.View, callback: CustomViewCallback) {
                if (current()) activity.showFullscreen(surface, callback, web)
                else runCatching { callback.onCustomViewHidden() }
            }
            override fun onHideCustomView() {
                if (current()) activity.hidePlayerFullscreen(web)
            }
        }
        web.webChromeClient = callbacks
    }
    private fun createView(): WebView {
        val candidate = webViewFactory(activity)
        viewIds[candidate] = PlayerDiagnostics.nextViewId()
        if (currentViewId == 0L) currentViewId = viewIds.getValue(candidate)
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
        attachListeners[this] = object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) { trace(PlayerDiagnostics.Event.VIEW_ATTACHED, playerWeb) }
            override fun onViewDetachedFromWindow(v: android.view.View) { trace(PlayerDiagnostics.Event.VIEW_DETACHED, playerWeb) }
        }.also { addOnAttachStateChangeListener(it) }
        layoutListeners[this] = android.view.View.OnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
            if (alive(playerWeb) && (r-l != oldR-oldL || b-t != oldB-oldT)) invalidateGeometry()
            traceViewIfChanged(playerWeb)
        }.also { addOnLayoutChangeListener(it) }
        diagnosticHooks[this] = PlayerPlaybackDiagnostics.install(this)
        playbackHooks[this] = PlayerPlaybackObserver.install(this) { state ->
            if (alive(playerWeb) && source != null && documentCommitted) {
                if (state.width > 0 && state.height > 0) startup("media")
                if (state.frame && state.width > 0 && state.height > 0 && !pictureReady) {
                    pictureReady = true
                    startup("frame-submitted")
                }
                playbackObservationRevision++
                playing = state.playing && playbackAllowed && activity.playerMayRun &&
                    (!isYoutube || adHooks[playerWeb]?.canPlay == true)
                if (playing) {
                    explicitStartRequested = false
                    startup("playing")
                }
                videoWidth = state.width; videoHeight = state.height
                activity.syncPipState()
            }
        }
        val ads = WebViewAdBlock(this,
            onReady = {
                if (alive(playerWeb) && source != null && isYoutube) {
                    startup("filter-ready")
                    applyPlayback(explicitStartRequested)
                    if (!loading && documentCommitted) explicitStartRequested = false
                    activity.syncPipState()
                }
            },
            onBreakthrough = { wasPlaying -> if (alive(playerWeb) && source != null && isYoutube) recoverFromAd(wasPlaying) },
            onFailure = { message -> if (alive(playerWeb) && source != null && isYoutube) {
                pauseFor(PlayerDiagnostics.PauseReason.FILTER_FAILURE); loading = false; error = message
            } })
        adHooks[this] = ads
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(this, MEDIA_GUARD, setOf("https://m.youtube.com", "https://www.youtube.com", "https://youtube.com"))
        }
        navigationHooks[this] = WatchNavigationScript.install(this, toggleFullscreen = { id ->
            if (alive(playerWeb) && documentCommitted && source?.let(Links::videoId) == id)
                activity.toggleWatchFullscreen()
        }) { url ->
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
                if (Links.isShorts(url)) { activity.openVideo(url); return true }
                if (Links.host(url) == "accounts.google.com") { expanded = false; activity.openAccountScreen(); return true }
                if (!Links.https(url)) { activity.external(url); return true }
                if (Links.youtube(url) && Links.videoId(url) == null) { expanded = false; activity.openAccountUrl(url); return true }
                if (!Links.youtube(url)) { activity.external(url); return true }
                // Explicit page navigation uses the same selection path as the native UI. It
                // invalidates outstanding polls before the old document can answer them.
                if (!selectedDocument(url)) { open(url); return true }
                return false
            }
            override fun onPageStarted(web: WebView, url: String, favicon: Bitmap?) {
                if (!acceptsDocument(web, url)) return
                zoomScriptInstalled = false; zoomRevision++; pendingZoom = null; web.removeCallbacks(zoomFrame)
                sponsorBlock.reset()
                generation++; playbackObservationRevision++
                documentCommitted = false
                pictureReady = false
                startup("navigation")
                ads.pageStarted(url)
                loading = true; error = null
                trace(PlayerDiagnostics.Event.NAVIGATION_STARTED)
            }
            override fun onPageCommitVisible(web: WebView, url: String) {
                if (!acceptsDocument(web, url)) return
                documentCommitted = true
                startup("commit")
                if (isYoutube && selectedDocument(web.url)) acceptWatchTitle(web.title)
                applyPresentation()
                playbackHooks[web]?.apply()
                trace(PlayerDiagnostics.Event.DOCUMENT_COMMITTED)
                applyPlayback(explicitStartRequested)
                if (!loading && lastPlaybackGate == true) explicitStartRequested = false
                if (recoveryDocumentPending) {
                    recoveryDocumentPending = false
                    // This confirms a new document, not decoded frames or successful playback.
                    trace(PlayerDiagnostics.Event.RECOVERY_DOCUMENT_READY)
                }
            }
            override fun doUpdateVisitedHistory(web: WebView, url: String?, isReload: Boolean) {
                if (!alive(web) || source == null) return
                // SPA history has no onPageStarted/commit callback. Accept a different video
                // only in an already committed page, corroborated by WebView's current URL.
                if (!selectedDocument(url)) {
                    if (!documentCommitted || loading || !isYoutube || url == null ||
                        !Links.https(url) || !Links.youtube(url) || Links.videoId(url) == null ||
                        Links.videoId(web.url.orEmpty()) != Links.videoId(url)) {
                        trace(PlayerDiagnostics.Event.STALE_DOCUMENT_IGNORED)
                        return
                    }
                    updateVideoIdentity(url)
                }
                applyPresentation()
            }
            override fun onPageFinished(web: WebView, url: String) {
                if (!acceptsDocument(web, url)) return
                // loadDataWithBaseURL may commit a data: URL but finish with our controlled base
                // URL. Keep this existing direct-media path separate from YouTube page commits.
                if (!isYoutube) documentCommitted = true
                loading = false; navigationHooks[web]?.apply(); applyPresentation(); ads.pageFinished()
                playbackHooks[web]?.apply()
                diagnosticHooks[web]?.apply()
                applyPlayback(explicitStartRequested)
                if (documentCommitted && (!isYoutube || ads.canPlay)) explicitStartRequested = false
                trace(PlayerDiagnostics.Event.NAVIGATION_FINISHED)
            }
            override fun onReceivedError(web: WebView, request: WebResourceRequest, failure: WebResourceError) {
                if (request.isForMainFrame && acceptsDocument(web, request.url.toString())) {
                    loading = false; playing = false; error = "Video nicht erreichbar. Prüfe die Verbindung und versuche es erneut."
                    trace(PlayerDiagnostics.Event.NAVIGATION_FAILED)
                }
            }
            override fun onReceivedHttpError(web: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame && response.statusCode >= 400 && acceptsDocument(web, request.url.toString())) {
                    loading = false; error = "YouTube ist momentan nicht erreichbar (${response.statusCode})."
                    trace(PlayerDiagnostics.Event.NAVIGATION_FAILED)
                }
            }
            override fun onRenderProcessGone(web: WebView, detail: RenderProcessGoneDetail): Boolean {
                rendererGone(web, detail.didCrash())
                return true
            }
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
        trace(PlayerDiagnostics.Event.VIEW_CREATED, this)
        }
        } catch (failure: Exception) {
            disposeView(candidate, force = true)
            throw failure
        }
    }

    private var watchStyleScript: androidx.webkit.ScriptHandler? = null
    private fun prepareWatchStyle() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        watchStyleScript?.remove()
        val pip = activity.pip
        val landscape = activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val initial = "window.weiktubeInitialWatchMode=[false,$dark,${landscape || pip},$pip];"
        watchStyleScript = WebViewCompat.addDocumentStartJavaScript(view, initial + WATCH_CHROME,
            setOf("https://m.youtube.com", "https://www.youtube.com", "https://youtube.com"))
    }

    private fun disposeView(web: WebView, force: Boolean = false) {
        watchStyleScript?.remove()
        watchStyleScript = null
        sponsorBlock.retire(web)
        web.removeCallbacks(zoomFrame)
        pendingZoom = null; zoomScriptInstalled = false; zoomRevision++
        val nav = navigationHooks.remove(web)
        val ads = adHooks.remove(web)
        val playback = playbackHooks.remove(web)
        val diagnostics = diagnosticHooks.remove(web)
        if (!force && nav == null && ads == null && playback == null && diagnostics == null) return
        // Cleanup against a dead renderer may throw. One failed bridge must not prevent destruction.
        listOf<() -> Unit>(
            { (web.parent as? android.view.ViewGroup)?.removeView(web) },
            { attachListeners.remove(web)?.let { web.removeOnAttachStateChangeListener(it) } },
            { layoutListeners.remove(web)?.let { web.removeOnLayoutChangeListener(it) } },
            { nav?.close() }, { ads?.close() }, { playback?.close() }, { diagnostics?.close() }, { web.destroy() },
        ).forEach { cleanup -> if (runCatching(cleanup).isFailure) trace(PlayerDiagnostics.Event.CLEANUP_FAILED) }
        trace(PlayerDiagnostics.Event.VIEW_DESTROYED, web)
        viewIds.remove(web); viewSnapshots.remove(web)
    }

    private fun rendererGone(web: WebView, crashed: Boolean) {
        if (!alive(web)) { disposeView(web); return }
        val saved = source?.let { Recovery(it, position, (playing || explicitStartRequested) && playbackAllowed && activity.playerMayRun, expanded) }
        generation++; playbackObservationRevision++
        rendererAlive = false; rendererUnresponsive = false; viewPaused = false
        documentCommitted = false
        loading = false; playing = false; explicitStartRequested = false
        trace(if (crashed) PlayerDiagnostics.Event.RENDERER_CRASHED else PlayerDiagnostics.Event.RENDERER_KILLED)
        activity.recordRotationEvent(if (crashed) "renderer-crashed" else "renderer-killed")
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
            currentViewId = viewIds.getValue(candidate)
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
    private fun recoverLostDocument() {
        if (!documentCommitted || loading || !activity.playerMayRun) return
        val selected = source ?: return
        trace(PlayerDiagnostics.Event.DOCUMENT_LOST)
        val saved = Recovery(selected, position, playing && playbackAllowed, expanded)
        documentCommitted = false
        generation++; playbackObservationRevision++
        playing = false
        if (automaticRecoveries >= 1) {
            error = "Das Videodokument wurde erneut verloren. Bitte erneut laden oder ein anderes Video wählen."
            trace(PlayerDiagnostics.Event.RECOVERY_LIMIT)
            return
        }
        automaticRecoveries++
        pendingRecovery = saved
        trace(PlayerDiagnostics.Event.RECOVERY_PENDING)
        val attempt = generation
        mainHandler.post { if (!destroyed && generation == attempt && activity.playerMayRun) restoreRenderer() }
    }
    private fun updateVideoIdentity(url: String) {
        if (!isYoutube || source == null) return
        val id = Links.videoId(url) ?: return
        if (id != Links.videoId(source!!)) {
            sponsorBlock.reset()
            holdGesture(false); supportedHoldRates = emptyList(); supportedQualities = emptyList()
            saveProgress(); progressSampleValid = false; awaitingResumePosition = null
            beginStartup()
            resumedAt = 0.0
            invalidateGeometry()
            clearVideoZoom()
            playbackHooks[view]?.prepare()
            playbackHooks[view]?.apply()
            generation++; position = 0.0; duration = 0.0; title = cachedTitle(id); lastRecordedTitle = ""
            videoWidth = 0; videoHeight = 0
            adRecoveryVideo = id; adRecoveryAttempts = 0
        }
        source = "https://www.youtube.com/watch?v=$id"
        activity.model.record(source!!, "")
    }
    fun open(url: String, userInitiated: Boolean = true) {
        if (Links.isShorts(url)) { activity.openVideo(url); return }
        openSelected(url, userInitiated)
        if (userInitiated && source != null && Links.historyUrl(source!!) == Links.historyUrl(url))
            activity.model.record(source!!, title.takeUnless { it == "Video" || it == "HTTPS-Video" }.orEmpty())
    }
    /** Process recreation is intentionally paused; no saved Play state can start background audio. */
    fun restore(url: String, seconds: Double) {
        openSelected(url, userInitiated = false, startAt = seconds)
        expanded = false
    }
    private fun openSelected(url: String, userInitiated: Boolean, recovery: Boolean = false, startAt: Double? = null) {
        holdGesture(false)
        clearVideoZoom()
        if (destroyed) return
        val id = Links.videoId(url)
        if (id == null && !Links.directVideoAllowed(url)) { error = "Keine unterstützte Video-Adresse."; return }
        if (!recovery) {
            if (pendingRecovery != null) trace(PlayerDiagnostics.Event.RECOVERY_CANCELLED)
            pendingRecovery = null; automaticRecoveries = 0
        }
        val requiresLoad = !rendererAlive || error != null || startAt != null || Links.hasStartTime(url) ||
            (source != null && !documentCommitted && !loading)
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
        sponsorBlock.reset()
        saveProgress()
        progressSampleValid = false
        historyRevision = activity.model.historyRevision
        playbackAllowed = activity.playerMayRun
        explicitStartRequested = userInitiated
        audibleStartRequested = userInitiated && !recovery
        supportedHoldRates = emptyList(); supportedQualities = emptyList(); qualityStatus = "Noch kein Video geprüft"
        activity.hidePlayerFullscreen(view)
        bindFullscreenCallbacks(view)
        watchSlot = null
        generation++; source = if (id != null) "https://www.youtube.com/watch?v=$id" else url
        documentCommitted = false
        recoveryDocumentPending = recovery
        isYoutube = id != null; expanded = true; mini = false; error = null
        val remembered = if (startAt == null && !Links.hasStartTime(url)) activity.model.progress.get(url)?.seconds else null
        position = (startAt ?: remembered ?: Links.startSeconds(url).toDouble()).takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        resumedAt = remembered ?: 0.0
        awaitingResumePosition = position.takeIf { it > 0 }
        beginStartup()
        duration = 0.0; playing = false; loading = true; title = cachedTitle(id); lastRecordedTitle = ""
        videoWidth = 0; videoHeight = 0
        playbackHooks[view]?.prepare()
        diagnosticHooks[view]?.prepare()
        lastPlaybackGate = null
        if (activity.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) android.util.Log.d("WeiktubePlayback", "document-load session=$generation")
        if (id != null) {
            val watchUrl = "https://m.youtube.com/watch?v=$id&t=${position.toInt()}"
            if (adHooks[view]?.prepare(watchUrl) != true) { loading = false; return }
            prepareWatchStyle()
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
        if (mini != minimized || this.videoOnly != videoOnly || this.systemPip != systemPip) {
            // Freeze the last good expanded slot for the compact animation, reject its pending reads.
            geometryEpoch++
            holdGesture(false)
            clearVideoZoom()
        }
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
        if (destroyed || !rendererAlive || source == null || !documentCommitted) return
        refreshSponsorBlock()
        pollEnhancements()
        pollDislikes()
        val landscapeAtRequest = activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val sampledEpoch = geometryEpoch
        val sampleSlot = expanded && !mini && !systemPip
        val polledView = view
        val requestGeneration = generation
        val observationRevision = playbackObservationRevision
        view.evaluateJavascript("""
            (function(){var v=document.querySelector('video');var h=document.querySelector('.ytVideoMetadataViewModelTitle,h1,.slim-video-information-title');
              var r=v&&v.getBoundingClientRect();
              var slot=r&&innerWidth>0&&innerHeight>0?[r.left/innerWidth,r.top/innerHeight,r.right/innerWidth,r.bottom/innerHeight]:null;
              return JSON.stringify({slot:slot,viewportAspect:innerWidth/innerHeight,decodedFallback:!!v&&(typeof v.requestVideoFrameCallback!=='function'||v.paused||!window.WeiktubePlaybackState),mediaReady:v?v.readyState:0,seeking:!!v&&v.seeking,ended:!!v&&v.ended,hasVideo:!!v&&v.isConnected,readyState:document.readyState,bodyEmpty:!document.body||document.body.childElementCount===0,
              playing:!!v&&!v.paused&&!v.ended,position:v&&isFinite(v.currentTime)?v.currentTime:0,
              duration:v&&isFinite(v.duration)?v.duration:0,width:v?v.videoWidth:0,height:v?v.videoHeight:0,
              details:!!h&&!!document.querySelector('ytm-slim-owner-renderer,ytm-video-owner-renderer,.slim-owner-icon-and-title'),title:h?h.textContent:(document.title||''),url:location.href});})()
        """.trimIndent()) { raw ->
            if (requestGeneration != generation || !alive(polledView) || source == null) return@evaluateJavascript
            runCatching {
                val decoded = JSONTokener(raw).nextValue() as? String ?: return@runCatching
                val value = JSONObject(decoded)
                val documentUrl = value.optString("url")
                if (!selectedDocument(documentUrl)) {
                    // A responsive JS reply proving that the committed watch document has
                    // disappeared is different from a slow/dead renderer. One bounded reload
                    // of this healthy WebView is allowed; silence/timeouts never trigger it.
                    if (documentUrl == "about:blank" && value.optString("readyState") == "complete" &&
                        value.optBoolean("bodyEmpty") && !value.optBoolean("hasVideo")) recoverLostDocument()
                    else trace(PlayerDiagnostics.Event.STALE_DOCUMENT_IGNORED)
                    return@runCatching
                }
                acceptWatchTitle(value.optString("title"))
                if (value.optBoolean("details")) startup("watch-details")
                // A temporary empty DOM during SPA updates is not an invalid renderer and must
                // not replace the last valid content timestamp with the script's default zero.
                if (!value.optBoolean("hasVideo")) return@runCatching
                val nativeAspect = polledView.width.toDouble() / polledView.height
                val cssAspect = value.optDouble("viewportAspect", Double.NaN)
                if (!pictureReady && value.optBoolean("decodedFallback") && value.optInt("mediaReady") >= 2 &&
                    value.optInt("width") > 0 && value.optInt("height") > 0) {
                    pictureReady = true
                    startup("decoded-frame-fallback")
                }
                if (sampledEpoch == geometryEpoch && nativeAspect.isFinite() && nativeAspect > 0 &&
                    kotlin.math.abs(cssAspect / nativeAspect - 1) < 0.03 &&
                    sampleSlot && expanded && !mini && !systemPip &&
                    landscapeAtRequest == (activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)) {
                    value.optJSONArray("slot")?.takeIf { it.length() == 4 }?.let { a ->
                        val edges = (0..3).map { a.optDouble(it).toFloat() }
                        if (edges.all { it.isFinite() && it in 0f..1.001f } && edges[2] > edges[0] && edges[3] > edges[1]) {
                            sampledLandscape = landscapeAtRequest
                            watchSlot = android.graphics.RectF(edges[0], edges[1], edges[2], edges[3])
                            startup("geometry")
                        }
                    }
                }
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
                    if (awaitingResumePosition?.let { position >= it - 2 } == true) awaitingResumePosition = null
                    progressSampleValid = awaitingResumePosition == null && value.optInt("mediaReady") >= 2 && !value.optBoolean("seeking") &&
                        position.isFinite() && duration.isFinite() && position >= 1 && duration > position &&
                        (!isYoutube || adHooks[view]?.canPlay == true)
                    if (activity.model.preferencesReady && historyRevision == activity.model.historyRevision) {
                        if (value.optBoolean("ended") && (!isYoutube || adHooks[view]?.canPlay == true) && duration > 0 && position >= duration - 1) {
                            source?.let(activity.model.progress::remove); progressSampleValid = false
                        } else if (progressSampleValid) source?.let { activity.model.progress.save(it, position, duration) }
                    }
                }
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
        val allowed = documentCommitted && playbackAllowed && activity.playerMayRun &&
            (!isYoutube || adHooks[view]?.canPlay == true)
        if (lastPlaybackGate != allowed) {
            if (allowed) startup("playback-authorized")
            lastPlaybackGate = allowed
            trace(if (allowed) PlayerDiagnostics.Event.GATE_ALLOWED else when {
                !activity.playerMayRun -> PlayerDiagnostics.Event.GATE_HOST_BLOCKED
                !playbackAllowed -> PlayerDiagnostics.Event.GATE_POLICY_BLOCKED
                else -> PlayerDiagnostics.Event.GATE_FILTER_BLOCKED
            })
        }
        sponsorBlock.gate(allowed)
        val requestSound = allowed && start && audibleStartRequested
        if (requestSound) audibleStartRequested = false
        view.evaluateJavascript(MEDIA_GUARD +
            (if (requestSound) ";window.weiktubeRequestAudibleStart();" else "") +
            ";window.weiktubeSetPlayback($allowed,${start && allowed});", null)
    }
    fun pause() = pauseFor(PlayerDiagnostics.PauseReason.USER)
    internal fun pauseFor(reason: PlayerDiagnostics.PauseReason) {
        sponsorBlock.gate(false)
        saveProgress()
        PlayerDiagnostics.pause(reason, generation, activity.diagnosticActivityId, diagnosticControllerId, currentViewId)
        pendingRecovery = pendingRecovery?.copy(play = false)
        explicitStartRequested = false
        audibleStartRequested = false
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
        traceViewIfChanged(view)
        if (viewPaused) {
            view.onResume(); viewPaused = false
            trace(PlayerDiagnostics.Event.VIEW_RESUMED, view)
            // Refresh the retained native surface after visibility changes, without navigation.
            view.requestLayout(); view.invalidate()
        }
    }
    internal fun pauseView(reason: PlayerDiagnostics.PauseReason = PlayerDiagnostics.PauseReason.HOST_STOP) {
        if (destroyed) return
        holdGesture(false)
        pauseFor(reason)
        if (rendererAlive && !viewPaused) {
            view.onPause(); viewPaused = true
            trace(PlayerDiagnostics.Event.VIEW_PAUSED, view)
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
        if (destroyed || source == null) return
        clearVideoZoom()
        mainHandler.removeCallbacks(clearFeedback)
        gestureFeedback = null
        dislikeJob?.cancel()
        dislikeId = null
        dislikeLabel = null
        expanded = false
        playing = false
        mini = false
        videoOnly = false
        systemPip = false
        sponsorBlock.reset()
        holdGesture(false)
        pendingRecovery = null
        pauseFor(PlayerDiagnostics.PauseReason.CLOSE); generation++; source = null; error = null; loading = false; position = 0.0; duration = 0.0
        videoWidth = 0; videoHeight = 0
        adRecoveryVideo = null; adRecoveryAttempts = 0
        documentCommitted = false
        recoveryDocumentPending = false
        if (rendererAlive) view.loadUrl("about:blank")
    }
    fun destroy() {
        if (destroyed) return
        holdGesture(false)
        mainHandler.removeCallbacks(clearFeedback)
        enhancementScope.cancel()
        sponsorBlock.close()
        saveProgress()
        destroyed = true; pendingRecovery = null; generation++; playbackObservationRevision++
        rendererAlive = false; playing = false; loading = false
        disposeView(view)
    }
    companion object {
        internal val MEDIA_GUARD = """
            (function(){
              if(window.weiktubeSetPlayback)return;
              var allowed=false,pending=false,audible=false,starting=null;
              // One explicit selection may request sound. Never reassert mute/volume on
              // presentation changes, resume, or subsequent user mute/pause operations.
              window.weiktubeRequestAudibleStart=function(){audible=true;};
              function applySound(v){
                if(!allowed||!audible||!v||v.tagName!=='VIDEO')return;
                audible=false;
                var p=document.getElementById&&document.getElementById('movie_player');
                if(p&&typeof p.unMute==='function')p.unMute();
                v.muted=false;
              }
              document.addEventListener('playing',function(e){applySound(e.target);},true);
              function pause(m){if(window.weiktubePlaybackScope)window.weiktubePlaybackScope('APP_GUARD',function(){m.pause();});else m.pause();}
              function start(v){
                if(!allowed||!pending||!v)return;
                pending=false;
                if(starting===v||(v.paused===false&&!v.ended))return;
                starting=v;
                function settled(){if(starting===v)starting=null;}
                v.play().then(settled,settled);
              }
              document.addEventListener('play',function(e){if(!allowed&&e.target&&e.target.pause)pause(e.target);},true);
              document.addEventListener('loadedmetadata',function(e){if(e.target&&e.target.tagName==='VIDEO')start(e.target);},true);
              window.weiktubeSetPlayback=function(next,requestStart){
                allowed=next;if(!allowed){pending=false;audible=false;starting=null;}else if(requestStart)pending=true;
                var v=document.querySelector('video');
                if(!allowed)document.querySelectorAll('video,audio').forEach(pause);
                else if(v&&v.readyState>0){
                  if(requestStart&&v.paused===false&&!v.ended&&v.readyState>=2)applySound(v);
                  start(v);
                }
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
                root.setAttribute('data-weiktube-fullscreen',window.WeiktubeWatchNavigation&&videoOnly&&!mini&&!systemPip?'1':'0');
                root.setAttribute('data-weiktube-mini',mini?'1':'0');
                root.setAttribute('data-weiktube-system-pip',systemPip?'1':'0');
                root.setAttribute('data-weiktube-video-only',videoOnly?'1':'0');root.style.colorScheme=dark?'dark':'light';
                fitWatch();
                if(!document.getElementById('weiktube-watch-style')){
                  var style=document.createElement('style');style.id='weiktube-watch-style';
                  style.textContent='ytm-mobile-topbar-renderer,ytm-pivot-bar-renderer{display:none!important}'+
                    'html[data-weiktube-fullscreen="1"] :is(button,[role=button]):is(.ytp-fullscreen-button,.fullscreen-icon,.fullscreen-button,:has(.fullscreen-icon),[aria-label*="fullscreen" i],[aria-label*="vollbild" i],[aria-label*="plein écran" i]) svg{visibility:hidden}'+
                    'html[data-weiktube-fullscreen="1"] :is(button,[role=button]):is(.ytp-fullscreen-button,.fullscreen-icon,.fullscreen-button,:has(.fullscreen-icon),[aria-label*="fullscreen" i],[aria-label*="vollbild" i],[aria-label*="plein écran" i]){background-image:url("data:image/svg+xml,%3Csvg xmlns=%27http://www.w3.org/2000/svg%27 viewBox=%270 0 24 24%27%3E%3Cpath fill=%27none%27 stroke=%27white%27 stroke-width=%272%27 d=%27M3 8h5V3m8 0v5h5M3 16h5v5m8 0v-5h5%27/%3E%3C/svg%3E")!important;background-position:center!important;background-size:24px!important;background-repeat:no-repeat!important}'+
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
              function initialPresentation(){
                var mode=window.weiktubeInitialWatchMode||[false,true,false,false];
                window.weiktubeWatchPresentation(mode[0],mode[1],mode[2],mode[3]);
              }
              if(document.documentElement)initialPresentation();
              else {
                var rootObserver=new MutationObserver(function(){
                  if(!document.documentElement)return;
                  rootObserver.disconnect();
                  if(!document.getElementById('weiktube-watch-style'))initialPresentation();
                });
                rootObserver.observe(document,{childList:true});
              }
            })();
        """.trimIndent()
    }
}

