const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('app/src/main/java/com/weiktube/app/ShortsAudioScript.kt','utf8').split('"""')[1];
let cases=0;
function fixture(options={}) {
  const events={}, videos=[]; let unmutes=0,writes=0;
  const document={hidden:false,addEventListener(n,f){(events[n]??=[]).push(f)},querySelectorAll(){return videos},getElementById(){return {unMute(){unmutes++}}}};
  const c={document,location:{hostname:'m.youtube.com',pathname:'/shorts/abcdefghijk'},innerHeight:800,innerWidth:400,Date,WeakMap,
    weiktubeShortsTab:true,weiktubePausePreview:false,weiktubeShortsSoundRequest:1};
  c.window=c;c.top=c;Object.assign(c,options);vm.createContext(c);
  function video(extra={}) {let muted=true;const v={tagName:'VIDEO',isConnected:true,paused:false,ended:false,readyState:4,getBoundingClientRect(){return {width:400,height:700,top:0,left:0,bottom:700,right:400}},closest(){return null},play(){throw Error('unexpected play')},pause(){throw Error('unexpected pause')},...extra};Object.defineProperty(v,'muted',{get(){return muted},set(x){writes++;muted=x}});videos.push(v);return v}
  return {c,document,events,video,run(){vm.runInContext(source,c)},emit(n,v,extra={}){for(const f of events[n]||[])f({target:v,...extra})},get unmutes(){return unmutes},get writes(){return writes}};
}
function test(name,fn){fn();cases++;console.log('PASS '+name)}
test('audible once and idempotent listeners',()=>{const f=fixture(),v=f.video();f.run();assert.equal(v.muted,false);assert.equal(f.unmutes,1);f.run();f.emit('playing',v);assert.equal(f.unmutes,1);assert.equal(f.events.playing.length,1)});
test('reused media and new media on later Shorts',()=>{const f=fixture(),v=f.video();f.run();f.c.location.pathname='/shorts/12345678901';f.emit('playing',v);assert.equal(f.unmutes,2);const w=f.video();f.emit('playing',w);assert.equal(f.unmutes,3)});
test('explicit user mute survives events and next Short',()=>{const f=fixture(),v=f.video();f.run();f.emit('click',{closest(){return {}}},{isTrusted:true});v.muted=true;f.emit('volumechange',v);f.emit('playing',v);f.c.location.pathname='/shorts/12345678901';f.emit('playing',v);assert.equal(v.muted,true);assert.equal(f.unmutes,1);f.c.weiktubeShortsSoundRequest=2;f.run();assert.equal(v.muted,false)});
for(const [name,configure] of Object.entries({background:f=>f.document.hidden=true,filter:f=>f.c.weiktubePausePreview=true,paused:(f,v)=>v.paused=true,unready:(f,v)=>v.readyState=0,offscreen:(f,v)=>v.getBoundingClientRect=()=>({width:400,height:700,top:900,left:0,bottom:1600,right:400}),normal:f=>f.c.location.pathname='/watch',search:f=>f.c.location.pathname='/results',foreign:f=>f.c.location.hostname='example.com',iframe:f=>f.c.top={},otherTab:f=>f.c.weiktubeShortsTab=false}))test('does not unmute '+name,()=>{const f=fixture(),v=f.video();configure(f,v);f.run();f.emit('playing',v);assert.equal(f.unmutes,0);assert.equal(f.writes,0)});
test('filter readiness handles initial and later Short without replay',()=>{const f=fixture({weiktubePausePreview:true}),v=f.video();f.run();f.emit('playing',v);f.c.weiktubePausePreview=false;f.run();assert.equal(f.unmutes,1);f.c.location.pathname='/shorts/12345678901';f.c.weiktubePausePreview=true;f.emit('playing',v);f.c.weiktubePausePreview=false;f.run();assert.equal(f.unmutes,2)});
test('paused media waits for actual playing event',()=>{const f=fixture(),v=f.video({paused:true});f.run();assert.equal(f.unmutes,0);v.paused=false;f.emit('playing',v);assert.equal(f.unmutes,1)});
test('native wiring scopes audible autoplay to dedicated Shorts and explicit entry',()=>{
  const browser=fs.readFileSync('app/src/main/java/com/weiktube/app/BrowserController.kt','utf8');
  const activity=fs.readFileSync('app/src/main/java/com/weiktube/app/MainActivity.kt','utf8');
  assert.ok(browser.includes('settings.mediaPlaybackRequiresUserGesture = !shortsPlayer'));
  assert.ok(browser.includes('shortsPlayer: Boolean = false'));
  assert.ok(browser.includes('ShortsAudioScript.SCRIPT + BROWSER_SCRIPT'));
  assert.ok(activity.includes('BrowserController(this, shield, ::openVideo, shortsPlayer = true)'));
  assert.ok(activity.includes('if (enteringShorts) target.requestShortsSound()'));
});
console.log(`${cases} Shorts sound fixtures passed (not device playback tests).`);
