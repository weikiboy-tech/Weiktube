package com.weiktube.home

/** A hidden retained tab may finish its already allowed YouTube page, never start web playback/auth. */
internal object OriginalHomeTabBackground {
    private fun page(raw: String): Boolean = OriginalHomePolicy.isYoutubePage(raw) &&
        !OriginalHomePolicy.isVideoNavigation(raw) && !OriginalHomePolicy.isExplicitOAuthUrl(raw)

    fun navigation(parent: String, target: String, nativeTarget: String? = null): Boolean =
        page(target) && (page(parent) ||
            parent in setOf("", "about:blank") && nativeTarget != null && page(nativeTarget))
}
