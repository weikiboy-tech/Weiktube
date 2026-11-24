const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const script=fs.readFileSync('app/src/main/java/com/weiktube/app/SponsorBlockScript.kt','utf8').split('"""')[1];
const segment=(start=10,end=30,category='sponsor',uuid='a',videoDuration=100)=>({start,end,category,uuid,videoDuration,actionType:'skip',color:'#00d400'});
function fixture(segments=[segment()],modes={sponsor:'AUTO'},options={}){
 let clock=1000,time=0,nextTimer=0,seekCommands=0,events={},timers=new Map(),live=false,ad=false,id='abcdefghijk';
 const v={isConnected:true,duration:100,readyState:4,paused:false,ended:false,seeking:false,playbackRate:1,
 seekable:{length:1,start:()=>0,end:()=>100},addEventListener(n,f){(events[n]??=new Set()).add(f)},removeEventListener(n,f){events[n]?.delete(f)},play(){throw Error('must never play')},pause(){throw Error('must never pause')},load(){throw Error('must never load')}};
 const emit=n=>{for(const f of [...events[n]||[]])f({type:n,target:v})};
 Object.defineProperty(v,'currentTime',{get:()=>time,set:t=>{time=t;seekCommands++;v.seeking=true;emit('seeking')}});
 const player={getVideoData:()=>({video_id:id,isLive:live})};
 const c={URL,Set,Date:{now:()=>clock},location:{href:'https://m.youtube.com/watch?v=abcdefghijk',pathname:'/watch'},
 document:{querySelector:q=>q==='video'?v:q.includes('.ad-showing')&&ad?{}:null,getElementById:()=>player,querySelectorAll:()=>[]},
 setTimeout(f,ms){const i=++nextTimer;timers.set(i,{f,at:clock+ms});return i},clearTimeout:i=>timers.delete(i),addEventListener(){}};
 c.window=c;vm.createContext(c);vm.runInContext(script,c);
 const config={id,token:'test-session',enabled:true,allowed:true,markers:false,modes,segments,...options};
 c.weiktubeSponsorBlock.configure(config);
 return {v,c,api:c.weiktubeSponsorBlock,config,emit,
 time(t){time=t;emit('timeupdate')},finish(){v.seeking=false;emit('seeked')},
 seek(t){v.currentTime=t;this.finish()},advance(ms){clock+=ms;for(const [i,t]of [...timers])if(t.at<=clock){timers.delete(i);t.f()}},
 commands:()=>seekCommands,snap:()=>c.weiktubeSponsorBlock.snapshot(),live:b=>live=b,ad:b=>ad=b,
 switchId:newId=>{id=newId;c.location.href='https://m.youtube.com/watch?v='+newId},listeners:()=>Object.values(events).reduce((n,e)=>n+e.size,0),timers:()=>timers.size};
}
let tests=0;function test(name,run){run();tests++;console.log('PASS '+name)}
test('auto seeks once, preserves playback/rate, counts only seeked',()=>{const f=fixture();f.v.playbackRate=2;f.time(10);assert.equal(f.v.currentTime,30);assert.equal(f.commands(),1);assert.equal(f.snap().events.length,0);f.finish();const s=f.snap();assert.equal(s.events.length,1);assert.equal(s.events[0].type,'skip');assert.equal(s.events[0].to-s.events[0].from,20);assert.equal(f.v.playbackRate,2);assert.equal(f.v.paused,false);f.emit('timeupdate');assert.equal(f.snap().events.length,0);assert.equal(f.commands(),1)});
test('overlapping and adjacent auto categories make one union jump',()=>{const f=fixture([segment(10,30),segment(20,40,'selfpromo','b'),segment(40,50,'sponsor','c')],{sponsor:'AUTO',selfpromo:'AUTO'});f.time(11);assert.equal(f.v.currentTime,50);f.finish();assert.equal(f.snap().events[0].ids.length,3);assert.equal(f.commands(),1)});
test('manual and disabled categories do not extend auto union',()=>{const f=fixture([segment(10,20),segment(15,40,'intro','b'),segment(20,60,'filler','c')],{sponsor:'AUTO',intro:'MANUAL',filler:'OFF'});f.time(10);assert.equal(f.v.currentTime,20);f.finish();assert.equal(f.snap().manual.category,'intro');assert.equal(f.commands(),1);assert.equal(f.api.manual('test-session',['b']),true);f.finish();assert.equal(f.v.currentTime,40)});
test('manual skips preserve pause, auto never seeks paused media',()=>{const f=fixture();f.v.paused=true;f.time(12);assert.equal(f.commands(),0);const m=fixture([segment(10,30,'intro')],{intro:'MANUAL'});m.v.paused=true;m.time(12);assert.equal(m.api.manual('test-session',['a']),true);m.finish();assert.equal(m.v.paused,true)});
test('undo returns exact pre-skip position, reports reversal and suppresses reskip',()=>{const f=fixture();f.time(14);f.finish();const n=f.snap().notice;assert.equal(n.seconds,16);assert.equal(f.api.undo('test-session',n.seq),true);f.finish();assert.equal(f.v.currentTime,14);assert.equal(f.snap().events[0].type,'undo');f.time(15);f.time(20);assert.equal(f.commands(),2)});
test('deliberate seek into a skipped section is viewable and rolls back latest count',()=>{const f=fixture();f.time(10);f.finish();f.snap();f.seek(15);assert.equal(f.v.currentTime,15);assert.equal(f.snap().events[0].type,'undo');f.time(16);assert.equal(f.commands(),2)});
test('seeking into an unskipped section respects user intention',()=>{const f=fixture();f.seek(15);f.time(16);assert.equal(f.commands(),1);assert.equal(f.snap().events.length,0)});
test('duration mismatch, invalid ranges, unknown duration automatic, live and unseekable',()=>{
 for(const bad of [segment(-1,5),segment(10,200),segment(30,10),segment(10,30,'sponsor','a',90)]){const f=fixture([bad]);f.time(12);assert.equal(f.commands(),0)}
 const u=fixture([segment(10,30,'sponsor','a',0)]);u.time(12);assert.equal(u.commands(),0);assert.ok(u.snap().manual);
 const l=fixture();l.live(true);l.time(12);assert.equal(l.commands(),0);
 const n=fixture();n.v.seekable.length=0;n.time(12);assert.equal(n.commands(),0);
 const inf=fixture();inf.v.duration=Infinity;inf.time(12);assert.equal(inf.commands(),0);
});
test('filter/host gate, wrong identity and ads prevent jumps',()=>{for(const change of [f=>f.api.gate(false),f=>f.switchId('zzzzzzzzzzz'),f=>f.ad(true)]){const f=fixture();change(f);f.time(12);assert.equal(f.commands(),0)}});
test('failed seek confirmation does not count or loop',()=>{const f=fixture();f.time(10);f.advance(2100);f.time(11);assert.equal(f.commands(),1);assert.equal(f.snap().events.length,0)});
test('configuration refresh / mini / pip retain attempts and listener identity',()=>{const f=fixture();const count=f.listeners();f.time(10);f.finish();f.snap();for(let i=0;i<10;i++)f.api.configure({...f.config});assert.equal(f.listeners(),count);f.seek(11);assert.equal(f.commands(),2);f.api.stop();assert.equal(f.listeners(),0);assert.equal(f.timers(),0);f.time(12);assert.equal(f.commands(),2)});
test('new video token clears old events and old manual/undo commands fail',()=>{const f=fixture();f.time(10);f.finish();f.switchId('zzzzzzzzzzz');f.api.configure({...f.config,id:'zzzzzzzzzzz',token:'next',segments:[]});assert.equal(f.snap().events.length,0);assert.equal(f.api.undo('test-session',1),false);assert.equal(f.api.manual('test-session',['a']),false)});
test('rate-aware scheduling catches the next boundary without polling bridge',()=>{const f=fixture();f.v.playbackRate=3;f.time(9);assert.ok(f.timers()>0);f.time(10.1);assert.equal(f.v.currentTime,30);assert.equal(f.commands(),1)});
test('manual category remains available after intentional seek',()=>{const f=fixture([segment(10,30,'intro')],{intro:'MANUAL'});f.seek(15);assert.ok(f.snap().manual);assert.equal(f.api.manual('test-session',['a']),true);f.finish();assert.equal(f.v.currentTime,30)});
console.log(`${tests} SponsorBlock media-state fixtures passed; no device playback claim.`);
module.exports={fixture,segment};
