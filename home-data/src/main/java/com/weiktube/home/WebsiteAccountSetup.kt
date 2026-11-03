package com.weiktube.home

/** Presentation/navigation state only. No state here asserts that a website account is signed in. */
internal class WebsiteAccountSetup(
    private val wasPresented: () -> Boolean,
    private val persistPresented: () -> Unit,
) {
    var active = false
        private set
    var automatic = false
        private set
    private var issued = false
    private var authRequested = false
    private var freshDocument = false

    fun offerAutomatically(eligible: Boolean): Boolean =
        eligible && !wasPresented() && begin(automatic = true)

    fun begin(automatic: Boolean = false): Boolean {
        if (active) return false
        active = true
        this.automatic = automatic
        issued = false; authRequested = false; freshDocument = false
        return true
    }

    /** Called only after the strip is visible, or an explicit user dismissal of that strip. */
    fun presented() { if (active && !wasPresented()) persistPresented() }

    fun loadIssued() {
        if (!active) return
        issued = true; authRequested = false; freshDocument = false
    }

    fun requestObserved(url: String) {
        if (active && issued && OriginalHomePolicy.isNormalWebAuthUrl(url)) authRequested = true
    }

    fun pageStarted(url: String) {
        if (!active || !issued || url == "about:blank") return
        requestObserved(url)
        if (authRequested) freshDocument = true
    }

    /** An old Home callback while the account target is queued is not a completed account journey. */
    fun locationChanged(url: String) {
        if (!active || !issued) return
        if (OriginalHomePolicy.isNormalWebAuthUrl(url)) {
            authRequested = true
            freshDocument = true // Account navigation can also be same-document.
        } else if (authRequested && freshDocument && OriginalHomePolicy.isYoutubePage(url) &&
            !OriginalHomePolicy.isVideoNavigation(url)) finish()
    }

    fun dismiss() { presented(); finish() }

    fun finish() {
        active = false; automatic = false
        issued = false; authRequested = false; freshDocument = false
    }

    companion object {
        const val ACCOUNT_URL = "https://www.youtube.com/channel_switcher?next=%2F"
    }
}
