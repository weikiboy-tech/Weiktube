// Exercises the actual packaged content script. This is not a device login/playback claim.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const { webcrypto } = require('node:crypto');
const dir = path.join(__dirname, '../home-data/src/main/assets/original-home-navigation');
const source = fs.readFileSync(path.join(dir, 'bridge.js'), 'utf8');
const manifest = JSON.parse(fs.readFileSync(path.join(dir, 'manifest.json'), 'utf8'));
assert.equal(manifest.browser_specific_settings.gecko.id, 'original-home-navigation@weiktube.local');
assert.deepEqual(manifest.content_scripts[0].matches, ['https://www.youtube.com/*', 'https://m.youtube.com/*']);
assert.equal(manifest.content_scripts[0].all_frames, false);
const id = 'abcDEFghi_1';
const canonical = `https://www.youtube.com/watch?v=${id}`;

function fixture({ url = 'https://m.youtube.com/', subframe = false } = {}) {
  const messages = [], windowEvents = {}, documentEvents = {};
  let receive, disconnected, pauses = 0, connects = 0;
  const window = { addEventListener: (name, handler, capture) => {
    if (name === 'click') assert.equal(capture, true);
    windowEvents[name] = handler;
  } };
  window.top = subframe ? {} : window;
  Object.defineProperty(window, 'wrappedJSObject', { get() { throw new Error('No page config access'); } });
  const document = { hidden: false, addEventListener: (name, handler) => documentEvents[name] = handler,
    querySelectorAll: selector => {
      assert.equal(selector, 'video,audio'); return [{ pause() { pauses++; } }];
    }
  };
  for (const field of ['cookie', 'title', 'scripts']) Object.defineProperty(document, field, {
    get() { throw new Error(`Forbidden document metadata: ${field}`); }
  });
  const location = new URL(url);
  const denyNetwork = () => { throw new Error('Click bridge must not fetch anything'); };
  vm.runInNewContext(source, { window, document, location, URL, crypto: webcrypto,
    fetch: denyNetwork, XMLHttpRequest: denyNetwork,
    browser: { runtime: { connectNative(name) {
      assert.equal(name, 'weiktubeOriginalHome'); connects++;
      return { onMessage: { addListener: callback => receive = callback },
        onDisconnect: { addListener: callback => disconnected = callback }, postMessage: message => {
          const value = JSON.parse(JSON.stringify(message));
          const allowed = value.type === 'ready' ? ['type', 'documentId'] : value.type === 'enabled'
            ? ['type', 'documentId', 'generation'] : ['type', 'documentId', 'generation', 'url'];
          assert.deepEqual(Object.keys(value).sort(), allowed.sort());
          assert.ok(['ready', 'enabled', 'openVideo'].includes(value.type));
          assert.match(value.documentId, /^[0-9a-f-]{36}$/);
          messages.push(value);
        } };
    } } }
  });
  return { messages, location, document, get connects() { return connects; }, get pauses() { return pauses; },
    get documentId() { return messages.find(message => message.type === 'ready')?.documentId; },
    send(message) { receive?.(message); },
    enable(generation = 'generation-1') { receive?.({ type: 'enable', documentId: this.documentId, generation }); },
    disconnect() { disconnected?.(); },
    background() { document.hidden = true; documentEvents.visibilitychange?.(); },
    pagehide() { windowEvents.pagehide?.(); },
    tap(href, overrides = {}) {
      let prevented = false, stopped = false;
      const anchor = { tagName: 'A', getAttribute: key => key === 'href' ? href : null,
        hasAttribute: key => key === 'download' && !!overrides.download };
      const target = { closest: () => anchor };
      const event = { isTrusted: true, defaultPrevented: false, button: 0, ctrlKey: false, metaKey: false, altKey: false, shiftKey: false,
        composedPath: () => [target, anchor], target,
        preventDefault() { prevented = true; }, stopImmediatePropagation() { stopped = true; }, ...overrides };
      windowEvents.click?.(event); return { prevented, stopped };
    }
  };
}

