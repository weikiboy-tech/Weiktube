package com.weiktube.app

import org.junit.Assert.*
import org.junit.Test

class ShieldEngineTest {
    private val engine = ShieldEngine()
    private val site = "https://news.example.org/"
    @Test fun blocksAdsAndTheirSubdomains() {
        assertTrue(engine.shouldBlock("https://a.doubleclick.net/ad", site))
        assertTrue(engine.shouldBlock("https://google-analytics.com/collect", site))
    }
    @Test fun domainBoundaryCannotMatchUnrelatedSite() {
        assertFalse(engine.shouldBlock("https://notdoubleclick.net/ad", site))
        assertFalse(engine.shouldBlock("https://doubleclick.net.attacker.org/ad", site))
    }
    @Test fun blocksThirdPartyAdsOnYoutubeWithoutBlanketCdnBlocking() {
        assertTrue(engine.shouldBlock("https://doubleclick.net/ad", "https://m.youtube.com/watch?v=abcdefghijk"))
        assertTrue(engine.shouldBlock("https://doubleclick.net/ad", "https://com.weiktube.app/"))
        engine.config = ShieldConfig(custom = setOf("googlevideo.com"))
        assertFalse(engine.shouldBlock("https://r1.googlevideo.com/videoplayback", site))
    }
    @Test fun enabledAndCategoriesWorkIndependently() {
        engine.config = ShieldConfig(enabled = false)
        assertFalse(engine.shouldBlock("https://doubleclick.net/ad", site))
        engine.config = ShieldConfig(ads = false)
        assertFalse(engine.shouldBlock("https://doubleclick.net/ad", site))
        assertTrue(engine.shouldBlock("https://google-analytics.com/collect", site))
    }
    @Test fun exceptionsApplyToSiteOrRequestedDomain() {
        engine.config = ShieldConfig(exceptions = setOf("example.org"))
        assertFalse(engine.shouldBlock("https://doubleclick.net/ad", site))
        engine.config = ShieldConfig(exceptions = setOf("doubleclick.net"))
        assertFalse(engine.shouldBlock("https://a.doubleclick.net/ad", site))
    }
    @Test fun mainDocumentsAreNeverBlocked() {
        assertFalse(engine.shouldBlock("https://doubleclick.net/", site, true))
    }
    @Test fun parsesHostsAndComments() {
        assertEquals(setOf("ads.example.org", "track.example.org"), ShieldEngine.parse("# comment\n0.0.0.0 ADS.example.org track.example.org # inline\n127.0.0.1 localhost\n"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnsupportedFilterSyntax() { ShieldEngine.parse("||doubleclick.net^") }
    @Test(expected = IllegalArgumentException::class) fun rejectsOversizedLists() { ShieldEngine.parse("a".repeat(262145)) }
    @Test fun counterOnlyCountsActualBlockingDecisions() {
        engine.intercept("https://doubleclick.net/ad", site, false)
        engine.intercept("https://example.org/logo.png", site, false)
        assertEquals(1L, engine.blocked.get())
    }
    @Test fun customListsBlockAndExceptionsWin() {
        engine.config = ShieldConfig(custom = setOf("custom.example.org"))
        assertTrue(engine.shouldBlock("https://a.custom.example.org/x", site))
        engine.config = engine.config.copy(exceptions = setOf("custom.example.org"))
        assertFalse(engine.shouldBlock("https://a.custom.example.org/x", site))
    }
}
