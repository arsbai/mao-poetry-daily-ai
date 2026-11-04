package cn.xiangdong.poetry;
import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.graphics.Color;
import android.content.Intent;
import android.net.Uri;
import android.webkit.*;
import android.view.View;
import android.widget.FrameLayout;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
 private WebView web;private final ExecutorService io=Executors.newFixedThreadPool(3);private volatile boolean gone=false;private boolean pageReady;
 private static final String APP="https://appassets.androidplatform.net/";
 @Override public void onCreate(Bundle b){
  super.onCreate(b);getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
  FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(245,242,235));web=new WebView(this);root.addView(web,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
  root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});root.requestApplyInsets();
  web.setBackgroundColor(Color.rgb(245,242,235));WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSupportMultipleWindows(false);s.setTextZoom(100);WebView.setWebContentsDebuggingEnabled(false);
  web.addJavascriptInterface(new Bridge(),"Android");web.setWebViewClient(new WebViewClient(){
   @Override public void onPageFinished(WebView v,String url){if(url.startsWith(APP+"assets/")){pageReady=true;openWidgetPoem();}}
   @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request){Uri uri=request.getUrl();if("appassets.androidplatform.net".equals(uri.getHost())&&"https".equals(uri.getScheme())){String p=uri.getPath();if(p==null||!p.startsWith("/assets/")||p.contains(".."))return error();String file=p.substring(8);String type=file.endsWith(".html")?"text/html":file.endsWith(".css")?"text/css":file.endsWith(".js")?"application/javascript":file.endsWith(".json")?"application/json":file.endsWith(".ttf")?"font/ttf":"application/octet-stream";try{InputStream stream=file.equals("corpus.json")?new ByteArrayInputStream(Repository.corpus(MainActivity.this).toString().getBytes(StandardCharsets.UTF_8)):getAssets().open(file);return new WebResourceResponse(type,file.endsWith(".ttf")?null:"UTF-8",stream);}catch(Exception e){return error();}}return null;}
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){Uri u=r.getUrl();if(u.toString().startsWith(APP+"assets/")&&!u.getPath().contains(".."))return false;if("https".equals(u.getScheme()))try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}return true;}
  });web.loadUrl(APP+"assets/index.html");Repository.schedule(this);
  io.execute(()->DailyWidgetProvider.refreshAll(getApplicationContext()));
  if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->handleBack());
 }
 private WebResourceResponse error(){return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",java.util.Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
 private void respond(String id,Object value,String error){if(gone)return;runOnUiThread(()->{if(gone)return;try{JSONObject data=new JSONObject().put("ok",error==null);if(error==null)data.put("value",value);else data.put("error",error);web.evaluateJavascript("window.nativeResult("+JSONObject.quote(id)+","+data+")",null);}catch(Exception ignored){}});}
 private final class Bridge {
  @JavascriptInterface public void request(String id,String action,String args){
   if(id==null||id.length()>80||args==null||args.length()>180000)return;
   io.execute(()->{try{JSONObject a=new JSONObject(args);Object result;
    switch(action){
     case "settings":result=Repository.config(MainActivity.this);break;
     case "saveSettings":result=Repository.saveSettings(MainActivity.this,a);break;
     case "clearKey":SecretStore.put(MainActivity.this,"apiKey","");result=Repository.config(MainActivity.this);break;
     case "corpus":result=Repository.corpus(MainActivity.this);break;
     case "sync":result=new JSONObject().put("updated",Repository.sync(getApplicationContext(),false)).put("running",Repository.isSyncing());break;
     case "autoSync":result=new JSONObject().put("updated",Repository.sync(getApplicationContext(),true));break;
     case "pinWidget":runOnUiThread(()->{try{boolean requested=DailyWidgetProvider.pin(MainActivity.this);respond(id,new JSONObject().put("requested",requested),null);}catch(Exception e){respond(id,null,"请长按桌面，在小组件列表中选择“向东 · 每日一句”");}});return;
     case "openWallpaper":
      DailyQuote selected=DailyQuote.requested(MainActivity.this,a.optString("documentId"),a.optString("quote"));
      runOnUiThread(()->{try{startActivity(new Intent(MainActivity.this,WallpaperActivity.class).putExtra("documentId",selected.documentId()).putExtra("quote",selected.quote));respond(id,true,null);}catch(Exception e){respond(id,null,"锁屏预览未能打开，请重试");}});return;
     case "model":
      String base=Repository.prefs(MainActivity.this).getString("apiBase","");Repository.validateBase(base,true);String key=SecretStore.get(MainActivity.this,"apiKey");if(key.isEmpty())throw new IOException("请先保存 API 密钥");
      String endpoint=base.replaceAll("/+$","").replaceAll("/chat/completions$","")+"/chat/completions";
      JSONObject response=Repository.request(endpoint,a.getJSONObject("body"),key);result=response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");break;
     default:throw new IOException("不支持的操作");
    }respond(id,result,null);
   }catch(Exception e){String m=e.getMessage();respond(id,null,m==null?"操作未完成，请稍后重试":m.length()>160?"连接未完成，请检查服务设置":m);}});
  }
 }
 private void openWidgetPoem(){if(!pageReady)return;String id=getIntent().getStringExtra("widgetDocumentId");if(id==null||id.length()>100)return;getIntent().removeExtra("widgetDocumentId");web.evaluateJavascript("window.openWidgetPoem("+JSONObject.quote(id)+")",null);}
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);openWidgetPoem();}
 private void handleBack(){web.evaluateJavascript("(function(){const d=document.querySelector('dialog[open]');if(d){d.close();return true;}if(document.querySelector('#view-home.active'))return false;document.querySelector('.mobile-nav [data-tab=home]').click();return true;})()",v->{if("false".equals(v))finish();});}
 @Override public void onBackPressed(){handleBack();}
 @Override public void onDestroy(){gone=true;io.shutdownNow();if(web!=null){web.removeJavascriptInterface("Android");web.destroy();}super.onDestroy();}
}
