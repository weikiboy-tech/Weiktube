package com.weiktube.home

/** Only a previously visible first-party sign-in may finish while an authenticator is foreground. */
internal object OriginalHomeBackground {
    fun navigation(raw: String): Boolean = !OriginalHomePolicy.isExplicitOAuthUrl(raw) &&
        (OriginalHomePolicy.isNormalWebAuthUrl(raw) || OriginalHomePolicy.isHomePage(raw))

    fun subframe(parent: String, raw: String): Boolean = OriginalHomePolicy.isNormalWebAuthUrl(parent) &&
        !OriginalHomePolicy.isExplicitOAuthUrl(raw) &&
        (OriginalHomePolicy.isNormalWebAuthUrl(raw) || OriginalHomePolicy.allowedAuthSubframe(raw))
}
