package com.weiktube.home

import android.app.AlertDialog
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.CheckBox
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.MediaSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebRequestError
import java.net.URI
import java.util.UUID

/** Explicit, separate login/data check. It never replaces the native homepage or opens a player. */
class HomeConnectionActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var root: LinearLayout
    private lateinit var container: FrameLayout
    private lateinit var panel: LinearLayout
    private lateinit var report: ScrollView
    private lateinit var domain: TextView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var verify: Button
    private lateinit var next: Button
    private lateinit var preview: TextView
    private lateinit var openFirst: Button
    private lateinit var diagnosticView: TextView
    private lateinit var diagnosticCopy: Button
    private val diagnostics = HomeConnectionDiagnostics()
    private var diagnosticRequestId: String? = null
    private var navigationSerial = 0
    private var runtime: GeckoRuntime? = null
    private var extension: WebExtension? = null
    private var main: GeckoSession? = null
    private var child: GeckoSession? = null
    private var mainView: GeckoView? = null
    private var childView: GeckoView? = null
    private val urls = mutableMapOf<GeckoSession, String>()
    private val back = mutableMapOf<GeckoSession, Boolean>()
    private val ports = mutableMapOf<GeckoSession, WebExtension.Port>()
    private val media = mutableMapOf<GeckoSession, MediaSession>()
    private var active = false
    private var generation = 0
    private var pendingId: String? = null
    private var pendingNext = false
    private var expected = ""
    private var firstCount = 0
    private var videoIds = emptySet<String>()
    private var firstVideoId: String? = null
    private var continuation: String? = null
    private var previousProof = false
    private var stoppedForClear = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        expected = intent.getStringExtra(EXTRA_EXPECTED_CHANNEL_ID).orEmpty()
        if (!CHANNEL.matches(expected)) { finish(); return }
        previousProof = HomeEngine.previouslyChecked(this, expected)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        createUi()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val popup = child
                when {
                    popup != null && back[popup] == true -> popup.goBack()
                    popup != null -> closeChild()
                    else -> finish()
                }
            }
        })
    }

    private fun createUi() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(18, 18, 18)) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bar = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bar.left, bar.top, bar.right, bar.bottom); insets
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("YouTube-Startseite verbinden", 18f), LinearLayout.LayoutParams(0, dp(54), 1f))
        header.addView(button("Schließen") { finish() })
        root.addView(header)
        domain = text("Verbindung wird vorbereitet …", 12f)
        root.addView(domain)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(2)))
        panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        report = ScrollView(this).apply { visibility = View.GONE; addView(panel) }
        root.addView(report, LinearLayout.LayoutParams(-1, 0, 1f))
        val name = intent.getStringExtra(EXTRA_EXPECTED_CHANNEL_TITLE)?.take(100).orEmpty().ifBlank { expected }
        panel.addView(text("Erwartetes YouTube-Profil: $name", 14f))
        panel.addView(text("Hier wird die separate Gecko-Websitzung geprüft. Deine Google-API-Anmeldung und eine Anmeldung im externen Browser sind davon getrennt. Browser-Cookies werden nicht übernommen.", 13f))
        status = text(if (previousProof) "Vorheriger Datenabruf vorhanden. Bitte Sitzung erneut prüfen." else "Die echte mobile YouTube-Startseite wird geladen. Die bestehende Google-API-Anmeldung meldet diese Websitzung nicht automatisch an.", 14f)
        panel.addView(status)
        val actions = LinearLayout(this)
        actions.addView(button("YouTube neu laden") { openLogin() }, LinearLayout.LayoutParams(0, -2, 1f))
        verify = button("Verbindung prüfen") { requestPage(false) }.apply { isEnabled = false }
        actions.addView(verify, LinearLayout.LayoutParams(0, -2, 1f))
        panel.addView(actions)
        panel.addView(button("YouTube im Browser prüfen") { openExternalBrowser() })
        panel.addView(button("YouTube-Profil wechseln") {
            if (!active) return@button
            clearResults(); closeChild(false)
            main?.loadUri("https://www.youtube.com/channel_switcher?next=%2F")
        })
        next = button("Weitere Ergebnisse prüfen") { requestPage(true) }.apply { isEnabled = false }
        panel.addView(next)
        preview = text("", 13f)
        panel.addView(preview)
        openFirst = button("Erstes Video im Originalplayer prüfen") {
            val id = firstVideoId ?: return@button
            if (!active || !VIDEO.matches(id) || pendingId != null || child != null) return@button
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id"))
                    .setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                finish()
            } catch (_: Exception) { fail("Der Originalplayer konnte nicht geöffnet werden. [PLAYER_UNAVAILABLE]") }
        }.apply { isEnabled = false }
        panel.addView(openFirst)
        panel.addView(CheckBox(this).apply {
            text = "Diagnosemodus (ohne Kontodaten)"; setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, checked ->
                diagnostics.enabled = checked
                if (checked) requestDiagnostics() else diagnosticRequestId = null
                updateDiagnostics()
            }
        })
        diagnosticView = text("", 12f).apply { visibility = View.GONE; setTextIsSelectable(true) }
        panel.addView(diagnosticView)
        diagnosticCopy = button("Bereinigte Diagnose kopieren") {
            if (diagnostics.enabled) {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Weiktube-Diagnose", diagnostics.render()))
                status.text = "Bereinigte Diagnose kopiert. Keine Cookies, Anmelde-URLs oder Kontodaten enthalten."
            }
        }.apply { visibility = View.GONE }
        panel.addView(diagnosticCopy)
        container = FrameLayout(this)
        root.addView(container, LinearLayout.LayoutParams(-1, 0, 1f))
        panel.addView(button("Webverbindung trennen") {
            AlertDialog.Builder(this).setTitle("Webverbindung trennen?")
                .setMessage("Nur die zusätzliche YouTube-Websitzung und ihre Prüfergebnisse werden gelöscht. Deine bisherige Google-API-Anmeldung bleibt erhalten.")
                .setNegativeButton("Abbrechen", null).setPositiveButton("Trennen") { _, _ ->
                    stoppedForClear = true; stopSessions(); HomeEngine.clearIfInitialized(this)
                    report.visibility = View.VISIBLE; container.visibility = View.GONE
                    status.text = "Webverbindung getrennt. Die Original-App bleibt unverändert."
                    domain.text = "Keine aktive Websitzung"
                }.show()
        })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        active = true
        if (::root.isInitialized && main == null && !stoppedForClear) prepare()
        main?.setActive(true); child?.setActive(true)
        updateButtons()
    }

    private fun prepare() {
        val expectedGeneration = ++generation
        HomeEngine.prepare(this, { engine ->
            handler.post runtimeReady@ {
                if (!active || isDestroyed || generation != expectedGeneration) return@runtimeReady
                runtime = engine
                engine.webExtensionController.ensureBuiltIn("resource://android/assets/home-bridge/", EXTENSION_ID).accept({ ext ->
                    handler.post extensionReady@ {
                        if (!active || isDestroyed || generation != expectedGeneration) return@extensionReady
                        if (ext == null) { fail("Die interne Datenverbindung ist nicht verfügbar."); return@extensionReady }
                        extension = ext
                        val session = createSession()
                        main = session
                        bind(session, ext)
                        session.open(engine)
                        mainView = GeckoView(this).also { it.setSession(session); container.addView(it, FrameLayout.LayoutParams(-1, -1)) }
                        session.setActive(true)
                        session.loadUri(HOME)
                    }
                }, { handler.post { if (active && generation == expectedGeneration) fail("Die interne Datenverbindung konnte nicht gestartet werden.") } })
            }
        }, { handler.post { if (active && generation == expectedGeneration) fail("Die Websitzung konnte nicht sicher vorbereitet werden. Bitte erneut versuchen.") } })
    }

    private fun openLogin() {
        if (!active) return
        stoppedForClear = false
        clearResults()
        closeChild(false)
        val session = main
        if (session == null) prepare() else session.loadUri(HOME)
    }

    private fun openExternalBrowser() {
        if (!active) return
        AlertDialog.Builder(this).setTitle("Separater Browser")
            .setMessage("YouTube wird in Chrome oder deinem Systembrowser geöffnet. Eine dortige Anmeldung verbindet nicht die Gecko-Websitzung in Weiktube. Es werden keine Cookies importiert und keine erfolgreiche Feed-Verbindung vorgetäuscht.")
            .setNegativeButton("Abbrechen", null).setPositiveButton("Browser öffnen") { _, _ ->
                val target = Intent(Intent.ACTION_VIEW, Uri.parse(HOME)).addCategory(Intent.CATEGORY_BROWSABLE)
                try {
                    val browserProbe = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com/")).addCategory(Intent.CATEGORY_BROWSABLE)
                    val browsers = packageManager.queryIntentActivities(browserProbe, 0).filter { it.activityInfo.packageName != packageName }
                    val chrome = browsers.firstOrNull { it.activityInfo.packageName == "com.android.chrome" }
                    val intents = browsers.map { Intent(target).setClassName(it.activityInfo.packageName, it.activityInfo.name) }
                    if (chrome != null) startActivity(Intent(target).setClassName(chrome.activityInfo.packageName, chrome.activityInfo.name))
                    else if (intents.isNotEmpty()) startActivity(Intent.createChooser(intents.first(), "Systembrowser wählen").putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.drop(1).toTypedArray()))
                    else { fail("Kein Systembrowser verfügbar. Die interne Websitzung bleibt unverändert."); return@setPositiveButton }
                    diagnostics.failure("EXTERNAL_BROWSER_OPENED"); updateDiagnostics()
                } catch (_: Exception) { fail("Der externe Browser konnte nicht geöffnet werden. Die interne Websitzung bleibt unverändert.") }
            }.show()
    }

    private fun createSession(): GeckoSession = GeckoSession().apply {
        navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> {
                if (!active || !owns(session)) return GeckoResult.fromValue(AllowOrDeny.DENY)
                if (request.uri == "about:blank") return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                if (HomeConnectionDiagnostics.isExternalSignIn(request.uri)) {
                    diagnostics.location(request.uri, "blocked"); diagnostics.failure("SIGNIN_EXTERNAL_REQUIRED")
                    handler.post { if (active && owns(session)) fail(failureText("SIGNIN_EXTERNAL_REQUIRED")) }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                val uri = parseHttps(request.uri)
                if (uri == null || !HomeConnectionDiagnostics.allowedEmbedded(request.uri) || (youtube(uri) && (uri.path == "/watch" || uri.path.startsWith("/shorts/") || uri.path.startsWith("/live/"))))
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }
            override fun onSubframeLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> =
                GeckoResult.fromValue(if (active && owns(session) && (request.uri == "about:blank" || HomeConnectionDiagnostics.allowedEmbedded(request.uri))) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
            override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
                if (!active || !owns(session) || url == null) return
                urls[session] = url
                if (session === main) { diagnostics.location(url, "location"); updateDiagnostics() }
                if (HomeConnectionDiagnostics.isExternalSignIn(url)) {
                    session.stop()
                    diagnostics.failure("SIGNIN_EXTERNAL_REQUIRED")
                    fail(failureText("SIGNIN_EXTERNAL_REQUIRED"))
                }
                if (session === main) clearResults()
                displayOrigin()
            }
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { back[session] = canGoBack }
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                if (!active || !owns(session)) return null
                if (HomeConnectionDiagnostics.isExternalSignIn(uri)) {
                    diagnostics.location(uri, "blocked"); diagnostics.failure("SIGNIN_EXTERNAL_REQUIRED")
                    handler.post { if (active && owns(session)) fail(failureText("SIGNIN_EXTERNAL_REQUIRED")) }
                    return null
                }
                if (child != null || (uri != "about:blank" && !HomeConnectionDiagnostics.allowedEmbedded(uri))) return null
                val popup = createSession()
                child = popup
                extension?.let { bind(popup, it) }
                handler.post {
                    if (!active || child !== popup || isDestroyed) return@post
                    childView = GeckoView(this@HomeConnectionActivity).also {
                        it.setSession(popup); container.addView(it, FrameLayout.LayoutParams(-1, -1))
                    }
                    popup.setActive(true)
                    displayOrigin()
                }
                return GeckoResult.fromValue(popup)
            }
            override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? {
                if (owns(session)) {
                    diagnostics.error(error.code, error.category)
                    fail("Gecko konnte die Seite nicht laden. [GECKO_ERROR ${error.category}/${error.code}]")
                }
                return null
            }
        }
        progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                if (!active || !owns(session)) return
                urls[session] = url
                if (session === main) {
                    ports.remove(session)?.let { old -> runCatching { old.postMessage(JSONObject().put("type", "pause")); old.disconnect() } }
                    navigationSerial++; diagnosticRequestId = null; clearResults()
                    diagnostics.location(url, "start"); diagnostics.bridge(false); updateDiagnostics()
                }
            }
            override fun onProgressChange(session: GeckoSession, value: Int) {
                if (!owns(session)) return
                progress.progress = value
                progress.visibility = if (value >= 100) View.GONE else View.VISIBLE
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                if (session === main && active) {
                    val serial = navigationSerial
                    handler.postDelayed({
                        if (active && main === session && serial == navigationSerial && ports[session] == null) {
                            val code = diagnostics.missingBridgeCode(); diagnostics.failure(code); fail(failureText(code))
                        }
                    }, 8_000L)
                    if (diagnostics.current.kind == "PAGE_ERROR") { diagnostics.failure("PAGE_ERROR"); fail(failureText("PAGE_ERROR")) }
                    else if (diagnostics.enabled) requestDiagnostics()
                }
                if (success && session === child && homeDocument(urls[session].orEmpty())) handler.post { if (session === child) closeChild() }
            }
        }
        contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                if (session === main) { diagnostics.title(title); updateDiagnostics() }
            }
            override fun onCloseRequest(session: GeckoSession) { if (session === child) closeChild() }
            override fun onCrash(session: GeckoSession) { if (owns(session)) { stopSessions(); fail("Die Anmelde-Engine wurde beendet. Bitte Anmeldung erneut öffnen.") } }
        }
        permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onContentPermissionRequest(session: GeckoSession, perm: GeckoSession.PermissionDelegate.ContentPermission): GeckoResult<Int> =
                GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
        }
        mediaSessionDelegate = object : MediaSession.Delegate {
            override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
                media[session] = mediaSession
                // Visible Google audio challenges may be user-initiated; Home previews never play.
                if (!active || homeOrigin(urls[session].orEmpty())) mediaSession.pause()
            }
        }
    }

    private fun bind(session: GeckoSession, ext: WebExtension) {
        session.webExtensionController.setMessageDelegate(ext, object : WebExtension.MessageDelegate {
            override fun onConnect(port: WebExtension.Port) {
                if (!active || !owns(session) || port.sender.session !== session || !port.sender.isTopLevel || !homeOrigin(port.sender.url)) { port.disconnect(); return }
                ports.remove(session)?.disconnect()
                ports[session] = port
                val portDocumentSerial = navigationSerial
                if (session === main) { diagnostics.bridge(true); updateDiagnostics() }
                port.setDelegate(object : WebExtension.PortDelegate {
                    override fun onPortMessage(message: Any, source: WebExtension.Port) {
                        if (!active || session !== main || portDocumentSerial != navigationSerial || ports[session] !== source || source.sender.session !== session || !source.sender.isTopLevel || !homeOrigin(source.sender.url)) return
                        val obj = message as? JSONObject ?: return
                        if (obj.toString().length > MAX_RESPONSE) { fail("Die Feed-Antwort ist zu groß. Keine Daten übernommen."); return }
                        if (obj.optString("type") == "ready") {
                            if (diagnostics.enabled) diagnostics.details(obj.optJSONObject("diagnostics"))
                            updateDiagnostics(); return
                        }
                        if (obj.optString("type") == "diagnostics") {
                            if (diagnostics.enabled && diagnosticRequestId != null && obj.optString("requestId") == diagnosticRequestId) {
                                diagnosticRequestId = null; diagnostics.details(obj.optJSONObject("diagnostics")); updateDiagnostics()
                            }
                            return
                        }
                        val id = pendingId ?: return
                        if (obj.optString("requestId") != id) return
                        if (diagnostics.enabled) diagnostics.details(obj.optJSONObject("diagnostics"))
                        if (obj.optString("type") == "failure") { diagnostics.failure(obj.optString("code")); fail(failureText(obj.optString("code"))); return }
                        if (obj.optString("type") != "home") return
                        if (obj.optString("channelId") != expected) { clearResults(); fail("Das angemeldete YouTube-Profil stimmt nicht mit deinem Weiktube-Konto überein. Bitte das passende Profil wählen."); return }
                        val response = obj.optJSONObject("response") ?: run { fail("YouTube hat keine auswertbaren Feed-Daten geliefert."); return }
                        val page = runCatching { HomeFeedData.parse(response) }.getOrNull() ?: run { fail("Das Feed-Format wird noch nicht unterstützt. Keine Ersatzempfehlungen geladen."); return }
                        val wasNext = pendingNext
                        cancelRequest()
                        if (!wasNext) {
                            diagnostics.failure(if (page.videos.isEmpty()) "NO_FEED" else "FEED_RECEIVED")
                            firstCount = page.videos.size; videoIds = page.videos.map { it.id }.toSet(); continuation = page.continuation
                            firstVideoId = page.videos.firstOrNull()?.id
                            preview.text = page.videos.take(3).mapIndexed { index, video -> "${index + 1}. ${video.title.take(160)}" }.joinToString("\n")
                            status.text = if (firstCount == 0) "Profil stimmt überein. Keine unterstützten Home-Videos geliefert; der Feed ist noch nicht nachgewiesen."
                                else "Profil stimmt überein: $firstCount Home-Videos empfangen.${if (previousProof) " Sitzung nach erneutem Öffnen erkannt." else ""} Bitte weitere Ergebnisse prüfen. Die Original-Startseite bleibt aktiv."
                        } else {
                            val freshCount = page.videos.count { it.id !in videoIds }
                            videoIds = videoIds + page.videos.map { it.id }; continuation = page.continuation
                            if (firstCount > 0 && freshCount > 0) {
                                HomeEngine.recordProof(this@HomeConnectionActivity, expected, firstCount, freshCount)
                                status.text = "Profil und Datenabruf geprüft: $firstCount Videos und $freshCount weitere. Dies aktiviert noch keine neue Startseite. Bitte Weiktube schließen und die Sitzung erneut prüfen."
                            } else status.text = "Weitere Seite empfangen, aber keine zusätzlichen unterstützten Videos. Nachladen noch nicht nachgewiesen."
                        }
                        updateButtons(); updateDiagnostics()
                    }
                    override fun onDisconnect(port: WebExtension.Port) {
                        if (ports[session] === port) { ports.remove(session); if (session === main) { diagnostics.bridge(false); diagnosticRequestId = null; clearResults() }; updateButtons(); updateDiagnostics() }
                    }
                })
                port.postMessage(JSONObject().put("type", "pause"))
                if (diagnostics.enabled && session === main) requestDiagnostics()
                updateButtons()
            }
        }, "weiktubeHome")
    }

    private fun requestPage(more: Boolean) {
        val session = main ?: run { fail("Die Gecko-Seite wurde noch nicht vorbereitet. Bitte YouTube neu laden."); return }
        val port = ports[session] ?: run { val code = diagnostics.missingBridgeCode(); diagnostics.failure(code); fail(failureText(code)); return }
        if (!active || child != null || pendingId != null || (more && continuation == null)) return
        val id = UUID.randomUUID().toString()
        diagnostics.failure("none"); updateDiagnostics()
        pendingId = id; pendingNext = more
        if (!more) { firstCount = 0; videoIds = emptySet(); firstVideoId = null; preview.text = ""; continuation = null }
        status.text = "Angemeldetes Profil und echte Home-Daten werden geprüft …"
        val command = JSONObject().put("type", if (more) "next" else "probe").put("requestId", id).put("expectedChannelId", expected)
        if (more) command.put("continuation", continuation)
        if (runCatching { port.postMessage(command) }.isFailure) {
            fail("Die Website-Verbindung wurde unterbrochen. Bitte erneut prüfen. [BRIDGE_DISCONNECTED]")
            return
        }
        updateButtons()
        handler.postDelayed({ if (pendingId == id && active) { diagnostics.failure("TIMEOUT"); fail(failureText("TIMEOUT")) } }, 30_000L)
    }

    private fun requestDiagnostics() {
        if (!active || !diagnostics.enabled || child != null) return
        val port = main?.let { ports[it] } ?: return
        val id = UUID.randomUUID().toString(); diagnosticRequestId = id
        if (runCatching { port.postMessage(JSONObject().put("type", "diagnose").put("requestId", id)) }.isFailure) {
            diagnosticRequestId = null; diagnostics.failure("BRIDGE_DISCONNECTED"); updateDiagnostics()
        }
        handler.postDelayed({ if (active && diagnosticRequestId == id) { diagnosticRequestId = null; diagnostics.failure("TIMEOUT"); updateDiagnostics() } }, 8_000L)
    }

    private fun updateDiagnostics() {
        if (!::diagnosticView.isInitialized) return
        diagnosticView.visibility = if (diagnostics.enabled) View.VISIBLE else View.GONE
        diagnosticCopy.visibility = if (diagnostics.enabled) View.VISIBLE else View.GONE
        if (diagnostics.enabled) diagnosticView.text = diagnostics.render()
    }

    private fun closeChild(reload: Boolean = true) {
        val session = child ?: return
        child = null
        childView?.let { it.releaseSession(); container.removeView(it) }; childView = null
        ports.remove(session)?.disconnect(); media.remove(session)?.pause(); urls.remove(session); back.remove(session)
        runCatching { session.close() }
        if (reload && active) main?.loadUri(HOME)
        displayOrigin(); updateButtons()
    }

    private fun displayOrigin() {
        val session = child ?: main
        val raw = session?.let { urls[it] }.orEmpty()
        val uri = parseHttps(raw)
        domain.text = uri?.host?.let { "Gecko · HTTPS · $it" } ?: "Gecko-Seite wird geladen …"
        // Only login/profile-selection is visible. No YouTube Home, channel or watch browser UI.
        mainView?.visibility = if (child == null && loginDocument(urls[main].orEmpty())) View.VISIBLE else View.INVISIBLE
        childView?.visibility = if (loginDocument(urls[child].orEmpty())) View.VISIBLE else View.INVISIBLE
        val showLogin = loginDocument(raw)
        report.visibility = if (showLogin) View.GONE else View.VISIBLE
        container.visibility = if (showLogin) View.VISIBLE else View.GONE
        if (!showLogin) WindowInsetsControllerCompat(window, root).hide(WindowInsetsCompat.Type.ime())
        updateButtons()
    }
    private fun updateButtons() {
        if (!::verify.isInitialized) return
        val available = active && child == null && ports[main] != null && pendingId == null
        verify.isEnabled = active && child == null && main != null && pendingId == null
        next.isEnabled = available && continuation != null && firstCount > 0
        if (::openFirst.isInitialized) openFirst.isEnabled = available && firstVideoId != null
    }
    private fun cancelRequest() { pendingId = null; pendingNext = false; updateButtons() }
    private fun clearResults() {
        cancelRequest(); firstCount = 0; videoIds = emptySet(); firstVideoId = null; continuation = null
        if (::preview.isInitialized) preview.text = ""
        updateButtons()
    }
    private fun fail(text: String) {
        clearResults()
        if (::status.isInitialized) status.text = text
        if (::report.isInitialized) report.visibility = View.VISIBLE
        updateDiagnostics()
    }
    private fun stopSessions() {
        generation++; navigationSerial++; diagnosticRequestId = null; clearResults()
        ports.values.toList().forEach { runCatching { it.postMessage(JSONObject().put("type", "pause")); it.disconnect() } }; ports.clear()
        media.values.toList().forEach { runCatching { it.pause() } }; media.clear()
        mainView?.let { it.releaseSession(); container.removeView(it) }; mainView = null
        childView?.let { it.releaseSession(); container.removeView(it) }; childView = null
        val sessions = listOfNotNull(main, child); main = null; child = null
        sessions.forEach { runCatching { it.setActive(false); it.close() } }
        urls.clear(); back.clear(); diagnostics.bridge(false); updateButtons(); updateDiagnostics()
    }
    override fun onPause() {
        active = false
        diagnosticRequestId = null
        if (::root.isInitialized) {
            clearResults()
            ports.values.toList().forEach { runCatching { it.postMessage(JSONObject().put("type", "pause")) } }
            media.values.toList().forEach { runCatching { it.pause() } }
            main?.setActive(false); child?.setActive(false)
        }
        super.onPause()
    }
    override fun onStop() { active = false; if (::root.isInitialized) stopSessions(); super.onStop() }
    override fun onDestroy() {
        active = false
        if (::root.isInitialized) stopSessions()
        handler.removeCallbacksAndMessages(null); super.onDestroy()
    }
    private fun owns(session: GeckoSession) = session === main || session === child
    private fun text(value: String, size: Float) = TextView(this).apply {
        text = value; textSize = size; setTextColor(Color.WHITE); setPadding(dp(12), dp(6), dp(12), dp(6))
    }
    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; textSize = 13f; setOnClickListener { action() }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_EXPECTED_CHANNEL_ID = "com.weiktube.home.EXPECTED_CHANNEL_ID"
        const val EXTRA_EXPECTED_CHANNEL_TITLE = "com.weiktube.home.EXPECTED_CHANNEL_TITLE"
        private const val EXTENSION_ID = "home-data@weiktube.local"
        private const val HOME = "https://m.youtube.com/"
        private const val MAX_RESPONSE = 4 * 1024 * 1024
        private val CHANNEL = Regex("UC[A-Za-z0-9_-]{22}")
        private val VIDEO = Regex("[A-Za-z0-9_-]{11}")
        private fun parseHttps(value: String): URI? = runCatching { URI(value).takeIf { it.scheme == "https" && it.host != null && it.rawUserInfo == null && it.port == -1 && value.length <= 8192 } }.getOrNull()
        private fun youtube(uri: URI) = uri.host in setOf("www.youtube.com", "m.youtube.com", "youtube.com")
        private fun homeOrigin(value: String) = parseHttps(value)?.let(::youtube) == true
        private fun homeDocument(value: String): Boolean = parseHttps(value)?.let { youtube(it) && (it.path.isNullOrEmpty() || it.path == "/" || it.path == "/feed/what_to_watch") } == true
        private fun loginDocument(value: String): Boolean = parseHttps(value)?.let {
            // Authentication is never embedded. Consent/profile selection may be shown; Home is data-only.
            val kind = HomeConnectionDiagnostics.locationOf(value).kind
            kind in setOf("CONSENT", "PROFILE_SELECTION")
        } ?: false
        private fun failureText(code: String): String = when (code) {
            "SIGNED_OUT" -> "Diese Gecko-Websitzung ist nicht angemeldet. Die Google-API-Freigabe und eine Chrome-Anmeldung sind getrennt; es gibt derzeit keine bestätigte Sitzungsübernahme. [SIGNED_OUT]"
            "PAGE_ERROR" -> "YouTube hat eine Fehlerseite statt der Startseite geliefert. Das ist keine fehlende Anmeldungskonfiguration. Bitte YouTube neu laden. [PAGE_ERROR]"
            "SIGNIN_REQUIRED", "SIGNIN_EXTERNAL_REQUIRED" -> "Google-Anmeldung wird nicht in Gecko eingebettet. Über „YouTube im Browser prüfen“ kannst du den Systembrowser öffnen; dessen Anmeldung verbindet diese Websitzung nicht. [SIGNIN_EXTERNAL_REQUIRED]"
            "CONSENT_REQUIRED" -> "YouTube zeigt zuerst eine Zustimmungsseite. Bitte die sichtbare Auswahl treffen und anschließend erneut prüfen. [CONSENT_REQUIRED]"
            "BRIDGE_UNAVAILABLE" -> "Die YouTube-Seite wurde erreicht, aber die JavaScript-Verbindung fehlt. Diagnosemodus aktivieren und YouTube neu laden. [BRIDGE_UNAVAILABLE]"
            "WRONG_PAGE" -> "Die aktuelle Seite ist keine auswertbare YouTube-Startseite. Bitte YouTube neu laden. [WRONG_PAGE]"
            "GECKO_ERROR" -> "Gecko meldet einen Ladefehler. Im Diagnosemodus stehen Fehlercode und Kategorie; ein HTTP-Status ist dadurch nicht bekannt. [GECKO_ERROR]"
            "ACCOUNT_MISMATCH" -> "Das YouTube-Profil stimmt nicht mit deinem Weiktube-Konto überein. Bitte das passende Profil wählen. [ACCOUNT_MISMATCH]"
            "PROFILE_UNVERIFIED" -> "Das aktive YouTube-Profil konnte nicht sicher bestätigt werden. Keine Feed-Daten übernommen. [PROFILE_UNVERIFIED]"
            "CONFIG_UNAVAILABLE" -> "YouTube hat keine unterstützte Seitenkonfiguration geliefert. [CONFIG_UNAVAILABLE]"
            "SESSION_UNAVAILABLE" -> "Die Website-Anmeldung ist für den Datenabruf nicht verfügbar. Keine Ersatzanmeldung verwendet. [SESSION_UNAVAILABLE]"
            "CONTINUATION_INVALID" -> "Die nächste Ergebnisseite konnte nicht bestätigt werden. Bitte Verbindung neu prüfen. [CONTINUATION_INVALID]"
            "ACCESS_DENIED" -> "YouTube hat den Datenzugriff abgelehnt. Die Verbindung bleibt ungeprüft. [ACCESS_DENIED]"
            "NETWORK_ERROR" -> "Der Datenabruf ist fehlgeschlagen. Bitte Verbindung prüfen. [NETWORK_ERROR]"
            "RESPONSE_TOO_LARGE" -> "Die Feed-Antwort war zu groß. Keine Daten übernommen. [RESPONSE_TOO_LARGE]"
            "RESPONSE_INVALID" -> "YouTube hat kein unterstütztes Feed-Format geliefert. [RESPONSE_INVALID]"
            "TIMEOUT" -> "Die Feed-Prüfung hat zu lange gedauert. Bitte erneut versuchen. [TIMEOUT]"
            "TRANSPORT_UNAVAILABLE" -> "Der angemeldete Website-Kontext konnte nicht für den Abruf verwendet werden. [TRANSPORT_UNAVAILABLE]"
            else -> "Die persönlichen Home-Daten konnten nicht bestätigt werden. Die Original-App bleibt unverändert. [UNKNOWN_FAILURE]"
        }
    }
}
