const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const script=fs.readFileSync('app/src/main/java/com/weiktube/app/VideoZoomScript.kt','utf8').split('"""')[1];
function fixture(w=1000,h=600,vw=1920,vh=1080){
 const styles=new Map(),listeners={};let s=1,x=0,y=0,commands=0;
 const v={videoWidth:vw,videoHeight:vh,currentTime:37,paused:false,muted:false,playbackRate:1.5,
  style:{getPropertyValue:p=>styles.get(p)?.[0]||'',getPropertyPriority:p=>styles.get(p)?.[1]||'',setProperty(p,val,priority){styles.set(p,[val,priority]);if(p==='transform'){const n=val.match(/-?[\d.]+/g).map(Number);[x,y,s]=n}},removeProperty(p){styles.delete(p);if(p==='transform'){s=1;x=y=0}}},
  getBoundingClientRect(){return {width:w*s,height:h*s,left:x,top:y}},play(){commands++},pause(){commands++},load(){commands++}};
 const c={location:{hostname:'m.youtube.com',pathname:'/watch'},innerWidth:w,innerHeight:h,document:{querySelector:()=>v,addEventListener(n,f){(listeners[n]??=[]).push(f)}},addEventListener(n,f){(listeners[n]??=[]).push(f)}};c.window=c;c.top=c;vm.createContext(c);vm.runInContext(script,c);
 return {v,c,styles,listeners,zoom:(z,fx=.5,fy=.5)=>c.weiktubeVideoZoom(z,fx,fy),geometry:()=>({s,x,y}),commands:()=>commands};
}
let count=0;function test(n,f){f();count++;console.log('PASS '+n)}
for(const [vw,vh] of [[1920,1080],[1440,1080],[1080,1920],[1000,1000]])test(`contain/fill/clipping ${vw}:${vh}`,()=>{const f=fixture(1000,600,vw,vh),mw=Math.min(1000,600*vw/vh),mh=mw*vh/vw,fill=Math.max(1000/mw,600/mh);f.zoom(fill);let {s,x,y}=f.geometry();assert.ok(Math.abs(s-fill)<1e-5);assert.ok(x+(1000-mw)*s/2<=.001);assert.ok(y+(600-mh)*s/2<=.001);assert.ok(x+(1000+mw)*s/2>=999.999);assert.ok(y+(600+mh)*s/2>=599.999);assert.ok(f.styles.get('clip-path')[0].startsWith('inset('));f.zoom(1);assert.equal(f.styles.size,0)});
test('bounded zoom and finite inputs',()=>{const f=fixture();f.zoom(100);assert.equal(f.geometry().s,8);f.zoom(NaN);assert.equal(f.geometry().s,8);f.zoom(-4);assert.equal(f.geometry().s,1)});
test('focus moves zoom around actual finger midpoint',()=>{const left=fixture(),right=fixture();left.zoom(2,.25,.5);right.zoom(2,.75,.5);assert.ok(left.geometry().x>right.geometry().x);left.zoom(3,.25,.5);assert.equal(left.geometry().x,-500)});
test('resize and source metadata restore original styles',()=>{const f=fixture();f.v.style.setProperty('object-fit','contain','');f.zoom(3);f.listeners.resize[0]();assert.equal(f.geometry().s,1);assert.deepEqual(f.styles.get('object-fit'),['contain','']);f.zoom(2);f.listeners.loadedmetadata[0]({target:f.v});assert.equal(f.geometry().s,1)});
test('ten zoom mini/pip/reset cycles retain media and playback',()=>{const f=fixture(),v=f.v;for(let i=0;i<10;i++){f.zoom(2);f.zoom(4);f.zoom(1);assert.equal(f.geometry().s,1)}assert.equal(f.v,v);assert.equal(v.currentTime,37);assert.equal(v.paused,false);assert.equal(v.muted,false);assert.equal(v.playbackRate,1.5);assert.equal(f.commands(),0);vm.runInContext(script,f.c);assert.equal(f.listeners.resize.length,1)});
test('unknown media geometry waits without corrupting styles',()=>{const f=fixture(0,0,0,0);f.zoom(2);assert.equal(f.styles.size,0)});
console.log(`${count} video zoom fixtures passed. No device playback claim.`);
