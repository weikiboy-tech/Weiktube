package com.weiktube.app

/** Safe even while close/retry clears position and duration before Compose removes the indicator. */
internal fun playerProgress(position: Double, duration: Double): Float {
    if (!position.isFinite() || !duration.isFinite() || duration <= 0.0 || position <= 0.0) return 0f
    // Compare before dividing so extreme but finite values cannot overflow the ratio.
    if (position >= duration) return 1f
    return (position / duration).toFloat().coerceIn(0f, 1f)
}
