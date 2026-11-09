// Actual Chromium media stream and exact production scripts. No Android/PiP or audible-output claim.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const {chromium} = require(process.env.WEIKTUBE_PLAYWRIGHT_MODULE || 'playwright-core');
const source = fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/weiktube/app/PlayerController.kt'), 'utf8');
const template = name => source.match(new RegExp('internal val '+name+' = """([\\s\\S]*?)"""'))[1];
const guard = template('MEDIA_GUARD'), presentation = template('WATCH_CHROME');
const html = `<!doctype html><html><body><button id="start">Start test media</button>
<div id="movie_player" class="html5-video-player"><video playsinline controls></video></div>
<script>
window.starts=0;window.pauses=0;
const v=document.querySelector('video');
v.addEventListener('play',()=>starts++);v.addEventListener('pause',()=>pauses++);
document.querySelector('button').onclick=async()=>{
  const canvas=document.createElement('canvas');canvas.width=320;canvas.height=180;
  const ctx=canvas.getContext('2d');let frame=0;
  function paint(){ctx.fillStyle=frame++%2?'red':'blue';ctx.fillRect(0,0,320,180);window.frame=requestAnimationFrame(paint)}paint();
  window.audio=new AudioContext();await audio.resume();
  const oscillator=audio.createOscillator(),gain=audio.createGain(),destination=audio.createMediaStreamDestination();
  // Zero-gain synthetic audio track: verifies track/state preservation without making noise.
  gain.gain.value=0;oscillator.connect(gain).connect(destination);oscillator.start();
  window.stream=new MediaStream([...canvas.captureStream(20).getTracks(),...destination.stream.getAudioTracks()]);
  v.srcObject=stream;window.weiktubeSetPlayback(true,true);
};
</script></body></html>`;
(async()=>{
  const browser=await chromium.launch({headless:true, executablePath:process.env.WEIKTUBE_CHROME_PATH});
  try {
    const context=await browser.newContext({viewport:{width:384,height:800}});
    await context.route('**/*',route=>route.request().isNavigationRequest()?route.fulfill({contentType:'text/html',body:html}):route.abort());
    const page=await context.newPage();
    for (const selection of ['first','second','first-again']) {
      await page.goto('https://m.youtube.com/watch?v=offline-'+selection);
      await page.evaluate(guard);await page.evaluate(presentation);
      await page.locator('#start').click();
      await page.waitForFunction(()=>{const v=document.querySelector('video');return !v.paused&&v.currentTime>0.1&&v.videoWidth>0;});
      await page.evaluate(()=>window.originalVideo=document.querySelector('video'));
      const state=()=>page.evaluate(()=>{const v=document.querySelector('video');return {same:v===originalVideo,
        paused:v.paused,muted:v.muted,volume:v.volume,time:v.currentTime,starts,pauses,
        audio:v.srcObject.getAudioTracks().map(t=>({enabled:t.enabled,ready:t.readyState})),context:audio.state};});
      let previous=await state();assert.equal(previous.muted,false);assert.equal(previous.volume,1);
      for(let cycle=0;cycle<10;cycle++) {
        for(const [mini,pip,w,h] of [[true,false,196,110],[false,true,240,135],[false,false,384,800]]) {
          await page.setViewportSize({width:w,height:h});
          await page.evaluate(({mini,pip})=>{weiktubeWatchPresentation(mini,true,pip,pip);weiktubeSetPlayback(true,false);},{mini,pip});
          const next=await state();assert.equal(next.same,true);assert.equal(next.paused,false);
          assert.equal(next.muted,false);assert.equal(next.volume,1);assert.equal(next.context,'running');
          assert.deepEqual(next.audio,[{enabled:true,ready:'live'}]);assert.equal(next.starts,previous.starts);assert.equal(next.pauses,previous.pauses);
          assert(next.time>=previous.time);previous=next;
        }
      }
      await page.evaluate(()=>{originalVideo.muted=true;originalVideo.volume=0.35;originalVideo.pause();});
      for(let cycle=0;cycle<10;cycle++) {
        await page.evaluate(()=>{weiktubeWatchPresentation(true,true,true,true);weiktubeSetPlayback(true,false);weiktubeWatchPresentation(false,true,false,false);});
        const next=await state();assert.equal(next.paused,true);assert.equal(next.muted,true);assert.equal(next.volume,0.35);
      }
      await page.evaluate(()=>weiktubeSetPlayback(true,true));
      await page.waitForFunction(()=>!originalVideo.paused);
      assert.equal((await state()).muted,true,'Explicit Play must not undo intentional mute');
      await page.evaluate(()=>{cancelAnimationFrame(frame);stream.getTracks().forEach(t=>t.stop());audio.close();});
    }
    console.log('PASS 3 media documents, each 10 playing mini/PiP-presentation/normal cycles + 10 paused/muted cycles. Same media and live synthetic audio track; no reload or extra Play/Pause. Not Android PiP, YouTube, or audible-speaker verification.');
  } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
