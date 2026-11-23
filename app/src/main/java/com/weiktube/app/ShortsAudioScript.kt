package com.weiktube.app

/** Audible starts for the visible Shorts tab only. Never starts/restarts media or alters volume. */
internal object ShortsAudioScript {
    val SCRIPT = """
        (function(){
          if(window.top!==window||window.weiktubeShortsTab!==true||
             !['m.youtube.com','www.youtube.com','youtube.com'].includes(location.hostname))return;
          if(window.weiktubeUpdateShortsSound){window.weiktubeUpdateShortsSound();return;}
          var token=0,userMuted=false,gestureUntil=0,seen=new WeakMap(),pending=true;
          function eligible(v){
            if(window.weiktubePausePreview!==false||document.hidden||userMuted||!token||
               !/^\/shorts\/[A-Za-z0-9_-]{11}\/?$/.test(location.pathname)||
               !v||v.tagName!=='VIDEO'||!v.isConnected||v.paused||v.ended||v.readyState<2)return false;
            var r=v.getBoundingClientRect();
            return r.width>0&&r.height>0&&r.bottom>0&&r.right>0&&r.top<innerHeight&&r.left<innerWidth;
          }
          function audible(v){
            if(!eligible(v))return;
            var id=location.pathname;
            if(seen.get(v)===id)return;
            seen.set(v,id);pending=false;
            // Keep YouTube's own mute state/indicator aligned with the actual media element.
            var container=v.closest&&v.closest('ytm-reel-video-renderer,ytm-shorts-lockup-view-model');
            var p=(container&&container.querySelector('.html5-video-player'))||document.getElementById('movie_player');
            if(p&&typeof p.unMute==='function'){try{p.unMute();}catch(ignore){}}
            v.muted=false;
          }
          document.addEventListener('playing',function(e){pending=true;audible(e.target);},true);
          // A mute selected via a real control takes precedence for the remainder of this visit.
          document.addEventListener('click',function(e){
            if(e.isTrusted&&e.target&&e.target.closest&&e.target.closest('button,[role="button"],input'))
              gestureUntil=Date.now()+1000;
          },true);
          document.addEventListener('volumechange',function(e){
            var v=e.target;
            if(gestureUntil>=Date.now()&&v&&v.tagName==='VIDEO'&&v.muted){userMuted=true;pending=false;}
          },true);
          window.weiktubeUpdateShortsSound=function(){
            var next=window.weiktubeShortsSoundRequest||0;
            if(next!==token){token=next;userMuted=false;gestureUntil=0;seen=new WeakMap();pending=true;}
            // Handle a playing event that preceded filter readiness, once, without replaying it.
            if(pending)document.querySelectorAll('video').forEach(audible);
          };
          window.weiktubeUpdateShortsSound();
        })();
    """.trimIndent()
}
