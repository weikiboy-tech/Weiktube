package com.weiktube.filter

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.PublicKey
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

data class AdBlockStatus(
    val initialized: Boolean = false,
    val enabled: Boolean = true,
    val version: Int = 0,
    val networkBlocked: Long = 0,
    val dataRemoved: Long = 0,
    val cosmeticHidden: Long = 0,
    val breakthroughs: Long = 0,
    val updating: Boolean = false,
    val engineErrors: Map<String, String> = emptyMap(),
    val message: String = "Werbeschutz wird vorbereitet …",
)

/** One rule snapshot provider, shared by WebView and Gecko. It never owns website sessions. */
object YoutubeAdBlock {
    private const val ASSETS = "weiktube-adblock/"
    private const val UPDATE_ROOT = "https://github.com/weikiboy-tech/Weiktube/releases/download/adblock-rules-v1/"
    private const val CHECK_INTERVAL = 24L * 60 * 60 * 1000
    private val mutableState = MutableStateFlow(AdBlockStatus())
    val state: StateFlow<AdBlockStatus> = mutableState.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val updating = AtomicBoolean(false)
    @Volatile private var context: Context? = null
    @Volatile private var snapshot = FilterSnapshot(0, emptyList(), emptyList(), emptyList())
    private var publicKey: PublicKey? = null
    private var vendorScript = ""
    private var runtimeScript = ""
    @Volatile var enabled: Boolean = true
        set(value) {
            field = value
            mutableState.update { it.copy(enabled = value) }
        }
    @Volatile var exceptions: Set<String> = emptySet()
        set(value) { field = value.map { it.lowercase().trim().trimEnd('.') }.filter { it.isNotEmpty() }.toSet() }

    @Synchronized fun initialize(context: Context) {
        if (this.context != null) return
        val app = context.applicationContext
        this.context = app
        try {
            val key = SignedFilterPackage.publicKey(app.assets.open(ASSETS + "update-public-key.txt").bufferedReader().use { it.readText() })
            val bundled = app.assets.open(ASSETS + "rules.json").use { readLimited(it, FilterSnapshot.MAX_BYTES) }
            val signature = app.assets.open(ASSETS + "rules.sig").bufferedReader().use { it.readText() }
            val builtin = SignedFilterPackage.verify(bundled, signature, key)
            vendorScript = app.assets.open(ASSETS + "vendor-scriptlets.js").bufferedReader().use { it.readText() }
            runtimeScript = app.assets.open(ASSETS + "filter-runtime.js").bufferedReader().use { it.readText() }
            require(vendorScript.isNotBlank() && runtimeScript.isNotBlank())
            publicKey = key
            snapshot = builtin
            var discarded = false
            val cache = cache(app)
            if (cache.baseFile.exists()) {
                runCatching {
                    val envelope = cache.openRead().use { readLimited(it, 2 * FilterSnapshot.MAX_BYTES) }
                    val data = JSONObject(envelope.toString(Charsets.UTF_8))
                    val bytes = Base64.getDecoder().decode(data.getString("payload"))
                    snapshot = SignedFilterPackage.verify(bytes, data.getString("signature"), key, builtin.version)
                }.onFailure { discarded = true }
            }
            mutableState.update { it.copy(initialized = true, enabled = enabled, version = snapshot.version,
                message = if (discarded) "Ungültiges Update verworfen. Mitgelieferte Filter sind bereit."
                    else "Filter bereit · kein Nachweis, dass jede Anzeige erkannt wird.") }
        } catch (_: Exception) {
            mutableState.update { it.copy(initialized = false,
                message = "Werbeschutz konnte nicht vorbereitet werden. Bitte die App aktualisieren.") }
        }
    }

    fun currentSnapshot(): FilterSnapshot = snapshot

    fun configJson(snapshot: FilterSnapshot, token: String, active: Boolean = enabled,
                   excluded: Set<String> = exceptions): JSONObject = snapshot.toJson()
        .put("token", token).put("enabled", active).put("exceptions", JSONArray(excluded.toList()))

    fun script(snapshot: FilterSnapshot, token: String, active: Boolean = enabled,
               excluded: Set<String> = exceptions): String {
        if (!state.value.initialized) return "window.__weiktubeAdBlock={ready:false,version:0};"
        require(token.length <= 256) { "FILTER_TOKEN" }
        return "window.__weiktubeAdConfig=" + configJson(snapshot, token, active, excluded).toString() + ";\n" +
            vendorScript + "\n" + runtimeScript
    }

    fun shouldBlock(snapshot: FilterSnapshot, url: String, topLevelUrl: String, mainFrame: Boolean,
                    active: Boolean = enabled, excluded: Set<String> = exceptions): Boolean =
        FilterNetwork.blocked(snapshot, url, topLevelUrl, mainFrame, active, excluded).also { if (it) report("network") }

