const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {Engine,validatePicks}=require('../web/engine.js');
const corpus=JSON.parse(fs.readFileSync(path.join(__dirname,'../web/corpus.json'),'utf8'));
const engine=new Engine(corpus.documents);
test('every searchable quote is an exact, source-attributed substring',()=>{
 assert.ok(engine.chunks.length>100);
 for(const c of engine.chunks){assert.equal(c.doc.author,'毛泽东');assert.equal(c.quote,c.doc.text.slice(c.start,c.end));assert.ok(c.doc.source_url.startsWith('https://'));}
});
test('difficulties retrieve a verifiable poem with courage',()=>{
 const r=engine.search('我现在遇到困难了，很难过');
 assert.ok(r.some(x=>x.quote.includes('而今迈步从头越')));
 assert.ok(!r.some(x=>x.quote.includes('我取《淮南子')));
});
test('triumph retrieves the actual military-victory poem',()=>{
 const r=engine.search('描述凯旋而来的句子');
 assert.ok(r.some(x=>/百万雄师|三军过后尽开颜|红旗报捷/.test(x.quote)));
 assert.ok(r.every(x=>/胜利|捷报|红旗|百万雄师|凯歌|欢声|三军过后尽开颜/.test(x.quote+x.doc.title)),'generic words such as 而来 must not be treated as evidence of triumph');
});
test('direction requests contain relevant original evidence',()=>{
 const r=engine.search('描述不要乱改变方向的句子');
 assert.ok(r.length);
 assert.ok(r.some(x=>/方向|方针|政策|路线|不管风吹浪打|正确|原则/.test(x.quote)));
});
test('kind filters are respected and duplicate text does not recur',()=>{
 const r=engine.search('胜利',{kind:'诗词',limit:100});
 assert.ok(r.every(x=>x.doc.kind==='诗词'));
 assert.equal(new Set(r.map(x=>x.doc.text)).size,r.length);
});
test('daily selections are stable, rotate and belong to the attached poem',()=>{
 const date=new Date(2026,10,3),a=engine.daily(date),b=engine.daily(date),c=engine.daily(date,1);
 assert.equal(a.id,b.id);assert.notEqual(a.id,c.id);assert.ok(a.doc.text.includes(a.quote));assert.ok(a.doc.artwork.length);
});
test('model cannot fabricate an evidence id or replace quoted original words',()=>{
 const candidates=engine.search('我现在遇到困难了，很难过');
 assert.throws(()=>validatePicks({picks:[{id:'made-up',reason:'fake'}]},candidates));
 const r=validatePicks({picks:[{id:candidates[0].id,quote:'forged quote',reason:'这是解读'},{id:candidates[0].id}]},candidates);
 assert.equal(r.length,1);assert.equal(r[0].quote,candidates[0].quote);assert.equal(r[0].reason,'这是解读');
});
test('no related tokens yields an empty answer rather than invented sayings',()=>{
 assert.equal(engine.search('zxqv778899').length,0);
});
