package com.weiktube.app

import org.json.JSONObject

/** Explicit API payloads, tested without signing in or modifying a real account. */
object YoutubeActionPayloads {
    fun subscription(channelId: String): JSONObject = JSONObject().put("snippet", JSONObject().put("resourceId",
        JSONObject().put("kind", "youtube#channel").put("channelId", VideoActionPolicy.resourceId(channelId))))

    fun comment(channelId: String, videoId: String, text: String): JSONObject = JSONObject().put("snippet", JSONObject()
        .put("channelId", VideoActionPolicy.resourceId(channelId)).put("videoId", VideoActionPolicy.videoId(videoId))
        .put("topLevelComment", JSONObject().put("snippet", JSONObject().put("textOriginal", VideoActionPolicy.comment(text)))))

    fun reply(parentId: String, text: String): JSONObject = JSONObject().put("snippet", JSONObject()
        .put("parentId", VideoActionPolicy.resourceId(parentId)).put("textOriginal", VideoActionPolicy.comment(text)))

    fun playlistItem(playlistId: String, videoId: String): JSONObject = JSONObject().put("snippet", JSONObject()
        .put("playlistId", VideoActionPolicy.playlistId(playlistId)).put("resourceId", JSONObject()
            .put("kind", "youtube#video").put("videoId", VideoActionPolicy.videoId(videoId))))
}
