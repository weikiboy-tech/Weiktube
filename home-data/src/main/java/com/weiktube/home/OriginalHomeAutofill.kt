package com.weiktube.home

import android.app.Activity
import android.graphics.Rect
import android.os.Looper
import android.view.View
import android.view.autofill.AutofillManager
import org.mozilla.geckoview.Autofill
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/** Uses Gecko's existing Android Autofill delegate. Never accesses field values or HTML attributes. */
internal class OriginalHomeAutofill(
    activity: Activity,
    private val target: () -> Target?,
    private val status: (String) -> Unit,
) {
    data class Target(val session: GeckoSession, val view: GeckoView, val revision: Int, val active: Boolean, val authPage: Boolean, val documentReady: Boolean)
    private data class Capture(val target: Target, val state: OriginalHomeAutofillPolicy.Snapshot, val bounds: Rect, val hint: String)
    private enum class Event { NONE, REQUESTED, SHOWN, HIDDEN, UNAVAILABLE, REQUEST_FAILED, CALLBACK_UNAVAILABLE }
    private val manager = activity.getSystemService(AutofillManager::class.java)
    private var registered = false
    private var request: OriginalHomeAutofillPolicy.Snapshot? = null
    private var event = Event.NONE

    private val callback = object : AutofillManager.AutofillCallback() {
        override fun onAutofillEvent(view: View, virtualId: Int, eventCode: Int) {
            val pending = request ?: return
            if (!registered || view !== pending.view || virtualId != pending.fieldId) return
            val now = capture() ?: return
            if (OriginalHomeAutofillPolicy.revalidate(pending, now.state) != OriginalHomeAutofillPolicy.Result.READY) return
            when (eventCode) {
                EVENT_INPUT_SHOWN -> { event = Event.SHOWN; status("Der Android-Passwortmanager zeigt Vorschläge für das gewählte Feld.") }
                EVENT_INPUT_HIDDEN -> event = Event.HIDDEN
                EVENT_INPUT_UNAVAILABLE -> {
                    event = Event.UNAVAILABLE
                    status("Der Passwortmanager bietet hier gerade keine Vorschläge an. Daraus lässt sich nicht erkennen, ob Zugangsdaten gespeichert sind.")
                }
                else -> return
            }
        }
    }

    fun start() {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!registered && manager != null) {
            registered = runCatching { manager.registerCallback(callback); true }.getOrDefault(false)
            if (!registered) event = Event.CALLBACK_UNAVAILABLE
        }
    }

    fun stop() {
        check(Looper.myLooper() == Looper.getMainLooper())
        request = null
        event = Event.NONE
        if (registered) runCatching { manager?.unregisterCallback(callback) }
        registered = false
        // Do not cancel the provider's authentication UI or clear Gecko's field/session state.
    }

    fun invalidate() { request = null; event = Event.NONE }

    fun suggest() {
        check(Looper.myLooper() == Looper.getMainLooper())
        request = null
        event = Event.NONE
        val first = capture()
        if (first == null) { status("Tippe zuerst in das sichtbare Google-Anmeldefeld."); return }
        val eligible = OriginalHomeAutofillPolicy.evaluate(first.state)
        if (eligible != OriginalHomeAutofillPolicy.Result.READY) { rejected(eligible); return }
        // Re-read the actual visible host, focused virtual node and document immediately before the call.
        val now = capture()
        if (now == null) { rejected(OriginalHomeAutofillPolicy.Result.STALE); return }
        val current = OriginalHomeAutofillPolicy.revalidate(first.state, now.state)
        if (current != OriginalHomeAutofillPolicy.Result.READY) { rejected(current); return }
        request = now.state
        event = Event.REQUESTED
        status("Android-Passwortmanager angefragt. Ob Vorschläge erscheinen, entscheidet dein Anbieter.")
        runCatching {
            manager!!.requestAutofill(now.target.view, now.state.fieldId!!, Rect(now.bounds))
        }.onFailure {
            request = null; event = Event.REQUEST_FAILED
            status("Android konnte die Vorschläge nicht anfordern. Bitte das Anmeldefeld erneut antippen und den gewählten Passwortmanager prüfen.")
        }
    }

    private fun rejected(result: OriginalHomeAutofillPolicy.Result) {
        status(when (result) {
            OriginalHomeAutofillPolicy.Result.UNSUPPORTED -> "Android-Autofill ist für diese Ansicht oder dieses Gerät nicht verfügbar."
            OriginalHomeAutofillPolicy.Result.DISABLED -> "Für diese App ist aktuell kein aktiver Android-Autofill-Dienst verfügbar. Prüfe den gewählten Passwortmanager in den Android-Einstellungen."
            OriginalHomeAutofillPolicy.Result.WRONG_PAGE -> "Vorschläge lassen sich auf der sichtbaren normalen Google-Anmeldeseite anfordern."
            OriginalHomeAutofillPolicy.Result.DOCUMENT_LOADING -> "Bitte warten, bis die Google-Anmeldeseite geladen ist, und dann das Eingabefeld antippen."
            else -> "Tippe zuerst direkt in das sichtbare Google-E-Mail- oder Passwortfeld und dann auf „Zugangsdaten vorschlagen“."
        })
    }

    private fun capture(): Capture? {
        val current = target() ?: return null
        val view = current.view
        val geckoBound = view.session === current.session
        val fields = if (geckoBound) current.session.autofillSession else null
        val node = fields?.focused
        val fieldData = fields?.focusedData
        val rect = node?.screenRect?.let(::Rect) ?: Rect()
        val viewport = Rect()
        val visibleBounds = view.getGlobalVisibleRect(viewport)
        // getGlobalVisibleRect is relative to the root view; Gecko reports absolute screen bounds.
        val rootOnScreen = IntArray(2)
        view.rootView.getLocationOnScreen(rootOnScreen)
        viewport.offset(rootOnScreen[0], rootOnScreen[1])
        val boundsValid = !rect.isEmpty && visibleBounds && Rect.intersects(rect, viewport)
        val snapshot = OriginalHomeAutofillPolicy.Snapshot(
            session = current.session, view = view, revision = current.revision,
            active = current.active && geckoBound, authPage = current.authPage,
            documentReady = current.documentReady,
            attached = view.isAttachedToWindow, shown = view.isShown, focused = view.hasFocus(),
            geckoEnabled = view.autofillEnabled && current.session.autofillDelegate != null,
            supported = runCatching { manager?.isAutofillSupported == true }.getOrDefault(false),
            enabled = runCatching { manager?.isEnabled == true }.getOrDefault(false),
            field = node, fieldId = fieldData?.id, usableBounds = boundsValid,
        )
        val hint = if (node == null) "kein Feld" else when (node.hint) {
            Autofill.Hint.EMAIL_ADDRESS -> "E-Mail"
            Autofill.Hint.USERNAME -> "Benutzername"
            Autofill.Hint.PASSWORD -> "Passwort"
            else -> "anderes Feld"
        }
        return Capture(current, snapshot, rect, hint)
    }

    /** Deliberately excludes values, attributes, URLs, account names, provider data and internal IDs. */
    fun diagnostics(): String {
        val now = capture()
        val state = now?.state
        val visibleEvent = if (request != null && (state == null ||
            OriginalHomeAutofillPolicy.revalidate(request!!, state) != OriginalHomeAutofillPolicy.Result.READY)) Event.NONE else event
        fun yes(value: Boolean?) = when (value) { true -> "ja"; false -> "nein"; null -> "unbekannt" }
        return "Autofill unterstützt: ${yes(state?.supported)} · aktiviert: ${yes(state?.enabled)}\n" +
            "Gecko-Anbindung: ${yes(state?.geckoEnabled)} · Ansicht sichtbar: ${yes(state?.shown)} · Fokus: ${yes(state?.focused)}\n" +
            "Seite geladen: ${yes(state?.documentReady)} · Eingabefeld erkannt: ${yes(state?.let { it.field != null })} · Feldtyp: ${now?.hint ?: "unbekannt"} · Feld sichtbar: ${yes(state?.usableBounds)}\n" +
            "Autofill-Rückmeldung: ${when (visibleEvent) {
                Event.NONE -> "keine Anfrage"
                Event.REQUESTED -> "angefragt, Ergebnis offen"
                Event.SHOWN -> "Vorschläge angezeigt"
                Event.HIDDEN -> "Vorschläge ausgeblendet"
                Event.UNAVAILABLE -> "Anbieter zeigt keine Vorschläge"
                Event.REQUEST_FAILED -> "Android-Anfrage fehlgeschlagen"
                Event.CALLBACK_UNAVAILABLE -> "Rückmeldung nicht verfügbar"
            }}"
    }
}
