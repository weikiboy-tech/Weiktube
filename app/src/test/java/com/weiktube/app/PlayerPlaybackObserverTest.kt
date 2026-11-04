package com.weiktube.app

import org.junit.Assert.*
import org.junit.Test

class PlayerPlaybackObserverTest {
    private val token = "current-document"
    private fun payload(playing: String = "true", width: String = "1920", height: String = "1080") =
        """{"token":"$token","playing":$playing,"width":$width,"height":$height}"""

    @Test fun acceptsOnlyBoundedTypedPlaybackState() {
        assertEquals(PlayerPlaybackObserver.State(true, 1920, 1080), PlayerPlaybackObserver.decode(payload(), token))
        assertEquals(PlayerPlaybackObserver.State(false, 0, 16384), PlayerPlaybackObserver.decode(payload("false", "0", "16384"), token))
    }

    @Test fun rejectsPreviousDocumentAndMissingCorrelation() {
        assertNull(PlayerPlaybackObserver.decode(payload(), "next-document"))
        assertNull(PlayerPlaybackObserver.decode(payload(), ""))
        assertNull(PlayerPlaybackObserver.decode("""{"playing":true,"width":1920,"height":1080}""", token))
    }

    @Test fun rejectsMalformedOversizedAndUnrelatedData() {
        assertNull(PlayerPlaybackObserver.decode("not-json", token))
        assertNull(PlayerPlaybackObserver.decode(" ".repeat(513) + payload(), token))
        assertNull(PlayerPlaybackObserver.decode(payload().dropLast(1) + ""","url":"https://example.org/"}""", token))
        assertNull(PlayerPlaybackObserver.decode(payload("\"true\""), token))
        listOf("-1", "16385", "1.5", "1e100", "null", "\"1920\"").forEach { invalid ->
            assertNull(invalid, PlayerPlaybackObserver.decode(payload(width = invalid), token))
            assertNull(invalid, PlayerPlaybackObserver.decode(payload(height = invalid), token))
        }
    }
}
