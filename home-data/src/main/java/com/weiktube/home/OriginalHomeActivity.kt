package com.weiktube.home

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
    private lateinit var routingLabel: TextView
    private lateinit var autofill: OriginalHomeAutofill
    private lateinit var autofillButton: Button
    private lateinit var autofillActions: LinearLayout
    private var autofillRevision = 0
    private val autofillLoading = mutableSetOf<GeckoSession>()
    private var runtime: GeckoRuntime? = null
    private var lease: HomeExperimentLease.Lease? = null
    private var main: GeckoSession? = null
    private var popup: GeckoSession? = null
    private var mainView: GeckoView? = null
    private var popupView: GeckoView? = null
    private var extension: WebExtension? = null
    private var active = false
    private var generation = 0
    private var confirmed = false
    private var handoff = false
    private var installing = false
    private var extensionOperation = false
    private val deferredRelease = mutableListOf<HomeExperimentLease.Lease>()
    private var openingPlayer = false
    private var attemptedResume = false
    private val locations = mutableMapOf<GeckoSession, String>()
    private val history = mutableMapOf<GeckoSession, Boolean>()
    private val documentSerial = mutableMapOf<GeckoSession, Int>()
    private val backgroundAuthentication = mutableSetOf<GeckoSession>()
    private val media = mutableMapOf<GeckoSession, MediaSession>()
    private val connections = mutableMapOf<GeckoSession, OriginalHomeReturn.Connection>()
    private val loggedRoutingCauses = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(18, 18, 18)) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS }
        listOf("‹" to { back() }, "Home" to { goHome() }, "Konto" to { account() },
            "Test" to { panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE },
            "×" to { finish() }).forEach { (label, action) -> toolbar.addView(button(label, action), LinearLayout.LayoutParams(0, dp(46), 1f)) }
        root.addView(toolbar)
        origin = label("Gecko · Webseite wird vorbereitet …", 12f)
        root.addView(origin)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(2)))
        status = label("Die YouTube-Startseite wird vorbereitet. Videotipps öffnen automatisch den Weiktube-Player.", 12f).apply { maxLines = 3 }
        root.addView(status)
        autofillButton = button("Zugangsdaten vorschlagen") { autofill.suggest() }.apply {
            // A touch on this action must not take focus away from Gecko's real virtual input.
            isFocusable = false; isFocusableInTouchMode = false
        }
        autofillActions = LinearLayout(this).apply {
            visibility = View.GONE; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            addView(autofillButton, LinearLayout.LayoutParams(0, -1, 1f))
            addView(button("Info") {
                val snapshot = autofill.diagnostics()
                AlertDialog.Builder(this@OriginalHomeActivity).setTitle("Autofill-Diagnose")
                    .setMessage("Momentaufnahme vor Öffnen dieses Fensters. Keine Eingaben oder Kontodaten.\n\n$snapshot")
                    .setPositiveButton("Schließen", null).show()
            }.apply { isFocusable = false; contentDescription = "Autofill-Diagnose ohne Eingaben anzeigen" }, LinearLayout.LayoutParams(dp(64), -1))
        }
        root.addView(autofillActions, LinearLayout.LayoutParams(-1, dp(44)))
        panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS }
        panel.addView(label("Diese Webseite verwendet ihr eigenes gespeichertes Profil. Die Google-API-Anmeldung und Chrome sind davon getrennt. Die optionale Bestätigung prüft nur, ob du dein Konto und deine Vorschläge erkennst; die Videoübergabe ist automatisch aktiv.", 13f))
        proof = CheckBox(this).apply {
            text = "Ich sehe mein angemeldetes Konto und meine echten Vorschläge"; setTextColor(Color.WHITE); textSize = 13f
            setOnCheckedChangeListener { _, checked ->
                if (checked && (!active || popup != null || !OriginalHomePolicy.isHomePage(locations[main].orEmpty()))) {
                    isChecked = false; status.text = "Öffne zuerst Home und prüfe dort das angemeldete YouTube-Profil."
                } else {
                    confirmed = checked
                    updateControls()
                }
            }
        }
        panel.addView(proof)
        routingLabel = label("Videoübergabe wird vorbereitet …", 13f)
        panel.addView(routingLabel)
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
        autofill = OriginalHomeAutofill(this, ::autofillTarget, { status.text = it })
        setContentView(root)
        updateControls()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = back()
        })
    }

    override fun onResume() {
        super.onResume(); active = true
        autofill.start()
        backgroundAuthentication.clear()
        if (main == null && !openingPlayer) {
            val retained = if (!attemptedResume) OriginalHomeReturn.take(intent.getStringExtra(OriginalHomeReturn.EXTRA_RESUME_TOKEN)) else null
            attemptedResume = true
            if (retained != null) restoreRetained(retained) else prepare()
        }
        main?.setActive(true); popup?.setActive(true)
        if (main != null) showOrigin()
        connections.toMap().forEach { (session, connection) -> resumeConnection(session, connection) }
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
                    prepareRouting(engine, attempt)
                }, { if (current(attempt)) status.text = "Der isolierte Webtest konnte nicht vorbereitet werden. Bitte schließen und erneut öffnen." })
            }
        }, { handler.post { if (current(attempt)) status.text = "Das gespeicherte Webprofil ist gerade nicht verfügbar. Bitte schließen und erneut öffnen." } })
    }

    private fun createSession(): GeckoSession = configureSession(GeckoSession().apply { settings.suspendMediaWhenInactive = true })

    private fun configureSession(existing: GeckoSession): GeckoSession = existing.apply {
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
                if (OriginalHomePolicy.isVideoNavigation(request.uri)) {
                    val video = OriginalHomePolicy.videoUrl(request.uri)
                    if (video != null && canRoute(session)) openOriginal(video)
                    else {
                        logRoutingCause(if (video == null) "INVALID_VIDEO" else "ROUTING_NOT_READY")
                        status.text = "Dieser Videolink konnte nicht sicher an den Weiktube-Player übergeben werden. Die Website-Wiedergabe bleibt gesperrt."
                    }
                    return decision(false)
                }
                return decision(OriginalHomePolicy.allowedNavigation(request.uri))
            }
            override fun onSubframeLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> {
                if (!owns(session)) return decision(false)
                if (!active) return decision(session in backgroundAuthentication && OriginalHomeBackground.subframe(locations[session].orEmpty(), request.uri))
                if (OriginalHomePolicy.isVideoNavigation(request.uri)) { logRoutingCause("UNTRUSTED_NAVIGATION"); return decision(false) }
                return decision(request.uri == "about:blank" || OriginalHomePolicy.allowedNavigation(request.uri) ||
                    OriginalHomePolicy.isNormalWebAuthUrl(locations[session].orEmpty()) && OriginalHomePolicy.allowedAuthSubframe(request.uri))
            }
            override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
                if (!owns(session) || url == null || (!active && session !in backgroundAuthentication)) return
                invalidateAutofill()
                locations[session] = url
                if (!active) return
                applyMediaPolicy(session)
                if (OriginalHomePolicy.isNormalWebAuthUrl(url)) resetProof()
                // SPA navigation often loses Gecko's gesture flag. This is still our active, owned YouTube document.
                if (OriginalHomePolicy.isVideoNavigation(url)) {
                    media[session]?.pause()
                    val video = OriginalHomePolicy.videoUrl(url)
                    if (video != null && canRoute(session)) openOriginal(video)
                    else {
                        logRoutingCause(if (video == null) "INVALID_VIDEO" else "ROUTING_NOT_READY")
                        session.stop(); status.text = "Ungültiger Videolink. Die Website-Wiedergabe bleibt gesperrt."
                    }
                    return
                }
                showOrigin()
            }
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { if (owns(session)) history[session] = canGoBack }
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                if (!active || !owns(session)) return null
                if (OriginalHomePolicy.isVideoNavigation(uri)) {
                    val video = OriginalHomePolicy.videoUrl(uri)
                    if (video != null && canRoute(session)) openOriginal(video)
                    else logRoutingCause(if (video == null) "INVALID_VIDEO" else "ROUTING_NOT_READY")
                    return null
                }
                if (popup != null || OriginalHomePolicy.isExplicitOAuthUrl(uri) || (uri != "about:blank" && !OriginalHomePolicy.allowedNavigation(uri))) return null
                if (OriginalHomePolicy.isNormalWebAuthUrl(uri)) resetProof()
                invalidateAutofill()
                val child = createSession()
                popup = child
                extension?.let { bind(child, it) }
                handler.post {
                    if (!active || popup !== child || isDestroyed) return@post
                    popupView = createGeckoView(child, R.id.original_home_gecko_popup).also { viewport.addView(it, FrameLayout.LayoutParams(-1, -1)) }
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
                invalidateAutofill()
                autofillLoading += session
                disconnect(session)
                documentSerial[session] = (documentSerial[session] ?: 0) + 1
                locations[session] = url
                if (!active) return
                if (OriginalHomePolicy.isNormalWebAuthUrl(url)) resetProof()
                progress.visibility = View.VISIBLE; showOrigin()
            }
            override fun onProgressChange(session: GeckoSession, value: Int) { if (active && owns(session)) progress.progress = value }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                if (!owns(session)) return
                if (success) autofillLoading.remove(session)
                if (active && owns(session)) {
                    progress.visibility = View.GONE
                    updateControls()
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
                if (!active || session !== (popup ?: main) || !OriginalHomePolicy.isNormalWebAuthUrl(locations[session].orEmpty())) {
                    mediaSession.pause(); mediaSession.muteAudio(true)
                } else mediaSession.muteAudio(false)
            }
            override fun onPlay(session: GeckoSession, mediaSession: MediaSession) {
                if (owns(session)) { media[session] = mediaSession; applyMediaPolicy(session) }
                else { mediaSession.pause(); mediaSession.muteAudio(true) }
            }
        }
    }

    private fun prepareRouting(engine: GeckoRuntime, attempt: Int) {
        installing = true; extensionOperation = true; updateControls()
        status.text = "Videoübergabe wird vor dem Laden der Startseite vorbereitet …"
        handler.postDelayed({
            if (current(attempt) && extensionOperation) {
                logRoutingCause("ROUTING_NOT_READY")
                stopSessions()
                status.text = "Die Videoübergabe konnte nicht rechtzeitig vorbereitet werden. Home startet einen neuen Versuch."
            }
        }, 25_000)
        engine.webExtensionController.ensureBuiltIn("resource://android/assets/original-home-navigation/", HomeExperimentLease.CLICK_ID).accept({ found ->
            handler.post {
                if (!current(attempt)) { operationFinished(); return@post }
                if (found == null) { operationFinished(); setupFailed(); return@post }
                engine.webExtensionController.enable(found, WebExtensionController.EnableSource.APP).accept({ enabled ->
                    handler.post {
                        operationFinished()
                        if (!current(attempt)) return@post
                        if (enabled == null || !enabled.metaData.enabled) { setupFailed(); return@post }
                        extension = enabled; handoff = true
                        val session = createSession()
                        main = session
                        bind(session, enabled)
                        session.open(engine)
                        mainView = createGeckoView(session, R.id.original_home_gecko_main).also { viewport.addView(it, FrameLayout.LayoutParams(-1, -1)) }
                        session.setActive(true)
                        status.text = "Videotipps öffnen automatisch den Weiktube-Player."
                        updateControls()
                        session.loadUri(HOME)
                    }
                }, { handler.post { operationFinished(); if (current(attempt)) setupFailed() } })
            }
        }, { handler.post { operationFinished(); if (current(attempt)) setupFailed() } })
    }

    private fun setupFailed() {
        logRoutingCause("BRIDGE_UNAVAILABLE")
        stopSessions()
        status.text = "Die Videoübergabe ist nicht bereit. Die Startseite wurde deshalb noch nicht geladen. Home versucht es erneut."
    }

    private fun restoreRetained(held: OriginalHomeReturn.Retained) {
        generation++
        runtime = held.runtime; extension = held.extension; lease = held.lease
        main = held.main; popup = held.popup; handoff = true; openingPlayer = false
        locations.putAll(held.locations); history.putAll(held.history); documentSerial.putAll(held.documentSerial)
        connections.putAll(held.connections); media.putAll(held.media); autofillLoading.addAll(held.loading)
        held.sessions().forEach { session ->
            configureSession(session)
            bind(session, held.extension)
        }
        mainView = createGeckoView(held.main, R.id.original_home_gecko_main).also { viewport.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        held.popup?.let { child ->
            popupView = createGeckoView(child, R.id.original_home_gecko_popup).also { viewport.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        }
        media.keys.toList().forEach(::applyMediaPolicy)
        connections.toMap().forEach { (session, connection) ->
            connection.token = null; connection.acknowledged = false
            attachPort(session, connection)
        }
        status.text = "Deine bisherige Startseite ist wieder geöffnet."
        invalidateAutofill()
        showOrigin()
        // No loadUri or restoreState: reattach the same live document and its existing scroll position.
    }

    private fun bind(session: GeckoSession, ext: WebExtension) {
        session.webExtensionController.setMessageDelegate(ext, object : WebExtension.MessageDelegate {
            override fun onConnect(port: WebExtension.Port) {
                if (!active || !owns(session) || !handoff || port.sender.session !== session || !port.sender.isTopLevel || !OriginalHomePolicy.isYoutubePage(port.sender.url)) {
                    port.disconnect(); return
                }
                disconnect(session)
                val state = OriginalHomeReturn.Connection(port, documentSerial[session] ?: 0)
                connections[session] = state
                attachPort(session, state)
            }
        }, OriginalHomeReturn.NATIVE_APP)
    }

    private fun attachPort(session: GeckoSession, state: OriginalHomeReturn.Connection) {
        val port = state.port
        port.setDelegate(object : WebExtension.PortDelegate {
            override fun onPortMessage(message: Any, source: WebExtension.Port) {
                if (!active || !owns(session) || !handoff || openingPlayer || connections[session] !== state || source !== port ||
                    state.serial != (documentSerial[session] ?: 0) || source.sender.session !== session || !source.sender.isTopLevel ||
                    !OriginalHomePolicy.isYoutubePage(source.sender.url) || !OriginalHomePolicy.isYoutubePage(locations[session].orEmpty())) return
                val data = message as? JSONObject ?: run { logRoutingCause("UNTRUSTED_NAVIGATION"); return }
                when (data.optString("type")) {
                    "ready" -> {
                        val id = data.optString("documentId")
                        if (!UUID_PATTERN.matches(id) || (state.documentId != null && state.documentId != id)) {
                            logRoutingCause("UNTRUSTED_NAVIGATION"); return
                        }
                        state.documentId = id
                        resumeConnection(session, state)
                    }
                    "enabled" -> if (matches(state, data)) {
                        state.acknowledged = true
                        if (state.blockedReason == null) status.text = "Videotipps öffnen automatisch den Weiktube-Player."
                    }
                    "openVideo" -> {
                        if (!canRoute(session) || !state.acknowledged || !matches(state, data)) { logRoutingCause("UNTRUSTED_NAVIGATION"); return }
                        val video = OriginalHomePolicy.videoUrl(data.optString("url"))
                        if (video != null) openOriginal(video) else logRoutingCause("INVALID_VIDEO")
                    }
                    "blocked" -> if (data.optString("documentId") == state.documentId &&
                        (matches(state, data) || (!state.acknowledged && !data.has("generation")))) {
                        val reason = data.optString("reason")
                        logRoutingCause(if (reason in ROUTING_CAUSES) reason else "UNTRUSTED_NAVIGATION")
                        if (reason in setOf("HISTORY_GUARD_UNAVAILABLE", "MEDIA_GUARD_UNAVAILABLE")) state.blockedReason = reason
                        status.text = when (reason) {
                            "INVALID_VIDEO" -> "Dieser Videolink ist ungültig. Website-Wiedergabe bleibt gesperrt."
                            "BRIDGE_UNAVAILABLE" -> "Die Player-Verbindung ist unterbrochen. Home lädt sie erneut; Website-Wiedergabe bleibt gesperrt."
                            "HISTORY_GUARD_UNAVAILABLE", "MEDIA_GUARD_UNAVAILABLE" -> "Ein Schutz der Videoübergabe ist nicht verfügbar. Website-Wiedergabe bleibt gesperrt."
                            "UNTRUSTED_NAVIGATION" -> "Automatische Website-Wiedergabe wurde gesperrt. Tippe direkt auf ein Video."
                            else -> "Die Website-Wiedergabe wurde gesperrt. Bitte den Videotipp erneut versuchen."
                        }
                    }
                }
            }
            override fun onDisconnect(port: WebExtension.Port) {
                if (connections[session] === state) {
                    connections.remove(session)
                    if (active && owns(session)) { logRoutingCause("BRIDGE_UNAVAILABLE"); status.text = "Die Player-Verbindung wurde unterbrochen. Home lädt sie erneut." }
                }
            }
        })
    }

    private fun resumeConnection(session: GeckoSession, state: OriginalHomeReturn.Connection) {
        val id = state.documentId ?: return
        if (!canRoute(session) || !OriginalHomePolicy.isYoutubePage(locations[session].orEmpty())) return
        state.token = UUID.randomUUID().toString(); state.acknowledged = false
        runCatching {
            state.port.postMessage(JSONObject().put("type", "enable").put("documentId", id).put("generation", state.token))
        }.onFailure { logRoutingCause("BRIDGE_UNAVAILABLE"); disconnect(session); status.text = "Die Player-Verbindung konnte nicht fortgesetzt werden. Home lädt sie erneut." }
    }

    private fun matches(state: OriginalHomeReturn.Connection, message: JSONObject) = state.documentId != null && state.token != null &&
        message.optString("documentId") == state.documentId && message.optString("generation") == state.token

    private fun openOriginal(raw: String) {
        val url = OriginalHomePolicy.videoUrl(raw) ?: run { logRoutingCause("INVALID_VIDEO"); return }
        if (!active || !handoff || openingPlayer) return
        val engine = runtime ?: return
        val ext = extension ?: return
        val heldLease = lease ?: return
        val feed = main ?: return
        openingPlayer = true
        invalidateAutofill(); autofill.stop()
        media.values.forEach { runCatching { it.pause(); it.muteAudio(true) } }
        mainView?.let { it.releaseSession(); viewport.removeView(it) }; mainView = null
        popupView?.let { it.releaseSession(); viewport.removeView(it) }; popupView = null
        val retained = OriginalHomeReturn.Retained(
            engine, ext, heldLease, feed, popup, locations.toMutableMap(), history.toMutableMap(),
            documentSerial.toMutableMap(), connections.toMutableMap(), media.toMutableMap(), autofillLoading.toSet(),
        )
        val token = OriginalHomeReturn.park(retained, url)
        // Ownership has moved to OriginalHomeReturn. Later onStop/onDestroy must not close these objects.
        main = null; popup = null; lease = null; extension = null; handoff = false
        connections.clear(); media.clear(); locations.clear(); history.clear(); documentSerial.clear(); autofillLoading.clear()
        backgroundAuthentication.clear()
        setResult(RESULT_OK, Intent().putExtra(OriginalHomeReturn.EXTRA_RETURN_TOKEN, token).putExtra(OriginalHomeReturn.EXTRA_VIDEO_URL, url))
        finish()
    }

    private fun disconnect(session: GeckoSession) {
        connections.remove(session)?.let { state ->
            runCatching { state.port.postMessage(JSONObject().put("type", "disable").put("documentId", state.documentId).put("generation", state.token)) }
            runCatching { state.port.disconnect() }
        }
    }

    private fun resetProof() {
        confirmed = false
        if (::proof.isInitialized) proof.isChecked = false
        updateControls()
    }
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
        invalidateAutofill()
        disconnect(child); media.remove(child)?.pause()
        popupView?.let { it.releaseSession(); viewport.removeView(it) }; popupView = null; popup = null
        locations.remove(child); history.remove(child); documentSerial.remove(child); backgroundAuthentication.remove(child); autofillLoading.remove(child)
        runCatching { child.setActive(false); child.close() }
        showOrigin()
        main?.let { session -> connections[session]?.let { resumeConnection(session, it) } }
    }
    private fun showOrigin() {
        val raw = locations[popup ?: main].orEmpty()
        val parsed = runCatching { URI(raw) }.getOrNull()
        origin.text = if (parsed?.scheme == "https" && parsed.host != null) "Gecko · HTTPS · ${parsed.host}" else "Gecko · Seite wird geladen …"
        // Website content is always visible, including Google's own rejection/error pages.
        mainView?.visibility = if (popup == null) View.VISIBLE else View.INVISIBLE
        media.keys.toList().forEach(::applyMediaPolicy)
        updateControls()
    }
    private fun applyMediaPolicy(session: GeckoSession) {
        val source = media[session] ?: return
        runCatching {
            val audibleAuth = active && session === (popup ?: main) && OriginalHomePolicy.isNormalWebAuthUrl(locations[session].orEmpty())
            source.muteAudio(!audibleAuth)
            if (!audibleAuth) source.pause()
        }
    }
    private fun updateControls() {
        if (!::routingLabel.isInitialized) return
        routingLabel.text = if (handoff) "Videotipps öffnen automatisch im Weiktube-Player." else "Videoübergabe wird vorbereitet …"
        if (::autofillButton.isInitialized) {
            val target = autofillTarget()
            autofillActions.visibility = if (active && target?.authPage == true) View.VISIBLE else View.GONE
            autofillButton.isEnabled = active && target?.documentReady == true && target.view.isShown
        }
    }
    private fun stopSessions() {
        invalidateAutofill()
        generation++; installing = false; handoff = false; resetProof()
        connections.keys.toList().forEach(::disconnect)
        media.values.toList().forEach { runCatching { it.pause() } }; media.clear()
        mainView?.let { it.releaseSession(); viewport.removeView(it) }; mainView = null
        popupView?.let { it.releaseSession(); viewport.removeView(it) }; popupView = null
        val sessions = listOfNotNull(main, popup); main = null; popup = null
        sessions.forEach { runCatching { it.setActive(false); it.close() } }
        locations.clear(); history.clear(); documentSerial.clear(); backgroundAuthentication.clear(); autofillLoading.clear(); extension = null
        lease?.let { if (extensionOperation) deferredRelease += it else it.release() }; lease = null
    }
    override fun onPause() {
        backgroundAuthentication.clear()
        listOfNotNull(main, popup).filterTo(backgroundAuthentication) { OriginalHomePolicy.isNormalWebAuthUrl(locations[it].orEmpty()) }
        active = false
        autofill.stop()
        invalidateAutofill()
        resetProof()
        connections.values.forEach { state ->
            state.acknowledged = false
            runCatching { state.port.postMessage(JSONObject().put("type", "disable").put("documentId", state.documentId).put("generation", state.token)) }
        }
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
    override fun onDestroy() {
        if (::autofill.isInitialized) autofill.stop()
        if (::viewport.isInitialized) stopSessions()
        super.onDestroy()
    }
    private fun createGeckoView(session: GeckoSession, viewId: Int) = GeckoView(this).apply {
        id = viewId
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
        // setSession retains Gecko's own default AndroidAutofillDelegate and virtual structure.
        setSession(session)
    }
    private fun autofillTarget(): OriginalHomeAutofill.Target? {
        val session = popup ?: main ?: return null
        val view = (if (session === popup) popupView else mainView) ?: return null
        return OriginalHomeAutofill.Target(session, view, autofillRevision, active && owns(session) && !openingPlayer,
            OriginalHomeAutofillPolicy.isGoogleSignIn(locations[session].orEmpty()), session !in autofillLoading)
    }
    private fun invalidateAutofill() {
        autofillRevision++
        if (::autofill.isInitialized) autofill.invalidate()
    }
    private fun owns(session: GeckoSession) = session === main || session === popup
    private fun canRoute(session: GeckoSession) = active && handoff && !openingPlayer && session === (popup ?: main)
    private fun logRoutingCause(code: String) {
        // Fixed allowlist, at most one line per cause per Activity. Never log URLs, tokens or exceptions.
        if (code in ROUTING_CAUSES && loggedRoutingCauses.add(code)) Log.w("WeiktubeHomeRouting", code)
    }
    private fun current(attempt: Int) = active && !isDestroyed && generation == attempt
    private fun decision(allow: Boolean) = GeckoResult.fromValue(if (allow) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
    private fun label(value: String, size: Float) = TextView(this).apply { text = value; textSize = size; setTextColor(Color.WHITE); importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO; setPadding(dp(10), dp(3), dp(10), dp(3)) }
    private fun button(value: String, action: () -> Unit) = Button(this).apply { text = value; isAllCaps = false; textSize = 12f; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO; setOnClickListener { action() } }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object {
        private const val HOME = "https://m.youtube.com/"
        private val ROUTING_CAUSES = setOf("INVALID_VIDEO", "ROUTING_NOT_READY", "BRIDGE_UNAVAILABLE", "HISTORY_GUARD_UNAVAILABLE", "MEDIA_GUARD_UNAVAILABLE", "UNTRUSTED_NAVIGATION")
        private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}
