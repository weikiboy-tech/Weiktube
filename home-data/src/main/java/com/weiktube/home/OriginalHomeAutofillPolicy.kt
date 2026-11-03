package com.weiktube.home

import java.net.URI

/** Request eligibility only. Contains no field text, credentials, URLs or provider data. */
internal object OriginalHomeAutofillPolicy {
    enum class Result { READY, INACTIVE, WRONG_PAGE, DOCUMENT_LOADING, VIEW_UNAVAILABLE, UNSUPPORTED, DISABLED, NO_FIELD, STALE }

    data class Snapshot(
        val session: Any,
        val view: Any,
        val revision: Int,
        val active: Boolean,
        val authPage: Boolean,
        val documentReady: Boolean,
        val attached: Boolean,
        val shown: Boolean,
        val focused: Boolean,
        val geckoEnabled: Boolean,
        val supported: Boolean,
        val enabled: Boolean,
        val field: Any?,
        val fieldId: Int?,
        val usableBounds: Boolean,
    )

    fun isGoogleSignIn(raw: String): Boolean = OriginalHomePolicy.isNormalWebAuthUrl(raw) &&
        runCatching { URI(raw).host.equals("accounts.google.com", ignoreCase = true) }.getOrDefault(false)

    fun evaluate(value: Snapshot): Result = when {
        !value.active -> Result.INACTIVE
        !value.authPage -> Result.WRONG_PAGE
        !value.documentReady -> Result.DOCUMENT_LOADING
        !value.attached || !value.shown || !value.focused -> Result.VIEW_UNAVAILABLE
        !value.geckoEnabled || !value.supported -> Result.UNSUPPORTED
        !value.enabled -> Result.DISABLED
        value.field == null || value.fieldId == null || !value.usableBounds -> Result.NO_FIELD
        else -> Result.READY
    }

    fun revalidate(requested: Snapshot, current: Snapshot): Result {
        if (evaluate(requested) != Result.READY) return Result.STALE
        val eligibility = evaluate(current)
        if (eligibility != Result.READY) return eligibility
        return if (requested.session === current.session && requested.view === current.view &&
            requested.revision == current.revision && requested.field === current.field && requested.fieldId == current.fieldId)
            Result.READY else Result.STALE
    }
}
