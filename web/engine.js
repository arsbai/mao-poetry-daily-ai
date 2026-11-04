(function(root){
'use strict';
const TOPICS=[
 {name:'困难与低落',triggers:['困难','难过','低落','挫折','失败','坚持','沮丧','压力','痛苦','伤心','失落','害怕','不顺','勇气','焦虑'],terms:['困难','光明','勇气','信心','胜利','坚持','决心','奋斗','前途'],preferred:['雄关漫道真如铁，而今迈步从头越','在困难的时候，要看到成绩','下定决心，不怕牺牲','世上无难事，只要肯登攀','红军不怕远征难'],reason:'可从直面困难、保持勇气的角度阅读。请同时留意原作的具体背景。'},
 {name:'胜利与凯旋',triggers:['凯旋','胜利','归来','大获全胜','捷报','庆功','成功','胜仗','得胜','赢了','告捷'],terms:['胜利','捷报','红旗','百万雄师','凯歌','欢声','喜','归'],preferred:['百万雄师过大江','更喜岷山千里雪，三军过后尽开颜','红旗报捷','喜闻捷报'],reason:'原文中的胜利意象，与表达凯旋、告捷的场景相关。'},
 {name:'目标与方向',triggers:['方向','目标','乱改','动摇','改弦更张','初心','方针','路线','摇摆','朝令夕改','改变','跑偏'],terms:['方向','方针','政策','原则','路线','坚定','目标','坚持','实际'],preferred:['政策和策略是党的生命','坚持真理，修正错误','不管风吹浪打，胜似闲庭信步','认清中国的国情，乃是认清一切革命问题的基本的根据','正确的政治路线'],reason:'可联系原则、目标与行动方向来理解；原文也强调依据实际情况判断，不能简单理解为永远不作调整。'},
 {name:'学习与实践',triggers:['学习','实践','调查','研究','读书','空想','知识','经验','理论','成长','事实','求知'],terms:['实践','调查','研究','真理','认识','学习','事实','实际'],preferred:['没有调查，没有发言权','实践、认识、再实践、再认识','实事求是','要有革命的理论'],reason:'原文涉及调查、认识与实践，可从获得真知和解决实际问题的角度阅读。'},
 {name:'重新出发',triggers:['出发','重新','从头','开始','重来','重启','放下','再来'],terms:['从头','前进','开始','奋斗','勇气','长征','迈步'],preferred:['而今迈步从头越','万里长征走完了第一步','人间正道是沧桑'],reason:'可从继续前进、重新开始的角度联想，原文有其独立的历史语境。'},
 {name:'团结与合作',triggers:['合作','团结','伙伴','朋友','团队','同事','协作','互相'],terms:['团结','共同','互相','帮助','集体','力量'],preferred:['团结就是力量','互相关心，互相爱护，互相帮助','团结起来'],reason:'原文中的共同目标和互助表达，与团结合作的场景相关。'}
];
function tokens(text){const parts=text.toLowerCase().match(/[\u3400-\u9fff]+|[a-z0-9]+/g)||[],result=[];for(const p of parts){if(/[a-z0-9]/.test(p)){result.push(p);continue;}if(p.length===1)result.push(p);for(let i=0;i<p.length-1;i++)result.push(p.slice(i,i+2));}return result;}
function makeChunks(doc){
 const chunks=[],text=doc.text;let m;const re=/[^。！？!?；\n]+[。！？!?；]?/g;
 while((m=re.exec(text))){let raw=m[0],lead=raw.length-raw.trimStart().length,start=m.index+lead,end=m.index+raw.trimEnd().length;if(end-start<6)continue;
  if(end-start>360){for(let p=start;p<end;p+=220){let q=Math.min(p+240,end);if(q-p>=10)chunks.push({id:doc.id+':'+p,start:p,end:q,quote:text.slice(p,q),doc});}}
  else chunks.push({id:doc.id+':'+start,start,end,quote:text.slice(start,end),doc});
 }
 return chunks;
}
class Engine{
 constructor(documents){this.docs=documents.filter(d=>d.author==='毛泽东'&&d.text&&d.source_url);this.byId=new Map(this.docs.map(d=>[d.id,d]));const seen=new Set();this.chunks=this.docs.filter(d=>{if(seen.has(d.text))return false;seen.add(d.text);return true;}).flatMap(makeChunks);this.chunkById=new Map(this.chunks.map(c=>[c.id,c]));this.postings=new Map();this.avg=0;this.chunks.forEach((c,i)=>{const ts=tokens(c.quote+' '+c.doc.title);c.length=ts.length;this.avg+=c.length;const counts=new Map();for(const t of ts)counts.set(t,(counts.get(t)||0)+1);for(const [t,n]of counts){if(!this.postings.has(t))this.postings.set(t,[]);this.postings.get(t).push([i,n]);}});this.avg/=this.chunks.length||1;}
 search(query,{kind='all',limit=8,expanded=[]}={}){
  const topics=TOPICS.filter(t=>t.triggers.some(w=>query.includes(w)));const weighted=new Map();
  for(const t of tokens(query))weighted.set(t,(weighted.get(t)||0)+1.5);
  for(const topic of topics)for(const term of topic.terms)for(const t of tokens(term))weighted.set(t,Math.max(weighted.get(t)||0,1.2));
  for(const term of expanded.slice(0,12))for(const t of tokens(String(term).slice(0,30)))weighted.set(t,Math.max(weighted.get(t)||0,1.1));
  const scores=new Map();for(const [t,weight]of weighted){const list=this.postings.get(t)||[],idf=Math.log(1+(this.chunks.length-list.length+.5)/(list.length+.5));for(const [i,tf]of list){const c=this.chunks[i];if(kind!=='all'&&c.doc.kind!==kind)continue;const bm=idf*tf*2.2/(tf+1.2*(.25+.75*c.length/this.avg));scores.set(i,(scores.get(i)||0)+weight*bm);}}
  for(let i=0;i<this.chunks.length;i++){const c=this.chunks[i];if(kind!=='all'&&c.doc.kind!==kind)continue;for(const t of topics){for(let j=0;j<t.preferred.length;j++){if(c.quote.includes(t.preferred[j])||c.doc.title.includes(t.preferred[j]))scores.set(i,(scores.get(i)||0)+32-j*2);}}}
  const hasTopicEvidence=c=>topics.some(t=>t.terms.some(w=>w.length>1&&(c.quote+c.doc.title).includes(w))||t.preferred.some(w=>c.quote.includes(w)||c.doc.title.includes(w)));
  const sorted=[...scores].filter(([i])=>!topics.length||hasTopicEvidence(this.chunks[i])).sort((a,b)=>b[1]-a[1]),cutoff=Math.max(3.5,(sorted[0]?.[1]||0)*.18);const ranked=sorted.filter(([,s])=>s>=cutoff);const docsUsed=new Set(),out=[];
  for(const [i,score]of ranked){const c=this.chunks[i];if(docsUsed.has(c.doc.id))continue;docsUsed.add(c.doc.id);const pos=topics.find(t=>t.terms.some(w=>(c.quote+c.doc.title).includes(w))||t.preferred.some(w=>c.quote.includes(w)||c.doc.title.includes(w)));out.push({...c,score,reason:pos?pos.reason:'与输入词语有直接匹配，建议打开原文核对其完整含义。',mode:'本地检索'});if(out.length>=limit)break;}
  return out;
 }
 daily(date,offset=0){const preferred=['雄关漫道真如铁，而今迈步从头越','世上无难事，只要肯登攀','不管风吹浪打，胜似闲庭信步','更喜岷山千里雪，三军过后尽开颜','天若有情天亦老，人间正道是沧桑','待到山花烂漫时，她在丛中笑','踏遍青山人未老，风景这边独好','长风破浪会有时'];let pool=[];for(const phrase of preferred){const d=this.docs.find(x=>x.kind==='诗词'&&x.text.includes(phrase));if(d){const s=d.text.indexOf(phrase),end=s+phrase.length+( /[。！？]/.test(d.text[s+phrase.length]||'')?1:0);pool.push({id:d.id+':daily:'+s,quote:d.text.slice(s,end),start:s,end,doc:d});}}
  if(!pool.length)pool=this.chunks.filter(c=>c.doc.kind==='诗词'&&c.quote.length<60);if(!pool.length)return null;
  const day=Math.floor(Date.UTC(date.getFullYear(),date.getMonth(),date.getDate())/86400000),i=((day+offset)%pool.length+pool.length)%pool.length;return {...pool[i],sequence:i+1,total:pool.length};
 }
}
function validatePicks(value,candidates){if(!value||!Array.isArray(value.picks))throw Error('模型返回内容无法识别');const map=new Map(candidates.map(c=>[c.id,c])),seen=new Set();const output=[];for(const p of value.picks.slice(0,8)){if(!p||typeof p.id!=='string'||!map.has(p.id)||seen.has(p.id))continue;seen.add(p.id);const c=map.get(p.id);output.push({...c,reason:typeof p.reason==='string'?p.reason.slice(0,600):'请结合原文阅读。',mode:'模型解读'});}if(!output.length)throw Error('模型没有选出可核验的原文，已保留本地检索结果');return output;}
const api={Engine,TOPICS,tokens,makeChunks,validatePicks};if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.XiangDong=api;
})(typeof window==='undefined'?globalThis:window);
