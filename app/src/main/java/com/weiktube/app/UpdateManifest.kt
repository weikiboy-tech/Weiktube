package com.weiktube.app

import org.json.JSONObject

/** Optional ABI artifacts keep the original feed readable by older ARM64 installations. */
object UpdateManifest {
    fun parse(json: JSONObject, supportedAbis: List<String>): UpdateRelease {
        val artifact = if (!json.has("apks")) json else {
            val apks = json.opt("apks") as? JSONObject
            require(apks != null && apks.length() > 0) { "Die Updatequelle enthält keine gültige APK-Auswahl." }
            val abi = supportedAbis.firstOrNull { apks.has(it) }
            require(abi != null) { "Für dieses Gerät ist keine passende Update-APK verfügbar." }
            val selected = apks.opt(abi) as? JSONObject
            require(selected != null) { "Die passende Update-APK ist in der Updatequelle ungültig." }
            selected
        }
        return UpdateRelease(
            json.getLong("versionCode"), json.getString("versionName"),
            artifact.getString("apkUrl"), artifact.getString("sha256"), json.optString("notes"),
        ).validate()
    }
}
