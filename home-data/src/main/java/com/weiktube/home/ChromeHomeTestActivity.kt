package com.weiktube.home

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomContentAction
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import androidx.browser.customtabs.ExperimentalCustomContentAction
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Isolated, user-observed Chrome test. No website identity or feed is read by the app. */
class ChromeHomeTestActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var openFeed: Button
    private lateinit var confirmFeed: CheckBox
    private lateinit var testHandoff: Button
    private var session: CustomTabsSession? = null
    private var bound = false
    private var bindingGeneration = 0
    private var awaitingFeedReturn = false
    private var feedReturned = false

    private val connection = object : CustomTabsServiceConnection() {
        override fun onCustomTabsServiceConnected(name: ComponentName, client: CustomTabsClient) {
            if (isDestroyed || name.packageName != CHROME) return
            session = runCatching {
                client.warmup(0L)
                client.newSession(object : CustomTabsCallback() {
                    // Navigation callbacks contain no usable video URL and never trigger a handoff.
                })
            }.getOrNull()
            status.text = if (session != null) "Chrome ist bereit. Öffne zuerst die Original-Startseite."
                else "Chrome konnte keine Custom-Tab-Sitzung bereitstellen. Der Test ist nicht gestartet."
            updateButtons()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            session = null
            ChromeHomeTestState.setHandoffEnabled(this@ChromeHomeTestActivity, false)
            if (!isDestroyed) {
                status.text = "Die Chrome-Verbindung wurde beendet. Bitte diesen Test schließen und erneut öffnen."
                updateButtons()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        awaitingFeedReturn = savedInstanceState?.getBoolean("awaitingFeedReturn") == true
        feedReturned = savedInstanceState?.getBoolean("feedReturned") == true
        if (savedInstanceState == null) ChromeHomeTestState.setHandoffEnabled(this, false)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(18, 18, 18))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        fun line(value: String, size: Float = 15f) = TextView(this).apply {
            text = value; textSize = size; setTextColor(Color.WHITE)
            setPadding(dp(16), dp(9), dp(16), dp(9)); content.addView(this)
        }
        fun action(value: String, block: () -> Unit) = Button(this).apply {
            text = value; isAllCaps = false; setOnClickListener { block() }; content.addView(this)
        }
        line("Original-Startseite · Chrome-Test", 20f)
        line("1. Startseite ansehen", 18f)
        line("YouTube öffnet sich mit der normalen Chrome-Sitzung. Prüfe dort dein aktives YouTube-Profil und deine Empfehlungen. Das in Weiktube verbundene Google-Konto bestimmt das Chrome-Profil nicht.")
        status = line("Chrome wird geprüft …")
        openFeed = action("1. Original-Startseite in Chrome öffnen") { launchChrome(false) }
        line("Kehre anschließend mit Zurück hierher zurück. Die folgende Bestätigung ist nur deine Sichtprüfung; Weiktube liest keine Kontodaten oder Cookies aus Chrome.", 13f)
        confirmFeed = CheckBox(this).apply {
            text = "Ich habe mein richtiges YouTube-Profil und meine originale Startseite in Chrome gesehen."
            setTextColor(Color.WHITE)
            isChecked = feedReturned && savedInstanceState?.getBoolean("confirmedThisVisit") == true
            setOnCheckedChangeListener { _, checked ->
                ChromeHomeTestState.recordManualFeedSeen(this@ChromeHomeTestActivity, checked && feedReturned)
                if (!checked) {
                    ChromeHomeTestState.setHandoffEnabled(this@ChromeHomeTestActivity, false)
                    cancelHandoffActions()
                }
                updateButtons()
            }
            content.addView(this)
        }
        line("2. Videoübergabe getrennt testen", 18f)
        line("Erst dieser Schritt fügt die Aktion „Im Weiktube-Player öffnen“ hinzu. Ein normaler Tipp auf ein Video darf weiterhin die YouTube-Webseite öffnen; eine automatische Übergabe ist nicht zugesichert.")
        testHandoff = action("2. Videoübergabe ausdrücklich testen") { launchChrome(true) }
        line("Test A: Videolink lange drücken und „Im Weiktube-Player öffnen“ wählen, falls Chrome diesen Eintrag anbietet. Test B: Ein Video öffnen und die Wiedergabe-Aktion in der Chrome-Leiste drücken. Auf der Startseite selbst enthält diese Aktion noch keine Videoadresse.", 13f)
        line("Prüfe beim Wechsel: Startet der bisherige Weiktube-Player? Läuft Chrome-Ton weiter? Was passiert bei Zurück? Die App kann Chrome-Wiedergabe nicht fernsteuern. Weiktubes Adblocker filtert den Chrome-Inhalt nicht.", 13f)
        if (savedInstanceState == null && ChromeHomeTestState.manuallySawFeed(this)) {
            line("Eine frühere Sichtprüfung wurde lokal gespeichert. Sie bestätigt weder das aktuelle Konto noch eine Anmeldung; für diesen Test bitte erneut Schritt 1 durchführen.", 13f)
        }
        action("Test schließen") { finish() }
        setContentView(root)
        updateButtons()
        connectChrome()
    }

    private fun connectChrome() {
        val serviceIntent = Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION).setPackage(CHROME)
        val info = runCatching { packageManager.resolveService(serviceIntent, 0)?.serviceInfo }.getOrNull()
        if (info == null || info.packageName != CHROME || !info.enabled || !info.exported || !info.applicationInfo.enabled) {
            status.text = "Die normale Chrome-App mit Custom-Tabs-Unterstützung wurde nicht gefunden. Dieser Chrome-Test ist auf dem Gerät nicht verfügbar."
            return
        }
        val generation = ++bindingGeneration
        bound = runCatching { CustomTabsClient.bindCustomTabsService(this, CHROME, connection) }.getOrDefault(false)
        if (!bound) status.text = "Chrome konnte nicht verbunden werden. Kein Browser wurde als Ersatz geöffnet."
        handler.postDelayed({
            if (!isDestroyed && generation == bindingGeneration && session == null) {
                status.text = "Chrome hat keine nutzbare Custom-Tab-Verbindung bereitgestellt. Test schließen und erneut versuchen."
                updateButtons()
            }
        }, 8_000L)
    }

    @androidx.annotation.OptIn(markerClass = [ExperimentalCustomContentAction::class])
    private fun launchChrome(withHandoff: Boolean) {
        val currentSession = session ?: return
        if (withHandoff && (!feedReturned || !confirmFeed.isChecked)) return
        cancelHandoffActions()
        ChromeHomeTestState.setHandoffEnabled(this, withHandoff)
        if (!withHandoff) {
            confirmFeed.isChecked = false
            feedReturned = false
            awaitingFeedReturn = true
        }
        val builder = CustomTabsIntent.Builder(currentSession)
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
            .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(Color.rgb(18, 18, 18)).build())
            .setShowTitle(false)
            .setUrlBarHidingEnabled(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .setSendToExternalDefaultHandlerEnabled(false)
        // No ephemeral/incognito flag and no partial-tab size: ordinary Chrome login, full screen.
        if (withHandoff) {
            builder.setActionButton(playIcon(), "Im Weiktube-Player öffnen", handoffAction(ChromeHomeTestState.TOOLBAR_ACTION, TOOLBAR_REQUEST), true)
            builder.addCustomContentAction(CustomContentAction.Builder(
                ChromeHomeTestState.LINK_ACTION_ID, "Im Weiktube-Player öffnen",
                handoffAction(ChromeHomeTestState.LINK_ACTION, LINK_REQUEST), CustomTabsIntent.CONTENT_TARGET_TYPE_LINK
            ).build())
        }
        try {
            val tab = builder.build()
            tab.intent.setPackage(CHROME)
            tab.launchUrl(this, Uri.parse(HOME))
            status.text = if (withHandoff) "Videoübergabe-Test geöffnet. Nur eine ausdrücklich gewählte Chrome-Aktion übergibt eine Adresse."
                else "Original-Startseite geöffnet. Kontoprofil und Empfehlungen bitte direkt in YouTube prüfen."
        } catch (_: Exception) {
            awaitingFeedReturn = false
            ChromeHomeTestState.setHandoffEnabled(this, false)
            cancelHandoffActions()
            status.text = "Chrome konnte den Test nicht öffnen. Die bisherige App bleibt unverändert."
        }
        updateButtons()
    }

    private fun handoffAction(action: String, requestCode: Int): PendingIntent {
        // Mutable only so Chrome can attach the user-selected URL. Target and action are explicit.
        val target = Intent(this, OriginalHomeHandoffActivity::class.java).setAction(action)
        val options = if (Build.VERSION.SDK_INT >= 35) ActivityOptions.makeBasic().apply {
            setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }.toBundle() else null
        return PendingIntent.getActivity(this, requestCode, target, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE, options)
    }

    private fun cancelHandoffActions() {
        for ((action, code) in listOf(ChromeHomeTestState.TOOLBAR_ACTION to TOOLBAR_REQUEST, ChromeHomeTestState.LINK_ACTION to LINK_REQUEST)) {
            PendingIntent.getActivity(this, code, Intent(this, OriginalHomeHandoffActivity::class.java).setAction(action),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_MUTABLE)?.cancel()
        }
    }

    private fun playIcon(): Bitmap = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888).also { bitmap ->
        val path = Path().apply { moveTo(21f, 12f); lineTo(60f, 36f); lineTo(21f, 60f); close() }
        Canvas(bitmap).drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    }

    override fun onResume() {
        super.onResume()
        if (awaitingFeedReturn) {
            awaitingFeedReturn = false
            feedReturned = true
            status.text = "Zurück aus Chrome. Bestätige nur, wenn du dort dein richtiges Profil und deine eigene Startseite gesehen hast."
        }
        if (::openFeed.isInitialized) updateButtons()
    }

    private fun updateButtons() {
        if (!::openFeed.isInitialized) return
        openFeed.isEnabled = session != null
        if (::confirmFeed.isInitialized) confirmFeed.isEnabled = feedReturned
        if (::testHandoff.isInitialized) testHandoff.isEnabled = session != null && feedReturned && confirmFeed.isChecked
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("awaitingFeedReturn", awaitingFeedReturn)
        outState.putBoolean("feedReturned", feedReturned)
        outState.putBoolean("confirmedThisVisit", confirmFeed.isChecked)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        bindingGeneration++
        handler.removeCallbacksAndMessages(null)
        if (bound) runCatching { unbindService(connection) }
        if (isFinishing) {
            ChromeHomeTestState.setHandoffEnabled(this, false)
            cancelHandoffActions()
        }
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val CHROME = "com.android.chrome"
        private const val HOME = "https://m.youtube.com/"
        private const val TOOLBAR_REQUEST = 23101
        private const val LINK_REQUEST = 23102
    }
}

/** Only manual test switches. None of these values establish an authenticated identity. */
internal object ChromeHomeTestState {
    const val TOOLBAR_ACTION = "com.weiktube.home.CHROME_CURRENT_VIDEO"
    const val LINK_ACTION = "com.weiktube.home.CHROME_LINK_VIDEO"
    const val LINK_ACTION_ID = 101
    private fun prefs(context: Context) = context.getSharedPreferences("weiktube_chrome_manual_test", Context.MODE_PRIVATE)
    fun manuallySawFeed(context: Context) = prefs(context).getBoolean("manual_feed_seen", false)
    fun recordManualFeedSeen(context: Context, seen: Boolean) { prefs(context).edit().putBoolean("manual_feed_seen", seen).apply() }
    fun setHandoffEnabled(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean("handoff_enabled", enabled).apply() }
    fun handoffEnabled(context: Context) = manuallySawFeed(context) && prefs(context).getBoolean("handoff_enabled", false)
}
