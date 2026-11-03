package com.weiktube.app

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/** Foreground update checks. No silent install, background service, credentials or arbitrary package installs. */
class UpdateController(private val activity: Activity) {
    private val preferences = activity.getSharedPreferences("weiktube_updates", android.content.Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val outputDirectory = File(activity.cacheDir, "updates")
    private val output = File(outputDirectory, "Weiktube-update.apk")
    var feedUrl by mutableStateOf(preferences.getString("feed", "https://github.com/weikiboy-tech/Weiktube/releases/latest/download/update.json").orEmpty()); private set
    var automatic by mutableStateOf(preferences.getBoolean("automatic", true)); private set
    var busy by mutableStateOf(false); private set
    var status by mutableStateOf("Noch keine Updatequelle eingerichtet."); private set
    var release by mutableStateOf<UpdateRelease?>(null); private set
    var ready by mutableStateOf(false); private set
    var progress by mutableIntStateOf(0); private set
    val installedVersion: String = installed().versionName ?: "unbekannt"

    init { if (feedUrl.isNotBlank()) status = "Updatequelle eingerichtet. Noch nicht geprüft." }

    fun saveSource(value: String) {
        if (busy) return
        val source = value.trim()
        if (source.isNotEmpty() && !UpdatePolicy.https(source)) {
            status = "Bitte eine vollständige HTTPS-Adresse zur update.json eingeben."; return
        }
        feedUrl = source
        preferences.edit().putString("feed", source).putLong("checked", 0).apply()
        release = null; ready = false
        output.delete()
        status = if (source.isBlank()) "Noch keine Updatequelle eingerichtet." else "Updatequelle gespeichert."
    }

    fun updateAutomaticEnabled(enabled: Boolean) {
        automatic = enabled
        preferences.edit().putBoolean("automatic", enabled).apply()
        if (enabled) onForeground()
    }

    fun onForeground() {
        if (UpdatePolicy.shouldAutoCheck(automatic, feedUrl, preferences.getLong("checked", 0), System.currentTimeMillis())) check()
    }

    fun check() {
        if (busy) return
        if (!UpdatePolicy.https(feedUrl)) { status = "Für Updates zuerst eine HTTPS-Updatequelle speichern."; return }
        busy = true; ready = false; release = null; status = "Suche nach Updates …"
        val source = feedUrl
        preferences.edit().putLong("checked", System.currentTimeMillis()).apply()
        scope.launch {
            try {
                val candidate = withContext(Dispatchers.IO) {
                    val bytes = java.io.ByteArrayOutputStream()
                    download(source, UpdatePolicy.MAX_FEED_BYTES) { data, length, _ -> bytes.write(data, 0, length) }
                    val json = JSONObject(bytes.toString("UTF-8"))
                    UpdateManifest.parse(json, Build.SUPPORTED_ABIS.toList())
                }
                if (candidate.versionCode > version(installed())) {
                    release = candidate; status = "Weiktube ${candidate.versionName} ist verfügbar."
                } else status = "Weiktube ist aktuell (Version $installedVersion)."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { status = "Updateprüfung fehlgeschlagen: ${safeError(e)}" }
            finally { busy = false }
        }
    }

    fun downloadUpdate() {
        val candidate = release ?: return
        if (busy) return
        busy = true; ready = false; progress = 0; status = "Update wird heruntergeladen …"
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    check(outputDirectory.isDirectory || outputDirectory.mkdirs()) { "Updateordner konnte nicht angelegt werden." }
                    val partial = File(outputDirectory, "download.part")
                    try {
                        partial.outputStream().use { stream ->
                            var previousPercentage = -1
                            download(candidate.apkUrl, UpdatePolicy.MAX_APK_BYTES) { bytes, length, percentage ->
                                stream.write(bytes, 0, length)
                                if (percentage != previousPercentage) { previousPercentage = percentage; scope.launch { progress = percentage } }
                            }
                        }
                        verifyApk(partial, candidate)
                        check(!output.exists() || output.delete()) { "Alte Update-Datei konnte nicht entfernt werden." }
                        check(partial.renameTo(output)) { "Update konnte nicht gespeichert werden." }
                    } finally { partial.delete() }
                }
                ready = true; status = "Update geprüft. Android fragt vor der Installation nach deiner Bestätigung."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { output.delete(); status = "Download abgebrochen: ${safeError(e)}" }
            finally { busy = false }
        }
    }

