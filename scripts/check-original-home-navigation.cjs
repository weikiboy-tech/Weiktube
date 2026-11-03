// Actual packaged script in isolated/page-world fixtures. Not a live Gecko/YouTube proof.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const { webcrypto } = require('node:crypto');
const dir = path.join(__dirname, '../home-data/src/main/assets/original-home-navigation');
const source = fs.readFileSync(process.env.WEIKTUBE_HOME_BRIDGE_SOURCE || path.join(dir, 'bridge.js'), 'utf8');
const manifest = JSON.parse(fs.readFileSync(path.join(dir, 'manifest.json'), 'utf8'));
assert.equal(manifest.version, '2.0.1');
assert.equal(manifest.browser_specific_settings.gecko.id, 'original-home-navigation@weiktube.local');
assert.deepEqual(manifest.content_scripts[0].matches, ['https://www.youtube.com/*', 'https://m.youtube.com/*', 'https://youtube.com/*']);
assert.equal(manifest.content_scripts[0].run_at, 'document_start');
assert.equal(manifest.content_scripts[0].all_frames, false);
assert.deepEqual(manifest.permissions, ['nativeMessaging', 'nativeMessagingFromContent', 'geckoViewAddons']);
const id = 'abcDEFghi_1', id2 = 'zyxWVUtuv_2';
const canonical = `https://www.youtube.com/watch?v=${id}`;
const reasons = new Set(['INVALID_VIDEO', 'BRIDGE_UNAVAILABLE', 'HISTORY_GUARD_UNAVAILABLE', 'MEDIA_GUARD_UNAVAILABLE', 'UNTRUSTED_NAVIGATION']);
function element(tagName = 'SPAN', attrs = {}, pageData) {
  const node = { tagName, getAttribute: name => attrs[name] ?? null, hasAttribute: name => Object.hasOwn(attrs, name),
    querySelectorAll: () => [], isContentEditable: false };
  if (pageData) node.wrappedJSObject = pageData;
  return node;
}
const anchor = href => element('A', { href });
function fixture({ url = 'https://m.youtube.com/', subframe = false, exports = true, connectFailure = false } = {}) {
  const messages = [], windowEvents = {}, documentEvents = {}, nativeHistory = [], exportTargets = [];
  let receive, disconnected, mutation, connects = 0, pauses = 0, plays = 0, now = 100, failPost = false;
  let currentGeneration, rejectConnect = connectFailure;
  const portListeners = [];
  const location = new URL(url);
  function CleanHistory() {}
  function PageHistory() {}
  for (const method of ['pushState', 'replaceState']) {
    CleanHistory.prototype[method] = function (...args) {
      nativeHistory.push({ method, args, receiver: this });
      if (args[2] !== undefined) location.href = new URL(args[2], location.href).href;
      return undefined;
    };
    PageHistory.prototype[method] = CleanHistory.prototype[method];
  }
  const history = new PageHistory();
  function CleanMedia() {}
  CleanMedia.prototype.pause = function () { this.paused = true; pauses++; };
  CleanMedia.prototype.play = function () { this.paused = false; plays++; return Promise.resolve(); };
  function PageMedia() { this.tagName = 'VIDEO'; this.paused = false; this.muted = false; this.autoplay = true; }
  PageMedia.prototype.pause = CleanMedia.prototype.pause;
  PageMedia.prototype.play = CleanMedia.prototype.play;
  const media = new PageMedia(), mediaNodes = [media];
  const page = new Proxy({ History: PageHistory, HTMLMediaElement: PageMedia }, {
    get(target, key) { assert.ok(['History', 'HTMLMediaElement'].includes(key), `Forbidden page global: ${String(key)}`); return target[key]; }
  });
  const window = { history, History: CleanHistory, HTMLMediaElement: CleanMedia, Promise, DOMException,
    wrappedJSObject: page, scrollX: 0, scrollY: 480,
    addEventListener(name, handler, options) {
      if (['click', 'auxclick', 'play', 'playing', 'popstate', 'pointerdown', 'pointermove', 'pointerup', 'pointercancel'].includes(name)) assert.equal(options, true);
      (windowEvents[name] ||= []).push(handler);
    }, requestAnimationFrame: fn => fn(), scrollTo(x, y) { this.scrollX = x; this.scrollY = y; }
  };
  window.top = subframe ? {} : window;
  const document = { hidden: false,
    addEventListener: (name, handler) => (documentEvents[name] ||= []).push(handler),
    querySelectorAll(selector) { assert.equal(selector, 'video,audio'); return mediaNodes; }
  };
  for (const field of ['cookie', 'title', 'scripts', 'body', 'documentElement', 'textContent']) {
    Object.defineProperty(document, field, { get() { throw new Error(`Forbidden document data: ${field}`); } });
  }
  const noNetwork = () => { throw new Error('Bridge must not fetch'); };
  vm.runInNewContext(source, { window, document, location, URL, crypto: webcrypto, performance: { now: () => now },
    MutationObserver: class { constructor(callback) { mutation = callback; } observe(doc, options) {
      assert.equal(doc, document); assert.deepEqual(JSON.parse(JSON.stringify(options)), { childList: true, subtree: true });
    } },
    exportFunction: exports ? (fn, scope, options) => {
      assert.ok(scope === PageHistory.prototype || scope === PageMedia.prototype);
      assert.deepEqual(Object.keys(options), ['defineAs']);
      exportTargets.push(options.defineAs); scope[options.defineAs] = fn; return fn;
    } : undefined,
    fetch: noNetwork, XMLHttpRequest: noNetwork,
    browser: { runtime: { connectNative(name) {
      assert.equal(name, 'weiktubeOriginalHome'); connects++;
      if (rejectConnect) throw new Error('fixture connection failed');
      const listeners = { message: 0, disconnect: 0 }; portListeners.push(listeners);
      return { onMessage: { addListener: fn => { receive = fn; listeners.receive = fn; listeners.message++; } },
        onDisconnect: { addListener: fn => { disconnected = fn; listeners.disconnected = fn; listeners.disconnect++; } }, disconnect() {},
        postMessage(message) {
          if (failPost) throw new Error('fixture native port lost');
          const value = JSON.parse(JSON.stringify(message));
          const allowed = value.type === 'ready' ? ['type', 'documentId'] : value.type === 'enabled'
            ? ['type', 'documentId', 'generation'] : value.type === 'openVideo'
              ? ['type', 'documentId', 'generation', 'url'] : ['type', 'documentId', 'reason', ...(value.generation ? ['generation'] : [])];
          assert.deepEqual(Object.keys(value).sort(), allowed.sort());
          assert.ok(['ready', 'enabled', 'openVideo', 'blocked'].includes(value.type));
          assert.match(value.documentId, /^[0-9a-f-]{36}$/);
          if (value.type === 'blocked') assert.ok(reasons.has(value.reason));
          if (value.type === 'openVideo') assert.match(value.url, /^https:\/\/www\.youtube\.com\/watch\?v=[A-Za-z0-9_-]{11}$/);
          messages.push(value);
        } };
    } } }
  });
  function dispatch(name, event) { for (const fn of windowEvents[name] || []) fn(event); }
  return { messages, location, document, window, history, nativeHistory, exportTargets, media,
    get connects() { return connects; }, get pauses() { return pauses; }, get plays() { return plays; },
    get opens() { return messages.filter(x => x.type === 'openVideo'); },
    get documentId() { return messages.find(x => x.type === 'ready')?.documentId; },
    send(message) { receive?.(message); },
    enable(generation = 'generation-1') { currentGeneration = generation; receive?.({ type: 'enable', documentId: this.documentId, generation }); },
    disable(generation = currentGeneration) { receive?.({ type: 'disable', documentId: this.documentId, generation }); },
    allowConnect() { rejectConnect = false; }, restorePost() { failPost = false; },
    disconnect() { disconnected?.(); }, failPost() { failPost = true; }, advance(ms) { now += ms; },
    background() { document.hidden = true; (documentEvents.visibilitychange || []).forEach(fn => fn()); },
    foreground() { document.hidden = false; (documentEvents.visibilitychange || []).forEach(fn => fn()); },
    pagehide() { dispatch('pagehide', {}); }, pageshow() { dispatch('pageshow', {}); },
    get listeners() { return JSON.stringify({ window: Object.fromEntries(Object.entries(windowEvents).map(([k,v]) => [k,v.length])),
      document: Object.fromEntries(Object.entries(documentEvents).map(([k,v]) => [k,v.length])) }); },
    get portListeners() { return portListeners; },
    tap(href, overrides = {}) { return this.tapPath([element('IMG'), anchor(href)], overrides); },
    tapPath(path, overrides = {}) {
      let prevented = false, stopped = false;
      const event = { isTrusted: true, defaultPrevented: false, button: 0, ctrlKey: false, metaKey: false, altKey: false, shiftKey: false,
        target: path[0], composedPath: () => path,
        preventDefault() { prevented = true; }, stopImmediatePropagation() { stopped = true; }, ...overrides };
      dispatch(overrides.eventName || 'click', event); return { prevented, stopped };
    },
    pointer(name, path, overrides = {}) {
      return this.tapPath(path, { eventName: name, pointerId: 1, isPrimary: true, clientX: 60, clientY: 200, ...overrides });
    },
    pageHistory(method, url, state = { ordinary: 'state' }, title = '') { return history[method](state, title, url); },
    addMedia() { const next = new PageMedia(); mediaNodes.push(next); mutation?.([{ addedNodes: [next] }]); return next; },
    mediaEvent(name, node = media) { node.paused = false; dispatch(name, { target: node }); },
    scroll(x, y) { window.scrollX = x; window.scrollY = y; dispatch('scroll', {}); },
    pop(url) { location.href = new URL(url, location.href).href; let stopped = false; dispatch('popstate', { stopImmediatePropagation() { stopped = true; } }); return stopped; }
  };
}

