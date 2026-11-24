package com.weiktube.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

internal object PlayerQuality {
    enum class Network { WIFI, MOBILE, OTHER }
    fun network(context: Context): Network = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        classify(capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true, manager.isActiveNetworkMetered)
    }.getOrDefault(Network.OTHER)
    fun classify(wifi: Boolean, mobile: Boolean, metered: Boolean) = when {
        mobile || metered -> Network.MOBILE
        wifi -> Network.WIFI
        else -> Network.OTHER
    }
    fun preferred(network: Network, prefs: PlayerPreferences) = when(network) {
        Network.WIFI -> prefs.wifiQuality
        Network.MOBILE -> prefs.mobileQuality
        Network.OTHER -> 0
    }
    val SCRIPT = """
        (function(){
          if(window.weiktubeQuality)return;
          var key='',manual=false,attempts=0,choice='',issued=0,verified=false,failed=false;
          var sizes={tiny:144,small:240,medium:360,large:480,hd720:720,hd1080:1080,hd1440:1440,hd2160:2160};
          document.addEventListener('click',function(e){
            if(!e.isTrusted||!e.target||!e.target.closest)return;
            // Conservatively leave settings selections to the user, including adaptive quality.
            if(e.target.closest('.ytp-settings-menu,[role="menuitemradio"],ytm-quality-selector,ytm-select'))manual=true;
          },true);
          window.weiktubeQuality=function(id,preference,now){
            if(new URL(location.href).searchParams.get('v')!==id)return {status:'waiting',levels:[]};
            if(key!==id){key=id;manual=false;attempts=0;choice='';issued=0;verified=false;failed=false;}
            var p=document.getElementById('movie_player'),levels=[];
            try{if(p&&typeof p.getAvailableQualityLevels==='function')levels=p.getAvailableQualityLevels().filter(function(q){return sizes[q];});}catch(e){}
            function result(status){return {status:status,levels:levels.map(function(q){return sizes[q];}),target:sizes[choice]||0};}
            if(manual)return result('manual');
            if(failed)return result('unavailable');
            if(issued){
              // Exactly one request per video. Network changes apply on the next selection,
              // avoiding a tug-of-war with adaptive playback or a manual choice.
              var current='';try{current=p.getPlaybackQuality();}catch(e){}
              if(verified&&current!==choice){manual=true;return result('manual');}
              if(current===choice){verified=true;return result('confirmed');}
              if(now-issued>5000){failed=true;return result('unconfirmed');}
              return result('requested');
            }
            if(preference===0)return result('automatic');
            if(++attempts>12){failed=true;return result('unavailable');}
            if(!p||!levels.length)return result('waiting');
            choice=levels.filter(function(q){return sizes[q]<=preference;}).sort(function(a,b){return sizes[b]-sizes[a];})[0]||'';
            if(!choice)return result('automatic');
            if(typeof p.setPlaybackQualityRange!=='function'||typeof p.getPlaybackQuality!=='function'){
              failed=true;return result('unavailable');
            }
            try{p.setPlaybackQualityRange(choice,choice);issued=now;return result('requested');}
            catch(e){failed=true;return result('unavailable');}
          };
        })();
    """.trimIndent()
}
