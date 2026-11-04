package cn.xiangdong.poetry;

/** Shared, platform-independent geometry: every artwork uses contain, never crop. */
public final class PosterGeometry {
 public static final int BURGUNDY=0xff6b2028;
 public static final class Box {
  public final double left,top,width,height;
  Box(double x,double y,double w,double h){left=x;top=y;width=w;height=h;}
  public double right(){return left+width;}
  public double bottom(){return top+height;}
 }
 public static Box contain(double imageWidth,double imageHeight,double x,double y,double width,double height){
  if(imageWidth<=0||imageHeight<=0||width<=0||height<=0)throw new IllegalArgumentException("Invalid image bounds");
  double scale=Math.min(width/imageWidth,height/imageHeight),w=imageWidth*scale,h=imageHeight*scale;
  return new Box(x+(width-w)/2,y+(height-h)/2,w,h);
 }
 public static double quoteTop(int width,int height){return height>width?height*.245:height*.13;}
 public static double quoteFont(int width,int height){return Math.min(width*.068,height*.063);}
 public static Box artwork(int width,int height,int imageWidth,int imageHeight,double textBottom){
  double top=textBottom+height*.035,bottom=height*.94;
  return contain(imageWidth,imageHeight,0,top,width,Math.max(height*.12,bottom-top));
 }
 /** Robust paper tone from light edge samples; dark brush strokes don't tint the background. */
 public static int paperColor(int[] samples){
  int[] r=new int[samples.length],g=new int[samples.length],b=new int[samples.length];int n=0;
  for(int c:samples){int red=(c>>>16)&255,green=(c>>>8)&255,blue=c&255;
   if((c>>>24)<128||(.2126*red+.7152*green+.0722*blue)<110)continue;
   r[n]=red;g[n]=green;b[n]=blue;n++;
  }
  if(n==0)return 0xfff1e7d5;
  java.util.Arrays.sort(r,0,n);java.util.Arrays.sort(g,0,n);java.util.Arrays.sort(b,0,n);
  return 0xff000000|(r[n/2]<<16)|(g[n/2]<<8)|b[n/2];
 }
 private PosterGeometry(){}
}
