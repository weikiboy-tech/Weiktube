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

  const CONFIG_KEYS = ['INNERTUBE_CONTEXT', 'INNERTUBE_CLIENT_NAME', 'INNERTUBE_CLIENT_VERSION',
    'INNERTUBE_CONTEXT_CLIENT_NAME', 'INNERTUBE_CONTEXT_CLIENT_VERSION', 'INNERTUBE_CONTEXT_HL',
    'INNERTUBE_CONTEXT_GL', 'INNERTUBE_API_KEY', 'SESSION_INDEX', 'DELEGATED_SESSION_ID', 'LOGGED_IN'];

  function dataProperty(object, key) {
    if (!object || (typeof object !== 'object' && typeof object !== 'function')) return undefined;
    const property = Object.getOwnPropertyDescriptor(object, key);
    // Never invoke page getters or ytcfg.get()/toJSON().
    return property && Object.prototype.hasOwnProperty.call(property, 'value') ? property.value : undefined;
  }
  function copyPlain(value, budget = { nodes: 0, chars: 0 }, depth = 0) {
    if (depth > 14 || ++budget.nodes > 4000) throw new Error('Bounded config');
    if (value === null || typeof value === 'boolean' || typeof value === 'number') return value;
    if (typeof value === 'string') {
      budget.chars += value.length;
      if (value.length > 16384 || budget.chars > 262144) throw new Error('Bounded config');
      return value;
    }
    if (!value || typeof value !== 'object') return undefined;
    const result = Array.isArray(value) ? [] : Object.create(null);
    const keys = Object.keys(value);
    if (keys.length > 4000) throw new Error('Bounded config');
    for (const key of keys) {
      if (key === '__proto__' || key === 'constructor' || key === 'prototype') continue;
      const item = copyPlain(dataProperty(value, key), budget, depth + 1);
      if (item !== undefined) result[key] = item;
    }
    return result;
  }
  function jsonObjectAt(text, offset) {
    while (/\s/.test(text[offset] || 'x')) offset++;
    if (text[offset] !== '{') return null;
    let depth = 0, quoted = false, escaped = false;
    // Current public config includes >700 KB of unrelated experiment flags.
    // Bound the literal document at 4 MB, then copy only the small known keys.
    for (let i = offset; i < text.length && i - offset <= MAX_BYTES; i++) {
      const char = text[i];
      if (quoted) {
        if (escaped) escaped = false;
        else if (char === '\\') escaped = true;
        else if (char === '"') quoted = false;
      } else if (char === '"') quoted = true;
      else if (char === '{' || char === '[') depth++;
      else if (char === '}' || char === ']') {
        if (--depth === 0) {
          try { return JSON.parse(text.slice(offset, i + 1)); } catch { return null; }
        }
      }
    }
    return null;
  }
  function pageDiagnostics(step = 'ready') {
    const path = location.pathname;
    const title = boundedString(document.title, 512).trim();
    const errorPath = /^\/(?:oops|error|sorry)(?:\/|$)/.test(path);
    const consent = /^\/consent(?:\/|$)/.test(path) || /^(?:before you continue|bevor du (?:zu|fort)|consent)/i.test(title);
    const signin = /^\/(?:signin|login|account_login|ServiceLogin|t\/signin)(?:\/|$)/i.test(path);
    const errorTitle = /^(?:oops|hoppla|an error occurred|fehler|something went wrong)/i.test(title);
    const pageKind = errorPath || errorTitle ? 'error' : consent ? 'consent' : signin ? 'signin' :
      path === '/' || path === '/feed/what_to_watch' ? 'home' : 'youtube_other';
    const titleKind = errorTitle ? 'error' : consent ? 'consent' : /^(?:sign in|anmelden).*google/i.test(title) ? 'google_login' :
      /youtube/i.test(title) ? 'youtube' : 'other';
    let pageHttpStatus = null;
    try {
      const status = performance.getEntriesByType('navigation')[0]?.responseStatus;
      if (Number.isInteger(status) && status >= 100 && status <= 599) pageHttpStatus = status;
    } catch { /* Not supported is unknown, never an assumed HTTP 200. */ }
    const readyState = ['loading', 'interactive', 'complete'].includes(document.readyState) ? document.readyState : 'unknown';
    return { pageKind, titleKind, domReady: readyState === 'interactive' || readyState === 'complete', bridgeReady: true,
      configPresent: false, contextPresent: false, clientIdPresent: false, apiKeyPresent: false,
      loggedIn: null, initialDataPresent: false, httpStatus: null, pageHttpStatus,
      source: 'none', missing: [], readyState, step };
  }
  function readPageConfig(diagnostics) {
    const data = Object.create(null), sources = new Set();
    let readFailed = false, getterOnly = false;
    const merge = (candidate, source) => {
      if (!candidate || typeof candidate !== 'object') return;
      let found = false;
      for (const key of CONFIG_KEYS) {
        try {
          const value = copyPlain(dataProperty(candidate, key));
          if (value !== undefined) { data[key] = value; found = true; }
        } catch { readFailed = true; }
      }
      if (found) sources.add(source);
    };
    // Inline set calls contain literal JSON; parse it as data, never execute it.
    // This also supports pages whose get-only ytcfg wrapper conceals its storage.
    let inspected = 0;
    try {
      for (const script of Array.from(document.scripts || []).slice(0, 120)) {
        if (script.src) continue;
        const text = script.textContent || '';
        inspected += text.length;
        if (inspected > MAX_BYTES) break;
        if (/\bytInitialData\s*=/.test(text) || /window\s*\[\s*["']ytInitialData["']\s*\]/.test(text)) diagnostics.initialDataPresent = true;
        const pattern = /(?:^|[;{}\n])\s*(?:window\.)?ytcfg\s*(?:\.\s*set\s*\(|\.\s*(?:data_|d)\s*=)\s*/g;
        for (let match, count = 0; (match = pattern.exec(text)) && count++ < 24;) merge(jsonObjectAt(text, pattern.lastIndex), 'inline');
      }
    } catch { readFailed = true; }
    try {
      const page = window.wrappedJSObject;
      diagnostics.initialDataPresent ||= !!dataProperty(page, 'ytInitialData');
      const config = dataProperty(page, 'ytcfg');
      diagnostics.configPresent = !!config || sources.size > 0;
      getterOnly = typeof dataProperty(config, 'get') === 'function';
      // Live data supersedes the initial document after an account switch.
      merge(config, 'direct');
      merge(dataProperty(config, 'd'), 'data');
      merge(dataProperty(config, 'data_'), 'data');
    } catch { readFailed = true; }
    diagnostics.configPresent ||= sources.size > 0;
    diagnostics.source = sources.size > 1 ? 'combined' : sources.size ? [...sources][0] : getterOnly ? 'get_only' : 'none';
    diagnostics.missing = readFailed ? ['config_read'] : [];
    return data;
  }
  function currentConfig(diagnostics) {
    const page = pageDiagnostics(diagnostics.step);
    for (const key of ['pageKind', 'titleKind', 'domReady', 'readyState', 'pageHttpStatus']) diagnostics[key] = page[key];
    diagnostics.configPresent = false;
    diagnostics.initialDataPresent = false;
    const data = readPageConfig(diagnostics);
    const originalContext = data.INNERTUBE_CONTEXT;
    let context = originalContext && typeof originalContext === 'object' && !Array.isArray(originalContext) ? originalContext : null;
    diagnostics.contextPresent = !!context;
    const clientName = context?.client?.clientName || data.INNERTUBE_CLIENT_NAME;
    const version = boundedString(context?.client?.clientVersion, 128) || boundedString(data.INNERTUBE_CLIENT_VERSION, 128) ||
      boundedString(data.INNERTUBE_CONTEXT_CLIENT_VERSION, 128);
    const inferredId = clientName === 'WEB' ? '1' : clientName === 'MWEB' ? '2' : '';
    const givenId = data.INNERTUBE_CONTEXT_CLIENT_NAME;
    const clientId = givenId == null ? inferredId : ['string', 'number'].includes(typeof givenId) ? String(givenId) : '';
    diagnostics.clientIdPresent = !!inferredId && clientId === inferredId;
    diagnostics.apiKeyPresent = !!boundedString(data.INNERTUBE_API_KEY, 256);
    diagnostics.loggedIn = typeof data.LOGGED_IN === 'boolean' ? data.LOGGED_IN : null;
    if (!diagnostics.configPresent) diagnostics.missing.push('config');
    if (!['WEB', 'MWEB'].includes(clientName)) diagnostics.missing.push('client_name');
    if (!version) diagnostics.missing.push('client_version');
    if (!diagnostics.clientIdPresent) diagnostics.missing.push('client_id');
    if (diagnostics.pageKind === 'error') fail('PAGE_ERROR');
    if (diagnostics.pageKind === 'consent') fail('CONSENT_REQUIRED');
    if (diagnostics.pageKind === 'signin') fail('SIGNIN_REQUIRED');
    if (diagnostics.loggedIn === false) fail('SIGNED_OUT');
    if (!diagnostics.configPresent || !inferredId || !version || !diagnostics.clientIdPresent) fail('CONFIG_UNAVAILABLE');
    // Preserve the page's actual WEB/MWEB identity and version; never substitute
    // a hardcoded version, TV/Android identity or user agent.
    if (!context) context = { client: { clientName, clientVersion: version } };
    else context.client = { ...context.client, clientName, clientVersion: version };
    const accountIndex = String(data.SESSION_INDEX ?? '0');
    if (!/^\d{1,3}$/.test(accountIndex)) { diagnostics.missing.push('session_index'); fail('PROFILE_UNVERIFIED'); }
    const apiKey = boundedString(data.INNERTUBE_API_KEY, 256);
    const delegated = boundedString(data.DELEGATED_SESSION_ID, 256);
    if (delegated) context.user = { ...context.user, onBehalfOfUser: delegated };
    return { context, clientId, accountIndex, apiKey, delegated };
  }

  function diagnosticSnapshot(step = 'ready') {
    const diagnostics = pageDiagnostics(step);
    try { currentConfig(diagnostics); } catch { /* Only allowlisted metadata leaves the document. */ }
    return diagnostics;
  }
  const reportReady = () => { if (connected) port.postMessage({ type: 'ready', diagnostics: diagnosticSnapshot() }); };
  document.addEventListener('DOMContentLoaded', reportReady, { once: true });
  reportReady();

  async function awaitConfig(signal, diagnostics) {
    for (let n = 0; n < 40; n++) {
      if (signal.aborted) fail('CANCELLED');
      try { return currentConfig(diagnostics); }
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

  async function request(endpoint, body, signal, diagnostics, step) {
    if (!['account/account_menu', 'navigation/resolve_url', 'browse'].includes(endpoint)) fail('REQUEST_INVALID');
    diagnostics.httpStatus = null;
    if (!pageFetch) fail('TRANSPORT_UNAVAILABLE');
    diagnostics.step = 'config';
    const config = await awaitConfig(signal, diagnostics);
    diagnostics.step = 'session';
    const headers = await headersFor(config);
    if (signal.aborted) fail('CANCELLED');
    diagnostics.step = step;
    // Current YouTube.js sends browse/account requests without an API-key query.
    // Use the page's key when present, but do not reject otherwise-valid config.
    const query = config.apiKey ? `key=${encodeURIComponent(config.apiKey)}&prettyPrint=false` : 'prettyPrint=false';
    const response = await pageFetch(`${location.origin}/youtubei/v1/${endpoint}?${query}`, {
      method: 'POST', credentials: 'include', mode: 'same-origin', redirect: 'error',
      signal, headers, body: JSON.stringify({ context: config.context, ...body })
    });
    diagnostics.httpStatus = Number.isInteger(response.status) && response.status >= 100 && response.status <= 599 ? response.status : null;
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
  async function identify(signal, diagnostics, step = 'identity') {
    const header = findActiveHeader(await request('account/account_menu', {}, signal, diagnostics, step));
    const title = textOf(header.accountName);
    if (!title) fail('PROFILE_UNVERIFIED');
    let id = header.serviceEndpoint?.browseEndpoint?.browseId;
    if (!channelPattern.test(id || '')) {
      const handle = textOf(header.channelHandle);
      // Resolve only the handle supplied by the authenticated active-account header.
      if (!/^@[\p{L}\p{N}_.\-\u00b7]{1,100}$/u.test(handle)) fail('PROFILE_UNVERIFIED');
      const resolved = await request('navigation/resolve_url', { url: `${location.origin}/${encodeURI(handle)}` }, signal, diagnostics, 'resolve');
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
    if (!['probe', 'next', 'diagnose'].includes(message.type)) return;
    const requestId = boundedString(message.requestId, 128);
    if (!/^[A-Za-z0-9_-]{1,128}$/.test(requestId)) return;
    if (message.type === 'diagnose') {
      port.postMessage({ type: 'diagnostics', requestId, diagnostics: diagnosticSnapshot() }); return;
    }
    const diagnostics = pageDiagnostics('config');
    const expected = message.expectedChannelId;
    if (!channelPattern.test(expected || '')) { port.postMessage({ type: 'failure', requestId, code: 'PROFILE_UNVERIFIED', diagnostics }); return; }
    if (message.type === 'next' && (verifiedChannel !== expected || !continuations.has(message.continuation))) {
      port.postMessage({ type: 'failure', requestId, code: 'CONTINUATION_INVALID', diagnostics }); return;
    }
    cancel();
    const current = generation;
    const controller = new AbortController(); pending = controller;
    const timeout = setTimeout(() => controller.abort(), 25000);
    const active = () => connected && current === generation && !controller.signal.aborted;
    (async () => {
      const identity = await identify(controller.signal, diagnostics);
      if (identity.id !== expected) fail('ACCOUNT_MISMATCH');
      const body = message.type === 'next' ? { continuation: message.continuation } : { browseId: 'FEwhat_to_watch' };
      const response = await request('browse', body, controller.signal, diagnostics, message.type === 'next' ? 'continuation' : 'home');
      const after = await identify(controller.signal, diagnostics, 'verify');
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
      diagnostics.step = 'done';
      port.postMessage({ type: 'home', requestId, channelId: identity.id, channelTitle: identity.title, response: feed, diagnostics });
    })().catch(error => {
      if (!connected || current !== generation) return;
      verifiedChannel = null; continuations.clear();
      const code = controller.signal.aborted ? 'TIMEOUT' : error instanceof BridgeFailure ? error.code : 'NETWORK_ERROR';
      port.postMessage({ type: 'failure', requestId, code, diagnostics });
    }).finally(() => { clearTimeout(timeout); if (pending === controller) pending = null; pauseMedia(); });
  });
})();
