// Source contracts for app wiring. Android window-manager/device playback is NOT simulated here.
const fs = require('node:fs');
const assert = require('node:assert/strict');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const read = name => fs.readFileSync(path.join(root, name), 'utf8');
const dir = 'app/src/main/java/com/weiktube/app/';
const activity = read(dir + 'MainActivity.kt');
const player = read(dir + 'PlayerController.kt');
const ui = read(dir + 'WeiktubeUi.kt');
const native = read(dir + 'NativePipController.kt');
const block = (source, from, to) => {
  const start = source.indexOf(from), end = source.indexOf(to, start + from.length);
  assert(start >= 0 && end > start, `missing boundaries ${from}`);
  return source.slice(start, end);
};
assert.match(read('app/src/main/AndroidManifest.xml'), /android:supportsPictureInPicture="true"/);
assert.doesNotMatch(block(activity, 'override fun onPause()', 'override fun onStop()'), /player\.(pause|close|destroy)/,
  'PiP can enter after onPause: do not pause the retained player here');
const stop = block(activity, 'override fun onStop()', 'override fun onStart()');
assert(stop.indexOf('foreground = false') < stop.indexOf('player.pauseView()'), 'remove playback permission before stop');
assert.match(stop, /player\.pauseView\(\)/, 'closing/hidden PiP must stop playback');
assert.match(activity, /val playerMayRun get\(\) = foreground && !screenOff && !isFinishing && !isDestroyed/,
  'stale PiP flag must not authorize hidden/locked playback');
const mode = block(activity, 'override fun onPictureInPictureModeChanged(', 'override fun onPause()');
assert.doesNotMatch(mode, /player\.(open|close|play)\(/, 'Expand must not reload, close, or override a user pause');
assert.match(activity, /override fun onUserLeaveHint\(\)/);
assert.match(native, /Build\.VERSION\.SDK_INT < 31\) enter\(manual = false\) else sync\(\)/);
assert.doesNotMatch(native, /WebView\(|PlayerController\(|loadUrl\(|loadData|SYSTEM_ALERT_WINDOW/,
  'Android PiP controller must never own a second playback document or overlay');
assert.match(player, /val allowed = playbackAllowed && activity\.playerMayRun/);
assert.match(player, /!isYoutube \|\| adHooks\[view\]\?\.canPlay == true/,
  'existing protected-playback gate stays enforced');
assert.match(ui, /key\(p\.view\)/);
assert.match(ui, /p\.setPresentation\(mini, dark, landscapeVideo \|\| a\.pip, systemPip = a\.pip\)/);
assert.match(ui, /p\.setPlaybackAllowed\(!actions && a\.playerMayRun/);
assert.doesNotMatch(ui, /if \(!p\.isYoutube\) IconButton\(onClick = a::enterPip\)/);
assert.doesNotMatch(player + native, /Object\.defineProperty\([^\n]*(hidden|visibilityState)|pauseTimers\(\)/,
  'do not spoof page visibility or freeze every WebView');
console.log('PASS native PiP integration source contracts: retained player, lifecycle, eligibility, ad gate, no background/overlay replacement. No device test.');
