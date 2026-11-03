package com.weiktube.home

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HomeFeedDataTest {
    private val channel = "UCabcdefghijklmnopqrstuv"
    private fun video(id: String, title: String = id): JSONObject = JSONObject().put("videoRenderer", JSONObject()
        .put("videoId", id).put("title", JSONObject().put("runs", JSONArray().put(JSONObject().put("text", title))))
        .put("thumbnail", JSONObject().put("thumbnails", JSONArray().put(JSONObject().put("url", "https://i.ytimg.com/vi/$id/hqdefault.jpg").put("width", 480))))
        .put("ownerText", JSONObject().put("runs", JSONArray().put(JSONObject().put("text", "The channel").put("navigationEndpoint", JSONObject().put("browseEndpoint", JSONObject().put("browseId", channel))))))
        .put("lengthText", JSONObject().put("simpleText", "12:34")))
    private fun page(vararg items: JSONObject) = JSONObject().put("contents", JSONObject().put("richGridRenderer", JSONObject().put("contents", JSONArray(items.toList()))))
    private fun more(token: String) = JSONObject().put("continuationItemRenderer", JSONObject().put("continuationEndpoint", JSONObject().put("continuationCommand", JSONObject().put("token", token))))

    @Test fun parsesServerOrderThroughRichRowsAndPreservesRealMetadata() {
        val nested = JSONObject().put("richItemRenderer", JSONObject().put("content", video("abcdefghijk", "Title")))
        val response = page(nested, video("0123456789_"), more("opaque-next"))
        val result = HomeFeedData.parse(response)
        assertEquals(listOf("abcdefghijk", "0123456789_"), result.videos.map { it.id })
        assertEquals("Title", result.videos.first().title)
        assertEquals(channel, result.videos.first().channelId)
        assertEquals("The channel", result.videos.first().channelTitle)
        assertEquals("12:34", result.videos.first().durationText)
        assertEquals("opaque-next", result.continuation)
    }

    @Test fun continuationMergesOverlapsWithoutSortingOrMovingEarlierRows() {
        val first = HomeFeedData.parse(page(video("abcdefghijk"), video("0123456789_"))).videos
        val raw = JSONObject().put("onResponseReceivedActions", JSONArray().put(JSONObject().put("appendContinuationItemsAction",
            JSONObject().put("continuationItems", JSONArray().put(video("0123456789_", "Changed title")).put(video("ZYXWVUTSRQP")).put(more("last"))))))
        val next = HomeFeedData.parse(raw)
        val all = HomeFeedData.merge(first, next.videos)
        assertEquals(listOf("abcdefghijk", "0123456789_", "ZYXWVUTSRQP"), all.map { it.id })
        assertEquals("0123456789_", all[1].title)
        assertEquals("last", next.continuation)
    }

    @Test fun ignoresAdsSidebarsMenusAndUnknownContainers() {
        val ad = JSONObject().put("adSlotRenderer", JSONObject().put("content", video("aaaaaaaaaaa")))
        val mixed = video("bbbbbbbbbbb").put("adInfo", JSONObject())
        val fakeUnknown = JSONObject().put("unknownRenderer", JSONObject().put("contents", JSONArray().put(video("ccccccccccc"))))
        val raw = page(ad, mixed, fakeUnknown, video("abcdefghijk"))
            .put("sidebar", JSONObject().put("contents", JSONArray().put(video("ddddddddddd"))))
            .put("menu", video("eeeeeeeeeee"))
        assertEquals(listOf("abcdefghijk"), HomeFeedData.parse(raw).videos.map { it.id })
    }

    @Test fun selectedTabOnlyIsParsed() {
        fun tab(selected: Boolean, id: String) = JSONObject().put("tabRenderer", JSONObject().put("selected", selected)
            .put("content", page(video(id)).getJSONObject("contents")))
        val raw = JSONObject().put("contents", JSONObject().put("twoColumnBrowseResultsRenderer", JSONObject()
            .put("tabs", JSONArray().put(tab(false, "aaaaaaaaaaa")).put(tab(true, "abcdefghijk")))))
        assertEquals(listOf("abcdefghijk"), HomeFeedData.parse(raw).videos.map { it.id })
    }

    @Test fun malformedOrMismatchedVideoIdsNeverBecomePlayableCards() {
        val mismatch = video("abcdefghijk").apply { getJSONObject("videoRenderer").put("navigationEndpoint", JSONObject().put("watchEndpoint", JSONObject().put("videoId", "0123456789_"))) }
        assertTrue(HomeFeedData.parse(page(video("short"), video("bad<script>"), video("0123456789_", ""), mismatch)).videos.isEmpty())
        assertTrue(HomeFeedData.parse(JSONObject().put("contents", "invalid")).videos.isEmpty())
        assertTrue(HomeFeedData.parse(JSONObject().put("random", video("abcdefghijk"))).videos.isEmpty())
    }

    @Test fun invalidThumbnailUrlsAreNotInventedOrForwarded() {
        for (url in listOf("http://i.ytimg.com/image", "https://i.ytimg.com.evil.test/image", "https://evil.test@i.ytimg.com/image", "https://i.ytimg.com:444/image", "file:///image", "https://127.0.0.1/image", "https://i.ytimg.com/image#fragment")) {
            val node = video("abcdefghijk")
            node.getJSONObject("videoRenderer").put("thumbnail", JSONObject().put("thumbnails", JSONArray().put(JSONObject().put("url", url))))
            assertEquals(url, "", HomeFeedData.parse(page(node)).videos.single().thumbnailUrl)
        }
        val node = video("abcdefghijk")
        node.getJSONObject("videoRenderer").getJSONObject("thumbnail").getJSONArray("thumbnails")
            .put(JSONObject().put("url", "https://i.ytimg.com/actual-higher.jpg").put("width", 1280))
            .put(JSONObject().put("url", "https://evil.test/image").put("width", 9999))
        assertEquals("https://i.ytimg.com/actual-higher.jpg", HomeFeedData.parse(page(node)).videos.single().thumbnailUrl)
    }

    @Test fun invalidChannelIdIsEmptyRatherThanGuessedFromName() {
        val node = video("abcdefghijk")
        node.getJSONObject("videoRenderer").getJSONObject("ownerText").getJSONArray("runs").getJSONObject(0)
            .getJSONObject("navigationEndpoint").getJSONObject("browseEndpoint").put("browseId", "FEtrending")
        val result = HomeFeedData.parse(page(node)).videos.single()
        assertEquals("", result.channelId)
        assertEquals("The channel", result.channelTitle)
    }

    @Test fun rejectsOversizedAndControlCharacterContinuations() {
        assertNull(HomeFeedData.parse(page(more("a".repeat(16_385)))).continuation)
        assertNull(HomeFeedData.parse(page(more("abc\nxyz"))).continuation)
        assertNull(HomeFeedData.parse(page(more(""))).continuation)
    }

    @Test fun handlesLegacyContinuationAndMobileRenderer() {
        val mobile = video("abcdefghijk").getJSONObject("videoRenderer")
        mobile.put("headline", mobile.remove("title"))
        val response = JSONObject().put("continuationContents", JSONObject().put("sectionListContinuation", JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("itemSectionRenderer", JSONObject().put("contents", JSONArray().put(JSONObject().put("videoWithContextRenderer", mobile))))))
            .put("continuations", JSONArray().put(JSONObject().put("nextContinuationData", JSONObject().put("continuation", "mobile-next"))))))
        assertEquals("abcdefghijk", HomeFeedData.parse(response).videos.single().id)
        assertEquals("mobile-next", HomeFeedData.parse(response).continuation)
    }

    @Test fun handlesModernVideoLockupWithoutTreatingPlaylistAsVideo() {
        val attributedAuthor = JSONObject().put("content", "Creator").put("commandRuns", JSONArray().put(JSONObject().put("startIndex", 0).put("length", 7)
            .put("onTap", JSONObject().put("innertubeCommand", JSONObject().put("browseEndpoint", JSONObject().put("browseId", channel))))))
        val lockup = JSONObject().put("contentId", "abcdefghijk").put("contentType", "LOCKUP_CONTENT_TYPE_VIDEO")
            .put("metadata", JSONObject().put("lockupMetadataViewModel", JSONObject().put("title", JSONObject().put("content", "New title"))
                .put("metadata", JSONObject().put("contentMetadataViewModel", JSONObject().put("metadataRows", JSONArray().put(JSONObject().put("metadataParts", JSONArray()
                    .put(JSONObject().put("text", attributedAuthor)).put(JSONObject().put("text", JSONObject().put("content", "42 views"))))))))))
            .put("contentImage", JSONObject().put("thumbnailViewModel", JSONObject().put("image", JSONObject().put("sources", JSONArray().put(JSONObject().put("url", "https://i.ytimg.com/actual.jpg").put("width", 640))))))
        val result = HomeFeedData.parse(page(JSONObject().put("lockupViewModel", lockup))).videos.single()
        assertEquals("New title", result.title)
        assertEquals("Creator", result.channelTitle)
        assertEquals(channel, result.channelId)
        assertEquals("42 views", result.metadataText)
        assertEquals("https://i.ytimg.com/actual.jpg", result.thumbnailUrl)
        lockup.put("contentType", "LOCKUP_CONTENT_TYPE_PLAYLIST")
        assertTrue(HomeFeedData.parse(page(JSONObject().put("lockupViewModel", lockup))).videos.isEmpty())
    }
}
