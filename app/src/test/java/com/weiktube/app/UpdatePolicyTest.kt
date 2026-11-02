package com.weiktube.app

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    private fun release() = UpdateRelease(2, "1.1.0", "https://example.org/Weiktube.apk", "a".repeat(64))
    @Test fun acceptsValidManifestAndNormalizesChecksum() {
        assertEquals("a".repeat(64), release().copy(sha256 = "A".repeat(64)).validate().sha256)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsMissingChecksum() { release().copy(sha256 = "").validate() }
    @Test(expected = IllegalArgumentException::class) fun rejectsHttpDownload() { release().copy(apkUrl = "http://example.org/app.apk").validate() }
    @Test fun rejectsCredentialsAndFragments() {
        assertFalse(UpdatePolicy.https("https://password@example.org/app.apk"))
        assertFalse(UpdatePolicy.https("https://example.org/update.json#part"))
        assertFalse(UpdatePolicy.https("file:///tmp/app.apk"))
        assertTrue(UpdatePolicy.https("https://github.com/weikiboy-tech/Weiktube/releases/latest/download/update.json"))
    }
    @Test fun autoCheckRespectsDailyIntervalAndOptOut() {
        val url = "https://example.org/update.json"
        assertTrue(UpdatePolicy.shouldAutoCheck(true, url, 0, 100))
        assertFalse(UpdatePolicy.shouldAutoCheck(true, url, 100, 101))
        assertFalse(UpdatePolicy.shouldAutoCheck(false, url, 0, 100))
        assertFalse(UpdatePolicy.shouldAutoCheck(true, "", 0, 100))
        assertTrue(UpdatePolicy.shouldAutoCheck(true, url, 1, UpdatePolicy.CHECK_INTERVAL_MS + 2))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsDifferentPackage() {
        UpdatePolicy.verifyMetadata(release(), "com.other.app", "com.weiktube.app", 2, 1)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsDowngrade() {
        UpdatePolicy.verifyMetadata(release(), "com.weiktube.app", "com.weiktube.app", 2, 3)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsManifestVersionMismatch() {
        UpdatePolicy.verifyMetadata(release(), "com.weiktube.app", "com.weiktube.app", 3, 1)
    }
    @Test fun acceptsMatchingUpgrade() {
        UpdatePolicy.verifyMetadata(release(), "com.weiktube.app", "com.weiktube.app", 2, 1)
    }
}
