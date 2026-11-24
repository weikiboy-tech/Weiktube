package com.weiktube.app

import android.content.Intent

/** No queued launches: every delivered intent routes synchronously through the existing entry points. */
internal object ExternalYoutubeIntent {
    const val STATE_KEY = "last_external_youtube_url"
    fun url(intent: Intent): String? = runCatching {
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString?.let(Links::externalYoutubeUrl)
            Intent.ACTION_SEND -> {
                if (intent.type != "text/plain") return@runCatching null
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() ?: return@runCatching null
                if (text.length > 16384) return@runCatching null
                Regex("https://[^\\s<>]+", RegexOption.IGNORE_CASE).findAll(text).mapNotNull {
                    Links.externalYoutubeUrl(it.value.trimEnd('.', ',', ';', '!', '?', ')', ']', '}', '"', '\''))
                }.firstOrNull()
            }
            else -> null
        }
    }.getOrNull()

    fun isFreshLaunch(intent: Intent, handled: String?): Boolean = url(intent)?.let { it != handled } == true
    fun route(url: String, video: (String) -> Unit, page: (String) -> Unit) {
        if (Links.videoId(url) != null || Links.isShorts(url)) video(url) else page(url)
    }
}
