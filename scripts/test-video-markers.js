const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('app/src/main/assets/video-markers.js','utf8');
const rect=(left,top,width,height)=>({left,top,right:left+width,bottom:top+height,width,height});
function video(url,title,box){return {currentSrc:url,src:url,title,poster:'',readyState:2,videoWidth:640,videoHeight:360,duration:12,box,parentElement:null,
  getAttribute(){return '';},getClientRects(){return [this.box];},getBoundingClientRect(){return this.box;},contains(hit){return hit===this;},
  closest(selector){if(selector.includes('.ad-showing'))return this.ad?{}:null;return this.player||null;},querySelectorAll(){return this.alternatives||[];}};}
function page(videos=[],frames=[]){const doc={title:'Page',videos,frames,querySelectorAll(selector){return selector==='video'?this.videos:this.frames;},
  elementFromPoint(x,y){if(this.cover)return {};for(const item of [...this.frames,...this.videos].reverse()){const r=item.getBoundingClientRect();if(x>=r.left&&x<=r.right&&y>=r.top&&y<=r.bottom)return item;}return null;},
  createElement(){return {getContext:()=>({drawImage:v=>{v.captures=(v.captures||0)+1;}}),toDataURL:()=> 'data:image/jpeg;base64,abcd'};}};
  return {document:doc,location:{href:'https://page.test/watch'},innerWidth:400,innerHeight:800,getComputedStyle:v=>Object.assign({display:'block',visibility:'visible',opacity:'1',overflowX:'visible',overflowY:'visible'},v.style||{})};}
const a=video('https://media.test/a.mp4','Movie A',rect(10,40,360,140)),b=video('https://media.test/b.mp4','Movie B',rect(10,220,360,140));
const root=page([a,b]);const context=vm.createContext({window:root});
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
console.log('Video markers: source isolation, live DOM updates, stable IDs, clipping, occlusion, nested frames, cross-origin isolation, visual viewport, YouTube identity and click-only thumbnails passed');
