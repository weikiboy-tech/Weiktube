package com.weiktube.home

/** Gecko closes a session before reporting either content crash or content kill. */
internal enum class HomeContentLoss(val diagnostic: String) {
    CRASHED("CONTENT_CRASHED"),
    KILLED("CONTENT_KILLED"),
    CLOSED("CONTENT_CLOSED"),
}

/** Release only the document this host still owns; late events cannot close its replacement. */
internal class HomeContentRecovery<S>(
    private val owns: (S) -> Boolean,
    private val release: () -> Unit,
    private val report: (HomeContentLoss) -> Unit,
) {
    fun lost(session: S, cause: HomeContentLoss): Boolean {
        if (!owns(session)) return false
        release()
        report(cause)
        return true
    }
}
