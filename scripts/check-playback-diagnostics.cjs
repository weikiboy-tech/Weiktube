// Executes the exact production diagnostic script with media/bridge fakes, not Android or YouTube.
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const assert = require('node:assert/strict');
const source = fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/weiktube/app/PlayerPlaybackDiagnostics.kt'), 'utf8');
const script = source.match(/internal val SCRIPT = """([\s\S]*?)"""/)[1];
new vm.Script(script);
let passed = 0;
function test(name, run) { run(); console.log(`PASS ${name}`); passed++; }
function fixture({origin='https://m.youtube.com', embedded=false, bridge=true}={}) {
  const listeners = new Map(), windows = new Map(), messages = [], queue = [], attributes = new Map();
  let time=0;
  const forbidden = () => {throw Error('Diagnostic read secret/URL/stack/time or issued a playback command');};
  function emit(type,target=current) {for (const fn of listeners.get(type)||[]) fn({target});}
  class Media {
    constructor(tag='VIDEO') {Object.assign(this,{tagName:tag,paused:false,isConnected:true,readyState:4,pauseCount:0,result:{originalResult:true}});}
    pause(...args) {
      if (!(this instanceof Media)) throw invalidReceiver;
      this.pauseCount++; this.originalArgs=args;
      if (this.failure) throw this.failure;
      const changed=!this.paused;this.paused=true;
      if (changed) queue.push(()=>emit('pause',this));
      return this.result;
    }
    play() {forbidden();}
  }
  const invalidReceiver=Error('native receiver error');
  const current=new Media();
  for (const key of ['src','currentSrc','currentTime']) Object.defineProperty(current,key,{get:forbidden,set:forbidden});
  const original=Media.prototype.pause, descriptor=Object.getOwnPropertyDescriptor(Media.prototype,'pause');
  const document={hidden:false,documentElement:{getAttribute:key=>attributes.get(key)||null},
    querySelector(selector) {assert.equal(selector,'video');return current;},
    addEventListener(type,fn) {const list=listeners.get(type)||[];list.push(fn);listeners.set(type,list);}};
  Object.defineProperty(document,'cookie',{get:forbidden,set:forbidden});
  const location={origin};Object.defineProperty(location,'href',{get:forbidden});
  const context={document,location,HTMLMediaElement:Media,performance:{now:()=>time},innerWidth:384,innerHeight:800,
    addEventListener(type,fn) {const list=windows.get(type)||[];list.push(fn);windows.set(type,list);}};
  for (const key of ['localStorage','sessionStorage','fetch','XMLHttpRequest']) Object.defineProperty(context,key,{get:forbidden});
  context.window=context;context.top=embedded?{}:context;
  if (bridge) context.WeiktubePlaybackDiagnostics={postMessage(raw){assert(raw.length<=768);messages.push(JSON.parse(raw));}};
  vm.createContext(context);
  const install=()=>vm.runInContext(script,context);install();
  return {context,current,Media,messages,listeners,windows,descriptor,original,invalidReceiver,emit,install,attributes,
    start(token='document-one'){context.weiktubeDiagnosePlayback(token);},
    advance(ms){time+=ms;},flush(){while(queue.length)queue.shift()();},
    windowEvent(type){for(const fn of windows.get(type)||[])fn();},latest(){return messages.at(-1);}};
}

test('Transparent pause preserves receiver, arguments, return value and descriptor; installs once',()=>{
  const f=fixture();f.start();const wrapped=f.Media.prototype.pause;
  assert.equal(f.current.pause('argument',7),f.current.result);f.flush();
  assert.equal(f.current.pauseCount,1);assert.deepEqual(f.current.originalArgs,['argument',7]);
  assert.equal(f.messages.at(-2).event,'PAUSE_CALL');assert.equal(f.messages.at(-2).reason,'JS_UNATTRIBUTED');
  assert.equal(f.latest().event,'PAUSE');assert.equal(f.latest().reason,'JS_UNATTRIBUTED');
  const descriptor=Object.getOwnPropertyDescriptor(f.Media.prototype,'pause');
  for(const key of ['writable','configurable','enumerable'])assert.equal(descriptor[key],f.descriptor[key]);
  f.install();f.install();f.start();assert.equal(f.Media.prototype.pause,wrapped);
  for(const list of [...f.listeners.values(),...f.windows.values()])assert.equal(list.length,1);
  assert.throws(()=>wrapped.call({}),error=>error===f.invalidReceiver);
});

test('Original pause exceptions and scope callback exceptions propagate unchanged',()=>{
  const f=fixture();f.start();const failure=Error('original exception');
  Object.defineProperty(failure,'stack',{get(){throw Error('must not read stack');}});
  f.current.failure=failure;
  assert.throws(()=>f.context.weiktubePlaybackScope('APP_COMMAND',()=>f.current.pause()),error=>error===failure);
  assert.equal(f.current.pauseCount,1);
  delete f.current.failure;f.emit('pause');assert.equal(f.latest().reason,'ENGINE_OR_UNOBSERVED');
  assert.throws(()=>f.context.weiktubePlaybackScope('APP_GUARD',()=>{throw failure;}),error=>error===failure);
  f.current.pause();assert.equal(f.latest().reason,'JS_UNATTRIBUTED');
});

