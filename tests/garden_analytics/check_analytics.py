"""Production Java analytics and Report binding executed against real host SQLite.
Android bridge is reused from İş Emri 01; this is not an Android/device build.
"""
from pathlib import Path
import ast,re,sqlite3,subprocess,sys,tempfile,math,hashlib
ROOT=Path(__file__).resolve().parents[2]
BRIDGE=ROOT/'tests/garden_detail/check_sqlite.py'
module=ast.parse(BRIDGE.read_text())
constants={n.targets[0].id:ast.literal_eval(n.value) for n in module.body if isinstance(n,ast.Assign)
           and isinstance(n.targets[0],ast.Name) and n.targets[0].id in ['CONTENT_VALUES','CURSOR','DATABASE']}
constants['CURSOR']=constants['CURSOR'].replace(' public void close(){}',' public boolean isNull(int i){return getString(i)==null;} public void close(){}')
constants['DATABASE']=constants['DATABASE'].replace(' private Process bridge;', ' public static int queries=0; private Process bridge;')
constants['DATABASE']=constants['DATABASE'].replace(' public Cursor rawQuery(String sql,String[] args){',' public Cursor rawQuery(String sql,String[] args){queries++;')
constants['DATABASE']=constants['DATABASE'].replace(' public void beginTransaction(){',' public void beginTransactionNonExclusive(){beginTransaction();}\n public void beginTransaction(){')
source=(ROOT/'app/src/main/java/DatabaseHelper.java').read_text()
def method(source,signature):
 a=source.index(signature);i=source.index('{',a)+1;depth=1
 while depth:
  depth+=(source[i]=='{')-(source[i]=='}');i+=1
 return source[a:i]
legacy='\n'.join(method(source,s) for s in ['public double getOperationCostTotal(',
 'public double getTotalHarvestKg(', 'public double getTotalHarvestCost(', 'public double getTotalBrineCost(',
 'public double getTotalSalesRevenue(', 'public double getTotalSoldKg(', 'public double getTotalAmountReceived(',
 'public double getTotalExpenses(', 'public double getTotalOpenReceivableBalance(',
 'private String activeOwnerCondition(', 'private String[] activeOwnerArgs(', 'private String activeSeasonCondition('])
a=source.index('    private double getDoubleValue(');b=source.index('    private String safe(',a)
legacy+=source[a:b]
HELPER='''package com.zeytindefteri.app;import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;import java.util.*;
public class DatabaseHelper {private final SQLiteDatabase db;private final String owner;private final int year;
 public DatabaseHelper(SQLiteDatabase db,String owner,int year){this.db=db;this.owner=owner;this.year=year;}
 public SQLiteDatabase getReadableDatabase(){return db;}public String getActiveOwnerUid(){return owner;} public int getActiveSeasonYear(){return year;}
'''+legacy+'}'
report=(ROOT/'app/src/main/java/ReportActivity.java').read_text()
projection='''package com.zeytindefteri.app;import java.text.NumberFormat;import java.util.Locale;
class ReportProjection {
 static class TextView {String value="";void setText(String s){value=s;}void setTextColor(int c){}}
 static class Container {void removeAllViews(){}void addView(Object o){}}
 final TextView tvTotalExpense=new TextView(),tvTotalHarvest=new TextView(),tvSalesRevenue=new TextView(),tvNetProfit=new TextView(),tvSoldKg=new TextView(),tvSaleCount=new TextView(),tvCollected=new TextView(),tvReceivable=new TextView(),tvBrineOlive=new TextView(),tvReadyBrine=new TextView(),tvBrineCost=new TextView(),tvActiveBrine=new TextView();
 final Container containerGardens=new Container();final int COLOR_RED=1,COLOR_GREEN=2,COLOR_GRAY=3;final Locale TR=new Locale("tr","TR");
 Object createText(String s,int n,int c,boolean b){return s;}
 Object createGardenReportCard(String n,int t,String v,double h,double s,double c,double r,double p){return n;}
 void render(SeasonAnalyticsSnapshot s){showReport(s);}
'''+ '\n'.join(method(report,s) for s in ['private void showReport(', 'private String formatMoney(',
 'private String formatKg(', 'private String formatSignedMoney(', 'private String formatNumber('])+'}'
