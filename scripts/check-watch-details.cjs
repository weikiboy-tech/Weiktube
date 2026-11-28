// Regression for an expanded watch wrapper that keeps its fullscreen extent in portrait.
const fs = require('node:fs');
const assert = require('node:assert/strict');
const {chromium} = require(process.env.WEIKTUBE_PLAYWRIGHT_MODULE || 'playwright-core');
const source = fs.readFileSync('app/src/main/java/com/weiktube/app/PlayerController.kt','utf8');
const presentation = source.match(/internal val WATCH_CHROME = """([\s\S]*?)"""/)[1];
(async () => {
  const browser = await chromium.launch({headless:true,executablePath:process.env.WEIKTUBE_CHROME_PATH});
  try {
    const page = await browser.newPage({viewport:{width:400,height:800}});
    await page.route('**/*', route => route.fulfill({contentType:'text/html',body:
      '<style>*{box-sizing:border-box}body{margin:0}#player-container-id{position:relative;height:100vh}#player{position:relative;top:52px;height:225px}.html5-video-container{position:relative}video{position:absolute}</style>'+
      '<div id="player-container-id"><div id="player"><div class="html5-video-container"><video></video></div></div></div><h1>Originaltitel</h1>'}));
    await page.goto('https://m.youtube.com/watch?v=abcdefghijk');
    const top = selector => page.locator(selector).evaluate(el => el.getBoundingClientRect().top);
    assert((await top('h1'))>=800);
    if (process.env.WEIKTUBE_BASELINE_PLAYER) {
      const previous = fs.readFileSync(process.env.WEIKTUBE_BASELINE_PLAYER,'utf8').match(/internal val WATCH_CHROME = """([\s\S]*?)"""/)[1];
      await page.evaluate(previous);
      assert((await top('h1'))>=800);
      assert.equal(await top('video'),52);
      console.log('1.3.27 fixture: video y=' + await top('video') + ', title y=' + await top('h1'));
      await page.reload();
    }
    await page.evaluate(presentation);
    console.log('Current fixture: video y=' + await top('video') + ', title y=' + await top('h1'));
    assert.equal(await top('video'),0);
    assert((await top('h1'))>=225 && (await top('h1'))<275);
    await page.locator('h1').evaluate(el => el.remove());
    const pollFor = target => source.split('val observationRevision = playbackObservationRevision')[1]
      .match(/view\.evaluateJavascript\("""([\s\S]*?)"""/)[1].replace('$pictureReady','true')
      .replace('${target ?: "null"}',String(target)).replaceAll('$requestGeneration','1');
    await page.evaluate(() => {
      window.ytInitialPlayerResponse={videoDetails:{videoId:'wrong-video',title:'old',author:'old',shortDescription:'old'}};
    });
    const poll = pollFor(null);
    let snapshot=JSON.parse(await page.evaluate(poll));
    assert.equal(snapshot.mediaTitle,'');
    await page.evaluate(() => {window.ytInitialPlayerResponse.videoDetails.videoId='abcdefghijk';});
    snapshot=JSON.parse(await page.evaluate(poll));
    assert.equal(snapshot.mediaTitle,'old');
    await page.evaluate(() => document.body.insertAdjacentHTML('beforeend','<div role="dialog">Anmelden</div>'));
    await page.locator('[role=dialog]').evaluate(el => el.remove());
    await page.evaluate(() => weiktubeWatchPresentation(true,true,true,false));
    await page.evaluate(() => weiktubeWatchPresentation(false,true,false,false));
    await page.evaluate(() => document.body.insertAdjacentHTML('beforeend','<ytm-slim-video-information-renderer></ytm-slim-video-information-renderer>'));
    const details = page.locator('ytm-slim-video-information-renderer');
    assert.equal(await details.evaluate(el => getComputedStyle(el,'::before').display),'block');
    await details.evaluate(el => { el.innerHTML='<h1>Originaltitel</h1>'; });
    assert.equal(await details.evaluate(el => getComputedStyle(el,'::before').content),'none');
    assert((await top('h1'))<275);
    assert.equal(await page.locator('h1').count(),1);
    // A dedicated outer player shell can retain fullscreen minimum height even after
    // its inner video is correctly sized. Reproduce both with the released stylesheet.
    await page.evaluate(() => {
      const slot=document.querySelector('#player-container-id'), details=document.querySelector('ytm-slim-video-information-renderer');
      const watch=document.createElement('ytm-watch'), shell=document.createElement('div');
      watch.style.display='block';shell.className='player-container';
      shell.style.cssText='height:100vh;min-height:100vh';slot.style.minHeight='100vh';
      document.body.append(watch); watch.append(shell,details); shell.append(slot);
    });
    const sheet=await page.locator('#weiktube-watch-style').textContent();
    if (process.env.WEIKTUBE_PREVIOUS_WATCH) {
      const previous=fs.readFileSync(process.env.WEIKTUBE_PREVIOUS_WATCH,'utf8').match(/internal val WATCH_CHROME = """([\s\S]*?)"""/)[1];
      await page.evaluate(() => {
        document.querySelector('#weiktube-watch-style').remove();
        delete window.weiktubeWatchPresentation;
      });
      await page.evaluate(previous);
      assert((await top('h1'))>=800);
      console.log('1.3.31 nested fullscreen minimum: title y='+await top('h1'));
      await page.locator('#weiktube-watch-style').evaluate((el,css)=>{el.textContent=css;},sheet);
    }
    assert((await top('h1'))<275);
    console.log('Corrected nested fullscreen minimum: title y='+await top('h1'));
    await details.evaluate(el=>el.remove());
    const placeholder=()=>page.locator('ytm-watch').evaluate(el=>getComputedStyle(el,'::after').content);
    assert.equal(await placeholder(),'""');
    await page.evaluate(()=>weiktubeWatchPresentation(true,true,true,false));
    assert.equal(await placeholder(),'none');
    await page.evaluate(()=>weiktubeWatchPresentation(false,true,false,false));
    await page.locator('ytm-watch').evaluate(el=>el.insertAdjacentHTML('beforeend','<h1>Originaltitel</h1>'));
    assert.equal(await placeholder(),'none');
    // Drive the production poll with controlled media events, including an old frame after seeking.
    await page.evaluate(() => {
      const v=document.querySelector('video');
      for(const [key,value] of Object.entries({currentTime:135, readyState:4, seeking:false})) {
        Object.defineProperty(v,key,{value,configurable:true});
      }
      v.requestVideoFrameCallback=cb=>{window.frameCallback=cb;};
    });
    const sampleResume=async () => JSON.parse(await page.evaluate(pollFor(135))).resumeFrameTime;
    assert.equal(await sampleResume(),null);
    await page.evaluate(() => window.frameCallback(0,{mediaTime:0}));
    assert.equal(await sampleResume(),null);
    await page.evaluate(() => window.frameCallback(0,{mediaTime:135.2}));
    assert.equal(await sampleResume(),135.2);
    await page.evaluate(() => {
      window.sampleClock=100;
      Object.defineProperty(performance,'now',{value:()=>window.sampleClock});
      Object.defineProperty(document.querySelector('video'),'paused',{value:false,configurable:true});
    });
    const stalled=async()=>JSON.parse(await page.evaluate(poll)).stalled;
    assert.equal(await stalled(),false);
    for(let i=1;i<=8;i++){
      await page.evaluate(()=>{window.sampleClock+=1000;});
      assert.equal(await stalled(),i===8);
    }
    await page.locator('video').evaluate(v=>Object.defineProperty(v,'currentTime',{value:136,configurable:true}));
    assert.equal(await stalled(),false);
    await page.locator('video').evaluate(v=>Object.defineProperty(v,'paused',{value:true}));
    await page.evaluate(()=>{window.sampleClock+=10000;});
    assert.equal(await stalled(),false);
    const restart=source.split('fun restartFromBeginning()')[1].match(/evaluateJavascript\("""([\s\S]*?)"""/)[1]
      .replace('${JSONObject.quote(id)}','"abcdefghijk"');
    await page.evaluate(() => {
      const v=document.querySelector('video');
      Object.defineProperty(v,'currentTime',{value:135,writable:true});
      Object.defineProperty(v,'seekable',{value:{length:1,start:()=>0,end:()=>300}});
    });
    assert.equal(await page.evaluate(restart),true);
    assert.equal(await page.locator('video').evaluate(v=>v.currentTime),0);
    assert.equal(await page.evaluate(restart.replace('abcdefghijk','another-id')),false);
    console.log('PASS portrait wrapper, first media offset, visible details, delayed details, dialog and mini/restore (offline Chromium)');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode=1; });
