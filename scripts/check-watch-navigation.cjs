const fs=require('node:fs'), vm=require('node:vm'), assert=require('node:assert/strict'),path=require('node:path');
const src=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/weiktube/app/WatchNavigationScript.kt'),'utf8');
const script=src.match(/internal val SCRIPT = """([\s\S]*?)"""/)[1];
const listeners=[],messages=[];
const scope={URL,location:{hostname:'m.youtube.com',pathname:'/watch',href:'https://m.youtube.com/watch?v=abcdefghijk'},document:{addEventListener:(n,fn)=>listeners.push(fn)},WeiktubeWatchNavigation:{postMessage:url=>messages.push(url)}};
scope.top=scope;scope.window=scope;vm.createContext(scope);vm.runInContext(script,scope);vm.runInContext(script,scope);assert.equal(listeners.length,1);
function click(url,trusted=true){let stopped=false;listeners[0]({isTrusted:trusted,button:0,target:{closest:()=>({href:url})},preventDefault(){stopped=true;},stopImmediatePropagation(){}});return stopped;}
for(const url of ['https://m.youtube.com/watch?v=abcdefghijk','https://m.youtube.com/@creator','https://m.youtube.com/channel/UC_123','https://m.youtube.com/playlist?list=PL_123','https://accounts.google.com/ServiceLogin?service=youtube'])assert.equal(click(url),true,url);
for(const url of ['https://m.youtube.com/redirect?q=ad','https://ad.example.org/','https://youtube.com.evil.test/watch?v=abcdefghijk','https://user:pass@youtube.com/@creator','javascript:alert(1)'])assert.equal(click(url),false,url);
assert.equal(click('https://m.youtube.com/@creator',false),false);
assert.equal(messages.length,5);
console.log('PASS watch navigation fixture: trusted anchor handoff preserves document, channel/video/playlist/login routes only; adverts, foreign hosts and scripted clicks untouched.');
