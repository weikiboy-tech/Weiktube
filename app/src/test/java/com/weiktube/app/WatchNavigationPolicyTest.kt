package com.weiktube.app

import org.junit.Assert.*
import org.junit.Test

class WatchNavigationPolicyTest {
    @Test fun handsOffOnlyPublicNavigationAndGoogleLogin() {
        assertNotNull(WatchNavigationScript.allowed("https://m.youtube.com/watch?v=abcdefghijk"))
        assertNotNull(WatchNavigationScript.allowed("https://m.youtube.com/@creator"))
        assertNotNull(WatchNavigationScript.allowed("https://m.youtube.com/channel/UC_123"))
        assertNotNull(WatchNavigationScript.allowed("https://m.youtube.com/playlist?list=PL_123"))
        assertNotNull(WatchNavigationScript.allowed("https://accounts.google.com/ServiceLogin?service=youtube"))
    }
    @Test fun leavesAdvertRedirectsAndExternalDestinationsUntouched() {
        listOf("https://m.youtube.com/redirect?q=advert", "https://advert.example.org/", "https://youtube.com.evil.org/@creator",
            "https://user:pass@youtube.com/@creator", "https://youtube.com:8443/@creator", "javascript:alert(1)",
            "https://m.youtube.com/playlist?list=bad%2Fpath").forEach { assertNull(it, WatchNavigationScript.allowed(it)) }
    }
}