RUNNER='''package com.zeytindefteri.app;
import android.database.sqlite.SQLiteDatabase;
public class RunAnalytics {
 static void print(GardenAnalyticsSnapshot s){
 System.out.println("S\\t"+s.gardenId+"\\t"+s.season+"\\t"+s.harvestKg+"\\t"+s.expense+"\\t"+s.revenue+"\\t"+s.grossSales+"\\t"+s.soldKg+"\\t"+s.collected+"\\t"+s.receivable+"\\t"+s.netResult()+"\\t"+s.kgPerTree()+"\\t"+s.kgPerDecare()+"\\t"+s.costPerKg()+"\\t"+s.averageSalePrice()+"\\t"+s.dominantGrade()+"\\t"+s.harvestRecords+"\\t"+s.saleRecords+"\\t"+s.elekRecords+"\\t"+s.gardenCount+"\\t"+s.unassignedCost+"\\t"+s.unassignedReceivable);
 for(int i=0;i<6;i++)System.out.println("G\\t"+s.gardenId+"\\t"+s.season+"\\t"+i+"\\t"+s.gradeKg(i));
 }
 public static void main(String[] args)throws Exception{
  try(SQLiteDatabase db=new SQLiteDatabase(args[0],args[1],args[2])){
   DatabaseHelper h=new DatabaseHelper(db,args[3],Integer.parseInt(args[4]));
   if(args[5].equals("legacy")){System.out.println(h.getTotalHarvestKg()+"\\t"+h.getTotalExpenses()+"\\t"+h.getTotalSalesRevenue()+"\\t"+h.getTotalAmountReceived()+"\\t"+h.getTotalOpenReceivableBalance());return;}
   GardenAnalyticsRepository.Result r=new GardenAnalyticsRepository(h).load(args[3],Integer.parseInt(args[4]),true);
   for(SeasonAnalyticsSnapshot season:new SeasonAnalyticsSnapshot[]{r.current,r.previous}){print(season.total);for(GardenAnalyticsSnapshot s:season.gardens.values())print(s);}
   System.out.println("Q\\t"+SQLiteDatabase.queries);
   GardenAnalyticsSnapshot c=r.current.total,p=r.previous.total;
   System.out.println("P\\t"+GardenAnalyticsSnapshot.percent(c.harvestKg,p.harvestKg,c.harvestRecords>0,p.harvestRecords>0));
   ReportProjection report=new ReportProjection();report.render(r.current);
   System.out.println("R\\t"+report.tvTotalHarvest.value+"\\t"+report.tvTotalExpense.value+"\\t"+report.tvSalesRevenue.value+"\\t"+report.tvCollected.value+"\\t"+report.tvReceivable.value);
   if(GardenAnalyticsSnapshot.percent(1.0,0.0,true,true)!=null)throw new AssertionError("zero previous");
   if(GardenAnalyticsSnapshot.percent(1.0,2.0,false,true)!=null)throw new AssertionError("missing current");
   if(GardenAnalyticsSnapshot.ratio(1,0)!=null)throw new AssertionError("division zero");
   if(GardenAnalyticsSnapshot.ratio(Double.POSITIVE_INFINITY,1)!=null)throw new AssertionError("infinity ratio");
   if(GardenAnalyticsSnapshot.percent(Double.NaN,2.0,true,true)!=null)throw new AssertionError("NaN percent");
   GardenAnalyticsSnapshot.Builder bad=new GardenAnalyticsSnapshot.Builder();
   bad.revenue=Double.MAX_VALUE;bad.expense=-Double.MAX_VALUE;
   try { new GardenAnalyticsSnapshot(bad,2026);throw new AssertionError("overflow snapshot accepted"); }
   catch(IllegalArgumentException expected) {}

  }
 }
}'''
fields=['harvest','expense','revenue','gross','sold','collected','balance','net','perTree','perArea','perKg','avg','dominant','harvestCount','saleCount','elekCount','gardenCount','unassignedCost','unassignedBalance']
checks=0
def check(value,label):
 global checks
 assert value,label
 checks+=1;print('PASS',label,flush=True)
