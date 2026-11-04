import assert from 'node:assert/strict';
import fs from 'node:fs';

// Source contracts plus a deterministic lifecycle model, NOT an Android/Gecko device test.
// The scheduling rule below was verified in the bundled GeckoView 159.0.20261022150525
// assets/omni.ja, not inferred from the app:
//   chrome/toolkit/content/extensions/parent/ext-backgroundPage.js:1167-1186
//     APP_STARTUP waits for ExtensionParent.browserStartupPromise; install/update does not.
//   modules/ExtensionParent.sys.mjs:2589-2592
//     browserStartupPromise waits for sessionstore-windows-restored/extensions-late-startup.
//   chrome/geckoview/content/geckoview.js:799-804
//     the first Gecko window emits extensions-late-startup from InitLater.
// The model's failure control demonstrates the circular wait in the previous app order.

const root = new URL('../home-data/src/main/java/com/weiktube/home/', import.meta.url);
const read = name => fs.readFileSync(new URL(name, root), 'utf8');
const engine = read('HomeEngine.kt');
const home = read('OriginalHomeController.kt');
const diagnostic = read('HomeConnectionActivity.kt');
const retained = read('OriginalHomeReturn.kt');
const between = (source, start, end) => {
  const from = source.indexOf(start);
  const to = source.indexOf(end, from + start.length);
  assert(from >= 0 && to > from, `source boundaries missing: ${start}`);
  return source.slice(from, to);
};
assert(!engine.includes('GeckoAdBlock.prepare('), 'profile preparation must not wait for a background before the first window');
assert(engine.includes('HomeExperimentLease.recover('), 'existing profile/lease recovery stays in place');

const homePrepare = between(home, 'private fun prepareRouting(', 'private fun setupFailed(');
const diagnosticPrepare = between(diagnostic, 'private fun prepare()', 'private fun loadInitialPage()');
for (const [name, source] of [['Home', homePrepare], ['diagnostic', diagnosticPrepare]]) {
  assert.equal((source.match(/session\.open\(engine\)/g) || []).length, 1, `${name}: one existing main session`);
  assert(source.indexOf('session.open(engine)') < source.indexOf('GeckoAdBlock.prepare('), `${name}: window must open before waiting for protection`);
  assert(!source.includes('loadUri('), `${name}: setup delegates navigation to a protected loader`);
  const gate = source.slice(source.indexOf('GeckoAdBlock.prepare('));
  assert(gate.includes('main !== session'), `${name}: late readiness checks session identity`);
  assert(gate.includes('stopSessions()'), `${name}: failure closes the blank session, not an unprotected website`);
}
const homeLoad = between(home, 'private fun loadPendingNavigation()', 'fun reload()');
assert(/if \([^\n]*preparing\) return/.test(homeLoad), 'Home queued navigation must wait for protection');
assert(/if \(preparing && request\.uri != "about:blank"\) return decision\(false\)/.test(home), 'Home native navigation is gated');
assert.equal((home.match(/if \(preparing && request\.uri != "about:blank"\) return decision\(false\)/g) || []).length, 2, 'Home main-frame and subframe navigation are gated');
const homePopup = between(home, 'override fun onNewSession(', 'override fun onLoadError(');
assert(homePopup.includes('if (!active || preparing || !owns(session)) return null'), 'Home popup creation cannot bypass the gate');
const homeLocation = between(home, 'override fun onLocationChange(', 'override fun onCanGoBack(');
assert(homeLocation.includes('if (preparing && url == "about:blank") return'), 'blank bootstrap must retain startup progress/status');
assert(home.includes('if (preparing || (popup ?: main) in autofillLoading) View.VISIBLE else View.GONE'), 'tab return must retain startup progress');
assert(/fun reload\(\)[\s\S]*?if \(disposed \|\| preparing\) return/.test(home), 'Home reload cannot bypass the gate');
const diagnosticLoad = between(diagnostic, 'private fun loadInitialPage()', 'private fun openLogin()');
assert(diagnosticLoad.indexOf('!protectionReady') < diagnosticLoad.indexOf('session.loadUri(HOME)'), 'diagnostic first load is gated');
assert(diagnostic.includes('if (!protectionReady) return GeckoResult.fromValue(AllowOrDeny.DENY)'), 'diagnostic native navigation is gated');
assert(diagnostic.includes('else if (protectionReady) session.loadUri(HOME)'), 'diagnostic explicit reload is gated');
assert(diagnostic.includes('if (!active || !protectionReady) return@button'), 'profile switching is gated');
assert(diagnostic.includes('preparing = false; protectionReady = false; initialNavigationPending = false'), 'stop clears only per-session preparation state');

// The handler itself is exercised with JVM session/lease fakes in HomeContentRecoveryTest.
// These contracts connect those tests to every real Gecko ContentDelegate, including parked pages.
for (const [name, source] of [['Home', home], ['parked Home', retained], ['diagnostic', diagnostic]]) {
  const callbacks = source.slice(source.indexOf('override fun onCrash('));
  assert(callbacks.includes('contentRecovery.lost(session, HomeContentLoss.CRASHED)'), `${name}: content crash releases the owned document`);
  assert(callbacks.includes('override fun onKill(session: GeckoSession) { contentRecovery.lost(session, HomeContentLoss.KILLED) }'), `${name}: content kill must not fall through Gecko's no-op default`);
}
const visible = between(home, 'fun setVisible(', 'private fun resumeOrPrepare(');
assert(visible.indexOf('firstOrNull { !it.isOpen }') < visible.indexOf('if (active &&'), 'closed retained windows are checked before the active-view fast path');
assert(visible.includes('active && !lostContent'), 'a missed kill callback must bypass the healthy-document fast path');
const take = between(retained, 'internal fun take(', 'internal fun park(');
assert(take.includes('held.retained.sessions().any { !it.isOpen }'), 'parked closed windows cannot be handed back as a working document');
assert(take.includes('discard()'), 'a lost parked document releases its bridge and extension lease');