    fun install() {
        val candidate = release ?: return
        if (!ready || busy) return
        if (!activity.packageManager.canRequestPackageInstalls()) {
            status = "Erlaube in Android „Apps aus dieser Quelle“, kehre zurück und tippe erneut auf Installieren."
            try { activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${activity.packageName}".toUri())) }
            catch (_: Exception) { status = "Android-Einstellung nicht erreichbar. Bitte die Installationsfreigabe für Weiktube manuell öffnen." }
            return
        }
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { verifyApk(output, candidate) }
                val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", output)
                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                activity.startActivity(intent)
                status = "Android-Installation geöffnet. Bei Abbruch kannst du erneut auf Installieren tippen."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { ready = false; status = "Installation nicht gestartet: ${safeError(e)}" }
            finally { busy = false }
        }
    }

    private suspend fun download(address: String, limit: Long, consume: (ByteArray, Int, Int) -> Unit) {
        var addressToOpen = address
        val started = android.os.SystemClock.elapsedRealtime()
        repeat(6) { redirect ->
            require(UpdatePolicy.https(addressToOpen)) { "Unsichere Updateadresse oder Weiterleitung abgelehnt." }
            currentCoroutineContext().ensureActive()
            val connection = URL(addressToOpen).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15000; connection.readTimeout = 15000
                connection.setRequestProperty("User-Agent", "Weiktube-Updater/$installedVersion")
                connection.setRequestProperty("Accept-Encoding", "identity")
                val response = connection.responseCode
                if (response in listOf(301, 302, 303, 307, 308)) {
                    require(redirect < 5) { "Zu viele Weiterleitungen." }
                    addressToOpen = URL(URL(addressToOpen), connection.getHeaderField("Location") ?: error("Weiterleitung ohne Ziel.")).toString()
                } else {
                    require(response == 200) { "Der Updateserver antwortet mit HTTP $response." }
                    val declared = connection.contentLengthLong
                    require(declared <= limit) { "Die Updatedatei ist zu groß." }
                    var total = 0L
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(32768)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            require(android.os.SystemClock.elapsedRealtime() - started < 180000) { "Zeitlimit beim Download überschritten." }
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= limit) { "Die Updatedatei ist zu groß." }
                            consume(buffer, count, if (declared > 0) (100 * total / declared).toInt().coerceIn(0, 100) else 0)
                        }
                    }
                    require(total > 0 && (declared < 0 || total == declared)) { "Der Download ist unvollständig." }
                    return
                }
            } finally { connection.disconnect() }
        }
        error("Zu viele Weiterleitungen.")
    }

    private fun verifyApk(file: File, candidate: UpdateRelease) {
        require(file.isFile && file.length() in 1..UpdatePolicy.MAX_APK_BYTES) { "Keine vollständige Update-Datei vorhanden." }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream -> val b = ByteArray(32768); while (true) { val n = stream.read(b); if (n < 0) break; digest.update(b, 0, n) } }
        require(digest.digest().hex() == candidate.sha256) { "SHA-256 stimmt nicht überein. Datei wird nicht installiert." }
        val current = installed()
        val archive = activity.packageManager.getPackageArchiveInfo(file.absolutePath, signingFlags()) ?: error("Ungültige Android-APK.")
        UpdatePolicy.verifyMetadata(candidate, archive.packageName, activity.packageName, version(archive), version(current))
        val currentSigners = signers(current)
        require(currentSigners.isNotEmpty() && currentSigners == signers(archive)) { "Die APK hat einen anderen Signierschlüssel. Installation abgelehnt." }
    }

    @Suppress("DEPRECATION")
    private fun installed() = activity.packageManager.getPackageInfo(activity.packageName, signingFlags())
    @Suppress("DEPRECATION")
    private fun signingFlags() = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    @Suppress("DEPRECATION")
    private fun version(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return signatures.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).hex() }.toSet()
    }
    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 255) }
    private fun safeError(error: Exception): String = when (error) {
        is java.net.UnknownHostException -> "Server nicht erreichbar. Internet und Updateadresse prüfen."
        is java.net.SocketTimeoutException -> "Zeitüberschreitung. Bitte später erneut versuchen."
        is javax.net.ssl.SSLException -> "Die HTTPS-Verbindung konnte nicht sicher aufgebaut werden."
        else -> error.message?.take(220) ?: "Unbekannter Fehler."
    }
    fun close() { scope.cancel() }
}

