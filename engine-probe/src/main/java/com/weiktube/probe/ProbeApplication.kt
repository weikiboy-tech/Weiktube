package com.weiktube.probe

import android.app.Application
import org.mozilla.geckoview.GeckoRuntime

class ProbeApplication : Application() {
    val runtime: GeckoRuntime by lazy { GeckoRuntime.create(this) }
}
