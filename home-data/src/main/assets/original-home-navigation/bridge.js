/* Public video navigation only. No cookies, tokens, feed/config extraction or network access. */
(() => {
  'use strict';
  const youtubeHosts = ['www.youtube.com', 'm.youtube.com', 'youtube.com'];
  const accountPath = /^\/(?:signin|login|ServiceLogin|account_login|consent|account|account_switcher|channel_switcher|select_site|t\/signin)(?:\/|$)/i;
  const accountPage = () => {
    try { return accountPath.test(decodeURIComponent(location.pathname)); } catch { return true; }
  };
  const guarded = () => location.protocol === 'https:' && youtubeHosts.includes(location.hostname) && !accountPage();
  if (window.top !== window || !guarded() || location.port) return;

  const documentId = crypto.randomUUID();
  let port = null, connected = false, enabled = false, handingOff = false, generation = null, queued = null;
  let gestureUntil = 0, pointer = null, consumedClick = null;
  const reports = new Set(), pendingReports = new Set();
  const validNonce = value => typeof value === 'string' && /^[A-Za-z0-9_-]{1,128}$/.test(value);
  const idPattern = /^[A-Za-z0-9_-]{11}$/;
  const clock = () => performance.now();
  const nativePause = window.HTMLMediaElement?.prototype.pause;
  const nativePush = window.History?.prototype.pushState;
  const nativeReplace = window.History?.prototype.replaceState;
  let safeHref = location.href, safeX = window.scrollX || 0, safeY = window.scrollY || 0;

  function silence(media) {
    if (!guarded()) return;
    try {
      media.muted = true; media.autoplay = false;
      if (nativePause) Reflect.apply(nativePause, media, []); else media.pause();
    } catch { /* Detached media is never a reason for web playback fallback. */ }
  }
  function pauseMedia(root = document) {
    if (!guarded()) return;
    if (root?.tagName === 'VIDEO' || root?.tagName === 'AUDIO') silence(root);
    try { root?.querySelectorAll?.('video,audio').forEach(silence); } catch { /* Removed subtree. */ }
  }
  function send(value) {
    if (!connected || !port) return false;
    try { port.postMessage(value); return true; }
    catch { connected = false; enabled = false; queued = null; pauseMedia(); return false; }
  }
  function blocked(reason) {
    if (reports.has(reason)) return;
    reports.add(reason);
    if (!connected) { pendingReports.add(reason); return; }
    send({ type: 'blocked', documentId, ...(generation ? { generation } : {}), reason });
  }
  function target(raw) {
    if (typeof raw !== 'string') return { kind: 'other' };
    const looksLocal = /^\/(?:watch|shorts|live)(?:[/?#]|$)/i.test(raw);
    let url;
    try { url = new URL(raw, location.href); } catch { return { kind: looksLocal ? 'invalid' : 'other' }; }
    if (!youtubeHosts.includes(url.hostname) && url.hostname !== 'youtu.be') return { kind: 'other' };
    let decoded;
    try { decoded = decodeURIComponent(url.pathname); } catch { decoded = url.pathname; }
    const recognizable = url.hostname === 'youtu.be' || /^\/(?:watch|shorts|live)(?:\/|$)/i.test(decoded);
    if (!recognizable) return { kind: 'other' };
    if (!raw || raw.length > 8192 || /[\x00-\x20\x7f\\]/.test(raw) || url.protocol !== 'https:' || url.username || url.password || url.port)
      return { kind: 'invalid' };
    const parts = url.pathname.slice(1).split('/');
    let id;
    if (url.hostname === 'youtu.be' && parts.length === 1) id = parts[0];
    else if (url.pathname === '/watch' && url.searchParams.getAll('v').length === 1) id = url.searchParams.get('v');
    else if (parts.length === 2 && ['shorts', 'live'].includes(parts[0])) id = parts[1];
    return typeof id === 'string' && idPattern.test(id)
      ? { kind: 'video', url: `https://www.youtube.com/watch?v=${id}` } : { kind: 'invalid' };
  }

  // Only known public navigation properties on the clicked renderer. Skip all accessors.
  function own(object, key) {
    if (!object || (typeof object !== 'object' && typeof object !== 'function')) return undefined;
    try {
      const descriptor = Object.getOwnPropertyDescriptor(object, key);
      return descriptor && Object.prototype.hasOwnProperty.call(descriptor, 'value') ? descriptor.value : undefined;
    } catch { return undefined; }
  }
  function at(object, keys) { for (const key of keys) { object = own(object, key); if (object == null) break; } return object; }
  function endpoint(value) {
    if (!value) return null;
    for (const key of ['watchEndpoint', 'reelWatchEndpoint']) {
      const watch = own(value, key);
      if (watch != null) {
        const id = own(watch, 'videoId');
        return typeof id === 'string' && idPattern.test(id)
          ? { kind: 'video', url: `https://www.youtube.com/watch?v=${id}` } : { kind: 'invalid' };
      }
    }
    const url = at(value, ['commandMetadata', 'webCommandMetadata', 'url']);
    return typeof url === 'string' ? target(url) : null;
  }
  const rendererNames = new Set(['YTM-VIDEO-WITH-CONTEXT-RENDERER', 'YTM-RICH-ITEM-RENDERER', 'YTM-COMPACT-VIDEO-RENDERER',
    'YTM-VIDEO-RENDERER', 'YTM-REEL-ITEM-RENDERER', 'YTD-RICH-ITEM-RENDERER', 'YTD-VIDEO-RENDERER',
    'YTD-COMPACT-VIDEO-RENDERER', 'YTD-REEL-ITEM-RENDERER', 'YT-LOCKUP-VIEW-MODEL', 'YTM-LOCKUP-VIEW-MODEL']);
  function rendererTarget(node) {
    let page;
    try { page = node.wrappedJSObject || node; } catch { return { kind: 'invalid' }; }
    for (const root of [page, own(page, 'data'), at(page, ['__data', 'data'])]) {
      for (const path of [['navigationEndpoint'], ['onTap', 'innertubeCommand'], ['onTap'],
        ['rendererContext', 'commandContext', 'onTap', 'innertubeCommand'], ['rendererContext', 'commandContext', 'onTap'],
        ['videoRenderer', 'navigationEndpoint'], ['compactVideoRenderer', 'navigationEndpoint'],
        ['reelItemRenderer', 'navigationEndpoint'], ['lockupViewModel', 'onTap', 'innertubeCommand']]) {
        const result = endpoint(at(root, path));
        if (result) return result;
      }
    }
    return { kind: 'invalid' };
  }
  function clickedTarget(event) {
    const path = (typeof event.composedPath === 'function' ? event.composedPath() : []).slice(0, 64);
    if (!path.length && event.target) path.push(event.target);
    for (const node of path) {
      const tag = node?.tagName;
      if (tag === 'A' && typeof node.getAttribute === 'function' && node.hasAttribute('href')) {
        if (node.hasAttribute('download')) return { kind: 'other', excluded: true };
        return { ...target(node.getAttribute('href')), excluded: true };
      }
      const role = typeof node?.getAttribute === 'function' ? node.getAttribute('role') : null;
      if (['BUTTON', 'INPUT', 'TEXTAREA', 'SELECT', 'SUMMARY', 'YTM-MENU-RENDERER', 'YTD-MENU-RENDERER',
        'YT-ICON-BUTTON', 'YTM-ICON-BUTTON'].includes(tag) || ['button', 'menuitem', 'checkbox', 'textbox'].includes(role) ||
        node?.isContentEditable === true) return { kind: 'other', excluded: true };
      if (rendererNames.has(tag)) return rendererTarget(node);
    }
    return { kind: 'other', excluded: false };
  }
  function deliver(url) {
    if (handingOff) return;
    pauseMedia();
    if (!connected) { blocked('BRIDGE_UNAVAILABLE'); return; }
    if (!enabled) { if (!queued) queued = url; return; }
    queued = null; handingOff = true; enabled = false; gestureUntil = 0;
    if (!send({ type: 'openVideo', documentId, generation, url })) blocked('BRIDGE_UNAVAILABLE');
  }
  function intercept(result, trusted) {
    if (result.kind === 'invalid') { pauseMedia(); blocked('INVALID_VIDEO'); return; }
    if (handingOff) { pauseMedia(); return; }
    if (!trusted || document.hidden) { pauseMedia(); blocked('UNTRUSTED_NAVIGATION'); return; }
    deliver(result.url);
  }
  function click(event) {
    if (!guarded()) return;
    const result = clickedTarget(event);
    const trusted = event.isTrusted === true && event.button === 0 && !event.ctrlKey && !event.metaKey && !event.altKey && !event.shiftKey;
    if (result.kind === 'other') {
      gestureUntil = trusted && !result.excluded ? clock() + 1500 : 0;
      return;
    }
    // Block before handshake, on invalid IDs, synthetic clicks and connection failure alike.
    event.preventDefault(); event.stopImmediatePropagation(); gestureUntil = 0;
    if (consumedClick && clock() <= consumedClick.until && consumedClick.key === (result.url || result.kind)) {
      consumedClick = null; pauseMedia(); return;
    }
    intercept(result, trusted);
  }
  window.addEventListener('click', click, true);
  window.addEventListener('auxclick', click, true);

  // Some mobile routers navigate on pointerup before click. Do not cancel pointerdown/move:
  // a scrolling gesture must remain a scroll, not an accidental video selection.
  window.addEventListener('pointerdown', event => {
    pointer = null; gestureUntil = 0;
    if (!guarded() || event.isTrusted !== true || event.isPrimary === false || event.button !== 0 ||
        event.ctrlKey || event.metaKey || event.altKey || event.shiftKey) return;
    const result = clickedTarget(event);
    if (result.kind === 'other' && result.excluded) { gestureUntil = 0; return; }
    if (!Number.isFinite(event.clientX) || !Number.isFinite(event.clientY)) return;
    pointer = { id: event.pointerId, x: event.clientX, y: event.clientY, at: clock(), key: result.url || result.kind,
      origin: event.target, moved: false };
  }, true);
  window.addEventListener('pointermove', event => {
    if (pointer && event.pointerId === pointer.id && Math.hypot(event.clientX - pointer.x, event.clientY - pointer.y) > 12)
      pointer.moved = true;
  }, true);
  window.addEventListener('pointercancel', () => { pointer = null; gestureUntil = 0; }, true);
  window.addEventListener('contextmenu', () => { pointer = null; gestureUntil = 0; }, true);
  window.addEventListener('pointerup', event => {
    const down = pointer; pointer = null;
    if (!down || !guarded() || event.isTrusted !== true || event.pointerId !== down.id || down.moved || clock() - down.at > 800 ||
        event.isPrimary === false || event.button !== 0 || event.ctrlKey || event.metaKey || event.altKey || event.shiftKey ||
        !Number.isFinite(event.clientX) || !Number.isFinite(event.clientY) ||
        Math.hypot(event.clientX - down.x, event.clientY - down.y) > 12) return;
    const result = clickedTarget(event);
    if (down.key !== (result.url || result.kind)) return;
    if (result.kind === 'other') {
      if (!result.excluded && down.origin === event.target) gestureUntil = clock() + 1500;
      return;
    }
    event.preventDefault(); event.stopImmediatePropagation(); gestureUntil = 0;
    consumedClick = { key: result.url || result.kind, until: clock() + 800 };
    intercept(result, true);
  }, true);

  // Clean native methods come through Xray. Only URL is inspected; state/title pass untouched.
  try {
    if (typeof exportFunction !== 'function' || !nativePush || !nativeReplace) throw new Error();
    const pageHistoryPrototype = window.wrappedJSObject.History.prototype;
    for (const [name, original] of [['pushState', nativePush], ['replaceState', nativeReplace]]) {
      exportFunction(function (...args) {
        if (guarded()) {
          const result = target(args[2]);
          if (result.kind !== 'other') {
            const trusted = clock() <= gestureUntil && gestureUntil > 0;
            gestureUntil = 0; intercept(result, trusted);
            // Abort an uncaught synchronous router stack before its subsequent DOM replacement.
            // A router that catches this or mutated earlier still needs the primary click guard.
            throw new window.DOMException('Video navigation is handled by Weiktube.', 'AbortError');
          }
        }
        const value = Reflect.apply(original, this, args);
        if (guarded() && target(location.href).kind === 'other') safeHref = location.href;
        return value;
      }, pageHistoryPrototype, { defineAs: name });
    }
  } catch { blocked('HISTORY_GUARD_UNAVAILABLE'); }
  try {
    if (typeof exportFunction !== 'function' || !window.HTMLMediaElement) throw new Error();
    const originalPlay = window.HTMLMediaElement.prototype.play;
    exportFunction(function (...args) {
      if (!guarded()) return Reflect.apply(originalPlay, this, args);
      silence(this);
      return window.Promise.reject(new window.DOMException('Playback is handled by Weiktube.', 'NotAllowedError'));
    }, window.wrappedJSObject.HTMLMediaElement.prototype, { defineAs: 'play' });
  } catch { blocked('MEDIA_GUARD_UNAVAILABLE'); }
  window.addEventListener('play', event => { if (guarded()) silence(event.target); }, true);
  window.addEventListener('playing', event => { if (guarded()) silence(event.target); }, true);
  try {
    const observer = new MutationObserver(records => {
      if (guarded()) for (const record of records) for (const node of record.addedNodes) pauseMedia(node);
    });
    observer.observe(document, { childList: true, subtree: true });
  } catch { blocked('MEDIA_GUARD_UNAVAILABLE'); }
  pauseMedia();

  window.addEventListener('scroll', () => {
    if (guarded() && target(location.href).kind === 'other') { safeX = window.scrollX; safeY = window.scrollY; }
  }, { passive: true });
  window.addEventListener('popstate', event => {
    if (!guarded()) return;
    const result = target(location.href);
    if (result.kind === 'other') { safeHref = location.href; return; }
    event.stopImmediatePropagation();
    const x = safeX, y = safeY;
    try { Reflect.apply(nativeReplace, window.history, [null, '', safeHref]); } catch { blocked('HISTORY_GUARD_UNAVAILABLE'); }
    pauseMedia();
    window.requestAnimationFrame(() => { if (guarded()) window.scrollTo(x, y); });
    // history.go can generate popstate without a video-card tap.
    const trusted = gestureUntil > 0 && clock() <= gestureUntil;
    gestureUntil = 0; intercept(result, trusted);
  }, true);
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) { gestureUntil = 0; pointer = null; queued = null; pauseMedia(); }
  });
  window.addEventListener('pagehide', () => { enabled = false; queued = null; pointer = null; gestureUntil = 0; pauseMedia(); });

  try {
    port = browser.runtime.connectNative('weiktubeOriginalHome'); connected = true;
    port.onDisconnect.addListener(() => {
      connected = false; enabled = false; queued = null; pointer = null; gestureUntil = 0;
      pauseMedia(); blocked('BRIDGE_UNAVAILABLE');
    });
    port.onMessage.addListener(message => {
      if (!connected || !message || message.documentId !== documentId) return;
      if (message.type === 'disable') { enabled = false; queued = null; pointer = null; gestureUntil = 0; pauseMedia(); return; }
      if (message.type === 'pause') { pauseMedia(); return; }
      if (message.type !== 'enable' || !validNonce(message.generation) || !guarded()) return;
      if (message.generation === generation) return; // Return uses a fresh nonce, not duplicate permission.
      generation = message.generation; enabled = true; handingOff = false; reports.clear();
      if (!send({ type: 'enabled', documentId, generation })) return;
      const next = queued; queued = null;
      if (next && !document.hidden) deliver(next);
    });
    send({ type: 'ready', documentId });
    for (const reason of pendingReports) send({ type: 'blocked', documentId, ...(generation ? { generation } : {}), reason });
    pendingReports.clear();
  } catch { connected = false; enabled = false; queued = null; pauseMedia(); }
})();