(async () => {
  // 1.2.7 regression: a late disable from the previous activation must not revoke the return.
  let returning = fixture(); returning.enable('before-player'); returning.tap(`/watch?v=${id}`);
  returning.disable(); returning.background(); returning.foreground(); returning.enable('after-player');
  returning.disable('before-player'); returning.tap(`/watch?v=${id2}`);
  assert.equal(returning.opens.length, 2, 'A retired disable must not strand the second video');

  // Page lifecycle delivery may follow native onResume. Reannounce readiness without reloading.
  returning = fixture(); returning.enable('before-player'); returning.tap(`/watch?v=${id}`);
  returning.disable(); returning.background(); returning.enable('after-player'); returning.pagehide();
  const readyBeforeReturn = returning.messages.filter(x => x.type === 'ready').length;
  returning.foreground(); returning.pageshow();
  assert.ok(returning.messages.filter(x => x.type === 'ready').length > readyBeforeReturn, 'Visible document must rejoin native handshake');
  returning.enable('after-player'); returning.tap(`/watch?v=${id2}`);
  assert.equal(returning.opens.length, 2, 'Same activation ACK resumes a pagehidden document');

  // A nonce retry acknowledges a lost ACK but never re-arms an already selected video.
  returning = fixture(); returning.enable('active'); returning.tap(`/watch?v=${id}`); returning.enable('active');
  assert.equal(returning.messages.filter(x => x.type === 'enabled').length, 2, 'Native retry receives an ACK');
  returning.tap(`/watch?v=${id2}`); assert.equal(returning.opens.length, 1, 'ACK retry cannot duplicate player launch');

  // A real post-return tap before the new ACK waits for that grant; it needs no second tap.
  returning = fixture(); returning.enable('before-player'); returning.tap(`/watch?v=${id}`);
  returning.disable(); returning.background(); returning.foreground();
  returning.tap(`/watch?v=${id2}`); returning.tap(`/watch?v=${id}`);
  returning.enable('before-player');
  assert.equal(returning.opens.length, 1, 'Retried old grant cannot launch the queued return tap');
  returning.enable('after-player');
  assert.equal(returning.opens.length, 2, 'First post-return tap is delivered after the fresh grant');
  assert.equal(returning.opens[1].url, `https://www.youtube.com/watch?v=${id2}`, 'Only the first post-return selection waits');

  returning = fixture(); returning.enable('before-player'); returning.tap(`/watch?v=${id}`);
  returning.tap(`/watch?v=${id2}`); returning.disable(); returning.background(); returning.foreground(); returning.enable('after-player');
  assert.equal(returning.opens.length, 1, 'Old foreground double-tap is never replayed on return');

  returning = fixture(); returning.enable('before-player');
  const fastSameVideo = [element('IMG'), anchor(`/watch?v=${id}`)];
  returning.pointer('pointerdown', fastSameVideo); returning.pointer('pointerup', fastSameVideo);
  returning.disable(); returning.background(); returning.foreground(); returning.enable('after-player');
  returning.tapPath(fastSameVideo);
  assert.equal(returning.opens.length, 1, 'A late old compatibility click is still consumed after a fast return');
  returning.pointer('pointerdown', fastSameVideo); returning.pointer('pointerup', fastSameVideo); returning.tapPath(fastSameVideo);
  assert.equal(returning.opens.length, 2, 'A new real gesture can reopen the same video inside the 800ms compatibility window');

  returning = fixture(); returning.enable('before-player');
  returning.pointer('pointerdown', fastSameVideo); returning.pointer('pointerup', fastSameVideo);
  returning.disable(); returning.background(); returning.foreground(); returning.enable('after-player');
  returning.pointer('pointerdown', fastSameVideo); returning.tapPath(fastSameVideo);
  assert.equal(returning.opens.length, 2, 'A fresh pointerdown also permits the click fallback if no usable pointerup arrives');

  // A disconnected document remains alive: reconnect only on a visible event or real user action.
  returning = fixture(); returning.enable('before-player'); returning.tap(`/watch?v=${id}`);
  const originalListeners = returning.listeners;
  returning.background(); returning.disconnect(); returning.advance(1000); returning.foreground(); returning.pageshow();
  assert.equal(returning.connects, 2, 'Foreground reconnects the existing document once');
  returning.enable('after-player'); returning.tap(`/watch?v=${id2}`); assert.equal(returning.opens.length, 2);
  assert.equal(returning.listeners, originalListeners, 'Reconnect never adds document event handlers');
  assert.ok(returning.portListeners.every(x => x.message === 1 && x.disconnect === 1));
  returning.portListeners[0].disconnected();
  returning.portListeners[0].receive({ type: 'disable', documentId: returning.documentId, generation: 'after-player' });
  returning.disable(); returning.enable('next-player'); returning.tap(`/watch?v=${id}`);
  assert.equal(returning.opens.length, 3, 'Callbacks from a replaced port cannot disable the current port');

  // Ten full simulated return cycles on one DOM/session, including the same video twice,
  // lower scroll positions, search/channel navigation, Shorts and fast duplicate input.
  returning = fixture(); const cycleListeners = returning.listeners;
  const identity = returning.documentId;
  returning.enable('cycle-0');
  for (let cycle = 0; cycle < 10; cycle++) {
    returning.scroll(0, 480 + cycle * 250);
    assert.deepEqual(returning.tap('/@channel'), { prevented: false, stopped: false });
    assert.deepEqual(returning.tap('/results?search_query=normal'), { prevented: false, stopped: false });
    returning.pageHistory('pushState', '/results?search_query=normal');
    const route = cycle === 5 ? `/shorts/${id2}` : `/watch?v=${cycle < 2 || cycle % 2 === 0 ? id : id2}`;
    const hit = [element('IMG'), anchor(route)];
    returning.pointer('pointerdown', hit); returning.pointer('pointerup', hit);
    returning.tapPath(hit); returning.tap(route); returning.enable(`cycle-${cycle}`);
    returning.tap(`/watch?v=${id2}`);
    assert.equal(returning.opens.length, cycle + 1, 'One native launch per activation even with retries and repeated taps');
    const originalLocation = returning.location.href, scroll = returning.window.scrollY;
    returning.disable(); returning.background(); returning.pagehide(); returning.advance(1000);
    returning.foreground(); returning.pageshow(); returning.enable(`cycle-${cycle + 1}`);
    returning.disable(`cycle-${cycle}`); returning.enable(`cycle-${cycle}`);
    assert.equal(returning.location.href, originalLocation, 'No reload or web video navigation');
    assert.equal(returning.window.scrollY, scroll, 'No script scroll reset');
    assert.equal(returning.documentId, identity);
    assert.equal(returning.listeners, cycleListeners);
  }
  assert.equal(returning.opens[0].url, returning.opens[1].url, 'The same video can be selected after returning');
  assert.equal(returning.connects, 1, 'Healthy port survives every cycle');
  assert.equal(returning.opens.length, 10);
  assert.equal(returning.plays, 0);

  returning = fixture({ connectFailure: true });
  for (let i = 0; i < 10; i++) { returning.tap(`/watch?v=${id}`); returning.pageshow(); }
  assert.equal(returning.connects, 1, 'Failed reconnection is coalesced; no retry loop');
  returning.advance(600); returning.allowConnect(); returning.tap(`/watch?v=${id}`); returning.enable('recovered');
  assert.equal(returning.connects, 2); assert.equal(returning.opens.length, 1, 'A later trusted tap can recover the same document');

  returning = fixture(); returning.enable('active'); returning.disconnect(); returning.advance(1000);
  returning.tap(`/watch?v=${id}`, { isTrusted: false });
  assert.equal(returning.connects, 1, 'Synthetic click cannot reconnect or request playback');
  returning.tap(`/watch?v=${id}`); returning.enable('reconnected');
  assert.equal(returning.connects, 2); assert.equal(returning.opens.length, 1);

  for (const url of ['https://accounts.google.com/ServiceLogin', 'https://consent.youtube.com/m',
    'https://www.youtube.com.evil.test/', 'http://m.youtube.com/', 'https://www.youtube.com:444/',
    'https://m.youtube.com/signin', 'https://www.youtube.com/t/signin', 'https://m.youtube.com/account_switcher',
    'https://www.youtube.com/%73ignin', 'https://m.youtube.com/%XX']) {
    const f = fixture({ url }); assert.equal(f.connects, 0, url); assert.equal(f.exportTargets.length, 0); assert.equal(f.pauses, 0);
  }
  assert.equal(fixture({ subframe: true }).connects, 0);
  let f = fixture();
  assert.deepEqual(f.exportTargets, ['pushState', 'replaceState', 'play']);
  assert.equal(f.media.paused, true); assert.equal(f.media.muted, true); assert.equal(f.media.autoplay, false);
  assert.deepEqual(f.tap(`/watch?v=${id}&token=PRIVATE_QUERY#PRIVATE_FRAGMENT`), { prevented: true, stopped: true });
  assert.equal(f.opens.length, 0, 'Early trusted tap queues; never navigates');
  f.tap(`/watch?v=${id2}`); f.enable();
  assert.equal(f.opens.length, 1); assert.equal(f.opens[0].url, canonical, 'Only first queued video');
  assert.ok(!JSON.stringify(f.messages).includes('PRIVATE'));
  f.tap(`/watch?v=${id}`); f.enable(); assert.equal(f.opens.length, 1, 'Duplicate nonce/tap does not reopen');
  f.disable(); f.enable('generation-2'); f.tap(`/watch?v=${id}`);
  assert.equal(f.opens.length, 2); assert.equal(f.opens[1].generation, 'generation-2');

  for (const url of [`https://www.youtube.com/watch?v=${id}`, `/shorts/${id}`, `/live/${id}`, `https://youtu.be/${id}?si=PRIVATE`,
    `https://youtube.com/watch?v=${id}`, `https://www.youtube.com:443/watch?v=${id}`]) {
    f = fixture(); f.enable(); assert.equal(f.tap(url).prevented, true, url); assert.equal(f.opens[0].url, canonical);
  }
  for (const url of [`/watch?v=${id}&v=${id}`, '/watch', '/watch?v=short', `/watch?v=${id}%0A`, '/%77atch?v=bad',
    `/shorts/${id}/extra`, `https://youtu.be/${id}/extra`, `http://m.youtube.com/watch?v=${id}`, `https://private@youtube.com/watch?v=${id}`,
    `https://m.youtube.com:444/watch?v=${id}`]) {
    f = fixture(); f.enable(); assert.equal(f.tap(url).prevented, true, url); assert.equal(f.opens.length, 0);
    assert.ok(f.messages.some(x => x.reason === 'INVALID_VIDEO'));
  }
  for (const url of ['/signin', '/account_switcher', '/@channel', '/feed/subscriptions', '/results?search_query=example',
    `https://evil.test/watch?v=${id}`, `https://youtube.com.evil.test/watch?v=${id}`, 'javascript:alert(1)', `/redirect?q=https://youtu.be/${id}`]) {
    f = fixture(); f.enable(); assert.deepEqual(f.tap(url), { prevented: false, stopped: false }, url); assert.equal(f.opens.length, 0);
  }
  for (const overrides of [{ isTrusted: false }, { button: 1, eventName: 'auxclick' }, { ctrlKey: true }, { metaKey: true }, { shiftKey: true }, { altKey: true }]) {
    f = fixture(); f.enable(); assert.equal(f.tap(`/watch?v=${id}`, overrides).prevented, true); assert.equal(f.opens.length, 0);
  }

  // Mobile renderer data and modern nested onTap command; no global/outer-card fallback.
  for (const data of [ { data: { navigationEndpoint: { watchEndpoint: { videoId: id } } } },
    { data: { onTap: { innertubeCommand: { watchEndpoint: { videoId: id } } } } },
    { __data: { data: { rendererContext: { commandContext: { onTap: { innertubeCommand: { reelWatchEndpoint: { videoId: id } } } } } } } } ]) {
    f = fixture(); f.enable(); const card = element('YTM-VIDEO-WITH-CONTEXT-RENDERER', {}, data);
    const hit = f.tapPath([element('IMG'), element('DIV'), card]);
    assert.equal(hit.stopped, true); assert.equal(f.opens[0].url, canonical);
    let domChanged = false; if (!hit.stopped) domChanged = true;
    assert.equal(domChanged, false, 'Capture prevents router DOM replacement');
  }
  const validCard = () => element('YTM-VIDEO-WITH-CONTEXT-RENDERER', {}, { data: { navigationEndpoint: { watchEndpoint: { videoId: id } } } });
  for (const control of [anchor('/@channel'), element('BUTTON'), element('DIV', { role: 'button' }), element('YTM-MENU-RENDERER')]) {
    f = fixture(); f.enable(); assert.equal(f.tapPath([element('IMG'), control, validCard()]).prevented, false);
    assert.throws(() => f.pageHistory('pushState', `/watch?v=${id}`), { name: 'AbortError' });
    assert.equal(f.opens.length, 0, 'Menu/channel gesture never reused');
  }
  f = fixture(); f.enable();
  const unknown = element('YTM-VIDEO-WITH-CONTEXT-RENDERER', {}, {});
  assert.equal(f.tapPath([element('IMG'), unknown, validCard()]).prevented, true); assert.equal(f.opens.length, 0);
  const accessor = {}; Object.defineProperty(accessor, 'data', { get() { throw new Error('Page getter must not run'); } });
  f.tapPath([element('YTM-VIDEO-WITH-CONTEXT-RENDERER', {}, accessor)]); assert.equal(f.opens.length, 0);

  // Calls come from the simulated page prototype, distinct from the clean isolated History.
  for (const method of ['pushState', 'replaceState']) {
    f = fixture(); f.enable(); f.tapPath([element('DIV')]);
    let routerMutated = false;
    assert.throws(() => { f.pageHistory(method, `/watch?v=${id}`); routerMutated = true; }, { name: 'AbortError' });
    assert.equal(routerMutated, false, 'Uncaught router stack stops before following DOM mutation');
    assert.equal(f.opens[0].url, canonical); assert.equal(f.nativeHistory.length, 0); assert.equal(f.location.href, 'https://m.youtube.com/');
    f = fixture(); f.enable(); assert.throws(() => f.pageHistory(method, `/watch?v=${id}`), { name: 'AbortError' });
    assert.equal(f.opens.length, 0); assert.equal(f.nativeHistory.length, 0); assert.ok(f.messages.some(x => x.reason === 'UNTRUSTED_NAVIGATION'));
    f = fixture(); f.enable(); f.tapPath([element('DIV')]); f.advance(2000);
    assert.throws(() => f.pageHistory(method, `/watch?v=${id}`), { name: 'AbortError' });
    assert.equal(f.opens.length, 0);
    const state = new Proxy({}, { get() { throw new Error('History state must not be inspected'); } });
    f.pageHistory(method, '/results?search_query=normal', state, 'normal title');
    assert.equal(f.nativeHistory[0].args[0], state); assert.equal(f.nativeHistory[0].args[1], 'normal title');
    assert.equal(f.location.pathname, '/results');
  }
  f = fixture(); f.enable(); f.scroll(0, 940);
  assert.equal(f.pop(`/watch?v=${id}`), true); assert.equal(f.location.pathname, '/'); assert.equal(f.window.scrollY, 940); assert.equal(f.opens.length, 0);
  f = fixture(); f.enable(); f.disconnect(); assert.equal(f.tap(`/watch?v=${id}`).prevented, true);
  assert.throws(() => f.pageHistory('pushState', `/watch?v=${id}`), { name: 'AbortError' });
  assert.equal(f.opens.length, 0); assert.equal(f.location.pathname, '/');
  f = fixture({ connectFailure: true }); assert.equal(f.tap(`/watch?v=${id}`).prevented, true); assert.equal(f.location.pathname, '/');
  f = fixture(); f.enable(); f.failPost(); assert.equal(f.tap(`/watch?v=${id}`).prevented, true); assert.equal(f.opens.length, 0);
  f = fixture({ exports: false }); assert.ok(f.messages.some(x => x.reason === 'HISTORY_GUARD_UNAVAILABLE'));
  assert.ok(f.messages.some(x => x.reason === 'MEDIA_GUARD_UNAVAILABLE')); assert.equal(f.tap(`/watch?v=${id}`).prevented, true);

  f = fixture(); const inserted = f.addMedia(); assert.equal(inserted.paused, true); assert.equal(inserted.muted, true);
  f.mediaEvent('play'); assert.equal(f.media.paused, true); f.mediaEvent('playing'); assert.equal(f.media.paused, true);
  await assert.rejects(f.media.play(), error => error.name === 'NotAllowedError'); assert.equal(f.plays, 0);
  f.disconnect(); await assert.rejects(f.media.play()); assert.equal(f.plays, 0);
  f.location.pathname = '/signin'; await f.media.play(); assert.equal(f.plays, 1, 'No auth-page media patch effect');
  assert.equal(f.tap(`/watch?v=${id}`).prevented, false, 'Auth-page clicks left alone');
  f = fixture(); f.tap(`/watch?v=${id}`); f.background(); f.enable(); f.foreground();
  assert.equal(f.opens.length, 0, 'Queued tap cannot open from the background');
  f.pagehide(); assert.equal(f.tap(`/watch?v=${id}`).prevented, true);
  const next = fixture(); assert.notEqual(next.documentId, f.documentId);
  next.send({ type: 'enable', documentId: f.documentId, generation: 'stale' }); next.tap(`/watch?v=${id}`);
  assert.equal(next.opens.length, 0);
  f = fixture(); f.enable();
  const pointerPath = [element('IMG'), anchor(`/watch?v=${id}`)];
  assert.equal(f.pointer('pointerdown', pointerPath).prevented, false, 'Pointerdown never blocks scroll');
  assert.equal(f.pointer('pointerup', pointerPath).prevented, true); assert.equal(f.opens.length, 1);
  f.enable('generation-2'); // Even an unusually early native return must not duplicate compatibility click.
  assert.equal(f.tapPath(pointerPath).prevented, true); assert.equal(f.opens.length, 1);
  f.advance(900); f.tapPath(pointerPath); assert.equal(f.opens.length, 2);
  f = fixture(); f.enable(); f.pointer('pointerdown', pointerPath);
  assert.equal(f.pointer('pointermove', pointerPath, { clientY: 240 }).prevented, false);
  assert.equal(f.pointer('pointerup', pointerPath, { clientY: 240 }).prevented, false); assert.equal(f.opens.length, 0);
  f.pointer('pointerdown', pointerPath); f.pointer('pointercancel', pointerPath);
  assert.equal(f.pointer('pointerup', pointerPath).prevented, false); assert.equal(f.opens.length, 0);
  f.pointer('pointerdown', pointerPath); f.advance(900);
  assert.equal(f.pointer('pointerup', pointerPath).prevented, false, 'Long press does not become native playback');
  f.pointer('pointerdown', pointerPath); f.pointer('contextmenu', pointerPath);
  assert.equal(f.pointer('pointerup', pointerPath).prevented, false); assert.equal(f.opens.length, 0);
  const genericPath = [element('DIV')];
  f.pointer('pointerdown', genericPath); f.pointer('pointerup', genericPath);
  assert.throws(() => f.pageHistory('pushState', `/watch?v=${id}`), { name: 'AbortError' }); assert.equal(f.opens.length, 1);
  console.log('PASS Original Home bridge: ten simulated return cycles including same video, Shorts/search/channel/scroll; stale disable/enable and lost-ACK recovery; page visibility rehandshake and bounded port recovery without duplicate listeners; early/invalid/duplicate clicks fail closed; renderer/history/media guards; sanitized protocol and no secret/network access. Real Android touch, Gecko/Xray, current YouTube DOM and playback still require runtime testing.');
})().catch(error => { console.error(error); process.exitCode = 1; });
