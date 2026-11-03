package com.weiktube.app

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** A recent-upload feed for the user's subscriptions, using read-only YouTube API requests only. */
class SubscriptionFeedController(private val account: YoutubeAccountController) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val uploadsPermits = Semaphore(4)
    private var job: Job? = null
    private var generation = 0
    private var observedSession = account.sessionRevision
    private val visibility = FeedVisibilityLeases()
    private var closed = false
    private var cachedAtNanos = 0L
    private var nextSubscriptions = ""
    private var processedChannels: Set<String> = emptySet()
    private var unavailableChannels = 0
    var videos by mutableStateOf<List<SubscriptionVideo>>(emptyList()); private set
    var channels by mutableStateOf<List<YoutubeAccountItem>>(emptyList()); private set
    var busy by mutableStateOf(false); private set
    var loaded by mutableStateOf(false); private set
    var status by mutableStateOf(""); private set
    var loadedChannelCount by mutableIntStateOf(0); private set
    var hasMoreChannels by mutableStateOf(false); private set

    init {
        scope.launch {
            snapshotFlow { Triple(account.sessionRevision, account.connected, account.busy) }.collect {
                synchronizeSession()
                if (!account.connected) {
                    if (!account.busy) status = "Melde dich mit Google an, um neue Videos deiner Abos zu sehen."
                } else if (visibility.visible && !account.busy) loadIfNeeded()
            }
        }
    }

    fun onOpened() {
        if (closed) return
        visibility.acquire()
        synchronizeSession()
        account.onAccountOpened() // Restores an existing grant silently; never opens consent itself.
        if (account.connected && !account.busy) loadIfNeeded()
    }

    fun onHidden() {
        if (!visibility.release()) return
        generation++; job?.cancel(); busy = false
        // Keep successful same-session rows in memory; do not fetch while the tab is absent.
    }

    fun refresh() {
        synchronizeSession()
        if (closed || busy) return
        account.onAccountOpened()
        if (!account.connected || account.busy) {
            status = if (account.busy) "Google-Kontodaten werden vorbereitet …" else "Bitte zuerst mit Google anmelden."
            return
        }
        startBatch(reset = true)
    }

    fun loadMoreChannels() {
        // A click can arrive before snapshotFlow delivers a just-replaced account revision.
        // Never use the previous account's channel list or pagination cursor with the new token.
        synchronizeSession()
        if (closed || busy || !hasMoreChannels) return
        if (!account.connected || account.busy) { status = "Bitte warten, bis dein Google-Konto bereit ist."; return }
        startBatch(reset = false)
    }

    private fun loadIfNeeded() {
        synchronizeSession()
        if (closed || busy || !account.connected || account.busy) return
        if (!loaded || System.nanoTime() - cachedAtNanos >= CACHE_NANOS) startBatch(reset = true)
    }

    private fun startBatch(reset: Boolean) {
        val expected = ++generation
        val session = account.sessionRevision
        busy = true; status = "Neue Videos deiner Abos werden geladen …"
        job = scope.launch {
            try {
                var discovered = if (reset) emptyList() else channels
                var nextToken = if (reset) "" else nextSubscriptions
                val processed = if (reset) emptySet() else processedChannels
                var unavailable = if (reset) 0 else unavailableChannels
                if (reset || discovered.count { it.id !in processed } < CHANNEL_BATCH && nextToken.isNotEmpty()) {
                    val previousToken = nextToken
                    val response = account.requestApi("subscriptions", mapOf("part" to "snippet", "mine" to "true", "order" to "alphabetical", "maxResults" to "50") +
                        nextToken.takeIf { it.isNotEmpty() }?.let { mapOf("pageToken" to it) }.orEmpty(), expectedSession = session)
                    ensureCurrent(expected, session)
                    discovered = (discovered + SubscriptionFeedData.subscriptionChannels(response)).distinctBy { it.id }
                    nextToken = response.optString("nextPageToken").take(4096)
                    require(nextToken.isEmpty() || nextToken != previousToken) { "YouTube hat dieselbe Aboseite erneut geliefert. Bitte aktualisieren." }
                }
                val selected = discovered.filterNot { it.id in processed }.take(CHANNEL_BATCH)
                val selectedIds = selected.map { it.id }.toSet()
                var freshVideos = emptyList<SubscriptionVideo>()
                if (selected.isNotEmpty()) {
                    val channelResponse = account.requestApi("channels", mapOf("part" to "contentDetails", "id" to selectedIds.joinToString(","), "maxResults" to "50"), expectedSession = session)
                    ensureCurrent(expected, session)
                    val uploads = SubscriptionFeedData.uploadPlaylists(channelResponse, selectedIds)
                    unavailable += selected.size - uploads.size
                    val uploadsResults = coroutineScope {
                        uploads.values.map { playlist -> async {
                            uploadsPermits.withPermit {
                                try {
                                    val response = account.requestApi("playlistItems", mapOf("part" to "contentDetails", "playlistId" to playlist, "maxResults" to "3"), expectedSession = session)
                                    SubscriptionFeedData.uploadVideoIds(response) to false
                                } catch (failure: YoutubeApiFailure) {
                                    if (failure.reason in setOf("playlistNotFound", "playlistForbidden", "playlistOperationUnsupported")) emptyList<String>() to true
                                    else throw failure
                                }
                            }
                        } }.awaitAll()
                    }
                    ensureCurrent(expected, session)
                    unavailable += uploadsResults.count { it.second }
                    val videoIds = uploadsResults.flatMap { it.first }.distinct()
                    for (batch in videoIds.chunked(50)) {
                        val response = account.requestApi("videos", mapOf("part" to "snippet,contentDetails", "id" to batch.joinToString(",")), expectedSession = session)
                        ensureCurrent(expected, session)
                        freshVideos = freshVideos + SubscriptionFeedData.videos(response, batch.toSet(), selectedIds)
                    }
                }
                ensureCurrent(expected, session)
                val complete = processed + selectedIds
                videos = SubscriptionFeedData.merge(if (reset) emptyList() else videos, freshVideos)
                channels = discovered; nextSubscriptions = nextToken; processedChannels = complete
                unavailableChannels = unavailable; loadedChannelCount = (complete.size - unavailable).coerceAtLeast(0)
                hasMoreChannels = discovered.any { it.id !in complete } || nextToken.isNotEmpty()
                loaded = true
                if (reset || cachedAtNanos == 0L) cachedAtNanos = System.nanoTime()
                status = when {
                    discovered.isEmpty() && nextToken.isEmpty() -> "Du hast noch keine Kanäle abonniert."
                    unavailable > 0 -> "$unavailable Kanäle sind momentan nicht abrufbar. Mit Aktualisieren kannst du es erneut versuchen."
                    videos.isEmpty() -> "Für die bisher geladenen Kanäle sind keine verfügbaren Uploads vorhanden."
                    else -> ""
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (expected == generation && session == account.sessionRevision) status = errorText(failure)
            } finally {
                if (expected == generation) busy = false
            }
        }
    }

    private fun clearCache() {
        videos = emptyList(); channels = emptyList(); processedChannels = emptySet(); nextSubscriptions = ""
        loadedChannelCount = 0; unavailableChannels = 0; hasMoreChannels = false
        cachedAtNanos = 0; loaded = false; busy = false; status = ""
    }
    private fun synchronizeSession() {
        val session = account.sessionRevision
        if (session != observedSession) {
            observedSession = session; generation++; job?.cancel(); clearCache()
        }
    }
    private fun ensureCurrent(expected: Int, session: Long) {
        if (closed || expected != generation) throw CancellationException("Subscription feed closed or refreshed")
        if (session != account.sessionRevision) throw AccountSessionChanged()
    }
    private fun errorText(failure: Exception): String = when {
        failure is YoutubeApiFailure && failure.reason in setOf("quotaExceeded", "dailyLimitExceeded") -> "Das kostenlose YouTube-Tageskontingent ist erschöpft. Bitte später erneut aktualisieren."
        failure is YoutubeApiFailure && failure.http == 401 -> "Die Google-Anmeldung ist abgelaufen. Bitte erneut anmelden."
        failure is YoutubeApiFailure && failure.http == 403 -> "YouTube hat den Lesezugriff abgelehnt. Bitte deine Google-Freigabe prüfen."
        failure is java.io.IOException -> "Neue Videos konnten nicht geladen werden. Prüfe die Verbindung und versuche es erneut."
        else -> failure.message?.take(240) ?: "Die Abovideos konnten nicht geladen werden. Bitte erneut versuchen."
    }
    fun close() { closed = true; visibility.clear(); generation++; scope.cancel(); clearCache() }
    companion object {
        private const val CHANNEL_BATCH = 12
        private const val CACHE_NANOS = 5L * 60 * 1_000_000_000
    }
}
