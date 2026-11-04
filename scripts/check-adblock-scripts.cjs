/* SPDX-License-Identifier: GPL-3.0-only */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const {execFileSync} = require('node:child_process');
const root = path.join(__dirname, '..');
const assets = path.join(root, 'content-filter/src/main/assets/weiktube-adblock');
const vendor = fs.readFileSync(path.join(assets, 'vendor-scriptlets.js'), 'utf8');
const runtime = fs.readFileSync(path.join(assets, 'filter-runtime.js'), 'utf8');
const rules = JSON.parse(fs.readFileSync(path.join(assets, 'rules.json'), 'utf8'));
new vm.Script(vendor); new vm.Script(runtime);
execFileSync(process.execPath, [path.join(__dirname, 'vendor-adblock.mjs'), '--check'], {stdio: 'inherit'});

function fixture(options = {}) {
  const events = [], consoleCalls = [], requests = [], timers = [], microtasks = [], observers = [];
  let pauses = 0, plays = 0, seeks = 0;
  class Target {
    constructor() { this.listeners = {}; }
    addEventListener(name, fn) { (this.listeners[name] ||= []).push(fn); }
    dispatchEvent(event) { for (const fn of this.listeners[event.type] || []) fn(event); return true; }
  }
  const windowEvents = new Target();
  class Element extends Target {
    constructor(tag) {
      super(); this.tagName = tag.toLowerCase(); this.children = []; this.parentElement = null;
      this.style = {position: '', visibility: '', display: ''}; this.attributes = {}; this.hidden = false;
      this.classes = new Set();
      this.classList = {contains: value => this.classes.has(value), add: value => this.classes.add(value), remove: value => this.classes.delete(value)};
      this.muted = false; this.paused = false;
    }
    get isConnected() { return this === document.documentElement || !!this.parentElement?.isConnected; }
    setAttribute(key, value) { this.attributes[key] = value; }
    appendChild(node) { node.parentElement = this; this.children.push(node); return node; }
    remove() { if (this.parentElement) this.parentElement.children = this.parentElement.children.filter(node => node !== this); this.parentElement = null; }
    matches(selector) {
      return selector.split(',').some(part => {
        part = part.trim();
        if (part.startsWith('.')) return this.classList.contains(part.slice(1));
        if (part.startsWith('#')) return this.id === part.slice(1);
        return this.tagName === part;
      });
    }
    closest(selector) { return this.matches(selector) ? this : this.parentElement?.closest(selector); }
    querySelectorAll(selector) {
      const result = [];
      const walk = node => { for (const child of node.children) { if (child.matches(selector)) result.push(child); walk(child); } };
      walk(this); return result;
    }
    getClientRects() { return this.hidden || this.style.display === 'none' ? [] : [{}]; }
    pause() { pauses++; this.paused = true; }
    play() { plays++; this.paused = false; return Promise.resolve(); }
    set currentTime(_) { seeks++; }
    get currentTime() { return 0; }
  }
  const document = new Target();
  document.documentElement = new Element('html');
  document.createElement = tag => new Element(tag);
  document.querySelectorAll = selector => document.documentElement.querySelectorAll(selector);
  const data = {adPlacements: [{slot: 'ad'}], playerAds: [{ad: true}], adSlots: [{slot: 1}],
    videoDetails: {videoId: 'abcdefghijk', title: 'Adventures with a creator', author: 'Creator'},
    streamingData: {adaptiveFormats: [{url: 'https://fixture.googlevideo.com/content', mimeType: 'video/mp4'}], serverAbrStreamingUrl: 'fixture'},
    captions: {playerCaptionsTracklistRenderer: {captionTracks: [{languageCode: 'de'}]}}};
  let responseBody = JSON.stringify(data);
  class FixtureResponse {
    constructor(body, init = {}) {
      this.bodyValue = body; this.status = init.status ?? 200; this.statusText = init.statusText ?? 'OK';
      this.headers = init.headers ?? {fixture: 'safe'}; this.url = init.url ?? ''; this.type = 'basic'; this.ok = this.status >= 200 && this.status < 300;
    }
    clone() { return new FixtureResponse(this.bodyValue, this); }
    text() { return Promise.resolve(this.bodyValue); }
    json() { return Promise.resolve().then(() => JSON.parse(this.bodyValue)); }
  }
  class FixtureRequest { constructor(url, init = {}) { this.url = url; Object.assign(this, init); } clone() { return new FixtureRequest(this.url, this); } }
  class Xhr extends Target {
    constructor() { super(); this.withCredentials = false; this.responseType = ''; this.headers = {}; this.readyState = 0; }
    open(method, url, async = true) { this.method = method; this.url = url; this.async = async; this.readyState = 1; }
    setRequestHeader(key, value) { this.headers[key] = value; }
    send(body) {
      requests.push({kind: 'xhr', url: this.url, method: this.method, body, headers: this.headers, credentials: this.withCredentials});
      this.responseText = responseBody; this.response = responseBody; this.responseURL = new URL(this.url, 'https://m.youtube.com').href;
      this.responseXML = null; this.status = 200; this.statusText = 'OK'; this.readyState = 4;
      this.dispatchEvent({type: 'readystatechange'}); this.dispatchEvent({type: 'load'});
    }
  }
  const url = new URL(options.url || 'https://m.youtube.com/');
  const bindings = {
    document, location: url, URL, Response: FixtureResponse, Request: FixtureRequest, XMLHttpRequest: Xhr,
    TextEncoder, Blob, Event: class { constructor(type) { this.type = type; } },
    CustomEvent: class { constructor(type, init) { this.type = type; this.detail = init.detail; } },
    getComputedStyle: node => ({display: node.style.display || 'block', visibility: node.style.visibility || 'visible', position: node.style.position || 'static'}),
    MutationObserver: class { constructor(fn) { this.fn = fn; observers.push(this); } observe() {} },
    setTimeout: fn => { timers.push(fn); return timers.length; }, queueMicrotask: fn => microtasks.push(fn),
    addEventListener: windowEvents.addEventListener.bind(windowEvents), dispatchEvent: windowEvents.dispatchEvent.bind(windowEvents),
    fetch: async (...args) => {
      requests.push({kind: 'fetch', args});
      return new FixtureResponse(responseBody, {url: String(args[0]?.url || args[0]), status: 206, headers: {fixture: 'preserved'}});
    },
    console: {log: (...args) => consoleCalls.push(args), error: (...args) => consoleCalls.push(args), trace: (...args) => consoleCalls.push(args)},
    WeiktubeAdEvents: {postMessage: message => events.push(JSON.parse(message))},
    __weiktubeAdConfig: {...rules, token: 'fixture-token', enabled: options.enabled !== false, exceptions: options.exceptions || []},
  };
  // A contextified global converts existing accessor descriptors to data descriptors;
  // real browser Window does not. Use Node >=22's ordinary global for property-trap tests.
  const context = vm.createContext(vm.constants.DONT_CONTEXTIFY);
  Object.assign(context, bindings);
  context.window = context; context.top = options.frame ? {} : context;
  vm.runInContext('function Window() {}', context);
  if (options.configMissing) delete context.__weiktubeAdConfig;
  if (options.rules) context.__weiktubeAdConfig.scriptlets = options.rules;
  if (options.unsupported) context.Response = undefined;
  vm.runInContext(options.vendor || vendor, context);
  const initialFetch = context.fetch;
  vm.runInContext(runtime, context);
  const flush = () => { let guard = 0; while (microtasks.length || timers.length) { if (++guard > 100) throw new Error('Observer/timer loop'); while (microtasks.length) microtasks.shift()(); while (timers.length) timers.shift()(); } };
  return {context, data, events, requests, consoleCalls, observers, document, Element, initialFetch,
    setBody: value => { responseBody = typeof value === 'string' ? value : JSON.stringify(value); },
    flush, notify: () => { observers.forEach(observer => observer.fn([])); flush(); },
    counts: () => ({pauses, plays, seeks}), run: source => vm.runInContext(source, context)};
}

