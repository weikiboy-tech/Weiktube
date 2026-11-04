/* ISOLATED world. Page messages can report bounded counters, never request native actions. */
(() => {
  'use strict';
  if (window !== window.top || globalThis.__weiktubeGeckoAdsBridge) return;
  if (/^\/(?:signin|login|logout|account)(?:[/?_]|$)|^\/(?:o\/oauth|ServiceLogin)/i.test(location.pathname)) return;
  globalThis.__weiktubeGeckoAdsBridge = true;
  const policy = globalThis.__weiktubeGeckoPolicy;
  if (!policy || typeof policy.token !== 'string') return;
  const events = new Set(['ready', 'data', 'cosmetic', 'breakthrough', 'error']);
  let ready = false;
  let windowStart = Date.now();
  let received = 0;
  window.addEventListener('weiktube-ad-status', event => {
    if (typeof event.detail !== 'string' || event.detail.length > 2048) return;
    if (Date.now() - windowStart > 1000) { windowStart = Date.now(); received = 0; }
    if (++received > 100) return;
    try {
      const value = JSON.parse(event.detail);
      if (value.token !== policy.token || value.version !== policy.version || !events.has(value.type)) return;
      if (value.type === 'ready') ready = true;
      const count = Number.isInteger(value.count) && value.count >= 1 && value.count <= 10000 ? value.count : 0;
      browser.runtime.sendMessage({type: 'status', event: value.type, token: policy.token,
        version: policy.version, count}).catch(() => {});
    } catch (_) { /* Untrusted webpage data is discarded without logging it. */ }
  }, true);
  const request = () => window.dispatchEvent(new CustomEvent('weiktube-ad-status-request'));
  request();
  document.addEventListener('DOMContentLoaded', request, {once: true});
  setTimeout(() => {
    if (!ready) browser.runtime.sendMessage({type: 'status', event: 'error', token: policy.token,
      version: policy.version, count: 0}).catch(() => {});
  }, 5000);
})();
