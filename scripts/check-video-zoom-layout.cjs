// Actual Chromium rendering of the production video-only zoom. Offline synthetic media, not YouTube/device QA.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {chromium}=require(process.env.WEIKTUBE_PLAYWRIGHT_MODULE||'playwright-core');
const script=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/weiktube/app/VideoZoomScript.kt'),'utf8').split('"""')[1];
(async()=>{const browser=await chromium.launch({headless:true,executablePath:process.env.WEIKTUBE_CHROME_PATH});try{
const page=await browser.newPage({viewport:{width:1000,height:600}});
await page.route('**/*',r=>r.fulfill({contentType:'text/html',body:`<style>body{margin:0;background:magenta}#player-container-id{width:100vw;height:100vh;background:black;position:relative}video{width:100%;height:100%;object-fit:contain}button{position:absolute;top:10px;right:10px;width:90px;height:40px}main{height:600px;background:yellow}</style><div id="player-container-id"><video muted playsinline></video><button>Controls</button></div><main>Unscaled details</main>`}));
await page.goto('https://m.youtube.com/watch?v=fixture0000');
for(const [w,h] of [[1920,1080],[1440,1080],[1080,1920],[1000,1000]]){
 await page.evaluate(async([w,h])=>{clearInterval(window.fixtureTimer);window.fixtureStream?.getTracks().forEach(t=>t.stop());const c=document.createElement('canvas');c.width=w;c.height=h;const ctx=c.getContext('2d');ctx.fillStyle='lime';ctx.fillRect(0,0,w,h);ctx.fillStyle='red';ctx.fillRect(0,0,w/4,h);ctx.fillStyle='blue';ctx.fillRect(w*3/4,0,w/4,h);window.fixtureTimer=setInterval(()=>{ctx.fillStyle="red";ctx.fillRect(0,0,1,1)},40);window.fixtureStream=c.captureStream(20);const v=document.querySelector('video');v.srcObject=window.fixtureStream;await v.play();window.fixtureVideo=v;window.fixtureCanvas=c;},[w,h]);
 await page.evaluate(script);
 const before=await page.evaluate(()=>({time:fixtureVideo.currentTime,button:JSON.stringify(document.querySelector('button').getBoundingClientRect()),details:JSON.stringify(document.querySelector('main').getBoundingClientRect())}));
 const fill=Math.max(1000/Math.min(1000,600*w/h),600/(Math.min(1000,600*w/h)*h/w));
 await page.evaluate(z=>weiktubeVideoZoom(z,.5,.5),fill);
 await page.screenshot({path:process.env.WEIKTUBE_ZOOM_SCREENSHOT_DIR?path.join(process.env.WEIKTUBE_ZOOM_SCREENSHOT_DIR,`zoom-${w}-${h}.png`):undefined});
 for(let i=0;i<10;i++)await page.evaluate(()=>{weiktubeVideoZoom(3,.3,.4);weiktubeVideoZoom(1,.5,.5)});
 await page.waitForFunction(t=>fixtureVideo.currentTime>t+.1,before.time);
 const after=await page.evaluate(()=>({same:fixtureVideo===document.querySelector('video'),paused:fixtureVideo.paused,button:JSON.stringify(document.querySelector('button').getBoundingClientRect()),details:JSON.stringify(document.querySelector('main').getBoundingClientRect()),transform:fixtureVideo.style.transform}));
 assert.equal(after.same,true);assert.equal(after.paused,false);assert.equal(after.transform,'');assert.equal(after.button,before.button);assert.equal(after.details,before.details);
 await page.evaluate(()=>{fixtureVideo.pause();weiktubeVideoZoom(2,.5,.5);weiktubeVideoZoom(1,.5,.5)});
 assert.equal(await page.evaluate(()=>fixtureVideo.paused),true);
 await page.evaluate(()=>fixtureVideo.play());
 console.log(`PASS Chromium ${w}:${h}: fill, 10 resets, same live video, fixed controls/details`);
}
await page.evaluate(()=>weiktubeVideoZoom(3,.5,.5));await page.setViewportSize({width:400,height:800});await page.waitForFunction(()=>document.querySelector('video').style.transform==='');
console.log('PASS Chromium resize resets video transform. No Android/device-test claim.');
}finally{await browser.close()}})().catch(e=>{console.error(e);process.exitCode=1});
