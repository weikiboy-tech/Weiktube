package com.weiktube.home

import android.content.Context
import android.os.Looper
import android.os.Handler
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.StorageController

/** Only initialized by the explicit connection screen. Never shares Android WebView cookies. */
object HomeEngine {
    private var runtime: GeckoRuntime? = null
    private var clearing = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val waiting = mutableListOf<Pair<(GeckoRuntime) -> Unit, () -> Unit>>()
    private const val PREFS = "weiktube_home_connection"
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // A presentation marker, deliberately independent of the website/API account and old feed proof.
    // clearIfInitialized() clears it only when the user explicitly deletes website data.
    internal fun websiteSetupPresented(context: Context): Boolean =
        prefs(context).getBoolean("website_account_setup_presented_v1", false)

    internal fun recordWebsiteSetupPresented(context: Context) {
        prefs(context).edit().putBoolean("website_account_setup_presented_v1", true).apply()
    }

    fun prepare(context: Context, onReady: (GeckoRuntime) -> Unit, onFailure: () -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        val app = context.applicationContext
        val engine = try { runtime ?: GeckoRuntime.create(app).also { runtime = it } }
        catch (_: Exception) { onFailure(); return }
        if (clearing || prefs(app).getBoolean("clear_pending", false)) {
            waiting += onReady to onFailure
            beginClear(app, engine)
        } else recovered(app, engine, onReady, onFailure)
    }

    /** No runtime is created just to disconnect. A persistent marker prevents later reuse. */
    fun clearIfInitialized(context: Context) {
        check(Looper.myLooper() == Looper.getMainLooper())
        val app = context.applicationContext
        prefs(app).edit().clear().putBoolean("clear_pending", true).commit()
        runtime?.let { beginClear(app, it) }
    }

    private fun beginClear(context: Context, engine: GeckoRuntime) {
        if (clearing) return
        clearing = true
        engine.storageController.clearData(StorageController.ClearFlags.ALL).accept({
            mainHandler.post {
                prefs(context).edit().putBoolean("clear_pending", false).commit()
                clearing = false
                val callbacks = waiting.toList(); waiting.clear()
                callbacks.forEach { recovered(context, engine, it.first, it.second) }
            }
        }, {
            mainHandler.post {
                // Retain clear_pending, so a failed clear never silently reopens the old account.
                clearing = false
                val callbacks = waiting.toList(); waiting.clear()
                callbacks.forEach { it.second() }
            }
        })
    }

    private fun recovered(context: Context, engine: GeckoRuntime, ready: (GeckoRuntime) -> Unit, failure: () -> Unit) {
        HomeExperimentLease.recover(context, engine, { ready(engine) }, failure)
    }

    internal fun recordProof(context: Context, channelId: String, firstCount: Int, nextCount: Int) {
        prefs(context).edit().putString("proof_channel", channelId)
            .putInt("proof_first", firstCount).putInt("proof_next", nextCount)
            .putLong("proof_at", System.currentTimeMillis()).apply()
    }

    internal fun previouslyChecked(context: Context, channelId: String): Boolean =
        !prefs(context).getBoolean("clear_pending", false) &&
            prefs(context).getString("proof_channel", null) == channelId &&
            prefs(context).getLong("proof_at", 0L) > 0

    fun summary(context: Context): String {
        val p = prefs(context)
        if (p.getBoolean("clear_pending", false) || p.getLong("proof_at", 0L) == 0L)
            return "Persönliche Startseite noch nicht verbunden und geprüft."
        return "Letzter Datenabruf: ${p.getInt("proof_first", 0)} Videos, ${p.getInt("proof_next", 0)} weitere. Die Original-Startseite bleibt aktiv."
    }
}
