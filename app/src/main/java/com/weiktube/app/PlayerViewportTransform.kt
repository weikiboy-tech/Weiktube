package com.weiktube.app

/** Maps the actual contain-fitted video in a retained document onto its native viewport. */
internal data class PlayerViewportTransform(val scale: Float, val x: Float, val y: Float) {
    companion object {
        fun fit(documentWidth: Int, documentHeight: Int, targetWidth: Int, targetHeight: Int,
                videoWidth: Int, videoHeight: Int): PlayerViewportTransform? {
            if (documentWidth <= 0 || documentHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) return null
            val aspect = if (videoWidth > 0 && videoHeight > 0) videoWidth.toDouble() / videoHeight else 16.0 / 9.0
            val mediaWidth = minOf(documentWidth.toDouble(), documentHeight * aspect)
            val mediaHeight = mediaWidth / aspect
            val left = (documentWidth - mediaWidth) / 2
            val top = (documentHeight - mediaHeight) / 2
            val scale = minOf(targetWidth / mediaWidth, targetHeight / mediaHeight)
            return PlayerViewportTransform(scale.toFloat(),
                ((targetWidth - mediaWidth * scale) / 2 - left * scale).toFloat(),
                ((targetHeight - mediaHeight * scale) / 2 - top * scale).toFloat())
        }
    }
}
