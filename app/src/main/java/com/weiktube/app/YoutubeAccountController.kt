package com.weiktube.app

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class YoutubeAccountItem(val id: String, val title: String, val description: String, val url: String, val thumbnailUrl: String = "", val resourceId: String = id)
private data class AccountPage(val items: List<YoutubeAccountItem>, val nextToken: String)
class YoutubeApiFailure(val http: Int, val reason: String) : Exception("YouTube HTTP $http: $reason")
private class AccountConsentRequired : Exception()

/** Real Google authorization for the native read-only account screens. Never transfers tokens to WebViews. */
class YoutubeAccountController(activity: ComponentActivity) {
    private val preferences = activity.getSharedPreferences("weiktube_account", android.content.Context.MODE_PRIVATE)
    private val client = Identity.getAuthorizationClient(activity)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var token: String? = null
    private var grantedScopes: Set<String> = emptySet()
    private val nativeAuthorizationMutex = Mutex()
    private var nativeAuthorization: CompletableDeferred<String>? = null
    private var nativeAuthorizationScopes: Set<String> = emptySet()
    private var closed = false
    private var generation = 0
    private val sessionGuard = AccountSessionGuard()
    var sessionRevision by mutableLongStateOf(0); private set
    private var resolutionGeneration = 0
    private var resolutionPending = false
    private var dataJob: Job? = null
    private var attemptedRestore = false
    private val scopes = listOf(Scope(YOUTUBE_READONLY))
    var linked by mutableStateOf(preferences.getBoolean("linked", false)); private set
    var revocationPending by mutableStateOf(preferences.getBoolean("revocation_pending", false)); private set
    var connected by mutableStateOf(false); private set
    var loaded by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var status by mutableStateOf("Für diese Kontoansicht ist eine separate Google-Freigabe nötig."); private set
    var ownChannels by mutableStateOf<List<YoutubeAccountItem>>(emptyList()); private set
    var subscriptions by mutableStateOf<List<YoutubeAccountItem>>(emptyList()); private set
    var playlists by mutableStateOf<List<YoutubeAccountItem>>(emptyList()); private set
    var moreSubscriptions by mutableStateOf(false); private set
    var morePlaylists by mutableStateOf(false); private set
    private var nextSubscriptions = ""
    private var nextPlaylists = ""
    var selectedPlaylist by mutableStateOf<YoutubeAccountItem?>(null); private set
    var playlistItems by mutableStateOf<List<YoutubeAccountItem>>(emptyList()); private set
    var playlistLoaded by mutableStateOf(false); private set
    var morePlaylistItems by mutableStateOf(false); private set
    private var nextPlaylistItems = ""

