"""Compile the production scroll view against a small measurement/cache model.
This checks measure arithmetic and invalidation; it is NOT an Android render test.
"""
from pathlib import Path
import tempfile,subprocess,sys
root=Path(__file__).resolve().parents[2]
sources={
'android/content/Context.java':'package android.content; public class Context {}',
'android/content/res/Resources.java':'package android.content.res; public class Resources {public static float density=1; public static class Metrics {public float density=Resources.density;} public Metrics getDisplayMetrics(){return new Metrics();}}',
'android/view/View.java':'''package android.view; import android.content.res.Resources;
public class View {protected int width;protected boolean dirty=true;protected android.widget.LinearLayout.LayoutParams params=new android.widget.LinearLayout.LayoutParams();public Object getLayoutParams(){return params;}public void forceLayout(){dirty=true;}public int getMeasuredWidth(){return width;}public Resources getResources(){return new Resources();}public int getPaddingLeft(){return 0;}public int getPaddingRight(){return 0;}public void measureChild(){if(dirty){width=params.width;dirty=false;}}public static class MeasureSpec {public static final int EXACTLY=1<<30,AT_MOST=2<<30,UNSPECIFIED=0;public static int getMode(int s){return s&0xc0000000;}public static int getSize(int s){return s&0x3fffffff;}public static int makeMeasureSpec(int n,int m){return n|m;}}}''',
'android/widget/LinearLayout.java':'''package android.widget; import android.view.View;import java.util.*;public class LinearLayout extends View {public static class LayoutParams {public int width=60,leftMargin,rightMargin;}public List<View> children=new ArrayList<>();public int getChildCount(){return children.size();}public View getChildAt(int i){return children.get(i);}public void measureStrip(){if(dirty){width=0;for(View c:children){c.measureChild();LayoutParams p=(LayoutParams)c.getLayoutParams();width+=c.getMeasuredWidth()+p.leftMargin+p.rightMargin;}dirty=false;}}}''',
'android/widget/HorizontalScrollView.java':'''package android.widget;import android.view.View;import android.content.Context;public class HorizontalScrollView extends View {public LinearLayout strip;public HorizontalScrollView(Context c){}public int getChildCount(){return strip==null?0:1;}public View getChildAt(int i){return strip;}protected void onMeasure(int w,int h){strip.measureStrip();width=MeasureSpec.getMode(w)==MeasureSpec.EXACTLY?MeasureSpec.getSize(w):Math.min(MeasureSpec.getSize(w),strip.getMeasuredWidth());}public void testMeasure(int w){onMeasure(w,0);}}''',
'WeatherMeasureTest.java':'''import android.view.View;import android.widget.*;import android.content.*;import android.content.res.*;import com.zeytindefteri.app.WeatherForecastScrollView;
public class WeatherMeasureTest {static int count;static void verify(WeatherForecastScrollView v,int width,int mode){v.testMeasure(View.MeasureSpec.makeMeasureSpec(width,mode));int sum=0;for(View c:v.strip.children){LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)c.getLayoutParams();if(c.getMeasuredWidth()!=p.width)throw new AssertionError("stale child cache");sum+=p.width+p.leftMargin+p.rightMargin;}if(sum>v.getMeasuredWidth() || v.getMeasuredWidth()-sum>4)throw new AssertionError("clipped/unused viewport "+sum+"/"+v.getMeasuredWidth());count++;}
public static void main(String[] a){for(float density:new float[]{1,2,3})for(int screen:new int[]{360,393,412}){Resources.density=density;WeatherForecastScrollView v=new WeatherForecastScrollView(new Context());v.strip=new LinearLayout();for(int i=0;i<5;i++)v.strip.children.add(new View());int width=Math.round((screen-32)*density);verify(v,width,View.MeasureSpec.EXACTLY);verify(v,width-13,View.MeasureSpec.EXACTLY);verify(v,width+11,View.MeasureSpec.AT_MOST);v.strip=new LinearLayout();for(int i=0;i<5;i++)v.strip.children.add(new View());verify(v,width,View.MeasureSpec.EXACTLY);}System.out.println(count+" viewport/cache simulation checks passed; not Android/device layout tests");}}'''
}
with tempfile.TemporaryDirectory() as folder:
 p=Path(folder)
 for name,text in sources.items():
  f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text)
 compiler=p/'Compile.java';compiler.write_text('import javax.tools.ToolProvider;public class Compile {public static void main(String[] a){System.exit(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,a));}}')
 subprocess.run(['java',str(compiler),'--release','8','-d',str(p/'classes')]+[str(p/n) for n in sources]+[str(Path(sys.argv[1]) if len(sys.argv)>1 else root/'app/src/main/java/com/zeytindefteri/app/WeatherForecastScrollView.java')],check=True)
 subprocess.run(['java','-cp',str(p/'classes'),'WeatherMeasureTest'],check=True)
