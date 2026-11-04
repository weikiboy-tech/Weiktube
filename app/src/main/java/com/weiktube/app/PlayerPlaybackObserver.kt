package com.weiktube.app

import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.util.UUID

/** Read-only media state from the retained top-level player. Never observes account or media URLs. */
internal object PlayerPlaybackObserver {
    private const val BRIDGE = "WeiktubePlaybackState"
    private val origins = setOf("https://m.youtube.com", "https://www.youtube.com", "https://youtube.com", "https://com.weiktube.app")
    data class State(val playing: Boolean, val width: Int, val height: Int)

    internal fun decode(raw: String, token: String): State? {
        if (raw.length > 512 || token.isEmpty()) return null
        return runCatching {
            val data = JSONObject(raw)
            if (data.length() != 4 || data.opt("token") != token || data.opt("playing") !is Boolean) return null
            fun dimension(key: String): Int? {
                val number = data.opt(key) as? Number ?: return null
                val value = number.toDouble()
                return if (value.isFinite() && value in 0.0..16384.0 && value == value.toInt().toDouble()) value.toInt() else null
            }
            State(data.getBoolean("playing"), dimension("width") ?: return null, dimension("height") ?: return null)
        }.getOrNull()
    }

    fun install(web: WebView, onState: (State) -> Unit): Hooks = Hooks(web, onState)

    class Hooks internal constructor(private val web: WebView, private val onState: (State) -> Unit) {
        private val supported = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        private var script: ScriptHandler? = null
        private var token = ""
        private var closed = false

        init {
            if (supported) WebViewCompat.addWebMessageListener(web, BRIDGE, origins) { sender, message, origin, mainFrame, _ ->
                if (closed || sender !== web || !mainFrame || origin.toString().trimEnd('/') !in origins) return@addWebMessageListener
                val raw = runCatching { message.data }.getOrNull() ?: return@addWebMessageListener
                decode(raw, token)?.let(onState)
            }
        }

        /** Rotate before an explicit document load so a previous video's queued events cannot win. */
        fun prepare() {
            if (closed || !supported) return
            token = UUID.randomUUID().toString()
            script?.remove(); script = null
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
                script = WebViewCompat.addDocumentStartJavaScript(web, command(), origins)
        }

        /** Installs once per document; also reports current state when document-start is unavailable. */
        fun apply() { if (!closed && supported && token.isNotEmpty()) web.evaluateJavascript(command(), null) }
        private fun command() = SCRIPT + ";window.weiktubeObservePlayback&&window.weiktubeObservePlayback(${JSONObject.quote(token)});"

        fun close() {
            if (closed) return
            closed = true; token = ""
            script?.remove(); script = null
            if (supported) WebViewCompat.removeWebMessageListener(web, BRIDGE)
        }
    }

    internal val SCRIPT = """
        (function(){
          if(window.top!==window||!['https://m.youtube.com','https://www.youtube.com','https://youtube.com','https://com.weiktube.app'].includes(location.origin))return;
          if(window.weiktubeObservePlayback)return;
          var token='',last='';
          function report(){
            if(!token||!window.WeiktubePlaybackState)return;
            var v=document.querySelector('video');
            function size(value){return Number.isInteger(value)&&value>=0&&value<=16384?value:0;}
            var raw=JSON.stringify({token:token,playing:!!v&&!v.paused&&!v.ended&&v.readyState>0,
              width:v?size(v.videoWidth):0,height:v?size(v.videoHeight):0});
            if(raw===last)return;
            try{window.WeiktubePlaybackState.postMessage(raw);last=raw;}catch(ignore){}
          }
          ['play','playing','pause','ended','loadedmetadata','emptied','resize'].forEach(function(type){
            document.addEventListener(type,function(e){if(e.target&&e.target.tagName==='VIDEO')report();},true);
          });
          document.addEventListener('DOMContentLoaded',report);
          window.addEventListener('resize',report);
          window.weiktubeObservePlayback=function(next){if(token!==next){token=next;last='';}report();};
        })();
    """.trimIndent()
}
