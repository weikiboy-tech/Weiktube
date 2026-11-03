package com.weiktube.app

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.json.JSONArray
import org.json.JSONObject

data class YoutubeComment(val id: String, val author: String, val text: String, val likeCount: Long, val replyCount: Int, val parentId: String? = null)

/** Real authenticated actions. Successful UI state follows a successful YouTube HTTP response. */
class VideoActionsController(private val account: YoutubeAccountController) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var generation = 0
    private var loadedSession: Long? = null
    private val readReloadGate = VideoReadReloadGate()
    private var visible = false
    private var waitingForInitialRead = false
    private var pendingReadReload = false
    private var writeInProgress = false
    private var writeNeedsConfirmation = false
    private var writeRefreshMessage: String? = null
    private var nextComments = ""
    private val nextReplies = mutableMapOf<String, String>()
    var videoId by mutableStateOf(""); private set
    var title by mutableStateOf(""); private set
    var channelId by mutableStateOf(""); private set
    var channelTitle by mutableStateOf(""); private set
    var rating by mutableStateOf("none"); private set
    var ratingKnown by mutableStateOf(false); private set
    var subscriptionId by mutableStateOf<String?>(null); private set
    var subscriptionKnown by mutableStateOf(false); private set
    var comments by mutableStateOf<List<YoutubeComment>>(emptyList()); private set
    var replies by mutableStateOf<Map<String, List<YoutubeComment>>>(emptyMap()); private set
    var moreComments by mutableStateOf(false); private set
    var moreReplies by mutableStateOf<Set<String>>(emptySet()); private set
    var commentsMessage by mutableStateOf<String?>(null); private set
    var commentsNeedAuthorization by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var loaded by mutableStateOf(false); private set
    var status by mutableStateOf(""); private set
    var postedCommentSequence by mutableIntStateOf(0); private set
    var savedPlaylistSequence by mutableIntStateOf(0); private set

    init {
        scope.launch {
            snapshotFlow { Triple(account.sessionRevision, account.connected, account.busy) }.collect {
                // Compare against current state, not a snapshot emission queued before a fast load.
                if (loadedSession != null && loadedSession != account.sessionRevision) {
                    clearAccountState()
                    pendingReadReload = true
                    if (writeInProgress) writeNeedsConfirmation = true
                    status = "Google-Verbindung aktualisiert. Videodetails werden neu geladen …"
                }
                maybeReloadReads()
            }
        }
    }

    private fun clearAccountState() {
        rating = "none"; ratingKnown = false; subscriptionId = null; subscriptionKnown = false
        comments = emptyList(); replies = emptyMap(); nextComments = ""; nextReplies.clear()
        moreComments = false; moreReplies = emptySet(); commentsMessage = null; commentsNeedAuthorization = false; loaded = false
    }

    fun onOpened(id: String) {
        visible = true
        account.onAccountOpened(loadAccountData = false)
        load(id)
    }

    fun onHidden() {
        visible = false; waitingForInitialRead = false; pendingReadReload = false
        writeNeedsConfirmation = false; writeRefreshMessage = null
        generation++; job?.cancel(); busy = false; writeInProgress = false
    }

    fun load(id: String) {
        runCatching { VideoActionPolicy.videoId(id) }.getOrElse { status = it.message.orEmpty(); return }
        if (writeInProgress) return
        visible = true; videoId = id
        readReloadGate.userOperation(); waitingForInitialRead = true; pendingReadReload = false
        if (!account.connected) {
            clearAccountState()
            status = "Bitte zuerst mit Google anmelden."
        } else if (account.busy) status = "Google-Konto wird vorbereitet …"
        maybeReloadReads()
    }

    private fun maybeReloadReads() {
        if (readReloadGate.take(visible, account.connected, account.busy, busy, waitingForInitialRead, pendingReadReload)) {
            waitingForInitialRead = false; pendingReadReload = false
            startRead(videoId)
        }
    }

    private fun startRead(id: String) {
        generation++; job?.cancel()
        val expected = generation
        loadedSession = account.sessionRevision
        videoId = id; title = ""; channelId = ""; channelTitle = ""
        rating = "none"; ratingKnown = false; subscriptionId = null; subscriptionKnown = false
        comments = emptyList(); replies = emptyMap(); nextComments = ""; nextReplies.clear(); moreComments = false; moreReplies = emptySet()
        commentsMessage = null; commentsNeedAuthorization = false; loaded = false; busy = true; status = "Videodetails werden geladen …"
        job = scope.launch {
            try {
                val video = request("videos", mapOf("part" to "snippet", "id" to id)).optJSONArray("items")?.optJSONObject(0)
                    ?: error("Das Video ist nicht verfügbar.")
                ensureCurrent(expected)
                val snippet = video.getJSONObject("snippet")
                title = snippet.getString("title"); channelId = snippet.getString("channelId"); channelTitle = snippet.optString("channelTitle")
                loaded = true
                readRating(id); ensureCurrent(expected)
                readSubscription(channelId); ensureCurrent(expected)
                fetchComments(id, reset = true); ensureCurrent(expected)
                status = writeRefreshMessage ?: if (writeNeedsConfirmation) "Google-Freigabe aktualisiert. Bitte die gewünschte Aktion erneut wählen." else ""
                writeNeedsConfirmation = false; writeRefreshMessage = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (expected == generation) {
                    if (failure is AccountSessionChanged) { clearAccountState(); pendingReadReload = true }
                    status = errorText(failure, false)
                }
            }
            finally { if (expected == generation) { busy = false; maybeReloadReads() } }
        }
    }

    fun toggleLike() {
        if (!ratingKnown) { status = "Bitte die Bewertung zuerst laden."; return }
        val target = VideoActionPolicy.requestedRating(rating)
        perform {
            request("videos/rate", mapOf("id" to videoId, "rating" to target), "POST")
            rating = target; ratingKnown = true
            status = if (target == "like") "Bei YouTube mit „Gefällt mir“ markiert." else "Deine Bewertung wurde entfernt."
        }
    }

    fun toggleSubscription() {
        if (!subscriptionKnown) { status = "Bitte den Abostatus zuerst laden."; return }
        val subscribe = subscriptionId == null
        perform {
            // Permission escalation can select another account. Resolve its actual subscription before deleting.
            readSubscription(channelId)
            if (subscribe && subscriptionId == null) {
                val body = YoutubeActionPayloads.subscription(channelId)
                val created = request("subscriptions", mapOf("part" to "snippet"), "POST", body)
                subscriptionId = created.getString("id")
            } else if (!subscribe && subscriptionId != null) {
                request("subscriptions", mapOf("id" to subscriptionId!!), "DELETE")
                subscriptionId = null
            }
            subscriptionKnown = true
            status = if (subscribe) "Kanal bei YouTube abonniert." else "YouTube-Abo beendet."
        }
    }

    fun postComment(value: String, parentId: String? = null) {
        val text = runCatching { VideoActionPolicy.comment(value) }.getOrElse { status = it.message.orEmpty(); return }
        if (!loaded) { status = "Bitte die Videodetails zuerst laden."; return }
        if (parentId != null && comments.none { it.id == parentId }) { status = "Der Kommentar ist nicht mehr geöffnet. Bitte erneut auswählen."; return }
        perform {
            val result = if (parentId == null) {
                request("commentThreads", mapOf("part" to "snippet"), "POST", YoutubeActionPayloads.comment(channelId, videoId, text))
            } else {
                request("comments", mapOf("part" to "snippet"), "POST", YoutubeActionPayloads.reply(parentId, text))
            }
            // Parsing the server-created resource, rather than fabricating a local author or posted time.
            if (parentId == null) {
                val snippet = result.getJSONObject("snippet")
                val created = parseComment(snippet.getJSONObject("topLevelComment"), snippet.optInt("totalReplyCount"))
                comments = listOf(created) + comments.filterNot { it.id == created.id }
            } else {
                val created = parseComment(result, 0, parentId)
                replies = replies + (parentId to (replies[parentId].orEmpty() + created).distinctBy { it.id })
                comments = comments.map { if (it.id == parentId) it.copy(replyCount = it.replyCount + 1) else it }
            }
            postedCommentSequence++
            status = "Von YouTube angenommen. Die Veröffentlichung kann von der Kanalmoderation abhängen."
        }
    }

    fun saveToPlaylist(playlistId: String) {
        runCatching { VideoActionPolicy.playlistId(playlistId) }.getOrElse { status = it.message.orEmpty(); return }
        if (account.playlists.none { it.id == playlistId }) { status = "Bitte eine deiner geladenen Playlists auswählen."; return }
        perform {
            val existing = request("playlistItems", mapOf("part" to "id", "playlistId" to playlistId, "videoId" to videoId, "maxResults" to "1"))
            if ((existing.optJSONArray("items")?.length() ?: 0) == 0) {
                val body = YoutubeActionPayloads.playlistItem(playlistId, videoId)
                val saved = request("playlistItems", mapOf("part" to "snippet"), "POST", body)
                require(saved.optString("id").isNotBlank()) { "YouTube hat den Speichervorgang nicht bestätigt." }
                status = "In deiner YouTube-Playlist gespeichert."
            } else status = "Das Video ist bereits in dieser Playlist."
            savedPlaylistSequence++
        }
    }

    fun loadMoreComments() {
        if (!moreComments) return
        readOperation { fetchComments(videoId, reset = false) }
    }
    fun loadReplies(parentId: String) {
        if (comments.none { it.id == parentId }) return
        readOperation {
            val response = request("comments", mapOf("part" to "snippet", "parentId" to parentId, "maxResults" to "50", "textFormat" to "plainText") +
                nextReplies[parentId]?.takeIf { it.isNotBlank() }?.let { mapOf("pageToken" to it) }.orEmpty())
            val rows = response.optJSONArray("items").objects().map { parseComment(it, 0, parentId) }
            replies = replies + (parentId to (replies[parentId].orEmpty() + rows).distinctBy { it.id })
            val next = response.optString("nextPageToken")
            nextReplies[parentId] = next
            moreReplies = if (next.isNotEmpty()) moreReplies + parentId else moreReplies - parentId
        }
    }

    private suspend fun readRating(id: String) {
        val result = request("videos/getRating", mapOf("id" to id))
        val item = result.optJSONArray("items")?.optJSONObject(0) ?: error("Deine Bewertung konnte nicht geladen werden.")
        rating = item.getString("rating")
        require(rating in setOf("like", "none", "dislike", "unspecified")) { "YouTube hat einen unbekannten Bewertungsstatus gemeldet." }
        ratingKnown = true
    }
    private suspend fun readSubscription(id: String) {
        val result = request("subscriptions", mapOf("part" to "id", "mine" to "true", "forChannelId" to id, "maxResults" to "1"))
        subscriptionId = result.optJSONArray("items")?.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
        subscriptionKnown = true
    }
    private suspend fun fetchComments(id: String, reset: Boolean) {
        if (!account.hasInteractionPermission()) {
            commentsNeedAuthorization = true; moreComments = false
            commentsMessage = "Für Kommentare verlangt Google eine zusätzliche YouTube-Freigabe. Sie erlaubt auch Interaktionen; Änderungen erfolgen nur über deine Aktionsschaltflächen."
            return
        }
        try {
            val result = request("commentThreads", mapOf("part" to "snippet", "videoId" to id, "maxResults" to "20", "textFormat" to "plainText", "order" to "relevance") +
                (if (!reset && nextComments.isNotBlank()) mapOf("pageToken" to nextComments) else emptyMap()))
            val rows = result.optJSONArray("items").objects().map { item ->
                val snippet = item.getJSONObject("snippet")
                parseComment(snippet.getJSONObject("topLevelComment"), snippet.optInt("totalReplyCount"))
            }
            comments = if (reset) rows else (comments + rows).distinctBy { it.id }
            nextComments = result.optString("nextPageToken"); moreComments = nextComments.isNotBlank(); commentsMessage = null; commentsNeedAuthorization = false
        } catch (failure: YoutubeApiFailure) {
            when {
                failure.reason == "commentsDisabled" -> { commentsMessage = "Kommentare sind für dieses Video deaktiviert."; moreComments = false }
                failure.http == 403 && failure.reason in setOf("insufficientPermissions", "forbidden") -> {
                    commentsMessage = "YouTube gibt die Kommentare für dieses Konto oder Video nicht frei. Deine Bewertung und dein Abostatus wurden separat geladen."
                    moreComments = false
                }
                else -> throw failure
            }
        }
    }

    /** Explicit consent button for comment reads. This path cannot issue any POST or DELETE. */
    fun authorizeComments() {
        if (busy || account.busy || !loaded) return
        readReloadGate.userOperation()
        busy = true; status = "Zusätzliche Google-Freigabe wird angefragt …"
        val expected = generation
        job = scope.launch {
            try {
                account.authorizeUserAction()
                if (expected != generation) throw CancellationException("Video changed")
                pendingReadReload = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (expected == generation) status = errorText(failure, false) }
            finally { if (expected == generation) { busy = false; maybeReloadReads() } }
        }
    }

    private fun perform(action: suspend () -> Unit) {
        if (busy || account.busy || !loaded) return
        readReloadGate.userOperation(); writeRefreshMessage = null; writeNeedsConfirmation = false
        writeInProgress = true; busy = true; status = "Google-Freigabe und Aktion werden geprüft …"
        val expected = generation
        job = scope.launch {
            try {
                account.authorizeUserAction() // This is reached only from a user's explicit action button.
                ensureCurrent(expected)
                action()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (expected == generation) {
                    if (loadedSession != account.sessionRevision) {
                        clearAccountState(); pendingReadReload = true; writeNeedsConfirmation = true
                        if (failure !is AccountSessionChanged) writeRefreshMessage = errorText(failure, true)
                    }
                    status = errorText(failure, true)
                }
            }
            finally {
                if (expected == generation) { writeInProgress = false; busy = false; maybeReloadReads() }
            }
        }
    }
    private fun readOperation(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        val expected = generation
        job = scope.launch {
            try { action(); ensureCurrent(expected) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (expected == generation) status = errorText(failure, false) }
            finally { if (expected == generation) { busy = false; maybeReloadReads() } }
        }
    }
    private fun ensureCurrent(expected: Int) {
        if (expected != generation) throw CancellationException("Video changed")
        loadedSession?.let { if (it != account.sessionRevision) throw AccountSessionChanged() }
    }
    private suspend fun request(resource: String, parameters: Map<String, String>, method: String = "GET", body: JSONObject? = null): JSONObject {
        val expected = loadedSession ?: throw AccountSessionChanged()
        return account.requestApi(resource, parameters, method, body, expectedSession = expected)
    }
    private fun errorText(failure: Exception, write: Boolean): String = when {
        failure is YoutubeApiFailure && failure.reason in setOf("quotaExceeded", "dailyLimitExceeded") -> "Das kostenlose YouTube-Tageskontingent ist erschöpft. Bitte später erneut versuchen."
        failure is YoutubeApiFailure && failure.http == 401 -> "Die Google-Freigabe ist abgelaufen. Bitte erneut anmelden und die Aktion nochmals bewusst ausführen."
        failure is YoutubeApiFailure && failure.reason == "commentsDisabled" -> "Kommentare sind für dieses Video deaktiviert."
        failure is YoutubeApiFailure && failure.http == 403 -> "YouTube erlaubt diese Aktion für das Konto oder Video nicht. Es wurde kein Erfolg bestätigt."
        failure is java.io.IOException && write -> "Keine sichere Bestätigung von YouTube erhalten. Prüfe zuerst den aktuellen Zustand; die Aktion wird nicht automatisch wiederholt."
        failure is java.io.IOException -> "YouTube ist nicht erreichbar. Bitte erneut laden."
        else -> failure.message?.take(240) ?: "Die YouTube-Aktion konnte nicht abgeschlossen werden."
    }
    private fun parseComment(comment: JSONObject, count: Int, parent: String? = null): YoutubeComment {
        val snippet = comment.getJSONObject("snippet")
        return YoutubeComment(comment.getString("id"), snippet.optString("authorDisplayName"), snippet.optString("textOriginal", snippet.optString("textDisplay")),
            snippet.optLong("likeCount"), count, parent)
    }
    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
    fun close() { generation++; scope.cancel() }
}
