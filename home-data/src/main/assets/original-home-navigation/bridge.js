/* Public-link handoff only. No account, cookie, page config or feed data access. */
(() => {
  'use strict';
  const accountPath = /^\/(?:signin|login|ServiceLogin|account_login|consent|account|account_switcher|channel_switcher|select_site|t\/signin)(?:\/|$)/i;
  const accountPage = () => {
    try { return accountPath.test(decodeURIComponent(location.pathname)); } catch { return true; }
  };
  if (window.top !== window || !['https://www.youtube.com', 'https://m.youtube.com'].includes(location.origin) || accountPage()) return;

  const documentId = crypto.randomUUID();
  const port = browser.runtime.connectNative('weiktubeOriginalHome');
  let connected = true, enabled = false, handingOff = false, generation = null;
  const validNonce = value => typeof value === 'string' && /^[A-Za-z0-9_-]{1,128}$/.test(value);
  const pauseMedia = () => document.querySelectorAll('video,audio').forEach(media => media.pause());
  const disable = () => { enabled = false; handingOff = false; generation = null; pauseMedia(); };

  function videoUrl(raw) {
    if (typeof raw !== 'string' || raw.length < 1 || raw.length > 8192 || /[\x00-\x20\x7f\\]/.test(raw)) return null;
    let url;
    try { url = new URL(raw, location.href); } catch { return null; }
    const youtube = ['www.youtube.com', 'm.youtube.com', 'youtube.com'].includes(url.hostname);
    if (url.protocol !== 'https:' || url.username || url.password || url.port || (!youtube && url.hostname !== 'youtu.be')) return null;
    const parts = url.pathname.slice(1).split('/');
    let id = null;
    if (url.hostname === 'youtu.be' && parts.length === 1) id = parts[0];
    else if (youtube && url.pathname === '/watch' && url.searchParams.getAll('v').length === 1) id = url.searchParams.get('v');
    else if (youtube && parts.length === 2 && ['shorts', 'live'].includes(parts[0])) id = parts[1];
    return /^[A-Za-z0-9_-]{11}$/.test(id || '') ? `https://www.youtube.com/watch?v=${id}` : null;
  }

  function click(event) {
    if (!connected || (!enabled && !handingOff) || event.isTrusted !== true || event.defaultPrevented ||
        event.button !== 0 || event.ctrlKey || event.metaKey || event.altKey || event.shiftKey ||
        accountPage()) return;
    const path = typeof event.composedPath === 'function' ? event.composedPath() : [];
    const anchor = path.find(node => node?.tagName === 'A' && typeof node.getAttribute === 'function') || event.target?.closest?.('a[href]');
    if (!anchor || anchor.hasAttribute('download')) return;
    const url = videoUrl(anchor.getAttribute('href'));
    if (!url) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    pauseMedia();
    if (handingOff) return; // Suppress a rapid second tap while native opens the player.
    enabled = false; handingOff = true;
    port.postMessage({ type: 'openVideo', documentId, generation, url });
  }

  // Capture before ordinary document/bubble handlers; no polling or page script injection.
  window.addEventListener('click', click, true);
  document.addEventListener('visibilitychange', () => { if (document.hidden) pauseMedia(); });
  window.addEventListener('pagehide', disable);
  port.onDisconnect.addListener(() => { connected = false; disable(); });
  port.onMessage.addListener(message => {
    if (!connected || !message || message.documentId !== documentId) return;
    if (message.type === 'disable') { disable(); return; }
    if (message.type === 'pause') { pauseMedia(); return; }
    if (message.type !== 'enable' || !validNonce(message.generation) || accountPage()) return;
    generation = message.generation; enabled = true; handingOff = false;
    port.postMessage({ type: 'enabled', documentId, generation });
  });
  port.postMessage({ type: 'ready', documentId });
})();
