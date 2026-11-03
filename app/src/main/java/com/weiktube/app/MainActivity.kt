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

class MainActivity : ComponentActivity() {
    val model: WeiktubeModel by viewModels()
    val shield = ShieldEngine()
    lateinit var player: PlayerController
    lateinit var browser: BrowserController
    lateinit var updater: UpdateController
    lateinit var youtubeAccount: YoutubeAccountController
    lateinit var videoActions: VideoActionsController
    lateinit var subscriptionFeed: SubscriptionFeedController
    var shorts: BrowserController? by mutableStateOf(null)
        private set
    var tab by mutableIntStateOf(0)
    var pip by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
    var blockedCount by mutableLongStateOf(0)
    val isWebTab get() = tab in listOf(0, 1)
    val isPersonalHome get() = tab == 0 && !browser.searchActive && HomeRoutePolicy.isHome(browser.currentUrl)
    val activeBrowser get() = if (tab == 1) shorts ?: browser else browser
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var orientationBeforeFullscreen = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    var foreground by mutableStateOf(true)
        private set

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
        setContent { WeiktubeApp(this, model) }
        if (savedInstanceState != null) {
            browser.navigate(savedInstanceState.getString("browse") ?: "https://m.youtube.com/")
            tab = savedInstanceState.getInt("tab", 0)
            if (tab == 1) ensureShorts()
            savedInstanceState.getString("video")?.let { player.open(it, userInitiated = false) }
            player.expanded = savedInstanceState.getBoolean("expanded", true)
            // Process-death restore deliberately does not autoplay; normal rotation retains the WebView.
        } else {
            browser.navigate("https://m.youtube.com/")
            acceptIntent(intent)
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); acceptIntent(intent) }
    private fun acceptIntent(intent: Intent) {
        val raw = intent.dataString ?: intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val url = Regex("https?://[^\\s]+").find(raw)?.value ?: raw
        if (Links.videoId(url) != null) openVideo(url) else if (Links.youtube(url) && Links.https(url)) { tab = 0; browser.navigate(url) }
    }
    fun openVideo(url: String) {
        browser.closeSearch()
        browser.pauseMedia(); shorts?.pauseMedia()
        player.open(url)
        player.resumeView()
    }
    fun minimizeWebVideo() {
        player.expanded = false
    }
    fun goHome() {
        selectTab(0)
        if (!HomeRoutePolicy.isHome(browser.currentUrl)) browser.navigate("https://m.youtube.com/")
    }
    fun selectTab(value: Int) {
        val previous = tab
        val wasExpanded = player.source != null && player.expanded
        browser.closeSearch()
        tab = value
        if (player.source != null) player.expanded = false
        if (value == 1) player.pause()
        when (value) {
            0 -> {
                shorts?.pauseMedia()
                // A tab round-trip preserves the feed and scroll. A second Home tap explicitly goes home.
                if (previous == 0 && !wasExpanded && browser.currentUrl != "https://m.youtube.com/") browser.navigate("https://m.youtube.com/")
                if (isPersonalHome) browser.pauseMedia() else browser.resume()
            }
            1 -> { browser.pauseMedia(); ensureShorts().resume() }
            else -> { browser.pauseMedia(); shorts?.pauseMedia() }
        }
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
        tab = 0
        player.expanded = false
        shorts?.pauseMedia()
        browser.resume()
        browser.navigate(url, true)
    }
    fun search(query: String) {
        if (query.isBlank()) return
        tab = 0; player.expanded = false; shorts?.pauseMedia(); browser.resume()
        if (Links.videoId(query.trim()) != null) openVideo(query.trim())
        else if (Links.https(query.trim())) browser.navigate(query.trim())
        else browser.navigate("https://m.youtube.com/results?search_query=" + android.net.Uri.encode(query.trim()))
    }
    fun openSearch() {
        tab = 0; player.expanded = false; shorts?.pauseMedia(); browser.resume(); browser.openYoutubeSearch()
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
    }
    override fun onPause() {
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
        if (::updater.isInitialized) updater.onForeground()
        if (::player.isInitialized) { player.resumeView(); if (isWebTab && !isPersonalHome) activeBrowser.resume() }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("browse", browser.currentUrl)
        outState.putInt("tab", tab)
        val url = player.source
        outState.putString("video", if (url != null && player.isYoutube) "https://www.youtube.com/watch?v=${Links.videoId(url)}&t=${player.position.toInt()}" else url)
        outState.putBoolean("expanded", player.expanded)
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
        player.source != null && player.expanded -> { player.expanded = false; true }
        browser.searchActive -> browser.closeSearch()
        !isWebTab -> { selectTab(0); true }
        activeBrowser.canGoBack -> activeBrowser.goBack()
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
