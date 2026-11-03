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
  assert.ok(!/video\s*\{|iframe\s*\{|filter\s*:|ad-showing|advert/i.test(css), 'No media/ad replacement, inversion or hiding rules');
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
  f.context.innerWidth = 900; f.context.innerHeight = 300;
  f.events.resize();
  assert.equal(f.cssProperties['--weiktube-watch-height'], '300px');
  assert.equal(f.context.document.querySelector('video'), f.video);
  assert.equal(f.video.currentTime, 73);
  assert.match(f.styles[0].textContent, /height:var\(--weiktube-watch-height\)!important/);
  assert.match(f.styles[0].textContent, /padding-bottom:0!important/);
});
console.log(`PASS ${passed} player script fixtures. Real Android/WebView playback remains a separate device test.`);
