package com.weiktube.app

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout

/** Retains one existing WebView and intercepts only an intentional video-origin downward drag. */
class PlayerGestureLayout(context: Context) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val gesture = PlayerDragGesture(
        ViewConfiguration.get(context).scaledTouchSlop.toFloat(), 64f * density,
        24f * density, 900f * density,
    )
    private var gestureEnabled = false
    private var youtubeVideo = true
    private var onDrag: (Float) -> Unit = {}
    private var onMinimize: () -> Unit = {}
    private var tracker: VelocityTracker? = null
    private var ownsTouch = false
    private var childDisallowed = false

    fun attach(webView: WebView) {
        if (webView.parent === this) return
        check(childCount == 0) { "PlayerGestureLayout must retain exactly one player view" }
        (webView.parent as? ViewGroup)?.removeView(webView)
        addView(webView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun configure(enabled: Boolean, youtube: Boolean, onDrag: (Float) -> Unit, onMinimize: () -> Unit) {
        this.onDrag = onDrag
        this.onMinimize = onMinimize
        if (gestureEnabled != enabled || youtubeVideo != youtube) reset()
        gestureEnabled = enabled
        youtubeVideo = youtube
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) reset()
        else track(event)
        val handled = super.dispatchTouchEvent(event)
        // A rejected gesture may have yielded interception to WebView, so its UP can
        // bypass onInterceptTouchEvent. Still release our observer for every sequence.
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) reset()
        return handled
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        childDisallowed = disallowIntercept
        // WebView otherwise suppresses all future intercept callbacks after DOWN.
        // Delay that request only for our narrow, still-undecided video candidate.
        if (disallowIntercept && (gesture.candidate || gesture.dragging)) return
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val eligible = gestureEnabled && event.pointerCount == 1 &&
                    PlayerDragGesture.insideVideo(event.y, width.toFloat(), height.toFloat(), density, youtubeVideo)
                gesture.begin(eligible, event.rawX, event.rawY)
                if (eligible) { tracker = VelocityTracker.obtain(); track(event) }
            }
            MotionEvent.ACTION_MOVE -> {
                if (gestureEnabled && gesture.move(event.rawX, event.rawY, event.pointerCount)) {
                    ownsTouch = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    onDrag(gesture.distance)
                    // ViewGroup sends ACTION_CANCEL to the WebView at this interception.
                    return true
                }
                if (!gesture.candidate && childDisallowed) super.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> reset()
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!ownsTouch) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                gesture.move(event.rawX, event.rawY, event.pointerCount)
                onDrag(gesture.distance)
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                gesture.cancel()
                onDrag(0f)
                // Keep consuming this already-cancelled child sequence until its final UP.
            }
            MotionEvent.ACTION_UP -> {
                tracker?.computeCurrentVelocity(1000)
                val minimize = gestureEnabled && gesture.finish(event.rawX, event.rawY, tracker?.yVelocity ?: 0f)
                reset()
                if (minimize) onMinimize()
            }
            MotionEvent.ACTION_CANCEL -> reset()
        }
        return true
    }

    private fun track(event: MotionEvent) {
        val velocity = tracker ?: return
        // The Compose card can translate while dragging; velocity must share our raw-coordinate basis.
        val screenEvent = MotionEvent.obtain(event)
        screenEvent.offsetLocation(event.rawX - event.x, event.rawY - event.y)
        velocity.addMovement(screenEvent)
        screenEvent.recycle()
    }

    private fun reset() {
        val hadDrag = ownsTouch || gesture.dragging
        gesture.cancel()
        tracker?.recycle()
        tracker = null
        ownsTouch = false
        childDisallowed = false
        super.requestDisallowInterceptTouchEvent(false)
        if (hadDrag) onDrag(0f)
    }

    override fun onDetachedFromWindow() {
        reset()
        super.onDetachedFromWindow()
    }
}
