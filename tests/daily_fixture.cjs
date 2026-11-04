const fs=require('node:fs');
const {Engine}=require('../web/engine.js');
const data=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));
const engine=new Engine(data.documents),fixture=[];
for(let i=0;i<730;i++){
 const date=new Date(2026,0,1+i),daily=engine.daily(date);
 fixture.push({date:[date.getFullYear(),String(date.getMonth()+1).padStart(2,'0'),String(date.getDate()).padStart(2,'0')].join('-'),documentId:daily.doc.id,quote:daily.quote});
}
fs.writeFileSync(process.argv[3],JSON.stringify(fixture));
