import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const root = new URL('../home-data/src/main/assets/youtube-adblock/', import.meta.url);
const background = fs.readFileSync(new URL('background.js', root), 'utf8');
const bridge = fs.readFileSync(new URL('status-bridge.js', root), 'utf8');
const manifest = JSON.parse(fs.readFileSync(new URL('manifest.json', root), 'utf8'));
assert(!manifest.permissions.includes('<all_urls>'));
assert(!manifest.permissions.includes('nativeMessagingFromContent'));
assert(!manifest.permissions.some(value => value.includes('googlevideo')));
const tick = () => new Promise(resolve => setImmediate(resolve));
const plain = value => JSON.parse(JSON.stringify(value));
const policy = (overrides = {}) => ({schemaVersion: 1, version: 1, token: 'doc-one', enabled: true,
  exceptions: [], networkRules: [
    {id: 'display', host: 'doubleclick.net', pathPrefix: ''},
    {id: 'youtube-ad-request', host: 'youtube.com', pathPrefix: '/pagead/'}
  ], scriptlets: [], cosmeticSelectors: [], ...overrides});

function setup() {
  const listeners = {};
  const sent = [];
  const registrations = [];
  const timers = [];
  let rejectRegistration = false;
  let holdRegistration = null;
  const port = {postMessage: value => sent.push(value),
    onMessage: {addListener: value => { listeners.native = value; }},
    onDisconnect: {addListener: value => { listeners.disconnect = value; }}};
  const browser = {
    runtime: {connectNative: name => { assert.equal(name, 'weiktubeAdBlock'); return port; },
      onMessage: {addListener: value => { listeners.status = value; }}},
    contentScripts: {register: async config => {
      if (rejectRegistration) throw new Error('unsupported world');
      if (holdRegistration) await holdRegistration;
      const registration = {config, removed: false, unregister: async () => { registration.removed = true; }};
      registrations.push(registration); return registration;
    }},
    webRequest: {onBeforeRequest: {addListener: (value, filter, flags) => {
      assert.deepEqual(plain(flags), ['blocking']);
      assert(!filter.urls.includes('<all_urls>'));
      listeners.request = value;
    }}}
  };
  vm.runInNewContext(background, {browser, URL, Map, Set, Promise, JSON, Number,
    setTimeout: (fn, delay) => { timers.push({fn, delay}); return timers.length; }});
  return {listeners, sent, registrations, timers,
    configure: async config => { listeners.native({type: 'configure', revision: config.version,
      config, script: 'window.__weiktubeAdConfig=' + JSON.stringify(config) + ';'}); await tick(); },
    reject: () => { rejectRegistration = true; },
    allow: () => { rejectRegistration = false; },
    hold: promise => { holdRegistration = promise; },
    flush: () => timers.filter(item => item.delay === 250).splice(0).forEach(item => item.fn())};
}

const state = setup();
const firstLoad = state.listeners.request({type: 'main_frame', tabId: 1, url: 'https://m.youtube.com/'});
assert.equal(typeof firstLoad.then, 'function', 'first navigation waits for document-start scripts');
await state.configure(policy());
assert.deepEqual(plain(await firstLoad), {});
assert.equal(state.registrations.length, 2);
assert.equal(state.registrations[0].config.world, 'ISOLATED');
assert.equal(state.registrations[1].config.world, 'MAIN');
assert(state.registrations.every(value => value.config.runAt === 'document_start' && value.config.allFrames === false));
assert.equal(state.sent[0].type, 'configured');
for (const pathname of ['/signin', '/login', '/LOGIN', '/account_switcher', '/o/oauth2/auth', '/ServiceLogin', '/sErViCeLoGiN']) {
  const authWindow = {};
  vm.runInNewContext(state.registrations[1].config.js[0].code, {window: authWindow, location: {pathname}});
  assert.equal(authWindow.__weiktubeAdConfig, undefined, 'page code must not execute on auth paths');
}

const request = (url, extra = {}) => state.listeners.request({type: 'xmlhttprequest', tabId: 1,
  documentUrl: 'https://m.youtube.com/', url, ...extra});
assert.deepEqual(plain(request('https://securepubads.g.doubleclick.net/ads')), {cancel: true});
assert.deepEqual(plain(request('https://www.youtube.com/pagead/adview')), {cancel: true});
for (const url of ['https://rr1.googlevideo.com/videoplayback?id=regular',
  'https://www.youtube.com/youtubei/v1/player', 'https://www.youtube.com/api/stats/watchtime',
  'https://doubleclick.net.example.org/ads', 'https://www.youtube.com/watch?v=abcdefghijk']) {
  assert.deepEqual(plain(request(url)), {}, `ordinary playback is preserved: ${url}`);
}
assert.deepEqual(plain(request('https://doubleclick.net/ads', {documentUrl: 'https://accounts.google.com/'})), {});
assert.deepEqual(plain(request('https://doubleclick.net/ads', {documentUrl: 'https://m.youtube.com/signin'})), {});
for (const path of ['/LOGIN', '/account_switcher', '/o/oauth2/auth', '/ServiceLogin', '/sErViCeLoGiN']) {
  assert.deepEqual(plain(request('https://doubleclick.net/ads', {documentUrl: 'https://m.youtube.com' + path})), {});
}
assert.deepEqual(plain(request('https://doubleclick.net/ads', {type: 'main_frame'})), {});
// Reset the same YouTube document after the deliberately unrelated main-frame check.
state.listeners.request({type: 'main_frame', tabId: 1, url: 'https://m.youtube.com/'});
state.flush();
assert.equal(state.sent.filter(value => value.type === 'network').at(-1).count, 2);