class Clock {
  time = 0;
  tasks = [];
  later(delay, callback) { this.tasks.push({at: this.time + delay, callback}); }
  advance(end) {
    for (;;) {
      this.tasks.sort((a, b) => a.at - b.at);
      if (!this.tasks.length || this.tasks[0].at > end) break;
      const task = this.tasks.shift(); this.time = task.at; task.callback();
    }
    this.time = end;
  }
}
class GeckoStartupModel {
  constructor(clock, mode, rejectPolicy = false) {
    Object.assign(this, {clock, mode, rejectPolicy, windows: 0, trace: [], waiters: [], configured: mode === 'warm'});
    this.background = mode !== 'cold';
  }
  event(type) { this.trace.push({type, at: this.clock.time}); }
  openWindow() {
    this.windows++; this.event('blank-window');
    this.clock.later(1, () => {
      this.event('extensions-late-startup'); this.background = true;
      this.waiters.splice(0).forEach(start => start());
    });
  }
  preparePolicy(ready, failure) {
    this.event('prepare-policy');
    let settled = false;
    const finish = ok => {
      if (settled) return;
      settled = true; this.event(ok ? 'policy-ack' : 'policy-failure');
      this.configured = ok; (ok ? ready : failure)();
    };
    this.clock.later(20_000, () => finish(false));
    const configure = () => this.clock.later(this.configured ? 0 : 3, () => finish(!this.rejectPolicy));
    if (this.background) configure(); else this.waiters.push(configure);
  }
}
class ControllerModel {
  constructor(runtime, openFirst) {
    Object.assign(this, {runtime, openFirst, active: true, preparing: false, ready: false, generation: 0, pending: '/', loads: []});
  }
  start() {
    const generation = ++this.generation;
    this.preparing = true;
    if (this.openFirst) this.runtime.openWindow();
    this.runtime.preparePolicy(() => {
      if (generation !== this.generation) return;
      if (!this.openFirst) this.runtime.openWindow();
      this.ready = true; this.preparing = false; this.load();
    }, () => { if (generation === this.generation) { this.preparing = false; this.ready = false; } });
  }
  navigate(path) { this.pending = path; this.load(); }
  load() {
    if (!this.active || this.preparing || !this.ready || this.pending === null) return;
    assert(this.runtime.configured, 'no external navigation before the policy acknowledgement');
    this.loads.push(this.pending); this.pending = null; this.runtime.event('website-load');
  }
  stop() { this.generation++; this.active = false; this.preparing = false; this.ready = false; this.pending = null; }
}
function scenario(mode, openFirst, reject = false) {
  const clock = new Clock();
  const runtime = new GeckoStartupModel(clock, mode, reject);
  const controller = new ControllerModel(runtime, openFirst);
  controller.start();
  return {clock, runtime, controller};
}

const old = scenario('cold', false);
old.clock.advance(20_000);
assert.equal(old.runtime.windows, 0);
assert.deepEqual(old.controller.loads, []);
assert.equal(old.runtime.trace.at(-1).type, 'policy-failure', 'negative control: old ordering times out before creating a window');

for (const source of [homePrepare, diagnosticPrepare]) {
  const openFirst = source.indexOf('session.open(engine)') < source.indexOf('GeckoAdBlock.prepare(');
  for (const mode of ['cold', 'install', 'warm']) {
    for (let attempt = 0; attempt < 20; attempt++) {
      const {clock, runtime, controller} = scenario(mode, openFirst);
      controller.navigate('/results?search_query=test');
      assert.deepEqual(controller.loads, [], 'queued navigation cannot load before acknowledgement');
      clock.advance(20);
      assert.equal(runtime.windows, 1, 'no second/hidden bootstrap session');
      assert.deepEqual(controller.loads, ['/results?search_query=test']);
      assert(runtime.trace.findIndex(x => x.type === 'policy-ack') < runtime.trace.findIndex(x => x.type === 'website-load'));
      clock.advance(20_000);
      assert.equal(controller.loads.length, 1, 'expired deadline cannot trigger another load');
    }
  }
  const failed = scenario('cold', openFirst, true);
  failed.controller.navigate('/account'); failed.clock.advance(20_000);
  assert.deepEqual(failed.controller.loads, [], 'policy failure never opens an unprotected page');
  const cancelled = scenario('cold', openFirst);
  cancelled.clock.advance(2); cancelled.controller.stop(); cancelled.clock.advance(20_000);
  assert.deepEqual(cancelled.controller.loads, [], 'stale acknowledgement cannot revive a stopped screen');
  const paused = scenario('cold', openFirst);
  paused.controller.active = false; paused.clock.advance(20);
  assert.deepEqual(paused.controller.loads, []);
  paused.controller.active = true; paused.controller.load();
  assert.deepEqual(paused.controller.loads, ['/'], 'completed setup resumes on the same session');
  assert.equal(paused.runtime.windows, 1);
}
console.log('PASS: cold Home source contracts and Gecko startup lifecycle model (120 startup cycles, failure/cancellation/pause cases). Not a device or real Gecko integration test.');
