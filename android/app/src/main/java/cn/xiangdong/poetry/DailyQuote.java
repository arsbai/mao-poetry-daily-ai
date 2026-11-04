package cn.xiangdong.poetry;

import android.content.Context;
import org.json.*;
import java.time.LocalDate;
import java.util.*;

final class DailyQuote {
 static final String[] PREFERRED={"雄关漫道真如铁，而今迈步从头越","世上无难事，只要肯登攀","不管风吹浪打，胜似闲庭信步","更喜岷山千里雪，三军过后尽开颜","天若有情天亦老，人间正道是沧桑","待到山花烂漫时，她在丛中笑","踏遍青山人未老，风景这边独好","长风破浪会有时"};
 final JSONObject document;final String quote;
 DailyQuote(JSONObject d,String q){document=d;quote=q;}
 static DailyQuote today(Context context) throws Exception {return select(Repository.corpus(context),LocalDate.now());}
 static DailyQuote select(JSONObject corpus,LocalDate date) throws Exception {
  JSONArray docs=corpus.getJSONArray("documents");List<DailyQuote> pool=new ArrayList<>();
  for(String phrase:PREFERRED)for(int i=0;i<docs.length();i++){
   JSONObject d=docs.getJSONObject(i);String text=d.optString("text");
   if("诗词".equals(d.optString("kind"))&&"毛泽东".equals(d.optString("author"))&&text.contains(phrase)){
    int end=text.indexOf(phrase)+phrase.length();String punctuation=end<text.length()&&"。！？".indexOf(text.charAt(end))>=0?text.substring(end,end+1):"";
    pool.add(new DailyQuote(d,phrase+punctuation));break;
   }
  }
  if(pool.isEmpty())for(int i=0;i<docs.length();i++){
   JSONObject d=docs.getJSONObject(i);if(!"诗词".equals(d.optString("kind"))||!"毛泽东".equals(d.optString("author")))continue;
   java.util.regex.Matcher m=java.util.regex.Pattern.compile("[^。！？!?；\\n]+[。！？!?；]?").matcher(d.optString("text"));
   while(m.find()){String q=m.group().trim();if(q.length()>=6&&q.length()<60)pool.add(new DailyQuote(d,q));}
  }
  if(pool.isEmpty())throw new IllegalStateException("本机还没有可用诗词");
  return pool.get((int)Math.floorMod(date.toEpochDay(),pool.size()));
 }
 static DailyQuote requested(Context context,String id,String quote) throws Exception {
  JSONArray docs=Repository.corpus(context).getJSONArray("documents");
  if(quote==null||quote.trim().isEmpty()||quote.length()>200)throw new IllegalArgumentException("请选择一句原文");
  for(int i=0;i<docs.length();i++){
   JSONObject d=docs.getJSONObject(i);
   if(d.optString("id").equals(id)&&"毛泽东".equals(d.optString("author"))&&"诗词".equals(d.optString("kind"))&&d.optString("text").contains(quote))return new DailyQuote(d,quote);
  }
  throw new IllegalArgumentException("这句话未能与本机原诗匹配，请重新打开首页");
 }
 String documentId(){return document.optString("id");}
 String title(){return document.optString("title");}
 String artworkURL(){JSONArray a=document.optJSONArray("artwork");return a!=null&&a.length()>0?a.optJSONObject(0).optString("url"):"";}
}
