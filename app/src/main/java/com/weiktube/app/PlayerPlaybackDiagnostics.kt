package com.weiktube.app

import android.os.SystemClock
import android.util.Log
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.util.UUID

/** Bounded playback diagnostics. No URL, account data, media time, stack or document token is logged. */
internal object PlayerPlaybackDiagnostics {
    private const val BRIDGE = "WeiktubePlaybackDiagnostics"
    private val origins = setOf("https://m.youtube.com", "https://www.youtube.com", "https://youtube.com", "https://com.weiktube.app")
    internal enum class Event { INITIAL, PLAY, PLAYING, PAUSE_CALL, PAUSE, ENDED, VISIBILITY, PAGE_HIDE, BLUR, METADATA, EMPTIED, RESIZE }
    internal enum class Reason { NONE, APP_COMMAND, APP_GUARD, JS_UNATTRIBUTED, ENGINE_OR_UNOBSERVED }
    internal data class State(
        val event: Event, val reason: Reason, val sequence: Int, val elapsedMs: Int,
        val video: Boolean, val paused: Boolean, val connected: Boolean, val hidden: Boolean,
        val readyState: Int, val mini: Boolean, val pip: Boolean, val width: Int, val height: Int,
    )

    internal fun decode(raw: String, token: String): State? {
        if (raw.length > 768 || token.isEmpty() || token.length > 64) return null
        return runCatching {
            val data = JSONObject(raw)
            if (data.length() != 14 || data.opt("token") != token) return null
            fun flag(key: String): Boolean? = data.opt(key) as? Boolean
            fun integer(key: String, min: Int, max: Int): Int? {
                val value = (data.opt(key) as? Number)?.toDouble() ?: return null
                return if (value.isFinite() && value in min.toDouble()..max.toDouble() && value == value.toInt().toDouble()) value.toInt() else null
            }
            val event = Event.valueOf(data.opt("event") as? String ?: return null)
            val reason = Reason.valueOf(data.opt("reason") as? String ?: return null)
            if (event == Event.PAUSE_CALL && reason !in setOf(Reason.APP_COMMAND, Reason.APP_GUARD, Reason.JS_UNATTRIBUTED) ||
                event == Event.PAUSE && reason == Reason.NONE ||
                event !in setOf(Event.PAUSE_CALL, Event.PAUSE) && reason != Reason.NONE) return null
            State(event, reason, integer("sequence", 1, 1_000_000_000) ?: return null,
                integer("elapsedMs", 0, 86_400_000) ?: return null,
                flag("video") ?: return null, flag("paused") ?: return null,
                flag("connected") ?: return null, flag("hidden") ?: return null,
                integer("readyState", 0, 4) ?: return null, flag("mini") ?: return null,
                flag("pip") ?: return null, integer("width", 0, 16384) ?: return null,
                integer("height", 0, 16384) ?: return null)
        }.getOrNull()
    }

    /** Native rate limit also bounds a page that bypasses or replaces the JavaScript reporter. */
    internal class RateLimit {
        private val times = ArrayDeque<Long>()
        fun allow(now: Long): Boolean {
            while (times.isNotEmpty() && now - times.first() >= 60_000L) times.removeFirst()
            if (times.size >= 120) return false
            times.addLast(now)
            return true
        }
    }

    internal fun format(state: State): String = with(state) {
        "event=${event.name} reason=${reason.name} seq=$sequence elapsedMs=$elapsedMs " +
            "video=$video paused=$paused connected=$connected hidden=$hidden ready=$readyState " +
            "mini=$mini pip=$pip width=$width height=$height"
    }

    fun install(web: WebView): Hooks = Hooks(web)

    class Hooks internal constructor(private val web: WebView) {
        private val supported = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        private var script: ScriptHandler? = null
        private var token = ""
        private var closed = false
        private var lastSequence = 0
        private val rate = RateLimit()

        init {
            if (supported) WebViewCompat.addWebMessageListener(web, BRIDGE, origins) { sender, message, origin, mainFrame, _ ->
                if (closed || sender !== web || !mainFrame || origin.toString().trimEnd('/') !in origins) return@addWebMessageListener
                val raw = runCatching { message.data }.getOrNull() ?: return@addWebMessageListener
                val state = decode(raw, token) ?: return@addWebMessageListener
                if (state.sequence <= lastSequence) return@addWebMessageListener
                lastSequence = state.sequence
                if (rate.allow(SystemClock.elapsedRealtime())) Log.i("WeiktubePlayback", format(state))
            }
        }

        fun prepare() {
            if (closed || !supported) return
            token = UUID.randomUUID().toString(); lastSequence = 0
            script?.remove(); script = null
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
                script = WebViewCompat.addDocumentStartJavaScript(web, command(), origins)
        }

