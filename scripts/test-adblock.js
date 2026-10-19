const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const template=fs.readFileSync('app/src/main/assets/adblock.js','utf8');
function run(host,allowed=[],constructed=true){
    const sheets=[],nodes=[];
    const parent={appendChild(node){nodes.push(node);node.parentNode=this;},removeChild(node){nodes.splice(nodes.indexOf(node),1);node.parentNode=null;}};
    const document={head:parent,documentElement:parent,createElement(){return {setAttribute(){},textContent:''};},removeEventListener(){},addEventListener(){}};
    if(constructed)document.adoptedStyleSheets=sheets;
    const context={document,location:{protocol:'https:',hostname:host},CSSStyleSheet:class{replaceSync(css){this.css=css;}},MutationObserver:class{observe(){}disconnect(){}}};
    context.window=context;context.top=context;
    const data={generic:['.ads','.sponsor'],rules:[[['news.example'],[],'.site-ad',false],[['news.example'],[],'.sponsor',true],[['adblock.turtlecute.org'],[],'.adbox.banner_ads.adsbox',false]]};
    const source=template.replace("'__FILTER_DATA__'",JSON.stringify(data)).replace("'__SITE_EXCEPTIONS__'",JSON.stringify(allowed));
    vm.runInNewContext(source,context);
    return {context,document,nodes,css:constructed?(document.adoptedStyleSheets[0]?.css||''):(nodes[0]?.textContent||'')};
}
for(const constructed of [true,false]){
    const normal=run('news.example',[],constructed);assert(normal.css.includes('.ads{'));assert(normal.css.includes('.site-ad{'));assert(!normal.css.includes('.sponsor{'));
    normal.context.__bestBrowserAdblock.stop();assert.equal(constructed?normal.document.adoptedStyleSheets.length:normal.nodes.length,0);
    assert.equal(run('sub.news.example',['news.example'],constructed).css,'');
    assert(run('news.example.evil.test',['news.example'],constructed).css.includes('.ads{'));
    const test=run('adblock.turtlecute.org',[],constructed);assert(test.css.includes('.adbox.banner_ads.adsbox{'));
}
const full=template.replace("'__FILTER_DATA__'",fs.readFileSync('app/src/main/assets/adblock_cosmetic.json','utf8')).replace("'__SITE_EXCEPTIONS__'",'[]');
new vm.Script(full);
console.log('Adblock selectors, domain exceptions, stylesheet cleanup and compiled template: OK');
