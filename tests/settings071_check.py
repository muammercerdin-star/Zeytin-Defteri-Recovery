"""Actual SettingsContentHost/ModerationAccess/SettingsRenderState under deterministic Android/Auth fakes.
Tests callback/render order, not physical-device pixels or live Firebase accounts.
"""
from pathlib import Path
import subprocess,tempfile,xml.etree.ElementTree as ET,re
root=Path(__file__).resolve().parents[1];java=root/'app/src/main/java/com/zeytindefteri/app'
stubs={
'android/content/res/Resources.java':'''package android.content.res; public class Resources {public static class Metrics{public float density=1;}public Metrics getDisplayMetrics(){return new Metrics();}}''',
'android/app/Activity.java':'''package android.app;import android.content.Intent;public class Activity {public Class<?> opened;public void startActivity(Intent i){opened=i.target;}public boolean isFinishing(){return false;}public boolean isDestroyed(){return false;}}''',
'android/content/Intent.java':'''package android.content;public class Intent{public Class<?> target;public Intent(Object a,Class<?> c){target=c;}}''',
'android/view/Gravity.java':'''package android.view;public class Gravity{public static final int CENTER=17;}''',
'android/view/View.java':'''package android.view;
import java.util.*;import android.app.Activity;import android.content.res.Resources;
public class View {
 public interface OnClickListener{void onClick(View v);} public OnClickListener click;
 public List<View> children=new ArrayList<>();public boolean attached;public int scroll;
 public View(){}public View(Activity a){} public void setBackgroundColor(int c){}public void setPadding(int a,int b,int c,int d){}public void setFocusable(boolean b){}public void setMinimumHeight(int h){}
 public void setOnClickListener(OnClickListener c){click=c;}public void performClick(){click.onClick(this);}
 public Resources getResources(){return new Resources();}public boolean isAttachedToWindow(){return attached;}
 protected void onAttachedToWindow(){}protected void onDetachedFromWindow(){}public void onWindowFocusChanged(boolean f){}protected void onLayout(boolean c,int l,int t,int r,int b){}
 public void attach(){attached=true;onAttachedToWindow();for(View v:new ArrayList<>(children))if(!v.attached)v.attach();}
 public void detach(){for(View v:new ArrayList<>(children))if(v.attached)v.detach();attached=false;onDetachedFromWindow();}
 public void layout(){onLayout(true,0,0,360,640);}public void scrollTo(int x,int y){scroll=y;}public int getScrollY(){return scroll;}
 public void addView(View v){children.add(v);if(attached)v.attach();}public void addView(View v,Object params){addView(v);}
 public void removeAllViews(){for(View v:new ArrayList<>(children))if(v.attached)v.detach();children.clear();}
}
''',
'android/widget/FrameLayout.java':'''package android.widget;public class FrameLayout extends android.view.View{public FrameLayout(android.app.Activity a){super(a);}public static class LayoutParams{public LayoutParams(int w,int h){}public LayoutParams(int w,int h,int g){}}}''',
'android/widget/LinearLayout.java':'''package android.widget;public class LinearLayout extends android.view.View{public LinearLayout(android.app.Activity a){super(a);}public void setGravity(int v){}public static class LayoutParams{public LayoutParams(int w,int h){}}}''',
'android/widget/ProgressBar.java':'''package android.widget;public class ProgressBar extends android.view.View{public ProgressBar(android.app.Activity a){super(a);}}''',
'android/widget/TextView.java':'''package android.widget;public class TextView extends android.view.View{public TextView(android.app.Activity a){super(a);}public void setText(String s){}public void setTextSize(int s){}public void setTextColor(int c){}}''',
'com/google/firebase/auth/FirebaseAuth.java':'''package com.google.firebase.auth;import java.util.*;public class FirebaseAuth{public interface AuthStateListener{void onAuthStateChanged(FirebaseAuth a);}static final FirebaseAuth SINGLE=new FirebaseAuth();public FirebaseUser user;public List<AuthStateListener> listeners=new ArrayList<>();public static FirebaseAuth getInstance(){return SINGLE;}public FirebaseUser getCurrentUser(){return user;}public void addAuthStateListener(AuthStateListener l){listeners.add(l);l.onAuthStateChanged(this);}public void removeAuthStateListener(AuthStateListener l){listeners.remove(l);}public void change(FirebaseUser u){user=u;for(AuthStateListener l:new ArrayList<>(listeners))l.onAuthStateChanged(this);}}''',
'com/google/firebase/auth/FirebaseUser.java':'''package com.google.firebase.auth;import java.util.*;import java.util.function.Consumer;
public class FirebaseUser{public String uid;public boolean anonymous,success=true;public Map<String,Object> claims=new HashMap<>();public List<TokenTask> pending=new ArrayList<>();public FirebaseUser(String id){uid=id;}public String getUid(){return uid;}public boolean isAnonymous(){return anonymous;}public TokenTask getIdToken(boolean force){if(!force)throw new AssertionError("must refresh");TokenTask t=new TokenTask(new HashMap<>(claims),success);pending.add(t);return t;}public TokenTask last(){return pending.get(pending.size()-1);}public static class TokenTask{Map<String,Object> claims;boolean success;Consumer<TokenTask> callback;TokenTask(Map<String,Object> c,boolean s){claims=c;success=s;}public void addOnCompleteListener(Consumer<TokenTask> c){callback=c;}public boolean isSuccessful(){return success;}public TokenTask getResult(){return this;}public Map<String,Object> getClaims(){return claims;}public void complete(){callback.accept(this);}}}
''',
'com/zeytindefteri/app/SessionManager.java':'''package com.zeytindefteri.app;class SessionManager{static String uid="session",type="USER";SessionManager(android.app.Activity a){}String getUserUid(){return uid;}String getSessionType(){return type;}}''',
'com/zeytindefteri/app/GreenTheme.java':'''package com.zeytindefteri.app;class GreenTheme{static int color(int c){return c;}}''',
'com/zeytindefteri/app/R.java':'''package com.zeytindefteri.app;class R{static class color{static final int zd_bg=1,zd_muted=2;}static class drawable{static final int ic_settings_shield=1,ic_settings_users=2;}}''',
'com/zeytindefteri/app/ModerationActivity.java':'''package com.zeytindefteri.app;class ModerationActivity extends android.app.Activity{}''',
'com/zeytindefteri/app/ModeratorManagementActivity.java':'''package com.zeytindefteri.app;class ModeratorManagementActivity extends android.app.Activity{}''',
'com/zeytindefteri/app/SettingsView.java':'''package com.zeytindefteri.app;
import android.app.Activity;import android.view.View;
class SettingsView extends View{
 final ModerationAccess.Access access;final View management;SettingsView(Activity a,ModerationAccess.Access r){super(a);access=r;management=ModerationAccess.settingsSection(a,r);}
 static View row(Activity a,int icon,String title,String sub,String value,Runnable r){View v=new View(a);v.setOnClickListener(x->r.run());return v;}
 static View separator(Activity a){return new View(a);}static View section(Activity a,String title,View... children){View v=new View(a);for(View c:children)v.addView(c);return v;}
}
''',
'com/zeytindefteri/app/Settings071Check.java':'''package com.zeytindefteri.app;
import android.app.Activity;import android.view.View;import com.google.firebase.auth.*;import java.util.*;
public class Settings071Check {
static int builds;static String fingerprint="profile-1";static Activity a=new Activity();
static void ok(boolean b,String label){if(!b)throw new AssertionError(label);}
static SettingsContentHost host(){return new SettingsContentHost(a,()->fingerprint,r->{builds++;return new SettingsView(a,r);});}
static SettingsView content(SettingsContentHost h){ok(h.children.size()==1&&h.children.get(0) instanceof SettingsView,"single complete view");return (SettingsView)h.children.get(0);}
static void pending(SettingsContentHost h){ok(h.children.size()==1&&!(h.children.get(0) instanceof SettingsView),"no incomplete settings before token");}
static void role(SettingsView v,boolean mod,boolean sup){ok(v.access.moderator==mod&&v.access.superAdmin==sup,"full snapshot");if(!mod&&!sup)ok(v.management==null,"no heading/no placeholder");else ok(v.management.children.size()==(mod&&sup?3:1),"all rows present before mount");}
static FirebaseUser user(String id,boolean mod,boolean sup){FirebaseUser u=new FirebaseUser(id);if(mod)u.claims.put("moderation_admin",true);if(sup)u.claims.put("super_admin",true);return u;}
static void cycle(int count,boolean mod,boolean sup){
 FirebaseAuth auth=FirebaseAuth.getInstance();FirebaseUser u=user("repeat-"+mod+sup,mod,sup);auth.user=u;
 for(int i=0;i<count;i++){
  int before=builds,requests=u.pending.size();SettingsContentHost h=host();h.attach();pending(h);
  // Initial attach/auth/focus/background notifications coalesce while loading.
  h.onWindowFocusChanged(true);h.refreshContent();h.resume();ok(u.pending.size()==requests+1,"one role request for both rows");pending(h);
  u.last().complete();role(content(h),mod,sup);ok(builds==before+1,"one initial atomic mount");
  SettingsView first=content(h);first.scrollTo(0,137);h.refreshContent();h.refreshContent();ok(content(h)==first&&first.getScrollY()==137,"sync refresh keeps identity/scroll");
  h.onWindowFocusChanged(true);u.last().complete();ok(content(h)==first&&builds==before+1,"same-role focus refresh does not rebuild");
  h.onWindowFocusChanged(true);FirebaseUser.TokenTask late=u.last();h.detach();late.complete();ok(h.children.isEmpty()&&builds==before+1,"detached callback rejected");ok(auth.listeners.isEmpty(),"listener released");
 }
 System.out.println("PASS entry/exit cycles="+count+" moderator="+mod+" super="+sup);
}
public static void main(String[] args){
 cycle(10,true,true);cycle(5,true,false);cycle(5,false,false);
 FirebaseAuth auth=FirebaseAuth.getInstance();FirebaseUser u=user("live",true,true);auth.user=u;SettingsContentHost h=host();h.attach();u.last().complete();h.layout();content(h).scrollTo(0,181);
 // A confirmed role change replaces the whole tree once, never individual rows.
 u.claims.clear();h.onWindowFocusChanged(true);SettingsView old=content(h);role(old,true,true);u.last().complete();role(content(h),false,false);h.layout();ok(content(h).getScrollY()==181,"scroll restored before draw");
 fingerprint="profile-2";h.refreshContent();u.last().complete();h.layout();ok(content(h).getScrollY()==181,"profile/season rebuild preserves offset");
 // Pause/resume, out-of-order completion, then account switch while old request is pending.
 h.onWindowFocusChanged(true);FirebaseUser.TokenTask paused=u.last();int before=builds;h.pause();paused.complete();ok(builds==before,"paused callback ignored");h.resume();FirebaseUser.TokenTask resumed=u.last();paused.complete();ok(builds==before,"old generation cannot consume new request");resumed.complete();
 h.onWindowFocusChanged(true);FirebaseUser.TokenTask oldAccount=u.last();FirebaseUser normal=user("other",false,false);SessionManager.uid="other";auth.change(normal);pending(h);oldAccount.complete();pending(h);normal.last().complete();role(content(h),false,false);
 normal.success=false;h.onWindowFocusChanged(true);normal.last().complete();pending(h); // error gate, not a guessed role
 normal.success=true;h.children.get(0).performClick();normal.last().complete();role(content(h),false,false);
 normal.claims.put("moderation_admin","true");normal.claims.put("super_admin","true");h.onWindowFocusChanged(true);normal.last().complete();role(content(h),false,false);
 auth.change(null);role(content(h),false,false);normal.anonymous=true;auth.change(normal);role(content(h),false,false);
 h.detach();ok(auth.listeners.isEmpty(),"cleanup");
 // Same Firebase UID with changed local session must also invalidate pending UI ownership.
 normal.anonymous=false;normal.claims.put("moderation_admin",true);auth.user=normal;h=host();h.attach();FirebaseUser.TokenTask sessionLate=normal.last();SessionManager.uid="changed-session";h.refreshContent();pending(h);sessionLate.complete();pending(h);normal.last().complete();role(content(h),true,false);h.detach();
 for(String route:new String[]{"HOME→PROFILE","MEYDAN→PROFILE","GARDENS→PROFILE","PROFILE→MEYDAN→PROFILE"}){
  h=host();h.attach();normal.last().complete();role(content(h),true,false);h.detach();System.out.println("PASS host attach/detach route model: "+route);
 }
 System.out.println("PASS whole-tree updates, scroll, token failure/retry, stale callbacks, account/session isolation and lifecycle");
}}
'''
}
with tempfile.TemporaryDirectory(prefix='settings071-') as temp:
 t=Path(temp)
 for name,code in stubs.items():p=t/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code)
 (t/'Compile.java').write_text('import javax.tools.ToolProvider;public class Compile{public static void main(String[] a){System.exit(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,a));}}')
 sources=list(t.rglob('*.java'))+[java/(n+'.java') for n in ['SettingsContentHost','SettingsRenderState','ModerationAccess','ModeratorManagementState']]
 subprocess.run(['java',str(t/'Compile.java'),'-source','8','-target','8','-d',str(t/'out')]+list(map(str,sources)),check=True)
 subprocess.run(['java','-cp',str(t/'out'),'com.zeytindefteri.app.Settings071Check'],check=True)
