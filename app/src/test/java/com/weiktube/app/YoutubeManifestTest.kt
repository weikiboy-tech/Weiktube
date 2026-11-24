package com.weiktube.app

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Uses the merged manifest, not the fake intent routing fixture. Does not emulate domain approval. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class YoutubeManifestTest {
    @Test fun existingActivityRemainsExportedSingleTask() {
        val app=RuntimeEnvironment.getApplication()
        val info=app.packageManager.getActivityInfo(ComponentName(app,MainActivity::class.java),0)
        assertTrue(info.exported)
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK,info.launchMode)
    }
    @Test fun registeredHttpsDomainsResolveToExistingActivity() {
        val app=RuntimeEnvironment.getApplication()
        for(host in listOf("youtube.com","www.youtube.com","m.youtube.com","youtu.be")) {
            val i=Intent(Intent.ACTION_VIEW,Uri.parse("https://$host/watch?v=abcdefghijk")).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(app.packageName)
            val found=app.packageManager.queryIntentActivities(i,0)
            assertTrue(host,found.any { it.activityInfo.name==MainActivity::class.java.name })
        }
        val foreign=Intent(Intent.ACTION_VIEW,Uri.parse("https://youtube.com.evil.org/watch?v=abcdefghijk")).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(app.packageName)
        assertTrue(app.packageManager.queryIntentActivities(foreign,0).isEmpty())
    }
}
