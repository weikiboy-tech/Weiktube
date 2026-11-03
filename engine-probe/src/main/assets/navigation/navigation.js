/* Only runs in top-level YouTube documents, never on Google sign-in pages. */
(() => {
  if (window.top !== window) return;
  let role = null, paused = true, connected = false;
  const port = browser.runtime.connectNative('weiktube');
  const pause = () => document.querySelectorAll('video,audio').forEach(v => v.pause());
  port.onDisconnect.addListener(() => { connected = false; paused = true; pause(); });
  port.onMessage.addListener(m => {
    if (m.type === 'pause') { pause(); return; }
    if (m.type !== 'configure') return;
    role = m.role; paused = !!m.paused; connected = true;
    if (paused) pause();
    const apply = () => {
      let style = document.getElementById('weiktube-navigation-style');
      if (!style) { style = document.createElement('style'); style.id='weiktube-navigation-style'; document.documentElement.appendChild(style); }
      style.textContent = 'ytm-pivot-bar-renderer{display:none!important}' + (role==='player' ? 'ytm-mobile-topbar-renderer{display:none!important}' : '');
    };
    if (document.documentElement) apply(); else document.addEventListener('DOMContentLoaded', apply, {once:true});
  });
  document.addEventListener('play', () => { if (paused) pause(); }, true);
  document.addEventListener('visibilitychange', () => { if (document.hidden) pause(); });
  function videoUrl(href) {
    try {
      const u = new URL(href, location.href);
      if (u.protocol !== 'https:' || u.username || u.password || u.port || !['youtube.com','www.youtube.com','m.youtube.com','youtu.be'].includes(u.hostname)) return null;
      const p=u.pathname.split('/').filter(Boolean);
      const id=u.hostname==='youtu.be' && p.length===1 ? p[0] : u.pathname==='/watch' ? u.searchParams.get('v') : p.length===2 && ['shorts','live','embed'].includes(p[0]) ? p[1] : null;
      return /^[A-Za-z0-9_-]{11}$/.test(id || '') ? u.href : null;
    } catch { return null; }
  }
  document.addEventListener('click', event => {
    if (!connected || role!=='feed' || !event.isTrusted || event.button!==0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    const anchor=event.target?.closest?.('a[href]');
    const url=anchor && videoUrl(anchor.href);
    if (!url) return;
    event.preventDefault(); event.stopImmediatePropagation(); pause();
    port.postMessage({type:'video',url});
  }, true);
})();
