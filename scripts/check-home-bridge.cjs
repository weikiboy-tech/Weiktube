// Test the real packaged bridge with representative endpoint responses.
// This proves local protocol behavior, not actual Google login/endpoint availability.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const { webcrypto } = require('node:crypto');
const source = fs.readFileSync(path.join(__dirname, '../home-data/src/main/assets/home-bridge/bridge.js'), 'utf8');
const manifest = JSON.parse(fs.readFileSync(path.join(__dirname, '../home-data/src/main/assets/home-bridge/manifest.json'), 'utf8'));
assert.deepEqual(manifest.content_scripts[0].matches, ['https://www.youtube.com/*', 'https://m.youtube.com/*']);
const channel = 'UCabcdefghijklmnopqrstuv';
const otherChannel = 'UCZYXWVUTSRQPONMLKJIHGFE';
const menu = (id = channel, handle = null) => ({ actions: [{ openPopupAction: { popup: { multiPageMenuRenderer: {
  header: { activeAccountHeaderRenderer: { accountName: { simpleText: 'Sample channel' },
    ...(handle ? { channelHandle: { simpleText: handle } } : { serviceEndpoint: { browseEndpoint: { browseId: id } } }) }
  }
} } } }] });
const feed = { responseContext: { serviceTrackingParams: ['PRIVATE-CONTEXT'] }, contents: { twoColumnBrowseResultsRenderer: {
  tabs: [{ tabRenderer: { selected: true, content: { richGridRenderer: { contents: [
    { richItemRenderer: { content: { videoRenderer: { videoId: 'abcdefghijk', title: { simpleText: 'Example' } } } } },
    { continuationItemRenderer: { continuationEndpoint: { continuationCommand: { token: 'NEXT_PAGE' } } } }
  ] } } } }]
} } };

const diagnosticKeys = ['pageKind', 'titleKind', 'domReady', 'bridgeReady', 'configPresent', 'contextPresent',
  'clientIdPresent', 'apiKeyPresent', 'loggedIn', 'initialDataPresent', 'httpStatus', 'pageHttpStatus',
  'source', 'missing', 'readyState', 'step'].sort();
function assertSafeDiagnostics(diagnostics) {
  assert.deepEqual(Object.keys(diagnostics).sort(), diagnosticKeys);
  const enums = {
    pageKind: ['home', 'youtube_other', 'consent', 'signin', 'error', 'other'],
    titleKind: ['youtube', 'consent', 'google_login', 'error', 'other'],
    source: ['none', 'data', 'direct', 'inline', 'combined', 'get_only'],
    readyState: ['loading', 'interactive', 'complete', 'unknown'],
    step: ['ready', 'config', 'session', 'identity', 'resolve', 'home', 'continuation', 'verify', 'done']
  };
  for (const [key, values] of Object.entries(enums)) assert.ok(values.includes(diagnostics[key]), `Safe ${key}`);
  for (const key of ['domReady', 'bridgeReady', 'configPresent', 'contextPresent', 'clientIdPresent', 'apiKeyPresent', 'initialDataPresent']) {
    assert.equal(typeof diagnostics[key], 'boolean');
  }
  assert.ok([null, true, false].includes(diagnostics.loggedIn));
  for (const key of ['httpStatus', 'pageHttpStatus']) assert.ok(diagnostics[key] === null ||
    Number.isInteger(diagnostics[key]) && diagnostics[key] >= 100 && diagnostics[key] <= 599);
  assert.ok(Array.isArray(diagnostics.missing));
  assert.ok(diagnostics.missing.every(key => ['config', 'config_read', 'context', 'client_name', 'client_version', 'client_id', 'session_index'].includes(key)));
}

