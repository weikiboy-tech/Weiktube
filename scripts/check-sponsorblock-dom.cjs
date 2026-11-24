const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),cp=require('node:child_process');
const {chromium}=require(process.env.WEIKTUBE_PLAYWRIGHT_MODULE||'playwright-core');
const script=fs.readFileSync('app/src/main/java/com/weiktube/app/SponsorBlockScript.kt','utf8').split('"""')[1];
(async()=>{
const file=path.resolve('validation/sponsor-fixture.mp4');fs.mkdirSync(path.dirname(file),{recursive:true});
cp.execFileSync(process.env.WEIKTUBE_FFMPEG||'ffmpeg',['-y','-hide_banner','-loglevel','error','-f','lavfi','-i','color=c=green:s=320x180:r=15','-t','12','-c:v','libx264','-pix_fmt','yuv420p','-movflags','+faststart',file]);
const browser=await chromium.launch({headless:true,executablePath:process.env.WEIKTUBE_CHROME_PATH,args:['--autoplay-policy=no-user-gesture-required']});
try{
 const page=await browser.newPage({viewport:{width:800,height:500}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.route('**/*',r=>{
  if(r.request().url().includes('fixture.mp4')){const data=fs.readFileSync(file),range=r.request().headers()['range'];if(!range)return r.fulfill({contentType:'video/mp4',headers:{'Accept-Ranges':'bytes'},body:data});const m=/bytes=(\d+)-(\d*)/.exec(range),start=+m[1],end=m[2]?Math.min(+m[2],data.length-1):data.length-1;return r.fulfill({status:206,contentType:'video/mp4',headers:{'Accept-Ranges':'bytes','Content-Range':`bytes ${start}-${end}/${data.length}`},body:data.subarray(start,end+1)})}
  return r.fulfill({contentType:'text/html',body:`<style>body{margin:0;background:black}#movie_player{position:relative;width:100%;height:400px}video{width:100%;height:100%;object-fit:contain}.ytp-progress-bar{position:absolute;left:10%;bottom:10px;width:80%;height:5px;background:#888}</style><div id="movie_player"><video muted playsinline src="/fixture.mp4"></video><div class="ytp-progress-bar" role="slider" aria-label="Zeitleiste"></div></div><script>window.v=document.querySelector('video');window.loads=0;v.addEventListener('loadstart',()=>loads++);window.barClicks=0;document.querySelector('[role=slider]').onclick=()=>barClicks++;document.getElementById('movie_player').getVideoData=()=>({video_id:new URL(location.href).searchParams.get('v'),isLive:false});</script>`});
 });
 await page.goto('https://m.youtube.com/watch?v=abcdefghijk');await page.waitForFunction(()=>v.readyState>=2&&v.seekable.length&&v.seekable.end(0)>11);
 const original=await page.evaluate(()=>{window.original=v;return v.currentSrc});
 await page.evaluate(script);
 await page.evaluate(()=>{window.cfg={id:'abcdefghijk',token:'real-test',enabled:true,allowed:true,markers:true,modes:{sponsor:'AUTO',intro:'MANUAL'},segments:[{start:1,end:3,uuid:'a',category:'sponsor',actionType:'skip',videoDuration:v.duration,color:'#00d400'},{start:5,end:7,uuid:'b',category:'intro',actionType:'skip',videoDuration:v.duration,color:'#00ffff'}]};weiktubeSponsorBlock.configure(cfg);return v.play()});
 await page.waitForFunction(()=>v.currentTime>=3&&!v.seeking,{},{timeout:6000});
 let snapshot=await page.evaluate(()=>weiktubeSponsorBlock.snapshot());assert.equal(snapshot.events.filter(e=>e.type==='skip').length,1);assert.ok(snapshot.notice.seconds>1.5);assert.equal(await page.evaluate(()=>v.paused),false);
 const notice=snapshot.notice;
 assert.equal(await page.evaluate(n=>weiktubeSponsorBlock.undo('real-test',n.seq),notice),true);await page.waitForFunction(()=>!v.seeking&&v.currentTime<2);
 snapshot=await page.evaluate(()=>weiktubeSponsorBlock.snapshot());assert.equal(snapshot.events[0].type,'undo');
 await page.waitForTimeout(300);assert.ok(await page.evaluate(()=>v.currentTime<3));
 console.log('PASS real HTMLVideoElement auto skip, seeked confirmation, undo and continued playback');
 await page.evaluate(()=>{v.pause();v.currentTime=5.5});await page.waitForFunction(()=>!v.seeking);snapshot=await page.evaluate(()=>weiktubeSponsorBlock.snapshot());
 // Seeking deliberately into any segment suppresses automatic handling, but a manual category remains user-skippable.
 assert.ok(snapshot.manual,'manual action should remain available after deliberate seek');
 assert.equal(await page.evaluate(()=>weiktubeSponsorBlock.manual('real-test',['b'])),true);await page.waitForFunction(()=>!v.seeking&&v.currentTime>=7);assert.equal(await page.evaluate(()=>v.paused),true);
 console.log('PASS manual skip preserves genuinely paused media');
 const geometry=await page.locator('[data-weiktube-sponsor-markers] > span').first().evaluate(e=>({left:parseFloat(e.style.left),width:parseFloat(e.style.width),pointer:getComputedStyle(e).pointerEvents}));assert.ok(Math.abs(geometry.left-100/12)<.2);assert.ok(Math.abs(geometry.width-200/12)<.2);assert.equal(geometry.pointer,'none');
 await page.locator('[role=slider]').click();assert.equal(await page.evaluate(()=>barClicks),1);
 await page.setViewportSize({width:400,height:800});await page.evaluate(()=>weiktubeSponsorBlock.snapshot());assert.equal(await page.locator('[data-weiktube-sponsor-markers]').count(),1);
 for(let i=0;i<10;i++)await page.evaluate(()=>weiktubeSponsorBlock.configure(cfg));assert.equal(await page.locator('[data-weiktube-sponsor-markers]').count(),1);assert.equal(await page.locator('video').count(),1);
 assert.equal(await page.evaluate(()=>v===original&&v.currentSrc),original);
 console.log('PASS markers share original seekbar, clicks pass through, rotation and reconfiguration keep one layer/video');
 await page.evaluate(()=>weiktubeSponsorBlock.configure({...cfg,markers:false}));assert.equal(await page.locator('[data-weiktube-sponsor-markers]').count(),0);
 await page.evaluate(()=>{history.pushState({},'', '/watch?v=zzzzzzzzzzz');v.currentTime=1.5});await page.waitForFunction(()=>!v.seeking);assert.equal(await page.evaluate(()=>weiktubeSponsorBlock.undo('real-test',1)),false);
 await page.evaluate(()=>weiktubeSponsorBlock.stop());assert.equal(await page.locator('[data-weiktube-sponsor-markers]').count(),0);assert.deepEqual(errors,[]);
 console.log('PASS opt-out/marker toggle and SPA mismatch safely stop effects');
}finally{await browser.close()}
})().catch(e=>{console.error(e);process.exit(1)});
