package com.weiktube.app

import java.util.concurrent.atomic.AtomicLong

class AccountSessionChanged : IllegalStateException("Die Google-Verbindung wurde geändert. Bitte den aktuellen Zustand prüfen und die Aktion bei Bedarf erneut ausführen.")

/** A result or write intent belongs to the grant from which it originated, never its replacement. */
class AccountSessionGuard {
    private val revision = AtomicLong(0)
    fun capture(): Long = revision.get()
    fun advance(): Long = revision.incrementAndGet()
    fun check(expected: Long) { if (capture() != expected) throw AccountSessionChanged() }
}

enum class NativeGrantDecision { FULL, READ_ONLY, NONE }

object NativeGrantPolicy {
    fun decide(granted: Set<String>, required: Set<String>, readScope: String, hasToken: Boolean): NativeGrantDecision = when {
        !hasToken || readScope !in granted -> NativeGrantDecision.NONE
        granted.containsAll(required) -> NativeGrantDecision.FULL
        else -> NativeGrantDecision.READ_ONLY
    }
}
