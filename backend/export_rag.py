"""UTF-8 chunks with exact original offsets for import into external retrieval systems."""
import hashlib, json, re
from collect import ROOT

def export_rag():
    docs=json.loads((ROOT/'web/corpus.json').read_text(encoding='utf-8'))['documents']
    count=0;seen=set()
    with (ROOT/'corpus/rag_chunks.jsonl').open('w',encoding='utf-8') as out:
        for d in docs:
            digest=hashlib.sha256(d['text'].encode()).hexdigest()
            if digest in seen: continue
            seen.add(digest);text=d['text'];start=0
            while start<len(text):
                end=min(start+700,len(text))
                if end<len(text):
                    matches=list(re.finditer(r'[。！？\n]',text[start+350:end]))
                    if matches: end=start+350+matches[-1].end()
                content=text[start:end]
                record={'id':d['id']+':'+str(start),'text':content,'metadata':{'document_id':d['id'],'title':d['title'],'author':d['author'],'kind':d['kind'],'date':d['date'],'source_url':d['source_url'],'source_name':d['source_name'],'edition_note':d['edition_note'],'start':start,'end':end,'document_sha256':digest}}
                out.write(json.dumps(record,ensure_ascii=False)+'\n');count+=1
                if end>=len(text): break
                start=max(start+1,end-100)
    return {'source_records':len(docs),'unique_texts':len(seen),'rag_chunks':count,'characters':sum(len(d['text']) for d in docs)}

if __name__=='__main__': print(export_rag())
