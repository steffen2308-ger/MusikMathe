const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('app/src/main/assets/video-markers.js','utf8');
const rect=(left,top,width,height)=>({left,top,right:left+width,bottom:top+height,width,height});
function video(url,title,box){return {currentSrc:url,src:url,title,poster:'',readyState:2,videoWidth:640,videoHeight:360,duration:12,box,parentElement:null,
  getAttribute(){return '';},getClientRects(){return [this.box];},getBoundingClientRect(){return this.box;},contains(hit){return hit===this;},
  closest(selector){if(selector.includes('.ad-showing'))return this.ad?{}:null;return this.player||null;},querySelectorAll(){return this.alternatives||[];}};}
function page(videos=[],frames=[]){const doc={title:'Page',videos,frames,links:[],posters:[],querySelectorAll(selector){return selector==='video'?this.videos:selector==='iframe,frame'?this.frames:selector==='a[href]'?this.links:selector.startsWith('img[alt=')?this.posters:[];},
  elementFromPoint(x,y){if(this.cover)return {};if(this.hit)return this.hit;for(const item of [...this.frames,...this.videos,...this.links.map(a=>a.image),...this.posters].reverse()){const r=item.getBoundingClientRect();if(x>=r.left&&x<=r.right&&y>=r.top&&y<=r.bottom)return item;}return null;},
  createElement(){return {getContext:()=>({drawImage:v=>{v.captures=(v.captures||0)+1;}}),toDataURL:()=> 'data:image/jpeg;base64,abcd'};}};
  return {document:doc,location:{href:'https://page.test/watch'},innerWidth:400,innerHeight:800,getComputedStyle:v=>Object.assign({display:'block',visibility:'visible',opacity:'1',overflowX:'visible',overflowY:'visible'},v.style||{})};}
