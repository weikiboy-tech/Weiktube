package com.weiktube.app

import android.content.Context
import android.webkit.WebView
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID

internal class SponsorBlockController(context: Context) {
    private val app=context.applicationContext
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val client by lazy {SponsorBlockClient(app)}
    val statistics by lazy {SponsorBlockStatistics(app)}
    private var job:Job?=null
    private var view:WebView?=null
    private var generation=-1L
    private var videoId:String?=null
    private var token=""
    private var fetched=false
    private var segments=emptyList<SponsorBlockSegment>()
    private var settings=SponsorBlockPreferences()
    private var lastConfiguration=""
    private var querying=false
    private var statisticsRevision=0
    data class Prompt(val category:String,val ids:List<String> = emptyList(),val sequence:Int=0,val seconds:Double=0.0)
    var manual by mutableStateOf<Prompt?>(null);private set
    var notice by mutableStateOf<Prompt?>(null);private set
    var status by mutableStateOf("Ausgeschaltet");private set
    var markerStatus by mutableStateOf("Zeitleiste noch nicht geprüft");private set

    fun reset(sendStop:Boolean=true) {
        job?.cancel();job=null
        if(sendStop)view?.evaluateJavascript("window.weiktubeSponsorBlock&&window.weiktubeSponsorBlock.stop();",null)
        view=null;videoId=null;generation=-1;token="";segments=emptyList();fetched=false;lastConfiguration="";querying=false
        manual=null;notice=null
    }
    fun retire(web:WebView){if(view===web)reset(false)}
    fun gate(allowed:Boolean){
        view?.evaluateJavascript("window.weiktubeSponsorBlock&&window.weiktubeSponsorBlock.gate($allowed);",null)
        if(!allowed)manual=null
    }
    fun sync(web:WebView,epoch:Long,id:String?,allowed:Boolean,prefs:SponsorBlockPreferences) {
        settings=prefs
        if(!prefs.enabled||id==null||!SponsorBlockCodec.validId(id)){
            if(view!=null)reset();status=if(prefs.enabled)"Normales YouTube-Video öffnen" else "Ausgeschaltet";return
        }
        if(view!==web||generation!=epoch||videoId!=id){
            reset();view=web;generation=epoch;videoId=id;token=UUID.randomUUID().toString();status="Segmente werden geladen"
        }
        if(!fetched&&allowed){
            fetched=true
            val requestedToken=token
            job=scope.launch {
                val result=client.get(id)
                if(view!==web||token!=requestedToken||!settings.enabled)return@launch
                segments=result.segments
                status=when { !result.available->"SponsorBlock nicht verfügbar – normale Wiedergabe";segments.isEmpty()->"Keine überspringbaren Abschnitte bekannt";else->"${segments.size} Community-Abschnitte geladen" }
                lastConfiguration=""
            }
        }
        val config=JSONObject().put("token",token).put("id",id).put("enabled",true).put("allowed",allowed)
            .put("markers",prefs.markers).put("modes",JSONObject(prefs.modes.mapValues {it.value.name}))
            .put("segments",JSONArray().also {array->segments.forEach {array.put(it.json())}}).toString()
        val requestedToken=token
        // A policy update must not wait for an earlier JS callback, especially on opt-out.
        if(config!=lastConfiguration){
            lastConfiguration=config
            web.evaluateJavascript(SponsorBlockScript.SCRIPT+";window.weiktubeSponsorBlock.configure($config);",null)
        }
        if(querying)return
        querying=true
        val capturedStatisticsRevision=statisticsRevision
        web.evaluateJavascript("JSON.stringify(window.weiktubeSponsorBlock&&window.weiktubeSponsorBlock.snapshot());") {raw->
            if(view!==web||requestedToken!=token)return@evaluateJavascript
            querying=false
            if(!settings.enabled)return@evaluateJavascript
            decode(raw,requestedToken,capturedStatisticsRevision==statisticsRevision)
        }
    }
    private fun decode(raw:String,expected:String,recordStatistics:Boolean){
        if(raw.length>131072)return
        runCatching {
            val data=JSONObject(JSONTokener(raw).nextValue() as? String?:return)
            if(data.optString("token")!=expected)return
            markerStatus=if(data.optString("markers")=="available")"Markierungen in vorhandener Zeitleiste" else "Keine zuverlässig unterstützte Zeitleiste sichtbar"
            fun prompt(value:JSONObject?):Prompt? {
                if(value==null||SponsorCategory.from(value.optString("category"))==null)return null
                val ids=value.optJSONArray("ids")?:JSONArray()
                return Prompt(value.getString("category"),(0 until ids.length()).map {ids.getString(it)},value.optInt("seq"),value.optDouble("seconds",0.0))
            }
            manual=prompt(data.optJSONObject("manual"))
            notice=if(settings.notices)prompt(data.optJSONObject("notice")) else null
            if(!recordStatistics)return
            val events=data.optJSONArray("events")?:return
            for(i in 0 until minOf(events.length(),64)){
                val e=events.optJSONObject(i)?:continue
                val seq=e.optInt("seq",0);if(seq<=0)continue
                val key="$expected:$seq"
                if(e.optString("type")=="undo"){statistics.undo(key);continue}
                if(e.optString("type")!="skip")continue
                val from=e.optDouble("from",Double.NaN);val to=e.optDouble("to",Double.NaN)
                val ids=e.optJSONArray("ids")?:continue
                val known=(0 until ids.length()).mapNotNull {j->segments.firstOrNull {it.uuid==ids.optString(j)}}
                if(known.isEmpty()||known.size!=ids.length()||!from.isFinite()||!to.isFinite()||to<=from||
                    from<known.minOf {it.start}-.05||to>known.maxOf {it.end}+.05||known.none {it.category.key==e.optString("category")})continue
                statistics.record(key,e.getString("category"),to-from)
            }
        }
    }
    fun skipManual(){
        val value=manual?:return
        view?.evaluateJavascript("window.weiktubeSponsorBlock&&window.weiktubeSponsorBlock.manual(${JSONObject.quote(token)},${JSONArray(value.ids)});",null)
    }
    fun undo(){
        val value=notice?:return
        view?.evaluateJavascript("window.weiktubeSponsorBlock&&window.weiktubeSponsorBlock.undo(${JSONObject.quote(token)},${value.sequence});",null)
    }
    fun clearCache(){job?.cancel();reset();scope.launch {client.clear();status="Cache geleert"}}
    fun resetStatistics(){
        statisticsRevision++;statistics.reset()
        view?.evaluateJavascript("window.weiktubeSponsorBlock&&window.weiktubeSponsorBlock.clearEvents();",null)
    }
    fun close(){reset();scope.cancel()}
}
