package com.weiktube.home

/** A later confirmation cannot authorize an asynchronous operation from an earlier observation. */
internal class OriginalHomeApproval {
    private var revision = 0L
    var confirmed: Boolean = false
        private set
    data class Ticket internal constructor(internal val revision: Long)

    fun setConfirmed(value: Boolean) {
        if (!value) revision++
        confirmed = value
    }
    fun begin(): Ticket? = if (confirmed) Ticket(revision) else null
    fun permits(ticket: Ticket): Boolean = confirmed && ticket.revision == revision
}