const a=video('https://media.test/a.mp4','Movie A',rect(10,40,360,140)),b=video('https://media.test/b.mp4','Movie B',rect(10,220,360,140));
const root=page([a,b]);const context=vm.createContext({window:root,URL});
function scan(capture=''){return JSON.parse(vm.runInContext('('+source+')(0.11,0.055,'+JSON.stringify(capture)+')',context));}
let first=scan();assert.equal(first.rows.length,2);assert.equal(first.rows[0].sources[0].url,a.src);assert.equal(first.rows[1].sources[0].url,b.src);assert.equal(a.captures,undefined);assert.equal(b.captures,undefined);
const bid=first.rows[1].id;root.document.videos=[b,a];let rows=scan().rows;assert.equal(rows[0].id,bid);assert.equal(rows[0].title,'Movie B');
rows=scan(bid).rows;assert.equal(b.captures,1);assert.equal(a.captures,undefined);assert.equal(rows[0].thumbnail,'data:image/jpeg;base64,abcd');
b.src=b.currentSrc='https://media.test/new-b.mp4';rows=scan().rows;assert.equal(rows[0].id,bid);assert.equal(rows[0].sources[0].url,b.src);
b.alternatives=[{src:b.src,type:'video/mp4'},{src:'https://media.test/low.mp4',type:'video/mp4'}];rows=scan().rows;assert.equal(rows[0].sources.length,2);assert.equal(rows[0].sources[0].type,'video/mp4');assert.equal(rows[0].sources[1].current,false);
root.document.cover=true;assert.equal(scan().rows.length,0);root.document.cover=false;
a.box=rect(10,-100,360,140);assert.equal(scan().rows.length,1);a.box=rect(10,40,360,140);
b.parentElement={style:{overflowY:'hidden'},getBoundingClientRect:()=>rect(0,220,400,20),parentElement:null};assert.equal(scan().rows.length,1);b.parentElement=null;
b.style={visibility:'hidden'};assert.equal(scan().rows.length,1);b.style={};
const childVideo=video('https://media.test/child.mp4','Child movie',rect(10,10,280,160)),child=page([childVideo]);child.location.href='https://page.test/child';
const frame={getBoundingClientRect:()=>rect(20,400,300,200),offsetWidth:300,offsetHeight:200,clientWidth:300,clientHeight:200,clientLeft:0,clientTop:0,contentWindow:child};
root.document.frames=[frame];rows=scan().rows;assert.equal(rows.length,3);const nested=rows.find(r=>r.title==='Child movie');assert.equal(nested.page,child.location.href);assert.equal(nested.main,false);assert.ok(nested.x>20&&nested.y>400);
Object.defineProperty(frame,'contentWindow',{get(){return {get document(){throw new Error('Cross-origin');}};}});assert.equal(scan().rows.length,2);
root.document.frames=[];a.player={getVideoData:()=>({video_id:'abcdefghijk'}),contains:hit=>hit===a};rows=scan().rows;assert.equal(rows.find(r=>r.title==='Movie A').youtubeId,'abcdefghijk');a.ad=true;assert.equal(scan().rows.length,1);a.ad=false;
root.visualViewport={offsetLeft:0,offsetTop:200,width:400,height:400};rows=scan().rows;assert.equal(rows.length,1);assert.ok(rows[0].y<200);root.visualViewport=null;
root.document.videos=[];assert.equal(scan().rows.length,0);
// Mobile YouTube and news players can have a control layer next to <video>,
// without any of the desktop CSS classes. A page-wide modal must still hide it.
const mobile=video('blob:mobile','Mobile player',rect(0,0,400,220)),controls={closest:()=>null};
mobile.parentElement={tagName:'DIV',parentElement:null,getBoundingClientRect:()=>mobile.box,contains:hit=>hit===controls};
root.document.videos=[mobile];root.document.hit=controls;assert.equal(scan().rows.length,1);
root.document.hit={closest:()=>({})};assert.equal(scan().rows.length,0);
root.document.hit=null;mobile.parentElement=null;
// An open shadow root exposes the actual video, rather than only its custom host.
const shadow={querySelectorAll:s=>s==='video'?[mobile]:[],elementFromPoint:()=>mobile},host={shadowRoot:shadow};
root.document.videos=[];root.document.hit=host;root.document.createTreeWalker=()=>{let n=0;return {nextNode:()=>n++?null:host};};
assert.equal(scan().rows.length,1);root.document.createTreeWalker=undefined;root.document.hit=null;
function card(href,title,box){const image={alt:title,src:'https://img.test/poster.jpg',getBoundingClientRect:()=>box};return {href,title,image,contains:hit=>hit===image,querySelector:()=>image,getAttribute:()=>'',textContent:title};}
root.document.links=[card('https://m.youtube.com/watch?v=abcdefghijk','YouTube preview',rect(10,30,360,180)),card('https://www.n-tv.de/mediathek/videos/politik/Film-id123.html','ntv preview',rect(10,250,360,180))];
rows=scan().rows;assert.equal(rows.length,2);assert.equal(rows[0].youtubeId,'abcdefghijk');assert.equal(rows[0].target,'https://www.youtube.com/watch?v=abcdefghijk');assert.equal(rows[1].kind,'link');assert.equal(rows[1].sources.length,0);
root.document.cover=true;assert.equal(scan().rows.length,0);root.document.cover=false;
root.document.links=[card('https://youtube.com.evil.test/watch?v=abcdefghijk','Fake video',rect(10,30,360,180)),card('javascript:alert(1)','Invalid link',rect(10,250,360,180))];assert.equal(scan().rows.length,0);
root.document.links=[];frame.src='https://player.rtl.de/video/player.html';frame.title='ntv embedded player';root.document.frames=[frame];rows=scan().rows;assert.equal(rows.length,1);assert.equal(rows[0].kind,'frame');assert.equal(rows[0].sources.length,0);
frame.src='https://ad.test/player.html';assert.equal(scan().rows.length,0);
root.document.frames=[];root.location={href:'https://www.n-tv.de/mediathek/videos/politik/Film-id123.html',hostname:'www.n-tv.de'};
const poster={tagName:'IMG',src:'https://img.test/poster.jpg',getBoundingClientRect:()=>rect(10,30,360,180),contains:hit=>hit===poster};root.document.posters=[poster];rows=scan().rows;assert.equal(rows.length,1);assert.equal(rows[0].kind,'poster');assert.equal(rows[0].sources.length,0);
root.document.videos=[video('https://media.test/film.mp4','Started film',rect(10,30,360,180))];root.document.posters=[];rows=scan().rows;assert.equal(rows.length,1);assert.equal(rows[0].sources[0].url,'https://media.test/film.mp4');
// Vimeo embeds are cross-origin: locate their frame and preserve its actual URL,
// without claiming access to its document or mixing the neighbouring player.
root.document.videos=[];root.document.frames=[frame];frame.src='https://player.vimeo.com/video/19231868?h=8272103f6e';frame.title='Vimeo B';
rows=scan().rows;assert.equal(rows.length,1);assert.equal(rows[0].page,frame.src);assert.equal(rows[0].title,'Vimeo B');assert.equal(rows[0].kind,'frame');assert.equal(rows[0].sources.length,0);
root.document.cover=true;assert.equal(scan().rows.length,0);root.document.cover=false;
frame.src='https://player.vimeo.com.evil.test/video/19231868';assert.equal(scan().rows.length,0);
root.document.frames=[];root.document.links=[card('https://vimeo.com/76979871/8272103f6e','Vimeo preview',rect(10,30,360,180))];
rows=scan().rows;assert.equal(rows.length,1);assert.equal(rows[0].target,'https://player.vimeo.com/video/76979871?h=8272103f6e');
root.document.links=[];const vimeo=video('blob:https://player.vimeo.com/opaque','',rect(10,30,360,180));root.document.videos=[vimeo];
root.location={href:'https://player.vimeo.com/video/76979871',hostname:'player.vimeo.com'};
root.playerConfig={video:{id:76979871,title:'Exact Vimeo film',duration:12,thumbs:{'640':'https://img.test/vimeo.jpg'}},request:{files:{progressive:[{url:'https://cdn.test/vimeo-360.mp4',mime:'video/mp4',width:640,height:360,fps:25,quality:'360p'},{url:'https://cdn.test/vimeo-720.mp4',mime:'video/mp4',width:1280,height:720,quality:'720p'}],hls:{default_cdn:'correct',cdns:{wrong:{url:'https://cdn.test/other.m3u8'},correct:{url:'https://cdn.test/vimeo.m3u8'}}}}}};
rows=scan().rows;assert.equal(rows.length,1);assert.equal(rows[0].title,'Exact Vimeo film');assert.equal(rows[0].thumbnail,'https://img.test/vimeo.jpg');assert.equal(rows[0].sources.length,4);assert.equal(rows[0].sources[1].width,640);assert.equal(rows[0].sources[2].height,720);assert.equal(rows[0].sources[3].url,'https://cdn.test/vimeo.m3u8');assert.equal(rows[0].sources[2].current,false);
root.playerConfig.video.id=19231868;assert.equal(scan().rows[0].sources.length,1);
root.playerConfig.video.id=76979871;root.playerConfig.request.files.drm={enabled:true};assert.equal(scan().rows[0].sources.length,1);delete root.playerConfig.request.files.drm;
root.location={href:'https://other.test/video/76979871',hostname:'other.test'};assert.equal(scan().rows[0].sources.length,1);
console.log('Video markers: source isolation, live DOM updates, stable IDs, clipping, occlusion, nested frames, cross-origin isolation, visual viewport, YouTube identity and click-only thumbnails passed');
