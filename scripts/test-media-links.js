const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const code = fs.readFileSync('app/src/main/assets/media-links.js', 'utf8');
function doc(base='https://page.test/base/') {
  const d = {baseURI:base, nodes:[]};
  d.createTreeWalker = root => {let i=0;return {nextNode:()=>root.nodes[i++]||null};};
  d.add=(tag, attrs={})=>{const n={tagName:tag.toUpperCase(),ownerDocument:d,baseURI:base,getAttribute:k=>attrs[k]||null};for(const p of ['href','src'])if(attrs[p])n[p]=new URL(attrs[p],base).href;d.nodes.push(n);return n;};
  return d;
}
function session(document) {
  const window={}, context={window,document,URL,fetch(){throw Error('Discovery must not fetch links');}};
  return (token, embedded)=>JSON.parse(vm.runInNewContext(code+`(${JSON.stringify(token)},${embedded})`,context));
}
const d=doc();d.add('a',{href:'movie.mp4?token=a%2Fb#part',download:'Movie.mp4'});d.add('a',{href:'article.html'});d.add('a',{href:'javascript:alert(1)'});d.add('a',{href:'blob:https://page.test/id'});d.add('a',{href:'data:image/png;base64,AAAA'});d.add('img',{src:'embedded.png'});d.add('video',{src:'movie.webm'});
const shadow=d.add('div');shadow.shadowRoot={ownerDocument:d,nodes:[{tagName:'A',href:'https://shadow.test/sound.mp3',baseURI:d.baseURI,getAttribute:k=>k==='href'?'https://shadow.test/sound.mp3':null}]};
const child=doc('https://page.test/frame/');child.add('a',{href:'sound.wav'});d.add('iframe').contentDocument=child;
Object.defineProperty(d.add('iframe'),'contentDocument',{get(){throw Error('Cross-origin');}});
let next=session(d), all=[], chunk;do{chunk=next('one',false);all.push(...chunk.items);}while(!chunk.done);
assert.deepEqual(all.map(x=>x.url).sort(),['https://page.test/base/movie.mp4?token=a%2Fb#part','https://page.test/base/article.html','https://shadow.test/sound.mp3','https://page.test/frame/sound.wav'].sort());
all=[];do{chunk=next('two',true);all.push(...chunk.items);}while(!chunk.done);assert(all.some(x=>x.url.endsWith('/embedded.png')));assert(all.some(x=>x.url.endsWith('/movie.webm')));
const large=doc();for(let i=0;i<2600;i++)large.add('a',{href:`${i}.jpg`});next=session(large);let calls=0;all=[];do{chunk=next('large',false);assert(chunk.items.length<=100);all.push(...chunk.items);calls++;}while(!chunk.done);assert.equal(all.length,2600);assert(calls>=26);assert.equal(new Set(all.map(x=>x.url)).size,2600);
const huge=doc();for(let i=0;i<101000;i++)huge.add('span');next=session(huge);calls=0;do{chunk=next('huge',false);calls++;}while(!chunk.done);assert(chunk.limited);assert(calls<200);
const reset=doc();for(let i=0;i<200;i++)reset.add('a',{href:`${i}.png`});next=session(reset);assert.equal(next('old',false).items[0].url,next('new',false).items[0].url);
console.log('Media links: bounded scans, opt-in embedded sources, same-origin frames, shadow roots, source URLs and cancellation tokens passed');
