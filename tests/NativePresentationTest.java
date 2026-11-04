package cn.xiangdong.poetry;

import org.json.*;
import java.nio.file.*;
import java.time.LocalDate;

/** Runs real Android-independent selection/geometry code on the host JDK. */
public final class NativePresentationTest {
 private static int assertions;
 private static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
 public static void main(String[] args) throws Exception {
  JSONObject corpus=new JSONObject(Files.readString(Path.of(args[0])));JSONArray fixtures=new JSONArray(Files.readString(Path.of(args[1])));
  for(int i=0;i<fixtures.length();i++){
   JSONObject f=fixtures.getJSONObject(i);DailyQuote actual=DailyQuote.select(corpus,LocalDate.parse(f.getString("date")));
   check(actual.quote.equals(f.getString("quote")),"Widget quote differs from homepage on "+f.getString("date"));
   check(actual.documentId().equals(f.getString("documentId")),"Widget source differs from homepage");
  }
  int[][] screens={{1080,2400},{1440,3200},{2400,1080},{768,1024},{1080,1080},{720,1600}};
  int[][] art={{5000,700},{4800,3200},{852,1173},{600,5000},{30,12000},{12000,30},{1000,1000}};
  for(int[] screen:screens)for(int[] image:art){
   PosterGeometry.Box box=PosterGeometry.artwork(screen[0],screen[1],image[0],image[1],screen[1]*.42);
   check(box.left>=-.001&&box.top>=0&&box.right()<=screen[0]+.001&&box.bottom()<=screen[1]+.001,"Artwork escapes the screen");
   check(Math.abs(box.width/box.height-(double)image[0]/image[1])<.000001,"Artwork ratio was distorted");
   check(box.top>=screen[1]*.42,"Artwork overlaps the quote");
  }
  PosterGeometry.Box horizontal=PosterGeometry.artwork(1080,2400,6000,800,1000);
  check(Math.abs(horizontal.width-1080)<.001,"Horizontal artwork must fit the full screen width");
  PosterGeometry.Box vertical=PosterGeometry.artwork(1080,2400,500,6000,1000);
  check(vertical.width<1080&&vertical.bottom()<=2400,"Tall artwork must fit in height without cropping");
  check(PosterGeometry.paperColor(new int[]{0xffe7d9bd,0xffe7d9bd,0xff000000,0x00000000})==0xffe7d9bd,"Ink must not contaminate paper tone");
  check(PosterGeometry.paperColor(new int[]{0xff000000,0x00000000})==0xfff1e7d5,"Unusable edges need a paper fallback");
  System.out.println("Native presentation: "+fixtures.length()+" daily selections and 42 artwork/screen combinations; "+assertions+" assertions passed.");
 }
}
