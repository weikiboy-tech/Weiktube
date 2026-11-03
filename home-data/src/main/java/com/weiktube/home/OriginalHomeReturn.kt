package com.weiktube.home

import android.os.Looper
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.MediaSession
import org.mozilla.geckoview.WebExtension
import java.util.UUID

/** Holds one live feed document during an original-player round trip, without retaining an Activity. */
object OriginalHomeReturn {
    const val EXTRA_RETURN_TOKEN = "com.weiktube.home.RETURN_TOKEN"
    const val EXTRA_VIDEO_URL = "com.weiktube.home.VIDEO_URL"
    const val EXTRA_RESUME_TOKEN = "com.weiktube.home.RESUME_TOKEN"
    internal const val NATIVE_APP = "weiktubeOriginalHome"

    internal class Connection(val port: WebExtension.Port, val serial: Int) {
        var documentId: String? = null
        val activation = OriginalHomeBridgeActivation()
        val token get() = activation.token
        val acknowledged get() = activation.acknowledged
        var retryGeneration: String? = null
        var blockedReason: String? = null
    }
    internal class Retained(
        val runtime: GeckoRuntime,
        val extension: WebExtension,
        val lease: HomeExperimentLease.Lease,
        val main: GeckoSession,
        val popup: GeckoSession?,
        val locations: MutableMap<GeckoSession, String>,
        val history: MutableMap<GeckoSession, Boolean>,
        val documentSerial: MutableMap<GeckoSession, Int>,
        val connections: MutableMap<GeckoSession, Connection>,
        val media: MutableMap<GeckoSession, MediaSession>,
        val loading: Set<GeckoSession>,
    ) {
        fun sessions() = listOfNotNull(main, popup)
    }
    private class Entry(val policy: OriginalHomeReturnPolicy, val retained: Retained)
    private var entry: Entry? = null

    /** Public result boundary. Only the exact selected public video is accepted, once. */
    fun acceptPlayerResult(token: String?, url: String?): String? {
        mainThread()
        val canonical = url?.let(OriginalHomePolicy::videoUrl) ?: return null
        return canonical.takeIf { entry?.policy?.accept(token, canonical) == true }
    }

    fun resumeToken(): String? {
        mainThread()
        return entry?.policy?.resumeToken()
    }

    internal fun take(token: String?): Retained? {
        mainThread()
        val held = entry ?: return null
        if (!held.policy.take(token)) return null
        entry = null
        return held.retained
    }

    internal fun park(retained: Retained, video: String): String {
        mainThread()
        check(entry == null) { "A feed is already parked" }
        val canonical = requireNotNull(OriginalHomePolicy.videoUrl(video))
        val token = UUID.randomUUID().toString()
        val held = Entry(OriginalHomeReturnPolicy(token, canonical), retained)
        entry = held
        retained.media.values.forEach { runCatching { it.pause(); it.muteAudio(true) } }
        retained.sessions().forEach { session ->
            session.settings.suspendMediaWhenInactive = true
            session.setActive(false); session.setFocused(false)
            // Every replacement below is Activity-free. A parked feed cannot start a new navigation.
            session.navigationDelegate = object : GeckoSession.NavigationDelegate {
                override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest) = GeckoResult.fromValue(AllowOrDeny.DENY)
                override fun onSubframeLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest) = GeckoResult.fromValue(AllowOrDeny.DENY)
            }
            // Keep Gecko's Progress module enabled while this document is parked. Disabling it
            // would replay _fireInitialLoad/about:blank on return, invalidating its live bridge.
            // A non-null, Activity-free delegate consumes parked callbacks without that restart.
            session.progressDelegate = object : GeckoSession.ProgressDelegate {}
            session.historyDelegate = null
            session.contentDelegate = object : GeckoSession.ContentDelegate {
                override fun onCrash(session: GeckoSession) { if (entry === held) discard() }
                override fun onCloseRequest(session: GeckoSession) { if (entry === held) discard() }
            }
            session.mediaSessionDelegate = object : MediaSession.Delegate {
                override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
                    mediaSession.pause(); mediaSession.muteAudio(true)
                    if (entry === held) retained.media[session] = mediaSession
                }
                override fun onPlay(session: GeckoSession, mediaSession: MediaSession) {
                    mediaSession.pause(); mediaSession.muteAudio(true)
                }
            }
            session.webExtensionController.setMessageDelegate(retained.extension, object : WebExtension.MessageDelegate {
                override fun onConnect(port: WebExtension.Port) { port.disconnect() }
            }, NATIVE_APP)
        }
        retained.connections.toMap().forEach { (session, connection) ->
            connection.activation.suspend()
            runCatching {
                connection.port.postMessage(JSONObject().put("type", "disable").put("documentId", connection.documentId).put("generation", connection.token))
            }
            connection.port.setDelegate(object : WebExtension.PortDelegate {
                override fun onPortMessage(message: Any, port: WebExtension.Port) {
                    // A ready event already in flight may complete while parking; never enable or hand off here.
                    if (entry !== held || retained.connections[session] !== connection || port !== connection.port ||
                        port.sender.session !== session || !port.sender.isTopLevel || !OriginalHomePolicy.isYoutubePage(port.sender.url)) return
                    val data = message as? JSONObject ?: return
                    if (data.optString("type") == "ready" && connection.documentId == null) {
                        val id = data.optString("documentId")
                        if (DOCUMENT_ID.matches(id)) connection.documentId = id
                    }
                }
                override fun onDisconnect(port: WebExtension.Port) {
                    if (entry === held && retained.connections[session] === connection) retained.connections.remove(session)
                }
            })
        }
        return token
    }

    fun discard() {
        mainThread()
        val held = entry ?: return
        entry = null
        held.policy.discard()
        val retained = held.retained
        retained.connections.values.forEach { runCatching { it.port.disconnect() } }
        retained.connections.clear()
        retained.media.values.forEach { runCatching { it.pause(); it.muteAudio(true) } }
        retained.sessions().forEach { session ->
            session.webExtensionController.setMessageDelegate(retained.extension, null, NATIVE_APP)
            session.navigationDelegate = null; session.progressDelegate = null; session.contentDelegate = null; session.mediaSessionDelegate = null
            runCatching { session.setActive(false); session.close() }
        }
        retained.lease.release()
    }

    private fun mainThread() = check(Looper.myLooper() == Looper.getMainLooper())
    private val DOCUMENT_ID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
}