function fixture(options = {}) {
  let receive, disconnect, paused = 0, connections = 0, invokedPageFunction = 0;
  const messages = [], calls = [], events = {};
  const origin = options.origin || 'https://www.youtube.com';
  const loc = new URL(origin + (options.pathname || '/'));
  const config = options.config ?? { LOGGED_IN: options.loggedIn !== false, INNERTUBE_API_KEY: 'PAGE-API-KEY', SESSION_INDEX: '0',
    INNERTUBE_CONTEXT_CLIENT_NAME: 1, INNERTUBE_CONTEXT: { client: { clientName: 'WEB', clientVersion: 'actual-page-version' }, user: {} } };
  for (const key of options.omit || []) delete config[key];
  const container = options.shape === 'direct' ? config : options.shape === 'd' ? { d: config } : { data_: config };
  if (options.shape === 'get_only') {
    delete container.data_;
    container.get = () => { invokedPageFunction++; throw new Error('Never invoke page get()'); };
    Object.defineProperty(container, 'data_', { enumerable: true, get() { invokedPageFunction++; throw new Error('Never invoke page getter'); } });
  }
  const window = { wrappedJSObject: options.shape === 'inline' ? {} : { ytcfg: container } };
  window.top = options.subframe ? {} : window;
  if (options.initialData) window.wrappedJSObject.ytInitialData = {};
  const document = { cookie: options.cookie ?? 'SAPISID=SESSION-SECRET; other=value',
    title: options.title ?? 'YouTube', readyState: options.readyState || 'complete',
    scripts: (options.inlineScripts || []).map(textContent => ({ textContent, src: '' })),
    addEventListener: (event, handler) => { events[event] = handler; },
    querySelectorAll: () => [{ pause: () => paused++ }]
  };
  const context = { window, document, location: loc, URL, TextEncoder, TextDecoder, Uint8Array, crypto: webcrypto,
    AbortController, clearTimeout,
    setTimeout: (fn, ms) => setTimeout(fn, ms === 125 ? 1 : ms),
    performance: { getEntriesByType: () => [{ responseStatus: options.pageStatus }] },
    browser: { runtime: { connectNative(name) {
      assert.equal(name, 'weiktubeHome'); connections++;
      return { onMessage: { addListener: handler => receive = handler }, onDisconnect: { addListener: handler => disconnect = handler },
        postMessage: message => {
          const copied = JSON.parse(JSON.stringify(message));
          assertSafeDiagnostics(copied.diagnostics);
          messages.push(copied);
        } };
    } } },
    fetch() { throw new Error('Extension-origin global fetch must never be used'); },
    content: options.noPageFetch ? {} : { async fetch(url, init) {
      const endpoint = new URL(url).pathname.split('/youtubei/v1/')[1];
      const call = { url, endpoint, init, body: JSON.parse(init.body) }; calls.push(call);
      assert.equal(new URL(url).origin, loc.origin);
      assert.equal(init.credentials, 'include'); assert.equal(init.mode, 'same-origin'); assert.equal(init.redirect, 'error');
      assert.equal(init.headers['User-Agent'], undefined); assert.equal(init.headers.Cookie, undefined);
      assert.match(init.headers.Authorization, /^SAPISIDHASH \d+_[0-9a-f]{40}$/);
      if (options.fetch) return options.fetch(call, calls);
      let body;
      if (endpoint === 'account/account_menu') body = options.menu || menu();
      else if (endpoint === 'navigation/resolve_url') body = { endpoint: { browseEndpoint: { browseId: channel } } };
      else body = options.feed || feed;
      return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
    } }
  };
  vm.runInNewContext(source, context);
  return { calls, messages, config, document, events, get connections() { return connections; }, get paused() { return paused; },
    get invokedPageFunction() { return invokedPageFunction; },
    send: message => receive?.(message), disconnect: () => disconnect?.(),
    async wait(count = 1) {
      const deadline = Date.now() + 2000;
      const replies = () => messages.filter(message => message.type !== 'ready');
      while (replies().length < count && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 5));
      assert.ok(replies().length >= count, 'Expected bounded native reply'); return replies()[replies().length - 1];
    }
  };
}
const command = (requestId = 'request-1') => ({ type: 'probe', requestId, expectedChannelId: channel });
async function main() {
  assert.equal(fixture({ origin: 'https://accounts.google.com' }).connections, 0);
  assert.equal(fixture({ origin: 'https://www.youtube.com.evil.test' }).connections, 0);
  assert.equal(fixture({ subframe: true }).connections, 0);
  let noTransport = fixture({ noPageFetch: true }); noTransport.send(command());
  assert.equal((await noTransport.wait()).code, 'TRANSPORT_UNAVAILABLE'); noTransport.disconnect();

  let f = fixture({ loggedIn: false }); f.send(command());
  assert.equal((await f.wait()).code, 'SIGNED_OUT'); assert.equal(f.calls.length, 0); f.disconnect();
  f = fixture({ cookie: '' }); f.send(command()); let reply = await f.wait(); assert.equal(reply.code, 'SESSION_UNAVAILABLE');
  assert.equal(reply.diagnostics.step, 'session');
  assert.equal(f.calls.length, 0); f.disconnect();
  f = fixture(); f.send({ ...command(), expectedChannelId: '' });
  assert.equal((await f.wait()).code, 'PROFILE_UNVERIFIED'); assert.equal(f.calls.length, 0); f.disconnect();

  f = fixture({ menu: menu(otherChannel) }); f.send(command());
  assert.equal((await f.wait()).code, 'ACCOUNT_MISMATCH'); assert.ok(f.calls.every(c => c.endpoint !== 'browse')); f.disconnect();
  f = fixture({ menu: { actions: [] } }); f.send(command());
  assert.equal((await f.wait()).code, 'PROFILE_UNVERIFIED'); assert.equal(f.calls.length, 1); f.disconnect();

  f = fixture({ menu: menu(channel, '@sample_handle') }); f.send(command());
  reply = await f.wait(); assert.equal(reply.type, 'home'); assert.equal(reply.channelId, channel);
  assert.equal(f.calls.find(c => c.endpoint === 'browse').body.browseId, 'FEwhat_to_watch');
  assert.ok(f.calls.filter(c => c.endpoint === 'navigation/resolve_url').every(c => c.body.url === 'https://www.youtube.com/@sample_handle'));
  assert.ok(!('responseContext' in reply.response));
  assert.deepEqual(Object.keys(reply).sort(), ['channelId', 'channelTitle', 'diagnostics', 'requestId', 'response', 'type']);
  assert.ok(!JSON.stringify(f.messages).includes('SESSION-SECRET'));
  assert.ok(!JSON.stringify(f.messages).includes('PRIVATE-CONTEXT'));
  assert.ok(!JSON.stringify(f.messages).includes('PAGE-API-KEY'));
  assert.ok(!JSON.stringify(f.messages).includes('Authorization'));
  f.send({ type: 'next', requestId: 'next-1', expectedChannelId: channel, continuation: 'NEXT_PAGE' });
  assert.equal((await f.wait(2)).type, 'home');
  assert.equal(f.calls.filter(c => c.endpoint === 'browse')[1].body.continuation, 'NEXT_PAGE');
  f.send({ type: 'next', requestId: 'next-2', expectedChannelId: channel, continuation: 'UNISSUED_TOKEN' });
  assert.equal((await f.wait(3)).code, 'CONTINUATION_INVALID');
  const pauses = f.paused; f.events.play(); assert.ok(f.paused > pauses);
  f.disconnect(); const sent = f.messages.length; f.send(command()); await new Promise(resolve => setTimeout(resolve, 10));
  assert.equal(f.messages.length, sent);

  let menuCalls = 0;
  f = fixture({ fetch: async call => new Response(JSON.stringify(call.endpoint === 'account/account_menu' ? menu(++menuCalls === 1 ? channel : otherChannel) : feed)) });
  f.send(command()); assert.equal((await f.wait()).code, 'ACCOUNT_MISMATCH'); assert.ok(!f.messages.some(m => m.type === 'home')); f.disconnect();

  f = fixture({ fetch: async () => new Response('x', { status: 403 }) }); f.send(command());
  reply = await f.wait(); assert.equal(reply.code, 'ACCESS_DENIED');
  assert.equal(reply.diagnostics.step, 'identity'); assert.equal(reply.diagnostics.httpStatus, 403); f.disconnect();
  f = fixture({ fetch: async call => {
    if (call.endpoint === 'browse') throw new Error('Private network details must not leave engine');
    return new Response(JSON.stringify(menu()));
  } });
  f.send(command()); reply = await f.wait(); assert.equal(reply.code, 'NETWORK_ERROR');
  assert.equal(reply.diagnostics.step, 'home'); assert.equal(reply.diagnostics.httpStatus, null);
  assert.ok(!JSON.stringify(f.messages).includes('Private network')); f.disconnect();
  f = fixture({ fetch: async () => new Response('{}', { headers: { 'Content-Length': String(5 * 1024 * 1024) } }) });
  f.send(command()); assert.equal((await f.wait()).code, 'RESPONSE_TOO_LARGE'); f.disconnect();

  f = fixture({ fetch: async call => new Promise((resolve, reject) => call.init.signal.addEventListener('abort', () => reject(new Error('Cancelled')), { once: true })) });
  f.send(command()); await new Promise(resolve => setTimeout(resolve, 15)); f.send({ type: 'pause' });
  await new Promise(resolve => setTimeout(resolve, 15)); assert.equal(f.messages.filter(m => m.type !== 'ready').length, 0); f.disconnect();

  // The old /signin entry can redirect to an HTTP 200 error document without
  // ytcfg. Diagnose the document instead of misreporting unavailable config.
  for (const options of [{ pathname: '/oops', title: 'YouTube' }, { title: 'Hoppla! Ein Fehler ist aufgetreten' }]) {
    f = fixture({ ...options, shape: 'inline', pageStatus: 200 }); f.send(command()); reply = await f.wait();
    assert.equal(reply.code, 'PAGE_ERROR'); assert.equal(reply.diagnostics.pageKind, 'error');
    assert.equal(reply.diagnostics.pageHttpStatus, 200); assert.equal(reply.diagnostics.httpStatus, null);
    assert.equal(f.calls.length, 0); f.disconnect();
  }
  for (const pathname of ['/signin', '/t/signin', '/ServiceLogin']) {
    f = fixture({ pathname, shape: 'inline' }); f.send(command()); reply = await f.wait();
    assert.equal(reply.code, 'SIGNIN_REQUIRED'); assert.equal(f.calls.length, 0); f.disconnect();
  }
  f = fixture({ pathname: '/consent', shape: 'inline' }); f.send(command());
  assert.equal((await f.wait()).code, 'CONSENT_REQUIRED'); assert.equal(f.calls.length, 0); f.disconnect();

  // Unknown LOGGED_IN is not a signed-out verdict. Account identity must still
  // succeed before any Home request; no generic fallback is accepted.
  f = fixture({ omit: ['LOGGED_IN', 'INNERTUBE_API_KEY', 'INNERTUBE_CONTEXT_CLIENT_NAME'] });
  f.send(command()); reply = await f.wait(); assert.equal(reply.type, 'home');
  assert.equal(reply.diagnostics.loggedIn, null); assert.equal(reply.diagnostics.apiKeyPresent, false);
  assert.ok(f.calls.every(call => !new URL(call.url).searchParams.has('key')));
  assert.ok(f.calls.every(call => call.init.headers['X-Youtube-Client-Name'] === '1')); f.disconnect();
  f = fixture({ omit: ['LOGGED_IN'], menu: menu(otherChannel) }); f.send(command());
  assert.equal((await f.wait()).code, 'ACCOUNT_MISMATCH'); assert.ok(f.calls.every(call => call.endpoint !== 'browse')); f.disconnect();

  for (const shape of ['direct', 'd']) {
    f = fixture({ shape }); f.send(command()); reply = await f.wait();
    assert.equal(reply.type, 'home'); assert.equal(reply.diagnostics.source, shape === 'd' ? 'data' : 'direct'); f.disconnect();
  }
  const mobileConfig = { LOGGED_IN: true, INNERTUBE_CLIENT_NAME: 'MWEB', INNERTUBE_CLIENT_VERSION: 'page-mobile-version',
    INNERTUBE_CONTEXT_CLIENT_NAME: 2, SESSION_INDEX: '0' };
  for (const prefix of ['ytcfg.set(', 'window.ytcfg.data_ = ', 'ytcfg.d = ']) {
    f = fixture({ origin: 'https://m.youtube.com', shape: 'inline', initialData: true,
      inlineScripts: [`${prefix}${JSON.stringify(mobileConfig)}${prefix.endsWith('(') ? ')' : ''};`] });
    f.send(command()); reply = await f.wait(); assert.equal(reply.type, 'home');
    assert.equal(reply.diagnostics.source, 'inline'); assert.equal(reply.diagnostics.initialDataPresent, true);
    assert.ok(f.calls.every(call => call.body.context.client.clientName === 'MWEB' &&
      call.body.context.client.clientVersion === 'page-mobile-version')); f.disconnect();
  }
  // Public YouTube's full config is currently >700 KB; only copied fields have
  // the stricter per-field bound. Unrelated experiment flags must not hide it.
  f = fixture({ shape: 'inline', inlineScripts: [`ytcfg.set(${JSON.stringify({
    EXPERIMENT_FLAGS: { unrelated: 'x'.repeat(800000) }, ...mobileConfig })});`] });
  f.send(command()); reply = await f.wait(); assert.equal(reply.type, 'home');
  assert.equal(reply.diagnostics.source, 'inline');
  assert.ok(f.messages.every(message => !JSON.stringify(message).includes('EXPERIMENT_FLAGS'))); f.disconnect();
  f = fixture({ shape: 'get_only', inlineScripts: [`ytcfg.set(${JSON.stringify(mobileConfig)});`] });
  f.send(command()); reply = await f.wait(); assert.equal(reply.type, 'home');
  assert.equal(reply.diagnostics.source, 'inline'); assert.equal(f.invokedPageFunction, 0); f.disconnect();
  f = fixture({ shape: 'get_only' }); f.send(command()); reply = await f.wait();
  assert.equal(reply.code, 'CONFIG_UNAVAILABLE'); assert.equal(reply.diagnostics.source, 'get_only');
  assert.equal(f.invokedPageFunction, 0); assert.equal(f.calls.length, 0); f.disconnect();

  // A non-literal setter and malformed JSON are never evaluated as page code.
  f = fixture({ shape: 'inline', inlineScripts: ['ytcfg.set((()=>{throw new Error("never execute")})());', 'ytcfg.set({bad: true});'] });
  f.send(command()); reply = await f.wait(); assert.equal(reply.code, 'CONFIG_UNAVAILABLE');
  assert.ok(reply.diagnostics.missing.includes('config')); assert.equal(f.calls.length, 0); f.disconnect();
  f = fixture({ config: { LOGGED_IN: true, INNERTUBE_CONTEXT_CLIENT_NAME: 1,
    INNERTUBE_CONTEXT: { client: { clientName: 'WEB' } } } });
  f.send(command()); reply = await f.wait(); assert.equal(reply.code, 'CONFIG_UNAVAILABLE');
  assert.deepEqual(reply.diagnostics.missing, ['client_version']); f.disconnect();
  f = fixture({ config: { LOGGED_IN: true, INNERTUBE_CONTEXT: { enormous: 'x'.repeat(17000) } } });
  f.send(command()); reply = await f.wait(); assert.equal(reply.code, 'CONFIG_UNAVAILABLE');
  assert.ok(reply.diagnostics.missing.includes('config_read')); f.disconnect();

  f = fixture({ title: 'PRIVATE ACCOUNT NAME - YouTube', readyState: 'loading', pageStatus: 0 });
  assert.equal(f.messages[0].type, 'ready'); assert.equal(f.messages[0].diagnostics.domReady, false);
  Object.defineProperty(f.document, 'cookie', { get() { throw new Error('Diagnostics must not read cookies'); } });
  f.send({ type: 'diagnose', requestId: 'diagnose-1' }); reply = await f.wait();
  assert.equal(reply.type, 'diagnostics'); assert.equal(reply.diagnostics.pageHttpStatus, null);
  assert.equal(f.calls.length, 0); assert.ok(!JSON.stringify(f.messages).includes('PRIVATE ACCOUNT NAME'));
  f.document.readyState = 'complete'; f.events.DOMContentLoaded();
  assert.equal(f.messages.at(-1).type, 'ready'); assert.equal(f.messages.at(-1).diagnostics.domReady, true); f.disconnect();

  console.log('PASS Home bridge: exact origins, page-origin transport, account proof, Home/pagination, credential isolation, cancellation, HTTP failures, safe bounded config formats, unknown login/API-key handling, error-page diagnosis, safe ready/diagnostics protocol.');
  if (process.argv.includes('--public')) await probePublicHtml();
}

