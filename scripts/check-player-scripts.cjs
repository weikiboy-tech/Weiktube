// Deterministic VM fixtures for the scripts actually embedded by PlayerController.
// These exercise playback/lifecycle decisions and document preservation, not YouTube/network availability.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/weiktube/app/PlayerController.kt'), 'utf8');
function template(name) {
  const match = source.match(new RegExp('internal val ' + name + ' = """([\\s\\S]*?)"""'));
  assert.ok(match, `Missing ${name} template`);
  new vm.Script(match[1]);
  return match[1];
}
const guard = template('MEDIA_GUARD');
const chrome = template('WATCH_CHROME');
let passed = 0;
function test(name, body) {
  body(); passed++;
  console.log(`PASS ${name}`);
}
function mediaFixture({ initialVideo = true, ready = 0 } = {}) {
  const listeners = new Map();
  const dispatch = (type, target) => (listeners.get(type) || []).forEach(fn => fn({ target }));
  function media(tagName) {
    return { tagName, readyState: ready, currentTime: 73, paused: true, playCalls: 0, pauseCalls: 0,
      play() { this.playCalls++; this.paused = false; dispatch('play', this); return Promise.resolve(); },
      pause() { this.pauseCalls++; this.paused = true; }
    };
  }
  const video = media('VIDEO');
  const audio = media('AUDIO');
  let present = initialVideo;
  const context = { document: {
    addEventListener(type, fn) { listeners.set(type, [...(listeners.get(type) || []), fn]); },
    querySelector(selector) { assert.equal(selector, 'video'); return present ? video : null; },
    querySelectorAll(selector) { assert.equal(selector, 'video,audio'); return present ? [video, audio] : [audio]; }
  } };
  context.window = context;
  vm.createContext(context);
  const install = () => vm.runInContext(guard, context);
  install();
  return { context, video, audio, dispatch, listeners, install, insert() { present = true; } };
}

test('Denied/background media is paused, including late play events', () => {
  const f = mediaFixture();
  f.video.play(); f.audio.play();
  assert.equal(f.video.paused, true); assert.equal(f.audio.paused, true);
  f.context.weiktubeSetPlayback(true, false);
  f.video.play(); assert.equal(f.video.paused, false);
  f.context.weiktubeSetPlayback(false, false);
  assert.equal(f.video.paused, true);
  const pauseCount = f.video.pauseCalls;
  f.video.play();
  assert.equal(f.video.paused, true);
  assert.equal(f.video.pauseCalls, pauseCount + 1);
});

test('Explicit start waits for late video metadata and starts exactly once', () => {
  const f = mediaFixture({ initialVideo: false });
  f.context.weiktubeSetPlayback(true, true);
  assert.equal(f.video.playCalls, 0);
  f.insert(); f.video.readyState = 1; f.dispatch('loadedmetadata', f.video);
  assert.equal(f.video.playCalls, 1); assert.equal(f.video.paused, false);
  f.dispatch('loadedmetadata', f.video);
  assert.equal(f.video.playCalls, 1);
});

test('Already loaded video starts on explicit request, not on ordinary allowance', () => {
  const f = mediaFixture({ ready: 2 });
  f.context.weiktubeSetPlayback(true, false);
  assert.equal(f.video.playCalls, 0);
  f.context.weiktubeSetPlayback(true, true);
  assert.equal(f.video.playCalls, 1);
});

test('Repeated native Play requests never toggle or replace the retained video', () => {
  const f = mediaFixture({ ready: 2 });
  const node = f.video;
  node.currentTime = 87;
  for (let attempt = 0; attempt < 5; attempt++) {
    f.context.weiktubeSetPlayback(true, true);
    assert.equal(node.paused, false);
    assert.equal(node.currentTime, 87);
    assert.equal(f.context.document.querySelector('video'), node);
  }
  f.context.weiktubeSetPlayback(false, false);
  assert.equal(node.paused, true, 'native Pause still revokes permission');
  node.play();
  assert.equal(node.paused, true, 'late media events cannot bypass a closed/background player');
});

test('Ten explicit reselections resume the retained video without replacing or rewinding it', () => {
  const f = mediaFixture({ ready: 2 });
  const node = f.context.document.querySelector('video');
  for (let cycle = 1; cycle <= 10; cycle++) {
    const seconds = 73 + cycle;
    node.currentTime = seconds;
    f.context.weiktubeSetPlayback(false, false); // Returning to Home pauses the current video.
    assert.equal(node.paused, true);
    f.context.weiktubeSetPlayback(true, false); // Visibility/permission restoration is not a play request.
    assert.equal(node.paused, true);
    assert.equal(node.playCalls, cycle - 1);
    f.context.weiktubeSetPlayback(true, true); // Explicitly selecting the same source requests playback.
    assert.equal(node.paused, false);
    assert.equal(node.playCalls, cycle);
    assert.equal(f.context.document.querySelector('video'), node);
    assert.equal(node.currentTime, seconds);
    f.context.weiktubeSetPlayback(true, false);
    assert.equal(node.playCalls, cycle, 'A later permission update must not restart playback');
  }
});

