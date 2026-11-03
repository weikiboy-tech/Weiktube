const fs=require('node:fs');
const vm=require('node:vm');
const assert=require('node:assert/strict');
const path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/weiktube/app/BrowserController.kt'),'utf8');
const script=source.match(/internal val BROWSER_SCRIPT = """([\s\S]*?)"""/)[1];
new vm.Script(script);
const events={};const attrs={};const inserted=[];const messages=[];
const root={setAttribute:(k,v)=>{attrs[k]=v;},appendChild:node=>inserted.push(node)};
let searchInput=null;
const context={URL,location:{hostname:'m.youtube.com',pathname:'/',href:'https://m.youtube.com/'},
  document:{documentElement:root,head:root,addEventListener:(name,fn)=>{events[name]=fn;},
    querySelector:()=>({querySelector:()=>searchInput}),
    getElementById:id=>inserted.find(n=>n.id===id),createElement:()=>({})},
  MutationObserver:class{observe(){}},requestAnimationFrame:fn=>fn(),setTimeout:fn=>fn(),
  addEventListener(){},WeiktubeNavigation:{postMessage:url=>messages.push(url)}};
context.window=context;context.top=context;
vm.createContext(context);vm.runInContext(script,context);
assert.equal(inserted.length,1);
vm.runInContext(script,context);assert.equal(inserted.length,1,'idempotent style and listener setup');
function click(href,{trusted=true,button=0}={}){
  let prevented=false;const anchor={href};
  const target={closest:selector=>selector==='a[href]'?anchor:null};
  events.click({target,isTrusted:trusted,button,preventDefault(){prevented=true;},stopImmediatePropagation(){}});
  return prevented;
}
assert.equal(click('https://m.youtube.com/watch?v=abcdefghijk'),true);
assert.equal(click('https://www.youtube.com/live/abcdefghijk'),true);
assert.equal(click('https://youtu.be/abcdefghijk?t=10'),true);
assert.equal(messages.length,3);
for(const url of ['https://m.youtube.com/shorts/abcdefghijk','https://m.youtube.com/feed/you','https://youtube.com.evil.org/watch?v=abcdefghijk','javascript:alert(1)','https://user:pass@youtube.com/watch?v=abcdefghijk','https://youtube.com:8443/watch?v=abcdefghijk'])assert.equal(click(url),false,url);
assert.equal(click('https://m.youtube.com/watch?v=abcdefghijk',{trusted:false}),false);
assert.equal(click('https://m.youtube.com/watch?v=abcdefghijk',{button:1}),false);
let pauses=0;context.weiktubePausePreview=false;events.play({target:{pause(){pauses++;}}});assert.equal(pauses,0);
context.weiktubePausePreview=true;events.play({target:{pause(){pauses++;}}});assert.equal(pauses,1);
context.weiktubeForceSearch=true;context.weiktubeApplyChrome();assert.equal(attrs['data-weiktube-search'],'1');
context.weiktubeForceSearch=false;searchInput={type:'text',getClientRects:()=>[{}]};context.weiktubeApplyChrome();assert.equal(attrs['data-weiktube-search'],'1');
searchInput=null;context.weiktubeApplyChrome();assert.equal(attrs['data-weiktube-search'],'0');
context.weiktubeDark=false;context.weiktubeApplyChrome();assert.equal(attrs['data-weiktube-theme'],'light');
const css=inserted[0].textContent;
assert.ok(css.includes('ytm-pivot-bar-renderer'));
assert.ok(!/video\s*\{|iframe\s*\{|advert|ad-showing|filter\s*:/i.test(css),'no hidden/modified playback or ads');
console.log('PASS browser scripts: trusted public watch navigation, Shorts untouched, no credential/foreign routes, no preview audio overlap, search state, theme shell and idempotence.');
