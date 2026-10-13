from pathlib import Path
import runpy,tempfile,subprocess
base=Path(__file__).resolve().parents[2]
v=runpy.run_path(str(base/'tests/elek_ui/check_form.py'))
code=v['code'].split('public static void main')[0]
for key in v['keys']:code+='EditText etPrice'+key+'=new EditText();TextView tvPrice'+key+'=new TextView();\n'
code+='TextView tvPriceLastUpdate=new TextView();\n'
code+='''static class Toast {static final int LENGTH_SHORT=0;static Toast makeText(Object c,String s,int d){return new Toast();}void show(){}}
static class Dialog {boolean dismissed=false;void dismiss(){dismissed=true;}}
Dialog priceDialog=new Dialog();
static class Prefs {Map<String,String> values=new HashMap<>();int writes=0;Prefs edit(){return this;}Prefs putString(String k,String v){values.put(k,v);return this;}void apply(){writes++;}String getString(String k,String d){return values.getOrDefault(k,d);}}
Prefs preferences=new Prefs();
'''
for key,default in [('DOUBLE',100),('1',90),('2',70),('3',55),('4',40),('OIL',32)]:code+=f'static final String KEY_PRICE_{key}="price_{key}";static final double DEFAULT_PRICE_{key}={default};\n'
code+='static final String KEY_LAST_UPDATE="last_update";\n'
for name in ['updatePrices','updatePriceLabels','moneyUnit','loadSavedPrices','readSavedPrice','plainNumber']:code+=v['method'](name)+'\n'
code+='''public static void main(String[] a){ElekMathTest t=new ElekMathTest();t.setupNumberFormat();t.loadSavedPrices();
t.etKgDouble.setText("2");t.etKg1.setText("15");t.etKg2.setText("30");
t.etPriceDouble.setText("");t.updatePrices();if(t.preferences.writes!=0||t.priceDialog.dismissed)throw new AssertionError("invalid price must not save/close");
t.etPriceDouble.setText("110");t.etPrice1.setText("85");t.etPrice2.setText("60");t.updatePrices();
if(t.preferences.writes!=1||!t.priceDialog.dismissed)throw new AssertionError("valid save/close");
if(!t.etKgDouble.value.equals("2")||!t.etKg1.value.equals("15")||!t.etKg2.value.equals("30"))throw new AssertionError("kg changed");
if(!t.tvTotalRevenue.value.equals("3.295 TL")||!t.tvTotalKg.value.equals("47 kg")||!t.tvAveragePrice.value.equals("70,11 TL/kg"))throw new AssertionError("refresh");
t.etPriceDouble.setText("999");t.loadSavedPrices();
if(!t.etPriceDouble.value.equals("110")||t.preferences.writes!=1)throw new AssertionError("cancel must discard draft without write");
System.out.println("PASS actual price methods: invalid remains open, saved preferences, kg preserved, totals/average refreshed, success dismissed, canceled draft discarded");}}
'''
with tempfile.TemporaryDirectory() as d:
 file=Path(d)/'ElekMathTest.java';file.write_text(code);subprocess.run(['java',str(file)],check=True)
