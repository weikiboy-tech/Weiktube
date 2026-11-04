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
assert.match(player, /val allowed = documentCommitted && playbackAllowed && activity\.playerMayRun/);
assert.match(player, /!isYoutube \|\| adHooks\[view\]\?\.canPlay == true/,
  'existing protected-playback gate stays enforced');
assert.match(ui, /key\(p\.view\)/);
assert.match(ui, /val landscapeVideo = full && !a\.pip && orientation == android\.content\.res\.Configuration\.ORIENTATION_LANDSCAPE/,
  'system PiP is not the phone landscape viewport');
assert.match(ui, /setSystemInsetsHandledByHost\(!landscapeVideo\)/,
  'Mini and system PiP must explicitly clear phone system-bar/cutout insets');
assert.doesNotMatch(ui, /setSystemInsetsHandledByHost\(!landscapeVideo && !a\.pip\)/,
  'do not reintroduce the phone safe area in the small PiP window');
const minimizedByBack = block(activity, 'player.source != null && player.expanded -> {', 'isPersonalHome && homeCanGoBack');
assert.match(minimizedByBack, /minimizeWebVideo\(\)/,
  'Android Back must use the same retained-player minimization path as the down arrow/drag');
assert.match(minimizedByBack, /originalHomePlayer && OriginalHomeReturn\.resumeToken\(\) != null/);
assert.match(minimizedByBack, /openOriginalHome\(\)/, 'Back retains the original Home return');
assert.doesNotMatch(minimizedByBack, /player\.(pause|pauseView|close|destroy|open)\(/,
  'minimizing an Original Home video is not a stop/reload/pause command');
assert.doesNotMatch(block(activity, 'fun minimizeWebVideo()', 'fun goHome()'), /player\.(pause|pauseView|close|destroy|open)\(/,
  'the shared minimization path changes presentation only');
assert.match(ui, /p\.setPresentation\(mini, dark, landscapeVideo \|\| a\.pip, systemPip = a\.pip\)/);
assert.match(ui, /p\.setPlaybackAllowed\(!actions && a\.playerMayRun/);
const miniControls = block(ui, 'if (mini && playerVisible)', 'if (p.error != null)');
assert.match(miniControls, /val miniPlaying = p\.playing/);
assert.match(miniControls, /onClick = if \(miniPlaying\) p::pause else p::play/,
  'the rendered Mini action sends explicit Play/Pause even if DOM state changes before the click');
assert.match(miniControls, /Icon\(if \(miniPlaying\) Icons\.Rounded\.Pause else Icons\.Rounded\.PlayArrow/);
assert.doesNotMatch(miniControls, /p::toggle|p\.toggle\(/, 'Mini Play must never toggle a newer DOM state to Pause');
assert.doesNotMatch(ui, /if \(!p\.isYoutube\) IconButton\(onClick = a::enterPip\)/);
assert.doesNotMatch(player + native, /Object\.defineProperty\([^\n]*(hidden|visibilityState)|pauseTimers\(\)/,
  'do not spoof page visibility or freeze every WebView');
console.log('PASS native PiP integration source contracts: retained player, Back minimizes without pause, small-window insets, lifecycle, eligibility, ad gate, no background/overlay replacement. No device test.');
