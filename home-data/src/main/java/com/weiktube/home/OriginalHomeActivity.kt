package com.weiktube.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Compatibility host for the same view/controller now used directly by the Home tab. */
class OriginalHomeActivity : ComponentActivity() {
    private lateinit var home: OriginalHomeController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        home = OriginalHomeController(this, embedded = false,
            onVideo = { token, url ->
                setResult(RESULT_OK, Intent().putExtra(OriginalHomeReturn.EXTRA_RETURN_TOKEN, token)
                    .putExtra(OriginalHomeReturn.EXTRA_VIDEO_URL, url))
                finish()
            },
            onExit = { finish() },
            initialResumeToken = intent.getStringExtra(OriginalHomeReturn.EXTRA_RESUME_TOKEN))
        ViewCompat.setOnApplyWindowInsetsListener(home.view) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        setContentView(home.view)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (!home.back()) finish() }
        })
    }
    override fun onResume() { super.onResume(); home.setVisible(true) }
    override fun onPause() { home.setVisible(false); super.onPause() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (::home.isInitialized) home.onWindowFocusChanged(hasFocus)
    }
    override fun onDestroy() { if (::home.isInitialized) home.close(); super.onDestroy() }
}
