package com.weiktube.app

import android.content.Context
import android.content.res.Configuration
import android.view.ScaleGestureDetector
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

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
    private var systemInsetsHandledByHost = true
    private var stableCompact = false
    private var viewportSource: String? = null
    private var logicalWidth = 0
    private var logicalHeight = 0
    private var mediaWidth = 0
    private var mediaHeight = 0

    private var advancedEnabled = false
    private var advancedKey: String? = null
    private var advancedPlayer: PlayerController? = null
    private var advanced: AdvancedPlayerGesture? = null
    private var windowControls: PlayerWindowControls? = null
    internal fun configureAdvanced(enabled: Boolean, key: String?, player: PlayerController, options: PlayerPreferences) {
        if (advanced == null) {
            var ctx = context
            while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) ctx = ctx.baseContext
            val activity = ctx as? android.app.Activity
            if (activity != null) windowControls = PlayerWindowControls(activity)
            advanced = AdvancedPlayerGesture(ViewConfiguration.get(context).scaledTouchSlop.toFloat(),
                send = { super.dispatchTouchEvent(it) },
                hitTest = { x, y, callback -> advancedPlayer?.gestureHit(x, y, callback) ?: callback(false) },
                startLevel = { windowControls?.begin(it) },
                level = { left, delta -> windowControls?.change(delta)?.let { value ->
                    advancedPlayer?.feedback(if (left) "brightness" else "volume", "$value %", value)
                } }, seek = { advancedPlayer?.seekGesture(it) },
                hold = { advancedPlayer?.holdGesture(it) }, finish = { advancedPlayer?.finishGesture() })
        }
        if (advancedEnabled != enabled || advancedKey != key || advancedPlayer !== player || advanced?.options != options) {
            advanced?.cancel()
            if (!enabled || advancedKey != key) windowControls?.restore()
        }
        advancedEnabled = enabled; advancedKey = key; advancedPlayer = player; advanced?.options = options
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) { advanced?.cancel(); windowControls?.restore() }
    }

    private var zoomEnabled = false
    private var zoomFullVideo = false
    private var zoomKey: String? = null
    private var zoomCandidate = false
    private var pinchOwned = false
    private var pinchCancelled = false
    private var videoZoom = 1f
    private var onZoom: (Float, Float, Float) -> Unit = { _, _, _ -> }
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector) = pinchOwned && zoomEnabled && !pinchCancelled
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (!zoomEnabled || pinchCancelled) return false
            val factor = detector.scaleFactor
            if (!factor.isFinite() || factor <= 0f || width <= 0 || height <= 0) return false
            videoZoom = (videoZoom * factor).coerceIn(1f, 8f)
            onZoom(videoZoom, (detector.focusX / width).coerceIn(0f, 1f),
                (detector.focusY / height).coerceIn(0f, 1f))
            return true
        }
    }).apply { isQuickScaleEnabled = false; isStylusScaleEnabled = false }

    internal fun configureZoom(enabled: Boolean, fullVideo: Boolean, key: String?,
                               onZoom: (Float, Float, Float) -> Unit) {
        this.onZoom = onZoom
        if (zoomEnabled != enabled || zoomFullVideo != fullVideo || zoomKey != key) resetVideoZoom()
        zoomEnabled = enabled
        zoomFullVideo = fullVideo
        zoomKey = key
    }

    private fun resetVideoZoom() {
        videoZoom = 1f
        if (pinchOwned) pinchCancelled = true
        onZoom(1f, .5f, .5f)
        zoomCandidate = false
        // An owned sequence stays consumed until UP; never give a leftover finger to drag.
    }

    private fun insideZoomVideo(x: Float, y: Float): Boolean {
        if (width <= 0 || height <= 0 || x < 0 || x >= width) return false
        val h = if (zoomFullVideo || !youtubeVideo) height.toFloat() else minOf(width * 9f / 16f, height.toFloat())
        return y >= 0f && y < h
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) { advanced?.cancel(); resetVideoZoom() }
    }

    /** The fallback is the measured full app content area, never the compact card's size. */
    internal fun setViewportPresentation(compact: Boolean, source: String?, videoWidth: Int, videoHeight: Int,
                                         normalWidth: Int, normalHeight: Int) {
        val sourceChanged = viewportSource != source
        val modeChanged = stableCompact != compact
        val oldWidth = logicalWidth
        val oldHeight = logicalHeight
        if (sourceChanged) {
            viewportSource = source
            logicalWidth = 0; logicalHeight = 0
        }
        if (compact && (logicalWidth <= 0 || logicalHeight <= 0) && normalWidth > 0 && normalHeight > 0) {
            logicalWidth = normalWidth; logicalHeight = normalHeight
        }
        val dimensionsChanged = mediaWidth != videoWidth || mediaHeight != videoHeight
        stableCompact = compact
        mediaWidth = videoWidth; mediaHeight = videoHeight
        if (modeChanged || sourceChanged || dimensionsChanged || oldWidth != logicalWidth || oldHeight != logicalHeight) {
            if (!compact) clearViewportTransform()
            requestLayout()
            if (modeChanged || sourceChanged) android.util.Log.i("WeiktubeViewport",
                "compact=$compact sourceChanged=$sourceChanged logical=${logicalWidth}x$logicalHeight")
        }
    }

    private fun clearViewportTransform() {
        getChildAt(0)?.apply { scaleX = 1f; scaleY = 1f; translationX = 0f; translationY = 0f }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (stableCompact) {
            // Do not call FrameLayout.onMeasure: it would first resize the retained WebView.
            setMeasuredDimension(resolveSize(suggestedMinimumWidth, widthMeasureSpec),
                resolveSize(suggestedMinimumHeight, heightMeasureSpec))
            if (logicalWidth > 0 && logicalHeight > 0)
                getChildAt(0)?.measure(MeasureSpec.makeMeasureSpec(logicalWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(logicalHeight, MeasureSpec.EXACTLY))
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            if (!stableCompact && measuredWidth > 0 && measuredHeight > 0) {
                logicalWidth = measuredWidth; logicalHeight = measuredHeight
            }
        }
    }
    private val replayInsets = Runnable {
        if (isAttachedToWindow) ViewCompat.getRootWindowInsets(this)?.let(::dispatchPlayerInsets)
    }

    fun attach(webView: WebView) {
        if (webView.parent === this) return
        check(childCount == 0) { "PlayerGestureLayout must retain exactly one player view" }
        (webView.parent as? ViewGroup)?.removeView(webView)
        addView(webView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        scheduleInsetsReplay()
    }

    /** Compose pads portrait/mini itself; landscape passes the screen's real safe area. */
    fun setSystemInsetsHandledByHost(handled: Boolean) {
        if (systemInsetsHandledByHost == handled) return
        systemInsetsHandledByHost = handled
        scheduleInsetsReplay()
    }

    internal fun dispatchPlayerInsets(insets: WindowInsetsCompat) {
        val web = getChildAt(0) as? WebView ?: return
        val forwarded = if (systemInsetsHandledByHost) {
            val handledTypes = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            WindowInsetsCompat.Builder(insets)
                .setInsets(handledTypes, Insets.NONE)
                .setInsetsIgnoringVisibility(handledTypes, Insets.NONE)
                .setDisplayCutout(null)
                .build()
        } else insets
        // Compose 1.7 consumes the portrait update before AndroidView receives it.
        // Explicit zeros clear the WebView's previous landscape safe-area values.
        // Dispatch only to this retained child, retaining default WebView overlap/IME handling.
        ViewCompat.dispatchApplyWindowInsets(web, forwarded)
    }

    private fun scheduleInsetsReplay() {
        removeCallbacks(replayInsets)
        postOnAnimation(replayInsets)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scheduleInsetsReplay()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        scheduleInsetsReplay()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (stableCompact && logicalWidth > 0 && logicalHeight > 0) {
            getChildAt(0)?.apply {
                layout(0, 0, logicalWidth, logicalHeight)
                pivotX = 0f; pivotY = 0f
                PlayerViewportTransform.fit(logicalWidth, logicalHeight, this@PlayerGestureLayout.width,
                    this@PlayerGestureLayout.height, mediaWidth, mediaHeight)?.let {
                    scaleX = it.scale; scaleY = it.scale; translationX = it.x; translationY = it.y
                }
            }
        } else if (!stableCompact) {
            clearViewportTransform()
            super.onLayout(changed, left, top, right, bottom)
        }
        if (changed) scheduleInsetsReplay()
    }

    fun configure(enabled: Boolean, youtube: Boolean, onDrag: (Float) -> Unit, onMinimize: () -> Unit) {
        this.onDrag = onDrag
        this.onMinimize = onMinimize
        if (gestureEnabled != enabled || youtubeVideo != youtube) reset()
        gestureEnabled = enabled
        youtubeVideo = youtube
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            reset()
            pinchOwned = false
            pinchCancelled = false
            zoomCandidate = zoomEnabled && insideZoomVideo(event.x, event.y)
            if (zoomCandidate) scaleDetector.onTouchEvent(event)
        }
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN && !pinchOwned && zoomCandidate && zoomEnabled &&
            (0 until event.pointerCount).all { insideZoomVideo(event.getX(it), event.getY(it)) }) {
            reset()
            advanced?.cancel()
            pinchOwned = true
            val cancel = MotionEvent.obtain(event)
            cancel.action = MotionEvent.ACTION_CANCEL
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        if (pinchOwned) {
            scaleDetector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                pinchOwned = false; zoomCandidate = false
                reset()
            }
            return true
        }
        if (advancedEnabled && advanced?.event(event, width, height) == true) return true
        if (zoomCandidate && event.actionMasked != MotionEvent.ACTION_DOWN) scaleDetector.onTouchEvent(event)
        if (event.actionMasked != MotionEvent.ACTION_DOWN) track(event)
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
        advanced?.cancel(); windowControls?.restore()
        removeCallbacks(replayInsets)
        reset()
        super.onDetachedFromWindow()
    }
}
