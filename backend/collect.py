"""Public text-only collectors. Never fetches images, video, PDF, or scholarly literature."""
from __future__ import annotations
import argparse, hashlib, html, json, re, time, urllib.request, urllib.parse, urllib.robotparser
from datetime import datetime, timezone
from pathlib import Path
from html.parser import HTMLParser

ROOT = Path(__file__).resolve().parents[1]
USER_AGENT = 'XiangDongPoetry/1.0 (personal text reader; 1 request/second)'
BASE = 'https://www.dlmzd.net'
POEM_DB = 'KRM_1741159449741'

def fetch(url, body=None):
    data = json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, headers={'User-Agent': USER_AGENT, 'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=40) as r:
        raw = r.read(12 * 1024 * 1024)
        if len(raw) >= 12 * 1024 * 1024: raise ValueError('Response too large')
        enc = re.search(br'charset\s*=\s*[\"\x27]?([\w-]+)', raw[:2000])
        encoding=enc.group(1).decode() if enc else r.headers.get_content_charset('utf-8')
        if encoding.lower() in ('gb2312','gbk'): encoding='gb18030'
        return raw.decode(encoding, errors='strict')

class Plain(HTMLParser):
    def __init__(self): super().__init__(); self.parts=[]; self.ignored=0
    def handle_starttag(self, tag, attrs):
        if tag in ('script','style','sup'): self.ignored += 1
        if not self.ignored and tag in ('p','br','div','h1','h2','h3','h4'): self.parts.append('\n')
    def handle_endtag(self, tag):
        if tag in ('script','style','sup') and self.ignored: self.ignored -= 1
        if not self.ignored and tag in ('p','div','h1','h2','h3','h4'): self.parts.append('\n')
    def handle_data(self, data):
        if not self.ignored: self.parts.append(data)

def plain(s, compact=False):
    p=Plain(); p.feed(s or ''); s=''.join(p.parts).replace('\xa0',' ').replace('\u3000',' ')
    s='\n'.join(re.sub(r'[ \t]+',' ',x).strip() for x in s.splitlines())
    s=re.sub(r'\n{3,}','\n\n',s).strip()
    return re.sub(r'\s+','',s) if compact else s

def clean_author_text(text, title=''):
    parts=re.split(r'\n\s*\n',text.strip())
    lead=re.compile(r'^(?:[＊*]|这是毛泽东|毛泽东(?:此文|的这篇|这篇|同志在|同志为)|这(?:篇文章|个文件|个布告|封信|篇论文|个报告|个决定|篇社论|篇评论|个指示).{0,45}毛泽东)')
    while parts and lead.search(parts[0]): parts.pop(0)
    while parts and re.match(r'^[（(]《毛泽东选集》',parts[-1]): parts.pop()
    text='\n\n'.join(parts).strip()
    # Interview questions are not words spoken by Mao. Preserve only the answer turns.
    if ('谈话' in title or re.search(r'(?:记者|贝)问[:：]',text)) and re.search(r'(?:毛答|毛泽东[:：]|答[:：])',text):
        answers=[]; speaking=False
        for para in re.split(r'\n\s*\n',text):
            if re.match(r'^(?:(?:记者|贝|斯|斯特朗)?问[:：]|斯特朗[:：])',para): speaking=False;continue
            answer=re.match(r'^(?:毛答|毛泽东|答)[:：]\s*(.*)',para,re.S)
            if answer: speaking=True;answers.append(answer.group(1));continue
            if speaking: answers.append(para)
        if answers: text='\n\n'.join(answers)
    return re.sub(r'\[\d{1,3}\]','',text)

def clean_poem_text(raw):
    # Inline (原注) marks refer to an endnote; they do not end the poem.
    text=plain(raw,True).split('作者原注',1)[0]
    return re.sub(r'[（(]原注[）)]','',text).strip()

def decorate(doc):
    doc['sha256']=hashlib.sha256(doc['text'].encode()).hexdigest()
    doc['downloaded_at']=datetime.now(timezone.utc).isoformat()
    return doc

def collect_poems(progress=print):
    records=[]; current=1
    while True:
        payload={'dataProperty':POEM_DB,'existCnki':True,'conditions':[], 'searchList':[], 'mode':'ADVANCED','dbway':'SINGLE','sorts':[], 'current':current,'size':100}
        res=json.loads(fetch(BASE+'/api/knowledge/getSingleKnowledgeListClient',payload))
        if res.get('code')!=200: raise ValueError('Poetry API: '+str(res.get('msg','Unavailable')))
        page=res['data']; records.extend(page['records'])
        if current >= page['pages']: break
        current+=1; time.sleep(1)
    result=[]; errors=[]
    for i,r in enumerate(records):
        time.sleep(1)
        try:
            detail=json.loads(fetch(BASE+'/api/knowledge/dynamic/searchDetail',{'dataProperty':POEM_DB,'guid':r['uuid']}))
            if detail.get('code')!=200: raise ValueError('Detail unavailable')
            p=detail['data']['props']
            if plain(p.get('AUTHOR',''),True)!='毛泽东': continue
            text=clean_poem_text(p.get('MFULLTEXT',''))
            if len(text)<8: continue
            title=plain(p.get('TITLE',''),True)
            sub=plain(p.get('SUBTITLE',''),True)
            if sub: title+='·'+sub
            artwork=[]
            for key in ('MFILE','MCOVER'):
                try:
                    for a in json.loads(p.get(key) or '[]'):
                        path=a.get('filePath','')
                        if re.fullmatch(r'[\w/.-]+\.(gif|jpg|jpeg|png|webp)',path,re.I):
                            u=BASE+'/miniocnki/'+path
                            if u not in [x['url'] for x in artwork]:
                                artwork.append({'url':u,'label':'平台配套书法','source_url':BASE+'/detail-poem?'+urllib.parse.urlencode({'guid':r['uuid'],'base':POEM_DB})})
                except (ValueError,TypeError): pass
            result.append(decorate({'id':'poem-'+r['uuid'],'title':title,'author':'毛泽东','kind':'诗词', 'date':plain(p.get('PUBLISH_TIME','')),'year':str(p.get('KRM_YEAR','')),'text':text,'source_name':'毛泽东文献数字资源平台','source_url':BASE+'/detail-poem?'+urllib.parse.urlencode({'guid':r['uuid'],'base':POEM_DB}), 'edition_note':plain(p.get('SOURCE','')), 'artwork':artwork, 'author_basis':'平台 AUTHOR 字段为毛泽东；仅提取 MFULLTEXT，排除注释和鉴赏。'}))
            progress(f'Poems {i+1}/{len(records)}: {title}')
        except Exception as e: errors.append({'id':r.get('uuid'),'error':str(e)})
    if len(result)<max(1,len(records)*.9): raise RuntimeError(f'Incomplete poetry sync: {len(result)}/{len(records)}; previous corpus retained')
    return result,errors

class Links(HTMLParser):
    def __init__(self): super().__init__(); self.links=[]; self.href=None; self.label=[]
    def handle_starttag(self,t,a):
        if t=='a': self.href=dict(a).get('href'); self.label=[]
    def handle_data(self,d):
        if self.href: self.label.append(d)
    def handle_endtag(self,t):
        if t=='a' and self.href: self.links.append((self.href,''.join(self.label).strip())); self.href=None

def parse_article(raw, title, url):
    meta=re.search(r'<p\b[^>]*class\s*=\s*[\"\x27]?date[\"\x27]?[^>]*>(.*?)</p>',raw,re.I|re.S)
    if not meta: return None
    date=plain(meta.group(1)); body=raw[meta.end():]
    # Initial * paragraphs are the editor's introduction, not the author's prose.
    note=''
    first=re.search(r'^\s*(?:<br\s*/?>\s*)*<p[^>]*>(.*?)</p>',body,re.I|re.S)
    if first and plain(first.group(1)).startswith(('*','＊')):
        note=plain(first.group(1)); body=body[first.end():]
    body=re.split(r'<hr\b',body,flags=re.I)[0]
    body=re.split(r'<(?:p|div|span)[^>]*(?:class=[\"\x27]?notes?|id=[\"\x27]?notes?)',body,flags=re.I)[0]
    text=plain(body)
    text=re.split(r'\n\s*(?:注\s*释|注\s*解)\s*\n',text)[0].strip()
    text=clean_author_text(text,title)
    if not 35 < len(text) < 250000 or '\ufffd' in text: return None
    # Do not mix collective or externally-authored material into a sole-author corpus.
    if title in {'中国革命和中国共产党','第十八集团军总司令给蒋介石的两个电报','向全国进军的命令','中国人民解放军布告'}: return None
    if re.search(r'合写|共同起草|共同署名',note): return None
    return decorate({'id':'text-'+hashlib.sha256(url.encode()).hexdigest()[:20], 'title':title,'author':'毛泽东','kind':'著作','date':date,'year':'','text':text,'source_name':'中文马克思主义文库·毛泽东选集','source_url':url,'edition_note':'保留所载版本；脚注、编者说明不进入检索。'+('访谈只收毛泽东答语，提问请查看来源页。' if '谈话' in title else ''),'artwork':[], 'author_basis':'仅收毛泽东选集作者目录正文；排除出版说明、未删节对照版本及已识别的共同署名篇目。'})

def collect_texts(progress=print, limit=250):
    base='https://www.marxists.org/chinese/maozedong/'
    robots=urllib.robotparser.RobotFileParser('https://www.marxists.org/robots.txt'); robots.parse(fetch(robots.url).splitlines())
    index=fetch(base+'index.htm'); parser=Links(); parser.feed(index)
    candidates=[]; seen=set()
    for href,title in parser.links:
        url=urllib.parse.urljoin(base,href)
        # Only the numbered Selected Works archive, not secondary studies or PDFs.
        if not re.fullmatch(re.escape(base)+r'marxist\.org-chinese-mao-[\w-]+\.htm',url): continue
        if not title or any(w in title for w in ('未删','原始版本','出版说明','讲话稿按此','毛泽东集')): continue
        if title in seen or not robots.can_fetch(USER_AGENT,url): continue
        seen.add(title); candidates.append((url,title))
        if len(candidates)>=limit: break
    result=[]; errors=[]
    for i,(url,title) in enumerate(candidates):
        time.sleep(max(1,robots.crawl_delay('*') or 1))
        try:
            doc=parse_article(fetch(url),title,url)
            if doc: result.append(doc)
            else: errors.append({'url':url,'reason':'正文格式或独立作者范围不符合；未入库'})
            progress(f'Texts {i+1}/{len(candidates)}: {title}')
        except Exception as e: errors.append({'url':url,'error':str(e)})
    if not result: raise RuntimeError('No original texts collected')
    return result,errors

def save(name,docs,errors):
    directory=ROOT/'corpus'; directory.mkdir(exist_ok=True)
    tmp=directory/(name+'.json.tmp'); tmp.write_text(json.dumps(docs,ensure_ascii=False,indent=2),encoding='utf-8'); tmp.replace(directory/(name+'.json'))
    (directory/(name+'-report.json')).write_text(json.dumps({'count':len(docs),'characters':sum(len(d['text']) for d in docs),'errors':errors,'time':datetime.now(timezone.utc).isoformat()},ensure_ascii=False,indent=2),encoding='utf-8')

def export():
    docs=[]
    for name in ('poems','texts'):
        p=ROOT/'corpus'/(name+'.json')
        if p.exists(): docs+=json.loads(p.read_text(encoding='utf-8'))
    payload={'schema':1,'updated_at':datetime.now(timezone.utc).isoformat(),'documents':docs}
    (ROOT/'web'/'corpus.json').write_text(json.dumps(payload,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
    seen=set()
    with (ROOT/'corpus'/'originals.jsonl').open('w',encoding='utf-8') as f:
        for d in docs:
            key=hashlib.sha256(d['text'].encode()).hexdigest()
            if key not in seen: f.write(json.dumps(d,ensure_ascii=False)+'\n'); seen.add(key)
    textdir=ROOT/'corpus'/'txt'; textdir.mkdir(exist_ok=True)
    for d in docs:
        name=re.sub(r'[<>:"/\\|?*]','_',d['title'])[:65]+'-'+d['id'][-8:]+'.txt'
        (textdir/name).write_text(d['title']+'\n毛泽东\n'+d['date']+'\n\n'+d['text']+'\n\n来源：'+d['source_url']+'\n版本：'+d['edition_note'],encoding='utf-8')
    return payload

if __name__=='__main__':
    ap=argparse.ArgumentParser(); ap.add_argument('scope',choices=['poems','texts','all','export']); ap.add_argument('--limit',type=int,default=250); a=ap.parse_args()
    if a.scope in ('poems','all'): save('poems',*collect_poems())
    if a.scope in ('texts','all'): save('texts',*collect_texts(limit=a.limit))
    data=export(); print('Exported',len(data['documents']),'documents')
