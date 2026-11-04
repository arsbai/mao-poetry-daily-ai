package cn.xiangdong.poetry;
import android.app.job.JobService;
import android.app.job.JobParameters;

public final class DailySyncService extends JobService {
 private Thread worker;
 @Override public boolean onStartJob(JobParameters params){worker=new Thread(()->{boolean retry=false;try{Repository.sync(getApplicationContext(),true);}catch(Exception e){retry=true;Repository.prefs(this).edit().putString("lastError","更新未完成，已保留现有原文").apply();}jobFinished(params,retry);},"daily-poems");worker.start();return true;}
 @Override public boolean onStopJob(JobParameters params){if(worker!=null)worker.interrupt();return true;}
}
