package com.weiktube.app

import androidx.datastore.preferences.core.*
import org.junit.Assert.*
import org.junit.Test

class PlayerPreferencesTest {
    @Test fun defaultsAreConservativeAndDislikesRequireConsent() {
        val p = PlayerPreferences.read(emptyPreferences())
        assertEquals(10,p.seekSeconds); assertEquals(2f,p.holdSpeed)
        assertEquals(1080,p.wifiQuality); assertEquals(720,p.mobileQuality); assertFalse(p.dislikes)
    }
    @Test fun existingDatastoreKeysRoundTripAndInvalidValuesFallBack() {
        val p = mutablePreferencesOf(booleanPreferencesKey("playerVolume") to false,
            stringPreferencesKey("playerSeek") to "30", stringPreferencesKey("playerHoldSpeed") to "1.5",
            stringPreferencesKey("playerWifiQuality") to "2160", booleanPreferencesKey("playerDislikes") to true)
        val result = PlayerPreferences.read(p.toPreferences())
        assertFalse(result.volume); assertEquals(30,result.seekSeconds); assertEquals(1.5f,result.holdSpeed)
        assertEquals(2160,result.wifiQuality); assertTrue(result.dislikes)
        p[stringPreferencesKey("playerSeek")]="999"; p[stringPreferencesKey("playerHoldSpeed")]="NaN"
        assertEquals(10,PlayerPreferences.read(p).seekSeconds); assertEquals(2f,PlayerPreferences.read(p).holdSpeed)
    }
    @Test fun wifiMobileMeteredAndUnknownNetworksSelectCorrectPreference() {
        val p=PlayerPreferences()
        assertEquals(1080,PlayerQuality.preferred(PlayerQuality.classify(true,false,false),p))
        assertEquals(720,PlayerQuality.preferred(PlayerQuality.classify(false,true,true),p))
        assertEquals(720,PlayerQuality.preferred(PlayerQuality.classify(true,false,true),p))
        assertEquals(0,PlayerQuality.preferred(PlayerQuality.classify(false,false,false),p))
    }
}
