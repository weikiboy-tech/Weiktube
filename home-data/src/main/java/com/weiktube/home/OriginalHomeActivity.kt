package com.weiktube.home

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.MediaSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import org.mozilla.geckoview.WebRequestError
import java.net.URI
import java.util.UUID

/** Visible, optional website experiment. No API login, feed extraction or player implementation. */
class OriginalHomeActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var root: LinearLayout
    private lateinit var viewport: FrameLayout
    private lateinit var panel: LinearLayout
    private lateinit var origin: TextView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var proof: CheckBox
    private lateinit var enable: Button
    private var runtime: GeckoRuntime? = null
    private var lease: HomeExperimentLease.Lease? = null
    private var main: GeckoSession? = null
    private var popup: GeckoSession? = null
    private var mainView: GeckoView? = null
    private var popupView: GeckoView? = null
    private var extension: WebExtension? = null
    private var active = false
    private var generation = 0
    private val approval = OriginalHomeApproval()
    private var confirmed: Boolean
        get() = approval.confirmed
        set(value) { approval.setConfirmed(value) }
    private var handoff = false
    private var installing = false
    private var extensionOperation = false
    private val deferredRelease = mutableListOf<HomeExperimentLease.Lease>()
    private var openingPlayer = false
    private val locations = mutableMapOf<GeckoSession, String>()
    private val history = mutableMapOf<GeckoSession, Boolean>()
    private val documentSerial = mutableMapOf<GeckoSession, Int>()
    private val backgroundAuthentication = mutableSetOf<GeckoSession>()
    private val media = mutableMapOf<GeckoSession, MediaSession>()
    private val connections = mutableMapOf<GeckoSession, Connection>()

    private class Connection(val port: WebExtension.Port, val serial: Int) {
        var documentId: String? = null
        var token: String? = null
        var acknowledged = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(18, 18, 18)) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        listOf("‹" to { back() }, "Home" to { goHome() }, "Konto" to { account() },
            "Test" to { panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE },
            "×" to { finish() }).forEach { (label, action) -> toolbar.addView(button(label, action), LinearLayout.LayoutParams(0, dp(46), 1f)) }
        root.addView(toolbar)
        origin = label("Gecko · Webseite wird vorbereitet …", 12f)
        root.addView(origin)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(2)))
        status = label("Prüfe dein Konto und deine Vorschläge. Anschließend unter „Test“ bestätigen.", 12f).apply { maxLines = 3 }
        root.addView(status)
        panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        panel.addView(label("Diese Webseite verwendet ihr eigenes gespeichertes Profil. Die Google-API-Anmeldung und Chrome sind davon getrennt. Nur du bestätigst hier, ob wirklich dein Konto und deine Vorschläge sichtbar sind.", 13f))
        proof = CheckBox(this).apply {
            text = "Ich sehe mein angemeldetes Konto und meine echten Vorschläge"; setTextColor(Color.WHITE); textSize = 13f
            setOnCheckedChangeListener { _, checked ->
                if (checked && (!active || popup != null || !OriginalHomePolicy.isHomePage(locations[main].orEmpty()))) {
                    isChecked = false; status.text = "Öffne zuerst Home und prüfe dort das angemeldete YouTube-Profil."
                } else {
                    confirmed = checked
                    if (!checked) disableHandoff()
                    updateControls()
                }
            }
        }
        panel.addView(proof)
        enable = button("Videoübergabe an Originalplayer aktivieren") { enableHandoff() }
        panel.addView(enable)
        panel.addView(button("Chrome-Test als Alternative") {
            AlertDialog.Builder(this).setTitle("Separates Chrome-Profil")
                .setMessage("Chrome kann eine eigene YouTube-Anmeldung verwenden. Sie wird nicht in diese Gecko-Ansicht übernommen. Im Chrome-Test erfolgt die Videoübergabe über die angebotene manuelle Aktion.")
                .setNegativeButton("Hier bleiben", null).setPositiveButton("Chrome-Test öffnen") { _, _ ->
                    stopSessions()
                    startActivity(Intent().setClassName(packageName, "com.weiktube.home.ChromeHomeTestActivity"))
                    finish()
                }.show()
        })
        root.addView(panel)
        viewport = FrameLayout(this)
        root.addView(viewport, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        updateControls()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = back()
        })
    }

    override fun onResume() {
        super.onResume(); active = true
        backgroundAuthentication.clear()
        if (main == null && !openingPlayer) prepare()
        main?.setActive(true); popup?.setActive(true)
        if (main != null) showOrigin()
        updateControls()
    }

    private fun prepare() {
        val attempt = ++generation
        HomeEngine.prepare(this, { engine ->
            handler.post {
                if (!current(attempt)) return@post
                runtime = engine
                HomeExperimentLease.acquire(this, engine, { acquired ->
                    if (!current(attempt)) { acquired.release(); return@acquire }
                    lease = acquired
                    val session = createSession()
                    main = session
                    session.open(engine)
                    mainView = GeckoView(this).also { it.setSession(session); viewport.addView(it, FrameLayout.LayoutParams(-1, -1)) }
                    session.setActive(true)
                    session.loadUri(HOME)
                }, { if (current(attempt)) status.text = "Der isolierte Webtest konnte nicht vorbereitet werden. Bitte schließen und erneut öffnen." })
            }
        }, { handler.post { if (current(attempt)) status.text = "Das gespeicherte Webprofil ist gerade nicht verfügbar. Bitte schließen und erneut öffnen." } })
    }

    private fun createSession(): GeckoSession = GeckoSession().apply {
        navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> {
                if (!owns(session)) return decision(false)
                if (!active) return decision(session in backgroundAuthentication && OriginalHomeBackground.navigation(request.uri))
                if (request.uri == "about:blank") return decision(true)
                if (OriginalHomePolicy.isExplicitOAuthUrl(request.uri)) {
                    resetProof()
                    status.text = "Diese Weiterleitung fordert Google-OAuth im Browser an. Sie wird hier nicht eingebettet; Chrome hätte eine getrennte Sitzung."
                    return decision(false)
                }
                if (OriginalHomePolicy.isNormalWebAuthUrl(request.uri)) resetProof()
                val video = OriginalHomePolicy.videoUrl(request.uri)
                if (video != null) {
                    if (handoff && confirmed && request.hasUserGesture) openOriginal(video)
                    else status.text = "Für Videos zuerst unter „Test“ deinen Feed bestätigen und die Originalplayer-Übergabe aktivieren."
                    return decision(false)
                }
                return decision(OriginalHomePolicy.allowedNavigation(request.uri))
            }
            override fun onSubframeLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> {
                if (!owns(session)) return decision(false)
                if (!active) return decision(session in backgroundAuthentication && OriginalHomeBackground.subframe(locations[session].orEmpty(), request.uri))
                return decision(request.uri == "about:blank" || OriginalHomePolicy.allowedNavigation(request.uri) ||
                    OriginalHomePolicy.isNormalWebAuthUrl(locations[session].orEmpty()) && OriginalHomePolicy.allowedAuthSubframe(request.uri))
            }
            override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
                if (!owns(session) || url == null || (!active && session !in backgroundAuthentication)) return
                locations[session] = url
                if (!active) return
                if (OriginalHomePolicy.isNormalWebAuthUrl(url)) resetProof()
                // Some website SPA changes have no onLoadRequest. Only gesture-backed changes may hand off.
                OriginalHomePolicy.videoUrl(url)?.let { video ->
                    if (handoff && confirmed && hasUserGesture) openOriginal(video)
                    else { session.stop(); media[session]?.pause() }
                }
                showOrigin()
            }
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { if (owns(session)) history[session] = canGoBack }
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                if (!active || !owns(session) || popup != null || OriginalHomePolicy.isExplicitOAuthUrl(uri) ||
                    (uri != "about:blank" && !OriginalHomePolicy.allowedNavigation(uri)) || OriginalHomePolicy.videoUrl(uri) != null) return null
                if (OriginalHomePolicy.isNormalWebAuthUrl(uri)) resetProof()
                val child = createSession()
                popup = child
                extension?.let { bind(child, it) }
                handler.post {
                    if (!active || popup !== child || isDestroyed) return@post
                    popupView = GeckoView(this@OriginalHomeActivity).also { it.setSession(child); viewport.addView(it, FrameLayout.LayoutParams(-1, -1)) }
                    child.setActive(true); showOrigin()
                }
                return GeckoResult.fromValue(child)
            }
            override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? {
                if (active && owns(session)) status.text = "Webseite nicht geladen. Gecko-Fehler ${error.category}/${error.code}. Dein bisheriges App-Konto bleibt unverändert."
                return null
            }
        }
        progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                if (!owns(session) || (!active && session !in backgroundAuthentication)) return
                disconnect(session)
                documentSerial[session] = (documentSerial[session] ?: 0) + 1
                locations[session] = url
                if (!active) return
                if (OriginalHomePolicy.isNormalWebAuthUrl(url)) resetProof()
                progress.visibility = View.VISIBLE; showOrigin()
            }
            override fun onProgressChange(session: GeckoSession, value: Int) { if (active && owns(session)) progress.progress = value }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                if (active && owns(session)) {
                    progress.visibility = View.GONE
                    if (!success) status.text = "Die Webseite wurde nicht vollständig geladen. Home lädt sie erneut; eine sichtbare Google-Fehlermeldung bleibt unverändert."
                }
            }
        }
        contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCloseRequest(session: GeckoSession) { if (session === popup) closePopup() }
            override fun onCrash(session: GeckoSession) {
                if (owns(session)) { stopSessions(); status.text = "Der Webtest wurde beendet. Die Original-App bleibt verfügbar; Home startet einen neuen Versuch." }
            }
        }
        mediaSessionDelegate = object : MediaSession.Delegate {
            override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
                if (owns(session)) media[session] = mediaSession
                // Do not mute an audio challenge on a foreground Google website sign-in page.
                // YouTube playback and all background media belong outside this experiment.
                if (!active || !owns(session) || !OriginalHomePolicy.isNormalWebAuthUrl(locations[session].orEmpty())) mediaSession.pause()
            }
        }
    }

    private fun enableHandoff() {
        if (!active || !confirmed || installing || extensionOperation || main == null || popup != null || !OriginalHomePolicy.isHomePage(locations[main].orEmpty())) return
        val engine = runtime ?: return
        val attempt = generation
        val authorization = approval.begin() ?: return
        installing = true; extensionOperation = true; updateControls()
        status.text = "Videoübergabe wird vorbereitet. Home wird dafür einmal neu geladen."
        handler.postDelayed({
            if (current(attempt) && approval.permits(authorization) && extensionOperation) {
                stopSessions()
                status.text = "Die Vorbereitung dauert zu lange. Der Webtest wurde angehalten; bitte später erneut öffnen."
            }
        }, 25_000)
        engine.webExtensionController.ensureBuiltIn("resource://android/assets/original-home-navigation/", HomeExperimentLease.CLICK_ID).accept({ found ->
            handler.post {
                if (!current(attempt) || !approval.permits(authorization)) { operationFinished(); return@post }
                if (found == null) { operationFinished(); bridgeFailure(); return@post }
                engine.webExtensionController.enable(found, WebExtensionController.EnableSource.APP).accept({ enabled ->
                    handler.post {
                        operationFinished()
                        if (!current(attempt) || !approval.permits(authorization)) return@post
                        installing = false
                        if (enabled == null || !enabled.metaData.enabled) { bridgeFailure(); return@post }
                        extension = enabled; handoff = true
                        main?.let { bind(it, enabled) }
                        panel.visibility = View.GONE
                        status.text = "Feed von dir bestätigt. Die Verbindung für Videotipps wird fertiggestellt …"
                        updateControls(); main?.loadUri(HOME)
                        handler.postDelayed({
                            if (current(attempt) && handoff && connections.values.none { it.acknowledged })
                                status.text = "Die Klickverbindung wurde noch nicht bestätigt. Home lädt sie erneut; der Webtest gilt noch nicht als bestanden."
                        }, 15_000)
                    }
                }, { handler.post { operationFinished(); if (current(attempt) && approval.permits(authorization)) bridgeFailure() } })
            }
        }, { handler.post { operationFinished(); if (current(attempt) && approval.permits(authorization)) bridgeFailure() } })
    }

    private fun bind(session: GeckoSession, ext: WebExtension) {
        session.webExtensionController.setMessageDelegate(ext, object : WebExtension.MessageDelegate {
            override fun onConnect(port: WebExtension.Port) {
                if (!active || !owns(session) || !handoff || !confirmed || port.sender.session !== session || !port.sender.isTopLevel || !OriginalHomePolicy.isYoutubePage(port.sender.url)) {
                    port.disconnect(); return
                }
                disconnect(session)
                val state = Connection(port, documentSerial[session] ?: 0)
                connections[session] = state
                port.setDelegate(object : WebExtension.PortDelegate {
                    override fun onPortMessage(message: Any, source: WebExtension.Port) {
                        if (!active || !owns(session) || !handoff || !confirmed || connections[session] !== state || source !== port ||
                            state.serial != (documentSerial[session] ?: 0) || source.sender.session !== session || !source.sender.isTopLevel ||
                            !OriginalHomePolicy.isYoutubePage(source.sender.url) || !OriginalHomePolicy.isYoutubePage(locations[session].orEmpty())) return
                        val data = message as? JSONObject ?: return
                        when (data.optString("type")) {
                            "ready" -> {
                                val id = data.optString("documentId")
                                if (!UUID_PATTERN.matches(id) || state.documentId != null) return
                                state.documentId = id; state.token = UUID.randomUUID().toString()
                                runCatching { source.postMessage(JSONObject().put("type", "enable").put("documentId", id).put("generation", state.token)) }
                                    .onFailure { disconnect(session); bridgeFailure() }
                            }
                            "enabled" -> if (matches(state, data)) {
                                state.acknowledged = true
                                status.text = "Klickverbindung bereit. Jetzt einen Videotipp im Originalplayer testen."
                            }
                            "openVideo" -> if (state.acknowledged && matches(state, data)) OriginalHomePolicy.videoUrl(data.optString("url"))?.let { openOriginal(it) }
                        }
                    }
                    override fun onDisconnect(port: WebExtension.Port) {
                        if (connections[session] === state) connections.remove(session)
                    }
                })
            }
        }, "weiktubeOriginalHome")
    }

    private fun matches(state: Connection, message: JSONObject) = state.documentId != null && state.token != null &&
        message.optString("documentId") == state.documentId && message.optString("generation") == state.token

    private fun openOriginal(raw: String) {
        val url = OriginalHomePolicy.videoUrl(raw) ?: return
        if (!active || !confirmed || !handoff || openingPlayer) return
        openingPlayer = true
        stopSessions()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(packageName)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        } catch (_: Exception) { openingPlayer = false; status.text = "Der Originalplayer konnte nicht geöffnet werden. Home startet den Webtest erneut." }
    }

    private fun disconnect(session: GeckoSession) {
        connections.remove(session)?.let { state ->
            runCatching { state.port.postMessage(JSONObject().put("type", "disable").put("documentId", state.documentId).put("generation", state.token)) }
            runCatching { state.port.disconnect() }
        }
    }

    private fun disableHandoff() {
        handoff = false
        connections.keys.toList().forEach(::disconnect)
        media.values.forEach { runCatching { it.pause() } }
    }
    private fun resetProof() {
        val wasConfirmed = confirmed
        confirmed = false; disableHandoff()
        if (::proof.isInitialized) proof.isChecked = false
        if (wasConfirmed && ::status.isInitialized) status.text = "Bitte das aktuelle Profil erneut prüfen. Die Videoübergabe ist deaktiviert."
        updateControls()
    }
    private fun bridgeFailure() { installing = false; disableHandoff(); updateControls(); status.text = "Die Videoübergabe ist noch nicht verfügbar. Die sichtbare Webseite bleibt zum Prüfen geöffnet." }
    private fun operationFinished() {
        extensionOperation = false
        installing = false
        updateControls()
        val pending = deferredRelease.toList(); deferredRelease.clear()
        pending.forEach { it.release() }
    }
    private fun goHome() {
        if (!active) return
        closePopup()
        if (main == null) prepare() else main?.loadUri(HOME)
    }
    private fun account() {
        if (!active) return
        resetProof(); closePopup()
        main?.loadUri("https://www.youtube.com/channel_switcher?next=%2F")
    }
    private fun back() {
        val top = popup ?: main
        when { top != null && history[top] == true -> top.goBack(); popup != null -> closePopup(); else -> finish() }
    }
    private fun closePopup() {
        val child = popup ?: return
        disconnect(child); media.remove(child)?.pause()
        popupView?.let { it.releaseSession(); viewport.removeView(it) }; popupView = null; popup = null
        locations.remove(child); history.remove(child); documentSerial.remove(child); backgroundAuthentication.remove(child)
        runCatching { child.setActive(false); child.close() }
        showOrigin()
    }
    private fun showOrigin() {
        val raw = locations[popup ?: main].orEmpty()
        val parsed = runCatching { URI(raw) }.getOrNull()
        origin.text = if (parsed?.scheme == "https" && parsed.host != null) "Gecko · HTTPS · ${parsed.host}" else "Gecko · Seite wird geladen …"
        // Website content is always visible, including Google's own rejection/error pages.
        mainView?.visibility = if (popup == null) View.VISIBLE else View.INVISIBLE
        updateControls()
    }
    private fun updateControls() {
        if (!::enable.isInitialized) return
        enable.isEnabled = active && confirmed && !installing && !extensionOperation && !handoff && popup == null && main != null
        enable.text = if (handoff) "Originalplayer-Übergabe aktiv" else "Videoübergabe an Originalplayer aktivieren"
    }
    private fun stopSessions() {
        generation++; installing = false; resetProof()
        connections.keys.toList().forEach(::disconnect)
        media.values.toList().forEach { runCatching { it.pause() } }; media.clear()
        mainView?.let { it.releaseSession(); viewport.removeView(it) }; mainView = null
        popupView?.let { it.releaseSession(); viewport.removeView(it) }; popupView = null
        val sessions = listOfNotNull(main, popup); main = null; popup = null
        sessions.forEach { runCatching { it.setActive(false); it.close() } }
        locations.clear(); history.clear(); documentSerial.clear(); backgroundAuthentication.clear(); extension = null
        lease?.let { if (extensionOperation) deferredRelease += it else it.release() }; lease = null
    }
    override fun onPause() {
        backgroundAuthentication.clear()
        listOfNotNull(main, popup).filterTo(backgroundAuthentication) { OriginalHomePolicy.isNormalWebAuthUrl(locations[it].orEmpty()) }
        active = false
        val wasConfirmed = confirmed
        resetProof()
        if (wasConfirmed) status.text = "Nach der Unterbrechung bitte deinen Feed unter „Test“ erneut bestätigen und die Videoübergabe aktivieren."
        media.values.toList().forEach { runCatching { it.pause() } }
        main?.setActive(false); popup?.setActive(false)
        super.onPause()
    }
    override fun onStop() {
        // A user may leave briefly for an authenticator or Google's device confirmation.
        // Keep only that inactive sign-in document alive; onPause already muted all media.
        val awaitingAuthentication = !isFinishing && !openingPlayer && backgroundAuthentication.any(::owns)
        if (::viewport.isInitialized && !awaitingAuthentication) stopSessions()
        super.onStop()
    }
    // Asynchronous extension completions must still release their lease after Activity destruction.
    // Every UI callback checks generation/active; do not discard the cleanup callbacks.
    override fun onDestroy() { if (::viewport.isInitialized) stopSessions(); super.onDestroy() }
    private fun owns(session: GeckoSession) = session === main || session === popup
    private fun current(attempt: Int) = active && !isDestroyed && generation == attempt
    private fun decision(allow: Boolean) = GeckoResult.fromValue(if (allow) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
    private fun label(value: String, size: Float) = TextView(this).apply { text = value; textSize = size; setTextColor(Color.WHITE); setPadding(dp(10), dp(3), dp(10), dp(3)) }
    private fun button(value: String, action: () -> Unit) = Button(this).apply { text = value; isAllCaps = false; textSize = 12f; setOnClickListener { action() } }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object {
        private const val HOME = "https://m.youtube.com/"
        private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}
