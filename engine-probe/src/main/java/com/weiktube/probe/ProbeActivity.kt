package com.weiktube.probe

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONObject
import org.mozilla.geckoview.*
import java.net.URI

/** Isolated feasibility build. All credentials remain in the engine's private, non-backed-up profile. */
class ProbeActivity : ComponentActivity() {
    private val runtime get() = (application as ProbeApplication).runtime
    private lateinit var feed: GeckoSession
    private lateinit var watch: GeckoSession
    private lateinit var feedView: GeckoView
    private lateinit var watchView: GeckoView
    private lateinit var root: LinearLayout
    private lateinit var header: LinearLayout
    private lateinit var bottom: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var playerBox: LinearLayout
    private lateinit var playerBar: LinearLayout
    private lateinit var title: TextView
    private lateinit var origin: TextView
    private lateinit var progress: ProgressBar
    private var extension: WebExtension? = null
    private val ports = mutableMapOf<GeckoSession, WebExtension.Port>()
    private val backAvailable = mutableMapOf<GeckoSession, Boolean>()
    private val media = mutableMapOf<GeckoSession, MediaSession>()
    private var currentVideo: VideoRoute? = null
    private var feedUrl = HOME
    private var expanded = true
    private var fullscreen = false
    private var foreground = false
    private var lastRouteAt = 0L
    private var routed = 0
    private var errors = 0
    private var ready = false
    private var lastFeedPage = HOME
    private var watchUrl = "about:blank"
    private var popup: GeckoSession? = null
    private var popupView: GeckoView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        createUi()
        feed = newSession(false)
        watch = newSession(true)
        feed.open(runtime); watch.open(runtime)
        feedView.setSession(feed); watchView.setSession(watch)
        runtime.webExtensionController.ensureBuiltIn("resource://android/assets/navigation/", EXTENSION_ID).accept({ ext ->
            if (isDestroyed || ext == null) return@accept
            extension = ext
            bind(feed, ext); bind(watch, ext)
            ready = true
            feed.loadUri(HOME)
            savedInstanceState?.getString("video")?.let { VideoRoute.parse(it)?.let { route -> openVideo(route, false) } }
            updateVisibility()
        }, {
            errors++
            showError("Die interne Navigation konnte nicht gestartet werden. Bitte die Test-App neu öffnen.")
        })
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    popup != null -> if (backAvailable[popup] == true) popup?.goBack() else closePopup()
                    fullscreen -> watch.exitFullScreen()
                    currentVideo != null && expanded -> { expanded = false; updateVisibility() }
                    backAvailable[feed] == true -> feed.goBack()
                    currentVideo != null -> closeVideo()
                    else -> finish()
                }
            }
        })
    }

    private fun createUi() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(15,15,15)) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bar = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bar.left, bar.top, bar.right, bar.bottom); insets
        }
        header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("Weiktube Test", 20f), LinearLayout.LayoutParams(0, dp(52), 1f))
        header.addView(button("Konto") { navigate("https://m.youtube.com/feed/library") })
        header.addView(button("⋮") { menu() })
        root.addView(header)
        origin = label("", 12f).apply { visibility = View.GONE; setPadding(dp(12), dp(6), dp(12), dp(6)) }
        root.addView(origin)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(2)))
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        feedView = GeckoView(this)
        content.addView(feedView, FrameLayout.LayoutParams(-1,-1))
        playerBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        watchView = GeckoView(this)
        playerBox.addView(watchView, LinearLayout.LayoutParams(-1, 0, 1f))
        playerBar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(Color.rgb(32,32,32)) }
        playerBar.addView(button("↕") { expanded = !expanded; updateVisibility() })
        title = label("Video", 14f).apply { maxLines = 1; setOnClickListener { expanded = true; updateVisibility() } }
        playerBar.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        playerBar.addView(button("Ⅱ") { media[watch]?.pause(); command(watch, "pause") })
        playerBar.addView(button("×") { closeVideo() })
        playerBox.addView(playerBar, LinearLayout.LayoutParams(-1, dp(48)))
        content.addView(playerBox, FrameLayout.LayoutParams(-1,-1, Gravity.BOTTOM))
        playerBox.visibility = View.GONE
        bottom = LinearLayout(this)
        listOf("Startseite" to HOME, "Abos" to "https://m.youtube.com/feed/subscriptions", "Du" to "https://m.youtube.com/feed/library").forEach { (name, url) ->
            bottom.addView(button(name) { navigate(url) }, LinearLayout.LayoutParams(0,dp(52),1f))
        }
        root.addView(bottom)
        setContentView(root)
    }

    private fun newSession(isWatch: Boolean, isPopup: Boolean = false): GeckoSession = GeckoSession().apply {
        navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> {
                val uri = request.uri
                if (!uri.startsWith("https://") && uri != "about:blank") return GeckoResult.fromValue(AllowOrDeny.DENY)
                if (!isWatch) VideoRoute.parse(uri)?.let {
                    if (isPopup) root.post { closePopup() }
                    openVideo(it, true)
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }
            override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
                if (url == null || url == "about:blank") return
                if (!isWatch && !isPopup) {
                    val route = VideoRoute.parse(url)
                    if (route != null) {
                        openVideo(route, hasUserGesture)
                        if (backAvailable[feed] == true) feed.goBack() else feed.loadUri(lastFeedPage)
                    } else {
                        feedUrl = url
                        if (VideoRoute.youtube(url)) lastFeedPage = url
                    }
                }
                if (isWatch) { watchUrl = url; VideoRoute.parse(url)?.let { currentVideo = it } }
                if (isWatch || currentVideo == null || !expanded) displayOrigin(url)
            }
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { backAvailable[session] = canGoBack }
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                if ((!uri.startsWith("https://") && uri != "about:blank") || popup != null) return null
                // Gecko opens the returned session and preserves window.opener. Never load it here.
                val child = newSession(false, true)
                popup = child
                extension?.let { bind(child, it) }
                root.post {
                    if (isDestroyed || popup !== child) return@post
                    popupView = GeckoView(this@ProbeActivity).also {
                        it.setSession(child)
                        content.addView(it, FrameLayout.LayoutParams(-1, -1))
                    }
                    media.values.forEach { it.pause() }
                    updateVisibility()
                }
                return GeckoResult.fromValue(child)
            }
            override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? {
                errors++; showError("Seite konnte nicht geladen werden. Über ⋮ erneut laden."); return null
            }
        }
        progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, value: Int) { progress.progress = value; progress.visibility = if (value >= 100) View.GONE else View.VISIBLE }
        }
        contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCloseRequest(session: GeckoSession) { if (session === popup) closePopup() }
            override fun onTitleChange(session: GeckoSession, value: String?) { if (isWatch) title.text = value?.take(160) ?: "Video" }
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                fullscreen = fullScreen
                if (fullScreen) expanded = true
                requestedOrientation = if (fullScreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowInsetsControllerCompat(window, root).apply {
                    if (fullScreen) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
                updateVisibility()
            }
            override fun onCrash(session: GeckoSession) { errors++; showError("Die Web-Engine wurde beendet. Bitte die Test-App neu starten.") }
        }
        mediaSessionDelegate = object : MediaSession.Delegate {
            override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
                media[session] = mediaSession
                if (!foreground || (!isWatch && currentVideo != null)) mediaSession.pause()
            }
        }
    }

    private fun bind(session: GeckoSession, ext: WebExtension) {
        session.webExtensionController.setMessageDelegate(ext, object : WebExtension.MessageDelegate {
            override fun onConnect(port: WebExtension.Port) {
                if (port.sender.session !== session || !port.sender.isTopLevel || !VideoRoute.youtube(port.sender.url)) { port.disconnect(); return }
                ports[session] = port
                port.setDelegate(object : WebExtension.PortDelegate {
                    override fun onPortMessage(message: Any, source: WebExtension.Port) {
                        if (ports[session] !== source || !VideoRoute.youtube(source.sender.url) || !source.sender.isTopLevel) return
                        val obj = message as? JSONObject ?: return
                        if (obj.optString("type") == "video" && session === feed && foreground) {
                            VideoRoute.parse(obj.optString("url"))?.let { openVideo(it, true) }
                        }
                    }
                    override fun onDisconnect(port: WebExtension.Port) { if (ports[session] === port) ports.remove(session) }
                })
                configure(session)
            }
        }, "weiktube")
    }
    private fun configure(session: GeckoSession) {
        ports[session]?.postMessage(JSONObject().put("type", "configure").put("role", if (session === watch) "player" else "feed")
            .put("paused", !foreground || popup != null || (session === feed && currentVideo != null)).put("mini", session === watch && !expanded))
    }
    private fun command(session: GeckoSession, type: String) { ports[session]?.postMessage(JSONObject().put("type",type)) }
    private fun openVideo(route: VideoRoute, userInitiated: Boolean) {
        if (!ready) return
        val now = SystemClock.elapsedRealtime()
        if (currentVideo == route && now - lastRouteAt < 800) return
        lastRouteAt = now
        val same = currentVideo == route
        currentVideo = route; expanded = true
        media[feed]?.pause(); command(feed, "pause")
        if (!same) { routed++; watch.loadUri(route.url) }
        updateVisibility()
        if (!userInitiated) { media[watch]?.pause(); command(watch,"pause") }
    }
    private fun closeVideo() {
        if (fullscreen) watch.exitFullScreen()
        media[watch]?.pause(); command(watch,"pause"); watch.loadUri("about:blank")
        currentVideo = null; expanded = true; updateVisibility()
    }
    private fun navigate(url: String) {
        if (!ready) return
        closePopup(false)
        expanded = false
        feed.loadUri(url); updateVisibility()
    }
    private fun updateVisibility() {
        val big = currentVideo != null && expanded
        playerBox.visibility = if (currentVideo == null) View.GONE else View.VISIBLE
        playerBox.layoutParams = FrameLayout.LayoutParams(-1, if (big) -1 else dp(248), Gravity.BOTTOM)
        feedView.visibility = if (big) View.INVISIBLE else View.VISIBLE
        header.visibility = if (big || fullscreen) View.GONE else View.VISIBLE
        bottom.visibility = if (big || fullscreen) View.GONE else View.VISIBLE
        playerBar.visibility = if (fullscreen) View.GONE else View.VISIBLE
        if (popup == null) displayOrigin(if (big) watchUrl else feedUrl)
        if (popup != null) { header.visibility = View.VISIBLE; bottom.visibility = View.GONE }
        if (::feed.isInitialized) { feed.setActive(foreground && !big && popup == null); watch.setActive(foreground && currentVideo != null && popup == null); configure(feed); configure(watch) }
        popup?.setActive(foreground)
        if (foreground && currentVideo != null) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    private fun closePopup(reload: Boolean = true) {
        val child = popup ?: return
        popup = null
        popupView?.let { it.releaseSession(); content.removeView(it) }
        popupView = null
        ports.remove(child)?.disconnect(); media.remove(child); backAvailable.remove(child)
        child.close()
        if (reload) feed.reload()
        updateVisibility()
    }
    private fun displayOrigin(url: String) {
        val host = runCatching { URI(url).host }.getOrNull().orEmpty()
        origin.text = getString(R.string.connection_origin, host)
        origin.visibility = if (host.isNotEmpty() && !VideoRoute.youtube(url)) View.VISIBLE else View.GONE
    }
    private fun menu() {
        AlertDialog.Builder(this).setTitle("Weiktube Test 0.1.0").setItems(arrayOf("Neu laden", "Anmeldung / Konto", "Diagnose teilen", "Webdaten löschen", "Über diesen Test")) { _, which ->
            when(which) {
                0 -> popup?.reload() ?: if (currentVideo != null && expanded) watch.reload() else feed.reload()
                1 -> navigate("https://m.youtube.com/feed/library")
                2 -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Weiktube Test 0.1.0\nEngine: GeckoView 159 stable\nAndroid: ${android.os.Build.VERSION.SDK_INT}\nNavigation bereit: $ready\nVideoöffnungen: $routed\nLadefehler: $errors\nKeine Kontodaten oder URLs enthalten.")
                }, "Diagnose teilen"))
                3 -> AlertDialog.Builder(this).setMessage("Alle Webdaten dieser Test-App löschen und abmelden?").setNegativeButton("Abbrechen",null).setPositiveButton("Löschen") { _, _ ->
                    closePopup(false); closeVideo(); feed.loadUri("about:blank")
                    runtime.storageController.clearData(StorageController.ClearFlags.ALL).accept({ recreate() }, { showError("Webdaten konnten nicht vollständig gelöscht werden.") })
                }.show()
                4 -> AlertDialog.Builder(this).setTitle("Anmeldung zuerst prüfen").setMessage("Diese getrennte Test-App prüft deine echte YouTube-Startseite und die direkte Videoöffnung. Melde dich unter Konto an. Falls Google die Anmeldung blockiert, melde den sichtbaren Fehler. Keine Übernahme der Chrome-Anmeldung. Kein YouTube-Adblock. Deine bisherige Weiktube-App bleibt separat erhalten.").setPositiveButton("OK",null).show()
            }
        }.show()
    }
    private fun showError(text: String) { Toast.makeText(this,text,Toast.LENGTH_LONG).show() }
    private fun label(text: String, size: Float) = TextView(this).apply { this.text=text; textSize=size; setTextColor(Color.WHITE); gravity=Gravity.CENTER_VERTICAL; setPadding(dp(12),0,dp(4),0) }
    private fun button(text: String, action: () -> Unit) = Button(this).apply { this.text=text; isAllCaps=false; textSize=13f; minWidth=0; minimumWidth=0; setTextColor(Color.WHITE); setBackgroundColor(Color.TRANSPARENT); setOnClickListener { action() } }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onResume() { super.onResume(); foreground=true; if (::feed.isInitialized) updateVisibility() }
    override fun onPause() {
        foreground=false
        if (::feed.isInitialized) { media.values.forEach { it.pause() }; command(feed,"pause"); command(watch,"pause"); updateVisibility() }
        super.onPause()
    }
    override fun onSaveInstanceState(outState: Bundle) { currentVideo?.let { outState.putString("video",it.url) }; super.onSaveInstanceState(outState) }
    override fun onDestroy() {
        ports.values.toList().forEach { it.disconnect() }; ports.clear()
        popup?.close(); popup = null
        if (::feed.isInitialized) { feed.close(); watch.close() }
        super.onDestroy()
    }
    companion object { const val HOME="https://m.youtube.com/"; const val EXTENSION_ID="navigation@weiktube.local" }
}
