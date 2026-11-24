package com.weiktube.app

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

/** Owns at most one single-finger gesture. Buffered originals are replayed only to the same child.
 * Unknown DOM hit tests fail open after 120 ms; controls and horizontal drags retain WebView handling.
 * Native seek/hold sequences never reach YouTube, so its recognizers cannot execute them twice. */
internal class AdvancedPlayerGesture(
    private val slop: Float,
    private val send: (MotionEvent) -> Unit,
    private val hitTest: (Float, Float, (Boolean) -> Unit) -> Unit,
    private val startLevel: (Boolean) -> Unit,
    private val level: (Boolean, Float) -> Unit,
    private val seek: (Int) -> Unit,
    private val hold: (Boolean) -> Unit,
    private val finish: () -> Unit,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    var options = PlayerPreferences()
    private enum class Mode { IDLE, CHECKING, READY, LEVEL, HOLD, PASS, CONSUMED }
    private var mode = Mode.IDLE
    private var serial = 0
    private var x = 0f; private var y = 0f; private var height = 1f
    private var left = false
    private var buffer = mutableListOf<MotionEvent>()
    private var tap = mutableListOf<MotionEvent>()
    private var tapLeft = false
    private var lastSeekAt = 0L
    private var seekLeft = false
    private var ended = false
    private val single = Runnable { flushTap() }
    private val fallback = Runnable { if (mode == Mode.CHECKING) pass() }
    private val longPress = Runnable {
        if (mode == Mode.READY && !ended && options.hold) {
            mode = Mode.HOLD; discard(buffer); flushTap(); hold(true)
        }
    }
    fun event(e: MotionEvent, width: Int, viewHeight: Int): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            cancelCurrent()
            if (!(options.brightness || options.volume || options.doubleTap || options.hold)) return false
            x = e.x; y = e.y; height = viewHeight.toFloat().coerceAtLeast(1f); left = x < width / 2f
            mode = Mode.CHECKING; ended = false
            buffer.add(MotionEvent.obtain(e))
            val request = ++serial
            handler.postDelayed(fallback, 120)
            handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            hitTest(x / width.coerceAtLeast(1), y / height) { allowed ->
                if (request != serial || mode != Mode.CHECKING) return@hitTest
                handler.removeCallbacks(fallback)
                if (allowed) {
                    mode = Mode.READY
                    // Process movement collected while the renderer performed the hit test.
                    buffer.lastOrNull { it.actionMasked == MotionEvent.ACTION_MOVE }?.let { move(it) }
                    if (ended && mode == Mode.READY) tapped()
                    else if (ended && mode == Mode.LEVEL) { finish(); mode = Mode.IDLE }
                } else pass()
            }
            return true
        }
        if (mode == Mode.IDLE || mode == Mode.PASS) return false
        if (e.actionMasked == MotionEvent.ACTION_CANCEL || e.pointerCount > 1) { cancel(); return true }
        if (mode == Mode.CHECKING || mode == Mode.READY) buffer.add(MotionEvent.obtain(e))
        if (buffer.size > 64) { pass(); return true }
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> move(e)
            MotionEvent.ACTION_UP -> {
                ended = true; handler.removeCallbacks(longPress)
                when (mode) {
                    Mode.READY -> tapped()
                    Mode.HOLD -> { hold(false); finish(); mode = Mode.IDLE }
                    Mode.LEVEL, Mode.CONSUMED -> { finish(); mode = Mode.IDLE }
                    else -> Unit
                }
            }
        }
        return true
    }
    private fun move(e: MotionEvent) {
        val dx = e.x - x; val dy = e.y - y
        if (mode == Mode.READY && (abs(dx) > slop || abs(dy) > slop)) {
            handler.removeCallbacks(longPress)
            if (abs(dy) > abs(dx) * 1.3f && (if (left) options.brightness else options.volume)) {
                mode = Mode.LEVEL; discard(buffer); flushTap(); startLevel(left)
            } else { pass(); return }
        }
        if (mode == Mode.LEVEL) level(left, -dy / (height * .7f))
        if (mode == Mode.HOLD && (abs(dx) > slop * 2 || abs(dy) > slop * 2)) {
            hold(false); finish(); mode = Mode.CONSUMED
        }
    }
    private fun tapped() {
        handler.removeCallbacks(longPress)
        if (!options.doubleTap) { pass(); return }
        val now = SystemClock.uptimeMillis()
        if ((tap.isNotEmpty() && tapLeft == left) || (lastSeekAt > 0 && now - lastSeekAt < 350 && seekLeft == left)) {
            handler.removeCallbacks(single); discard(tap); discard(buffer)
            seek((if (left) -1 else 1) * options.seekSeconds)
            lastSeekAt = now; seekLeft = left; mode = Mode.IDLE
        } else {
            flushTap(); tap = buffer; buffer = mutableListOf(); tapLeft = left
            handler.postDelayed(single, ViewConfiguration.getDoubleTapTimeout().toLong())
            mode = Mode.IDLE
        }
    }
    private fun pass() {
        handler.removeCallbacks(longPress); handler.removeCallbacks(fallback)
        flushTap(); buffer.forEach(send); discard(buffer); mode = Mode.PASS
    }
    private fun flushTap() { handler.removeCallbacks(single); tap.forEach(send); discard(tap) }
    private fun discard(events: MutableList<MotionEvent>) { events.forEach { it.recycle() }; events.clear() }
    private fun cancelCurrent() {
        serial++; handler.removeCallbacks(fallback); handler.removeCallbacks(longPress)
        if (mode == Mode.HOLD) hold(false)
        discard(buffer); mode = Mode.IDLE
    }
    /** Rotation, source/mode change, focus loss and a second pointer all restore transient state. */
    fun cancel() { cancelCurrent(); handler.removeCallbacks(single); discard(tap); lastSeekAt = 0; finish() }
}
