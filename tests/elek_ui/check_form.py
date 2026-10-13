from pathlib import Path
import re,xml.etree.ElementTree as E,tempfile,subprocess
p=Path(__file__).resolve().parents[2]
s=(p/'app/src/main/java/com/zeytindefteri/app/ElekActivity.java').read_text()
ns='{http://schemas.android.com/apk/res/android}'
root=E.parse(p/'app/src/main/res/layout/activity_elek.xml').getroot();ids={}
for e in root.iter():
 if ns+'id' in e.attrib:
  key=e.attrib[ns+'id'].split('/')[-1];assert key not in ids;ids[key]=e
assert set(re.findall(r'R.id.(\w+)',s))<=set(ids)
for k in ['Double','1','2','3','4','Oil']:assert ids['etKg'+k].tag=='EditText'
assert ids['elekPricePanel'].get(ns+'visibility')=='gone'
assert 'elek_machine_hero' not in E.tostring(root).decode()
assert 'elek_hero_olive' not in E.tostring(root).decode()
for k in ['elekDistributionBar','tvElekDistributionLegend','tvElekSaveHint','tvElekGardenLabel']:assert k in ids
for f in p.glob('app/src/main/**/*.xml'):E.parse(f)

def method(name):
 m=re.search(r'private\s+(?:void|double|String)\s+'+name+r'\s*\(',s);a=m.start();b=s.index('{',m.end());depth=1;i=b+1
 while depth:
  if s[i]=='{':depth+=1
  elif s[i]=='}':depth-=1
  i+=1
 return s[a:i]
keys=['Double','1','2','3','4','Oil']
code='import java.util.*;import java.text.*;class ElekMathTest {\n'
code+='static class TextView {String value="";void setText(String s){value=s;}String getText(){return value;}} static class EditText extends TextView {}\n'
code+='Locale turkishLocale=new Locale("tr","TR");DecimalFormat numberFormat;\n'
for k in keys:code+='EditText etKg'+k+'=new EditText();TextView tvCalc'+k+'=new TextView(),tvTotal'+k+'=new TextView();double price'+k+';\n'
code+='TextView tvTotalKg=new TextView(),tvTotalRevenue=new TextView(),tvAveragePrice=new TextView();\n'
# calculateAll ends with a display-only refresh (dağılım şeridi, kaydet durumu); tested in tests/elek_insights.
code+='void refreshElekInsights(){}\n'
for name in ['setupNumberFormat','calculateAll','readDouble','calculationText','money','formatNumber']:code+=method(name)+'\n'
code+='''public static void main(String[] a){ElekMathTest t=new ElekMathTest();t.setupNumberFormat();
t.priceDouble=100;t.price1=90;t.price2=70;t.price3=55;t.price4=40;t.priceOil=32;
t.calculateAll();if(!t.tvTotalKg.value.equals("0 kg")||!t.tvAveragePrice.value.equals("0 TL/kg"))throw new AssertionError("zero");
t.etKgDouble.setText("120");t.etKg1.setText("100");t.calculateAll();
if(!t.tvTotalDouble.value.equals("12.000 TL")||!t.tvTotalRevenue.value.equals("21.000 TL")||!t.tvTotalKg.value.equals("220 kg")||!t.tvAveragePrice.value.equals("95,45 TL/kg"))throw new AssertionError("totals");
t.etKgOil.setText("2,5");t.calculateAll();if(!t.tvTotalOil.value.equals("80 TL")||!t.tvTotalKg.value.equals("222,5 kg"))throw new AssertionError("decimal");
t.priceOil=40;t.calculateAll();if(!t.tvTotalOil.value.equals("100 TL"))throw new AssertionError("price change");
System.out.println("PASS actual ElekActivity calculation methods: empty, row totals, sum, average, comma decimal, changed price");}}
'''
with tempfile.TemporaryDirectory() as d:
 f=Path(d)/'ElekMathTest.java';f.write_text(code);subprocess.run(['java',str(f)],check=True)
print('PASS XML, all bound IDs, six quantity inputs, hidden prices and removed hero reference')