    /** Only aggregate event types; adapters must validate origin, session and document token first. */
    fun report(type: String, count: Int = 1) {
        if (count !in 1..10000) return
        mutableState.update { current ->
            when (type) {
                "network" -> current.copy(networkBlocked = current.networkBlocked + count)
                "data" -> current.copy(dataRemoved = current.dataRemoved + count)
                "cosmetic" -> current.copy(cosmeticHidden = current.cosmeticHidden + count)
                "breakthrough" -> current.copy(breakthroughs = current.breakthroughs + count)
                else -> current
            }
        }
    }

    fun resetCounters() {
        mutableState.update { it.copy(networkBlocked = 0, dataRemoved = 0, cosmeticHidden = 0, breakthroughs = 0) }
    }

    fun engineError(engine: String, code: String) {
        if (engine !in setOf("webview", "gecko")) return
        val safeCode = code.takeIf { it in setOf("FILTER_NOT_READY", "DOCUMENT_START_UNSUPPORTED",
            "SCRIPTLET_INSTALL_FAILED", "BRIDGE_UNAVAILABLE", "FILTER_RUNTIME_ERROR") } ?: "FILTER_RUNTIME_ERROR"
        mutableState.update { it.copy(engineErrors = it.engineErrors + (engine to safeCode),
            message = "Werbeschutz in ${if (engine == "gecko") "der Startseite" else "der Wiedergabe"} nicht bereit ($safeCode).") }
    }

    fun engineReady(engine: String) {
        if (engine !in setOf("webview", "gecko") || engine !in state.value.engineErrors) return
        mutableState.update { current ->
            val remaining = current.engineErrors - engine
            current.copy(engineErrors = remaining,
                message = if (remaining.isEmpty()) "Filter bereit · kein Nachweis, dass jede Anzeige erkannt wird." else current.message)
        }
    }

    fun checkForUpdates(manual: Boolean = false) {
        val app = context ?: return
        val key = publicKey ?: return
        if (!state.value.initialized || !updating.compareAndSet(false, true)) return
        val prefs = app.getSharedPreferences("weiktube_filter_updates", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val elapsed = now - prefs.getLong("last_attempt", 0L)
        if (!manual && elapsed in 0 until CHECK_INTERVAL) { updating.set(false); return }
        prefs.edit().putLong("last_attempt", now).apply()
        mutableState.update { it.copy(updating = true, message = "Filterupdate wird geprüft …") }
        scope.launch {
            try {
                val json = download(UPDATE_ROOT + "youtube-filter.json", FilterSnapshot.MAX_BYTES)
                val signature = download(UPDATE_ROOT + "youtube-filter.sig", 4096).toString(Charsets.UTF_8)
                val candidate = SignedFilterPackage.verify(json, signature, key, snapshot.version)
                if (candidate.version > snapshot.version) {
                    val envelope = JSONObject().put("payload", Base64.getEncoder().encodeToString(json))
                        .put("signature", signature.trim()).toString().toByteArray(Charsets.UTF_8)
                    val file = cache(app)
                    val stream = file.startWrite()
                    try { stream.write(envelope); file.finishWrite(stream) }
                    catch (e: Exception) { file.failWrite(stream); throw e }
                    // Existing documents keep their captured snapshot. New documents use this revision.
                    snapshot = candidate
                    mutableState.update { it.copy(version = candidate.version,
                        message = "Filter aktualisiert. Neue Regeln gelten ab dem nächsten Video/Seitenaufruf.") }
                } else mutableState.update { it.copy(message = "Die Filter sind auf dem neuesten veröffentlichten Stand.") }
            } catch (_: Exception) {
                mutableState.update { it.copy(message = "Update nicht verfügbar oder ungültig. Vorhandene Filter bleiben aktiv.") }
            } finally {
                updating.set(false)
                mutableState.update { it.copy(updating = false) }
            }
        }
    }

    private fun cache(app: Context): AtomicFile {
        val directory = File(app.filesDir, "weiktube-adblock")
        check(directory.isDirectory || directory.mkdirs())
        return AtomicFile(File(directory, "rules.signed.json"))
    }

    private fun download(address: String, limit: Int): ByteArray {
        var current = URL(address)
        repeat(5) {
            require(FilterUpdatePolicy.trusted(current)) { "FILTER_UPDATE_ORIGIN" }
            val connection = current.openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10000; connection.readTimeout = 10000
                connection.setRequestProperty("User-Agent", "Weiktube-FilterUpdater/1")
                connection.setRequestProperty("Accept", "application/octet-stream,application/json,text/plain")
                connection.setRequestProperty("Accept-Encoding", "identity")
                val status = connection.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    current = URL(current, connection.getHeaderField("Location") ?: error("FILTER_REDIRECT"))
                } else {
                    require(status == 200 && (connection.contentLengthLong < 0 || connection.contentLengthLong <= limit))
                    return connection.inputStream.use { readLimited(it, limit) }
                }
            } finally { connection.disconnect() }
        }
        error("FILTER_REDIRECT_LIMIT")
    }

    private fun readLimited(stream: java.io.InputStream, limit: Int): ByteArray = FilterUpdatePolicy.readLimited(stream, limit)
}
