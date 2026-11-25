package com.weiktube.app

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.net.toUri
import com.weiktube.home.OriginalHomeController
import com.weiktube.home.OriginalHomeReturn

class MainActivity : ComponentActivity() {
    internal val diagnosticActivityId = PlayerDiagnostics.nextActivityId()
    private fun traceLifecycle(event: PlayerDiagnostics.Event, state: String = "HOST") =
        PlayerDiagnostics.record(event, state = state, activityId = diagnosticActivityId)

    val model: WeiktubeModel by viewModels()
    val shield = ShieldEngine()
    lateinit var player: PlayerController
    lateinit var browser: BrowserController
    lateinit var updater: UpdateController
    lateinit var youtubeAccount: YoutubeAccountController
    lateinit var videoActions: VideoActionsController
    lateinit var subscriptionFeed: SubscriptionFeedController
    lateinit var originalHome: OriginalHomeController
        private set
    var shorts: BrowserController? by mutableStateOf(null)
        private set
    var tab by mutableIntStateOf(0)
    var pip by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
    var blockedCount by mutableLongStateOf(0)
    val isWebTab get() = tab in listOf(0, 1)
    private var auxiliaryBrowser by mutableStateOf(false)
    private var homeCanGoBack by mutableStateOf(false)
    private var hostResumed by mutableStateOf(false)
    val isPersonalHome get() = tab == 0 && !auxiliaryBrowser
    val isOriginalHomeVisible get() = hostResumed && isPersonalHome && !pip && !fullscreen && !(player.source != null && player.expanded)
    val activeBrowser get() = if (tab == 1) shorts ?: browser else browser
    val canHandleBack get() = fullscreen || player.source != null || !isWebTab || tab == 1 ||
        (if (isPersonalHome) homeCanGoBack else auxiliaryBrowser || browser.searchActive || activeBrowser.canGoBack)
    private var customView: View? = null
    private var customViewOwner: android.webkit.WebView? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var orientationBeforeFullscreen = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var orientationBeforePlayer: Int? = null
    private var manualFullscreenSawLandscape = false
    private var orientationChoice: Int? = null
    private var orientationChoiceSource: String? = null

    internal fun toggleWatchFullscreen() {
        if (!player.expanded || player.source == null || pip || !hostResumed) return
        val exit = fullscreen || resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        orientationChoiceSource = player.source
        orientationChoice = if (exit) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        if (fullscreen) hideFullscreen()
        syncPlayerPresentation()
    }

    private var videoOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    private val fullscreenOrientation by lazy {
        object : android.view.OrientationEventListener(this) {
            override fun onOrientationChanged(degrees: Int) {
                if (!hostResumed || pip || degrees == ORIENTATION_UNKNOWN) return
                val previous = videoOrientation
                videoOrientation = UprightVideoOrientation.fromSensor(degrees, previous)
                if ((previous == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) !=
                    (videoOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)) orientationChoice = null
                if (customView == null) {
                    if (previous != videoOrientation) syncPlayerPresentation()
                    return
                }
                if (degrees in 60..120 || degrees in 240..300) manualFullscreenSawLandscape = true
                else if (manualFullscreenSawLandscape && UprightVideoOrientation.isUpright(degrees)) {
                    hideFullscreen()
                }
            }
        }
    }
    var foreground by mutableStateOf(true)
        private set
    private var screenOff by mutableStateOf(false)
    // STARTED remains true for visible PiP. A stale PiP flag after onStop must not grant playback.
    val playerMayRun get() = foreground && !screenOff && !isFinishing && !isDestroyed
    private var pipUiBlocked = false
    private lateinit var nativePip: NativePipController
    private var pendingOriginalHomeVideo: String? = null
    private var lastExternalUrl: String? = null
    private var originalHomePlayer = false
    private var shortsFromHome = false
    // The embedded view uses the same one-time return ticket and existing player entry point.
    private fun acceptOriginalHomeVideo(token: String, raw: String) {
        val url = OriginalHomeReturn.acceptPlayerResult(token, raw)
        if (url == null) {
            if (OriginalHomeReturn.resumeToken() == null) OriginalHomeReturn.discard()
            android.util.Log.w("WeiktubeHome", "RETURN_SESSION_UNAVAILABLE")
            message("Die Videoübergabe ist nicht mehr gültig. Öffne die YouTube-Startseite erneut und tippe das Video noch einmal an.")
        } else {
            pendingOriginalHomeVideo = url
            if (hostResumed) openPendingOriginalHomeVideo()
        }
    }

