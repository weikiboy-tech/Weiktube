package com.weiktube.home

/** A live document's native routing permission. Repeated readiness is not a new player permission. */
internal class OriginalHomeBridgeActivation {
    var token: String? = null
        private set
    var acknowledged = false
        private set
    private var suspended = true

    fun enable(newToken: () -> String): String {
        if (suspended || token == null) {
            token = newToken()
            acknowledged = false
            suspended = false
        }
        return requireNotNull(token)
    }

    /** Keep the retired token only so the matching document permission can be disabled. */
    fun suspend(): String? {
        suspended = true
        acknowledged = false
        return token
    }

    fun matches(candidate: String?): Boolean = !suspended && token != null && candidate == token

    fun acknowledge(candidate: String?): Boolean {
        if (!matches(candidate)) return false
        acknowledged = true
        return true
    }
}
