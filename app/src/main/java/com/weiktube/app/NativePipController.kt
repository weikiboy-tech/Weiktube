package com.weiktube.app

import android.app.Activity
import android.app.KeyguardManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.core.content.ContextCompat
import java.util.UUID

/** Android window/controls only. Playback and its document remain owned by PlayerController. */
internal class NativePipController(
    private val activity: Activity,
    private val snapshot: () -> Snapshot,
    private val play: () -> Unit,
    private val pause: () -> Unit,
    private val screenOff: () -> Unit,
    private val message: (String) -> Unit,
    private val screenUnlocked: () -> Unit = {},
    private val screenOn: () -> Unit = screenUnlocked,
) {
    data class Snapshot(
        val source: String?, val ready: Boolean, val playing: Boolean,
        val visible: Boolean, val resumed: Boolean, val blockedByUi: Boolean,
        val width: Int, val height: Int, val bounds: Rect?,
    )

    private val action = "${activity.packageName}.PIP_CONTROL"
    private var token = UUID.randomUUID().toString()
    private var source: String? = null
    private var last: Pair<Snapshot, Boolean>? = null
    private var entered = false
    private var entering = false
    private var closed = false
    private val supported get() = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    private val locked get() = activity.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (closed || intent.action != action || intent.getStringExtra("token") != token ||
                !entered || locked) return
            val state = snapshot()
            if (!state.visible || state.source != source || state.source == null) return
            when (intent.getStringExtra("command")) {
                "play" -> if (state.ready) play()
                "pause" -> pause()
                else -> return
            }
            sync()
        }
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (closed) return
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> screenOff()
                Intent.ACTION_SCREEN_ON -> screenOn()
                Intent.ACTION_USER_PRESENT -> screenUnlocked()
                else -> return
            }
            sync()
        }
    }

    init {
        ContextCompat.registerReceiver(activity, controlReceiver, IntentFilter(action).apply {
            addDataScheme("weiktube-pip")
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(activity, screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF).apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun sync() {
        if (closed || !supported || activity.isFinishing || activity.isDestroyed) return
        val state = snapshot()
        if (source != state.source) { source = state.source; token = UUID.randomUUID().toString() }
        val auto = eligible(state, automatic = true) && !entered
        val key = state to auto
        if (last == key) return
        try {
            activity.setPictureInPictureParams(params(state, auto))
            last = key
        } catch (_: IllegalArgumentException) {
            // OEM/platform rejection must not crash or replace the current player.
            android.util.Log.w("WeiktubePiP", "PARAMS_REJECTED")
        } catch (_: IllegalStateException) {
            android.util.Log.w("WeiktubePiP", "WINDOW_UNAVAILABLE")
        }
    }

    fun enter(manual: Boolean = true) {
        if (closed || entered || entering || activity.isInPictureInPictureMode) return
        if (!supported) { if (manual) message("Dieses Gerät unterstützt kein Android-Bild-in-Bild."); return }
        val state = snapshot()
        if (!eligible(state, automatic = !manual)) {
            if (manual) message("Öffne zuerst ein abspielbares Video für Bild-in-Bild.")
            return
        }
        sync()
        try {
            entering = activity.enterPictureInPictureMode(params(state, auto = false))
            last = null // enter() also supplies params; the next sync must restore auto eligibility.
            if (!entering && manual) message("Bild-in-Bild ist in den Android-Einstellungen deaktiviert oder gerade nicht verfügbar.")
        } catch (_: IllegalStateException) {
            if (manual) message("Bild-in-Bild ist momentan nicht verfügbar.")
        } catch (_: IllegalArgumentException) {
            if (manual) message("Das Gerät konnte das Bild-in-Bild-Fenster nicht öffnen.")
        }
    }

    fun onUserLeaveHint() {
        // Android 12+ owns automatic transitions; do not request a second transition there.
        if (Build.VERSION.SDK_INT < 31) enter(manual = false) else sync()
    }

    fun modeChanged(inPip: Boolean) {
        entered = inPip; entering = false; last = null
        sync()
        // No play/close here: false also means Expand, and a user pause must survive it.
    }

    fun resumed() { entering = false; sync() }
    fun stopped() { entering = false; sync() }

    private fun eligible(state: Snapshot, automatic: Boolean) =
        state.source != null && state.ready && state.visible && state.resumed &&
            !state.blockedByUi && !locked && (!automatic || state.playing) &&
            !activity.isFinishing && !activity.isDestroyed

    private fun params(state: Snapshot, auto: Boolean): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio(state.width, state.height))
            .setSourceRectHint(state.bounds?.let(::Rect) ?: Rect())
            .setActions(if (state.source != null && state.ready) listOf(control(state.playing)) else emptyList())
        if (Build.VERSION.SDK_INT >= 31) builder.setAutoEnterEnabled(auto).setSeamlessResizeEnabled(false)
        if (Build.VERSION.SDK_INT >= 33) builder.setTitle("Weiktube")
        return builder.build()
    }

    private fun control(playing: Boolean): RemoteAction {
        val command = if (playing) "pause" else "play"
        val label = if (playing) "Pausieren" else "Abspielen"
        val intent = Intent(action).setPackage(activity.packageName)
            .setData(android.net.Uri.parse("weiktube-pip:$token/$command"))
            .putExtra("token", token).putExtra("command", command)
        val pending = PendingIntent.getBroadcast(activity, if (playing) 1 else 2, intent, PendingIntent.FLAG_IMMUTABLE)
        return RemoteAction(Icon.createWithResource(activity,
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play), label, label, pending)
    }

    fun close() {
        if (closed) return
        closed = true
        activity.unregisterReceiver(controlReceiver)
        activity.unregisterReceiver(screenReceiver)
    }

    companion object {
        internal fun aspectRatio(width: Int, height: Int): Rational {
            if (width <= 0 || height <= 0) return Rational(16, 9)
            // Android's standard PiP range, including portrait videos. No expanded-PiP mode.
            val ratio = (width.toDouble() / height).coerceIn(1.0 / 2.39, 2.39)
            return Rational((ratio * 10_000).toInt().coerceAtLeast(4185), 10_000)
        }
    }
}