        fun apply() { if (!closed && supported && token.isNotEmpty()) web.evaluateJavascript(command(), null) }
        private fun command() = SCRIPT + ";window.weiktubeDiagnosePlayback&&window.weiktubeDiagnosePlayback(${JSONObject.quote(token)});"

        fun close() {
            if (closed) return
            closed = true; token = ""; lastSequence = 0
            script?.remove(); script = null
            if (supported) WebViewCompat.removeWebMessageListener(web, BRIDGE)
        }
    }

    internal val SCRIPT = """
        (function(){
          if(window.top!==window||!['https://m.youtube.com','https://www.youtube.com','https://youtube.com','https://com.weiktube.app'].includes(location.origin))return;
          if(window.weiktubeDiagnosePlayback)return;
          var token='',sequence=0,scope=null,started=performance.now(),times=[],calls=new WeakMap();
          function now(){return performance.now();}
          function media(v){return v&&(v.tagName==='VIDEO'||v.tagName==='AUDIO');}
          function integer(v,max){return Number.isInteger(v)&&v>=0&&v<=max?v:0;}
          function report(event,reason,target){
            try{
              if(!token||!window.WeiktubePlaybackDiagnostics||sequence>=1000000000)return;
              var time=now();while(times.length&&time-times[0]>=60000)times.shift();
              if(times.length>=120)return;times.push(time);
              var v=media(target)?target:document.querySelector('video'),root=document.documentElement;
              var payload={token:token,event:event,reason:reason,sequence:++sequence,
                elapsedMs:Math.min(86400000,Math.max(0,Math.floor(time-started))),
                video:!!v&&v.tagName==='VIDEO',paused:!v||!!v.paused,connected:!!v&&!!v.isConnected,
                hidden:!!document.hidden,readyState:v?integer(v.readyState,4):0,
                mini:!!root&&root.getAttribute('data-weiktube-mini')==='1',
                pip:!!root&&root.getAttribute('data-weiktube-system-pip')==='1',
                width:integer(window.innerWidth,16384),height:integer(window.innerHeight,16384)};
              window.WeiktubePlaybackDiagnostics.postMessage(JSON.stringify(payload));
            }catch(ignore){}
          }
          window.weiktubePlaybackScope=function(reason,action){
            var previous=scope;scope=reason==='APP_COMMAND'||reason==='APP_GUARD'?reason:null;
            try{return Reflect.apply(action,this,[]);}finally{scope=previous;}
          };
          try{
            var proto=window.HTMLMediaElement&&window.HTMLMediaElement.prototype;
            var descriptor=proto&&Object.getOwnPropertyDescriptor(proto,'pause');
            if(descriptor&&typeof descriptor.value==='function'){
              var original=descriptor.value;
              var wrapped=function(){
                try{
                  if(media(this)){
                    var reason=scope||'JS_UNATTRIBUTED';
                    if(!this.paused)calls.set(this,{reason:reason,at:now()});
                    report('PAUSE_CALL',reason,this);
                  }
                }catch(ignore){}
                try{return Reflect.apply(original,this,arguments);}
                catch(error){try{calls.delete(this);}catch(ignore){}throw error;}
              };
              Object.defineProperty(proto,'pause',Object.assign({},descriptor,{value:wrapped}));
            }
          }catch(ignore){}
          var names={play:'PLAY',playing:'PLAYING',pause:'PAUSE',ended:'ENDED',loadedmetadata:'METADATA',emptied:'EMPTIED'};
          Object.keys(names).forEach(function(type){
            document.addEventListener(type,function(e){
              if(!media(e.target))return;
              var reason='NONE';
              if(type==='pause'){
                var call=calls.get(e.target);calls.delete(e.target);
                reason=call&&now()-call.at>=0&&now()-call.at<=1000?call.reason:'ENGINE_OR_UNOBSERVED';
              }else if(type==='play'||type==='playing'||type==='emptied')calls.delete(e.target);
              report(names[type],reason,e.target);
            },true);
          });
          document.addEventListener('visibilitychange',function(){report('VISIBILITY','NONE');});
          window.addEventListener('pagehide',function(){report('PAGE_HIDE','NONE');});
          window.addEventListener('blur',function(){report('BLUR','NONE');});
          window.addEventListener('resize',function(){report('RESIZE','NONE');});
          window.weiktubeDiagnosePlayback=function(next){
            if(typeof next!=='string'||!/^[A-Za-z0-9_-]{1,64}$/.test(next)||next===token)return;
            token=next;sequence=0;calls=new WeakMap();report('INITIAL','NONE');
          };
        })();
    """.trimIndent()
}
