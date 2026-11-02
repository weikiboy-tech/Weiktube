package com.weiktube.app

import android.annotation.SuppressLint
import android.app.Activity
import android.webkit.*
import androidx.compose.runtime.*
import org.json.JSONObject

/** One WebView instance, attached once. Only a new source loads new HTML. No JS bridge. */
@SuppressLint("SetJavaScriptEnabled")
class PlayerController(private val activity: Activity) {
    var source by mutableStateOf<String?>(null); private set
    var isYoutube by mutableStateOf(true); private set
    var expanded by mutableStateOf(true)
    var playing by mutableStateOf(false); private set
    var position by mutableDoubleStateOf(0.0); private set
    var error by mutableStateOf<String?>(null); private set
    var title by mutableStateOf("Video"); private set
    val view = WebView(activity).apply {
        setBackgroundColor(android.graphics.Color.BLACK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.mediaPlaybackRequiresUserGesture = false // Playback starts only through visible user controls; no autoplay.
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // Keep iframe navigation within the official player. Main-frame links leave the player.
                if (!request.isForMainFrame) return false
                (activity as MainActivity).external(request.url.toString())
                return true
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                if (request.isForMainFrame) error = "Player nicht erreichbar. Internetverbindung prüfen."
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                error = "Der Android-WebView-Prozess wurde beendet. Bitte Weiktube neu starten."
                return true
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: android.view.View, callback: CustomViewCallback) {
                (activity as MainActivity).showFullscreen(view, callback)
            }
            override fun onHideCustomView() { (activity as MainActivity).hideFullscreen() }
        }
    }

    fun open(url: String) {
        if (url == source) { expanded = true; return }
        val id = Links.videoId(url)
        if (id == null && !Links.directVideoAllowed(url)) { error = "Keine unterstützte Video-Adresse."; return }
        source = url; isYoutube = id != null; expanded = true; error = null; position = 0.0; playing = false
        title = if (isYoutube) "YouTube · $id" else "HTTPS-Video"
        val body = if (id != null) """
            <div id="player"></div><script>
            var player, failure='';
            function onYouTubeIframeAPIReady(){player=new YT.Player('player',{
              width:'100%',height:'100%',videoId:'$id',
              playerVars:{playsinline:1,origin:'https://com.weiktube.app',start:${Links.startSeconds(url)}},
              events:{onReady:function(){fitPlayer();},onError:function(e){failure='YouTube-Fehler '+e.data+'. Im Browser öffnen, falls das Video nicht eingebettet werden darf.';}}
            });}
            function fitPlayer(){var f=document.getElementById('player');var h=Math.max(200,window.innerHeight);if(f&&f.clientHeight!==h){f.style.setProperty('height',h+'px','important');f.style.setProperty('display','block','important');if(player&&player.setSize)player.setSize(window.innerWidth,h);}}window.addEventListener('resize',fitPlayer);
            function weiktubeState(){return {playing:!!player&&player.getPlayerState&&player.getPlayerState()===1,
              position:player&&player.getCurrentTime?player.getCurrentTime():0,error:failure,
              title:player&&player.getVideoData?player.getVideoData().title:''};}
            function pause(){if(player&&player.pauseVideo)player.pauseVideo();}
            function toggle(){if(player&&player.getPlayerState){if(player.getPlayerState()===1)player.pauseVideo();else player.playVideo();}}
            </script><script src="https://www.youtube.com/iframe_api"></script>
        """.trimIndent() else """
            <video id="video" controls playsinline style="width:100%;height:100%;object-fit:contain"></video>
            <script>var v=document.getElementById('video');v.src=${JSONObject.quote(url)};
            function weiktubeState(){return {playing:!v.paused,position:v.currentTime,error:v.error?'Dieses HTTPS-Video konnte nicht abgespielt werden. Format oder URL prüfen.':'',title:'HTTPS-Video'};}
            function pause(){v.pause();}function toggle(){if(v.paused)v.play().catch(function(){});else v.pause();}
            </script>
        """.trimIndent()
        view.loadDataWithBaseURL("https://com.weiktube.app/", """
            <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <meta name="referrer" content="strict-origin-when-cross-origin">
            <style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#000}#player{position:absolute;inset:0;width:100vw!important;height:100vh!important;display:block;border:0}</style>
            </head><body>$body</body></html>
        """.trimIndent(), "text/html", "UTF-8", null)
    }
    fun poll() {
        if (source == null) return
        val polledSource = source
        view.evaluateJavascript("(function(){if(typeof fitPlayer==='function')fitPlayer();return typeof weiktubeState==='function'?JSON.stringify(weiktubeState()):null;})()") { result ->
            runCatching {
                if (source != polledSource) return@runCatching
                if (result == "null") return@runCatching
                val json = JSONObject(org.json.JSONTokener(result).nextValue() as String)
                playing = json.optBoolean("playing")
                position = json.optDouble("position", 0.0)
                json.optString("error").takeIf { it.isNotEmpty() }?.let { error = it }
                json.optString("title").takeIf { it.isNotEmpty() }?.let { title = it }
            }
        }
    }
    fun toggle() { view.evaluateJavascript("if(typeof toggle==='function')toggle()", null) }
    fun pause() { view.evaluateJavascript("if(typeof pause==='function')pause()", null); playing = false }
    fun close() { pause(); source = null; error = null; view.loadUrl("about:blank") }
    fun destroy() { view.stopLoading(); view.destroy() }
}





