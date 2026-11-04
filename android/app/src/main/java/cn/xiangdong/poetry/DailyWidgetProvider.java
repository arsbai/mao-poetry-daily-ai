package cn.xiangdong.poetry;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;
import java.time.*;
import java.time.format.DateTimeFormatter;

public final class DailyWidgetProvider extends AppWidgetProvider {
 private static final String REFRESH="cn.xiangdong.poetry.WIDGET_REFRESH";
 @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids){refreshAsync(c,manager,ids);}
 @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager manager,int id,Bundle options){refreshAsync(c,manager,new int[]{id});}
 @Override public void onEnabled(Context c){schedule(c);}
 @Override public void onDisabled(Context c){AlarmManager alarms=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);alarms.cancel(refreshIntent(c));}
 @Override public void onReceive(Context c,Intent intent){
  String action=intent.getAction();
  if(REFRESH.equals(action)||Intent.ACTION_DATE_CHANGED.equals(action)||Intent.ACTION_TIME_CHANGED.equals(action)||Intent.ACTION_TIMEZONE_CHANGED.equals(action)||Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)){
   AppWidgetManager manager=AppWidgetManager.getInstance(c);refreshAsync(c,manager,manager.getAppWidgetIds(new ComponentName(c,DailyWidgetProvider.class)));return;
  }
  super.onReceive(c,intent);
 }
 private void refreshAsync(Context c,AppWidgetManager manager,int[] ids){
  if(ids==null||ids.length==0)return;final PendingResult pending=goAsync();final Context app=c.getApplicationContext();
  new Thread(()->{try{update(app,manager,ids);schedule(app);}finally{pending.finish();}},"daily-widget").start();
 }
 static void refreshAll(Context c){AppWidgetManager manager=AppWidgetManager.getInstance(c);int[] ids=manager.getAppWidgetIds(new ComponentName(c,DailyWidgetProvider.class));if(ids.length>0){update(c,manager,ids);schedule(c);}}
 static void update(Context c,AppWidgetManager manager,int[] ids){
  try{DailyQuote daily=DailyQuote.today(c);for(int id:ids){
   RemoteViews views=new RemoteViews(c.getPackageName(),R.layout.daily_widget);Bundle options=manager.getAppWidgetOptions(id);
   boolean landscape=c.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
   int width=options.getInt(landscape?AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH:AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,250);
   int height=options.getInt(landscape?AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT:AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,140);
   views.setTextViewText(R.id.widget_date,LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 · E",java.util.Locale.CHINA)));
   views.setTextViewText(R.id.widget_quote,daily.quote.replace("，","，\n"));views.setTextViewText(R.id.widget_source,daily.title());
   views.setViewVisibility(R.id.widget_source,height<125?View.GONE:View.VISIBLE);
   float density=c.getResources().getDisplayMetrics().density;int side=(int)((width<220?12:18)*density),vertical=(int)(12*density);views.setViewPadding(R.id.widget_root,side,vertical,side,vertical);
   Intent open=new Intent(c,MainActivity.class).putExtra("widgetDocumentId",daily.documentId()).setData(android.net.Uri.parse("xiangdong://poem/"+daily.documentId())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
   views.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(c,id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));views.setOnClickPendingIntent(R.id.widget_refresh,refreshIntent(c));manager.updateAppWidget(id,views);
  }}catch(Exception e){/* Keep the last usable widget when the local snapshot cannot be opened. */}
 }
 private static PendingIntent refreshIntent(Context c){return PendingIntent.getBroadcast(c,7302,new Intent(c,DailyWidgetProvider.class).setAction(REFRESH),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 static void schedule(Context c){
  if(AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c,DailyWidgetProvider.class)).length==0)return;
  long midnight=LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
  ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).setAndAllowWhileIdle(AlarmManager.RTC,midnight,refreshIntent(c));
 }
 static boolean pin(Activity activity){AppWidgetManager manager=AppWidgetManager.getInstance(activity);return manager.isRequestPinAppWidgetSupported()&&manager.requestPinAppWidget(new ComponentName(activity,DailyWidgetProvider.class),null,null);}
}
