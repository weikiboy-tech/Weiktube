package com.weiktube.app

/** Only opens GET reloads; one automatic restart per user operation prevents token-refresh loops. */
class VideoReadReloadGate {
    private var automaticRestarts = 0
    fun userOperation() { automaticRestarts = 0 }
    fun take(visible: Boolean, connected: Boolean, accountBusy: Boolean, operationBusy: Boolean, initial: Boolean, changed: Boolean): Boolean {
        if (!visible || !connected || accountBusy || operationBusy || (!initial && !changed)) return false
        if (!initial && automaticRestarts >= 1) return false
        if (!initial) automaticRestarts++
        return true
    }
}
