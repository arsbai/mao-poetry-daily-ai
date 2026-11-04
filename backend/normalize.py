"""Apply the current original-only rules to a local snapshot; no network access."""
import hashlib, json, re
from collect import ROOT, clean_author_text, clean_poem_text, save, export

for name in ('poems','texts'):
    docs=json.loads((ROOT/'corpus'/(name+'.json')).read_text(encoding='utf-8'))
    for d in docs:
        d['text']=clean_poem_text(d['text']) if name=='poems' else clean_author_text(d['text'],d['title'])
        if '谈话' in d['title'] and '只收毛泽东答语' not in d['edition_note']: d['edition_note']+='访谈只收毛泽东答语，提问请查看来源页。'
        d['sha256']=hashlib.sha256(d['text'].encode()).hexdigest()
    report=json.loads((ROOT/'corpus'/(name+'-report.json')).read_text(encoding='utf-8'))
    save(name,[d for d in docs if len(d['text'])>8],report['errors'])
data=export();print('Normalized',len(data['documents']),'source records')
