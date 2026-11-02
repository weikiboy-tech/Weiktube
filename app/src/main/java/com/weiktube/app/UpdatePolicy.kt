package com.weiktube.app

import java.net.URI

/** Input policy shared by updater and plain JVM tests. */
data class UpdateRelease(val versionCode: Long, val versionName: String, val apkUrl: String, val sha256: String, val notes: String = "") {
    fun validate(): UpdateRelease {
        require(versionCode > 0) { "Die Updatequelle enthält keine gültige Versionsnummer." }
        require(versionName.isNotBlank() && versionName.length <= 80) { "Ungültiger Versionsname in der Updatequelle." }
        require(UpdatePolicy.https(apkUrl)) { "Die APK-Adresse muss eine gültige HTTPS-Adresse sein." }
        require(sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "Die Updatequelle enthält keine gültige SHA-256-Prüfsumme." }
        require(notes.length <= 10000) { "Die Versionshinweise sind zu lang." }
        return copy(sha256 = sha256.lowercase())
    }
}

object UpdatePolicy {
    const val MAX_FEED_BYTES = 65536L
    const val MAX_APK_BYTES = 128L * 1024 * 1024
    const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    fun https(url: String): Boolean = runCatching {
        val uri = URI(url)
        url.length <= 4096 && uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
    }.getOrDefault(false)
    fun shouldAutoCheck(enabled: Boolean, source: String, lastCheck: Long, now: Long): Boolean =
        enabled && https(source) && (lastCheck == 0L || now < lastCheck || now - lastCheck >= CHECK_INTERVAL_MS)
    fun verifyMetadata(expected: UpdateRelease, packageName: String, installedPackage: String, archiveVersion: Long, currentVersion: Long) {
        require(packageName == installedPackage) { "Die APK gehört nicht zu Weiktube." }
        require(archiveVersion == expected.versionCode) { "APK und Updatequelle enthalten verschiedene Versionen." }
        require(archiveVersion > currentVersion) { "Die APK ist nicht neuer als die installierte Version." }
    }
}
