/* App-owned session only. Never executes on Google login pages or exports cookies.
 * Home and continuation endpoint shapes: github.com/LuanRT/YouTube.js.
 * This is a feasibility bridge, not a Google-supported authentication mechanism.
 */
(() => {
  'use strict';
  if (window.top !== window || location.protocol !== 'https:' ||
      !['www.youtube.com', 'm.youtube.com'].includes(location.hostname)) return;

  const MAX_BYTES = 4 * 1024 * 1024;
  const channelPattern = /^UC[A-Za-z0-9_-]{22}$/;
  // Firefox MV2's global fetch uses extension privileges/origin. The browser's
  // Xray-protected content.fetch is the documented page-origin transport:
  // developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/Content_scripts#xhr_and_fetch
  // Do not use wrappedJSObject.fetch (the page could replace it), or fall back to
  // extension fetch: either would invalidate the same-session proof.
  const pageFetch = typeof content !== 'undefined' && typeof content.fetch === 'function'
    ? content.fetch.bind(content) : null;
  const port = browser.runtime.connectNative('weiktubeHome');
  let connected = true, generation = 0, pending = null, verifiedChannel = null;
  let continuations = new Set();
  const pauseMedia = () => document.querySelectorAll('video,audio').forEach(media => media.pause());
  document.addEventListener('play', pauseMedia, true);
  document.addEventListener('DOMContentLoaded', pauseMedia, { once: true });
  document.addEventListener('visibilitychange', pauseMedia);
  pauseMedia();

  class BridgeFailure extends Error { constructor(code) { super(code); this.code = code; } }
  const fail = code => { throw new BridgeFailure(code); };
  const boundedString = (value, max) => typeof value === 'string' && value.length <= max ? value : '';
  function cancel() { generation++; pending?.abort(); pending = null; pauseMedia(); }
  port.onDisconnect.addListener(() => { connected = false; cancel(); verifiedChannel = null; continuations.clear(); });

  function currentConfig() {
    // Firefox's content-world wrapper grants read access to the app-owned YouTube
    // document. Copy data, never invoke page functions or evaluate page script.
    const data = window.wrappedJSObject?.ytcfg?.data_;
    if (!data) fail('CONFIG_UNAVAILABLE');
    if (data.LOGGED_IN !== true) fail('SIGNED_OUT');
    let context;
    try { context = JSON.parse(JSON.stringify(data.INNERTUBE_CONTEXT)); }
    catch { fail('CONFIG_UNAVAILABLE'); }
    if (!['WEB', 'MWEB'].includes(context?.client?.clientName) ||
        !boundedString(context.client.clientVersion, 128)) fail('CONFIG_UNAVAILABLE');
    const clientId = String(data.INNERTUBE_CONTEXT_CLIENT_NAME ?? '');
    if (!/^\d{1,3}$/.test(clientId)) fail('CONFIG_UNAVAILABLE');
    const accountIndex = String(data.SESSION_INDEX ?? '0');
    if (!/^\d{1,3}$/.test(accountIndex)) fail('PROFILE_UNVERIFIED');
    const apiKey = boundedString(data.INNERTUBE_API_KEY, 256);
    if (!apiKey) fail('CONFIG_UNAVAILABLE');
    const delegated = boundedString(data.DELEGATED_SESSION_ID, 256);
    if (delegated) context.user = { ...context.user, onBehalfOfUser: delegated };
    return { context, clientId, accountIndex, apiKey, delegated };
  }

  async function awaitConfig(signal) {
    for (let n = 0; n < 40; n++) {
      if (signal.aborted) fail('CANCELLED');
      try { return currentConfig(); }
      catch (error) {
        if (error.code !== 'CONFIG_UNAVAILABLE' || n === 39) throw error;
        await new Promise(resolve => setTimeout(resolve, 125));
      }
    }
    fail('CONFIG_UNAVAILABLE');
  }

  async function headersFor(config) {
    // Only this engine's document cookies are read. They remain in this closure;
    // the platform attaches cookies to same-origin fetches itself.
    const sid = document.cookie.split(';').map(part => part.trim())
      .find(part => part.startsWith('SAPISID='))?.slice('SAPISID='.length);
    if (!sid || sid.length > 4096) fail('SESSION_UNAVAILABLE');
    const timestamp = Math.floor(Date.now() / 1000);
    const digest = await crypto.subtle.digest('SHA-1', new TextEncoder().encode(`${timestamp} ${sid} ${location.origin}`));
    const hash = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('');
    const headers = {
      'Content-Type': 'application/json',
      'Authorization': `SAPISIDHASH ${timestamp}_${hash}`,
      'X-Origin': location.origin,
      'X-Goog-AuthUser': config.accountIndex,
      'X-Youtube-Client-Name': config.clientId,
      'X-Youtube-Client-Version': config.context.client.clientVersion
    };
    if (config.delegated) headers['X-Goog-PageId'] = config.delegated;
    return headers;
  }

  async function readBounded(response) {
    const length = Number(response.headers.get('content-length'));
    if (Number.isFinite(length) && length > MAX_BYTES) fail('RESPONSE_TOO_LARGE');
    const reader = response.body?.getReader();
    if (!reader) fail('RESPONSE_INVALID');
    const decoder = new TextDecoder();
    let size = 0, text = '';
    try {
      while (true) {
        const chunk = await reader.read();
        if (chunk.done) break;
        size += chunk.value.byteLength;
        if (size > MAX_BYTES) { await reader.cancel(); fail('RESPONSE_TOO_LARGE'); }
        text += decoder.decode(chunk.value, { stream: true });
      }
      text += decoder.decode();
    } finally { reader.releaseLock(); }
    try { return JSON.parse(text); } catch { fail('RESPONSE_INVALID'); }
  }

  async function request(endpoint, body, signal) {
    if (!['account/account_menu', 'navigation/resolve_url', 'browse'].includes(endpoint)) fail('REQUEST_INVALID');
    if (!pageFetch) fail('TRANSPORT_UNAVAILABLE');
    const config = await awaitConfig(signal);
    const headers = await headersFor(config);
    if (signal.aborted) fail('CANCELLED');
    const response = await pageFetch(`${location.origin}/youtubei/v1/${endpoint}?key=${encodeURIComponent(config.apiKey)}&prettyPrint=false`, {
      method: 'POST', credentials: 'include', mode: 'same-origin', redirect: 'error',
      signal, headers, body: JSON.stringify({ context: config.context, ...body })
    });
    if (response.status === 401) fail('SIGNED_OUT');
    if (response.status === 403) fail('ACCESS_DENIED');
    if (!response.ok) fail('NETWORK_ERROR');
    const value = await readBounded(response);
    if (!value || typeof value !== 'object' || Array.isArray(value) || value.error) fail('RESPONSE_INVALID');
    return value;
  }

  function textOf(value) {
    return boundedString(value?.simpleText, 256) || boundedString(value?.runs?.map(run => run.text || '').join(''), 256);
  }
  function findActiveHeader(value) {
    // Account menu response shape; no broad search through unrelated video data.
    const candidates = [];
    for (const action of value.actions || []) {
      const header = action?.openPopupAction?.popup?.multiPageMenuRenderer?.header?.activeAccountHeaderRenderer;
      if (header) candidates.push(header);
    }
    if (candidates.length !== 1) fail('PROFILE_UNVERIFIED');
    return candidates[0];
  }
  async function identify(signal) {
    const header = findActiveHeader(await request('account/account_menu', {}, signal));
    const title = textOf(header.accountName);
    if (!title) fail('PROFILE_UNVERIFIED');
    let id = header.serviceEndpoint?.browseEndpoint?.browseId;
    if (!channelPattern.test(id || '')) {
      const handle = textOf(header.channelHandle);
      // Resolve only the handle supplied by the authenticated active-account header.
      if (!/^@[\p{L}\p{N}_.\-\u00b7]{1,100}$/u.test(handle)) fail('PROFILE_UNVERIFIED');
      const resolved = await request('navigation/resolve_url', { url: `${location.origin}/${encodeURI(handle)}` }, signal);
      id = resolved.endpoint?.browseEndpoint?.browseId;
    }
    if (!channelPattern.test(id || '')) fail('PROFILE_UNVERIFIED');
    return { id, title };
  }

  function extractContinuationTokens(value) {
    const tokens = new Set();
    let nodes = 0;
    function walk(node, depth) {
      if (!node || typeof node !== 'object' || depth > 50 || ++nodes > 50000) return;
      const token = node.continuationEndpoint?.continuationCommand?.token ||
        node.nextContinuationData?.continuation || node.reloadContinuationData?.continuation;
      if (typeof token === 'string' && token.length > 0 && token.length <= 16384) tokens.add(token);
      for (const child of Object.values(node)) walk(child, depth + 1);
    }
    walk(value, 0); return tokens;
  }

  port.onMessage.addListener(message => {
    if (!connected || !message || typeof message !== 'object') return;
    if (message.type === 'pause') { cancel(); return; }
    if (!['probe', 'next'].includes(message.type)) return;
    const requestId = boundedString(message.requestId, 128);
    if (!/^[A-Za-z0-9_-]{1,128}$/.test(requestId)) return;
    const expected = message.expectedChannelId;
    if (!channelPattern.test(expected || '')) { port.postMessage({ type: 'failure', requestId, code: 'PROFILE_UNVERIFIED' }); return; }
    if (message.type === 'next' && (verifiedChannel !== expected || !continuations.has(message.continuation))) {
      port.postMessage({ type: 'failure', requestId, code: 'CONTINUATION_INVALID' }); return;
    }
    cancel();
    const current = generation;
    const controller = new AbortController(); pending = controller;
    const timeout = setTimeout(() => controller.abort(), 25000);
    const active = () => connected && current === generation && !controller.signal.aborted;
    (async () => {
      const identity = await identify(controller.signal);
      if (identity.id !== expected) fail('ACCOUNT_MISMATCH');
      const body = message.type === 'next' ? { continuation: message.continuation } : { browseId: 'FEwhat_to_watch' };
      const response = await request('browse', body, controller.signal);
      const after = await identify(controller.signal);
      if (after.id !== identity.id) fail('ACCOUNT_MISMATCH');
      if (!active()) return;
      // No account-menu response, session config, responseContext or request headers
      // cross this boundary. The native parser consumes these feed branches only.
      const feed = {};
      for (const name of ['contents', 'onResponseReceivedActions', 'onResponseReceivedEndpoints', 'continuationContents']) {
        if (response[name] != null) feed[name] = response[name];
      }
      if (!Object.keys(feed).length) fail('RESPONSE_INVALID');
      verifiedChannel = identity.id;
      continuations = extractContinuationTokens(feed);
      port.postMessage({ type: 'home', requestId, channelId: identity.id, channelTitle: identity.title, response: feed });
    })().catch(error => {
      if (!connected || current !== generation) return;
      verifiedChannel = null; continuations.clear();
      const code = controller.signal.aborted ? 'TIMEOUT' : error instanceof BridgeFailure ? error.code : 'NETWORK_ERROR';
      port.postMessage({ type: 'failure', requestId, code });
    }).finally(() => { clearTimeout(timeout); if (pending === controller) pending = null; pauseMedia(); });
  });
})();
