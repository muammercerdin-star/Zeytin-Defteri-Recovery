"""Compile/run the actual Android parser method bodies with org.json; no APK/device claim."""
from pathlib import Path
import os,subprocess,tempfile
root=Path(__file__).resolve().parents[1];java=root/'app/src/main/java/com/zeytindefteri/app'
s=(java/'MarketCloudRepository.java').read_text();methods=s[s.index('    private void parseSnapshot('):s.index('    private RegionRef parseRegionRef(')]
with tempfile.TemporaryDirectory(prefix='market-five-') as tmp:
 p=Path(tmp);jar=os.environ['JSON_JAR']
 harness='''package com.zeytindefteri.app;
import org.json.*;import java.util.*;
public class MarketFiveCheck {
 static class Result {boolean available,previousAvailable;int producerCount,recordCount,confidenceScore,outlierCount,previousRecordCount;double totalKg,median,rangeLow,rangeHigh,previousMedian,changeAbs,changePct;String confidenceLabel;List<ChartPoint> chart=new ArrayList<>();List<RegionStat> regions=new ArrayList<>();}
 static class ChartPoint {String day;double price;}
 static class RegionStat {String id,name;double median,totalKg;int producerCount;}
 static String safe(String s){return s==null?"":s;}static int confidenceScore(String s){return 0;}
'''+methods+'''
 public static void main(String[] args)throws Exception{
  MarketFiveCheck parser=new MarketFiveCheck();
  if(MarketBackendConfig.PUBLIC_MARKET_MIN_PRODUCERS!=5)throw new AssertionError();
  for(int n=1;n<=5;n++){
   Result out=new Result();JSONObject raw=new JSONObject().put("published",true).put("producer_count",n).put("record_count",10).put("median_price",60).put("min_price",55).put("max_price",65);
   parser.parseSnapshot(raw,out);if(out.available!=(n==5)||out.median!=(n==5?60:0))throw new AssertionError("snapshot "+n);
   parser.parseChart(new JSONArray().put(new JSONObject().put("market_date","2026-09-10").put("median_price",60)),out);
   if(out.chart.size()!=(n==5?1:0))throw new AssertionError("hidden chart");
   parser.parseRegionStats(new JSONArray().put(new JSONObject().put("region_key","test").put("median_price",60).put("producer_count",n)),out);
   if(out.regions.size()!=(n==5?1:0))throw new AssertionError("region "+n);
   System.out.println("PASS Android parser: "+n+" producers, 10 sales -> "+(out.available?"PUBLIC":"HIDDEN"));
  }
  Result out=new Result();parser.parseSnapshot(new JSONObject().put("published",false).put("producer_count",5).put("median_price",60),out);if(out.available||out.median!=0)throw new AssertionError("server denial ignored");
  out=new Result();parser.parseSnapshot(new JSONObject().put("published",true).put("median_price",60),out);if(out.available)throw new AssertionError("missing count accepted");
 }
}
'''
 (p/'MarketFiveCheck.java').write_text(harness);(p/'Compile.java').write_text('import javax.tools.ToolProvider;public class Compile{public static void main(String[] a){System.exit(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,a));}}')
 subprocess.run(['java',str(p/'Compile.java'),'-source','8','-target','8','-cp',jar,'-d',tmp,str(java/'MarketBackendConfig.java'),str(p/'MarketFiveCheck.java')],check=True)
 subprocess.run(['java','-cp',tmp+os.pathsep+jar,'com.zeytindefteri.app.MarketFiveCheck'],check=True)
