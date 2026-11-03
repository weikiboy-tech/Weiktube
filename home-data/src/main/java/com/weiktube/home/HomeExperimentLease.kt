package com.weiktube.home

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/** Owns extension changes, never profile cookies. Recovery runs before any later engine navigation. */
internal object HomeExperimentLease {
    const val CLICK_ID = "original-home-navigation@weiktube.local"
    private const val OLD_ID = "home-data@weiktube.local"
    private const val PREFS = "original_home_experiment_lease"
    private val handler = Handler(Looper.getMainLooper())
    private var owner: Lease? = null
    private var recovering = false
    private val waiters = mutableListOf<Pair<() -> Unit, () -> Unit>>()
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    class Lease internal constructor(private val context: Context, private val runtime: GeckoRuntime) {
        private var released = false
        fun release() {
            check(Looper.myLooper() == Looper.getMainLooper())
            if (released) return
            released = true
            if (owner === this) owner = null
            recover(context, runtime, {}, {})
        }
    }

    fun acquire(context: Context, runtime: GeckoRuntime, ready: (Lease) -> Unit, failure: () -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (owner != null || recovering) { failure(); return }
        val app = context.applicationContext
        val lease = Lease(app, runtime)
        owner = lease
        // Durable before any asynchronous mutation, including a new click-extension install.
        if (!prefs(app).edit().putBoolean("cleanup_click", true).commit()) {
            owner = null; failure(); return
        }
        fun failed() {
            if (owner === lease) owner = null
            recover(app, runtime, failure, failure)
        }
        runtime.webExtensionController.list().accept({ extensions ->
            handler.post {
                val old = extensions.orEmpty().firstOrNull { it.id == OLD_ID }
                val click = extensions.orEmpty().firstOrNull { it.id == CLICK_ID }
                fun disableClick() {
                    if (click == null || hasAppFlag(click)) ready(lease)
                    else runtime.webExtensionController.disable(click, WebExtensionController.EnableSource.APP)
                        .accept({ handler.post { ready(lease) } }, { handler.post { failed() } })
                }
                if (old == null || hasAppFlag(old)) disableClick()
                else if (!prefs(app).edit().putBoolean("restore_old", true).commit()) failed()
                else runtime.webExtensionController.disable(old, WebExtensionController.EnableSource.APP)
                    .accept({ handler.post { disableClick() } }, { handler.post { failed() } })
            }
        }, { handler.post { failed() } })
    }

    fun recover(context: Context, runtime: GeckoRuntime, ready: () -> Unit, failure: () -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (owner != null) { failure(); return }
        waiters += ready to failure
        if (recovering) return
        val app = context.applicationContext
        val p = prefs(app)
        if (!p.getBoolean("restore_old", false) && !p.getBoolean("cleanup_click", false)) {
            val callbacks = waiters.toList(); waiters.clear(); callbacks.forEach { it.first() }; return
        }
        recovering = true
        fun complete(success: Boolean) {
            val saved = success && p.edit().remove("restore_old").remove("cleanup_click").commit()
            recovering = false
            val callbacks = waiters.toList(); waiters.clear()
            callbacks.forEach { if (saved) it.first() else it.second() }
        }
        runtime.webExtensionController.list().accept({ extensions ->
            handler.post {
                val old = extensions.orEmpty().firstOrNull { it.id == OLD_ID }
                val click = extensions.orEmpty().firstOrNull { it.id == CLICK_ID }
                fun restoreOld() {
                    if (!p.getBoolean("restore_old", false) || old == null || !hasAppFlag(old)) complete(true)
                    else runtime.webExtensionController.enable(old, WebExtensionController.EnableSource.APP)
                        .accept({ handler.post { complete(true) } }, { handler.post { complete(false) } })
                }
                if (!p.getBoolean("cleanup_click", false) || click == null || hasAppFlag(click)) restoreOld()
                else runtime.webExtensionController.disable(click, WebExtensionController.EnableSource.APP)
                    .accept({ handler.post { restoreOld() } }, { handler.post { complete(false) } })
            }
        }, { handler.post { complete(false) } })
    }

    private fun hasAppFlag(extension: WebExtension): Boolean =
        extension.metaData.disabledFlags and WebExtension.DisabledFlags.APP != 0
}
