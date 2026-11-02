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
    var tab by mutableIntStateOf(0)
    var pip by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
    var blockedCount by mutableLongStateOf(0)
    val isWebTab get() = tab in listOf(0, 1, 3, 4)
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    var foreground by mutableStateOf(true)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        player = PlayerController(this)
        browser = BrowserController(this, shield, ::openVideo)
        updater = UpdateController(this)
        setContent { WeiktubeApp(this, model) }
        if (savedInstanceState != null) {
            browser.navigate(savedInstanceState.getString("browse") ?: "https://m.youtube.com/")
            savedInstanceState.getString("video")?.let(player::open)
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
        if (Links.videoId(url) != null) {
            player.close()
            tab = 0
            browser.view.onResume()
            browser.navigate(url, true)
            model.record(url, "YouTube · ${Links.videoId(url)}")
            return
        }
        player.open(url)
        if (player.source == url) model.record(url, player.title)
    }
    fun minimizeWebVideo() {
        val url = browser.currentUrl
        val id = Links.videoId(url) ?: return
        browser.view.evaluateJavascript("(function(){var v=document.querySelector('video');var t=v?v.currentTime:0;if(v)v.pause();return Math.floor(t);})()") { value ->
            val seconds = value.toIntOrNull()?.coerceAtLeast(0) ?: 0
            player.open("https://www.youtube.com/watch?v=$id&t=$seconds")
            player.expanded = false
            browser.navigate("https://m.youtube.com/")
        }
    }
    fun selectTab(value: Int) {
        if (value == 0 && Links.videoId(browser.currentUrl) != null && player.source == null) {
            tab = 0
            minimizeWebVideo()
            return
        }
        tab = value
        if (player.source != null) player.expanded = false
        if (value == 1) player.pause() // Never play Shorts and the separate player concurrently.
        when (value) {
            0 -> browser.navigate("https://m.youtube.com/")
            1 -> browser.navigate("https://m.youtube.com/shorts/", true)
            3 -> browser.navigate("https://m.youtube.com/feed/subscriptions")
            4 -> browser.navigate("https://m.youtube.com/feed/you")
            else -> { browser.view.onPause() }
        }
        if (isWebTab) browser.view.onResume()
    }
    fun search(query: String) {
        if (query.isBlank()) return
        tab = 0; player.expanded = false; browser.view.onResume()
        if (Links.videoId(query.trim()) != null) openVideo(query.trim())
        else if (Links.https(query.trim())) browser.navigate(query.trim())
        else browser.navigate("https://m.youtube.com/results?search_query=" + android.net.Uri.encode(query.trim()))
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
        if (pip) browser.view.onPause()
        else if (foreground && isWebTab) browser.view.onResume()
    }
    override fun onPause() {
        super.onPause()
        if (!isInPictureInPictureMode) { player.pause(); player.view.onPause(); browser.pauseMedia() }
    }
    override fun onStop() {
        foreground = false
        player.pause(); player.view.onPause(); browser.pauseMedia()
        super.onStop()
    }
    override fun onStart() { super.onStart(); foreground = true }
    override fun onResume() {
        super.onResume()
        if (::updater.isInitialized) updater.onForeground()
        if (::player.isInitialized) { player.view.onResume(); if (isWebTab) browser.view.onResume() }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("browse", browser.currentUrl)
        val url = player.source
        outState.putString("video", if (url != null && player.isYoutube) "https://www.youtube.com/watch?v=${Links.videoId(url)}&t=${player.position.toInt()}" else url)
        outState.putBoolean("expanded", player.expanded)
        super.onSaveInstanceState(outState)
    }
    fun showFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) { callback.onCustomViewHidden(); return }
        customView = view; customCallback = callback; fullscreen = true
        (window.decorView as FrameLayout).addView(view, FrameLayout.LayoutParams(-1, -1))
        WindowInsetsControllerCompat(window, view).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    fun hideFullscreen() {
        customView?.let { (window.decorView as FrameLayout).removeView(it) }
        customView = null; customCallback?.onCustomViewHidden(); customCallback = null; fullscreen = false
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }
    fun back(): Boolean = when {
        fullscreen -> { hideFullscreen(); true }
        player.source != null && player.expanded -> { player.expanded = false; true }
        !isWebTab -> { selectTab(0); true }
        browser.view.canGoBack() -> { browser.view.goBack(); true }
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
        player.close(); browser.view.stopLoading(); browser.view.loadUrl("about:blank")
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush(); WebStorage.getInstance().deleteAllData()
            browser.view.clearCache(true); browser.view.clearHistory()
            message("Cookies, Cache und Webspeicher gelöscht.")
        }
    }
    fun message(text: String) { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
    fun keepAwake(playing: Boolean) {
        if (playing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    override fun onDestroy() { updater.close(); hideFullscreen(); browser.destroy(); player.destroy(); super.onDestroy() }
}
