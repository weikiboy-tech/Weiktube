/* SPDX-License-Identifier: GPL-3.0-only */
(function () {
  'use strict';
  var config = window.__weiktubeAdConfig;
  if (window.WeiktubeAdsRuntime) return;
  if (window.top !== window || location.protocol !== 'https:' || (location.port && location.port !== '443') ||
      !/^(?:m\.|www\.)?youtube\.com$/.test(location.hostname)) return;

  var version = config && Number.isSafeInteger(config.version) ? config.version : 0;
  var token = config && typeof config.token === 'string' ? config.token : '';
  var serialize = JSON.stringify.bind(JSON);
  var counts = {data: 0, cosmetic: 0, breakthrough: 0, error: 0};
  var pending = {data: 0, cosmetic: 0};
  var eventTimer = null;
  var errorSent = false;
  function emit(type, count, wasPlaying) {
    var payload = {token: token, type: type, version: version, count: Math.min(10000, count || 0)};
    if (type === 'breakthrough') payload.wasPlaying = wasPlaying === true;
    var message = serialize(payload);
    try {
      if (window.WeiktubeAdEvents && typeof window.WeiktubeAdEvents.postMessage === 'function') {
        window.WeiktubeAdEvents.postMessage(message);
      }
      window.dispatchEvent(new CustomEvent('weiktube-ad-status', {detail: message}));
    } catch (_) { /* Native bridge may detach while the tab is hidden. */ }
  }
  function fail() {
    if (errorSent) return;
    errorSent = true;
    counts.error++;
    emit('error', 1);
  }
  function add(type, count) {
    counts[type] = Math.min(1000000, counts[type] + count);
    pending[type] = Math.min(10000, pending[type] + count);
    if (eventTimer !== null) return;
    eventTimer = setTimeout(function () {
      eventTimer = null;
      ['data', 'cosmetic'].forEach(function (key) {
        if (pending[key]) emit(key, pending[key]);
        pending[key] = 0;
      });
    }, 250);
  }
  var excluded = !!(config && Array.isArray(config.exceptions) && config.exceptions.some(function (entry) {
    return typeof entry === 'string' && (location.hostname === entry || location.hostname.endsWith('.' + entry));
  }));
  var authPath = /^\/(?:signin|login|logout|account)(?:[/?_]|$)/i.test(location.pathname) ||
    /^\/(?:o\/oauth|ServiceLogin)/i.test(location.pathname);
  var enabled = !!(config && config.enabled && !excluded && !authPath);
  var ready = false;
  var runtime = {
    get ready() { return ready; },
    version: version,
    get enabled() { return enabled; },
    // Scriptlet property traps cannot be removed safely. Native code creates a new document.
    setEnabled: function (value) { return {reloadRequired: !!value !== enabled}; },
    inspect: function () {
      return {ready: ready, version: version, enabled: enabled, data: counts.data,
        cosmetic: counts.cosmetic, breakthrough: counts.breakthrough, errors: counts.error};
    }
  };
  Object.defineProperty(window, 'WeiktubeAdsRuntime', {value: Object.freeze(runtime), configurable: false});
  var lastStatusRequest = 0;
  window.addEventListener('weiktube-ad-status-request', function () {
    if (Date.now() - lastStatusRequest < 1000) return;
    lastStatusRequest = Date.now();
    emit(ready ? 'ready' : 'error', 0);
  });
  if (!config || !token || !version || !Array.isArray(config.scriptlets) || !Array.isArray(config.cosmeticSelectors)) {
    fail(); return;
  }
  if (!enabled) { ready = true; emit('ready', 0); return; }

  var allowed = ['set-constant', 'json-prune', 'json-prune-fetch-response', 'json-prune-xhr-response'];
  try {
    if (!window.WeiktubeAdguardScriptlets || typeof Response !== 'function' || typeof Request !== 'function' ||
        typeof Proxy !== 'function' || typeof MutationObserver !== 'function' ||
        typeof fetch !== 'function' || typeof XMLHttpRequest !== 'function') throw new Error('Unsupported hooks');
    config.scriptlets.forEach(function (rule) {
      if (!rule || allowed.indexOf(rule.name) < 0 || !Array.isArray(rule.args) || !rule.args.length ||
          !rule.args.every(function (arg) { return typeof arg === 'string'; })) throw new Error('Unsupported rule');
      var before = {parse: JSON.parse, responseJson: Response.prototype.json, fetch: window.fetch,
        xhrOpen: XMLHttpRequest.prototype.open, xhrSend: XMLHttpRequest.prototype.send};
      window.WeiktubeAdguardScriptlets[rule.name]({
        name: rule.name, args: rule.args, verbose: false, uniqueId: 'weiktube-ads-' + version,
        onPrune: function (count) { add('data', count); }
      }, rule.args);
      if (rule.name === 'json-prune' && (JSON.parse === before.parse || Response.prototype.json === before.responseJson) ||
          rule.name === 'json-prune-fetch-response' && window.fetch === before.fetch ||
          rule.name === 'json-prune-xhr-response' && (XMLHttpRequest.prototype.open === before.xhrOpen || XMLHttpRequest.prototype.send === before.xhrSend)) {
        throw new Error('Hook was not installed');
      }
      if (rule.name === 'set-constant') {
        var path = rule.args[0].split('.'), base = window, descriptor;
        for (var index = 0; index < path.length; index++) {
          descriptor = Object.getOwnPropertyDescriptor(base, path[index]);
          if (index === path.length - 1 || base[path[index]] == null) break;
          base = base[path[index]];
        }
        if (!descriptor || typeof descriptor.get !== 'function' || typeof descriptor.set !== 'function') throw new Error('Constant trap unavailable');
      }
    });
  } catch (_) { fail(); return; }

  var selectors = config.cosmeticSelectors.filter(function (selector) {
    // Filters may update the list, but cannot inject CSS or hide unrelated containers.
    return typeof selector === 'string' && /^(?:ytd|ytm)-(?:ad-slot|display-ad|in-feed-ad-layout|promoted-video|promoted-sparkles-web|promoted-sparkles-text-search|search-pyv)-renderer$/.test(selector);
  });
  var selectorText = selectors.join(',');
  var seenCosmetic = new WeakSet();
  var protectedPlayers = new Map();
  var cosmeticStyle;
  var scheduled = false;
  var lastBreakthrough = 0;

  function visiblyPresent(element) {
    if (!element || !element.isConnected || element.hidden) return false;
    var style = getComputedStyle(element);
    return style.display !== 'none' && style.visibility !== 'hidden' && element.getClientRects().length > 0;
  }
  function isAd(player) {
    if (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting')) return true;
    return Array.from(player.querySelectorAll('.ytp-ad-preview-container,.ytp-ad-skip-button-container,.ytp-ad-player-overlay-instream-info')).some(visiblyPresent);
  }
  function protect(player) {
    var state = protectedPlayers.get(player);
    if (!state) {
      var wasPlaying = Array.from(player.querySelectorAll('video')).some(function (video) { return !video.paused; });
      state = {videos: new Map(), notice: null, position: player.style.position};
      protectedPlayers.set(player, state);
      if (getComputedStyle(player).position === 'static') player.style.position = 'relative';
      var notice = document.createElement('div');
      notice.setAttribute('data-weiktube-ad-notice', '');
      notice.setAttribute('role', 'status');
      notice.textContent = 'Werbung erkannt. Geschützte Wiedergabe wird vorbereitet …';
      notice.style.cssText = 'position:absolute;inset:0;z-index:2147483646;display:flex;align-items:center;justify-content:center;background:#0f0f0f;color:#eee;padding:20px;box-sizing:border-box;text-align:center;font:14px sans-serif;';
      player.appendChild(notice);
      state.notice = notice;
      counts.breakthrough++;
      // Bound page-to-native traffic even if the website repeatedly toggles ad state.
      if (Date.now() - lastBreakthrough >= 1000) { lastBreakthrough = Date.now(); emit('breakthrough', 1, wasPlaying); }
    }
    player.querySelectorAll('video').forEach(function (video) {
      if (!state.videos.has(video)) state.videos.set(video, {muted: video.muted, visibility: video.style.visibility});
      video.muted = true;
      video.style.visibility = 'hidden';
      if (!video.paused) video.pause();
    });
  }
  function release(player, state) {
    state.videos.forEach(function (previous, video) {
      video.muted = previous.muted;
      video.style.visibility = previous.visibility;
      // Never call play(): resuming content belongs to the existing native player/media guard.
    });
    if (state.notice) state.notice.remove();
    player.style.position = state.position;
    protectedPlayers.delete(player);
  }
  function scan() {
    scheduled = false;
    if (!document.documentElement) return;
    if (!cosmeticStyle || !cosmeticStyle.isConnected) {
      cosmeticStyle = document.createElement('style');
      cosmeticStyle.setAttribute('data-weiktube-ad-style', '');
      cosmeticStyle.textContent = selectorText ? selectorText + '{display:none!important}' : '';
      document.documentElement.appendChild(cosmeticStyle);
    }
    if (selectorText) document.querySelectorAll(selectorText).forEach(function (element) {
      if (!seenCosmetic.has(element)) { seenCosmetic.add(element); add('cosmetic', 1); }
    });
    document.querySelectorAll('.html5-video-player,#movie_player').forEach(function (player) {
      if (isAd(player)) protect(player);
    });
    protectedPlayers.forEach(function (state, player) {
      if (!player.isConnected || !isAd(player)) release(player, state);
    });
  }
  function scheduleScan() {
    if (scheduled) return;
    scheduled = true;
    queueMicrotask(function () { try { scan(); } catch (_) { fail(); } });
  }
  try {
    new MutationObserver(scheduleScan).observe(document, {subtree: true, childList: true, attributes: true,
      attributeFilter: ['class', 'hidden']});
    ['play', 'playing', 'loadedmetadata'].forEach(function (name) {
      document.addEventListener(name, function (event) {
        var player = event.target && event.target.closest && event.target.closest('.html5-video-player,#movie_player');
        if (player && isAd(player)) protect(player);
      }, true);
    });
    document.addEventListener('yt-navigate-finish', scheduleScan, true);
    window.addEventListener('pageshow', scheduleScan, true);
    scan();
    ready = true;
    emit('ready', 0);
  } catch (_) { fail(); }
})();
