package com.weiktube.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlayerPlaybackDiagnosticsTest {
    private val token = "private-document-correlation"
    private fun payload() = JSONObject().put("token", token).put("event", "PAUSE_CALL").put("reason", "APP_COMMAND")
        .put("sequence", 1).put("elapsedMs", 20).put("video", true).put("paused", false)
        .put("connected", true).put("hidden", false).put("readyState", 4).put("mini", true)
        .put("pip", false).put("width", 196).put("height", 110)
        .put("muted", true).put("defaultMuted", false).put("volumePercent", 70)

    @Test fun acceptsTypedBoundedStateWithoutLoggingCorrelation() {
        val state = PlayerPlaybackDiagnostics.decode(payload().toString(), token)!!
        assertEquals(PlayerPlaybackDiagnostics.Event.PAUSE_CALL, state.event)
        assertEquals(PlayerPlaybackDiagnostics.Reason.APP_COMMAND, state.reason)
        val log = PlayerPlaybackDiagnostics.format(state)
        assertFalse(log.contains(token))
        assertFalse(log.contains("token"))
        assertEquals("event=PAUSE_CALL reason=APP_COMMAND seq=1 elapsedMs=20 video=true paused=false connected=true hidden=false ready=4 mini=true pip=false width=196 height=110 muted=true defaultMuted=false volumePercent=70", log)
    }

    @Test fun rejectsForeignTokenAdditionalDataAndOversizedMessages() {
        assertNull(PlayerPlaybackDiagnostics.decode(payload().toString(), "next-document"))
        assertNull(PlayerPlaybackDiagnostics.decode(payload().toString(), ""))
        assertNull(PlayerPlaybackDiagnostics.decode(" ".repeat(769), token))
        assertNull(PlayerPlaybackDiagnostics.decode(payload().put("url", "https://private.invalid").toString(), token))
        assertNull(PlayerPlaybackDiagnostics.decode(payload().put("stack", "secret").toString(), token))
        assertNull(PlayerPlaybackDiagnostics.decode("not-json", token))
    }

    @Test fun rejectsUnknownEventsReasonsAndMismatchedAttribution() {
        for (key in listOf("event", "reason")) {
            assertNull(PlayerPlaybackDiagnostics.decode(payload().put(key, "private page text").toString(), token))
            assertNull(PlayerPlaybackDiagnostics.decode(payload().put(key, 1).toString(), token))
        }
        assertNull(PlayerPlaybackDiagnostics.decode(payload().put("reason", "ENGINE_OR_UNOBSERVED").toString(), token))
        assertNull(PlayerPlaybackDiagnostics.decode(payload().put("event", "PLAY").toString(), token))
        assertNotNull(PlayerPlaybackDiagnostics.decode(payload().put("event", "PAUSE").put("reason", "ENGINE_OR_UNOBSERVED").toString(), token))
    }

    @Test fun rejectsCoercionAndOutOfRangeNumbers() {
        for (key in listOf("video", "paused", "connected", "hidden", "mini", "pip", "muted", "defaultMuted"))
            assertNull(PlayerPlaybackDiagnostics.decode(payload().put(key, "true").toString(), token))
        for (key in listOf("sequence", "elapsedMs", "readyState", "width", "height", "volumePercent")) {
            for (invalid in listOf(-1, 0.5, "3", JSONObject.NULL, 1e30))
                assertNull("$key=$invalid", PlayerPlaybackDiagnostics.decode(payload().put(key, invalid).toString(), token))
        }
        assertNull(PlayerPlaybackDiagnostics.decode(payload().put("sequence", 0).toString(), token))
        assertNull(PlayerPlaybackDiagnostics.decode(payload().put("readyState", 5).toString(), token))
        assertNull(PlayerPlaybackDiagnostics.decode(payload().put("width", 16385).toString(), token))
        assertNull(PlayerPlaybackDiagnostics.decode(payload().put("elapsedMs", 86_400_001).toString(), token))
        assertNull(PlayerPlaybackDiagnostics.decode(payload().put("volumePercent", 101).toString(), token))
    }

    @Test fun nativeRateLimitUsesRollingMinuteAndNeverExceeds120() {
        val rate = PlayerPlaybackDiagnostics.RateLimit()
        repeat(60) { assertTrue(rate.allow(0)) }
        repeat(60) { assertTrue(rate.allow(1000)) }
        repeat(1000) { assertFalse(rate.allow(59_999)) }
        repeat(60) { assertTrue(rate.allow(60_000)) }
        assertFalse(rate.allow(60_000))
        repeat(60) { assertTrue(rate.allow(61_000)) }
        assertFalse(rate.allow(61_000))
    }
}
