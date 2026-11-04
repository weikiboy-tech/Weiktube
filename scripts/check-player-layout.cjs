// Real Chromium CSS/media fixture; no YouTube network and no Android/device-test claim.
// Replays the inline top:53px (mini) / top:292px (restore) offsets measured on the
// public mobile watch page, plus its off-canvas 360px drawer overflowing narrow
// viewports. Executes WATCH_CHROME directly from production Kotlin. Pixel probes
// ensure correct video bounds cannot hide an opaque element painted on top.
// Dependencies are supplied by CI/local tooling, never downloaded by this script:
//   WEIKTUBE_PLAYWRIGHT_MODULE=/absolute/path/to/playwright-core
//   WEIKTUBE_CHROME_PATH=/absolute/path/to/chrome
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {chromium} = require(process.env.WEIKTUBE_PLAYWRIGHT_MODULE || 'playwright-core');
const source = fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/weiktube/app/PlayerController.kt'), 'utf8');
const script = source.match(/internal val WATCH_CHROME = """([\s\S]*?)"""/)[1];
const html = `<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;padding:0}#player-container-id{position:fixed;top:0;left:0;width:100%;background:#000}
#player{position:relative;width:100%;height:var(--fixture-height,216px);overflow:hidden}
.html5-video-player{position:absolute;inset:0;overflow:hidden}
.html5-video-container{position:relative;width:100%;height:0}
video{position:absolute;left:0;top:0;width:384px;height:216px;object-fit:cover}
.ytmDrawerLayoutContainerDrawerLayout{position:fixed;left:-100vw;top:0;width:360px;height:100vh;background:#fff;z-index:2}
.details{height:2200px;padding-top:240px}</style>
<div id="player-container-id" class="player-container"><div id="player" class="player-size">
<div class="html5-video-player"><div class="html5-video-container"><video muted playsinline></video></div></div>
</div></div><main class="details">Synthetic watch details for scroll restoration</main>
<drawer-layout-container><div class="ytmDrawerLayoutContainerDrawerLayout"></div></drawer-layout-container>`;

