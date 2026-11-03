package com.weiktube.app

import org.junit.Assert.*
import org.junit.Test

/** Golden baseline: controller refactoring must not silently turn Shields into a YouTube adblocker. */
class ShieldBaselineRegressionTest {
    @Test fun allYoutubeFamiliesExemptBothPagesAndTargetsEvenWithCustomRules() {
        val families = listOf("youtube.com", "youtu.be", "youtube-nocookie.com", "googlevideo.com", "ytimg.com", "com.weiktube.app")
        val engine = ShieldEngine().apply { config = ShieldConfig(custom = families.toSet() + "doubleclick.net") }
        for (domain in families) for (host in listOf(domain, "child.$domain")) {
            assertFalse("page=$host", engine.shouldBlock("https://doubleclick.net/ad", "https://$host/"))
            assertFalse("target=$host", engine.shouldBlock("https://$host/media", "https://news.example.org/"))
        }
        assertTrue(engine.shouldBlock("https://doubleclick.net/ad", "https://youtube.com.evil.org/"))
    }
    @Test fun mainFrameAndExceptionsWinWhileUnrelatedSubresourcesBlock() {
        val engine = ShieldEngine().apply { config = ShieldConfig(custom = setOf("blocked.example.org"), exceptions = setOf("allowed.example.org")) }
        val page = "https://site.example.org/"
        assertFalse(engine.shouldBlock("https://blocked.example.org/", page, true))
        assertTrue(engine.shouldBlock("https://child.blocked.example.org/banner", page))
        assertFalse(engine.shouldBlock("https://blocked.example.org/banner", "https://child.allowed.example.org/"))
        assertFalse(engine.shouldBlock("https://notblocked.example.org/banner", page))
        assertFalse(engine.shouldBlock("https://blocked.example.org.evil.test/banner", page))
    }
    @Test fun categorySwitchesAndDisabledEngineLeaveCountersUntouched() {
        val engine = ShieldEngine()
        engine.config = ShieldConfig(enabled = false, custom = setOf("blocked.example.org"))
        assertFalse(engine.intercept("https://blocked.example.org/a", "https://site.example.org", false))
        engine.config = ShieldConfig(ads = false, trackers = false)
        assertFalse(engine.intercept("https://doubleclick.net/a", "https://site.example.org", false))
        assertFalse(engine.intercept("https://google-analytics.com/a", "https://site.example.org", false))
        assertEquals(0L, engine.blocked.get())
    }
}
