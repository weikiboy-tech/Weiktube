package com.weiktube.app

/** Media-event-driven controller on the retained watch document; never calls play/pause/load. */
internal object SponsorBlockScript {
    val SCRIPT = """
(function(){
  if(window.weiktubeSponsorBlock)return;
  var config=null,allowed=false,node=null,timer=0,pending=null,manual=null,last=null,notice=null,
      attempted=new Set(),suppressed=new Set(),events=[],sequence=0,markerState='unavailable';
  var handlers=['timeupdate','playing','pause','ratechange','loadedmetadata','durationchange','seeking','seeked','emptied'];
  function now(){return Date.now();}
  function same(){
    if(!config||!config.enabled)return false;
    try{
      if(new URL(location.href).searchParams.get('v')!==config.id||location.pathname!=='/watch')return false;
      var p=document.getElementById('movie_player'),d=p&&p.getVideoData&&p.getVideoData();
      if(d&&d.video_id&&d.video_id!==config.id)return false;
      return true;
    }catch(e){return false;}
  }
  function live(){
    try{var p=document.getElementById('movie_player'),d=p&&p.getVideoData&&p.getVideoData();
      return !!(d&&(d.isLive||d.isLiveContent))||!!(window.ytInitialPlayerResponse&&window.ytInitialPlayerResponse.videoDetails&&window.ytInitialPlayerResponse.videoDetails.isLive);
    }catch(e){return true;}
  }
  function usable(){return same()&&node&&node.isConnected&&isFinite(node.duration)&&node.duration>0&&!live();}
  function valid(s){return usable()&&isFinite(s.start)&&isFinite(s.end)&&s.start>=0&&s.end>s.start&&s.end<=node.duration+.05&&
    s.actionType==='skip'&&(s.videoDuration===0||Math.abs(node.duration-s.videoDuration)<=1);}
  function active(){return config?config.segments.filter(function(s){return valid(s)&&config.modes[s.category]!=='OFF';}):[];}
  function mode(s){return config.modes[s.category]==='AUTO'&&s.videoDuration===0?'MANUAL':config.modes[s.category];}
  function bind(){
    var v=document.querySelector('video');if(v===node)return;
    if(node)handlers.forEach(function(n){node.removeEventListener(n,onMedia);});
    node=v;pending=null;manual=null;
    if(node)handlers.forEach(function(n){node.addEventListener(n,onMedia);});
  }
  function removeMarkers(){document.querySelectorAll('[data-weiktube-sponsor-markers]').forEach(function(n){n.remove();});markerState='unavailable';}
  function markers(){
    if(!config||!config.markers||!usable()){removeMarkers();return;}
    var bar=document.querySelector('#movie_player .ytp-progress-bar,.ytm-progress-bar,.ytProgressBarLineHost');
    if(!bar||bar.getBoundingClientRect().width<20||getComputedStyle(bar).position==='static'){removeMarkers();return;}
    var rect=bar.getBoundingClientRect();if(rect.height>28||rect.height<=0){removeMarkers();return;}
    var layer=bar.querySelector('[data-weiktube-sponsor-markers]'),segments=active();
    var signature=JSON.stringify(segments.map(function(s){return [s.start,s.end,s.color];}))+':'+node.duration;
    if(layer&&layer.dataset.signature===signature){markerState='available';return;}
    removeMarkers();layer=document.createElement('div');layer.dataset.weiktubeSponsorMarkers='1';layer.dataset.signature=signature;
    layer.setAttribute('aria-hidden','true');
    layer.style.cssText='position:absolute;left:0;right:0;bottom:0;height:3px;pointer-events:none;z-index:2;overflow:hidden';
    segments.forEach(function(s){var item=document.createElement('span');
      item.style.cssText='position:absolute;height:100%;pointer-events:none;opacity:.85';
      item.style.left=(s.start/node.duration*100)+'%';item.style.width=((Math.min(s.end,node.duration)-s.start)/node.duration*100)+'%';
      item.style.backgroundColor=s.color;layer.appendChild(item);
    });bar.appendChild(layer);markerState='available';
  }
  function group(position,wanted){
    var eligible=active().filter(function(s){return mode(s)===wanted&&(wanted!=='AUTO'||(!suppressed.has(s.uuid)&&!attempted.has(s.uuid)));});
    var first=eligible.find(function(s){return position>=s.start&&position<s.end-.04;});if(!first)return null;
    var included=[first],end=first.end,changed=true;
    while(changed){changed=false;eligible.forEach(function(s){if(!included.includes(s)&&s.start<=end+.02&&s.end>position){included.push(s);end=Math.max(end,s.end);changed=true;}});}
    return {ids:included.map(function(s){return s.uuid;}),start:Math.min.apply(null,included.map(function(s){return s.start;})),end:Math.min(end,node.duration),category:first.category};
  }
  function seekable(from,to){
    if(!node||!isFinite(from)||!isFinite(to))return false;
    for(var i=0;i<node.seekable.length;i++)if(Math.min(from,to)>=node.seekable.start(i)-.01&&Math.max(from,to)<=node.seekable.end(i)+.01)return true;
    return false;
  }
  function emit(event){events.push(event);if(events.length>64)events.shift();}
  function request(g,undo){
    if(!usable()||!allowed||node.seeking||pending)return false;
    var from=node.currentTime,to=undo?g.from:g.end;
    if(!seekable(from,to)||(!undo&&to<=from+.04))return false;
    var seq=++sequence;
    pending={seq:seq,from:from,to:to,ids:g.ids,category:g.category,undo:undo?g.seq:0,expires:now()+2000,node:node};
    if(!undo)g.ids.forEach(function(id){attempted.add(id);});
    try{node.currentTime=to;}catch(e){pending=null;return false;}
    return true;
  }
  function tick(){
    clearTimeout(timer);timer=0;
    if(!same()){manual=null;pending=null;notice=null;removeMarkers();return;}
    bind();markers();
    if(notice&&notice.until<now())notice=null;
    if(pending&&pending.expires<now())pending=null;
    if(!usable()||!allowed||node.readyState<2||document.querySelector('.ad-showing,.ad-interrupting')){manual=null;return;}
    if(pending||node.seeking)return;
    var position=node.currentTime;
    manual=group(position,'MANUAL');
    if(node.paused||node.ended)return;
    var auto=group(position,'AUTO');
    if(auto&&request(auto,false))return;
    var next=active().filter(function(s){return mode(s)==='AUTO'&&!attempted.has(s.uuid)&&!suppressed.has(s.uuid)&&s.start>position;})
      .reduce(function(time,s){return Math.min(time,s.start);},Infinity);
    if(isFinite(next)&&node.playbackRate>0)timer=setTimeout(tick,Math.min(1000,Math.max(16,(next-position)/node.playbackRate*1000+8)));
  }
  function onMedia(e){
    if(!same()||e.target!==node)return;
    if(e.type==='emptied'){pending=null;manual=null;notice=null;clearTimeout(timer);return;}
    if(e.type==='seeking'){
      if(!pending||pending.node!==node||Math.abs(node.currentTime-pending.to)>.35){
        pending=null;
        active().forEach(function(s){if(node.currentTime>=s.start&&node.currentTime<s.end)suppressed.add(s.uuid);});
        notice=null;
      }
    }
    if(e.type==='seeked'){
      var action=pending;pending=null;
      if(action&&action.node===node&&action.expires>=now()&&Math.abs(node.currentTime-action.to)<=.35){
        if(action.undo){emit({type:'undo',seq:action.undo});notice=null;last=null;}
        else{
          last={seq:action.seq,from:action.from,end:action.to,ids:action.ids,category:action.category};
          emit({type:'skip',seq:action.seq,from:action.from,to:action.to,ids:action.ids,category:action.category});
          notice={seq:action.seq,category:action.category,seconds:action.to-action.from,until:now()+8000};
        }
      }else if(last&&node.currentTime>=last.from&&node.currentTime<last.end){
        emit({type:'undo',seq:last.seq});last=null;notice=null;
      }
    }
    tick();
  }
  function disable(){
    allowed=false;clearTimeout(timer);timer=0;pending=null;manual=null;notice=null;last=null;events=[];
    if(node)handlers.forEach(function(n){node.removeEventListener(n,onMedia);});node=null;
    removeMarkers();config=null;attempted.clear();suppressed.clear();
  }
  window.weiktubeSponsorBlock={
    configure:function(next){
      if(!next||!next.enabled){disable();return;}
      if(!config||next.token!==config.token){disable();sequence=0;}
      config=next;allowed=!!next.allowed;tick();
    },
    gate:function(value){allowed=!!value;if(!allowed){clearTimeout(timer);timer=0;manual=null;}else tick();},
    stop:disable,
    clearEvents:function(){events=[];},
    snapshot:function(){tick();return {token:config?config.token:'',manual:manual,notice:notice,markers:markerState,events:events.splice(0)};},
    manual:function(token,ids){
      if(!config||config.token!==token)return false;tick();
      return !!manual&&JSON.stringify(manual.ids)===JSON.stringify(ids)&&request(manual,false);
    },
    undo:function(token,seq){
      if(!config||config.token!==token||!last||last.seq!==seq||!usable())return false;
      last.ids.forEach(function(id){suppressed.add(id);});
      return request(last,true);
    }
  };
  window.addEventListener('resize',function(){if(config)markers();});
  window.addEventListener('pagehide',disable);
})();
    """.trimIndent()
}
