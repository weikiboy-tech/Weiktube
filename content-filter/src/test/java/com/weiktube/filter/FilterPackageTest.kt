package com.weiktube.filter

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.URL
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class FilterPackageTest {
    private fun assets(): File = listOf(File("src/main/assets/weiktube-adblock"),
        File("content-filter/src/main/assets/weiktube-adblock")).first { File(it, "rules.json").isFile }
    private fun bytes() = File(assets(), "rules.json").readBytes()
    private fun fixture() = FilterSnapshot.parse(bytes())
    private fun changed(block: (JSONObject) -> Unit): ByteArray = JSONObject(bytes().toString(Charsets.UTF_8))
        .also(block).toString().toByteArray()
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Invalid package was accepted") } catch (_: IllegalArgumentException) { }
    }

    @Test fun bundledPackageVerifiesWithBundledPublicKey() {
        val key = SignedFilterPackage.publicKey(File(assets(), "update-public-key.txt").readText())
        val snapshot = SignedFilterPackage.verify(bytes(), File(assets(), "rules.sig").readText(), key)
        assertEquals(1, snapshot.version)
        assertTrue(snapshot.networkRules.isNotEmpty())
        assertTrue(snapshot.scriptlets.any { it.name == "json-prune-fetch-response" })
        assertEquals(snapshot, FilterSnapshot.parse(snapshot.toJson().toString().toByteArray()))
    }

    @Test fun modifiedBytesCannotReuseValidSignature() {
        val key = SignedFilterPackage.publicKey(File(assets(), "update-public-key.txt").readText())
        rejected { SignedFilterPackage.verify(changed { it.put("version", 2) }, File(assets(), "rules.sig").readText(), key) }
    }

    @Test fun anotherKeyCannotSignAnUpdate() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val signature = Signature.getInstance("SHA256withRSA").apply { initSign(pair.private); update(bytes()) }.sign()
        val key = SignedFilterPackage.publicKey(File(assets(), "update-public-key.txt").readText())
        rejected { SignedFilterPackage.verify(bytes(), Base64.getEncoder().encodeToString(signature), key) }
    }

    @Test fun olderSignedPackageCannotReplaceNewerVersion() {
        val key = SignedFilterPackage.publicKey(File(assets(), "update-public-key.txt").readText())
        rejected { SignedFilterPackage.verify(bytes(), File(assets(), "rules.sig").readText(), key, minimumVersion = 2) }
    }

    @Test fun unknownExecutableFieldsAreRejected() {
        rejected { FilterSnapshot.parse(changed { it.put("javascript", "alert(1)") }) }
        rejected { FilterSnapshot.parse(changed { it.getJSONArray("scriptlets").getJSONObject(0).put("code", "alert(1)") }) }
    }

    @Test fun onlyBundledScriptletsAndSupportedConstantsAreAccepted() {
        rejected { FilterSnapshot.parse(changed { it.getJSONArray("scriptlets").getJSONObject(0).put("name", "trusted-create-element") }) }
        rejected { FilterSnapshot.parse(changed { it.getJSONArray("scriptlets").getJSONObject(0).put("args", JSONArray(listOf("location", "undefined"))) }) }
        rejected { FilterSnapshot.parse(changed { it.getJSONArray("scriptlets").getJSONObject(0).put("args", JSONArray(listOf("ytInitialPlayerResponse.adSlots", "true"))) }) }
    }

    @Test fun duplicateRuleIdentitiesAreRejected() {
        rejected { FilterSnapshot.parse(changed { it.getJSONArray("networkRules").getJSONObject(1).put("id", "network-1") }) }
    }

    @Test fun arbitraryCdnAndWholeYoutubeBlocksAreRejected() {
        rejected { FilterSnapshot.parse(changed { it.getJSONArray("networkRules").getJSONObject(0).put("host", "googlevideo.com") }) }
        rejected { FilterSnapshot.parse(changed { it.getJSONArray("networkRules").getJSONObject(0).put("host", "youtube.com") }) }
        rejected { FilterSnapshot.parse(changed { it.getJSONArray("networkRules").getJSONObject(0).put("host", "accounts.google.com") }) }
    }

    @Test fun unsafeOrUnsupportedCosmeticSelectorsAreRejected() {
        for (selector in listOf("body", "video", "ytm-ad-slot-renderer {display:none}", "*", "html:has(ytm-ad-slot-renderer)",
            ".ad-class", "ad-invented-renderer", "ytm-rich-item-renderer:has(ytm-ad-slot-renderer)")) {
            rejected { FilterSnapshot.parse(changed { it.put("cosmeticSelectors", JSONArray(listOf(selector))) }) }
        }
    }

    @Test fun everyRuntimeSupportedCosmeticSelectorIsAccepted() {
        for (prefix in listOf("ytd", "ytm")) for (renderer in listOf("ad-slot", "display-ad", "in-feed-ad-layout", "promoted-video",
            "promoted-sparkles-web", "promoted-sparkles-text-search", "search-pyv")) {
            val selector = "$prefix-$renderer-renderer"
            val rules = FilterSnapshot.parse(changed { it.put("cosmeticSelectors", JSONArray(listOf(selector))) })
            assertEquals(listOf(selector), rules.cosmeticSelectors)
        }
    }

    @Test fun rejectsUnsupportedSchemaAndOversizedPackage() {
        rejected { FilterSnapshot.parse(changed { it.put("schemaVersion", 2) }) }
        rejected { FilterSnapshot.parse(changed { it.put("version", 1.5) }) }
        rejected { FilterSnapshot.parse(changed { it.put("version", "1") }) }
        rejected { FilterSnapshot.parse(ByteArray(FilterSnapshot.MAX_BYTES + 1)) }
    }

    @Test fun pruneRulesCannotChangeContentOutsideReviewedAdFieldsOrRequestPatterns() {
        rejected { FilterSnapshot.parse(changed { it.getJSONArray("scriptlets").getJSONObject(5).put("args", JSONArray(listOf("streamingData"))) }) }
        for (pattern in listOf("url:/[/", "url:/.*/", "url:https://accounts.google.com/")) {
            rejected { FilterSnapshot.parse(changed {
                it.getJSONArray("scriptlets").getJSONObject(6).getJSONArray("args").put(2, pattern)
            }) }
        }
    }

    @Test fun updateRedirectPolicyAllowsOnlyKnownHttpsDownloadHosts() {
        for (host in listOf("github.com", "raw.githubusercontent.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com"))
            assertTrue(FilterUpdatePolicy.trusted(URL("https://$host/asset")))
        for (url in listOf("http://github.com/asset", "https://github.com.evil.example/asset", "https://evil.github.com/asset",
            "https://accounts.google.com/asset", "https://token@github.com/asset", "https://github.com:8443/asset"))
            assertFalse(url, FilterUpdatePolicy.trusted(URL(url)))
    }

    @Test fun unknownLengthUpdateBodiesAreStillBounded() {
        val body = ByteArray(8193) { 17 }
        assertArrayEquals(body, FilterUpdatePolicy.readLimited(body.inputStream(), 8193))
        rejected { FilterUpdatePolicy.readLimited(body.inputStream(), 8192) }
    }

    @Test fun blocksAdHostsOnlyWithinYoutubeSubresources() {
        val rules = fixture()
        assertTrue(FilterNetwork.blocked(rules, "https://googleads.g.doubleclick.net/pagead/x", "https://m.youtube.com/watch?v=abcdefghijk", false))
        assertTrue(FilterNetwork.blocked(rules, "https://www.youtube.com/pagead/adview", "https://www.youtube.com/", false))
        assertFalse(FilterNetwork.blocked(rules, "https://doubleclick.net/ad", "https://news.example.org/", false))
        assertFalse(FilterNetwork.blocked(rules, "https://doubleclick.net/", "https://www.youtube.com/", true))
    }

    @Test fun domainBoundariesAndCredentialsCannotMatch() {
        val rules = fixture()
        for (url in listOf("https://notdoubleclick.net/ad", "https://doubleclick.net.evil.example/ad",
            "https://secret@doubleclick.net/ad", "http://doubleclick.net/ad", "https://doubleclick.net:8443/ad"))
            assertFalse(url, FilterNetwork.blocked(rules, url, "https://m.youtube.com/", false))
        assertFalse(FilterNetwork.blocked(rules, "https://doubleclick.net/ad", "https://youtube.com.evil.example/", false))
    }

    @Test fun mediaImagesCaptionsApiAndAuthenticationArePreserved() {
        val rules = fixture()
        for (url in listOf("https://r1.googlevideo.com/videoplayback", "https://i.ytimg.com/vi/id/hqdefault.jpg",
            "https://www.youtube.com/youtubei/v1/player", "https://www.youtube.com/api/timedtext?v=id",
            "https://accounts.google.com/ServiceLogin", "https://consent.youtube.com/m"))
            assertFalse(url, FilterNetwork.blocked(rules, url, "https://m.youtube.com/", false))
        assertFalse(FilterNetwork.blocked(rules, "https://doubleclick.net/ad", "https://consent.youtube.com/m", false))
        assertFalse(FilterNetwork.blocked(rules, "https://doubleclick.net/ad", "https://accounts.google.com/", false))
    }

    @Test fun explicitDisableAndExceptionsWin() {
        val rules = fixture(); val ad = "https://googleads.g.doubleclick.net/ad"; val page = "https://m.youtube.com/"
        assertFalse(FilterNetwork.blocked(rules, ad, page, false, enabled = false))
        assertFalse(FilterNetwork.blocked(rules, ad, page, false, exceptions = setOf("youtube.com")))
        assertFalse(FilterNetwork.blocked(rules, ad, page, false, exceptions = setOf("doubleclick.net")))
        assertTrue(FilterNetwork.blocked(rules, ad, page, false, exceptions = setOf("notyoutube.com")))
    }

    @Test fun accountAndOauthPagesCannotActivateFiltering() {
        val rules = fixture()
        for (path in listOf("/signin", "/signin?next=home", "/signin/v2", "/login", "/ServiceLogin", "/SIGNIN", "/logout", "/account", "/account_switcher",
            "/account/advanced", "/o/oauth2/auth", "/o/oauth", "/o/oauth/v2/auth", "/%73ignin")) {
            val page = "https://m.youtube.com$path"
            assertFalse(path, FilterNetwork.enabledFor(page, true, emptySet()))
            assertFalse(path, FilterNetwork.blocked(rules, "https://doubleclick.net/ad", page, false))
        }
        assertTrue(FilterNetwork.enabledFor("https://m.youtube.com/results?search_query=account", true, emptySet()))
    }

    @Test fun dataAndNetworkCountersAreDistinctAndNeverCountAllowedRequests() {
        YoutubeAdBlock.resetCounters()
        YoutubeAdBlock.shouldBlock(fixture(), "https://r1.googlevideo.com/videoplayback", "https://m.youtube.com/", false)
        assertEquals(0, YoutubeAdBlock.state.value.networkBlocked)
        YoutubeAdBlock.shouldBlock(fixture(), "https://doubleclick.net/ad", "https://m.youtube.com/", false)
        YoutubeAdBlock.report("data", 3); YoutubeAdBlock.report("cosmetic", 2)
        YoutubeAdBlock.report("cookie", 9); YoutubeAdBlock.report("network", -1)
        YoutubeAdBlock.report("network", 10001)
        assertEquals(1, YoutubeAdBlock.state.value.networkBlocked)
        assertEquals(3, YoutubeAdBlock.state.value.dataRemoved)
        assertEquals(2, YoutubeAdBlock.state.value.cosmeticHidden)
        YoutubeAdBlock.resetCounters()
    }
}
