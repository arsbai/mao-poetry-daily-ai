package cn.xiangdong.poetry;

import android.app.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;

/** A deliberate preview step. Applying touches only FLAG_LOCK. */
public final class WallpaperActivity extends Activity {
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private ImageView preview;private TextView message;private Button apply,retry;
 private Bitmap artwork,poster;private DailyQuote daily;private volatile boolean gone;private int generation;
 private int dp(float v){return (int)(getResources().getDisplayMetrics().density*v+.5f);}
 @Override public void onCreate(Bundle state){
  super.onCreate(state);getWindow().setStatusBarColor(0xfff5f2eb);getWindow().setNavigationBarColor(0xfff5f2eb);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(12),dp(20),dp(18));root.setBackgroundColor(0xfff5f2eb);
  root.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(dp(20)+insets.getSystemWindowInsetLeft(),dp(12)+insets.getSystemWindowInsetTop(),dp(20)+insets.getSystemWindowInsetRight(),dp(18)+insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
  LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);Button back=new Button(this);back.setText("返回");back.setOnClickListener(v->finish());toolbar.addView(back,new LinearLayout.LayoutParams(dp(72),dp(48)));
  TextView title=new TextView(this);title.setText("锁屏预览");title.setTextSize(20);title.setTextColor(Color.BLACK);title.setGravity(Gravity.CENTER);toolbar.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(toolbar);
  preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setContentDescription("一句话与完整书法的锁屏构图");LinearLayout.LayoutParams imageParams=new LinearLayout.LayoutParams(-1,0,1);imageParams.setMargins(0,dp(12),0,dp(12));root.addView(preview,imageParams);
  message=new TextView(this);message.setTextSize(12);message.setTextColor(0xff655c54);message.setGravity(Gravity.CENTER);root.addView(message,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);retry=new Button(this);retry.setText("重新载入");retry.setOnClickListener(v->load());actions.addView(retry,new LinearLayout.LayoutParams(0,dp(52),1));
  apply=new Button(this);apply.setText("设为锁屏");apply.setTextColor(Color.WHITE);apply.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff8f3c34));apply.setEnabled(false);apply.setOnClickListener(v->setLockScreen());LinearLayout.LayoutParams applyParams=new LinearLayout.LayoutParams(0,dp(52),1);applyParams.setMargins(dp(12),0,0,0);actions.addView(apply,applyParams);root.addView(actions);setContentView(root);root.requestApplyInsets();load();
 }
 private int[] screenSize(){DisplayMetrics metrics=new DisplayMetrics();getWindowManager().getDefaultDisplay().getRealMetrics(metrics);int width=Math.max(1,metrics.widthPixels),height=Math.max(1,metrics.heightPixels);double ratio=Math.min(1,Math.min(3840.0/Math.max(width,height),Math.sqrt(10000000.0/((double)width*height))));return new int[]{Math.max(1,(int)(width*ratio)),Math.max(1,(int)(height*ratio))};}
 private void load(){
  if(gone)return;
  final int request=++generation;final int[] size=screenSize();apply.setEnabled(false);retry.setEnabled(false);message.setText("正在载入对应书法并按屏幕比例排版…");
  worker.execute(()->{try{
   if(daily==null)daily=DailyQuote.requested(this,getIntent().getStringExtra("documentId"),getIntent().getStringExtra("quote"));
   if(artwork==null)artwork=WallpaperPoster.loadArtwork(this,daily.artworkURL());
   Bitmap result=WallpaperPoster.render(this,daily,artwork,size[0],size[1]);
   runOnUiThread(()->{if(gone||request!=generation){result.recycle();return;}Bitmap previous=poster;poster=result;preview.setImageBitmap(result);if(previous!=null)previous.recycle();apply.setEnabled(true);retry.setEnabled(true);message.setText("书法完整显示，上方留出时钟区域。\n点击下方按钮应用此张锁屏。");});
  }catch(Exception|OutOfMemoryError e){runOnUiThread(()->{if(gone||request!=generation)return;retry.setEnabled(true);message.setText("预览未完成："+(e instanceof OutOfMemoryError?"图片较大，请换一句重试":e.getMessage()));});}});
 }
 private void setLockScreen(){
  if(poster==null)return;apply.setEnabled(false);retry.setEnabled(false);message.setText("正在设置锁屏…");final Bitmap selected=poster;
  worker.execute(()->{String result;boolean ok=false;try{
   WallpaperManager manager=WallpaperManager.getInstance(this);
   if(!manager.isWallpaperSupported()||!manager.isSetWallpaperAllowed())throw new IllegalStateException("当前设备不允许应用设置壁纸");
   int id=manager.setBitmap(selected,new Rect(0,0,selected.getWidth(),selected.getHeight()),false,WallpaperManager.FLAG_LOCK);
   if(id<=0)throw new IllegalStateException("系统未完成设置，请稍后重试");ok=true;result="锁屏已设置";
  }catch(Exception e){result="未能设置锁屏："+e.getMessage();}
  final String status=result;final boolean success=ok;runOnUiThread(()->{if(gone)return;message.setText(status);apply.setEnabled(true);retry.setEnabled(true);if(success)Toast.makeText(this,status,Toast.LENGTH_LONG).show();});});
 }
 @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);load();}
 @Override public void onDestroy(){gone=true;preview.setImageDrawable(null);worker.execute(()->{if(artwork!=null)artwork.recycle();if(poster!=null)poster.recycle();});worker.shutdown();super.onDestroy();}
}
