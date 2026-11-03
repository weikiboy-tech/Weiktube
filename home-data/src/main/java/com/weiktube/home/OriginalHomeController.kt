package com.weiktube.home

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
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

/** The existing original-YouTube view and routing, shared by Home and the compatibility Activity. */
class OriginalHomeController(
    private val activity: ComponentActivity,
    private val embedded: Boolean,
    private val onVideo: (token: String, url: String) -> Unit,
    private val onExit: () -> Unit,
    private val onNavigationState: (Boolean) -> Unit = {},
    initialResumeToken: String? = null,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val root: LinearLayout
    private val viewport: FrameLayout
    private val panel: LinearLayout
    private val origin: TextView
    private val status: TextView
    private val progress: ProgressBar
    private lateinit var proof: CheckBox
    private lateinit var routingLabel: TextView
    private lateinit var autofill: OriginalHomeAutofill
    private lateinit var autofillButton: Button
    private val autofillActions: LinearLayout
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
    private var prepareAfterExtensionOperation = false
    private val deferredRelease = mutableListOf<HomeExperimentLease.Lease>()
    private var openingPlayer = false
    private var parkedToken: String? = initialResumeToken
    private var preparing = false
    private var disposed = false
    private var pendingNavigation: String? = null
    private var initialNavigationPending = false
    private lateinit var retryButton: Button
    val view: View get() = root
    private var restoreFocusPending = false
    private val locations = mutableMapOf<GeckoSession, String>()
    private val history = mutableMapOf<GeckoSession, Boolean>()
    private val documentSerial = mutableMapOf<GeckoSession, Int>()
    private val backgroundAuthentication = mutableSetOf<GeckoSession>()
    private val media = mutableMapOf<GeckoSession, MediaSession>()
    private val connections = mutableMapOf<GeckoSession, OriginalHomeReturn.Connection>()
    private val requestedNavigation = mutableMapOf<GeckoSession, String>()
    private val loggedRoutingCauses = mutableSetOf<String>()

    init {
        root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(18, 18, 18)) }
        val toolbar = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS }
        listOf("‹" to { if (!back()) onExit() }, "Home" to { goHome() }, "Konto" to { account() },
            "Test" to { panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE },
            "×" to { onExit() }).forEach { (label, action) -> toolbar.addView(button(label, action), LinearLayout.LayoutParams(0, dp(46), 1f)) }
        if (!embedded) root.addView(toolbar)
        origin = label("Gecko · Webseite wird vorbereitet …", 12f)
        if (!embedded) root.addView(origin)
        progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(2)))
        status = label("Die YouTube-Startseite wird vorbereitet. Videotipps öffnen automatisch den Weiktube-Player.", 12f).apply { maxLines = 3 }
        status.visibility = if (embedded) View.GONE else View.VISIBLE
        root.addView(status)
        retryButton = button("Erneut versuchen") {
            if (main == null && parkedToken != null) { clearSession(); setVisible(true) }
            else if (main == null) resumeOrPrepare() else reload()
        }.apply { visibility = View.GONE }
        root.addView(retryButton)
        autofillButton = button("Zugangsdaten vorschlagen") { autofill.suggest() }.apply {
            // A touch on this action must not take focus away from Gecko's real virtual input.
            isFocusable = false; isFocusableInTouchMode = false
        }
        autofillActions = LinearLayout(activity).apply {
            visibility = View.GONE; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            addView(autofillButton, LinearLayout.LayoutParams(0, -1, 1f))
            addView(button("Info") {
                val snapshot = autofill.diagnostics()
                AlertDialog.Builder(activity).setTitle("Autofill-Diagnose")
                    .setMessage("Momentaufnahme vor Öffnen dieses Fensters. Keine Eingaben oder Kontodaten.\n\n$snapshot")
                    .setPositiveButton("Schließen", null).show()
            }.apply { isFocusable = false; contentDescription = "Autofill-Diagnose ohne Eingaben anzeigen" }, LinearLayout.LayoutParams(dp(64), -1))
        }
        root.addView(autofillActions, LinearLayout.LayoutParams(-1, dp(44)))
        panel = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS }
        panel.addView(label("Diese Webseite verwendet ihr eigenes gespeichertes Profil. Die Google-API-Anmeldung und Chrome sind davon getrennt. Die optionale Bestätigung prüft nur, ob du dein Konto und deine Vorschläge erkennst; die Videoübergabe ist automatisch aktiv.", 13f))
        proof = CheckBox(activity).apply {
            text = "Ich sehe mein angemeldetes Konto und meine echten Vorschläge"; setTextColor(Color.WHITE); textSize = 13f
            setOnCheckedChangeListener { _, checked ->
                if (checked && (!active || popup != null || !OriginalHomePolicy.isHomePage(locations[main].orEmpty()))) {
                    isChecked = false; setStatus("Öffne zuerst Home und prüfe dort das angemeldete YouTube-Profil.")
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
            AlertDialog.Builder(activity).setTitle("Separates Chrome-Profil")
                .setMessage("Chrome kann eine eigene YouTube-Anmeldung verwenden. Sie wird nicht in diese Gecko-Ansicht übernommen. Im Chrome-Test erfolgt die Videoübergabe über die angebotene manuelle Aktion.")
                .setNegativeButton("Hier bleiben", null).setPositiveButton("Chrome-Test öffnen") { _, _ ->
                    stopSessions()
                    activity.startActivity(Intent().setClassName(activity.packageName, "com.weiktube.home.ChromeHomeTestActivity"))
                    onExit()
                }.show()
        })
        if (!embedded) root.addView(panel)
        viewport = FrameLayout(activity)
        root.addView(viewport, LinearLayout.LayoutParams(-1, 0, 1f))
        autofill = OriginalHomeAutofill(activity, ::autofillTarget, { setStatus(it) })
        updateControls()
    }

    /** A tab/player/background hide suspends the existing document; it never closes it. */
    fun setVisible(visible: Boolean) {
        if (disposed) return
        root.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        if (!visible) { if (active) suspend(); return }
        if (active && parkedToken == null && pendingNavigation == null && !initialNavigationPending) {
            restoreVisibleFocus()
            return
        }
        if (!active) {
            active = true
            autofill.start()
            backgroundAuthentication.clear()
            restoreFocusPending = main != null
        }
        resumeOrPrepare()
        main?.setActive(true); popup?.setActive(true)
        popup?.let { child ->
            if (popupView == null) popupView = createGeckoView(child, R.id.original_home_gecko_popup).also {
                viewport.addView(it, FrameLayout.LayoutParams(-1, -1))
            }
        }
        if (main != null) showOrigin()
        connections.toMap().forEach { (session, connection) -> resumeConnection(session, connection) }
        loadPendingNavigation()
        restoreVisibleFocus()
        updateControls()
    }

    private fun resumeOrPrepare() {
        if (!active || disposed) return
        val token = parkedToken
        if (token != null) {
            val retained = OriginalHomeReturn.take(token)
            if (retained == null) {
                setStatus("Die vorherige Startseite ist nicht mehr verfügbar. Erneut versuchen öffnet eine neue Sitzung.")
                // Do not reset a pending launch or create another session on an unaccepted/stale result.
                // Only an explicit retry may discard this lost return transaction.
                return
            } else {
                parkedToken = null
                restoreRetained(retained)
            }
        }
        if (main == null && !openingPlayer && !preparing) prepare()
    }

    fun onWindowFocusChanged(hasFocus: Boolean) { if (hasFocus) restoreVisibleFocus() }

    private fun restoreVisibleFocus() {
        if (!restoreFocusPending || !active || openingPlayer || !activity.hasWindowFocus()) return
        val session = popup ?: main ?: return
        val view = (if (session === popup) popupView else mainView) ?: return
        view.post {
            if (!restoreFocusPending || !canRoute(session) || !view.isAttachedToWindow || !view.isShown || !activity.hasWindowFocus()) return@post
            // Only restore the retained view once; never steal a focused website input on later callbacks.
            view.requestFocus()
            session.setActive(true)
            if (view.hasFocus()) session.setFocused(true)
            restoreFocusPending = false
            connections[session]?.let { resumeConnection(session, it) }
        }
    }

    private fun prepare() {
        if (disposed || preparing || main != null || parkedToken != null) return
        // Explicit clear/retry may arrive while the old extension operation still owns its lease.
        // Wait for its cleanup instead of asking HomeEngine to acquire a competing owner.
        if (extensionOperation) { prepareAfterExtensionOperation = true; return }
        preparing = true
        val attempt = ++generation
        HomeEngine.prepare(activity, { engine ->
            handler.post {
                if (!current(attempt)) return@post
                runtime = engine
                HomeExperimentLease.acquire(activity, engine, { acquired ->
                    if (!current(attempt)) { acquired.release(); return@acquire }
                    lease = acquired
                    prepareRouting(engine, attempt)
                }, { if (current(attempt)) { preparing = false; setStatus("Die YouTube-Startseite konnte nicht vorbereitet werden. Bitte erneut versuchen.") } })
            }
        }, { handler.post { if (current(attempt)) { preparing = false; setStatus("Das gespeicherte Webprofil ist gerade nicht verfügbar. Bitte erneut versuchen.") } } })
    }

    private fun createSession(): GeckoSession = configureSession(GeckoSession().apply { settings.suspendMediaWhenInactive = true })

    private fun configureSession(existing: GeckoSession): GeckoSession = existing.apply {
        navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> {
                if (!owns(session)) return decision(false)
                if (!active) return decision(
                    session in backgroundAuthentication && OriginalHomeBackground.navigation(request.uri) ||
                        OriginalHomeTabBackground.navigation(locations[session].orEmpty(), request.uri, requestedNavigation[session]))
                if (request.uri == "about:blank") return decision(true)
                if (OriginalHomePolicy.isExplicitOAuthUrl(request.uri)) {
                    resetProof()
                    setStatus("Diese Weiterleitung fordert Google-OAuth im Browser an. Sie wird hier nicht eingebettet; Chrome hätte eine getrennte Sitzung.")
                    return decision(false)
                }
                if (OriginalHomePolicy.isNormalWebAuthUrl(request.uri)) resetProof()
                if (OriginalHomePolicy.isVideoNavigation(request.uri)) {
                    val video = OriginalHomePolicy.videoUrl(request.uri)
                    if (video != null && canRoute(session)) openOriginal(video)
                    else {
                        logRoutingCause(if (video == null) "INVALID_VIDEO" else "ROUTING_NOT_READY")
                        setStatus("Dieser Videolink konnte nicht sicher an den Weiktube-Player übergeben werden. Die Website-Wiedergabe bleibt gesperrt.")
                    }
                    return decision(false)
                }
                return decision(OriginalHomePolicy.allowedNavigation(request.uri))
            }
            override fun onSubframeLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> {
                if (!owns(session)) return decision(false)
                if (!active) return decision(
                    session in backgroundAuthentication && OriginalHomeBackground.subframe(locations[session].orEmpty(), request.uri) ||
                        OriginalHomeTabBackground.navigation(locations[session].orEmpty(), request.uri))
                if (OriginalHomePolicy.isVideoNavigation(request.uri)) { logRoutingCause("UNTRUSTED_NAVIGATION"); return decision(false) }
                return decision(request.uri == "about:blank" || OriginalHomePolicy.allowedNavigation(request.uri) ||
                    OriginalHomePolicy.isNormalWebAuthUrl(locations[session].orEmpty()) && OriginalHomePolicy.allowedAuthSubframe(request.uri))
            }
            override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
                if (!owns(session) || url == null) return
                invalidateAutofill()
                locations[session] = url
                if (url != "about:blank") requestedNavigation.remove(session)
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
                        session.stop(); setStatus("Ungültiger Videolink. Die Website-Wiedergabe bleibt gesperrt.")
                    }
                    return
                }
                showOrigin()
            }
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { if (owns(session)) { history[session] = canGoBack; notifyNavigationState() } }
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
                    if (!active || popup !== child || disposed) return@post
                    popupView = createGeckoView(child, R.id.original_home_gecko_popup).also { viewport.addView(it, FrameLayout.LayoutParams(-1, -1)) }
                    child.setActive(true); showOrigin()
                }
                return GeckoResult.fromValue(child)
            }
            override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? {
                if (active && owns(session)) setStatus("Webseite nicht geladen. Gecko-Fehler ${error.category}/${error.code}. Dein bisheriges App-Konto bleibt unverändert.")
                return null
            }
        }
        progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                if (!owns(session)) return
                invalidateAutofill()
                autofillLoading += session
                disconnect(session)
                documentSerial[session] = (documentSerial[session] ?: 0) + 1
                locations[session] = url
                if (url != "about:blank") requestedNavigation.remove(session)
                progress.visibility = View.VISIBLE
                if (!active) return
                if (OriginalHomePolicy.isNormalWebAuthUrl(url)) resetProof()
                showOrigin()
            }
            override fun onProgressChange(session: GeckoSession, value: Int) { if (active && owns(session)) progress.progress = value }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                if (!owns(session)) return
                if (success) autofillLoading.remove(session)
                progress.visibility = View.GONE
                if (active && owns(session)) {
                    updateControls()
                    if (!success) setStatus("Die Webseite wurde nicht vollständig geladen. Home lädt sie erneut; eine sichtbare Google-Fehlermeldung bleibt unverändert.")
                }
            }
        }
        contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCloseRequest(session: GeckoSession) { if (session === popup) closePopup() }
            override fun onCrash(session: GeckoSession) {
                if (owns(session)) { stopSessions(); setStatus("Der Webtest wurde beendet. Die Original-App bleibt verfügbar; Home startet einen neuen Versuch.") }
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
        setStatus("Videoübergabe wird vor dem Laden der Startseite vorbereitet …", false)
        handler.postDelayed({
            if (current(attempt) && extensionOperation) {
                logRoutingCause("ROUTING_NOT_READY")
                stopSessions()
                setStatus("Die Videoübergabe konnte nicht rechtzeitig vorbereitet werden. Home startet einen neuen Versuch.")
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
                        session.setActive(active)
                        preparing = false
                        initialNavigationPending = true
                        setStatus("Videotipps öffnen automatisch den Weiktube-Player.", false)
                        updateControls()
                        loadPendingNavigation()
                    }
                }, { handler.post { operationFinished(); if (current(attempt)) setupFailed() } })
            }
        }, { handler.post { operationFinished(); if (current(attempt)) setupFailed() } })
    }

    private fun setupFailed() {
        logRoutingCause("BRIDGE_UNAVAILABLE")
        stopSessions()
        setStatus("Die Videoübergabe ist nicht bereit. Die Startseite wurde deshalb noch nicht geladen. Home versucht es erneut.")
    }

    private fun restoreRetained(held: OriginalHomeReturn.Retained) {
        generation++
        runtime = held.runtime; extension = held.extension; lease = held.lease
        main = held.main; popup = held.popup; handoff = true; openingPlayer = false
        restoreFocusPending = true
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
            attachPort(session, connection)
        }
        setStatus("Deine bisherige Startseite ist wieder geöffnet. Die Player-Verbindung wird fortgesetzt …", false)
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
                        state.activation.acknowledge(data.optString("generation"))
                        if (state.blockedReason == null) setStatus("Videotipps öffnen automatisch den Weiktube-Player.", false)
                        updateControls()
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
                        setStatus(when (reason) {
                            "INVALID_VIDEO" -> "Dieser Videolink ist ungültig. Website-Wiedergabe bleibt gesperrt."
                            "BRIDGE_UNAVAILABLE" -> "Die Player-Verbindung wird wiederhergestellt. Die Startseite bleibt geöffnet; Website-Wiedergabe bleibt gesperrt."
                            "HISTORY_GUARD_UNAVAILABLE", "MEDIA_GUARD_UNAVAILABLE" -> "Ein Schutz der Videoübergabe ist nicht verfügbar. Website-Wiedergabe bleibt gesperrt."
                            "UNTRUSTED_NAVIGATION" -> "Automatische Website-Wiedergabe wurde gesperrt. Tippe direkt auf ein Video."
                            else -> "Die Website-Wiedergabe wurde gesperrt. Bitte den Videotipp erneut versuchen."
                        }, issue = reason != "UNTRUSTED_NAVIGATION")
                    }
                }
            }
            override fun onDisconnect(port: WebExtension.Port) {
                if (connections[session] === state) {
                    connections.remove(session)
                    if (active && owns(session)) {
                        logRoutingCause("BRIDGE_UNAVAILABLE")
                        setStatus("Die Player-Verbindung wird wiederhergestellt. Die Startseite bleibt geöffnet.")
                        updateControls()
                    }
                }
            }
        })
    }

    private fun resumeConnection(session: GeckoSession, state: OriginalHomeReturn.Connection) {
        val id = state.documentId ?: return
        if (!currentConnection(session, state)) return
        // ready/pageshow/focus/retry may repeat. Only a real suspension creates a new permission.
        val token = state.activation.enable { UUID.randomUUID().toString() }
        sendEnable(session, state, id, token)
        if (state.retryGeneration == token) return
        state.retryGeneration = token
        listOf(250L, 1_000L, 2_500L).forEach { delay ->
            handler.postDelayed({
                if (currentConnection(session, state) && state.activation.matches(token) && !state.acknowledged)
                    sendEnable(session, state, id, token)
            }, delay)
        }
        handler.postDelayed({
            if (currentConnection(session, state) && state.activation.matches(token) && !state.acknowledged) {
                logRoutingCause("BRIDGE_RESUME_TIMEOUT")
                setStatus("Die Player-Verbindung antwortet noch nicht. Tippe erneut auf ein Video; Website-Wiedergabe bleibt gesperrt.")
                updateControls()
            }
        }, 4_000)
    }

    private fun currentConnection(session: GeckoSession, state: OriginalHomeReturn.Connection) =
        canRoute(session) && connections[session] === state && state.serial == (documentSerial[session] ?: 0) &&
            OriginalHomePolicy.isYoutubePage(locations[session].orEmpty())

    private fun sendEnable(session: GeckoSession, state: OriginalHomeReturn.Connection, id: String, token: String) {
        runCatching {
            state.port.postMessage(JSONObject().put("type", "enable").put("documentId", id).put("generation", token))
        }.onFailure { logRoutingCause("BRIDGE_UNAVAILABLE"); disconnect(session); setStatus("Die Player-Verbindung konnte nicht fortgesetzt werden. Ein erneuter Videotipp versucht die Verbindung wiederherzustellen.") }
    }

    private fun matches(state: OriginalHomeReturn.Connection, message: JSONObject) = state.documentId != null &&
        message.optString("documentId") == state.documentId && state.activation.matches(message.optString("generation"))

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
        // Ownership has moved to OriginalHomeReturn; the host only receives the existing one-time result.
        main = null; popup = null; lease = null; extension = null; handoff = false
        connections.clear(); media.clear(); locations.clear(); history.clear(); documentSerial.clear(); autofillLoading.clear(); requestedNavigation.clear()
        backgroundAuthentication.clear()
        parkedToken = token
        active = false
        root.visibility = View.INVISIBLE
        notifyNavigationState()
        onVideo(token, url)
    }

    private fun disconnect(session: GeckoSession) {
        connections.remove(session)?.let { state ->
            state.activation.suspend()
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
        val requested = prepareAfterExtensionOperation
        prepareAfterExtensionOperation = false
        val pending = deferredRelease.toList(); deferredRelease.clear()
        pending.forEach { it.release() }
        if (requested) handler.post { if (active && !disposed) resumeOrPrepare() }
    }
    fun navigate(url: String) {
        if (disposed) return
        if (!OriginalHomePolicy.allowedNavigation(url)) {
            setStatus("Diese Adresse kann in der YouTube-Startseite nicht geöffnet werden.")
            return
        }
        pendingNavigation = url
        if (active) { resumeOrPrepare(); loadPendingNavigation() }
    }

    private fun loadPendingNavigation() {
        if (!active || openingPlayer || disposed) return
        val session = main ?: return
        val destination = pendingNavigation ?: if (initialNavigationPending) HOME else return
        pendingNavigation = null; initialNavigationPending = false
        closePopup()
        requestedNavigation[session] = destination
        session.loadUri(destination)
    }

    fun goHome() = navigate(HOME)

    fun reload() {
        if (disposed) return
        if (main == null) { if (active) resumeOrPrepare(); return }
        if (active) (popup ?: main)?.reload()
    }

    fun showDiagnostics() {
        AlertDialog.Builder(activity).setTitle("YouTube-Startseite")
            .setMessage("${origin.text}\n\n${routingLabel.text}\n${status.text}\n\n${autofill.diagnostics()}")
            .setNeutralButton("Startseite") { _, _ -> goHome() }
            .setNegativeButton("Neu laden") { _, _ -> reload() }
            .setPositiveButton("Schließen", null).show()
    }

    private fun account() {
        if (!active) return
        resetProof(); closePopup()
        main?.loadUri("https://www.youtube.com/channel_switcher?next=%2F")
    }
    fun back(): Boolean {
        if (!active || disposed) return false
        val top = popup ?: main
        return when {
            top != null && history[top] == true -> { top.goBack(); true }
            popup != null -> { closePopup(); true }
            else -> false
        }
    }

    private fun notifyNavigationState() = onNavigationState(popup != null || history[popup ?: main] == true)

    private fun closePopup() {
        val child = popup ?: return
        invalidateAutofill()
        disconnect(child); media.remove(child)?.pause()
        popupView?.let { it.releaseSession(); viewport.removeView(it) }; popupView = null; popup = null
        locations.remove(child); history.remove(child); documentSerial.remove(child); backgroundAuthentication.remove(child); autofillLoading.remove(child); requestedNavigation.remove(child)
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
        progress.visibility = if ((popup ?: main) in autofillLoading) View.VISIBLE else View.GONE
        media.keys.toList().forEach(::applyMediaPolicy)
        updateControls()
        notifyNavigationState()
    }
    private fun applyMediaPolicy(session: GeckoSession) {
        val source = media[session] ?: return
        runCatching {
            val audibleAuth = active && session === (popup ?: main) && OriginalHomePolicy.isNormalWebAuthUrl(locations[session].orEmpty())
            source.muteAudio(!audibleAuth)
            if (!audibleAuth) source.pause()
        }
    }
    private fun setStatus(message: String, issue: Boolean = true) {
        status.text = message
        status.visibility = if (!embedded || issue) View.VISIBLE else View.GONE
        if (::retryButton.isInitialized) retryButton.visibility = if (embedded && issue) View.VISIBLE else View.GONE
    }

    private fun updateControls() {
        if (!::routingLabel.isInitialized) return
        routingLabel.text = if (handoff && connections[popup ?: main]?.acknowledged == true)
            "Videotipps öffnen automatisch im Weiktube-Player." else "Videoübergabe wird vorbereitet …"
        if (::autofillButton.isInitialized) {
            val target = autofillTarget()
            autofillActions.visibility = if (active && target?.authPage == true) View.VISIBLE else View.GONE
            autofillButton.isEnabled = active && target?.documentReady == true && target.view.isShown
        }
    }
    private fun stopSessions() {
        invalidateAutofill()
        generation++; preparing = false; prepareAfterExtensionOperation = false
        installing = false; handoff = false; initialNavigationPending = false; resetProof()
        connections.keys.toList().forEach(::disconnect)
        media.values.toList().forEach { runCatching { it.pause() } }; media.clear()
        mainView?.let { it.releaseSession(); viewport.removeView(it) }; mainView = null
        popupView?.let { it.releaseSession(); viewport.removeView(it) }; popupView = null
        val sessions = listOfNotNull(main, popup); main = null; popup = null
        sessions.forEach { runCatching { it.setActive(false); it.close() } }
        locations.clear(); history.clear(); documentSerial.clear(); backgroundAuthentication.clear(); autofillLoading.clear(); requestedNavigation.clear(); extension = null
        lease?.let { if (extensionOperation) deferredRelease += it else it.release() }; lease = null
        notifyNavigationState()
    }
    private fun suspend() {
        backgroundAuthentication.clear()
        listOfNotNull(main, popup).filterTo(backgroundAuthentication) { OriginalHomePolicy.isNormalWebAuthUrl(locations[it].orEmpty()) }
        active = false
        autofill.stop()
        invalidateAutofill()
        resetProof()
        connections.values.forEach { state ->
            state.activation.suspend()
            runCatching { state.port.postMessage(JSONObject().put("type", "disable").put("documentId", state.documentId).put("generation", state.token)) }
        }
        media.values.toList().forEach { runCatching { it.pause(); it.muteAudio(true) } }
        main?.let { it.setActive(false); it.setFocused(false) }
        popup?.let { it.setActive(false); it.setFocused(false) }
    }

    /** Explicit data clearing is different from a tab or player transition. The view remains reusable. */
    fun clearSession() {
        if (disposed) return
        suspend()
        stopSessions()
        discardOwnedReturn()
        openingPlayer = false; pendingNavigation = null
        root.visibility = View.INVISIBLE
        setStatus("Die YouTube-Startseite wird beim nächsten Öffnen neu geladen.", false)
        notifyNavigationState()
    }

    private fun discardOwnedReturn() {
        val token = parkedToken ?: return
        val current = OriginalHomeReturn.resumeToken()
        if (current == null || current == token) OriginalHomeReturn.discard()
        parkedToken = null
    }

    /** Final host disposal only. A legacy Activity transfers its parked result to MainActivity. */
    fun close() {
        if (disposed) return
        suspend()
        disposed = true
        stopSessions()
        if (embedded) discardOwnedReturn()
        onNavigationState(false)
    }

    private fun createGeckoView(session: GeckoSession, viewId: Int) = GeckoView(activity).apply {
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
    private fun current(attempt: Int) = !disposed && generation == attempt
    private fun decision(allow: Boolean) = GeckoResult.fromValue(if (allow) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
    private fun label(value: String, size: Float) = TextView(activity).apply { text = value; textSize = size; setTextColor(Color.WHITE); importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO; setPadding(dp(10), dp(3), dp(10), dp(3)) }
    private fun button(value: String, action: () -> Unit) = Button(activity).apply { text = value; isAllCaps = false; textSize = 12f; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO; setOnClickListener { action() } }
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    companion object {
        private const val HOME = "https://m.youtube.com/"
        private val ROUTING_CAUSES = setOf("INVALID_VIDEO", "ROUTING_NOT_READY", "BRIDGE_UNAVAILABLE", "BRIDGE_RESUME_TIMEOUT", "HISTORY_GUARD_UNAVAILABLE", "MEDIA_GUARD_UNAVAILABLE", "UNTRUSTED_NAVIGATION")
        private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}
