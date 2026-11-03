package com.weiktube.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SubscriptionFeedDataTest {
    private val channel = "UC1234567890123456789012"

    @Test fun subscriptionsKeepTheRealChannelAndSubscriptionIdentifiersSeparate() {
        val result = SubscriptionFeedData.subscriptionChannels(JSONObject("""{"items":[
            {"id":"actual_subscription_resource","snippet":{"title":"Kanal & mehr","resourceId":{"channelId":"$channel"},"thumbnails":{"medium":{"url":"https://yt3.ggpht.com/actual"}}}},
            {"id":"wrong","snippet":{"resourceId":{"channelId":"https://example.com"}}}
        ]}"""))
        assertEquals(1, result.size)
        assertEquals(channel, result.single().id)
        assertEquals("actual_subscription_resource", result.single().resourceId)
        assertEquals("https://yt3.ggpht.com/actual", result.single().thumbnailUrl)
        assertEquals("https://m.youtube.com/channel/$channel", result.single().url)
    }

    @Test fun uploadsComeFromReturnedPlaylistIdsRatherThanGuessedChannelTransformations() {
        val result = SubscriptionFeedData.uploadPlaylists(JSONObject("""{"items":[
            {"id":"$channel","contentDetails":{"relatedPlaylists":{"uploads":"UU_actual_uploads"}}},
            {"id":"another","contentDetails":{"relatedPlaylists":{"uploads":"UU_unrequested"}}}
        ]}"""), setOf(channel))
        assertEquals(mapOf(channel to "UU_actual_uploads"), result)
    }

    @Test fun unavailableVideoIdsAndUnexpectedChannelsDoNotProducePlaceholderRows() {
        val response = JSONObject("""{"items":[
            {"id":"abcdefghijk","snippet":{"channelId":"$channel","title":"Actual title","channelTitle":"Actual channel","publishedAt":"2026-11-02T12:00:00Z","thumbnails":{"high":{"url":"https://i.ytimg.com/actual.jpg"}}},"contentDetails":{"duration":"PT1H2M3S"}},
            {"id":"other_video","snippet":{"channelId":"unsubscribed","title":"Wrong channel"}},
            {"id":"missingbody"}
        ]}""")
        val videos = SubscriptionFeedData.videos(response, setOf("abcdefghijk", "other_video", "missingbody", "deleted1234"), setOf(channel))
        assertEquals(1, videos.size)
        assertEquals("Actual title", videos.single().title)
        assertEquals("1:02:03", videos.single().duration)
        assertEquals("https://i.ytimg.com/actual.jpg", videos.single().thumbnailUrl)
    }

    @Test fun durationIsBasedOnTheReturnedIsoValueAndUnknownDurationsStayUnknown() {
        assertEquals("2:07", SubscriptionFeedData.duration("PT2M7S"))
        assertEquals("25:00:00", SubscriptionFeedData.duration("P1DT1H"))
        assertEquals("", SubscriptionFeedData.duration("invalid"))
        assertEquals("", SubscriptionFeedData.duration("PT0S"))
    }

    @Test fun aVideoNeedsBothARequestedUploadIdAndASubscribedChannel() {
        val response = JSONObject("""{"items":[
            {"id":"abcdefghijk","snippet":{"channelId":"$channel","title":"Requested upload"}},
            {"id":"zyxwvutsrqp","snippet":{"channelId":"$channel","title":"Not requested, despite matching channel"}},
            {"id":"12345678901","snippet":{"channelId":"UCabcdefghijklmnopqrstuv","title":"Requested ID, but foreign channel"}}
        ]}""")
        val videos = SubscriptionFeedData.videos(response, setOf("abcdefghijk", "12345678901"), setOf(channel))
        assertEquals(listOf("abcdefghijk"), videos.map { it.id })
    }

    @Test fun mergeUsesActualInstantsAndUpdatesDuplicateVideoResources() {
        fun row(id: String, date: String, title: String = id) = SubscriptionVideo(id, title, channel, "Channel", "", date, "")
        val merged = SubscriptionFeedData.merge(
            listOf(row("abcdefghijk", "2026-11-02T13:00:00+02:00", "Old title"), row("abcdefghijl", "2026-11-02T11:30:00Z")),
            listOf(row("abcdefghijk", "2026-11-02T13:00:00+02:00", "Updated title")))
        assertEquals(listOf("abcdefghijl", "abcdefghijk"), merged.map { it.id })
        assertEquals("Updated title", merged.last().title)
    }

    @Test fun uploadIdsAreDeduplicatedBeforeTheVideoDetailsRequest() {
        val ids = SubscriptionFeedData.uploadVideoIds(JSONObject("""{"items":[
            {"contentDetails":{"videoId":"abcdefghijk"}},
            {"contentDetails":{"videoId":"abcdefghijk"}},
            {"contentDetails":{"videoId":"deleted"}}
        ]}"""))
        assertEquals(listOf("abcdefghijk"), ids)
    }
}
