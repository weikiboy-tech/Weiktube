"""Exercise the shipped Gecko progress-module lifecycle, without claiming an Android test.

The real module setter/onEnable/onDisable/bootstrap bodies come from APK omni.ja.
Only Android's Activity event side effects are modeled; the model is checked against
the relevant production callback statements. --source accepts an old Return.kt to
demonstrate that the previous null-delegate parking fails this regression.
"""
from argparse import ArgumentParser
from hashlib import sha256
from io import BytesIO
import json
from pathlib import Path
import re
import subprocess
from zipfile import ZipFile


def method_body(source: str, marker: str) -> str:
    """These selected engine methods contain balanced braces, no brace strings."""
    start = source.index("{", source.index(marker)) + 1
    depth, end = 1, start
    while depth:
        if source[end] == "{":
            depth += 1
        elif source[end] == "}":
            depth -= 1
        end += 1
    return source[start:end - 1]


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, default=root / "Weiktube-Home-Test.apk")
    parser.add_argument("--source", type=Path, default=root / "home-data/src/main/java/com/weiktube/home/OriginalHomeReturn.kt")
    args = parser.parse_args()
    source = args.source.read_text(encoding="utf-8-sig")
    parking = source.split("internal fun park(", 1)[1].split("fun discard()", 1)[0]
    assignment = re.search(r"session\.progressDelegate\s*=\s*([^\r\n]+)", parking)
    if not assignment:
        raise AssertionError("No production parked ProgressDelegate assignment found")
    expression = assignment.group(1).split("//", 1)[0].strip()
    if expression == "null":
        parked_enabled = False
    elif re.fullmatch(r"object\s*:\s*GeckoSession\.ProgressDelegate\s*\{\s*\}", expression):
        parked_enabled = True
    else:
        raise AssertionError("Unknown parked ProgressDelegate; review the lifecycle fixture")

    # Bind the modeled Activity effects to the code that actually consumes these events.
    activity = (root / "home-data/src/main/java/com/weiktube/home/OriginalHomeActivity.kt").read_text(encoding="utf-8-sig")
    page_start = activity.split("override fun onPageStart(", 1)[1].split("override fun onProgressChange(", 1)[0]
    for statement in ("disconnect(session)", "documentSerial[session] = (documentSerial[session] ?: 0) + 1", "locations[session] = url"):
        if statement not in page_start:
            raise AssertionError("Activity page-start behavior changed; review the modeled side effects")
    if "progressDelegate = object : GeckoSession.ProgressDelegate" not in activity:
        raise AssertionError("Activity no longer installs its progress delegate")
    current = activity.split("private fun currentConnection(", 1)[1].split("private fun sendEnable(", 1)[0]
    if "OriginalHomePolicy.isYoutubePage(locations[session].orEmpty())" not in current:
        raise AssertionError("Activity routing precondition changed; review the fixture")

    with ZipFile(args.apk) as apk:
        with ZipFile(BytesIO(apk.read("assets/omni.ja"))) as omni:
            module = omni.read("chrome/geckoview/content/geckoview.js").decode("utf-8")
            progress = omni.read("modules/GeckoViewProgress.sys.mjs").decode("utf-8")
    progress_class = progress.split("export class GeckoViewProgress extends GeckoViewModule", 1)[1]
    payload = {
        "setter": method_body(module, "set enabled(aEnabled)"),
        "onEnable": method_body(progress_class, "onEnable()"),
        "onDisable": method_body(progress_class, "onDisable()"),
        "bootstrap": method_body(progress_class, "_fireInitialLoad() {"),
        "parkedEnabled": parked_enabled,
    }
    if "this._fireInitialLoad();" not in payload["onEnable"]:
        raise AssertionError("Bundled Gecko bootstrap changed; review this regression")
    print("Engine module SHA256: " + sha256(module.encode()).hexdigest(), flush=True)
    print("Engine progress SHA256: " + sha256(progress.encode()).hexdigest(), flush=True)
    node = r"""
const assert = require('node:assert/strict');
const vm = require('node:vm');
const p = JSON.parse(require('node:fs').readFileSync(0, 'utf8'));
const noop = () => {};
const globals = {
  debug: noop, ProgressTracker: class {}, SecurityTracker: class {}, StateTracker: class {},
  Ci: { nsIWebProgress: { NOTIFY_STATE_NETWORK: 1, NOTIFY_SECURITY: 2, NOTIFY_LOCATION: 4 } },
  Cc: { '@mozilla.org/appshell/component/browser-status-filter;1': {
    createInstance: () => ({ addProgressListener: noop, removeProgressListener: noop })
  } }, Services: { obs: { addObserver: noop, removeObserver: noop } }
};
const Progress = vm.runInNewContext(`(class {
  onEnable() { ${p.onEnable} }
  onDisable() { ${p.onDisable} }
  _fireInitialLoad() { ${p.bootstrap} }
  onSettingsUpdate() {}
})`, globals);
const Module = vm.runInNewContext(`(class { set enabled(aEnabled) { ${p.setter} } })`);

function fixture() {
  const events = [];
  const state = { location: 'https://m.youtube.com/', serial: 7, portConnected: true,
    canGoBack: true, scroll: 620, session: {} };
  const progress = new Progress();
  // Model only the documented Android callback effects, not its execution or touch delivery.
  progress.eventDispatcher = { sendRequest(name, data) {
    events.push({ name, ...data });
    if (name === 'GeckoView:PageStart') {
      state.portConnected = false; state.serial++; state.location = data.uri;
    } else if (name === 'GeckoView:LocationChange') {
      state.location = data.uri; state.canGoBack = data.canGoBack;
    }
  } };
  progress.browser = { addProgressListener: noop, removeProgressListener: noop };
  progress.registerListener = noop; progress.unregisterListener = noop;
  const module = new Module();
  Object.assign(module, { _enabled: false, _impl: progress, _loadResource: noop, _loadActors: noop });
  module.enabled = true; // Real first-open bootstrap is still expected.
  assert.equal(events.length, 3);
  state.location = 'https://m.youtube.com/results?search_query=mario';
  state.serial = 7; state.portConnected = true; state.canGoBack = true;
  events.length = 0;
  return { module, progress, events, state };
}
function selectable(state) {
  const url = new URL(state.location);
  return state.portConnected && url.protocol === 'https:' &&
    ['m.youtube.com', 'www.youtube.com', 'youtube.com'].includes(url.hostname);
}

// Establish the exact old failure using the shipped engine implementation.
const old = fixture();
old.module.enabled = false; old.module.enabled = true;
assert.deepEqual(old.events.map(x => x.name),
  ['GeckoView:PageStart', 'GeckoView:LocationChange', 'GeckoView:PageStop']);
assert.equal(old.state.location, 'about:blank');
assert.equal(old.state.serial, 8); assert.equal(old.state.portConnected, false);
assert.equal(selectable(old.state), false);
console.log('REPRODUCED old null parking: synthetic about:blank, modeled port disconnect and serial change; retained-page routing becomes false.');

// Apply the actual production parked-delegate choice across ten return cycles.
const active = fixture();
const session = active.state.session;
for (let cycle = 0; cycle < 10; cycle++) {
  assert.equal(selectable(active.state), true, 'Fixture page must be selectable before parking');
  active.state.location = cycle % 2 ? 'https://m.youtube.com/@channel/videos' :
    'https://m.youtube.com/results?search_query=mario';
  active.state.scroll = 620 + cycle * 210;
  const before = { ...active.state };
  active.module.enabled = p.parkedEnabled;
  active.module.enabled = true;
  assert.equal(active.events.length, 0,
    'Production parking must not replay synthetic about:blank when restoring the retained document');
  assert.deepEqual(active.state, before, 'Return must preserve modeled URL/serial/port/history/scroll');
  assert.equal(active.state.session, session);
  assert.equal(selectable(active.state), true, 'The retained page must remain eligible for routing');
}

// Keeping the delegate alive must not suppress a real next-document load.
active.progress.eventDispatcher.sendRequest('GeckoView:PageStart', {
  uri: 'https://m.youtube.com/results?search_query=another'
});
assert.equal(active.state.portConnected, false); assert.equal(active.state.serial, 8);
assert.equal(active.state.location, 'https://m.youtube.com/results?search_query=another');
console.log('PASS bundled Gecko progress lifecycle: ten retained-document returns (search/channel metadata), no bootstrap replay; genuine navigation still invalidates the modeled old document. Android Activity, native player, touch and actual scroll tests are NOT performed.');
"""
    completed = subprocess.run(["node", "-e", node], input=json.dumps(payload), text=True, check=False)
    return completed.returncode


if __name__ == "__main__":
    raise SystemExit(main())
