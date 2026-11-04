package com.weiktube.app

import kotlin.math.abs

/** Pure, single-pointer decision state. Coordinates are screen pixels, not moving-view pixels. */
internal class PlayerDragGesture(
    private val slop: Float,
    private val distanceThreshold: Float,
    private val flingDistance: Float,
    private val flingVelocity: Float,
) {
    private enum class State { IDLE, CANDIDATE, DRAGGING, REJECTED }
    private var state = State.IDLE
    private var startX = 0f
    private var startY = 0f
    var distance = 0f
        private set
    val candidate get() = state == State.CANDIDATE
    val dragging get() = state == State.DRAGGING

    fun begin(eligible: Boolean, rawX: Float, rawY: Float) {
        cancel()
        startX = rawX
        startY = rawY
        state = if (eligible) State.CANDIDATE else State.REJECTED
    }

    fun move(rawX: Float, rawY: Float, pointers: Int = 1): Boolean {
        if (pointers != 1) { cancel(); return false }
        val dx = rawX - startX
        val dy = rawY - startY
        if (candidate && (abs(dx) > slop || abs(dy) > slop)) {
            state = if (dy > slop && dy > abs(dx) * 1.25f) State.DRAGGING else State.REJECTED
        }
        if (dragging) distance = dy.coerceAtLeast(0f)
        return dragging
    }

    fun finish(rawX: Float, rawY: Float, velocityY: Float): Boolean {
        if (dragging) move(rawX, rawY)
        val minimize = dragging && (distance >= distanceThreshold ||
            (distance >= flingDistance && velocityY >= flingVelocity))
        cancel()
        return minimize
    }

    fun cancel() { state = State.IDLE; distance = 0f }

    companion object {
        fun insideVideo(localY: Float, width: Float, height: Float, density: Float, youtube: Boolean): Boolean {
            val videoHeight = minOf(width * 9f / 16f, height)
            val top = if (youtube) 0f else (height - videoHeight) / 2f
            // Leave the top controls and bottom seek bar to the original web player.
            return videoHeight > 56f * density && localY >= top + 20f * density &&
                localY < top + videoHeight - 36f * density
        }
    }
}
