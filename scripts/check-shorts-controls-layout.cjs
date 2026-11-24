// Offline Chromium test with an actual finite MP4 + audio track. Not Android/YouTube device QA.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),os=require('node:os'),cp=require('node:child_process');
const {chromium}=require(process.env.WEIKTUBE_PLAYWRIGHT_MODULE||'playwright-core');
const script=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/weiktube/app/ShortsAudioScript.kt'),'utf8').split('"""')[1];
(async()=>{const dir=fs.mkdtempSync(path.join(os.tmpdir(),'weiktube-shorts-')),file=path.join(dir,'clip.mp4');
cp.execFileSync(process.env.WEIKTUBE_FFMPEG||'ffmpeg',['-hide_banner','-loglevel','error','-f','lavfi','-i','color=c=blue:s=240x426:r=15','-f','lavfi','-i','sine=frequency=440:sample_rate=44100','-t','12','-c:v','libx264','-pix_fmt','yuv420p','-c:a','aac','-movflags','+faststart',file]);
const browser=await chromium.launch({headless:true,executablePath:process.env.WEIKTUBE_CHROME_PATH,args:[process.env.WEIKTUBE_STRICT_AUTOPLAY?'--autoplay-policy=document-user-activation-required':'--autoplay-policy=no-user-gesture-required']});try{
const page=await browser.newPage({viewport:{width:400,height:700}});
await page.route('**/*',r=>r.request().url().includes('clip.mp4')?(()=>{const data=fs.readFileSync(file),range=r.request().headers()['range'];if(!range)return r.fulfill({contentType:'video/mp4',headers:{'Accept-Ranges':'bytes'},body:data});const m=/bytes=(\d+)-(\d*)/.exec(range);const start=Number(m[1]),end=m[2]?Math.min(Number(m[2]),data.length-1):data.length-1;return r.fulfill({status:206,contentType:'video/mp4',headers:{'Accept-Ranges':'bytes','Content-Range':`bytes ${start}-${end}/${data.length}`},body:data.subarray(start,end+1)})})():r.fulfill({contentType:'text/html',body:`<style>body{margin:0;background:black}video{width:400px;height:700px}#mute{position:fixed;left:5px;top:5px}</style><ytm-reel-video-renderer><div class="html5-video-player"><video autoplay muted playsinline loop src="/clip.mp4"></video></div><button id="mute" aria-label="Stummschalten">Mute</button></ytm-reel-video-renderer>${process.env.WEIKTUBE_STRICT_AUTOPLAY?"<script>window.weiktubeShortsTab=true;window.weiktubePausePreview=false;window.weiktubeShortsSoundRequest=1;window.v=document.querySelector('video');v.volume=0;"+script+"</script>":""}`}));
page.on('pageerror',e=>console.log('PAGE ERROR',e.message));
await page.goto('https://m.youtube.com/shorts/abcdefghijk');
if(process.env.WEIKTUBE_STRICT_AUTOPLAY){
 await page.waitForTimeout(1000);
 const cdp=await page.context().newCDPSession(page);
 const observed=await cdp.send('Runtime.evaluate',{expression:'JSON.stringify({paused:v.paused,muted:v.muted,volume:v.volume,ready:v.readyState,activation:navigator.userActivation.hasBeenActive})',returnByValue:true,userGesture:false});
 const state=JSON.parse(observed.result.value);assert.equal(state.activation,false);assert.ok(state.paused||state.muted);
 console.log('Observed blocked autoplay without user activation:',state);
 await page.locator('#weiktube-shorts-sound').waitFor({state:'visible',timeout:2000});
 assert.ok(state.paused||state.muted);
 await page.locator('#weiktube-shorts-sound').click();
 await page.waitForFunction(()=>!v.muted&&v.volume>0&&!v.paused);
 console.log('PASS strict Chromium autoplay: genuine sound click resumes audible media after blocked automatic attempt');return;
}
await page.evaluate(()=>{window.weiktubeShortsTab=true;window.weiktubePausePreview=false;window.weiktubeShortsSoundRequest=1;window.v=document.querySelector('video');v.volume=0;document.querySelector('#mute').onclick=()=>v.muted=true;return v.play()});
await page.evaluate(script);
await page.waitForFunction(()=>!v.muted&&v.volume>0&&!v.paused);
await page.waitForFunction(()=>v.seekable.length&&v.seekable.end(0)>11);
const original=await page.evaluate(()=>v.currentSrc);
for(const f of [.25,.5,.75]){await page.mouse.click(400*f,684);await page.waitForFunction(t=>Math.abs(v.currentTime-v.duration*t)<.5,f,{timeout:1000});assert.equal(await page.evaluate(()=>v.paused),false);console.log('PASS real media tap '+f)}
await page.evaluate(()=>v.pause());
await page.mouse.move(80,684);await page.mouse.down();await page.mouse.move(320,684,{steps:8});
assert.ok(parseFloat(await page.locator('#weiktube-shorts-seek > div > div').evaluate(e=>e.style.width))>75);
await page.mouse.up();await page.waitForFunction(()=>Math.abs(v.currentTime-v.duration*.8)<.1);assert.equal(await page.evaluate(()=>v.paused),true);
await page.mouse.move(320,684);await page.mouse.down();await page.mouse.move(80,684,{steps:8});await page.mouse.up();await page.waitForFunction(()=>Math.abs(v.currentTime-v.duration*.2)<.1);
console.log('PASS real horizontal pointer drag both directions; paused state preserved');
await page.mouse.move(100,684);await page.mouse.down();await page.evaluate(()=>{history.pushState({},'', '/shorts/12345678901');v.currentTime=0});await page.mouse.move(300,684,{steps:5});await page.mouse.up();assert.ok(await page.evaluate(()=>v.currentTime<.1));
console.log('PASS SPA change rejects pending seek');
await page.evaluate(()=>{v.muted=true;return v.play()});await page.waitForFunction(()=>!v.muted);
await page.locator('#mute').click();await page.waitForTimeout(100);assert.equal(await page.evaluate(()=>v.muted),true);
await page.evaluate(()=>{history.pushState({},'', '/shorts/23456789012');v.dispatchEvent(new Event('timeupdate'))});await page.waitForTimeout(100);assert.equal(await page.evaluate(()=>v.muted),true);
await page.evaluate(()=>{weiktubeShortsSoundRequest=2;weiktubeUpdateShortsSound()});await page.waitForFunction(()=>!v.muted);
console.log('PASS intentional mute preserved across SPA; new visit restores audible media');
await page.evaluate(()=>v.muted=true);await page.locator('#weiktube-shorts-sound').waitFor({state:'visible'});await page.waitForTimeout(100);assert.equal(await page.evaluate(()=>v.muted),true);await page.locator('#weiktube-shorts-sound').click();await page.waitForFunction(()=>!v.muted&&!v.paused);
assert.equal(await page.evaluate(()=>v.currentSrc),original);assert.equal(await page.locator('video').count(),1);
console.log('PASS late remute remains stable until genuine user sound click; no reload/new video');
}finally{await browser.close();fs.rmSync(dir,{recursive:true,force:true})}})().catch(e=>{console.error(e);process.exitCode=1});