(async () => {
  const browser = await chromium.launch({headless: true,
    ...(process.env.WEIKTUBE_CHROME_PATH ? {executablePath: process.env.WEIKTUBE_CHROME_PATH} : {})});
  try {
    const context = await browser.newContext({viewport: {width: 384, height: 800}, deviceScaleFactor: 1});
    // Route every request locally. The YouTube hostname only exercises production's origin guard.
    await context.route('**/*', route => route.fulfill({contentType: 'text/html', body: html}));
    const page = await context.newPage();
    await page.goto('https://m.youtube.com/watch?v=fixture0000');
    const bounds = () => page.evaluate(() => {
      const v = document.querySelector('video'), parent = v.parentElement;
      const box = el => {const r = el.getBoundingClientRect(); return {x:r.x,y:r.y,width:r.width,height:r.height};};
      const r = v.getBoundingClientRect();
      let left = Math.max(r.left, 0), top = Math.max(r.top, 0), right = Math.min(r.right, innerWidth), bottom = Math.min(r.bottom, innerHeight);
      for (let el = v.parentElement; el; el = el.parentElement) {
        const style = getComputedStyle(el), clip = el.getBoundingClientRect();
        if (/(hidden|clip|scroll|auto)/.test(style.overflowX)) {left = Math.max(left, clip.left); right = Math.min(right, clip.right);}
        if (/(hidden|clip|scroll|auto)/.test(style.overflowY)) {top = Math.max(top, clip.top); bottom = Math.min(bottom, clip.bottom);}
      }
      const drawer=document.querySelector('.ytmDrawerLayoutContainerDrawerLayout');
      return {video:box(v), parent:box(parent), visibleWidth:Math.max(0,right-left), visibleHeight:Math.max(0,bottom-top),
        drawer:box(drawer),drawerDisplay:getComputedStyle(drawer).display,
        drawerCoversLeft:document.elementFromPoint(10,50)===drawer,
        inlineTop:v.style.top, computedTop:getComputedStyle(v).top, paused:v.paused, time:v.currentTime, scroll:scrollY,
        sameNode:v === window.fixtureVideo, sameSource:v.srcObject === window.fixtureStream};
    });

    // Negative controls verify that this actual CSS engine catches the observed clipping.
    await page.setViewportSize({width:196,height:110});
    await page.evaluate(() => {
      document.documentElement.style.setProperty('--fixture-height','110px');
      Object.assign(document.querySelector('video').style, {width:'196px',height:'110px',top:'53px'});
    });
    const oldMini = await bounds();
    assert.equal(oldMini.video.y, 53); assert.equal(oldMini.visibleHeight, 57);
    assert.equal(oldMini.drawerCoversLeft,true,'old mini: drawer paints on top of video');
    assert.equal(oldMini.drawer.x+oldMini.drawer.width,164,'old mini: exact 164px opaque overhang');
    await page.setViewportSize({width:320,height:180});
    const oldPip = await bounds();
    assert.equal(oldPip.drawerCoversLeft,true,'old PiP: drawer paints on top of video');
    assert.equal(oldPip.drawer.x+oldPip.drawer.width,40,'old PiP: exact 40px opaque overhang');
    await page.setViewportSize({width:384,height:800});
    await page.evaluate(() => {
      document.documentElement.style.setProperty('--fixture-height','216px');
      Object.assign(document.querySelector('video').style, {width:'384px',height:'216px',top:'292px'});
    });
    const oldRestore = await bounds();
    assert.equal(oldRestore.video.y, 292); assert.equal(oldRestore.visibleHeight, 0);

    await page.evaluate(script);
    await page.evaluate(async () => {
      const video = document.querySelector('video'), canvas = document.createElement('canvas');
      canvas.width = 320; canvas.height = 180;
      const ctx = canvas.getContext('2d'); let frames = 0;
      function draw() {
        ctx.fillStyle = '#e32636'; ctx.fillRect(0,0,160,180);
        ctx.fillStyle = '#1677ff'; ctx.fillRect(160,0,160,180);
        ctx.fillStyle = '#fff'; ctx.fillRect((frames++ % 300),20,20,20);
        window.fixtureFrame = requestAnimationFrame(draw);
      }
      draw();
      const stream = canvas.captureStream(15);
      window.fixtureVideo = video; window.fixtureStream = stream;
      video.muted = true; video.srcObject = stream;
      await video.play(); // Exactly one initial fixture play; no recovery/resume loop.
    });
    await page.waitForFunction(() => document.querySelector('video').readyState >= 2);
    let previousTime = 0;
    const geometry = (state, width, height, name) => {
      for (const [actual, expected] of [[state.video.x,0],[state.video.y,0],[state.video.width,width],[state.video.height,height],
        [state.parent.width,width],[state.parent.height,height],[state.visibleWidth,width],[state.visibleHeight,height]])
        assert(Math.abs(actual-expected) < 1, `${name}: expected ${expected}, got ${actual}`);
      assert.equal(state.sameNode, true, `${name}: original video node`);
      assert.equal(state.sameSource, true, `${name}: original stream`);
    };
    const paintedVideo = async (width, height, name) => {
      // Decode the actual compositor screenshot through Chromium's PNG decoder.
      // Sampling the source canvas would miss the drawer/other painted overlays.
      const png=await page.screenshot();
      const samples=await page.evaluate(async ({png,width,height})=>{
        const image=new Image();image.src='data:image/png;base64,'+png;await image.decode();
        const canvas=document.createElement('canvas');canvas.width=image.width;canvas.height=image.height;
        const ctx=canvas.getContext('2d');ctx.drawImage(image,0,0);
        return [10,width-10].map(x=>Array.from(ctx.getImageData(x,Math.floor(height/2),1,1).data));
      },{png:png.toString('base64'),width,height});
      const [left,right]=samples;
      assert(left[0]>150 && left[1]<100 && left[2]<130,`${name}: left painted video red, got ${left}`);
      assert(right[2]>150 && right[0]<100,`${name}: right painted video blue, got ${right}`);
    };
    for (let cycle = 1; cycle <= 10; cycle++) {
      // Test an intentionally paused player as well, without subsequent automatic play calls.
      if (cycle === 6) previousTime = await page.evaluate(() => {
        window.fixtureVideo.pause();
        return window.fixtureVideo.currentTime;
      });
      const paused = cycle >= 6;
      const scroll = 200 + cycle * 13;
      await page.evaluate(scroll => window.scrollTo(0,scroll), scroll);
      for (const [name,width,height,flags,top] of [
        ['mini',196,110,[true,true,false,false],53],
        ['pip',320,180,[false,true,true,true],0],
        ['restore',384,800,[false,true,false,false],292]
      ]) {
        await page.setViewportSize({width,height});
        await page.evaluate(({flags,top,width}) => {
          Object.assign(window.fixtureVideo.style, {width:width+'px',height:(width*9/16)+'px',top:top+'px'});
          window.weiktubeWatchPresentation(...flags);
        }, {flags,top,width});
        await page.waitForTimeout(60);
        const state = await bounds();
        geometry(state,width,name === 'restore' ? 216 : height,`cycle ${cycle} ${name}`);
        assert.equal(state.drawerCoversLeft,false,`${name}: drawer must not cover the video`);
        assert.equal(state.drawerDisplay,name === 'restore'?'block':'none',`${name}: scope drawer suppression to compact modes`);
        await paintedVideo(width,name === 'restore'?216:height,`cycle ${cycle} ${name}`);
        assert.equal(state.paused,paused,`${name}: presentation must preserve pause state`);
        assert(state.time >= previousTime-0.01,`${name}: no rewind`);
        if (paused) assert(Math.abs(state.time-previousTime)<0.15,`${name}: paused playback must stay stopped`);
        previousTime = state.time;
        if (name === 'restore') assert.equal(state.scroll,scroll,'portrait detail scroll restored');
      }
    }
    await page.setViewportSize({width:800,height:450});
    await page.evaluate(()=>window.weiktubeWatchPresentation(false,true,true,false));
    assert.equal((await bounds()).drawerDisplay,'block','landscape keeps regular YouTube drawer behavior');
    await paintedVideo(800,450,'landscape');
    await page.evaluate(() => {cancelAnimationFrame(window.fixtureFrame); window.fixtureStream.getTracks().forEach(track => track.stop());});
    console.log('PASS real Chromium player layout: negative controls reproduce 53px/292px offsets and 164px/40px opaque drawer overhang; 10 mini/PiP/restore cycles retain video/source/time/pause/scroll, full visible bounds and actual red/blue video pixels. Drawer suppression excludes portrait/landscape. Offline fixture, not Android or live YouTube playback verification.');
  } finally {await browser.close();}
})().catch(error => {console.error(error.stack); process.exitCode=1;});