test('Permission refresh preserves an explicit start until late metadata, without duplicate starts', () => {
  const f = mediaFixture({ initialVideo: false });
  f.context.weiktubeSetPlayback(true, true);
  f.context.weiktubeSetPlayback(true, false); // Compose can refresh permission before metadata arrives.
  f.install();
  f.context.weiktubeSetPlayback(true, false);
  assert.equal(f.video.playCalls, 0);
  f.insert(); f.video.readyState = 1; f.dispatch('loadedmetadata', f.video);
  assert.equal(f.video.paused, false);
  assert.equal(f.video.playCalls, 1);
  f.dispatch('loadedmetadata', f.video);
  f.context.weiktubeSetPlayback(true, false);
  assert.equal(f.video.playCalls, 1);
});

test('Pause cancels a pending explicit start before video metadata arrives', () => {
  const f = mediaFixture({ initialVideo: false });
  f.context.weiktubeSetPlayback(true, true);
  f.context.weiktubeSetPlayback(false, false);
  f.insert(); f.video.readyState = 1; f.dispatch('loadedmetadata', f.video);
  assert.equal(f.video.playCalls, 0);
  f.video.play(); assert.equal(f.video.paused, true);
});

test('Restoring permission without requestStart never autoplays', () => {
  const f = mediaFixture();
  f.context.weiktubeSetPlayback(true, true); // A request canceled by pause must not return with permission.
  f.context.weiktubeSetPlayback(false, false);
  f.context.weiktubeSetPlayback(true, false);
  f.video.readyState = 1; f.dispatch('loadedmetadata', f.video);
  assert.equal(f.video.playCalls, 0);
  f.video.play(); assert.equal(f.video.paused, false, 'A later user/page play is permitted once allowed');
});

test('Repeated guard injection preserves state and installs listeners once', () => {
  const f = mediaFixture();
  f.context.weiktubeSetPlayback(true, true);
  const setter = f.context.weiktubeSetPlayback;
  f.install(); f.install();
  assert.equal(f.context.weiktubeSetPlayback, setter);
  assert.equal(f.listeners.get('play').length, 1);
  assert.equal(f.listeners.get('loadedmetadata').length, 1);
  f.video.readyState = 1; f.dispatch('loadedmetadata', f.video);
  assert.equal(f.video.playCalls, 1, 'Pending start survived reinjection');
});

function watchFixture({ hostname = 'm.youtube.com', pathname = '/watch', embedded = false } = {}) {
  const attrs = {}, styles = [], scrollCalls = [], cssProperties = {}, events = {};
  const video = { tagName: 'VIDEO', currentTime: 73, paused: false, identity: Symbol('original watch video') };
  const root = { clientWidth: 363, style: { setProperty(key, value) { cssProperties[key] = value; } }, setAttribute(key, value) { attrs[key] = value; }, appendChild(node) { styles.push(node); } };
  const context = { scrollY: 345, innerWidth: 363, innerHeight: 669, location: { hostname, pathname },
    addEventListener(type, listener) { events[type] = listener; },
    scrollTo(x, y) { scrollCalls.push([x, y]); this.scrollY = y; },
    document: { documentElement: root, head: root,
      querySelector(selector) { return selector === 'video' ? video : null; },
      getElementById(id) { return styles.find(node => node.id === id) || null; },
      createElement(tag) { assert.equal(tag, 'style', 'Chrome must not recreate any player/document nodes'); return {}; }
    }
  };
  context.window = context; context.top = embedded ? {} : context;
  vm.createContext(context);
  const install = () => vm.runInContext(chrome, context);
  return { context, video, attrs, styles, scrollCalls, cssProperties, events, install };
}