test('App command/guard scopes nest and survive the asynchronous pause event without leaking',()=>{
  const f=fixture();f.start();
  const result=f.context.weiktubePlaybackScope('APP_COMMAND',()=>{
    f.context.weiktubePlaybackScope('APP_GUARD',()=>f.current.pause());f.flush();
    assert.equal(f.latest().reason,'APP_GUARD');
    f.current.paused=false;return f.current.pause();
  });
  assert.equal(result,f.current.result);f.flush();assert.equal(f.latest().reason,'APP_COMMAND');
  f.current.paused=false;f.current.pause();f.flush();assert.equal(f.latest().reason,'JS_UNATTRIBUTED');
  f.current.paused=false;f.context.weiktubePlaybackScope('https://secret.invalid/',()=>f.current.pause());
  f.flush();assert.equal(f.latest().reason,'JS_UNATTRIBUTED');
});

test('Unobserved/native pauses and stale calls are not falsely attributed to app scopes',()=>{
  const f=fixture();f.start();
  f.original.call(f.current);f.flush();assert.equal(f.latest().reason,'ENGINE_OR_UNOBSERVED');
  f.context.weiktubePlaybackScope('APP_COMMAND',()=>f.current.pause()); // already paused: no native pause event
  f.current.paused=false;f.emit('play');f.original.call(f.current);f.flush();
  assert.equal(f.latest().reason,'ENGINE_OR_UNOBSERVED');
  f.current.paused=false;f.context.weiktubePlaybackScope('APP_GUARD',()=>f.current.pause());
  f.advance(1001);f.flush();assert.equal(f.latest().reason,'ENGINE_OR_UNOBSERVED');
});

test('Only fixed event/state fields cross the bridge; no credentials, URL, time or stack reads',()=>{
  const f=fixture();f.start();f.emit('play');f.emit('playing');f.emit('loadedmetadata');
  f.attributes.set('data-weiktube-mini','1');f.current.isConnected=false;
  f.context.document.hidden=true;f.emit('visibilitychange');
  assert.equal(f.latest().event,'VISIBILITY');assert.equal(f.latest().hidden,true);
  assert.equal(f.latest().mini,true);assert.equal(f.latest().connected,false);
  f.attributes.set('data-weiktube-system-pip','1');f.context.innerWidth=Infinity;f.context.innerHeight=-1;
  f.current.readyState=99;f.windowEvent('pagehide');f.windowEvent('blur');f.windowEvent('resize');
  assert.equal(f.latest().event,'RESIZE');assert.equal(f.latest().pip,true);
  assert.equal(f.latest().width,0);assert.equal(f.latest().height,0);assert.equal(f.latest().readyState,0);
  assert.deepEqual(Object.keys(f.latest()).sort(),['token','event','reason','sequence','elapsedMs','video','paused','connected','hidden','readyState','mini','pip','width','height'].sort());
  assert.equal(f.current.pauseCount,0,'read-only state events never pause or start media');
});

test('Sliding-minute rate bound permits fresh events without accumulating an unbounded buffer',()=>{
  const f=fixture();f.start();for(let i=0;i<500;i++)f.emit('playing');
  assert.equal(f.messages.length,120);
  f.advance(59999);f.emit('play');assert.equal(f.messages.length,120);
  f.advance(1);f.emit('play');assert.equal(f.messages.length,121);
  assert.equal(f.latest().sequence,121);
  for(let i=0;i<500;i++)f.emit('playing');assert.equal(f.messages.length,240);
});

test('Origin/frame boundaries and missing bridge do not alter original playback behavior',()=>{
  for(const options of [{origin:'https://accounts.google.com'},{origin:'http://m.youtube.com'},
    {origin:'https://m.youtube.com:8443'},{origin:'https://youtube.com.evil.invalid'},{embedded:true}]){
    const f=fixture(options);assert.equal(f.Media.prototype.pause,f.original);
    assert.equal(f.context.weiktubePlaybackScope,undefined);assert.equal(f.listeners.size,0);
  }
  const direct=fixture({origin:'https://com.weiktube.app'});direct.start();assert.equal(direct.messages.length,1);
  const missing=fixture({bridge:false});missing.start();
  assert.equal(missing.current.pause(),missing.current.result);assert.equal(missing.current.pauseCount,1);
});

test('Document token rotation does not duplicate hooks or reset the rate limit',()=>{
  const f=fixture();f.start();f.current.pause();f.flush();
  const wrapped=f.Media.prototype.pause;f.start('document-two');
  assert.equal(f.latest().token,'document-two');assert.equal(f.latest().event,'INITIAL');assert.equal(f.latest().sequence,1);
  f.start('x'.repeat(65));assert.equal(f.latest().token,'document-two');
  for(let i=0;i<500;i++)f.start('document-'+i);
  assert.equal(f.messages.length,120);assert.equal(f.Media.prototype.pause,wrapped);
});
console.log(`PASS ${passed} playback diagnostic fixtures. Native parser/rate limit have separate JVM tests; no device-test claim.`);
