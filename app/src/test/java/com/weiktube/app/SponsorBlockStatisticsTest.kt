package com.weiktube.app

import android.content.Context
import androidx.datastore.preferences.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class SponsorBlockStatisticsTest {
    @Test fun defaultsRequireConsentAndOnlyThreeCategoriesAreAutomatic(){
        val p=SponsorBlockPreferences.read(emptyPreferences())
        assertFalse(p.enabled);assertTrue(p.markers);assertEquals(3,p.modes.values.count {it==SponsorMode.AUTO})
        assertEquals(SponsorMode.MANUAL,p.modes["intro"]);assertEquals(SponsorMode.OFF,p.modes["filler"])
        assertNull(p.modes["exclusive_access"])
    }
    @Test fun settingsRoundTripThroughExistingPreferenceKeys(){
        val values=mutablePreferencesOf(booleanPreferencesKey("sponsorEnabled") to true,stringPreferencesKey("sponsorMode_intro") to "AUTO",
            booleanPreferencesKey("sponsorMarkers") to false,stringPreferencesKey("sponsorMode_filler") to "bad")
        val p=SponsorBlockPreferences.read(values.toPreferences())
        assertTrue(p.enabled);assertFalse(p.markers);assertEquals(SponsorMode.AUTO,p.modes["intro"]);assertEquals(SponsorMode.OFF,p.modes["filler"])
    }
    @Test fun confirmedUnionCountDeduplicatesAndUndoRollsBackOnce(){
        val prefs=RuntimeEnvironment.getApplication().getSharedPreferences("stats_${System.nanoTime()}",Context.MODE_PRIVATE)
        val s=SponsorBlockStatistics(prefs)
        s.record("token:1","sponsor",39.0);s.record("token:1","sponsor",39.0)
        assertEquals(1L,s.totals.count);assertEquals(39.0,s.totals.seconds,0.0);assertEquals(1L,s.totals.categories["sponsor"])
        s.undo("token:1");s.undo("token:1");assertEquals(0L,s.totals.count);assertEquals(0.0,s.totals.seconds,0.0)
        s.record("token:1","sponsor",39.0);assertEquals(0L,s.totals.count)
        s.record("token:2","intro",10.0);assertEquals(10.0,SponsorBlockStatistics(prefs).totals.seconds,0.0)
        s.reset();s.undo("token:2");assertEquals(0L,s.totals.count)
    }
    @Test fun invalidAndUnknownEventsAreNotCounted(){
        val s=SponsorBlockStatistics(RuntimeEnvironment.getApplication().getSharedPreferences("badstats",Context.MODE_PRIVATE))
        s.reset();s.record("x","exclusive_access",100.0);s.record("y","sponsor",Double.NaN);s.record("z","sponsor",-1.0)
        assertEquals(0L,s.totals.count)
    }
}
