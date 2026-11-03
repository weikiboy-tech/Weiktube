package com.weiktube.app

/** Validation and intent decisions independent of Android/network. No local simulation of server success. */
object VideoActionPolicy {
    fun videoId(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9_-]{11}"))) { "Ungültige Video-ID." }
        return value
    }
    fun resourceId(value: String): String {
        require(value.isNotBlank() && value.length <= 256 && value.none { it.isWhitespace() || it.isISOControl() }) { "Ungültige YouTube-ID." }
        return value
    }
    fun playlistId(value: String): String {
        resourceId(value)
        require(value !in setOf("WL", "HL")) { "Diese YouTube-Systemplaylist wird nicht unterstützt. Bitte eine eigene Playlist wählen." }
        return value
    }
    fun comment(value: String): String {
        val text = value.trim()
        require(text.isNotEmpty()) { "Bitte einen Kommentar eingeben." }
        require(text.length <= 5000) { "Kommentare in Weiktube dürfen höchstens 5.000 Zeichen enthalten." }
        return text
    }
    fun requestedRating(current: String): String {
        require(current in setOf("like", "none", "dislike", "unspecified")) { "Bewertung noch nicht geladen." }
        return if (current == "like") "none" else "like"
    }
    fun retryAutomatically(method: String, status: Int, attempts: Int): Boolean = method == "GET" && status == 401 && attempts == 0
}