def near(a,b):return a is not None and math.isclose(a,b,rel_tol=1e-8,abs_tol=1e-8)
with tempfile.TemporaryDirectory(prefix='zeytin-analytics-') as folder:
 temp=Path(folder)
 for rel,body in {'android/content/ContentValues.java':constants['CONTENT_VALUES'],
                  'android/database/Cursor.java':constants['CURSOR'],'android/database/sqlite/SQLiteDatabase.java':constants['DATABASE'],
                  'com/zeytindefteri/app/DatabaseHelper.java':HELPER,'com/zeytindefteri/app/RunAnalytics.java':RUNNER,
                  'com/zeytindefteri/app/ReportProjection.java':projection}.items():
  p=temp/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body)
 for name in ['GardenAnalyticsRepository','GardenAnalyticsSnapshot','SeasonAnalyticsSnapshot']:
  (temp/'com/zeytindefteri/app'/f'{name}.java').write_text((ROOT/'app/src/main/java/com/zeytindefteri/app'/f'{name}.java').read_text())
 subprocess.run(['java','com.sun.tools.javac.Main','--release','8','-encoding','UTF-8','-d',str(temp/'classes')]+[str(p) for p in temp.rglob('*.java')],check=True)
 file=temp/'analytics.db';db=sqlite3.connect(file)
 schema=(ROOT/'app/src/main/java/DatabaseSchema.java').read_text()
 for expr in re.findall(r'db\.execSQL\(\s*("CREATE TABLE IF NOT EXISTS .*?)\s*\);',schema,re.S):
  db.execute(''.join(ast.literal_eval(s) for s in re.findall(r'"(?:[^"\\]|\\.)*"',expr)))
 db.execute('PRAGMA foreign_keys=ON')
 def insert(table,**values):
  keys=','.join(values);marks=','.join('?' for _ in values)
  return db.execute(f'INSERT INTO {table}({keys}) VALUES({marks})',list(values.values())).lastrowid
 def harvest(garden,owner,year,kg,**kw):return insert('harvests',garden_id=garden,owner_uid=owner,season_year=year,harvest_date=f'{year}-09-09',created_at=1,total_kg=kg,**kw)
 def sale(garden,owner,year,kg,gross,received=0,**kw):return insert('sales',garden_id=garden,owner_uid=owner,season_year=year,sale_date=f'{year}-09-09',created_at=1,total_kg=kg,gross_total=gross,amount_received=received,**kw)
 def receipt(sale_id,amount,received,owner='a',**kw):return insert('receivables',title='Alacak',related_sale_id=sale_id,amount=amount,total_received=received,owner_uid=owner,created_at=1,**kw)
 def run(owner='a',year=2026,action='current'):
  db.commit()
  before_hash=hashlib.sha256(file.read_bytes()).hexdigest()
  out=subprocess.check_output(['java','-cp',str(temp/'classes'),'com.zeytindefteri.app.RunAnalytics',sys.executable,str(BRIDGE),str(file),owner,str(year),action],text=True)
  assert before_hash==hashlib.sha256(file.read_bytes()).hexdigest(),'read-only repository changed SQLite bytes'
  if action=='legacy':return [float(x) for x in out.strip().split('\t')]
  result={};grades={};extra={}
  for line in out.splitlines():
   p=line.split('\t')
   if p[0]=='S':result[(int(p[1]),int(p[2]))]={key:(value if key=='dominant' else None if value=='null' else float(value)) for key,value in zip(fields,p[3:])}
   elif p[0]=='G':grades[(int(p[1]),int(p[2]),int(p[3]))]=float(p[4])
   else:extra[p[0]]=p[1:]
  return result,grades,extra
 for id,name,owner,trees,area in [(1,'Girelli','a',120,10),(2,'Afşar','a',10,0),(3,'Başka kullanıcı','b',50,2),(4,'Guest','',0,0)]:
  insert('gardens',id=id,name=name,owner_uid=owner,tree_count=trees,area_decare=area)
 harvest(1,'a',2026,1850,labor_cost=300)
 insert('operations',garden_id=1,owner_uid='a',season_year=2026,operation_date='2026-09-09',created_at=1,type='Sulama',cost=18000)
 insert('operations',garden_id=1,owner_uid='a',season_year=2026,operation_date='2026-09-09',created_at=1,type='Hasat',cost=300)
 batch=insert('brine_batches',garden_id=1,owner_uid='a',season_year=2026,start_date='2026-09-09',batch_name='Bidon',created_at=1,salt_cost=100)
 insert('brine_events',batch_id=batch,event_date='2026-09-09',event_type='CHECK',created_at=1,cost=50)
 s1=sale(1,'a',2026,100,5000,1000);s2=sale(1,'a',2026,900,63000,10000)
 receipt(s1,5000,1000);receipt(s2,63000,10000)
 insert('sale_grades',sale_id=s1,grade='1',kg=40,price_per_kg=50,line_total=2000)
 insert('sale_grades',sale_id=s1,grade='2',kg=60,price_per_kg=50,line_total=3000)
 insert('sale_grades',sale_id=s2,grade='1',kg=900,price_per_kg=70,line_total=63000)
 e=insert('elek_records',garden_id=1,owner_uid='a',season_year=2026,record_date='2026-09-09',created_at=1)
 for grade,kg in [('DOUBLE',100),('1',900),('2',500),('3',200),('4',100),('YAGLIK',50)]:insert('elek_grades',elek_id=e,grade=grade,kg=kg)
 r,g,x=run();a=r[(1,2026)];t=r[(-1,2026)]
 check(near(a['perTree'],1850/120),'1850 kg / 120 trees = 15.4166667')
 check(near(a['perKg'],18450/1850),'18450 TL / 1850 kg = 9.97297297')
 check(a['avg']==68,'weighted price 100×50 + 900×70 = 68 TL/kg')
 check(a['perArea']==185,'per-decare yield')
 check(a['expense']==18450 and a['net']==49550,'cost components; harvest operation not counted twice; net')
 check(a['collected']==11000 and a['balance']==57000,'sales and mirrored receivables counted once')
 check(a['dominant']=='1 No' and [g[(1,2026,i)] for i in range(6)]==[100,900,500,200,100,50],'actual six Elek grades and dominant')
 check(x['Q']==['15'],'fixed 15 queries for two seasons; no per-garden queries')
 check(x['P']==['null'],'no fake percentage without previous data')
 check(r[(2,2026)]['perKg'] is None and r[(2,2026)]['perArea'] is None and r[(2,2026)]['avg'] is None,'empty garden zero/area/sales safety')
 check(t['perArea'] is None,'all-garden yield not fabricated from partial area')
 old=run(action='legacy')
 check(old==[t['harvest'],t['expense'],t['revenue'],t['collected'],t['balance']],'clean fixture matches original production Report helper formulas')
 check(x['R']==['1.850 kg','18.450 TL','68.000 TL','11.000 TL','57.000 TL'],'actual ReportActivity rendering receives same totals')
 harvest(2,'a',2026,100);harvest(3,'b',2026,999999);harvest(1,'b',2026,999999)
 harvest(1,'a',2025,1000)
 sale(1,'a',2025,10,600);sale(3,'b',2026,99999,999999);sale(1,'b',2026,99999,999999)
 sale(1,'a',2026,99999,999999,sale_kind='ESTIMATE')
 insert('operations',garden_id=1,owner_uid='b',season_year=2026,operation_date='2026-09-09',created_at=1,type='Sulama',cost=999999)
 e2=insert('elek_records',garden_id=1,owner_uid='b',season_year=2026,record_date='2026-09-09',created_at=1);insert('elek_grades',elek_id=e2,grade='YAGLIK',kg=999999)
 r,g,x=run();a=r[(1,2026)];t=r[(-1,2026)]
 check(a['harvest']==1850 and t['harvest']==1950,'garden boundary, other owner and foreign child excluded')
 check(a['revenue']==68000 and a['sold']==1000,'real SALE only, foreign and previous sales excluded')
 check(a['expense']==18450 and a['dominant']=='1 No','foreign operation and Elek child excluded')
 check(r[(1,2025)]['harvest']==1000 and r[(1,2025)]['revenue']==600,'previous season independently scoped')
 check(x['P']==['95.0'],'actual percentage (1950-1000)/1000')
 check((3,2026) not in r and (1,2026) not in run(owner='b')[0],'garden metadata owner isolation')
 legacy_id=harvest(1,'',0,50);db.execute("UPDATE harvests SET harvest_date='09.09.2026' WHERE id=?",(legacy_id,))
 r,g,x=run();check(r[(1,2026)]['harvest']==1900,'legacy blank owner and dd.MM.yyyy season fallback')
 db.execute('UPDATE harvests SET labor_cost=NULL,transport_cost=20 WHERE id=1')
 db.execute('UPDATE sales SET transport_cost=10,deductions=NULL WHERE id=?',(s1,))
 r,g,x=run();a=r[(1,2026)]
 check(a['expense']==18170 and a['revenue']==67990 and a['avg']==68,'NULL components coalesce individually; net revenue versus gross average')
 receipt(s1,4990,2000,updated_at=200)
 receipt(s1,4990,9000,owner='b',updated_at=999)
 r,g,x=run();a=r[(1,2026)]
 check(a['collected']==12000 and a['balance']==55990,'latest owned receivable wins; foreign receipt and older duplicates excluded')
 db.execute('DELETE FROM receivables WHERE related_sale_id=?',(s2,))
 r,g,x=run();check(r[(1,2026)]['balance']==55990,'legacy sale without receivable falls back to sale balance once')
 insert('brine_batches',garden_id=None,owner_uid='a',season_year=2026,start_date='2026-09-09',batch_name='Bağımsız',created_at=1,salt_cost=30)
 insert('brine_batches',garden_id=3,owner_uid='a',season_year=2026,start_date='2026-09-09',batch_name='Yanlış',created_at=1,salt_cost=999999)
 insert('receivables',title='Diğer',amount=100,total_received=20,owner_uid='a',created_at=1788912000000)
 insert('receivables',title='Eski',amount=999999,total_received=0,owner_uid='a',created_at=1757376000000)
 r,g,x=run();a=r[(1,2026)];t=r[(-1,2026)]
 check(t['expense']==a['expense']+30 and t['unassignedCost']==30,'unassigned brine counted once in season only')
 check(t['balance']==a['balance']+80 and t['unassignedBalance']==80,'standalone receivable by creation year, not garden or sales revenue')
 check(x['R'][1]=='18.200 TL' and x['R'][2]=='67.990 TL','actual ReportActivity uses corrected NULL/owner totals too')
 check(x['Q']==['15'],'query count unchanged after adding gardens/children')
 db.execute("UPDATE elek_grades SET kg=900 WHERE elek_id=? AND grade='DOUBLE'",(e,))
 check(run()[0][(1,2026)]['dominant']=='Double','deterministic grade tie: Double before 1 No')
 guest=run(owner='')[0]
 check(guest[(-1,2026)]['gardenCount']==1 and guest[(-1,2026)]['harvest']==0 and guest[(4,2026)]['perTree'] is None,'guest isolation and zero trees')
 check(run(owner='missing')[0][(-1,2026)]['gardenCount']==0,'empty user supported')
 check(not db.execute('PRAGMA foreign_key_check').fetchall(),'foreign keys intact')
 for i in range(400):insert('operations',garden_id=2,owner_uid='a',season_year=2026,operation_date='2026-09-09',created_at=i+1,type='Sulama',cost=1)
 r,g,x=run();check(r[(2,2026)]['expense']==400 and x['Q']==['15'],'400 extra operations, same query budget')
 check(all(value is None or isinstance(value,str) or math.isfinite(value) for row in r.values() for value in row.values()),'all emitted values finite or explicitly missing')
 check(run(owner="a' OR 1=1 --")[0][(-1,2026)]['gardenCount']==0,'bound owner parameter resists SQL-shaped input')
 insert('gardens',id=5,name='Null Guest',owner_uid=None,tree_count=10,area_decare=1)
 h=harvest(5,None,0,20)
 db.execute("UPDATE harvests SET season_year=NULL,harvest_date='2026-09-09' WHERE id=?",(h,))
 null_guest=run(owner='')[0]
 check(null_guest[(5,2026)]['harvest']==20,'NULL guest owner/season and ISO date fallback')
 check(run(owner='a')[0][(-1,2026)]['gardenCount']==2,'null guest cannot enter signed-in summary')
 check(run(owner='a',year=2027)[2]['P']==['null'],'empty current season does not fabricate percent drop')
 check(True,'every repository call left committed SQLite file bytes unchanged')
 db.close()
print(f'PASS {checks} analytics fixture checks; compiled full analytics classes and real Report projection, NOT Android build')
