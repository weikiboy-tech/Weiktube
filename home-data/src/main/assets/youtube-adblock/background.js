/* Built-in extension only. Policy contains validated data; executable code comes from APK assets. */
(() => {
  'use strict';
  const MATCHES = ['https://youtube.com/*', 'https://www.youtube.com/*', 'https://m.youtube.com/*'];
  const REQUESTS = MATCHES.concat([
    'https://*.doubleclick.net/*', 'https://*.googlesyndication.com/*',
    'https://*.googleadservices.com/*', 'https://*.adservice.google.com/*'
  ]);
  const EXCLUDED = MATCHES.flatMap(pattern => ['signin*', 'login*', 'logout*', 'account*', 'o/oauth*', 'ServiceLogin*', 'servicelogin*'].map(path => pattern.replace('*', path)));
  const ACCOUNT_PATH = /^\/(?:signin|login|logout|account)(?:[/?_]|$)|^\/(?:o\/oauth|ServiceLogin)/i;
  const ORIGINS = new Set(['youtube.com', 'www.youtube.com', 'm.youtube.com']);
  const COUNTERS = new Set(['network', 'data', 'cosmetic', 'breakthrough']);
  const pages = new Map();
  const counts = new Map();
  let nativePort = null;
  let current = null;
  let registrations = [];
  let configuring = true;
  let attemptedConfiguration = false;
  let releaseGate;
  let gate = new Promise(resolve => { releaseGate = resolve; });
  let configurationQueue = Promise.resolve();
  let flushTimer = null;

  function parsed(url) {
    try {
      const value = new URL(url);
      return value.protocol === 'https:' && !value.username && !value.password && (!value.port || value.port === '443') ? value : null;
    } catch (_) { return null; }
  }
  function host(value) { return value.hostname.toLowerCase().replace(/\.$/, ''); }
  function domain(host, expected) { return host === expected || host.endsWith('.' + expected); }
  function excluded(host, policy) { return (policy.exceptions || []).some(value => domain(host, value)); }
  function youtube(url) {
    const value = parsed(url);
    return !!value && ORIGINS.has(host(value)) && !ACCOUNT_PATH.test(value.pathname);
  }
  function enabled(url, policy) {
    const value = parsed(url);
    return !!policy && policy.enabled === true && youtube(url) && !excluded(host(value), policy);
  }
  function blocked(url, top, policy) {
    const resource = parsed(url);
    if (!resource || !enabled(top, policy) || excluded(host(resource), policy)) return false;
    return policy.networkRules.some(rule => domain(host(resource), rule.host) &&
      (!rule.pathPrefix || resource.pathname.startsWith(rule.pathPrefix)));
  }
  function send(message) {
    if (!nativePort) return false;
    try { nativePort.postMessage(message); return true; } catch (_) { return false; }
  }
  function count(type, amount) {
    if (!COUNTERS.has(type) || !Number.isInteger(amount) || amount < 1 || amount > 10000) return;
    counts.set(type, (counts.get(type) || 0) + amount);
    if (flushTimer) return;
    flushTimer = setTimeout(() => {
      flushTimer = null;
      for (const [kind, total] of counts) {
        let remaining = total;
        while (remaining > 0) {
          const chunk = Math.min(10000, remaining);
          if (!send({type: kind, count: chunk})) break;
          remaining -= chunk;
        }
        if (remaining) counts.set(kind, remaining); else counts.delete(kind);
      }
    }, 250);
  }

  async function configure(packet) {
    if (!Number.isInteger(packet.revision) || !packet.config || typeof packet.script !== 'string' ||
        !Array.isArray(packet.config.networkRules) || typeof packet.config.token !== 'string') return;
    configuring = true;
    // Main-document requests wait through registration replacement, so no document starts in the gap.
    if (attemptedConfiguration) gate = new Promise(resolve => { releaseGate = resolve; });
    attemptedConfiguration = true;
    try {
      for (const registered of registrations) await registered.unregister();
      registrations = [];
      const policy = JSON.parse(JSON.stringify(packet.config));
      const isolated = await browser.contentScripts.register({
        matches: MATCHES, excludeMatches: EXCLUDED, allFrames: false, runAt: 'document_start', world: 'ISOLATED',
        js: [{code: 'globalThis.__weiktubeGeckoPolicy=' + JSON.stringify(policy) + ';'}, {file: 'status-bridge.js'}]
      });
      registrations.push(isolated);
      const page = await browser.contentScripts.register({
        matches: MATCHES, excludeMatches: EXCLUDED, allFrames: false, runAt: 'document_start', world: 'MAIN',
        // Match patterns are case-sensitive. This guard also excludes mixed-case auth URLs.
        js: [{code: '(function(){if(' + ACCOUNT_PATH.toString() + '.test(location.pathname))return;\n' + packet.script + '\n})();'}]
      });
      registrations.push(page);
      current = policy;
      configuring = false;
      releaseGate(true);
      send({type: 'configured', revision: packet.revision, version: policy.version});
    } catch (_) {
      // Never navigate an unfiltered replacement page after a registration failure.
      configuring = true;
      releaseGate(false);
      send({type: 'configure-error', revision: packet.revision});
    }
  }

  browser.webRequest.onBeforeRequest.addListener(details => {
    if (details.type === 'main_frame') {
      if (!youtube(details.url)) { pages.delete(details.tabId); return {}; }
      if (!configuring && current) {
        pages.set(details.tabId, current);
        return {};
      }
      return Promise.race([gate, new Promise(resolve => setTimeout(() => resolve(false), 15000))]).then(ready => {
        if (!ready || configuring || !current) return {cancel: true};
        pages.set(details.tabId, current);
        return {};
      });
    }
    // No tab URL fallback: login/consent subframes must not inherit a YouTube filter policy.
    const top = details.documentUrl || details.originUrl || '';
    const policy = pages.get(details.tabId);
    if (!policy || !youtube(top) || !blocked(details.url, top, policy)) return {};
    count('network', 1);
    return {cancel: true};
  }, {urls: REQUESTS}, ['blocking']);

  browser.runtime.onMessage.addListener((message, sender) => {
    if (!sender.tab || sender.frameId !== 0 || !youtube(sender.url) ||
        !message || message.type !== 'status' || typeof message.token !== 'string') return;
    // The isolated bridge sends its pinned policy before page code; this also handles restored pages.
    const known = pages.get(sender.tab.id);
    const policy = known && known.token === message.token ? known :
      current && current.token === message.token ? current : null;
    if (!policy || message.version !== policy.version) return;
    if (!known) pages.set(sender.tab.id, policy);
    if (COUNTERS.has(message.event)) count(message.event, message.count);
    else if (message.event === 'error') send({type: 'error'});
    else if (message.event === 'ready') send({type: 'ready'});
  });

  function connect() {
    try {
      const connected = browser.runtime.connectNative('weiktubeAdBlock');
      nativePort = connected;
      connected.onMessage.addListener(packet => {
        if (!packet || packet.type !== 'configure') return;
        configurationQueue = configurationQueue.then(() => configure(packet)).catch(() => {});
      });
      connected.onDisconnect.addListener(() => {
        if (nativePort !== connected) return;
        nativePort = null;
        setTimeout(connect, 1000);
      });
    } catch (_) { setTimeout(connect, 1000); }
  }
  connect();
})();
