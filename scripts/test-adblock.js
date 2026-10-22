const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const template=fs.readFileSync('app/src/main/assets/adblock.js','utf8');
function run(host,allowed=[],constructed=true){
    const sheets=[],nodes=[];
    let installs=0;
    const parent={appendChild(node){installs++;nodes.push(node);node.parentNode=this;},removeChild(node){nodes.splice(nodes.indexOf(node),1);node.parentNode=null;}};
    const document={head:parent,documentElement:parent,createElement(){return {setAttribute(){},textContent:''};},removeEventListener(){},addEventListener(){}};
    if(constructed)document.adoptedStyleSheets=sheets;
    const context={document,location:{protocol:'https:',hostname:host},CSSStyleSheet:class{replaceSync(css){installs++;this.css=css;}},MutationObserver:class{observe(){}disconnect(){}}};
    context.window=context;context.top=context;
    const data={generic:['.ads','.sponsor'],rules:[[['news.example'],[],'.site-ad',false],[['news.example'],[],'.sponsor',true],[['adblock.turtlecute.org'],[],'.adbox.banner_ads.adsbox',false]]};
    function apply(next=allowed){
        const source=template.replace("'__FILTER_DATA__'",JSON.stringify(data)).replace("'__SITE_EXCEPTIONS__'",JSON.stringify(next)).replace("'__CONFIG_KEY__'",JSON.stringify('cosmetic-v2:'+JSON.stringify(next)));
        vm.runInNewContext(source,context);
    }
    apply();
    return {context,document,nodes,apply,installs:()=>installs,css:constructed?(document.adoptedStyleSheets[0]?.css||''):(nodes[0]?.textContent||'')};
}
for(const constructed of [true,false]){
    const normal=run('news.example',[],constructed);assert(normal.css.includes('.ads{'));assert(normal.css.includes('.site-ad{'));assert(!normal.css.includes('.sponsor{'));
    for(let i=0;i<50;i++)normal.apply();assert.equal(normal.installs(),1,'Repeated page callbacks must not rebuild the stylesheet');
    normal.context.__bestBrowserAdblock.stop();assert.equal(constructed?normal.document.adoptedStyleSheets.length:normal.nodes.length,0);
    normal.apply();assert.equal(normal.installs(),2,'Re-enabling installs CSS again');
    normal.apply(['news.example']);assert.equal(constructed?normal.document.adoptedStyleSheets.length:normal.nodes.length,0,'A changed exception list removes existing CSS');
    normal.apply([]);assert.equal(normal.installs(),3);
    if(constructed)normal.document.adoptedStyleSheets=[];else normal.nodes[0].parentNode.removeChild(normal.nodes[0]);
    normal.apply();assert.equal(normal.installs(),4,'Removed stylesheets are repaired');
    assert.equal(run('sub.news.example',['news.example'],constructed).css,'');
    assert(run('news.example.evil.test',['news.example'],constructed).css.includes('.ads{'));
    const test=run('adblock.turtlecute.org',[],constructed);assert(test.css.includes('.adbox.banner_ads.adsbox{'));
}
const full=template.replace("'__FILTER_DATA__'",fs.readFileSync('app/src/main/assets/adblock_cosmetic.json','utf8')).replace("'__SITE_EXCEPTIONS__'",'[]').replace("'__CONFIG_KEY__'",JSON.stringify('cosmetic-v2:[]'));
new vm.Script(full);
console.log('Adblock selectors, domain exceptions, stylesheet cleanup and compiled template: OK');