for (const url of ['https://accounts.google.com/ServiceLogin', 'https://consent.youtube.com/m',
  'https://www.youtube.com.evil.test/', 'http://m.youtube.com/', 'https://www.youtube.com:444/',
  'https://m.youtube.com/signin', 'https://www.youtube.com/t/signin', 'https://m.youtube.com/account_switcher',
  'https://www.youtube.com/%73ignin', 'https://m.youtube.com/%XX']) {
  assert.equal(fixture({ url }).connects, 0, url);
}
assert.equal(fixture({ subframe: true }).connects, 0);
let f = fixture();
assert.equal(f.pauses, 0, 'Preview behavior is unchanged before explicit handoff');
assert.equal(f.messages.length, 1); assert.equal(f.messages[0].type, 'ready');
assert.deepEqual(f.tap(`/watch?v=${id}`), { prevented: false, stopped: false });
f.send({ type: 'enable', documentId: 'stale-document', generation: 'generation-1' });
assert.equal(f.tap(`/watch?v=${id}`).prevented, false);
for (const generation of ['', 'has space', '../bad', 'x'.repeat(129), 123]) f.enable(generation);
assert.equal(f.messages.length, 1);
f.enable();
assert.equal(f.messages.at(-1).type, 'enabled'); assert.equal(f.pauses, 0);
assert.deepEqual(f.tap(`/watch?v=${id}&list=PRIVATE_LIST&token=PRIVATE_QUERY#PRIVATE_FRAGMENT`), { prevented: true, stopped: true });
assert.equal(f.messages.at(-1).url, canonical);
assert.equal(f.messages.at(-1).generation, 'generation-1');
assert.ok(!JSON.stringify(f.messages).includes('PRIVATE'));
assert.ok(f.pauses > 0);
const handedOff = f.messages.length;
assert.equal(f.tap(`/watch?v=${id}`).prevented, true, 'Rapid duplicate stays suppressed');
assert.equal(f.messages.length, handedOff, 'Exactly one native handoff');

for (const url of [`https://www.youtube.com/watch?v=${id}`, `/shorts/${id}`, `/live/${id}`, `https://youtu.be/${id}?si=PRIVATE`,
  `https://youtube.com/watch?v=${id}`, `https://www.youtube.com:443/watch?v=${id}`]) {
  f = fixture(); f.enable(); assert.equal(f.tap(url).prevented, true, url); assert.equal(f.messages.at(-1).url, canonical);
}
for (const url of ['/signin', '/account_switcher', '/@channel', '/feed/subscriptions', '/results?search_query=example',
  `https://evil.test/watch?v=${id}`, `https://youtube.com.evil.test/watch?v=${id}`, `http://youtu.be/${id}`,
  `https://www.youtube.com./watch?v=${id}`, `https://private@youtube.com/watch?v=${id}`, `https://m.youtube.com:444/watch?v=${id}`,
  `https://youtu.be/${id}/extra`, `/watch?v=${id}&v=${id}`, `/watch?v=short`, `/watch?v=${id}%0A`,
  'javascript:alert(1)', `https://m.youtube.com\\@evil.test/watch?v=${id}`, `/redirect?q=https://youtu.be/${id}`]) {
  f = fixture(); f.enable(); const count = f.messages.length;
  assert.deepEqual(f.tap(url), { prevented: false, stopped: false }, url);
  assert.equal(f.messages.length, count); assert.equal(f.pauses, 0);
}
for (const overrides of [{ isTrusted: false }, { button: 1 }, { ctrlKey: true }, { metaKey: true }, { shiftKey: true },
  { altKey: true }, { defaultPrevented: true }, { download: true }]) {
  f = fixture(); f.enable(); assert.equal(f.tap(`/watch?v=${id}`, overrides).prevented, false);
  assert.equal(f.messages.length, 2); assert.equal(f.pauses, 0);
}
f = fixture(); f.enable(); f.send({ type: 'disable', documentId: f.documentId });
assert.ok(f.pauses > 0); assert.equal(f.tap(`/watch?v=${id}`).prevented, false);
f.enable('generation-2'); assert.equal(f.tap(`/watch?v=${id}`).prevented, true);
assert.equal(f.messages.at(-1).generation, 'generation-2');
f = fixture(); f.enable(); f.background(); assert.ok(f.pauses > 0);
f.send({ type: 'pause', documentId: f.documentId }); assert.ok(f.pauses > 1);
f.pagehide(); assert.equal(f.tap(`/watch?v=${id}`).prevented, false);
f = fixture(); f.enable(); f.disconnect(); assert.equal(f.tap(`/watch?v=${id}`).prevented, false);
f.enable(); assert.equal(f.messages.length, 2);
f = fixture(); f.enable(); f.location.pathname = '/signin';
assert.equal(f.tap(`/watch?v=${id}`).prevented, false); f.enable(); assert.equal(f.messages.length, 2);
const nextDocument = fixture(); assert.notEqual(nextDocument.documentId, f.documentId);
assert.equal(nextDocument.tap(`/watch?v=${id}`).prevented, false, 'Every new document starts disabled');
console.log('PASS Original Home click bridge: exact origins, auth-page exclusion, manual enable/document/generation gates, trusted video-only taps, canonical URL isolation, duplicate suppression, untouched account links/previews, pause and teardown.');
