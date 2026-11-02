// Small, deterministic smoke test for the embedded JavaScript templates; no network.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/weiktube/app/PlayerController.kt'), 'utf8');
const scripts = [...source.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m => m[1]);
assert.equal(scripts.length, 2);
let state = 1;
let config;
let playerSize;
const youtube = { YT: { Player: function(id, options) {
    config = options;
    return {
        getPlayerState: () => state, getCurrentTime: () => 83,
        getVideoData: () => ({title:'A real player title'}),
        pauseVideo: () => {state=2;}, playVideo: () => {state=1;}
        ,setSize: (width, height) => { playerSize = [width, height]; }
    };
} } };
youtube.document = { getElementById: () => ({clientHeight: 0, style: {setProperty() {}}}) };
youtube.window = { innerWidth: 363, innerHeight: 200, addEventListener() {} };
vm.createContext(youtube);
vm.runInContext(scripts[0].replaceAll('$id', 'abcdefghijk').replaceAll('${Links.startSeconds(url)}', '83'), youtube);
youtube.onYouTubeIframeAPIReady();
config.events.onReady();
assert.deepEqual(playerSize, [363, 200]);
assert.equal(config.playerVars.start, 83);
assert.equal(config.playerVars.origin, 'https://com.weiktube.app');
assert.equal(youtube.weiktubeState().playing, true);
youtube.toggle(); assert.equal(youtube.weiktubeState().playing, false);
youtube.toggle(); assert.equal(youtube.weiktubeState().playing, true);
youtube.pause(); assert.equal(youtube.weiktubeState().playing, false);
assert.equal(youtube.weiktubeState().position, 83);
config.events.onError({data: 101}); assert.match(youtube.weiktubeState().error, /101/);
const video = { paused: true, currentTime: 12, error: null,
    pause(){this.paused=true;}, play(){this.paused=false;return Promise.resolve();} };
const direct = {document: {getElementById: () => video}};
vm.createContext(direct);
vm.runInContext(scripts[1].replaceAll('${JSONObject.quote(url)}', JSON.stringify('https://example.org/movie.mp4')), direct);
assert.equal(video.src, 'https://example.org/movie.mp4');
direct.toggle(); assert.equal(direct.weiktubeState().playing, true);
direct.pause(); assert.equal(direct.weiktubeState().playing, false);
video.error={code:4}; assert.ok(direct.weiktubeState().error);
console.log('PASS: YouTube and HTTPS player scripts — syntax, state, controls, errors and source configuration.');
