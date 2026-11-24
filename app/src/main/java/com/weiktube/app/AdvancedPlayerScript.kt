package com.weiktube.app

internal object AdvancedPlayerScript {
    val SCRIPT = """
        (function(){
          if(window.weiktubeAdvanced)return;
          var held=null;
          function video(){return document.querySelector('video');}
          function restore(){if(held){try{held.v.playbackRate=held.rate;}catch(e){}held=null;}}
          window.weiktubeAdvanced={
            hit:function(x,y){
              var v=video();if(!v||!v.isConnected||v.readyState<1)return false;
              x*=innerWidth;y*=innerHeight;
              var r=v.getBoundingClientRect();
              if(x<r.left||x>r.right||y<r.top+24||y>r.bottom-56)return false;
              var el=document.elementFromPoint(x,y);
              if(!el||el.closest('button,a,input,select,textarea,[role="button"],[role="slider"],[role="menu"],[role="dialog"],.ytp-progress-bar-container,.ytp-settings-menu,.player-controls-bottom'))return false;
              return el===v||!!el.closest('#player,#movie_player,.html5-video-player,.player-container');
            },
            seek:function(delta){
              var v=video();if(!v||!isFinite(v.duration)||v.duration<=0||!v.seekable.length)return null;
              var old=v.currentTime,target=Math.max(0,Math.min(v.duration,old+delta)),best=null,d=Infinity;
              for(var i=0;i<v.seekable.length;i++){
                var t=Math.max(v.seekable.start(i),Math.min(v.seekable.end(i),target));
                if(Math.abs(t-target)<d){d=Math.abs(t-target);best=t;}
              }
              if(best===null)return null;v.currentTime=best;return best-old;
            },
            rates:function(){var p=document.getElementById('movie_player');
              try{return p&&typeof p.getAvailablePlaybackRates==='function'?p.getAvailablePlaybackRates().filter(function(n){return [1.5,2,2.5,3].includes(n);}):[];}catch(e){return [];}
            },
            hold:function(rate){
              restore();var v=video();if(!v||v.paused||v.ended||!this.rates().includes(rate))return false;
              held={v:v,rate:v.playbackRate};try{v.playbackRate=rate;}catch(e){restore();return false;}
              if(v.playbackRate!==rate){restore();return false;}return true;
            },
            release:restore
          };
          document.addEventListener('emptied',function(e){if(held&&e.target===held.v)restore();},true);
          window.addEventListener('pagehide',restore);
          document.addEventListener('visibilitychange',function(){if(document.hidden)restore();});
        })();
    """.trimIndent()
}