view=(java/'SettingsView.java').read_text();shell=(java/'ProducerFeedActivity.java').read_text();profile=(java/'ProfileActivity.java').read_text();host=(java/'SettingsContentHost.java').read_text();access=(java/'ModerationAccess.java').read_text()
assert 'getIdToken' not in access and 'setVisibility' not in access
assert 'postDelayed' not in host and 'getIdToken(true)' in host
assert 'if (management != null) body.addView(management)' in view
assert 'setVerticalScrollBarEnabled(false)' in view
assert 'settingsContent.refreshContent();' in shell and 'settingsContent.getParent() == contentHost' in shell
assert 'settingsContent.pause()' in shell and 'settingsContent.resume()' in shell
assert 'db.getActiveSeasonYear(), this::showSeasonPicker, this::openZeytinAssistant, access' in shell
assert 'getBooleanExtra(EXTRA_CONFIRM_LOGOUT, false)' in profile
ns='{http://schemas.android.com/apk/res/android}';manifest=ET.parse(root/'app/src/main/AndroidManifest.xml');activities={a.get(ns+'name') for a in manifest.findall('.//activity')}
for name in ['ProfileActivity','BlockedUsersActivity','RemindersActivity','PrivacyActivity','TermsActivity','ModerationActivity','ModeratorManagementActivity']:assert '.'+name in activities
for name in ['ProfileActivity','BlockedUsersActivity','RemindersActivity','PrivacyActivity','TermsActivity']:assert name+'.class' in view
for xml in (root/'app/src/main/res').rglob('*.xml'):ET.parse(xml)
for icon in re.findall(r'R.drawable.(\w+)',view+access):assert any((root/'app/src/main/res/drawable').glob(icon+'.*')),icon
nav=shell.split('private void refreshBottomNav()')[1].split('private LinearLayout navItem')[0]
positions=[nav.index(x) for x in ['"Ana Sayfa"','"Bahçelerim"','"Elek"','"ASİSTAN"','"Zeytin Ligi"','"Sohbet"','"Ayarlar"']];assert positions==sorted(positions)
assert 'section.equals(currentSection)' in shell
print('PASS static: atomic host wiring, existing intents/logout, seven tabs, scrollbar, XML and Manifest')