    // Construct this controller during Activity.onCreate, before the Activity reaches STARTED.
    private val resolutionLauncher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        resolutionPending = false
        if (!closed && resolutionGeneration == generation) {
            if (result.resultCode != Activity.RESULT_OK || result.data == null) {
                busy = false; status = "Google-Freigabe abgebrochen. Die YouTube-Startseite kannst du weiterhin öffnen."
                nativeAuthorization?.completeExceptionally(IllegalStateException("Google-Freigabe abgebrochen.")); nativeAuthorization = null
            } else try {
                acceptAuthorization(client.getAuthorizationResultFromIntent(result.data!!))
            } catch (failure: Exception) { authorizationFailed(failure) }
        }
    }

    /** Call only when the user deliberately opens Account or Subscriptions. No consent UI on restore. */
    fun onAccountOpened(loadAccountData: Boolean = true) {
        if (closed || busy || resolutionPending) return
        // Opening a video sheet must not refresh playlists or replace an already usable grant.
        if (connected && token != null) {
            if (loadAccountData && !loaded) refresh()
            return
        }
        if (linked && token == null && !attemptedRestore) {
            attemptedRestore = true
            authorize(interactive = false)
        }
    }

    fun connect() { authorize(interactive = true) }

    private fun authorize(interactive: Boolean) {
        if (busy || closed || resolutionPending) return
        busy = true; status = "Google-Freigabe wird vorbereitet …"
        val requestGeneration = generation
        try {
            client.authorize(AuthorizationRequest.builder().setRequestedScopes(scopes).build())
                .addOnSuccessListener { result ->
                    if (closed || requestGeneration != generation) return@addOnSuccessListener
                    if (result.hasResolution()) {
                        if (!interactive) {
                            busy = false
                            status = "Bitte die Freigabe für Abos und Playlists erneut bestätigen."
                        } else {
                            val pending = result.pendingIntent
                            if (pending == null) authorizationFailed(IllegalStateException("Google hat keinen Freigabedialog bereitgestellt."))
                            else try { resolutionGeneration = requestGeneration; resolutionPending = true; resolutionLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build()) }
                            catch (failure: Exception) { resolutionPending = false; authorizationFailed(failure) }
                        }
                    } else acceptAuthorization(result)
                }
                .addOnFailureListener { failure -> if (!closed && requestGeneration == generation) authorizationFailed(failure) }
        } catch (failure: Exception) { authorizationFailed(failure) }
    }

    private fun acceptAuthorization(result: AuthorizationResult) {
        nativeAuthorization?.let { waiting ->
            nativeAuthorization = null
            try { waiting.complete(acceptNativeGrant(result, nativeAuthorizationScopes)) }
            catch (failure: Exception) { waiting.completeExceptionally(failure) }
            busy = false
            return
        }
        if (!result.grantedScopes.contains(YOUTUBE_READONLY)) {
            // A partially granted account switch must not retain the previous account's rows.
            invalidateGrant()
            busy = false; status = "Der Lesezugriff auf YouTube wurde nicht freigegeben. Abos und Playlists bleiben verborgen."; return
        }
        val accessToken = result.accessToken
        if (accessToken.isNullOrBlank()) { invalidateGrant(); authorizationFailed(IllegalStateException("Google hat keinen Zugriffstoken ausgegeben.")); return }
        installGrant(accessToken, result.grantedScopes.toSet())
        busy = false
        refresh()
    }

    private fun installGrant(accessToken: String, scopesGranted: Set<String> = setOf(YOUTUBE_READONLY)) {
        if (connected && token == accessToken && grantedScopes == scopesGranted) return
        // A different account may have been chosen. Never expose rows from the previous grant.
        sessionRevision = sessionGuard.advance()
        clearAccountData()
        token = accessToken
        grantedScopes = scopesGranted
        linked = true; connected = true; attemptedRestore = true; revocationPending = false
        preferences.edit().putBoolean("linked", true).putBoolean("revocation_pending", false).apply()
    }

    private fun invalidateGrant() {
        sessionRevision = sessionGuard.advance()
        token = null; grantedScopes = emptySet(); connected = false
        clearAccountData()
    }

    private fun acceptNativeGrant(result: AuthorizationResult, required: Set<String>): String {
        val granted = result.grantedScopes.toSet()
        return when (NativeGrantPolicy.decide(granted, required, YOUTUBE_READONLY, !result.accessToken.isNullOrBlank())) {
            NativeGrantDecision.FULL -> {
                installGrant(result.accessToken!!, granted)
                result.accessToken!!
            }
            NativeGrantDecision.READ_ONLY -> {
                // Google may return a new account with only read consent. Preserve that valid access,
                // never the previous account's rows, and never treat refusal as write permission.
                installGrant(result.accessToken!!, granted)
                status = "Lesezugriff bleibt aktiv. Die zusätzliche Schreibfreigabe wurde nicht erteilt."
                throw IllegalStateException("Die zusätzliche Schreibfreigabe wurde nicht erteilt. Du kannst deine Kontodaten weiter ansehen.")
            }
            NativeGrantDecision.NONE -> {
                invalidateGrant()
                status = "Der YouTube-Kontozugriff wurde nicht freigegeben. Bitte die Freigabe erneut bestätigen."
                throw IllegalStateException("Der YouTube-Kontozugriff wurde nicht freigegeben.")
            }
        }
    }

    private fun clearAccountData() {
        loaded = false
        ownChannels = emptyList(); subscriptions = emptyList(); playlists = emptyList()
        nextSubscriptions = ""; nextPlaylists = ""; moreSubscriptions = false; morePlaylists = false
        closePlaylist()
    }

    private fun authorizationFailed(failure: Exception) {
        busy = false
        nativeAuthorization?.completeExceptionally(failure); nativeAuthorization = null
        status = when ((failure as? ApiException)?.statusCode) {
            10 -> "Google-Freigabe noch nicht freigeschaltet: Der Entwickler muss den Android-OAuth-Client für com.weiktube.app und diesen APK-Signierschlüssel im Google-Cloud-Projekt registrieren und die YouTube Data API aktivieren."
            7 -> "Google ist nicht erreichbar. Bitte die Internetverbindung prüfen und erneut anmelden."
            16, 12501 -> "Freigabe abgebrochen."
            4 -> "Bitte ein Google-Konto auf dem Gerät hinzufügen oder erneut anmelden."
            else -> "Google-Freigabe fehlgeschlagen${(failure as? ApiException)?.statusCode?.let { " (Code $it)" } ?: ""}. Prüfe Internet und Google Play-Dienste. Falls es weiterhin scheitert, muss der Entwickler OAuth-Client, Signierschlüssel und Testnutzer-Freigabe prüfen."
        }
    }

    fun refresh() {
        if (busy || closed) return
        val accessToken = token ?: run { connect(); return }
        busy = true; status = "Deine YouTube-Daten werden geladen …"
        val currentGeneration = generation
        val currentSession = sessionRevision
        dataJob = scope.launch {
            try {
                val pages = apiWithRefresh(currentGeneration) { accessToken ->
                    Triple(fetchPage("channels", accessToken), fetchPage("subscriptions", accessToken), fetchPage("playlists", accessToken))
                }
                if (currentGeneration == generation && currentSession == sessionRevision && pages != null) applyPages(pages)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (currentGeneration == generation && currentSession == sessionRevision) apiFailed(failure) }
            finally { if (currentGeneration == generation) busy = false }
        }
    }

    fun loadMoreSubscriptions() = loadMore("subscriptions")
    fun loadMorePlaylists() = loadMore("playlists")
    private fun loadMore(resource: String) {
        if (busy || closed) return
        val accessToken = token ?: run { connect(); return }
        val pageToken = if (resource == "subscriptions") nextSubscriptions else nextPlaylists
        if (pageToken.isEmpty()) return
        busy = true
        val currentGeneration = generation
        val currentSession = sessionRevision
        dataJob = scope.launch {
            try {
                val page = apiWithRefresh(currentGeneration) { accessToken -> fetchPage(resource, accessToken, pageToken) }
                if (currentGeneration == generation && currentSession == sessionRevision && page != null) {
                    if (resource == "subscriptions") {
                        subscriptions = (subscriptions + page.items).distinctBy { it.id }
                        nextSubscriptions = page.nextToken; moreSubscriptions = nextSubscriptions.isNotEmpty()
                    } else {
                        playlists = (playlists + page.items).distinctBy { it.id }
                        nextPlaylists = page.nextToken; morePlaylists = nextPlaylists.isNotEmpty()
                    }
                    status = "Weitere Einträge geladen."
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (currentGeneration == generation && currentSession == sessionRevision) apiFailed(failure) }
            finally { if (currentGeneration == generation) busy = false }
        }
    }

    private fun applyPages(pages: Triple<AccountPage, AccountPage, AccountPage>) {
        ownChannels = pages.first.items; loaded = true
        subscriptions = pages.second.items; nextSubscriptions = pages.second.nextToken; moreSubscriptions = nextSubscriptions.isNotEmpty()
        playlists = pages.third.items; nextPlaylists = pages.third.nextToken; morePlaylists = nextPlaylists.isNotEmpty()
        status = if (ownChannels.isEmpty()) "Google-Freigabe aktiv. Für dieses Konto wurde kein eigener YouTube-Kanal gefunden." else "Freigabe für Kontofunktionen aktiv. Deine Kontodaten sind aktuell."
    }

    /** One noninteractive refresh attempt per operation. Rebuild all rows; old-account page cursors are discarded. */
    private suspend fun <T> apiWithRefresh(currentGeneration: Int, operation: suspend (String) -> T): T? {
        val accessToken = token ?: throw AccountConsentRequired()
        val requestSession = sessionGuard.capture()
        try {
            val response = withContext(Dispatchers.IO) { operation(accessToken) }
            ensureGeneration(currentGeneration); sessionGuard.check(requestSession)
            return response
        }
        catch (failure: YoutubeApiFailure) {
            if (failure.http != 401) throw failure
            return nativeAuthorizationMutex.withLock {
                ensureGeneration(currentGeneration); sessionGuard.check(requestSession)
                invalidateGrant()
                var recoverySession = sessionGuard.capture()
                status = "Google-Freigabe wird erneuert …"
                try {
                    client.clearToken(ClearTokenRequest.builder().setToken(accessToken).build()).awaitResult()
                    ensureGeneration(currentGeneration); sessionGuard.check(recoverySession)
                    val result = client.authorize(AuthorizationRequest.builder().setRequestedScopes(scopes).build()).awaitResult()
                    ensureGeneration(currentGeneration); sessionGuard.check(recoverySession)
                    if (result.hasResolution() || !result.grantedScopes.contains(YOUTUBE_READONLY) || result.accessToken.isNullOrBlank()) throw AccountConsentRequired()
                    val renewed = result.accessToken!!
                    installGrant(renewed, result.grantedScopes.toSet())
                    recoverySession = sessionGuard.capture()
                    // One retry only; a second 401 is reported without prompting automatically.
                    val pages = withContext(Dispatchers.IO) {
                        Triple(fetchPage("channels", renewed), fetchPage("subscriptions", renewed), fetchPage("playlists", renewed))
                    }
                    ensureGeneration(currentGeneration); sessionGuard.check(recoverySession)
                    applyPages(pages)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (recoveryFailure: Exception) {
                    ensureGeneration(currentGeneration); sessionGuard.check(recoverySession)
                    apiFailed(recoveryFailure)
                }
                null
            }
        }
    }

    private fun ensureGeneration(expected: Int) {
        if (closed || generation != expected) throw CancellationException("Account session changed")
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result) }
        addOnFailureListener { failure -> if (continuation.isActive) continuation.resumeWithException(failure) }
        addOnCanceledListener { continuation.cancel() }
    }

    fun openPlaylist(playlist: YoutubeAccountItem) {
        if (busy || closed || !connected) return
        selectedPlaylist = playlist; playlistItems = emptyList(); playlistLoaded = false
        nextPlaylistItems = ""; morePlaylistItems = false
        loadPlaylistPage()
    }
    fun closePlaylist() {
        selectedPlaylist = null; playlistItems = emptyList(); playlistLoaded = false
        nextPlaylistItems = ""; morePlaylistItems = false
    }
    fun loadMorePlaylistItems() { if (morePlaylistItems) loadPlaylistPage() }
    private fun loadPlaylistPage() {
        val playlist = selectedPlaylist ?: return
        val accessToken = token ?: return
        if (busy || closed) return
        busy = true; status = "Playlist wird geladen …"
        val currentGeneration = generation
        val currentSession = sessionRevision
        val pageToken = nextPlaylistItems
        dataJob = scope.launch {
            try {
                val page = apiWithRefresh(currentGeneration) { currentToken -> fetchPage("playlistItems", currentToken, pageToken, playlist.id) }
                if (currentGeneration == generation && currentSession == sessionRevision && selectedPlaylist?.id == playlist.id && page != null) {
                    playlistItems = (playlistItems + page.items).distinctBy { it.id }
                    playlistLoaded = true; nextPlaylistItems = page.nextToken; morePlaylistItems = nextPlaylistItems.isNotEmpty()
                    status = "Playlist geladen."
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (currentGeneration == generation && currentSession == sessionRevision) apiFailed(failure) }
            finally { if (currentGeneration == generation) busy = false }
        }
    }

    /** Only called after the user confirms Disconnect. Revokes the current app grant, not a Google account. */
    fun disconnect() {
        if (closed) return
        generation++
        dataJob?.cancel()
        nativeAuthorization?.cancel(); nativeAuthorization = null; grantedScopes = emptySet()
        invalidateGrant(); linked = false; loaded = false; attemptedRestore = true
        revocationPending = true
        preferences.edit().putBoolean("linked", false).putBoolean("revocation_pending", true).apply()
        busy = true; status = "Lokale Kontodaten gelöscht. Google-Zugriff wird widerrufen …"
        try {
            client.revokeAccess(RevokeAccessRequest.builder().setScopes(scopes).build())
                .addOnSuccessListener { if (!closed) {
                    busy = false; revocationPending = false
                    preferences.edit().putBoolean("revocation_pending", false).apply()
                    status = "Google-Freigabe für Kontofunktionen widerrufen."
                } }
                .addOnFailureListener { if (!closed) { busy = false; status = "Lokale Kontodaten sind gelöscht. Google konnte den Zugriff nicht widerrufen. Bitte unter myaccount.google.com/connections Weiktube entfernen oder erneut trennen." } }
        } catch (_: Exception) { busy = false; status = "Lokale Kontodaten gelöscht. Bitte den Zugriff zusätzlich unter myaccount.google.com/connections entfernen." }
    }

    private suspend fun fetchPage(resource: String, accessToken: String, pageToken: String = "", playlistId: String = ""): AccountPage {
        require(resource in setOf("channels", "subscriptions", "playlists", "playlistItems"))
        val part = if (resource == "channels") "snippet,statistics" else if (resource == "playlists") "snippet,contentDetails" else "snippet"
        val filter = if (resource == "playlistItems") "playlistId=${URLEncoder.encode(playlistId, "UTF-8")}" else "mine=true"
        val query = "part=${URLEncoder.encode(part, "UTF-8")}&$filter&maxResults=50" +
            (if (resource == "subscriptions") "&order=alphabetical" else "") +
            (if (pageToken.isNotEmpty()) "&pageToken=${URLEncoder.encode(pageToken, "UTF-8")}" else "")
        val connection = URL("https://www.googleapis.com/youtube/v3/$resource?$query").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000; connection.readTimeout = 15000
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            val response = connection.responseCode
            val input = if (response == 200) connection.inputStream else connection.errorStream
            val body = java.io.ByteArrayOutputStream()
            input?.use { stream ->
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = stream.read(buffer); if (count < 0) break
                    require(body.size() + count <= 2 * 1024 * 1024) { "Die YouTube-Antwort ist zu groß." }
                    body.write(buffer, 0, count)
                }
            }
            val json = if (body.size() == 0) JSONObject() else JSONObject(body.toString("UTF-8"))
            if (response != 200) throw YoutubeApiFailure(response, json.optJSONObject("error")?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty())
            val items = json.optJSONArray("items") ?: org.json.JSONArray()
            val rows = (0 until items.length()).mapNotNull { index ->
                val item = items.getJSONObject(index)
                val snippet = item.optJSONObject("snippet") ?: return@mapNotNull null
                val id = if (resource == "subscriptions") snippet.optJSONObject("resourceId")?.optString("channelId").orEmpty() else item.optString("id")
                if (!id.matches(Regex("[A-Za-z0-9_-]{1,128}"))) return@mapNotNull null
                val videoId = snippet.optJSONObject("resourceId")?.optString("videoId").orEmpty()
                if (resource == "playlistItems" && !videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) return@mapNotNull null
                val url = when (resource) {
                    "playlists" -> "https://m.youtube.com/playlist?list=$id"
                    "playlistItems" -> "https://www.youtube.com/watch?v=$videoId"
                    else -> "https://m.youtube.com/channel/$id"
                }
                val thumbnails = snippet.optJSONObject("thumbnails")
                val image = (thumbnails?.optJSONObject("medium") ?: thumbnails?.optJSONObject("default"))?.optString("url").orEmpty()
                YoutubeAccountItem(id, snippet.optString("title").take(300), snippet.optString("description").take(500), url,
                    image.takeIf { it.startsWith("https://") }.orEmpty(), item.optString("id"))
            }
            return AccountPage(rows, json.optString("nextPageToken").take(4096))
        } finally { connection.disconnect() }
    }

    private fun apiFailed(failure: Exception) {
        status = when {
            failure is AccountConsentRequired -> {
                invalidateGrant()
                "Bitte den Zugriff auf deine Kontodaten erneut freigeben."
            }
            failure is YoutubeApiFailure && failure.http == 401 -> {
                invalidateGrant()
                "Die Google-Freigabe für Kontofunktionen ist abgelaufen. Bitte erneut bestätigen."
            }
            failure is YoutubeApiFailure && failure.reason in setOf("accessNotConfigured", "serviceDisabled") ->
                "Die YouTube Data API ist im Google-Cloud-Projekt noch nicht aktiviert. Der Entwickler muss sie zuerst freischalten."
            failure is YoutubeApiFailure && failure.reason in setOf("quotaExceeded", "dailyLimitExceeded") -> "Das YouTube-API-Tageskontingent ist ausgeschöpft. Bitte später erneut versuchen."
            failure is YoutubeApiFailure && failure.http == 403 -> "YouTube hat den Zugriff abgelehnt. Bitte Freigaben, OAuth-Testnutzer und aktivierte YouTube Data API prüfen."
            failure is java.net.UnknownHostException || failure is java.net.SocketTimeoutException -> "YouTube ist nicht erreichbar. Bitte die Internetverbindung prüfen und aktualisieren."
            else -> "Deine YouTube-Daten konnten nicht geladen werden. Bitte erneut aktualisieren."
        }
    }

    /** Native API access only. Tokens never leave this controller. GET may refresh once; writes never replay. */
    suspend fun authorizeUserAction() { obtainNativeToken(write = true, interactive = true) }
    fun hasInteractionPermission(): Boolean = connected && YOUTUBE_WRITE in grantedScopes

    suspend fun requestApi(resource: String, parameters: Map<String, String>, method: String = "GET", body: JSONObject? = null, expectedSession: Long? = null): JSONObject {
        require(resource in setOf("videos", "videos/getRating", "videos/rate", "channels", "subscriptions", "commentThreads", "comments", "playlists", "playlistItems"))
        require(method in setOf("GET", "POST", "DELETE"))
        val write = method != "GET"
        val expected = generation
        expectedSession?.let(sessionGuard::check)
        // Consent is launched only by authorizeUserAction(), never implicitly by API transport.
        var accessToken = obtainNativeToken(write, interactive = false)
        ensureGeneration(expected)
        expectedSession?.let(sessionGuard::check)
        var requestSession = sessionGuard.capture()
        try {
            val response = withContext(Dispatchers.IO) { nativeRequest(resource, parameters, method, body, accessToken, requestSession) }
            ensureGeneration(expected); sessionGuard.check(requestSession)
            return response
        } catch (failure: YoutubeApiFailure) {
            if (!VideoActionPolicy.retryAutomatically(method, failure.http, 0)) throw failure
            nativeAuthorizationMutex.withLock {
                ensureGeneration(expected); sessionGuard.check(requestSession)
                if (token == accessToken) {
                    client.clearToken(ClearTokenRequest.builder().setToken(accessToken).build()).awaitResult()
                    ensureGeneration(expected); sessionGuard.check(requestSession)
                    token = null; grantedScopes = emptySet()
                }
            }
            try { accessToken = obtainNativeToken(write = false, interactive = false) }
            catch (renewalFailure: Exception) {
                // Never leave the old account presented as usable after token renewal failed.
                if (!closed && expected == generation && requestSession == sessionGuard.capture() && token == null) {
                    invalidateGrant()
                    status = "Bitte unter „Du“ die Google-Freigabe erneut bestätigen."
                }
                throw renewalFailure
            }
            ensureGeneration(expected)
            expectedSession?.let(sessionGuard::check)
            requestSession = sessionGuard.capture()
            val response = withContext(Dispatchers.IO) { nativeRequest(resource, parameters, method, body, accessToken, requestSession) }
            ensureGeneration(expected); sessionGuard.check(requestSession)
            return response
        } finally {
            if (write && connected && !loaded && !busy && !closed) refresh()
        }
    }

    private suspend fun obtainNativeToken(write: Boolean, interactive: Boolean): String = nativeAuthorizationMutex.withLock {
        val required = if (write) setOf(YOUTUBE_READONLY, YOUTUBE_WRITE) else setOf(YOUTUBE_READONLY)
        if (closed) throw CancellationException("Account controller closed")
        token?.takeIf { grantedScopes.containsAll(required) }?.let { return@withLock it }
        if (write && !interactive) throw IllegalStateException("Bitte die zusätzliche Google-Freigabe zuerst über die gewünschte Aktion bestätigen.")
        if (!interactive && !linked) throw IllegalStateException("Bitte zuerst unter „Du“ Abos und Playlists freigeben.")
        if (busy || resolutionPending) throw IllegalStateException("Die Google-Freigabe wird gerade aktualisiert. Bitte kurz warten und erneut versuchen.")
        val expected = generation
        busy = true
        try {
            val result = client.authorize(AuthorizationRequest.builder().setRequestedScopes(required.map(::Scope)).build()).awaitResult()
            ensureGeneration(expected)
            if (result.hasResolution()) {
                if (!interactive) throw IllegalStateException("Bitte unter „Du“ die Google-Freigabe bestätigen.")
                val pending = result.pendingIntent ?: throw IllegalStateException("Google hat keine Freigabe bereitgestellt.")
                val waiting = CompletableDeferred<String>()
                nativeAuthorization = waiting; nativeAuthorizationScopes = required
                resolutionGeneration = expected
                try {
                    resolutionPending = true
                    resolutionLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                    return@withLock waiting.await().also { ensureGeneration(expected) }
                } catch (failure: Exception) {
                    if (failure !is CancellationException) resolutionPending = false
                    throw failure
                } finally {
                    if (nativeAuthorization === waiting) nativeAuthorization = null
                }
            }
            return@withLock acceptNativeGrant(result, required)
        } finally {
            if (expected == generation && !closed) busy = resolutionPending
        }
    }

    private suspend fun nativeRequest(resource: String, parameters: Map<String, String>, method: String, body: JSONObject?, accessToken: String, requestSession: Long): JSONObject {
        currentCoroutineContext().ensureActive(); sessionGuard.check(requestSession)
        val query = parameters.entries.joinToString("&") { "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" }
        val connection = URL("https://www.googleapis.com/youtube/v3/$resource?$query").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false; connection.connectTimeout = 15000; connection.readTimeout = 15000
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            currentCoroutineContext().ensureActive(); sessionGuard.check(requestSession)
            if (body != null) {
                connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            currentCoroutineContext().ensureActive(); sessionGuard.check(requestSession)
            val response = connection.responseCode
            val bytes = java.io.ByteArrayOutputStream()
            (if (response in 200..299) connection.inputStream else connection.errorStream)?.use { stream ->
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive(); sessionGuard.check(requestSession)
                    val count = stream.read(buffer); if (count < 0) break
                    require(bytes.size() + count <= 2 * 1024 * 1024) { "Die YouTube-Antwort ist zu groß." }
                    bytes.write(buffer, 0, count)
                }
            }
            val json = if (bytes.size() == 0) JSONObject() else runCatching { JSONObject(bytes.toString("UTF-8")) }.getOrElse {
                if (response !in 200..299) throw YoutubeApiFailure(response, "invalidResponse")
                throw IllegalStateException("Ungültige YouTube-Antwort.")
            }
            if (response !in 200..299) {
                val reason = json.optJSONObject("error")?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty()
                // Diagnostic only: never log query values, account data, response bodies or tokens.
                android.util.Log.w("WeiktubeApi", "$method /$resource HTTP $response reason=${reason.filter { it.isLetterOrDigit() || it == '_' }.take(80)}")
                throw YoutubeApiFailure(response, reason)
            }
            sessionGuard.check(requestSession)
            return json
        } finally { connection.disconnect() }
    }

    fun close() { closed = true; generation++; invalidateGrant(); nativeAuthorization?.cancel(); nativeAuthorization = null; scope.cancel(); resolutionLauncher.unregister() }
    companion object {
        const val YOUTUBE_READONLY = "https://www.googleapis.com/auth/youtube.readonly"
        const val YOUTUBE_WRITE = "https://www.googleapis.com/auth/youtube.force-ssl"
    }
}

