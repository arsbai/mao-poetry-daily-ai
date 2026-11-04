package cn.xiangdong.poetry;

import android.content.Context;
import android.graphics.*;
import android.text.*;
import java.io.*;
import java.net.*;

final class WallpaperPoster {
 static Bitmap loadArtwork(Context context,String address) throws Exception {
  URI uri=new URI(address);
  if(!"https".equals(uri.getScheme())||!"www.dlmzd.net".equals(uri.getHost())||uri.getPort()!=-1||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!uri.getPath().startsWith("/miniocnki/")||uri.getPath().contains(".."))throw new IOException("这首诗没有可用的配套书法");
  // Artwork stays in the app cache, never in the text knowledge base.
  String digest=toHex(java.security.MessageDigest.getInstance("SHA-256").digest(address.getBytes("UTF-8")));
  File directory=new File(context.getCacheDir(),"wallpaper-art");directory.mkdirs();File cached=new File(directory,digest);
  byte[] bytes;
  if(cached.exists()&&System.currentTimeMillis()-cached.lastModified()<30L*86400000){try(InputStream in=new FileInputStream(cached)){bytes=read(in);}}
  else{
   HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection();connection.setConnectTimeout(15000);connection.setReadTimeout(35000);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("User-Agent","XiangDong/1.1 wallpaper preview");
   try{if(connection.getResponseCode()!=200)throw new IOException("书法暂未载入，请联网后重试");try(InputStream in=connection.getInputStream()){bytes=read(in);}}finally{connection.disconnect();}
   // Limit the cache to a few original works.
   File[] older=directory.listFiles();if(older!=null&&older.length>=8){java.util.Arrays.sort(older,java.util.Comparator.comparingLong(File::lastModified));for(int i=0;i<=older.length-8;i++)older[i].delete();}
   try(OutputStream output=new FileOutputStream(cached)){output.write(bytes);}
  }
  BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
  if(options.outWidth<=0||options.outHeight<=0||options.outWidth>40000||options.outHeight>40000){cached.delete();throw new IOException("书法图片格式暂不支持，请重新载入或换一句");}
  options.inSampleSize=1;while((long)(options.outWidth/options.inSampleSize)*(options.outHeight/options.inSampleSize)>8_000_000L)options.inSampleSize*=2;
  options.inJustDecodeBounds=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;Bitmap image=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
  if(image==null){cached.delete();throw new IOException("书法图片未能打开，请重新载入");}return image;
 }
 private static byte[] read(InputStream in) throws IOException {ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1){output.write(block,0,n);if(output.size()>20*1024*1024)throw new IOException("书法图片过大，请换一句重试");}return output.toByteArray();}
 private static String toHex(byte[] b){StringBuilder s=new StringBuilder();for(byte value:b)s.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return s.toString();}
 static Bitmap render(Context context,DailyQuote daily,Bitmap artwork,int width,int height){
  int[] samples=new int[160];int w=artwork.getWidth(),h=artwork.getHeight();
  for(int i=0;i<40;i++){int x=(int)((w-1)*i/39.0),y=(int)((h-1)*i/39.0);samples[i]=artwork.getPixel(x,Math.min(h-1,1));samples[40+i]=artwork.getPixel(x,Math.max(0,h-2));samples[80+i]=artwork.getPixel(Math.min(w-1,1),y);samples[120+i]=artwork.getPixel(Math.max(0,w-2),y);}
  int paper=PosterGeometry.paperColor(samples);Bitmap poster=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(poster);
  TextPaint quotePaint=new TextPaint(Paint.ANTI_ALIAS_FLAG);quotePaint.setColor(Color.BLACK);quotePaint.setTypeface(Typeface.createFromAsset(context.getAssets(),"MaShanZheng-Regular.ttf"));
  float textWidth=width*.86f,font=(float)PosterGeometry.quoteFont(width,height);String quote=daily.quote.replace("，","，\n");StaticLayout quoteLayout;
  do{quotePaint.setTextSize(font);quoteLayout=StaticLayout.Builder.obtain(quote,0,quote.length(),quotePaint,(int)textWidth).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setLineSpacing(0,1.22f).build();font*=.93f;}while(quoteLayout.getHeight()>height*.21f&&font>width*.026f);
  TextPaint sourcePaint=new TextPaint(Paint.ANTI_ALIAS_FLAG);sourcePaint.setColor(Color.BLACK);sourcePaint.setTextSize(Math.min(width*.027f,height*.033f));
  String source="—— "+daily.title();StaticLayout sourceLayout=StaticLayout.Builder.obtain(source,0,source.length(),sourcePaint,(int)textWidth).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build();
  float quoteTop=(float)PosterGeometry.quoteTop(width,height),sourceTop=quoteTop+quoteLayout.getHeight()+height*.012f;
  PosterGeometry.Box box=PosterGeometry.artwork(width,height,w,h,sourceTop+sourceLayout.getHeight());
  // Keep the text and all paper edges light; fade the remaining lower space into deep red.
  float paperEnd=Math.min(.94f,(float)box.bottom()/height),fadeStart=Math.max(.45f,paperEnd);
  Paint background=new Paint();background.setShader(new LinearGradient(0,0,0,height,new int[]{paper,paper,PosterGeometry.BURGUNDY},new float[]{0,fadeStart,1},Shader.TileMode.CLAMP));canvas.drawRect(0,0,width,height,background);
  canvas.save();canvas.translate((width-textWidth)/2,quoteTop);quoteLayout.draw(canvas);canvas.restore();
  canvas.save();canvas.translate((width-textWidth)/2,sourceTop);sourceLayout.draw(canvas);canvas.restore();
  Paint imagePaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);canvas.drawBitmap(artwork,null,new RectF((float)box.left,(float)box.top,(float)box.right(),(float)box.bottom()),imagePaint);
  return poster;
 }
 private WallpaperPoster(){}
}
