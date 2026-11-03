package com.weiktube.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdateManifestTest {
    private val arm = "https://example.org/Weiktube.apk"
    private val x86 = "https://example.org/Weiktube-x86_64.apk"
    private fun artifact(url: String, hash: String) = JSONObject().put("apkUrl", url).put("sha256", hash.repeat(64))
    private fun legacy() = JSONObject().put("versionCode", 16).put("versionName", "1.3.2")
        .put("apkUrl", arm).put("sha256", "a".repeat(64)).put("notes", "Release notes")
    private fun variants() = legacy().put("apks", JSONObject()
        .put("arm64-v8a", artifact(arm, "a")).put("x86_64", artifact(x86, "b")))
    private fun rejected(block: () -> Unit) {
        try { block() } catch (_: IllegalArgumentException) { return } catch (_: org.json.JSONException) { return }
        fail("Invalid or incompatible artifact must not fall back to another APK")
    }

    @Test fun oldManifestRetainsExistingBehaviorWithoutAbiMap() {
        val release = UpdateManifest.parse(legacy(), listOf("unknown"))
        assertEquals(arm, release.apkUrl); assertEquals("a".repeat(64), release.sha256)
        assertEquals(16L, release.versionCode); assertEquals("1.3.2", release.versionName)
        assertEquals("Release notes", release.notes)
    }

    @Test fun arm64SelectsMatchingArtifactAndSharedReleaseMetadata() {
        val json = variants()
        json.getJSONObject("apks").getJSONObject("arm64-v8a")
            .put("versionCode", 999).put("versionName", "unrelated").put("notes", "unrelated")
        val release = UpdateManifest.parse(json, listOf("arm64-v8a", "armeabi-v7a"))
        assertEquals(arm, release.apkUrl); assertEquals(16L, release.versionCode)
        assertEquals("1.3.2", release.versionName); assertEquals("Release notes", release.notes)
    }

    @Test fun x86DoesNotUseLegacyArm64Fallback() {
        val release = UpdateManifest.parse(variants(), listOf("x86_64", "x86"))
        assertEquals(x86, release.apkUrl); assertEquals("b".repeat(64), release.sha256)
    }

    @Test fun deviceAbiPreferenceWinsOverManifestKeyOrder() {
        assertEquals(x86, UpdateManifest.parse(variants(), listOf("x86_64", "arm64-v8a")).apkUrl)
        assertEquals(arm, UpdateManifest.parse(variants(), listOf("arm64-v8a", "x86_64")).apkUrl)
    }

    @Test fun picksFirstAvailableSupportedAbi() {
        assertEquals(arm, UpdateManifest.parse(variants(), listOf("unavailable", "arm64-v8a")).apkUrl)
    }

    @Test fun unknownOrEmptyAbiListFailsInsteadOfUsingLegacyFallback() {
        rejected { UpdateManifest.parse(variants(), listOf("armeabi-v7a")) }
        rejected { UpdateManifest.parse(variants(), emptyList()) }
    }

    @Test fun malformedOrEmptyMapDoesNotFallBack() {
        for (map in listOf(JSONObject.NULL, JSONObject(), JSONArray(), "invalid", 1)) {
            rejected { UpdateManifest.parse(legacy().put("apks", map), listOf("arm64-v8a")) }
        }
    }

    @Test fun malformedSelectedArtifactDoesNotFallBack() {
        for (entry in listOf(JSONObject.NULL, JSONObject(), JSONArray(), "invalid")) {
            val json = variants()
            json.getJSONObject("apks").put("arm64-v8a", entry)
            rejected { UpdateManifest.parse(json, listOf("arm64-v8a", "x86_64")) }
        }
    }

    @Test fun selectedArtifactRequiresSafeHttpsAndChecksum() {
        for (url in listOf("http://example.org/app.apk", "https://secret@example.org/app.apk", "https://example.org/app.apk#part")) {
            val json = variants()
            json.getJSONObject("apks").getJSONObject("arm64-v8a").put("apkUrl", url)
            rejected { UpdateManifest.parse(json, listOf("arm64-v8a")) }
        }
        val json = variants()
        json.getJSONObject("apks").getJSONObject("arm64-v8a").put("sha256", "not-a-checksum")
        rejected { UpdateManifest.parse(json, listOf("arm64-v8a")) }
    }

    @Test fun checksumNormalizationAppliesToSelectedArtifact() {
        val json = variants()
        json.getJSONObject("apks").getJSONObject("x86_64").put("sha256", "B".repeat(64))
        assertEquals("b".repeat(64), UpdateManifest.parse(json, listOf("x86_64")).sha256)
    }

    @Test fun invalidPreferredArtifactCannotFallThroughToAnotherCompatibleAbi() {
        val json = variants()
        json.getJSONObject("apks").getJSONObject("arm64-v8a").put("apkUrl", "http://example.org/app.apk")
        rejected { UpdateManifest.parse(json, listOf("arm64-v8a", "x86_64")) }
    }

    @Test fun sharedVersionValidationStillAppliesWithAbiMap() {
        rejected { UpdateManifest.parse(variants().put("versionCode", 0), listOf("arm64-v8a")) }
        rejected { UpdateManifest.parse(variants().put("versionName", ""), listOf("arm64-v8a")) }
    }
}
