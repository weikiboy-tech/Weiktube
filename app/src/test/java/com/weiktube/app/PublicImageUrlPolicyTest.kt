package com.weiktube.app

import org.junit.Assert.*
import org.junit.Test

class PublicImageUrlPolicyTest {
    @Test fun acceptsKnownPublicImageCdns() {
        listOf("https://i.ytimg.com/vi/abcdefghijk/mqdefault.jpg", "https://yt3.ggpht.com/avatar=s176-c", "https://yt3.googleusercontent.com/avatar").forEach { assertTrue(PublicImageUrlPolicy.allowed(it)) }
    }
    @Test fun rejectsSuffixSpoofingAndUnrelatedRedirectHosts() {
        listOf("https://evilgoogleusercontent.com/a", "https://ytimg.com.evil.example/a", "https://example.org/a").forEach { assertFalse(PublicImageUrlPolicy.allowed(it)) }
    }
    @Test fun rejectsCredentialsCleartextNonstandardPortsAndLocalFiles() {
        listOf("https://user@i.ytimg.com/a", "http://i.ytimg.com/a", "https://i.ytimg.com:8443/a", "file:///avatar.png").forEach { assertFalse(PublicImageUrlPolicy.allowed(it)) }
    }
}
