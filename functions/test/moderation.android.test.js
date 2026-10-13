'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),cp=require('node:child_process');
const root=path.resolve(__dirname,'../..');
const java=path.join(root,'app/src/main/java/com/zeytindefteri/app');
test('actual Android page model: backend order, opaque cursor, dedup, filter reset, safe update',()=>{
 const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'zd-mod-page-'));
 try{
  fs.writeFileSync(path.join(tmp,'PageCheck.java'),`package com.zeytindefteri.app;
import java.util.*;
public class PageCheck {
 static void check(boolean v){if(!v)throw new AssertionError();}
 static Map<String,Object> row(String who,String id,String state){Map<String,Object> m=new LinkedHashMap<>();m.put("reporter_uid",who);m.put("report_id",id);m.put("status",state);return m;}
 static Map<String,Object> response(Object cursor,Object... rows){Map<String,Object> m=new LinkedHashMap<>();m.put("reports",Arrays.asList(rows));m.put("next_cursor",cursor);return m;}
 public static void main(String[] args){
  ModerationPage p=new ModerationPage();check(p.status.equals("OPEN"));check(!p.request(false).containsKey("cursor"));
  Object opaque=new LinkedHashMap<String,Object>();
  Map<String,Object> newest=row("A","z","OPEN"),older=row("A","a","OPEN"),other=row("B","a","OPEN");
  p.accept(response(opaque,newest,older));check(p.request(true).get("cursor")==opaque);
  p.accept(response(null,older,other));check(p.rows.size()==3);check(p.cursor==null);
  check(new ArrayList<>(p.rows.keySet()).equals(Arrays.asList("A/z","A/a","B/a")));
  for(String state:ModerationPage.STATES){p.reset(state);check(p.rows.isEmpty()&&p.cursor==null);check(p.request(true).get("status").equals(state));check(!p.request(true).containsKey("cursor"));p.accept(response(null,row("A","r",state)));}
  Map<String,Object> update=ModerationPage.update(newest,"REVIEWED");
  check(update.keySet().equals(new HashSet<>(Arrays.asList("reporter_uid","report_id","status","expected_status"))));
  check(update.get("expected_status").equals("OPEN"));check(update.get("status").equals("REVIEWED"));
  p.reset("OPEN");p.accept(response(opaque,newest));
  try{p.accept(response(null,older,"bad"));throw new AssertionError();}catch(IllegalArgumentException expected){}
  check(p.rows.size()==1&&p.cursor==opaque);
  try{p.accept(response(null,row("A","r","REVIEWED")));throw new AssertionError();}catch(IllegalArgumentException expected){}
  check(p.rows.size()==1);System.out.println("ModerationPage PASS");
 }
}`);
  fs.writeFileSync(path.join(tmp,'Compile.java'),'import javax.tools.ToolProvider; public class Compile {public static void main(String[] a){System.exit(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,a));}}');
  cp.execFileSync('java',[path.join(tmp,'Compile.java'),'-source','8','-target','8','-d',tmp,path.join(java,'ModerationPage.java'),path.join(tmp,'PageCheck.java')],{stdio:'pipe'});
  assert.match(cp.execFileSync('java',['-cp',tmp,'com.zeytindefteri.app.PageCheck'],{encoding:'utf8'}),/PASS/);
 }finally{fs.rmSync(tmp,{recursive:true,force:true});}
});
test('Android wiring retains backend boundary and lifecycle guards (static)',()=>{
 const activity=fs.readFileSync(path.join(java,'ModerationActivity.java'),'utf8');
 const access=fs.readFileSync(path.join(java,'ModerationAccess.java'),'utf8');
 const manifest=fs.readFileSync(path.join(root,'app/src/main/AndroidManifest.xml'),'utf8');
 assert.match(manifest,/<activity android:name="\.ModerationActivity" android:exported="false"/);
 assert.match(access,/Boolean.TRUE.equals\(claims.get\("moderation_admin"\)\)/);
 const host=fs.readFileSync(path.join(java,'SettingsContentHost.java'),'utf8');
 assert.match(host,/user.getIdToken\(true\)/);
 assert.match(host,/requests.accept\(request, scope\(\)\)/);
 assert.ok(!access.includes('setVisibility'));
 assert.match(access,/if \(!access.hasManagement\(\)\) return null/);
 assert.match(activity,/user.getIdToken\(true\)/);assert.match(activity,/if \(!ModerationAccess.allowed/);
 assert.match(activity,/ModerationBackendClient.getInstance\(\)/);
 for(const name of ['moderationListReports','moderationUpdateReportStatus'])assert.ok(activity.includes('operation("'+name+'")'));
 assert.ok(!activity.includes('FirebaseFirestore'));assert.ok(!activity.includes('setCustomUserClaims'));
 assert.match(activity,/generation == request/);assert.match(activity,/owner.equals\(user.getUid\(\)\)/);
 assert.match(activity,/PERMISSION_DENIED/);assert.match(activity,/page.reset\(state\)/);
});
