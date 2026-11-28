// Offline Chromium fixture for document-start injection before the root element exists.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {chromium} = require(process.env.WEIKTUBE_PLAYWRIGHT_MODULE || 'playwright-core');
const current = path.join(__dirname, '../app/src/main/java/com/weiktube/app/PlayerController.kt');
async function samples(browser, file, verify) {
  const script = fs.readFileSync(file, 'utf8').match(/internal val WATCH_CHROME = """([\s\S]*?)"""/)[1];
  const page = await browser.newPage({viewport: {width: 400, height: 800}});
  await page.route('**/*', route => route.fulfill({contentType:'text/html', body:'<!doctype html><html></html>'}));
  const times = [];
  for (let attempt = 0; attempt < 5; attempt++) {
    await page.goto('https://m.youtube.com/watch?v=localtest01');
    await page.evaluate(() => document.documentElement.remove());
    await page.evaluate(script);
    const measurement = await page.evaluate(async () => {
      const started = performance.now();
      const root = document.createElement('html');
      root.innerHTML = '<head></head><body><div id="player-container-id" class="player-container"><div class="player-size" style="height:83px"></div></div></body>';
      document.append(root);
      const slot = root.querySelector('.player-size');
      // Simulate the previous late presentation call after slow page resources finish.
      const timer = setTimeout(() => window.weiktubeWatchPresentation(false, true, false), 350);
      try {
        while (performance.now() - started < 2000) {
          await new Promise(requestAnimationFrame);
          if (Math.abs(slot.getBoundingClientRect().height - 225) < 1)
            return performance.now() - started;
        }
        throw Error('Watch slot never reached the expected 400 x 225 layout');
      } finally { clearTimeout(timer); }
    });
    times.push(Math.round(measurement));
  }
  await page.close();
  if (verify) assert(times.every(t => t < 350), 'Initial CSS must be applied before simulated page finish');
  console.log((verify ? 'Current' : 'Baseline') + ' root inserted to correctly sized watch slot (ms): ' + times.join(', '));
}
(async () => {
  const browser = await chromium.launch({headless:true, executablePath:process.env.WEIKTUBE_CHROME_PATH});
  try {
    if (process.env.WEIKTUBE_BASELINE_PLAYER) await samples(browser, process.env.WEIKTUBE_BASELINE_PLAYER, false);
    await samples(browser, current, true);
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