    fun openOriginalHome() {
        selectTab(0)
        originalHomePlayer = false
    }
    fun openWebsiteAccount() {
        // Queue before showing Home so a cold engine opens the account entry directly.
        originalHome.openWebsiteAccount()
        selectTab(0)
    }
    fun syncOriginalHomeVisibility() {
        if (!::originalHome.isInitialized) return
        originalHome.setVisible(isOriginalHomeVisible)
    }
    fun reloadCurrentPage() {
        if (isPersonalHome) originalHome.reload() else activeBrowser.reload()
    }
    fun openShieldTest() {
        selectTab(0)
        auxiliaryBrowser = true
        syncOriginalHomeVisibility()
        browser.testShield()
        browser.resume()
    }
    private fun openPendingOriginalHomeVideo() {
        if (!hostResumed || !foreground || isFinishing || isDestroyed || !::player.isInitialized) return
        val url = pendingOriginalHomeVideo ?: return
        pendingOriginalHomeVideo = null
        openVideo(url)
        originalHomePlayer = OriginalHomeReturn.resumeToken() != null
    }
    fun closeVideo() {
        if (player.source == null) return
        val returnHome = originalHomePlayer && OriginalHomeReturn.resumeToken() != null
        player.close()
        hidePlayerFullscreen(player.view)
        syncPlayerPresentation()
        originalHomePlayer = false
        if (returnHome) openOriginalHome()
        else syncOriginalHomeVisibility()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val restoreKind = PlayerDiagnostics.classifyRestore(savedInstanceState != null,
            savedInstanceState?.getString(PlayerDiagnostics.PROCESS_MARKER_STATE_KEY))
        traceLifecycle(if (savedInstanceState == null) PlayerDiagnostics.Event.ACTIVITY_CREATE else PlayerDiagnostics.Event.ACTIVITY_RESTORE,
            state = restoreKind.name)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        com.weiktube.filter.YoutubeAdBlock.initialize(this)
        player = PlayerController(this)
        browser = BrowserController(this, shield, ::openVideo)
        updater = UpdateController(this)
        // Registers the official Google authorization result before the Activity reaches STARTED.
        youtubeAccount = YoutubeAccountController(this)
        videoActions = VideoActionsController(youtubeAccount)
        subscriptionFeed = SubscriptionFeedController(youtubeAccount)
        originalHome = OriginalHomeController(this, embedded = true,
            onVideo = ::acceptOriginalHomeVideo,
            onExit = { selectTab(0) },
            onNavigationState = { homeCanGoBack = it })
        nativePip = NativePipController(this, ::pipSnapshot, player::play, player::pause, {
            reconcileScreenState(PlayerDiagnostics.ScreenSignal.SCREEN_OFF)
        }, ::message,
            screenUnlocked = { reconcileScreenState(PlayerDiagnostics.ScreenSignal.USER_PRESENT) },
            screenOn = { reconcileScreenState(PlayerDiagnostics.ScreenSignal.SCREEN_ON) })
        setContent { WeiktubeApp(this, model) }
        lastExternalUrl = savedInstanceState?.getString(ExternalYoutubeIntent.STATE_KEY)
        if (savedInstanceState != null && !ExternalYoutubeIntent.isFreshLaunch(intent, lastExternalUrl)) {
            if (savedInstanceState.containsKey("orientation_before_player"))
                orientationBeforePlayer = savedInstanceState.getInt("orientation_before_player")
            // A new Activity starts on the original Home. The persistent Gecko profile retains login.
            tab = 0
            savedInstanceState.getString("video")?.let {
                player.restore(it, savedInstanceState.getDouble("video_position", Links.startSeconds(it).toDouble()))
            }
            player.expanded = false
            originalHomePlayer = savedInstanceState.getBoolean("original_home_player", false) && OriginalHomeReturn.resumeToken() != null
            // A recreated Activity shows Home with the existing saved player minimized and paused.
            // Normal rotation is handled in place by the manifest and keeps its current presentation.
        } else {
            acceptIntent(intent)
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); acceptIntent(intent) }
    private fun acceptIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            openOriginalHome(); return
        }
        val url = ExternalYoutubeIntent.url(intent) ?: return
        lastExternalUrl = url
        pendingOriginalHomeVideo = null
        originalHomePlayer = false
        if (fullscreen) hideFullscreen()
        ExternalYoutubeIntent.route(url, ::openVideo, ::openAccountUrl)
    }
    fun openVideo(url: String) {
        if (Links.isShorts(url)) {
            if (fullscreen) hideFullscreen()
            pendingOriginalHomeVideo = null
            shortsFromHome = tab == 0
            selectTab(1)
            ensureShorts().navigate(Links.videoId(url)?.let { "https://m.youtube.com/shorts/$it" } ?: "https://m.youtube.com/shorts/")
            return
        }
        pendingOriginalHomeVideo = null
        originalHomePlayer = false
        browser.closeSearch()
        browser.pauseMedia(); shorts?.pauseMedia()
        player.open(url)
        player.resumeView()
        syncOriginalHomeVisibility()
    }
    private var settlingCardSource: String? = null
    internal fun cardSettled() {
        settlingCardSource = null
        syncPlayerPresentation()
    }
    fun minimizeWebVideo() {
        if (player.expanded && !pip) settlingCardSource = player.source
        player.expanded = false
        hidePlayerFullscreen(player.view)
        syncPlayerPresentation()
        syncOriginalHomeVisibility()
    }
    fun goHome() {
        val reselectedHome = isPersonalHome && !(player.source != null && player.expanded)
        openOriginalHome()
        if (reselectedHome) originalHome.goHome()
    }
    fun selectTab(value: Int) {
        val enteringShorts = value == 1 && tab != 1
        if (value != 1) shortsFromHome = false
        browser.closeSearch()
        tab = value
        if (player.source != null) player.expanded = false
        if (value == 1) player.pauseFor(PlayerDiagnostics.PauseReason.SHORTS_SELECTED)
        when (value) {
            0 -> {
                auxiliaryBrowser = false
                shorts?.pauseMedia()
                // Showing the retained Gecko document never navigates or creates a second session.
                browser.pauseMedia()
            }
            1 -> {
                browser.pauseMedia()
                val target = ensureShorts()
                if (enteringShorts) target.requestShortsSound()
                target.resume()
            }
            else -> { browser.pauseMedia(); shorts?.pauseMedia() }
        }
        syncOriginalHomeVisibility()
    }
    private fun ensureShorts(): BrowserController = shorts ?: BrowserController(this, shield, ::openVideo, shortsPlayer = true).also {
        shorts = it; it.navigate("https://m.youtube.com/shorts/")
    }
    fun openAccountScreen() { selectTab(4) }

    /** API-listed channels/playlists open their normal YouTube pages without transferring tokens. */
    fun openAccountUrl(url: String) {
        if (Links.videoId(url) != null || Links.isShorts(url)) { openVideo(url); return }
        if (!Links.https(url) || !Links.youtube(url)) {
            message("Diese YouTube-Adresse konnte nicht geöffnet werden.")
            return
        }
        selectTab(0)
        originalHome.navigate(url)
    }
    fun search(query: String) {
        if (query.isBlank()) return
        selectTab(0)
        if (Links.videoId(query.trim()) != null) openVideo(query.trim())
        else if (Links.https(query.trim()) && !Links.youtube(query.trim())) {
            auxiliaryBrowser = true; syncOriginalHomeVisibility(); browser.navigate(query.trim()); browser.resume()
        }
        else if (Links.https(query.trim())) originalHome.navigate(query.trim())
        else originalHome.navigate("https://m.youtube.com/results?search_query=" + android.net.Uri.encode(query.trim()))
    }
    fun openSearch() {
        selectTab(0)
        originalHome.navigate("https://m.youtube.com/results?search_query=")
    }
    fun enterPip() { nativePip.enter() }
    fun syncPipState(blockedByUi: Boolean? = null) {
        if (blockedByUi != null) pipUiBlocked = blockedByUi
        if (::nativePip.isInitialized && ::player.isInitialized) nativePip.sync()
    }
    private fun pipSnapshot(): NativePipController.Snapshot {
        val rect = android.graphics.Rect()
        // The compact WebView retains its large logical size. Its clipped host is the
        // source window; only the contained video pixels belong in the system hint.
        val target = customView ?: (player.view.parent as? PlayerGestureLayout ?: player.view)
        val visible = target.isShown && target.getGlobalVisibleRect(rect)
        if (visible) {
            val origin = IntArray(2)
            target.rootView.getLocationInWindow(origin)
            rect.offset(origin[0], origin[1])
            val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val normal = customView == null && player.expanded && !pip &&
                (target as? PlayerGestureLayout)?.compactViewport != true
            val slot = if (normal) player.mediaSlot(landscape) else null
            val area = android.graphics.RectF(rect)
            if (slot != null) {
                val location = IntArray(2)
                target.getLocationInWindow(location)
                area.set(location[0] + target.width * slot.left, location[1] + target.height * slot.top,
                    location[0] + target.width * slot.right, location[1] + target.height * slot.bottom)
            } else if (normal && player.isYoutube && !landscape) {
                area.bottom = minOf(area.bottom, area.top + area.width() * 9 / 16)
            }
            val aspect = if (player.videoWidth > 0 && player.videoHeight > 0)
                player.videoWidth.toFloat() / player.videoHeight else 16f / 9f
            val w = minOf(area.width(), area.height() * aspect)
            val h = w / aspect
            val media = android.graphics.Rect()
            android.graphics.RectF(area.centerX()-w/2, area.centerY()-h/2,
                area.centerX()+w/2, area.centerY()+h/2).round(media)
            if (!rect.intersect(media)) rect.setEmpty()
        }
        return NativePipController.Snapshot(player.source, player.pipReady, player.playing,
            playerMayRun && visible, hostResumed, pipUiBlocked || (tab == 1 && !player.expanded),
            player.videoWidth, player.videoHeight, rect.takeIf { visible && !it.isEmpty })
    }
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (::nativePip.isInitialized) nativePip.onUserLeaveHint()
    }
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pip = isInPictureInPictureMode
        traceLifecycle(if (pip) PlayerDiagnostics.Event.PIP_ENTER else PlayerDiagnostics.Event.PIP_EXIT)
        if (pip) hideFullscreen()
        syncPlayerPresentation()
        if (pip) { browser.pauseMedia(); shorts?.pauseMedia() }
        else if (playerMayRun && player.source == null && isWebTab && !isPersonalHome) activeBrowser.resume()
        syncOriginalHomeVisibility()
        if (::nativePip.isInitialized) nativePip.modeChanged(pip)
    }
    override fun onPause() {
        player.holdGesture(false)
        traceLifecycle(PlayerDiagnostics.Event.ACTIVITY_PAUSE)
        fullscreenOrientation.disable()
        hostResumed = false
        syncOriginalHomeVisibility()
        super.onPause()
        // PiP is a PAUSED, visible Activity. The system callback may arrive after onPause.
        // Pause the player in onStop instead; never interrupt the existing document here.
        browser.pauseMedia(); shorts?.pauseMedia()
    }
    override fun onStop() {
        traceLifecycle(PlayerDiagnostics.Event.ACTIVITY_STOP)
        foreground = false
        player.pauseView()
        browser.pauseMedia(); shorts?.pauseMedia()
        keepAwake(false)
        if (::nativePip.isInitialized) nativePip.stopped()
        super.onStop()
    }
    override fun onStart() {
        super.onStart()
        traceLifecycle(PlayerDiagnostics.Event.ACTIVITY_START)
        // Reconcile with Android rather than retaining a stale PiP callback flag after backgrounding.
        pip = isInPictureInPictureMode
        foreground = true
        reconcileScreenState(PlayerDiagnostics.ScreenSignal.ACTIVITY_START, resumeIfAllowed = false)
        if (::nativePip.isInitialized) nativePip.modeChanged(pip)
    }
    /** Broadcasts can be deferred while cached. Their name is not the current screen state. */
    internal fun reconcileScreenState(signal: PlayerDiagnostics.ScreenSignal, resumeIfAllowed: Boolean = true) {
        val interactive = getSystemService(android.os.PowerManager::class.java).isInteractive
        val locked = getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
        val previous = screenOff
        screenOff = !interactive || locked
        PlayerDiagnostics.screen(signal, diagnosticActivityId, interactive, locked, previous, screenOff, foreground)
        if (!::player.isInitialized) return
        if (screenOff) {
            player.pauseView(PlayerDiagnostics.PauseReason.SCREEN_LOCK)
            if (::browser.isInitialized) browser.pauseMedia()
            shorts?.pauseMedia()
            keepAwake(false)
        } else if (resumeIfAllowed && playerMayRun) {
            // Resume the retained native view, not playback. User Pause and policy remain intact.
            player.resumeView()
        }
    }
    override fun onResume() {
        super.onResume()
        traceLifecycle(PlayerDiagnostics.Event.ACTIVITY_RESUME)
        hostResumed = true
        reconcileScreenState(PlayerDiagnostics.ScreenSignal.ACTIVITY_RESUME, resumeIfAllowed = false)
        if (fullscreenOrientation.canDetectOrientation()) fullscreenOrientation.enable()
        syncPlayerPresentation()
        if (::updater.isInitialized) updater.onForeground()
        com.weiktube.filter.YoutubeAdBlock.checkForUpdates()
        if (::player.isInitialized) { player.resumeView(); if (isWebTab && !isPersonalHome) activeBrowser.resume() }
        openPendingOriginalHomeVideo()
        syncOriginalHomeVisibility()
        if (::nativePip.isInitialized) nativePip.resumed()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Unlock need not cause another onResume, and USER_PRESENT may have been deferred.
        // Focus loss alone must never pause visible PiP.
        if (hasFocus) reconcileScreenState(PlayerDiagnostics.ScreenSignal.WINDOW_FOCUS)
        if (::originalHome.isInitialized) originalHome.onWindowFocusChanged(hasFocus)
        if (hasFocus) updatePlayerSystemBars()
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        traceLifecycle(PlayerDiagnostics.Event.CONFIGURATION)
        syncPlayerPresentation()
        syncPipState()
    }

    /** Rotation changes only the presentation of the retained player, never its document. */
    fun syncPlayerPresentation() {
        if (!::player.isInitialized) return
        if (player.expanded || pip || settlingCardSource != player.source) settlingCardSource = null
        if (orientationChoiceSource != player.source || !player.expanded) orientationChoice = null
        val desiredOrientation = orientationChoice ?: videoOrientation
        val expanded = player.source != null && player.expanded && !pip && !isFinishing && !isDestroyed
        if (expanded) {
            if (orientationBeforePlayer == null) orientationBeforePlayer =
                if (customView == null) requestedOrientation else orientationBeforeFullscreen
            if (customView == null && requestedOrientation != desiredOrientation)
                requestedOrientation = desiredOrientation
        } else if (customView == null && settlingCardSource == null) {
            orientationBeforePlayer?.let { requestedOrientation = UprightVideoOrientation.safe(it) }
            orientationBeforePlayer = null
        }
        updatePlayerSystemBars()
    }
    private fun updatePlayerSystemBars() {
        val landscapeVideo = ::player.isInitialized && player.source != null && player.expanded &&
            !pip && !isFinishing && !isDestroyed && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        WindowInsetsControllerCompat(window, window.decorView).apply {
            if (fullscreen || landscapeVideo) {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            } else show(WindowInsetsCompat.Type.systemBars())
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(PlayerDiagnostics.PROCESS_MARKER_STATE_KEY, PlayerDiagnostics.processMarker)
        traceLifecycle(PlayerDiagnostics.Event.ACTIVITY_SAVE_STATE)
        orientationBeforePlayer?.let { outState.putInt("orientation_before_player", it) }
        outState.putString(ExternalYoutubeIntent.STATE_KEY, lastExternalUrl)
        outState.putString("browse", browser.currentUrl)
        outState.putInt("tab", tab)
        val url = player.source
        outState.putString("video", if (url != null && player.isYoutube) "https://www.youtube.com/watch?v=${Links.videoId(url)}&t=${player.position.toInt()}" else url)
        outState.putBoolean("expanded", player.expanded)
        outState.putDouble("video_position", player.position)
        outState.putBoolean("original_home_player", originalHomePlayer)
        super.onSaveInstanceState(outState)
    }
    fun showFullscreen(view: View, callback: WebChromeClient.CustomViewCallback, owner: android.webkit.WebView? = null) {
        val playerCannotEnter = owner === player.view && (player.source == null || !player.expanded || pip)
        if (customView != null || playerCannotEnter) {
            callback.onCustomViewHidden()
            return
        }
        customView = view; customCallback = callback; customViewOwner = owner; fullscreen = true
        orientationBeforeFullscreen = requestedOrientation
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        (window.decorView as FrameLayout).addView(view, FrameLayout.LayoutParams(-1, -1))
        manualFullscreenSawLandscape = false
        if (hostResumed && fullscreenOrientation.canDetectOrientation()) fullscreenOrientation.enable()
        updatePlayerSystemBars()
    }
    fun hideFullscreen() {
        if (!hostResumed) fullscreenOrientation.disable()
        manualFullscreenSawLandscape = false
        val wasFullscreen = fullscreen
        customView?.let { (window.decorView as FrameLayout).removeView(it) }
        val callback = customCallback
        customView = null; customCallback = null; customViewOwner = null; fullscreen = false
        callback?.onCustomViewHidden()
        if (wasFullscreen) requestedOrientation = UprightVideoOrientation.safe(orientationBeforeFullscreen)
        syncPlayerPresentation()
    }
    internal fun hidePlayerFullscreen(owner: android.webkit.WebView) {
        if (customViewOwner === owner) hideFullscreen()
    }
    /** A renderer-owned fullscreen surface must not cover the replacement player. */
    internal fun releasePlayerFullscreen(owner: android.webkit.WebView) {
        if (customViewOwner !== owner) return
        customCallback = null // Never invoke a callback into the dead renderer.
        hideFullscreen()
    }
    fun back(): Boolean = when {
        fullscreen -> { hideFullscreen(); true }
        tab == 1 && shortsFromHome && !(player.source != null && player.expanded) -> { shortsFromHome = false; openOriginalHome(); true }
        player.source != null && player.expanded -> {
            // Back, the down arrow and the drag gesture minimize the same retained player.
            // Returning to its Home document is not a playback-pause command.
            minimizeWebVideo()
            if (originalHomePlayer && OriginalHomeReturn.resumeToken() != null) {
                openOriginalHome()
            }
            true
        }
        isPersonalHome && homeCanGoBack -> originalHome.back()
        !isPersonalHome && browser.searchActive -> browser.closeSearch()
        !isWebTab -> { selectTab(0); true }
        !isPersonalHome && activeBrowser.canGoBack -> activeBrowser.goBack()
        tab == 0 && auxiliaryBrowser -> { openOriginalHome(); true }
        tab == 1 -> { selectTab(0); true }
        player.source != null -> { player.close(); true }
        else -> false
    }
    fun share() {
        val url = player.source ?: browser.currentUrl
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url) }, "Mit Weiktube teilen"))
    }
    fun external(url: String) {
        if (!Links.https(url)) { message("Diese Adresse wird aus Sicherheitsgründen nicht geöffnet."); return }
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
        val choices = packageManager.queryIntentActivities(intent, 0).filter { it.activityInfo.packageName != packageName }.map {
            Intent(intent).setClassName(it.activityInfo.packageName, it.activityInfo.name)
        }
        if (choices.isEmpty()) { message("Kein externer Browser verfügbar."); return }
        startActivity(Intent.createChooser(choices.first(), "Extern öffnen").putExtra(Intent.EXTRA_INITIAL_INTENTS, choices.drop(1).toTypedArray()))
    }
    fun clearWebData() {
        pendingOriginalHomeVideo = null; originalHomePlayer = false
        originalHome.clearSession()
        homeCanGoBack = false
        OriginalHomeReturn.discard()
        com.weiktube.home.HomeEngine.clearIfInitialized(this)
        hideFullscreen()
        browser.closeSearch(); shorts?.closeSearch()
        browser.pauseMedia(); shorts?.pauseMedia(); player.close()
        val activeViews = listOfNotNull(browser.view, player.view, shorts?.view)
        activeViews.forEach { web ->
            web.stopLoading(); web.loadUrl("about:blank")
            web.clearHistory(); web.clearCache(true); web.clearFormData(); web.clearSslPreferences()
        }
        // Shorts is created lazily. Discard its document and back stack completely.
        shorts?.destroy(); shorts = null
        android.webkit.WebViewDatabase.getInstance(this).apply {
            clearFormData(); clearHttpAuthUsernamePassword()
        }
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush(); WebStorage.getInstance().deleteAllData()
            if (!isDestroyed && !isFinishing) {
                listOf(browser.view, player.view).forEach { web ->
                    web.clearCache(true); web.clearHistory()
                    web.post { if (!isDestroyed && web.url == "about:blank") web.clearHistory() }
                }
                // Native Google authorization is separate from these website cookies.
                message("Websitzungen, Cookies, Cache und Webspeicher gelöscht.")
            }
        }
    }
    fun message(text: String) { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
    fun keepAwake(playing: Boolean) {
        if (playing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    override fun onDestroy() {
        traceLifecycle(PlayerDiagnostics.Event.ACTIVITY_DESTROY, when {
            isChangingConfigurations -> "CONFIGURATION_CHANGE"
            isFinishing -> "FINISHING"
            else -> "HOST"
        })
        hostResumed = false
        if (::nativePip.isInitialized) nativePip.close()
        originalHome.close()
        if (isFinishing) OriginalHomeReturn.discard()
        subscriptionFeed.close()
        youtubeAccount.close()
        updater.close()
        hideFullscreen()
        browser.destroy()
        shorts?.destroy()
        videoActions.close()
        player.destroy()
        super.onDestroy()
    }
}