test('Mini/expanded transitions preserve video node, position, playback and expanded scroll', () => {
  const f = watchFixture(); f.install();
  const node = f.context.document.querySelector('video');
  const presentation = f.context.weiktubeWatchPresentation;
  presentation(true, true);
  assert.equal(f.context.scrollY, 0); assert.equal(f.attrs['data-weiktube-mini'], '1');
  f.context.scrollY = 12;
  presentation(true, false); // Recomposition while already minimized must not overwrite saved scroll.
  f.install(); // Reinjecting fallback script must also preserve the saved expanded scroll.
  assert.equal(f.context.weiktubeWatchPresentation, presentation);
  presentation(false, false);
  assert.equal(f.context.scrollY, 345);
  assert.equal(f.attrs['data-weiktube-mini'], '0');
  assert.equal(f.context.document.documentElement.style.colorScheme, 'light');
  assert.equal(f.context.document.querySelector('video'), node);
  assert.equal(node, f.video); assert.equal(node.currentTime, 73); assert.equal(node.paused, false);
  assert.equal(f.styles.length, 1);
  const css = f.styles[0].textContent;
  assert.ok(!/(?:^|})\s*video\s*\{|iframe\s*\{|filter\s*:|ad-showing|advert/i.test(css), 'No global media/ad replacement, inversion or hiding rules');
  assert.match(css, /html\[data-weiktube-video-only="1"\] #player-container-id video\{[^}]*object-fit:contain!important/,
    'Video sizing is confined to the existing player in landscape-only presentation');
});

test('Chrome leaves embedded frames, non-YouTube pages and embed endpoints untouched', () => {
  for (const options of [{ embedded: true }, { hostname: 'example.org' }, { pathname: '/embed/abcdefghijk' }]) {
    const f = watchFixture(options); f.install();
    assert.equal(f.context.weiktubeWatchPresentation, undefined, JSON.stringify(options));
    assert.equal(f.styles.length, 0);
    assert.equal(f.scrollCalls.length, 0);
    assert.equal(f.video.currentTime, 73);
  }
});
test('Watch geometry uses explicit viewport pixels for full, mini and landscape sizes', () => {
  const f = watchFixture(); f.install();
  assert.equal(f.cssProperties['--weiktube-watch-height'], '204.1875px');
  f.context.innerHeight = 200;
  f.context.weiktubeWatchPresentation(true, true);
  assert.equal(f.cssProperties['--weiktube-watch-height'], '200px');
  f.context.innerWidth = 196; f.context.innerHeight = 110.25;
  f.events.resize();
  assert.equal(f.cssProperties['--weiktube-watch-height'], '110.25px', 'Compact card keeps 16:9 video geometry');
  f.context.weiktubeWatchPresentation(false, true);
  assert.equal(f.attrs['data-weiktube-mini'], '0', 'Expanded player restores its own website controls');
  f.context.innerWidth = 900; f.context.innerHeight = 300;
  f.events.resize();
  assert.equal(f.cssProperties['--weiktube-watch-height'], '300px');
  assert.equal(f.context.document.querySelector('video'), f.video);
  assert.equal(f.video.currentTime, 73);
  assert.match(f.styles[0].textContent, /height:var\(--weiktube-watch-height\)!important/);
  assert.match(f.styles[0].textContent, /padding-bottom:0!important/);
});

test('Ten portrait/landscape transitions preserve video identity, time, pause state and detail scroll', () => {
  const f = watchFixture(); f.install();
  const node = f.context.document.querySelector('video');
  const presentation = f.context.weiktubeWatchPresentation;
  for (let cycle = 0; cycle < 10; cycle++) {
    const scroll = 345 + cycle * 17;
    const seconds = 73 + cycle;
    const paused = cycle % 2 === 0;
    f.context.scrollY = scroll; node.currentTime = seconds; node.paused = paused;
    f.context.innerWidth = 600; f.context.innerHeight = 400;
    presentation(false, true, true);
    assert.equal(f.attrs['data-weiktube-video-only'], '1');
    assert.equal(f.attrs['data-weiktube-mini'], '0', 'Landscape retains website controls');
    assert.equal(f.cssProperties['--weiktube-watch-height'], '400px', 'Uses full height, not the 337.5px 16:9 portrait clamp');
    assert.equal(f.context.scrollY, 0);
    f.context.scrollY = 8; presentation(false, false, true); f.install();
    f.context.innerHeight = 420; f.events.resize();
    assert.equal(f.cssProperties['--weiktube-watch-height'], '420px');
    assert.equal(f.context.weiktubeWatchPresentation, presentation);
    f.context.innerWidth = 363; f.context.innerHeight = 669;
    presentation(false, true, false);
    assert.equal(f.attrs['data-weiktube-video-only'], '0');
    assert.equal(f.attrs['data-weiktube-mini'], '0');
    assert.equal(f.context.scrollY, scroll);
    assert.equal(f.cssProperties['--weiktube-watch-height'], '204.1875px');
    assert.equal(f.context.document.querySelector('video'), node);
    assert.equal(node.currentTime, seconds); assert.equal(node.paused, paused);
  }
  assert.equal(f.styles.length, 1);
  const css = f.styles[0].textContent;
  const videoOnlyRules = css.split('}').filter(rule => rule.includes('data-weiktube-video-only'));
  assert.ok(videoOnlyRules.length > 0);
  for (const rule of videoOnlyRules) assert.doesNotMatch(rule, /display\s*:\s*none|visibility\s*:\s*hidden/,
    'Landscape sizing must not hide website playback controls');
});

test('Mini to landscape to portrait restores the original detail scroll without hiding landscape controls', () => {
  const f = watchFixture(); f.install();
  const presentation = f.context.weiktubeWatchPresentation;
  presentation(true, true, false);
  assert.equal(f.context.scrollY, 0);
  assert.equal(f.attrs['data-weiktube-mini'], '1');
  presentation(false, true, true);
  assert.equal(f.attrs['data-weiktube-mini'], '0');
  assert.equal(f.attrs['data-weiktube-video-only'], '1');
  f.context.scrollY = 9;
  presentation(true, true, false);
  assert.equal(f.attrs['data-weiktube-mini'], '1');
  assert.equal(f.attrs['data-weiktube-video-only'], '0');
  presentation(false, true, true);
  presentation(false, true, false);
  assert.equal(f.context.scrollY, 345, 'Switches between compact and landscape modes must not overwrite the saved portrait position');
  assert.equal(f.video.currentTime, 73); assert.equal(f.video.paused, false);
  assert.equal(f.context.document.querySelector('video'), f.video);
});

test('Ten Android PiP round trips retain video/time/play state and restore portrait scroll', () => {
  const f = watchFixture(); f.install();
  const presentation = f.context.weiktubeWatchPresentation;
  const node = f.video;
  for (let cycle = 0; cycle < 10; cycle++) {
    const scroll = 250 + cycle * 21;
    const seconds = 140 + cycle;
    const paused = cycle % 2 === 0;
    f.context.scrollY = scroll; node.currentTime = seconds; node.paused = paused;
    f.context.innerWidth = 300; f.context.innerHeight = 200;
    presentation(false, true, true, true);
    assert.equal(f.attrs['data-weiktube-system-pip'], '1');
    assert.equal(f.attrs['data-weiktube-video-only'], '1');
    assert.equal(f.attrs['data-weiktube-mini'], '0');
    assert.equal(f.cssProperties['--weiktube-watch-height'], '200px');
    assert.equal(f.context.scrollY, 0);
    f.context.scrollY = 6; presentation(false, false, true, true); f.install();
    f.context.innerHeight = 220; f.events.resize();
    assert.equal(f.cssProperties['--weiktube-watch-height'], '220px');
    f.context.innerWidth = 363; f.context.innerHeight = 669;
    presentation(false, true, false, false);
    assert.equal(f.attrs['data-weiktube-system-pip'], '0');
    assert.equal(f.attrs['data-weiktube-video-only'], '0');
    assert.equal(f.context.scrollY, scroll);
    assert.equal(f.cssProperties['--weiktube-watch-height'], '204.1875px');
    assert.equal(f.context.weiktubeWatchPresentation, presentation);
    assert.equal(f.context.document.querySelector('video'), node);
    assert.equal(node.currentTime, seconds); assert.equal(node.paused, paused);
  }
  assert.equal(f.styles.length, 1);
  const css = f.styles[0].textContent;
  const pipRules = css.split('}').filter(rule => rule.includes('data-weiktube-system-pip'));
  assert.equal(pipRules.length, 1);
  assert.match(pipRules[0], /ytm-player-controls/);
  assert.match(pipRules[0], /display:none!important/);
  assert.doesNotMatch(pipRules[0], /caption-window|\bvideo\b/, 'PiP must not hide captions or the media element');
  for (const rule of css.split('}').filter(rule => rule.includes('data-weiktube-video-only')))
    assert.doesNotMatch(rule, /display\s*:\s*none|visibility\s*:\s*hidden/, 'landscape controls remain unaffected');
});

test('PiP from mini or landscape preserves isolated-scroll ownership and restores either mode', () => {
  for (const miniBefore of [true, false]) {
    const f = watchFixture(); f.install();
    const presentation = f.context.weiktubeWatchPresentation;
    presentation(miniBefore, true, !miniBefore, false);
    f.context.scrollY = 8;
    presentation(miniBefore, true, false, true); // PiP itself must override stale compact flags.
    assert.equal(f.attrs['data-weiktube-mini'], '0');
    assert.equal(f.attrs['data-weiktube-video-only'], '1');
    assert.equal(f.attrs['data-weiktube-system-pip'], '1');
    presentation(miniBefore, true, !miniBefore, false);
    assert.equal(f.attrs['data-weiktube-mini'], miniBefore ? '1' : '0');
    assert.equal(f.attrs['data-weiktube-video-only'], miniBefore ? '0' : '1');
    assert.equal(f.attrs['data-weiktube-system-pip'], '0');
    presentation(false, true, false); // Existing three-argument calls still restore portrait.
    assert.equal(f.context.scrollY, 345);
    assert.equal(f.context.document.querySelector('video'), f.video);
    assert.equal(f.video.currentTime, 73); assert.equal(f.video.paused, false);
  }
});
console.log(`PASS ${passed} player script fixtures. Real Android/WebView playback remains a separate device test.`);
