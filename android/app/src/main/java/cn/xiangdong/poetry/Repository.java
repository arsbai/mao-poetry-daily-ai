package cn.xiangdong.poetry;
import android.content.Context;
import android.content.SharedPreferences;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

final class Repository {
 static final String ORIGIN="https://www.dlmzd.net",DB="KRM_1741159449741";
 static final Object LOCK=new Object();
 private static final java.util.concurrent.locks.ReentrantLock SYNC=new java.util.concurrent.locks.ReentrantLock();
 static boolean isSyncing(){return SYNC.isLocked();}
 static String read(InputStream in) throws IOException {
  try(InputStream s=in;ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=s.read(buf))!=-1){b.write(buf,0,n);if(b.size()>16*1024*1024)throw new IOException("内容超过允许大小");}return b.toString("UTF-8");}
 }
 static JSONObject request(String address,JSONObject body,String token) throws Exception {
  URL url=new URL(address);if(!url.getProtocol().equals("https")&&!(url.getProtocol().equals("http")&&Arrays.asList("127.0.0.1","localhost","10.0.2.2").contains(url.getHost())))throw new IOException("请使用 HTTPS 服务地址");
  if(url.getUserInfo()!=null||url.getRef()!=null)throw new IOException("地址不能包含账号或片段");
  HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(65000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","XiangDong/1.0 text reader");
  try{
   if(!token.isEmpty())c.setRequestProperty("Authorization","Bearer "+token);
   if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
   int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("服务返回 "+code+"，请检查连接设置");return new JSONObject(read(c.getInputStream()));
  }finally{c.disconnect();}
 }
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("reader",0);}
 static JSONObject config(Context c) throws Exception {
  SharedPreferences p=prefs(c);return new JSONObject().put("apiBase",p.getString("apiBase","")).put("model",p.getString("model","")).put("serverBase",p.getString("serverBase","")).put("hasKey",!SecretStore.get(c,"apiKey").isEmpty()).put("hasServerToken",!SecretStore.get(c,"serverToken").isEmpty());
 }
 static void validateBase(String address,boolean model) throws Exception {
  URI uri=new URI(address);String host=uri.getHost();if(host==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)throw new IOException("请输入完整的服务地址");
  if(!"https".equals(uri.getScheme())&&(model||!"http".equals(uri.getScheme())||!Arrays.asList("localhost","127.0.0.1","10.0.2.2").contains(host)))throw new IOException("请使用 HTTPS 地址");
 }
 static JSONObject saveSettings(Context c,JSONObject args) throws Exception {
  SharedPreferences.Editor e=prefs(c).edit();for(String k:new String[]{"apiBase","model","serverBase"})if(args.has(k)){String value=args.getString(k).trim();if(k.equals("apiBase"))validateBase(value,true);if(k.equals("serverBase")&&!value.isEmpty())validateBase(value,false);e.putString(k,value);}
  if(args.has("key")&&!args.getString("key").isEmpty())SecretStore.put(c,"apiKey",args.getString("key"));
  if(args.has("serverToken")&&!args.getString("serverToken").isEmpty())SecretStore.put(c,"serverToken",args.getString("serverToken"));e.apply();return config(c);
 }
 static JSONObject corpus(Context c) throws Exception {
  synchronized(LOCK){File f=new File(c.getFilesDir(),"corpus.json");if(f.exists())try{return new JSONObject(read(new FileInputStream(f)));}catch(Exception ignored){}return new JSONObject(read(c.getAssets().open("corpus.json")));}
 }
 static void validateCorpus(JSONObject data) throws Exception {
  JSONArray docs=data.getJSONArray("documents");if(docs.length()==0)throw new IOException("服务返回了空书房");Set<String> ids=new HashSet<>();
  for(int i=0;i<docs.length();i++){JSONObject d=docs.getJSONObject(i);if(!d.getString("author").equals("毛泽东")||d.getString("text").length()<5||!ids.add(d.getString("id")))throw new IOException("原文数据格式不符合要求");String source=d.getString("source_url");if(!source.startsWith("https://"))throw new IOException("来源链接格式不符合要求");}
 }
 static void commit(Context c,JSONObject data) throws Exception {
  validateCorpus(data);synchronized(LOCK){AtomicFile f=new AtomicFile(new File(c.getFilesDir(),"corpus.json"));FileOutputStream out=f.startWrite();try{out.write(data.toString().getBytes(StandardCharsets.UTF_8));f.finishWrite(out);}catch(Exception ex){f.failWrite(out);throw ex;}}
  prefs(c).edit().putString("syncDay",LocalDate.now().toString()).putString("lastError","").apply();
  DailyWidgetProvider.refreshAll(c);
 }
 static String clean(String s){return s.replaceAll("<[^>]*>","").replace("&nbsp;","").replace("&amp;","&").replaceAll("\\s+","");}
 static String cleanPoem(String s){return clean(s).split("作者原注",2)[0].replaceAll("[（(]原注[）)]","").trim();}
 static JSONArray files(JSONObject props) {
  JSONArray result=new JSONArray();Set<String> used=new HashSet<>();for(String k:new String[]{"MFILE","MCOVER"})try{JSONArray a=new JSONArray(props.optString(k,"[]"));for(int i=0;i<a.length();i++){String path=a.getJSONObject(i).optString("filePath");if(path.matches("[\\w/.-]+\\.(gif|jpg|jpeg|png|webp)")&&used.add(path))result.put(new JSONObject().put("url",ORIGIN+"/miniocnki/"+path).put("label","平台配套书法"));}}catch(Exception ignored){}return result;
 }
 static boolean sync(Context c,boolean onlyIfDue) throws Exception {
  if(!SYNC.tryLock())return false;
  try{
   if(onlyIfDue&&LocalDate.now().toString().equals(prefs(c).getString("syncDay","")))return false;
   String server=prefs(c).getString("serverBase","");
   if(!server.isEmpty()){validateBase(server,false);JSONObject data=request(server.replaceAll("/+$","")+"/api/corpus",null,SecretStore.get(c,"serverToken"));commit(c,data);return true;}
   JSONArray entries=new JSONArray();int current=1,pages=1;
   do {JSONObject query=new JSONObject().put("dataProperty",DB).put("existCnki",true).put("conditions",new JSONArray()).put("searchList",new JSONArray()).put("mode","ADVANCED").put("dbway","SINGLE").put("sorts",new JSONArray()).put("current",current).put("size",100);JSONObject list=request(ORIGIN+"/api/knowledge/getSingleKnowledgeListClient",query,"");if(list.optInt("code")!=200)throw new IOException("诗词目录暂不可用");JSONObject data=list.getJSONObject("data");pages=data.optInt("pages",1);JSONArray page=data.getJSONArray("records");for(int i=0;i<page.length();i++)entries.put(page.get(i));current++;if(current<=pages)Thread.sleep(1000);}while(current<=pages&&current<=100);
   JSONArray poems=new JSONArray();Set<String> seen=new HashSet<>();
   for(int i=0;i<entries.length();i++){
    if(Thread.currentThread().isInterrupted())throw new InterruptedException();long began=System.currentTimeMillis();JSONObject record=entries.getJSONObject(i);String guid=record.getString("uuid");
    JSONObject detail=request(ORIGIN+"/api/knowledge/dynamic/searchDetail",new JSONObject().put("dataProperty",DB).put("guid",guid),"");if(detail.optInt("code")!=200)throw new IOException("诗词正文更新未完成");JSONObject p=detail.getJSONObject("data").getJSONObject("props");
    if(clean(p.optString("AUTHOR")).equals("毛泽东")&&seen.add(guid)){
     String text=cleanPoem(p.optString("MFULLTEXT"));if(text.length()<8)continue;String title=clean(p.optString("TITLE")),sub=clean(p.optString("SUBTITLE"));if(!sub.isEmpty())title+="·"+sub;
     poems.put(new JSONObject().put("id","poem-"+guid).put("title",title).put("author","毛泽东").put("kind","诗词").put("date",p.optString("PUBLISH_TIME")).put("year",p.optString("KRM_YEAR")).put("text",text).put("source_name","毛泽东文献数字资源平台").put("source_url",ORIGIN+"/detail-poem?guid="+guid+"&base="+DB).put("edition_note",p.optString("SOURCE")).put("artwork",files(p)).put("author_basis","平台AUTHOR字段为毛泽东；仅提取MFULLTEXT"));
    }
    long pause=1000-(System.currentTimeMillis()-began);if(pause>0)Thread.sleep(pause);
   }
   if(poems.length()<Math.max(1,entries.length()*.9))throw new IOException("更新不完整，已保留本机原文");
   JSONObject old=corpus(c);JSONArray oldDocs=old.getJSONArray("documents"),merged=new JSONArray();for(int i=0;i<oldDocs.length();i++){JSONObject d=oldDocs.getJSONObject(i);if(!d.optString("kind").equals("诗词"))merged.put(d);}for(int i=0;i<poems.length();i++)merged.put(poems.get(i));commit(c,new JSONObject().put("schema",1).put("updated_at",java.time.Instant.now().toString()).put("documents",merged));return true;
  }finally{SYNC.unlock();}
 }
 static void schedule(Context c){JobScheduler js=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);js.schedule(new JobInfo.Builder(7301,new ComponentName(c,DailySyncService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setPeriodic(24L*60*60*1000,4L*60*60*1000).build());}
}
