package com.weiktube.app

/** Transforms only the existing media element. Controls and document geometry never scale. */
internal object VideoZoomScript {
    val SCRIPT = """
        (function(){
          if(window.top!==window)return;
          if(window.weiktubeVideoZoom)return;
          var active=null,saved=null,state={scale:1,x:0,y:0};
          var properties=['transform','transform-origin','clip-path','object-fit'];
          function reset(){
            if(active&&saved)properties.forEach(function(p){
              if(saved[p][0])active.style.setProperty(p,saved[p][0],saved[p][1]);
              else active.style.removeProperty(p);
            });
            active=null;saved=null;state={scale:1,x:0,y:0};
          }
          function media(){
            if(['m.youtube.com','www.youtube.com','youtube.com'].includes(location.hostname)){
              if(location.pathname!=='/watch')return null;
              return document.querySelector('#player-container-id video');
            }
            return document.querySelector('video#video');
          }
          function axis(size,content,scale,offset){
            var margin=(size-content)/2;
            if(content*scale<=size)return size*(1-scale)/2;
            return Math.max(size-(margin+content)*scale,Math.min(-margin*scale,offset));
          }
          window.weiktubeVideoZoom=function(scale,fx,fy){
            if(!Number.isFinite(scale)||!Number.isFinite(fx)||!Number.isFinite(fy))return;
            scale=Math.max(1,Math.min(8,scale));
            var v=media();
            if(!v||v!==active){reset();}
            if(scale===1||!v){reset();return;}
            var r=v.getBoundingClientRect(),w=r.width/state.scale,h=r.height/state.scale;
            if(!(w>0&&h>0&&v.videoWidth>0&&v.videoHeight>0))return;
            var baseLeft=r.left-state.x,baseTop=r.top-state.y;
            var x=Math.max(0,Math.min(1,fx))*innerWidth-baseLeft;
            var y=Math.max(0,Math.min(1,fy))*innerHeight-baseTop;
            var ratio=scale/state.scale;
            var aspect=v.videoWidth/v.videoHeight;
            var mw=Math.min(w,h*aspect),mh=mw/aspect;
            var tx=axis(w,mw,scale,x-(x-state.x)*ratio);
            var ty=axis(h,mh,scale,y-(y-state.y)*ratio);
            if(!active){
              active=v;saved={};properties.forEach(function(p){saved[p]=[v.style.getPropertyValue(p),v.style.getPropertyPriority(p)];});
            }
            v.style.setProperty('object-fit','contain','important');
            v.style.setProperty('transform-origin','0 0','important');
            v.style.setProperty('transform','translate('+tx+'px,'+ty+'px) scale('+scale+')','important');
            // Inverse-transform the original video box to clip just the image, never its controls.
            v.style.setProperty('clip-path','inset('+Math.max(0,-ty/scale)+'px '+Math.max(0,(tx+w*scale-w)/scale)+'px '+Math.max(0,(ty+h*scale-h)/scale)+'px '+Math.max(0,-tx/scale)+'px)','important');
            state={scale:scale,x:tx,y:ty};
          };
          window.addEventListener('resize',reset);
          document.addEventListener('loadedmetadata',function(e){if(e.target===active)reset();},true);
          document.addEventListener('emptied',function(e){if(e.target===active)reset();},true);
        })();
    """.trimIndent()
}
