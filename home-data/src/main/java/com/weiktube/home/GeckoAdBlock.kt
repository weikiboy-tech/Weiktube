package com.weiktube.home

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.weiktube.filter.YoutubeAdBlock
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import java.util.UUID

/** A separate extension; it never owns the home session, website login or video handoff. */
object GeckoAdBlock {
    private const val EXTENSION_ID = "weiktube-youtube-adblock@weiktube.local"
    private const val NATIVE_APP = "weiktubeAdBlock"
    private const val TIMEOUT_MS = 20_000L
    private val main = Handler(Looper.getMainLooper())
    private var runtime: GeckoRuntime? = null
    private var extension: WebExtension? = null
    private var port: WebExtension.Port? = null
    private var installing = false
    private var configured = false
    private var revision = 0
    private var pendingKey = ""
    private var installedKey = ""
    private var timeout: Runnable? = null
    private val waiting = mutableListOf<Pair<() -> Unit, () -> Unit>>()

    internal fun prepare(context: Context, engine: GeckoRuntime, ready: () -> Unit, failure: () -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        YoutubeAdBlock.initialize(context.applicationContext)
        if (!YoutubeAdBlock.state.value.initialized) {
            YoutubeAdBlock.engineError("gecko", "FILTER_NOT_READY")
            failure(); return
        }
        if (runtime !== engine) {
            port?.disconnect()
            runtime = engine
            extension = null
            port = null
            configured = false
            installing = false
            installedKey = ""
        }
        if (configured && installedKey == policyKey()) { ready(); return }
        waiting += ready to failure
        if (port != null) { sendPolicy(); return }
        if (installing) return
        installing = true
        armTimeout()
        engine.webExtensionController.ensureBuiltIn(
            "resource://android/assets/youtube-adblock/", EXTENSION_ID
        ).accept({ found ->
            main.post {
                if (runtime !== engine) return@post
                if (found == null || found.id != EXTENSION_ID) { fail("INSTALL_FAILED"); return@post }
                extension = found
                found.setMessageDelegate(object : WebExtension.MessageDelegate {
                    override fun onConnect(connection: WebExtension.Port) {
                        // Only the extension background can receive policy and packaged executable code.
                        if (connection.sender.webExtension.id != EXTENSION_ID ||
                            connection.sender.session != null ||
                            connection.sender.environmentType != WebExtension.MessageSender.ENV_TYPE_EXTENSION) {
                            connection.disconnect(); return
                        }
                        port?.takeIf { it !== connection }?.disconnect()
                        port = connection
                        installing = false
                        configured = false
                        pendingKey = ""
                        connection.setDelegate(object : WebExtension.PortDelegate {
                            override fun onPortMessage(message: Any, source: WebExtension.Port) {
                                if (source !== port) return
                                val data = message as? JSONObject ?: return
                                when (data.optString("type")) {
                                    "configured" -> {
                                        if (data.optInt("revision", -1) != revision) return
                                        configured = true
                                        installedKey = pendingKey
                                        YoutubeAdBlock.engineReady("gecko")
                                        timeout?.let(main::removeCallbacks); timeout = null
                                        val callbacks = waiting.toList(); waiting.clear()
                                        callbacks.forEach { it.first() }
                                    }
                                    "configure-error" -> if (data.optInt("revision", -1) == revision) fail("CONFIGURATION_FAILED")
                                    "network", "data", "cosmetic", "breakthrough" -> {
                                        val count = data.optInt("count", 0)
                                        if (count in 1..10_000) YoutubeAdBlock.report(data.getString("type"), count)
                                    }
                                    "error" -> YoutubeAdBlock.engineError("gecko", "FILTER_RUNTIME_ERROR")
                                    "ready" -> YoutubeAdBlock.engineReady("gecko")
                                }
                            }

                            override fun onDisconnect(source: WebExtension.Port) {
                                if (source !== port) return
                                port = null
                                configured = false
                                if (waiting.isNotEmpty()) fail("CONNECTION_LOST")
                            }
                        })
                        sendPolicy()
                    }
                }, NATIVE_APP)
            }
        }, { main.post { if (runtime === engine) fail("INSTALL_FAILED") } })
    }

    /** Existing documents retain their policy. The next document receives current settings/rules. */
    fun refreshPolicy() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { refreshPolicy() }; return }
        if (port != null && (!configured || installedKey != policyKey())) sendPolicy()
    }

    private fun policyKey(): String = "${YoutubeAdBlock.currentSnapshot().version}:${YoutubeAdBlock.enabled}:" +
        YoutubeAdBlock.exceptions.sorted().joinToString(",")

    private fun sendPolicy() {
        val connection = port ?: return
        val key = policyKey()
        if (!configured && pendingKey == key && timeout != null && !installing) return
        val snapshot = YoutubeAdBlock.currentSnapshot()
        val active = YoutubeAdBlock.enabled
        val exceptions = YoutubeAdBlock.exceptions.toSet()
        val token = UUID.randomUUID().toString()
        try {
            val config = YoutubeAdBlock.configJson(snapshot, token, active, exceptions)
            val script = YoutubeAdBlock.script(snapshot, token, active, exceptions)
            revision += 1
            pendingKey = key
            configured = false
            armTimeout()
            connection.postMessage(JSONObject().put("type", "configure").put("revision", revision)
                .put("config", config).put("script", script))
        } catch (_: Exception) { fail("POLICY_FAILED") }
    }

    private fun armTimeout() {
        timeout?.let(main::removeCallbacks)
        timeout = Runnable { fail("INITIALIZATION_TIMEOUT") }.also { main.postDelayed(it, TIMEOUT_MS) }
    }

    private fun fail(code: String) {
        timeout?.let(main::removeCallbacks); timeout = null
        configured = false
        installing = false
        pendingKey = ""
        // Only a fixed diagnostic code, never a URL, account identifier or response body.
        Log.w("WeiktubeAdBlock", "GECKO_ADBLOCK_$code")
        YoutubeAdBlock.engineError("gecko", when (code) {
            "CONFIGURATION_FAILED" -> "DOCUMENT_START_UNSUPPORTED"
            "CONNECTION_LOST", "INITIALIZATION_TIMEOUT" -> "BRIDGE_UNAVAILABLE"
            else -> "FILTER_NOT_READY"
        })
        val callbacks = waiting.toList(); waiting.clear()
        callbacks.forEach { it.second() }
    }
}
