package com.weiktube.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class YoutubeActionPayloadsTest {
    @Test fun subscriptionTargetsTheChannelResource() {
        val resource = YoutubeActionPayloads.subscription("UC_example").getJSONObject("snippet").getJSONObject("resourceId")
        assertEquals("youtube#channel", resource.getString("kind"))
        assertEquals("UC_example", resource.getString("channelId"))
    }
    @Test fun commentEscapesUserTextAndContainsRequiredVideoAndOwner() {
        val text = "A \"quoted\" comment\nwith {json} and </script>"
        val roundTrip = JSONObject(YoutubeActionPayloads.comment("UC_owner", "abcdefghijk", text).toString()).getJSONObject("snippet")
        assertEquals("UC_owner", roundTrip.getString("channelId"))
        assertEquals("abcdefghijk", roundTrip.getString("videoId"))
        assertEquals(text, roundTrip.getJSONObject("topLevelComment").getJSONObject("snippet").getString("textOriginal"))
    }
    @Test fun replyUsesParentCommentIdNotThreadOrVideoId() {
        val snippet = YoutubeActionPayloads.reply("UgyParent.123", "reply").getJSONObject("snippet")
        assertEquals("UgyParent.123", snippet.getString("parentId"))
        assertFalse(snippet.has("videoId"))
    }
    @Test fun savedVideoAndChosenPlaylistRemainSeparateIds() {
        val snippet = YoutubeActionPayloads.playlistItem("PL_own", "abcdefghijk").getJSONObject("snippet")
        assertEquals("PL_own", snippet.getString("playlistId"))
        assertEquals("youtube#video", snippet.getJSONObject("resourceId").getString("kind"))
        assertEquals("abcdefghijk", snippet.getJSONObject("resourceId").getString("videoId"))
    }
    @Test(expected = IllegalArgumentException::class) fun noEmptyCommentPayload() { YoutubeActionPayloads.comment("UC_owner", "abcdefghijk", " ") }
    @Test(expected = IllegalArgumentException::class) fun noFakeSystemPlaylistSave() { YoutubeActionPayloads.playlistItem("WL", "abcdefghijk") }
}
