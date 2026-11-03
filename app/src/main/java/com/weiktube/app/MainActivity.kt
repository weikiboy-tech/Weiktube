package com.weiktube.app

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
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
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var orientationBeforeFullscreen = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    var foreground by mutableStateOf(true)
        private set
    private var pendingOriginalHomeVideo: String? = null
    private var originalHomePlayer = false
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
        val returnHome = originalHomePlayer && OriginalHomeReturn.resumeToken() != null
        player.close()
        originalHomePlayer = false
        if (returnHome) openOriginalHome()
        else syncOriginalHomeVisibility()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
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
        setContent { WeiktubeApp(this, model) }
        if (savedInstanceState != null) {
            // A new Activity starts on the original Home. The persistent Gecko profile retains login.
            tab = 0
            savedInstanceState.getString("video")?.let { player.open(it, userInitiated = false) }
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
        val raw = intent.dataString ?: intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val url = Regex("https?://[^\\s]+").find(raw)?.value ?: raw
        if (Links.videoId(url) != null) openVideo(url) else if (Links.youtube(url) && Links.https(url)) openAccountUrl(url)
    }
    fun openVideo(url: String) {
        pendingOriginalHomeVideo = null
        originalHomePlayer = false
        browser.closeSearch()
        browser.pauseMedia(); shorts?.pauseMedia()
        player.open(url)
        player.resumeView()
        syncOriginalHomeVisibility()
    }
    fun minimizeWebVideo() {
        player.expanded = false
        syncOriginalHomeVisibility()
    }
    fun goHome() {
        val reselectedHome = isPersonalHome && !(player.source != null && player.expanded)
        openOriginalHome()
        if (reselectedHome) originalHome.goHome()
    }
    fun selectTab(value: Int) {
        browser.closeSearch()
        tab = value
        if (player.source != null) player.expanded = false
        if (value == 1) player.pause()
        when (value) {
            0 -> {
                auxiliaryBrowser = false
                shorts?.pauseMedia()
                // Showing the retained Gecko document never navigates or creates a second session.
                browser.pauseMedia()
            }
            1 -> { browser.pauseMedia(); ensureShorts().resume() }
            else -> { browser.pauseMedia(); shorts?.pauseMedia() }
        }
        syncOriginalHomeVisibility()
    }
    private fun ensureShorts(): BrowserController = shorts ?: BrowserController(this, shield, ::openVideo).also {
        shorts = it; it.navigate("https://m.youtube.com/shorts/")
    }
    fun openAccountScreen() { selectTab(4) }

    /** API-listed channels/playlists open their normal YouTube pages without transferring tokens. */
    fun openAccountUrl(url: String) {
        if (Links.videoId(url) != null) { openVideo(url); return }
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
    fun enterPip() {
        if (player.source == null || player.isYoutube) return
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            message("Dieses Gerät unterstützt kein Picture-in-Picture."); return
        }
        hideFullscreen()
        try {
            val rect = android.graphics.Rect()
            player.view.getGlobalVisibleRect(rect)
            val params = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
            if (!rect.isEmpty) params.setSourceRectHint(rect)
            // Explicit button only: never auto-enter PiP for YouTube or while browsing.
            if (android.os.Build.VERSION.SDK_INT >= 31) params.setAutoEnterEnabled(false)
            if (!enterPictureInPictureMode(params.build()))
                message("Picture-in-Picture ist in den Android-Einstellungen deaktiviert.")
        } catch (_: IllegalStateException) { message("Picture-in-Picture ist momentan nicht verfügbar.") }
    }
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pip = isInPictureInPictureMode
        if (pip) { browser.pauseMedia(); shorts?.pauseMedia() }
        else if (foreground && isWebTab && !isPersonalHome) activeBrowser.resume()
        syncOriginalHomeVisibility()
    }
    override fun onPause() {
        hostResumed = false
        syncOriginalHomeVisibility()
        super.onPause()
        if (!isInPictureInPictureMode) { player.pauseView(); browser.pauseMedia(); shorts?.pauseMedia() }
    }
    override fun onStop() {
        foreground = false
        player.pauseView()
        browser.pauseMedia(); shorts?.pauseMedia()
        super.onStop()
    }
    override fun onStart() { super.onStart(); foreground = true }
    override fun onResume() {
        super.onResume()
        hostResumed = true
        if (::updater.isInitialized) updater.onForeground()
        if (::player.isInitialized) { player.resumeView(); if (isWebTab && !isPersonalHome) activeBrowser.resume() }
        openPendingOriginalHomeVideo()
        syncOriginalHomeVisibility()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (::originalHome.isInitialized) originalHome.onWindowFocusChanged(hasFocus)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("browse", browser.currentUrl)
        outState.putInt("tab", tab)
        val url = player.source
        outState.putString("video", if (url != null && player.isYoutube) "https://www.youtube.com/watch?v=${Links.videoId(url)}&t=${player.position.toInt()}" else url)
        outState.putBoolean("expanded", player.expanded)
        outState.putBoolean("original_home_player", originalHomePlayer)
        super.onSaveInstanceState(outState)
    }
    fun showFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) { callback.onCustomViewHidden(); return }
        customView = view; customCallback = callback; fullscreen = true
        orientationBeforeFullscreen = requestedOrientation
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        (window.decorView as FrameLayout).addView(view, FrameLayout.LayoutParams(-1, -1))
        WindowInsetsControllerCompat(window, view).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    fun hideFullscreen() {
        val wasFullscreen = fullscreen
        customView?.let { (window.decorView as FrameLayout).removeView(it) }
        val callback = customCallback
        customView = null; customCallback = null; fullscreen = false
        callback?.onCustomViewHidden()
        if (wasFullscreen) requestedOrientation = orientationBeforeFullscreen
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }
    fun back(): Boolean = when {
        fullscreen -> { hideFullscreen(); true }
        player.source != null && player.expanded -> {
            player.expanded = false
            if (originalHomePlayer && OriginalHomeReturn.resumeToken() != null) {
                player.pause()
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
        hostResumed = false
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
