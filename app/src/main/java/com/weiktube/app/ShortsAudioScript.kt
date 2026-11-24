package com.weiktube.app

/** One retained Shorts document: confirmed audio state and media-bound, event-driven seeking. */
internal object ShortsAudioScript {
    val SCRIPT = """
        (function(){
          if(window.top!==window||window.weiktubeShortsTab!==true||
             !['m.youtube.com','www.youtube.com','youtube.com'].includes(location.hostname))return;
          if(window.weiktubeUpdateShortsSound){window.weiktubeUpdateShortsSound();return;}
          var visit=0,userMuted=false,active=null,key='',audio='waiting',attempted=false,frame=0;
          var serial=0,ids=new WeakMap(),bar=null,fill=null,sound=null,drag=null,hidden=[];
          function permitted(){return window.weiktubeShortsTab===true&&window.weiktubePausePreview===false&&!document.hidden&&/^\/shorts(?:\/[A-Za-z0-9_-]{11})?\/?$/.test(location.pathname);}
          function video(){
            if(!permitted())return null;
            var best=null,score=0;
            document.querySelectorAll('video').forEach(function(v){
              if(!v.isConnected||v.closest('[aria-hidden="true"],[hidden],[inert]'))return;
              if(v.checkVisibility&&!v.checkVisibility({checkOpacity:true,checkVisibilityCSS:true}))return;
              if(typeof getComputedStyle==='function'){var style=getComputedStyle(v);if(style.visibility==='hidden'||style.display==='none'||style.opacity==='0')return;}
              var r=v.getBoundingClientRect(),area=Math.max(0,Math.min(innerWidth,r.right)-Math.max(0,r.left))*Math.max(0,Math.min(innerHeight,r.bottom)-Math.max(0,r.top));
              if(area>score){score=area;best=v;}
            });return best;
          }
          function identity(v){
            if(!v)return '';
            if(!ids.has(v))ids.set(v,++serial);
            return visit+'|'+ids.get(v)+'|'+location.pathname+'|'+(v.currentSrc||v.src||'');
          }
          function audible(v){return !!v&&!v.paused&&!v.ended&&v.readyState>=2&&!v.muted&&v.volume>0;}
          function rememberMute(value){userMuted=value;try{sessionStorage.setItem('weiktube-shorts-mute',JSON.stringify({visit:visit,muted:value}));}catch(ignore){}}
          function restoreHints(){hidden.forEach(function(x){if(x[0].style)x[0].style.display=x[1];});hidden=[];}
          function hints(v){
            restoreHints();
            if(!audible(v))return;
            var root=v.closest('ytm-reel-video-renderer,ytm-shorts-lockup-view-model')||v.parentElement;
            if(!root)return;
            root.querySelectorAll('button,[role="button"]').forEach(function(b){
              var label=(b.getAttribute('aria-label')||b.textContent||'').trim();
              if(/^(ton einschalten|unmute|tap to unmute|sound on)$/i.test(label)&&b!==sound){hidden.push([b,b.style.display]);b.style.display='none';}
            });
          }
          function requestAudio(v,explicit){
            if(!v||userMuted||!permitted()||v.readyState<2||(!explicit&&v.paused))return;
            if(!explicit&&attempted)return;
            attempted=true;audio='requested';
            var root=v.closest('ytm-reel-video-renderer,ytm-shorts-lockup-view-model');
            var p=v.closest('.html5-video-player')||(root&&root.querySelector('.html5-video-player'));
            // Never select a global player that may belong to a prefetched/inactive Short.
            try{if(p&&typeof p.unMute==='function')p.unMute();
                if(v.volume===0){if(p&&typeof p.setVolume==='function')p.setVolume(100);v.volume=1;}
                v.muted=false;
            }catch(ignore){audio='blocked';}
            if(explicit&&v.paused){try{var promise=v.play();if(promise&&promise.catch)promise.catch(function(){audio='blocked';schedule();});}catch(ignore){audio='blocked';}}
            // Verification only, never an automatic retry or playback loop.
            schedule();
          }
          function ranges(v){
            if(!v||!Number.isFinite(v.duration)||v.duration<=0)return [];
            var out=[];
            try{for(var i=0;i<Math.min(v.seekable.length,64);i++){
              var a=Math.max(0,v.seekable.start(i)),b=Math.min(v.duration,v.seekable.end(i));
              if(Number.isFinite(a)&&Number.isFinite(b)&&b>a)out.push([a,b]);
            }}catch(ignore){return [];}return out;
          }
          function seek(v,k,fraction){
            if(video()!==v||identity(v)!==k)return false;
            var rs=ranges(v);if(!rs.length||!Number.isFinite(fraction))return false;
            var target=Math.max(0,Math.min(1,fraction))*v.duration,chosen=null,distance=Infinity;
            rs.forEach(function(r){var value=Math.max(r[0],Math.min(r[1]-Math.min(.05,(r[1]-r[0])/2),target));var d=Math.abs(value-target);if(d<distance){distance=d;chosen=value;}});
            try{v.currentTime=chosen;return true;}catch(ignore){return false;}
          }
          function fraction(e){var r=bar.getBoundingClientRect();return Math.max(0,Math.min(1,(e.clientX-r.left)/Math.max(1,r.width)));}
          function paint(value){fill.style.width=(Math.max(0,Math.min(1,value))*100)+'%';bar.setAttribute('aria-valuenow',String(Math.round(value*100)));}
          function cancel(){drag=null;}
          function create(){
            if(bar||!document.body)return;
            bar=document.createElement('div');bar.id='weiktube-shorts-seek';bar.setAttribute('role','slider');bar.setAttribute('aria-label','Short spulen');bar.setAttribute('aria-valuemin','0');bar.setAttribute('aria-valuemax','100');bar.tabIndex=0;
            bar.style.cssText='position:fixed;left:0;right:0;bottom:0;height:32px;z-index:2147483646;touch-action:pan-y;user-select:none;';
            var track=document.createElement('div');track.style.cssText='position:absolute;left:0;right:0;bottom:2px;height:3px;background:#777;pointer-events:none';
            fill=document.createElement('div');fill.style.cssText='height:100%;width:0;background:#f03;pointer-events:none';track.appendChild(fill);bar.appendChild(track);document.body.appendChild(bar);
            sound=document.createElement('button');sound.id='weiktube-shorts-sound';sound.textContent='Ton einschalten';sound.style.cssText='position:fixed;right:12px;bottom:44px;z-index:2147483646;padding:10px 16px;border:0;border-radius:20px;background:#fff;color:#111;font:14px sans-serif;';document.body.appendChild(sound);
            sound.addEventListener('click',function(e){e.stopPropagation();if(!e.isTrusted)return;rememberMute(false);var v=video();requestAudio(v,true);});
            bar.addEventListener('pointerdown',function(e){
              if(drag){cancel();return;}
              if(!e.isPrimary||e.button>0)return;
              var v=video();if(!ranges(v).length)return;
              drag={v:v,key:identity(v),pointer:e.pointerId,x:e.clientX,y:e.clientY,value:fraction(e),horizontal:false};
            });
            bar.addEventListener('pointermove',function(e){
              if(!drag||e.pointerId!==drag.pointer)return;
              if(video()!==drag.v||identity(drag.v)!==drag.key){cancel();return;}
              var dx=Math.abs(e.clientX-drag.x),dy=Math.abs(e.clientY-drag.y);
              if(!drag.horizontal&&dy>8&&dy>dx){cancel();return;}
              if(!drag.horizontal&&dx>8&&dx>dy){drag.horizontal=true;bar.setPointerCapture(e.pointerId);}
              if(drag.horizontal){e.preventDefault();e.stopPropagation();drag.value=fraction(e);paint(drag.value);}
            });
            bar.addEventListener('pointerup',function(e){
              if(!drag||e.pointerId!==drag.pointer)return;
              var d=drag;cancel();e.preventDefault();e.stopPropagation();seek(d.v,d.key,fraction(e));schedule();
            });
            ['pointercancel','lostpointercapture'].forEach(function(n){bar.addEventListener(n,function(){cancel();schedule();});});
            bar.addEventListener('click',function(e){e.preventDefault();e.stopPropagation();});
            bar.addEventListener('keydown',function(e){
              var v=video();if(!ranges(v).length)return;
              var f=v.currentTime/v.duration;
              if(e.key==='ArrowLeft')f-=.05;else if(e.key==='ArrowRight')f+=.05;else if(e.key==='Home')f=0;else if(e.key==='End')f=1;else return;
              e.preventDefault();e.stopPropagation();seek(v,identity(v),f);schedule();
            });
          }
          function update(){
            frame=0;create();
            var next=window.weiktubeShortsSoundRequest||0;
            if(visit!==next){visit=next;userMuted=false;key='';try{var saved=JSON.parse(sessionStorage.getItem('weiktube-shorts-mute'));if(saved&&saved.visit===visit)userMuted=!!saved.muted;}catch(ignore){}}
            var v=video(),k=identity(v);
            if(k!==key){active=v;key=k;attempted=false;audio='waiting';cancel();restoreHints();}
            if(v&&visit&&!userMuted){
              if(audible(v))audio='audible';
              else if(attempted)audio='blocked';
              else requestAudio(v,false);
            }
            if(bar){
              bar.style.display=v?'block':'none';sound.style.display=v&&audio==='blocked'&&!userMuted?'block':'none';
              var valid=ranges(v).length>0;bar.setAttribute('aria-disabled',String(!valid));bar.tabIndex=valid?0:-1;
              if(!drag)paint(v&&Number.isFinite(v.duration)&&v.duration>0&&Number.isFinite(v.currentTime)?v.currentTime/v.duration:0);
            }
            hints(v);
          }
          function schedule(){if(!frame)frame=requestAnimationFrame(update);}
          document.addEventListener('click',function(e){
            if(!e.isTrusted)return;
            var b=e.target&&e.target.closest&&e.target.closest('button,[role="button"]');
            if(!b||b===sound)return;
            var label=(b.getAttribute('aria-label')||b.getAttribute('title')||b.textContent||'').trim();
            if(/^(ton einschalten|unmute|tap to unmute|sound on)$/i.test(label)){rememberMute(false);}
            else if(/^(stummschalten|ton ausschalten|mute|sound off)$/i.test(label)){rememberMute(true);audio='user-muted';}
            schedule();
          },true);
          ['playing','volumechange','loadedmetadata','durationchange','timeupdate','progress','pause','emptied','seeking','seeked'].forEach(function(n){document.addEventListener(n,schedule,true);});
          document.addEventListener('visibilitychange',function(){cancel();schedule();});
          window.addEventListener('resize',function(){cancel();schedule();});
          window.addEventListener('popstate',schedule);
          new MutationObserver(schedule).observe(document,{childList:true,subtree:true});
          window.addEventListener('pointerup',function(){cancel();schedule();});
          window.addEventListener('pointercancel',function(){cancel();schedule();});
          window.weiktubeUpdateShortsSound=schedule;
          schedule();
        })();
    """.trimIndent()
}