async function main() {
  const active = fixture();
  assert.equal(active.context.WeiktubeAdsRuntime.ready, true, 'all real vendored hooks install');
  assert.equal(active.events.filter(event => event.type === 'ready').length, 1);
  assert.notEqual(active.context.fetch, active.initialFetch);
  active.run('ytInitialPlayerResponse = {adPlacements:[1],playerAds:[2],adSlots:[3],videoDetails:{videoId:"abcdefghijk"},streamingData:{formats:[1]}}');
  assert.equal(active.context.ytInitialPlayerResponse.adPlacements, undefined);
  assert.equal(active.context.ytInitialPlayerResponse.playerAds, undefined);
  assert.equal(active.context.ytInitialPlayerResponse.adSlots, undefined);
  assert.equal(active.context.ytInitialPlayerResponse.videoDetails.videoId, 'abcdefghijk');
  active.run('ytInitialPlayerResponse = {adPlacements:[4],playerAds:[5],adSlots:[6],videoDetails:{videoId:"lmnopqrstuv"}}');
  assert.equal(active.context.ytInitialPlayerResponse.adSlots, undefined, 'initial-global reassignment remains filtered');
  active.context.fixtureText = JSON.stringify({playerResponse: active.data, keep: 'safe'});
  const parsed = active.run('JSON.parse(fixtureText)');
  assert.equal(parsed.playerResponse.adSlots, undefined);
  assert.equal(parsed.playerResponse.videoDetails.videoId, active.data.videoDetails.videoId);
  assert.equal(parsed.keep, 'safe');
  assert.equal(parsed.playerResponse.streamingData.adaptiveFormats[0].mimeType, 'video/mp4');
  const stable = active.context.WeiktubeAdsRuntime.inspect().data;
  active.context.fixtureText = JSON.stringify({videoDetails: {title: 'Adventures', videoId: 'abcdefghijk'}, ads: {creatorSponsor: true}});
  const innocent = active.run('JSON.parse(fixtureText)');
  assert.equal(innocent.ads.creatorSponsor, true);
  assert.equal(active.context.WeiktubeAdsRuntime.inspect().data, stable, 'unchanged responses do not increment counters');

  for (const endpoint of ['https://m.youtube.com/youtubei/v1/player?key=fixture', '/youtubei/v1/player',
    'https://www.youtube.com/watch?v=abcdefghijk', '/get_watch?v=abcdefghijk']) {
    const response = await active.context.fetch(endpoint, {credentials: 'include', method: 'POST', body: 'fixture-body'});
    const result = JSON.parse(await response.text());
    assert.equal(result.adSlots, undefined, `actual fetch response pruning: ${endpoint}`);
    assert.deepEqual(result.videoDetails, active.data.videoDetails);
    assert.deepEqual(result.streamingData, active.data.streamingData);
    assert.deepEqual(result.captions, active.data.captions);
    assert.equal(response.status, 206); assert.equal(response.headers.fixture, 'preserved');
    const request = active.requests.at(-1);
    assert.equal(request.args[1].credentials, 'include'); assert.equal(request.args[1].body, 'fixture-body');
  }
  // A fetch-only fixture avoids the independent Response.json hook when testing URL boundaries.
  const fetchOnly = fixture({rules: rules.scriptlets.filter(rule => rule.name === 'json-prune-fetch-response')});
  for (const endpoint of ['https://m.youtube.com/youtubei/v1/player/get_drm_license',
    'https://accounts.google.com/SignIn', 'https://example.org/youtubei/v1/player', 'https://fixture.googlevideo.com/videoplayback']) {
    const result = JSON.parse(await (await fetchOnly.context.fetch(endpoint)).text());
    assert.deepEqual(result.adSlots, fetchOnly.data.adSlots, `unrelated/DRM response preserved: ${endpoint}`);
  }
  fetchOnly.setBody('<html>consent page</html>');
  assert.equal(await (await fetchOnly.context.fetch('https://m.youtube.com/youtubei/v1/player')).text(), '<html>consent page</html>');

  for (const endpoint of ['https://m.youtube.com/youtubei/v1/player?key=fixture', '/get_watch?v=abcdefghijk']) {
    const xhr = new active.context.XMLHttpRequest();
    xhr.withCredentials = true; xhr.open('POST', endpoint, true); xhr.setRequestHeader('X-Fixture', 'preserved'); xhr.send('request-body'); active.flush();
    const result = JSON.parse(xhr.responseText);
    assert.equal(result.adPlacements, undefined, `actual XHR response pruning: ${endpoint}`);
    assert.deepEqual(result.streamingData, active.data.streamingData);
    assert.equal(xhr.status, 200); assert.equal(active.requests.at(-1).credentials, true);
    assert.equal(active.requests.at(-1).headers['X-Fixture'], 'preserved');
  }

  const observerCount = active.observers.length;
  const previousFetch = active.context.fetch;
  active.run(runtime); active.run(vendor);
  assert.equal(active.context.fetch, previousFetch); assert.equal(active.observers.length, observerCount);
  assert.equal(active.events.filter(event => event.type === 'ready').length, 1, 'idempotent bootstrap');

  const player = active.document.documentElement.appendChild(new active.Element('div'));
  player.classList.add('html5-video-player');
  const video = player.appendChild(new active.Element('video'));
  const ordinary = active.document.documentElement.appendChild(new active.Element('ytm-rich-item-renderer'));
  ordinary.textContent = 'Adventures in advertising — creator content';
  active.notify(); assert.equal(active.counts().pauses, 0);
  const hiddenMarker = player.appendChild(new active.Element('div'));
  hiddenMarker.classList.add('ytp-ad-preview-container'); hiddenMarker.style.display = 'none';
  active.notify(); assert.equal(active.counts().pauses, 0, 'hidden ad UI template is not an active ad');

  for (let index = 0; index < 10; index++) {
    const card = active.document.documentElement.appendChild(new active.Element('ytm-ad-slot-renderer'));
    active.document.dispatchEvent({type: 'yt-navigate-finish'}); active.notify();
    assert.equal(active.context.WeiktubeAdsRuntime.inspect().cosmetic, index + 1);
    active.notify(); assert.equal(active.context.WeiktubeAdsRuntime.inspect().cosmetic, index + 1, 'same card counted once');
    player.classList.add('ad-showing'); video.paused = false;
    active.document.dispatchEvent({type: 'play', target: video}); active.notify();
    assert.equal(video.paused, true); assert.equal(video.muted, true); assert.equal(video.style.visibility, 'hidden');
    assert.equal(player.children.filter(node => 'data-weiktube-ad-notice' in node.attributes).length, 1, 'one scoped notice');
    assert.equal(active.context.WeiktubeAdsRuntime.inspect().breakthrough, index + 1);
    player.classList.remove('ad-showing'); active.notify();
    assert.equal(video.muted, false); assert.equal(video.style.visibility, '');
    assert.equal(player.children.filter(node => 'data-weiktube-ad-notice' in node.attributes).length, 0);
    assert.equal(ordinary.isConnected, true); card.remove();
  }
  assert.equal(active.counts().pauses, 10); assert.equal(active.counts().plays, 0); assert.equal(active.counts().seeks, 0);
  active.flush();
  assert.equal(active.context.WeiktubeAdsRuntime.inspect().errors, 0);
  for (const event of active.events) {
    assert.deepEqual(Object.keys(event).sort(), event.type === 'breakthrough' ? ['count', 'token', 'type', 'version', 'wasPlaying'] : ['count', 'token', 'type', 'version']);
    assert.equal(event.token, 'fixture-token'); assert.ok(event.count >= 0 && event.count <= 10000);
  }
  assert.equal(active.events.find(event => event.type === 'breakthrough').wasPlaying, true, 'reports play intent before guard pause');
  const userPaused = fixture();
  const pausedPlayer = userPaused.document.documentElement.appendChild(new userPaused.Element('div'));
  pausedPlayer.classList.add('html5-video-player'); pausedPlayer.classList.add('ad-showing');
  const pausedVideo = pausedPlayer.appendChild(new userPaused.Element('video')); pausedVideo.paused = true;
  userPaused.notify();
  assert.equal(userPaused.events.find(event => event.type === 'breakthrough').wasPlaying, false, 'does not claim a user-paused video was playing');
  assert.equal(userPaused.counts().plays, 0);
  assert.deepEqual(active.consoleCalls, [], 'no response/auth/error detail logging');
  assert.equal(active.context.WeiktubeAdsRuntime.setEnabled(false).reloadRequired, true);
  assert.equal(active.context.WeiktubeAdsRuntime.enabled, true, 'does not falsely claim immutable hooks were disabled');
  assert.equal(active.context.WeiktubeAdsRuntime.setEnabled(true).reloadRequired, false);

  for (const options of [{enabled: false}, {exceptions: ['youtube.com']}, {exceptions: ['m.youtube.com']},
    ...['signin', 'account_login', 'logout', 'login', 'o/oauth2/v2/auth', 'ServiceLogin'].map(route => ({url: 'https://m.youtube.com/' + route}))]) {
    const excluded = fixture(options);
    assert.equal(excluded.context.WeiktubeAdsRuntime.enabled, false);
    assert.equal(excluded.context.fetch, excluded.initialFetch);
    assert.equal(excluded.observers.length, 0);
  }
  for (const options of [{url: 'https://accounts.google.com/'}, {url: 'https://youtube.com.evil.org/'}, {url: 'http://m.youtube.com/'}, {url: 'https://m.youtube.com:8443/'}, {frame: true}]) {
    const excluded = fixture(options);
    assert.equal(excluded.context.WeiktubeAdsRuntime, undefined);
    assert.equal(excluded.context.fetch, excluded.initialFetch);
    assert.equal(excluded.events.length, 0);
  }
  for (const options of [{configMissing: true}, {unsupported: true}, {rules: [{name: 'unknown', args: ['x']}]}]) {
    const failed = fixture(options);
    assert.equal(failed.context.WeiktubeAdsRuntime.ready, false);
    assert.equal(failed.events.filter(event => event.type === 'ready').length, 0);
    assert.equal(failed.events.filter(event => event.type === 'error').length, 1);
    assert.deepEqual(failed.consoleCalls, []);
  }
  active.context.dispatchEvent({type: 'weiktube-ad-status-request'});
  assert.equal(active.events.at(-1).type, 'ready', 'isolated Gecko bridge can request aggregate readiness');
  console.log('PASS adblock: pinned upstream hooks; initial globals; real JSON/fetch/XHR filtering and metadata/credentials preservation; DRM/foreign/CDN exclusions; 10 SPA/ad-card/player transitions; muted pause guard, no seek/play; honest counters; idempotence; unsupported frame/origin/config failure; private bounded telemetry. No device tests performed.');
}
module.exports = {fixture};
if (require.main === module) main().catch(error => { console.error(error); process.exitCode = 1; });
