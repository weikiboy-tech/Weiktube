// Exercise the packaged content script in a DOM/message-port fixture.
// This verifies interception and lifecycle policy, not real Google authentication.
const {readFileSync} = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const code = readFileSync(require('node:path').join(__dirname, '../engine-probe/src/main/assets/navigation/navigation.js'), 'utf8');
function fixture() {
  let receive, disconnect, pauses=0;
  const events={}, messages=[];
  const port={onMessage:{addListener:f=>receive=f},onDisconnect:{addListener:f=>disconnect=f},postMessage:m=>messages.push(m)};
  const window={}; window.top=window;
  const document={hidden:false,documentElement:{appendChild(){}},querySelectorAll:()=>[{pause(){pauses++}}],getElementById:()=>null,createElement:()=>({}),addEventListener:(n,f)=>events[n]=f};
  vm.runInNewContext(code,{window,document,browser:{runtime:{connectNative:()=>port}},location:{href:'https://m.youtube.com/'},URL});
  return {messages,events,document,get pauses(){return pauses},configure: m=>receive({type:'configure',role:'feed',paused:false,...m}),disconnect:()=>disconnect(),click(href,overrides={}) {
    const e={isTrusted:true,button:0,target:{closest:()=>({href})},preventDefault(){this.prevented=true},stopImmediatePropagation(){this.stopped=true},...overrides};
    events.click(e); return e;
  }};
}
let f=fixture(); assert.ok(!f.click('/watch?v=abcdefghijk').prevented);
f.configure(); let e=f.click('/watch?v=abcdefghijk&t=12');
assert.ok(e.prevented && e.stopped); assert.equal(f.messages.length,1); assert.equal(f.messages[0].type,'video');
assert.ok(f.pauses>0);
for(const url of ['/feed/library','/results?search_query=cat','https://youtube.com.evil.org/watch?v=abcdefghijk','http://youtube.com/watch?v=abcdefghijk','https://accounts.google.com/','/watch?v=short']) {
  assert.ok(!f.click(url).prevented,url);
}
assert.ok(!f.click('/watch?v=abcdefghijk',{isTrusted:false}).prevented);
assert.ok(!f.click('/watch?v=abcdefghijk',{ctrlKey:true}).prevented);
f.configure({role:'player'}); assert.ok(!f.click('/watch?v=abcdefghijk').prevented);
f.configure({paused:true}); let p=f.pauses; f.events.play(); assert.ok(f.pauses>p);
f.configure({paused:false}); p=f.pauses; f.document.hidden=true; f.events.visibilitychange(); assert.ok(f.pauses>p);
f.disconnect(); assert.ok(!f.click('/watch?v=abcdefghijk').prevented);
console.log('PASS: feed interception, non-video/auth links, trusted gestures, player isolation, background pause, disconnect');
