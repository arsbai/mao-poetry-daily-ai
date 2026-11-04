"""Local preview and optional always-on corpus sync server; Python standard library only."""
from __future__ import annotations
import argparse, hmac, json, os, threading, time
from datetime import datetime, timezone, timedelta
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from pathlib import Path
from collect import ROOT, collect_poems, save, export

SYNC_LOCK=threading.Lock()
STATE={'running':False,'last_success':None,'last_error':None}
TOKEN=os.environ.get('XIANGDONG_SERVER_TOKEN','')

def synchronize():
    if not SYNC_LOCK.acquire(blocking=False): return False
    STATE['running']=True
    try:
        docs,errors=collect_poems(progress=lambda s:None)
        save('poems',docs,errors); export()
        STATE['last_success']=datetime.now(timezone.utc).isoformat(); STATE['last_error']=None
    except Exception as e: STATE['last_error']=str(e)
    finally: STATE['running']=False; SYNC_LOCK.release()
    return True

def scheduler():
    # China calendar day, independent of the host's timezone. Retry after failure, retain last good data.
    last_day=''
    while True:
        day=datetime.now(timezone(timedelta(hours=8))).date().isoformat()
        if day!=last_day and not STATE['running']:
            synchronize()
            if STATE['last_error'] is None: last_day=day
        time.sleep(60)

class Handler(SimpleHTTPRequestHandler):
    def __init__(self,*args,**kwargs): super().__init__(*args,directory=str(ROOT/'web'),**kwargs)
    def log_message(self,fmt,*args):
        # Never log Authorization headers, keys, model queries, or full remote URLs.
        if self.path.split('?')[0] in ('/api/status','/api/corpus','/api/sync'): return
        super().log_message(fmt,*args)
    def authorized(self):
        return not TOKEN or hmac.compare_digest(self.headers.get('Authorization',''),'Bearer '+TOKEN)
    def reply(self,obj,status=200):
        data=json.dumps(obj,ensure_ascii=False).encode();self.send_response(status);self.send_header('Content-Type','application/json; charset=utf-8');self.send_header('Content-Length',str(len(data)));self.send_header('Cache-Control','no-store');self.send_header('X-Content-Type-Options','nosniff');self.end_headers();self.wfile.write(data)
    def do_GET(self):
        path=self.path.split('?')[0]
        if path.startswith('/api/'):
            if not self.authorized(): self.reply({'error':'需要书房服务访问令牌'},401);return
            if path=='/api/status':
                p=ROOT/'web'/'corpus.json';data=json.loads(p.read_text(encoding='utf-8'))
                self.reply({**STATE,'updated_at':data['updated_at'],'documents':len(data['documents'])});return
            if path=='/api/corpus':
                self.reply(json.loads((ROOT/'web'/'corpus.json').read_text(encoding='utf-8')));return
            self.reply({'error':'接口不存在'},404);return
        # Only serve the reader assets. Corpus archives, Android source, and service files are outside this root.
        if path=='/corpus.json' and TOKEN and not self.authorized(): self.reply({'error':'需要书房服务访问令牌'},401);return
        super().do_GET()
    def do_POST(self):
        path=self.path.split('?')[0]
        if path!='/api/sync': self.reply({'error':'接口不存在'},404);return
        if not self.authorized(): self.reply({'error':'需要书房服务访问令牌'},401);return
        origin=self.headers.get('Origin')
        expected='http://'+self.headers.get('Host','')
        if origin and origin!=expected: self.reply({'error':'不接受跨站写入'},403);return
        if int(self.headers.get('Content-Length','0') or 0)>100: self.reply({'error':'请求过大'},413);return
        threading.Thread(target=synchronize,daemon=True).start()
        self.reply({'running':True,'message':'正在后台更新；失败时保留原有书房'})

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--host',default='127.0.0.1');parser.add_argument('--port',type=int,default=8765);parser.add_argument('--daily',action='store_true');args=parser.parse_args()
    if args.host not in ('localhost','127.0.0.1','::1') and not TOKEN: raise SystemExit('远程服务必须先配置 XIANGDONG_SERVER_TOKEN，并置于 HTTPS 反向代理后')
    if args.daily: threading.Thread(target=scheduler,daemon=True).start()
    print(f'XiangDong reader: http://{args.host}:{args.port}',flush=True)
    ThreadingHTTPServer((args.host,args.port),Handler).serve_forever()