async function probePublicHtml() {
  // Optional, anonymous network check. No cookie/header/account data, downloaded
  // HTML, page config or raw document title is saved or printed.
  for (const [label, url] of [['public-home', 'https://m.youtube.com/'],
    ['legacy-signin', 'https://www.youtube.com/signin?action_handle_signin=true&next=%2F']]) {
    const response = await fetch(url, { credentials: 'omit', signal: AbortSignal.timeout(20000) });
    const html = await response.text();
    assert.ok(html.length <= 8 * 1024 * 1024, 'Bounded public document');
    const scripts = [...html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)]
      .filter(match => !/\bsrc\s*=/.test(match[1])).map(match => match[2]);
    const finalUrl = new URL(response.url);
    const title = html.match(/<title\b[^>]*>([\s\S]*?)<\/title>/i)?.[1] || '';
    const f = fixture({ origin: finalUrl.origin, pathname: finalUrl.pathname, title, pageStatus: response.status,
      shape: 'inline', inlineScripts: scripts, cookie: '' });
    assert.equal(f.connections, 1, 'Public page remained on allowed YouTube origin');
    f.send(command()); const reply = await f.wait();
    console.log(`PUBLIC ${label}: ${JSON.stringify({ code: reply.code, diagnostics: reply.diagnostics,
      scriptCount: scripts.length, literalSetScriptSizes: scripts.filter(text => /ytcfg\.set\(/.test(text)).map(text => text.length) })}`);
    if (label === 'public-home') {
      assert.equal(reply.code, 'SIGNED_OUT');
      assert.equal(reply.diagnostics.source, 'inline');
      assert.equal(reply.diagnostics.clientIdPresent, true);
    } else if (finalUrl.pathname === '/oops') assert.equal(reply.code, 'PAGE_ERROR');
    assert.equal(f.calls.length, 0);
    f.disconnect();
  }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
