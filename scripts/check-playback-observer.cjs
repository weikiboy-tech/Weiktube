// Runs the exact read-only observer script. No Android/WebView/device-test claim.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/weiktube/app/PlayerPlaybackObserver.kt'), 'utf8');
const script = source.match(/internal val SCRIPT = """([\s\S]*?)"""/)[1];
new vm.Script(script);
let passed = 0;
function test(name, run) { run(); passed++; console.log(`PASS ${name}`); }
function fixture({ origin = 'https://m.youtube.com', embedded = false, bridge = true } = {}) {
  const listeners = new Map(), windowListeners = new Map(), messages = [];
  let current = {tagName: 'VIDEO', paused: true, ended: false, readyState: 2, videoWidth: 1920, videoHeight: 1080};
  const denied = () => { throw Error('observer must not read credentials/URLs/time or control playback'); };
  for (const property of ['src', 'currentSrc', 'currentTime']) Object.defineProperty(current, property, {get: denied, set: denied});
  current.play = denied; current.pause = denied;
  const document = {
    querySelector(selector) { assert.equal(selector, 'video'); return current; },
    addEventListener(type, callback, capture) {
      const entries = listeners.get(type) || []; entries.push({callback, capture}); listeners.set(type, entries);
    }
  };
  Object.defineProperty(document, 'cookie', {get: denied, set: denied});
  const location = {origin}; Object.defineProperty(location, 'href', {get: denied});
  const context = {document, location,
    addEventListener(type, callback) { const entries = windowListeners.get(type) || []; entries.push(callback); windowListeners.set(type, entries); }};
  context.window = context; context.top = embedded ? {} : context;
  if (bridge) context.WeiktubePlaybackState = {postMessage(raw) { assert(raw.length <= 512); messages.push(JSON.parse(raw)); }};
  vm.createContext(context);
  const install = () => vm.runInContext(script, context);
  install();
  return {context, messages, listeners, windowListeners, install,
    get video() {return current;}, replace(video) {current = video;},
    emit(type, target = current) {for (const entry of listeners.get(type) || []) entry.callback({target});},
    latest: () => messages.at(-1), start(token = 'first-document') {context.weiktubeObservePlayback(token);} };
}

test('Play/pause/ended immediately report existing video state without playback commands', () => {
  const f = fixture(); f.start();
  assert.equal(f.latest().playing, false);
  f.video.paused = false; f.emit('play'); assert.equal(f.latest().playing, true);
  f.video.paused = true; f.emit('pause'); assert.equal(f.latest().playing, false);
  f.video.paused = false; f.emit('play'); f.video.ended = true; f.emit('ended');
  assert.equal(f.latest().playing, false);
  assert.deepEqual(Object.keys(f.latest()).sort(), ['height', 'playing', 'token', 'width']);
  assert.equal(f.latest().width, 1920); assert.equal(f.latest().height, 1080);
});

test('Metadata, video resize, empty and replaced media keep dimensions/current state accurate', () => {
  const f = fixture(); f.video.readyState = 0; f.video.paused = false; f.start();
  assert.equal(f.latest().playing, false);
  f.video.readyState = 1; f.emit('loadedmetadata'); assert.equal(f.latest().playing, true);
  f.video.videoWidth = 720; f.video.videoHeight = 1280; f.emit('resize');
  assert.equal(f.latest().width, 720); assert.equal(f.latest().height, 1280);
  const old = f.video; f.replace(null); f.emit('emptied', old);
  assert.deepEqual(f.latest(), {token: 'first-document', playing: false, width: 0, height: 0});
  f.replace({tagName: 'VIDEO', paused: true, ended: false, readyState: 1, videoWidth: 640, videoHeight: 360});
  f.emit('loadedmetadata'); assert.equal(f.latest().width, 640); assert.equal(f.latest().playing, false);
});

test('Repeated installation/fallback registers once and suppresses unchanged events', () => {
  const f = fixture(); f.start(); f.install(); f.install(); f.start();
  assert.equal(f.messages.length, 1);
  for (const entries of f.listeners.values()) assert.equal(entries.length, 1);
  for (const entries of f.windowListeners.values()) assert.equal(entries.length, 1);
  for (const event of ['play', 'playing', 'pause', 'ended', 'loadedmetadata', 'emptied', 'resize']) {
    assert.equal(f.listeners.get(event)[0].capture, true);
    f.emit(event);
  }
  assert.equal(f.messages.length, 1);
  f.start('second-document');
  assert.equal(f.messages.length, 2);
  assert.equal(f.messages[0].token, 'first-document'); assert.equal(f.latest().token, 'second-document');
});

test('Direct-video origin works; non-approved origins and subframes stay untouched', () => {
  const direct = fixture({origin: 'https://com.weiktube.app'}); direct.start();
  assert.equal(direct.messages.length, 1);
  for (const options of [{embedded: true}, {origin: 'http://m.youtube.com'}, {origin: 'https://youtube.com.evil.org'},
    {origin: 'https://m.youtube.com:8443'}, {origin: 'https://accounts.google.com'}]) {
    const f = fixture(options); assert.equal(f.context.weiktubeObservePlayback, undefined);
    assert.equal(f.listeners.size, 0); assert.equal(f.messages.length, 0);
  }
});

test('Missing bridge and malformed dimensions never create playback or leak other page data', () => {
  const missing = fixture({bridge: false}); missing.start(); missing.emit('play'); assert.equal(missing.messages.length, 0);
  const f = fixture(); f.video.videoWidth = Infinity; f.video.videoHeight = -10; f.start();
  assert.equal(f.latest().width, 0); assert.equal(f.latest().height, 0);
  f.video.videoWidth = 16385; f.video.videoHeight = 3.5; f.emit('resize');
  assert.equal(f.messages.length, 1);
});
console.log(`PASS ${passed} playback observer script fixtures. Native bridge/parser and real Android behavior require their separate tests.`);
