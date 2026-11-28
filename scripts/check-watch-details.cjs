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
    const poll = source.split('val observationRevision = playbackObservationRevision')[1]
      .match(/view\.evaluateJavascript\("""([\s\S]*?)"""/)[1].replace('$pictureReady','true');
    await page.evaluate(() => {
      window.ytInitialPlayerResponse={videoDetails:{videoId:'wrong-video',title:'old',author:'old',shortDescription:'old'}};
    });
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
    console.log('PASS portrait wrapper, first media offset, visible details, delayed details, dialog and mini/restore (offline Chromium)');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode=1; });
