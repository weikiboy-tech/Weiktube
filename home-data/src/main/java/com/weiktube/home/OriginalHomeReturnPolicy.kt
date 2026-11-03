package com.weiktube.home

/** One selected video, one activity result, one return. Contains no account or website data. */
internal class OriginalHomeReturnPolicy(private val token: String, private val video: String) {
    private enum class Phase { PARKED, IN_PLAYER, TAKEN, DISCARDED }
    private var phase = Phase.PARKED
    fun accept(candidate: String?, canonicalVideo: String?): Boolean {
        if (phase != Phase.PARKED || candidate != token || canonicalVideo != video) return false
        phase = Phase.IN_PLAYER
        return true
    }
    fun resumeToken(): String? = token.takeIf { phase == Phase.IN_PLAYER }
    fun take(candidate: String?): Boolean {
        if (phase != Phase.IN_PLAYER || candidate != token) return false
        phase = Phase.TAKEN
        return true
    }
    fun discard() { phase = Phase.DISCARDED }
}