const trusted = {tab: {id: 1}, frameId: 0, url: 'https://m.youtube.com/'};
state.listeners.status({type: 'status', event: 'data', token: 'doc-one', version: 1, count: 3}, trusted);
state.listeners.status({type: 'status', event: 'data', token: 'wrong', version: 1, count: 99}, trusted);
state.listeners.status({type: 'status', event: 'data', token: 'doc-one', version: 1, count: 99}, {...trusted, frameId: 1});
state.listeners.status({type: 'status', event: 'data', token: 'doc-one', version: 1, count: 99}, {...trusted, url: 'https://accounts.google.com/'});
state.flush();
assert.equal(state.sent.filter(value => value.type === 'data').at(-1).count, 3);
state.listeners.status({type: 'status', event: 'cosmetic', token: 'doc-one', version: 1, count: 9000}, trusted);
state.listeners.status({type: 'status', event: 'cosmetic', token: 'doc-one', version: 1, count: 9000}, trusted);
state.flush();
assert.equal(state.sent.filter(value => value.type === 'cosmetic').reduce((sum, value) => sum + value.count, 0), 18000);
assert(!state.sent.some(value => 'url' in value || 'script' in value || 'token' in value));

// Updates preserve existing document policy; new documents receive the changed settings.
await state.configure(policy({version: 2, token: 'doc-two', enabled: false}));
assert(state.registrations.slice(0, 2).every(value => value.removed));
assert.deepEqual(plain(request('https://doubleclick.net/ads')), {cancel: true});
state.listeners.request({type: 'main_frame', tabId: 2, url: 'https://www.youtube.com/'});
assert.deepEqual(plain(request('https://doubleclick.net/ads', {tabId: 2})), {});
await state.configure(policy({version: 3, token: 'doc-three', exceptions: ['doubleclick.net']}));
state.listeners.request({type: 'main_frame', tabId: 3, url: 'https://www.youtube.com/'});
assert.deepEqual(plain(request('https://ads.doubleclick.net/ads', {tabId: 3})), {});
assert.deepEqual(plain(request('https://www.youtube.com/pagead/ads', {tabId: 3})), {cancel: true});

let release;
state.hold(new Promise(resolve => { release = resolve; }));
state.listeners.native({type: 'configure', revision: 4, config: policy({version: 4}), script: '/* APK script */'});
await tick();
const duringUpdate = state.listeners.request({type: 'main_frame', tabId: 4, url: 'https://m.youtube.com/'});
assert.equal(typeof duringUpdate.then, 'function');
release(); await tick();
assert.deepEqual(plain(await duringUpdate), {});

const failed = setup(); failed.reject();
const failedLoad = failed.listeners.request({type: 'main_frame', tabId: 7, url: 'https://m.youtube.com/'});
await failed.configure(policy());
assert.deepEqual(plain(await failedLoad), {cancel: true}, 'registration failure must not load an unfiltered page');
assert.equal(failed.sent.at(-1).type, 'configure-error');
failed.allow();
let retryRelease;
failed.hold(new Promise(resolve => { retryRelease = resolve; }));
failed.listeners.native({type: 'configure', revision: 2, config: policy({version: 2}), script: '/* APK script */'});
await tick();
const retryLoad = failed.listeners.request({type: 'main_frame', tabId: 7, url: 'https://m.youtube.com/'});
assert.equal(typeof retryLoad.then, 'function');
retryRelease(); await tick();
assert.deepEqual(plain(await retryLoad), {}, 'a successful retry releases a new readiness gate');

// Isolated bridge accepts only bounded matching events and forwards no other page data.
const events = {};
const messages = [];
const timers = [];
const window = {addEventListener: (name, handler) => { events[name] = handler; },
  dispatchEvent: () => {}, top: null}; window.top = window;
vm.runInNewContext(bridge, {window, document: {addEventListener: () => {}},
  location: {pathname: '/'},
  __weiktubeGeckoPolicy: policy(), CustomEvent: class {constructor(type) { this.type = type; }},
  browser: {runtime: {sendMessage: message => { messages.push(message); return Promise.resolve(); }}},
  setTimeout: callback => { timers.push(callback); }, Date, JSON, Number, Set});
const status = value => events['weiktube-ad-status']({detail: JSON.stringify(value)});
status({type: 'data', token: 'doc-one', version: 1, count: 4, cookie: 'must never leave page'});
status({type: 'data', token: 'wrong', version: 1, count: 100});
status({type: 'openVideo', token: 'doc-one', version: 1, count: 1});
status({type: 'ready', token: 'doc-one', version: 1, count: 0});
timers.forEach(callback => callback());
assert.equal(messages.length, 2);
assert.equal(messages[0].count, 4);
assert(!messages.some(message => 'cookie' in message));
assert(!messages.some(message => message.event === 'error'));
console.log('Gecko adblock extension contracts: PASS (registration/readiness, network scope, policy pinning, update gate, safe telemetry).');
