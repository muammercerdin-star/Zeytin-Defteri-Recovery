"""Host JVM adapter tests with in-memory test doubles, NOT an Android/device build."""
from pathlib import Path
import tempfile,subprocess
root=Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix='zeytin-tests-') as directory:
    temp=Path(directory)
    stubs={
    'android/content/Context.java':'''package android.content; public class Context { public Context getApplicationContext(){return this;} }''',
    'android/database/Cursor.java':'''package android.database; public interface Cursor extends AutoCloseable {boolean moveToNext(); int getColumnIndexOrThrow(String s); boolean isNull(int i); String getString(int i);long getLong(int i);double getDouble(int i);void close();}''',
    'com/zeytindefteri/app/DatabaseHelper.java':'''package com.zeytindefteri.app;
import java.util.*; import android.database.Cursor; import android.content.Context;
public class DatabaseHelper {
 public static String owner="A";public static int season=2026;
 public static Map<String,List<Map<String,Object>>> tables=new HashMap<>();
 public DatabaseHelper(Context c){} public void close(){} public String getActiveOwnerUid(){return owner;}public int getActiveSeasonYear(){return season;}
 public static void add(String table,Object... values){Map<String,Object> row=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)row.put((String)values[i],values[i+1]);if(!tables.containsKey(table))tables.put(table,new ArrayList<>());tables.get(table).add(row);}
 static Cursor rows(String table,long garden){List<Map<String,Object>> out=new ArrayList<>();for(Map<String,Object> row:tables.getOrDefault(table,Collections.emptyList())){
 if(!owner.equals(row.get("owner")))continue;
 if(!table.equals("gardens") && !Integer.valueOf(season).equals(row.get("season")))continue;
 if(garden>0 && ((Number)row.get("garden_id")).longValue()!=garden)continue;
 out.add(row);}return new C(out);}
 public Cursor getGardens(){return rows("gardens",0);}public Cursor getGardenOperations(long id){return rows("operations",id);}public Cursor getGardenHarvests(long id){return rows("harvests",id);}public Cursor getGardenSales(long id){return rows("sales",id);}public Cursor getGardenElekRecords(long id){return rows("elek",id);}public Cursor getElekGrades(long id){return rows("grades",id);}public Cursor getBrineEvents(long id){return rows("brine_events",id);}public Cursor getBrineBatches(){return rows("brine",0);}
 static class C implements Cursor {List<Map<String,Object>> rows;int p=-1;List<String> keys;C(List<Map<String,Object>> r){rows=r;}public boolean moveToNext(){if(++p>=rows.size())return false;keys=new ArrayList<>(rows.get(p).keySet());return true;}public int getColumnIndexOrThrow(String s){int i=keys.indexOf(s);if(i<0)throw new IllegalArgumentException(s);return i;}Object value(int i){return rows.get(p).get(keys.get(i));}public boolean isNull(int i){return value(i)==null;}public String getString(int i){return String.valueOf(value(i));}public long getLong(int i){return ((Number)value(i)).longValue();}public double getDouble(int i){return ((Number)value(i)).doubleValue();}public void close(){}}
}''',
    'com/zeytindefteri/app/SaleRepository.java':'''package com.zeytindefteri.app; import android.database.Cursor;public class SaleRepository {public SaleRepository(DatabaseHelper d){} public Cursor getSale(long id){return DatabaseHelper.rows("sale_detail",id);}public Cursor getSaleGrades(long id){return DatabaseHelper.rows("sale_grades",id);}}'''
    }
    # Compile the exact existing weather DTO declarations, not the network implementation.
    weather=(root/'app/src/main/java/com/zeytindefteri/app/WeatherRepository.java').read_text()
    model=weather[weather.index('    public static class WeatherSnapshot'):weather.index('    private final DatabaseHelper db;')]
    stubs['com/zeytindefteri/app/WeatherRepository.java']='package com.zeytindefteri.app; import java.util.*; public class WeatherRepository {\n'+model+'\n}'
    for name,content in stubs.items():
        p=temp/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content)
    compiler=temp/'Compile.java';compiler.write_text('import javax.tools.ToolProvider; public class Compile {public static void main(String[] args){System.exit(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,args));}}')
    production=[root/'app/src/main/java/com/zeytindefteri/app'/f for f in ['ZeytinAssistantCommand.java','ZeytinAssistantData.java','ZeytinAssistantWeather.java','ZeytinAssistantPeriod.java','GardenTodayAdvisor.java']]
    tests=[root/'tests/assistant/AssistantCommandTest.java',root/'tests/assistant/AssistantDataTest.java',root/'tests/final_assistant/FinalCommandTest.java',root/'tests/final_assistant/FinalDataTest.java',root/'tests/today/GardenTodayAdvisorTest.java']
    subprocess.run(['java',str(compiler),'--release','8','-encoding','UTF-8','-d',str(temp/'classes')]+[str(temp/n) for n in stubs]+list(map(str,production+tests)),check=True)
    for test in ['AssistantCommandTest','AssistantDataTest','FinalCommandTest','FinalDataTest','GardenTodayAdvisorTest']:subprocess.run(['java','-cp',str(temp/'classes'),test],check=True)
