"""Re-parse the existing source pages with improved metadata boundaries and retry transient failures."""
import hashlib, json, re, sys, time, urllib.parse
from pathlib import Path
from collect import ROOT, fetch, parse_article, plain, Links, save, export
sys.stdout.reconfigure(encoding='utf-8',errors='replace')

def clean_author_text(text):
    parts=re.split(r'\n\s*\n',text.strip())
    lead=re.compile(r'^(?:[＊*]|这是毛泽东|毛泽东(?:此文|的这篇|这篇|同志在|同志为)|这(?:篇文章|个文件|个布告|封信|篇论文|个报告|个决定|篇社论|篇评论|个指示).{0,45}毛泽东)')
    while parts and lead.search(parts[0]): parts.pop(0)
    while parts and re.match(r'^[（(]《毛泽东选集》',parts[-1]): parts.pop()
    text='\n\n'.join(parts).strip()
    text=re.sub(r'\[\d{1,3}\]','',text)
    return text

def repair():
    path=ROOT/'corpus/texts.json';docs=json.loads(path.read_text(encoding='utf-8'));report=json.loads((ROOT/'corpus/texts-report.json').read_text(encoding='utf-8'))
    base='https://www.marxists.org/chinese/maozedong/'
    parser=Links();parser.feed(fetch(base+'index.htm'));titles={urllib.parse.urljoin(base,u):t for u,t in parser.links}
    previous={d['source_url'] for d in docs};errors=[]
    for i,item in enumerate(report['errors']):
        url=item['url']
        if url in previous: continue
        time.sleep(1)
        try:
            raw=fetch(url);title=titles.get(url,'')
            d=parse_article(raw,title,url)
            if d:
                docs.append(d);previous.add(url);print(f'Recovered {i+1}: {title}',flush=True)
            else: errors.append({'url':url,'reason':'正文格式或独立作者范围不符合；未入库'})
        except Exception as e: errors.append({'url':url,'error':str(e)});print(f'Unavailable {i+1}',flush=True)
    cleaned=[]
    for d in docs:
        d['text']=clean_author_text(d['text'])
        if len(d['text'])<35: continue
        d['sha256']=hashlib.sha256(d['text'].encode()).hexdigest()
        cleaned.append(d)
    save('texts',cleaned,errors);export();print('Verified original texts:',len(cleaned))

if __name__=='__main__': repair()
