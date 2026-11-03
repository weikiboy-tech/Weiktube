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

function fixture(options = {}) {
  let receive, disconnect, paused = 0, connections = 0;
  const messages = [], calls = [], events = {};
  const origin = options.origin || 'https://www.youtube.com';
  const loc = new URL(origin + '/');
  const config = { LOGGED_IN: options.loggedIn !== false, INNERTUBE_API_KEY: 'PAGE-API-KEY', SESSION_INDEX: '0',
    INNERTUBE_CONTEXT_CLIENT_NAME: 1, INNERTUBE_CONTEXT: { client: { clientName: 'WEB', clientVersion: 'actual-page-version' }, user: {} } };
  const window = { wrappedJSObject: { ytcfg: { data_: config } } }; window.top = options.subframe ? {} : window;
  const document = { cookie: options.cookie ?? 'SAPISID=SESSION-SECRET; other=value',
    addEventListener: (event, handler) => { events[event] = handler; },
    querySelectorAll: () => [{ pause: () => paused++ }]
  };
  const context = { window, document, location: loc, URL, TextEncoder, TextDecoder, Uint8Array, crypto: webcrypto,
    AbortController, setTimeout, clearTimeout,
    browser: { runtime: { connectNative(name) {
      assert.equal(name, 'weiktubeHome'); connections++;
      return { onMessage: { addListener: handler => receive = handler }, onDisconnect: { addListener: handler => disconnect = handler },
        postMessage: message => messages.push(JSON.parse(JSON.stringify(message))) };
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
  return { calls, messages, config, events, get connections() { return connections; }, get paused() { return paused; },
    send: message => receive?.(message), disconnect: () => disconnect?.(),
    async wait(count = 1) {
      const deadline = Date.now() + 2000;
      while (messages.length < count && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 5));
      assert.ok(messages.length >= count, 'Expected bounded native reply'); return messages[messages.length - 1];
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
  f = fixture({ cookie: '' }); f.send(command()); assert.equal((await f.wait()).code, 'SESSION_UNAVAILABLE');
  assert.equal(f.calls.length, 0); f.disconnect();
  f = fixture(); f.send({ ...command(), expectedChannelId: '' });
  assert.equal((await f.wait()).code, 'PROFILE_UNVERIFIED'); assert.equal(f.calls.length, 0); f.disconnect();

  f = fixture({ menu: menu(otherChannel) }); f.send(command());
  assert.equal((await f.wait()).code, 'ACCOUNT_MISMATCH'); assert.ok(f.calls.every(c => c.endpoint !== 'browse')); f.disconnect();
  f = fixture({ menu: { actions: [] } }); f.send(command());
  assert.equal((await f.wait()).code, 'PROFILE_UNVERIFIED'); assert.equal(f.calls.length, 1); f.disconnect();

  f = fixture({ menu: menu(channel, '@sample_handle') }); f.send(command());
  let reply = await f.wait(); assert.equal(reply.type, 'home'); assert.equal(reply.channelId, channel);
  assert.equal(f.calls.find(c => c.endpoint === 'browse').body.browseId, 'FEwhat_to_watch');
  assert.ok(f.calls.filter(c => c.endpoint === 'navigation/resolve_url').every(c => c.body.url === 'https://www.youtube.com/@sample_handle'));
  assert.ok(!('responseContext' in reply.response));
  assert.deepEqual(Object.keys(reply).sort(), ['channelId', 'channelTitle', 'requestId', 'response', 'type']);
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
  assert.equal((await f.wait()).code, 'ACCESS_DENIED'); f.disconnect();
  f = fixture({ fetch: async () => new Response('{}', { headers: { 'Content-Length': String(5 * 1024 * 1024) } }) });
  f.send(command()); assert.equal((await f.wait()).code, 'RESPONSE_TOO_LARGE'); f.disconnect();

  f = fixture({ fetch: async call => new Promise((resolve, reject) => call.init.signal.addEventListener('abort', () => reject(new Error('Cancelled')), { once: true })) });
  f.send(command()); await new Promise(resolve => setTimeout(resolve, 15)); f.send({ type: 'pause' });
  await new Promise(resolve => setTimeout(resolve, 15)); assert.equal(f.messages.length, 0); f.disconnect();
  console.log('PASS Home bridge: page-origin transport only, exact origins, logged-out/missing-session gate, account identity and handle resolution, real Home endpoint, continuation ownership, credential isolation, mid-request account switch, HTTP/size failures, media pause and cancellation.');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
