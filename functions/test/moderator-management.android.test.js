'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),os=require('node:os'),cp=require('node:child_process');
const root=path.resolve(__dirname,'../..'),java=path.join(root,'app/src/main/java/com/zeytindefteri/app');
test('06.3D real Java model: super gate, exact lookup, grant/revoke UI state, protected super targets and pagination',()=>{
 const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'zd-management-'));
 try{
 fs.writeFileSync(path.join(tmp,'ManagementCheck.java'),`package com.zeytindefteri.app;
import java.util.*;
public class ManagementCheck {
 static void ok(boolean v){if(!v)throw new AssertionError();}
 static Map<String,Object> m(Object... pairs){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
 static Map<String,Object> u(String id,boolean mod,boolean sup){return m("uid",id,"display_name","Üretici","email",id+"@example.com","disabled",false,"moderation_admin",mod,"super_admin",sup);}
 static void rejects(Runnable r){try{r.run();throw new AssertionError();}catch(IllegalArgumentException expected){}}
 public static void main(String[] args){
  ok(!ModeratorManagementState.superAllowed(null));ok(!ModeratorManagementState.superAllowed(m()));
  ok(!ModeratorManagementState.superAllowed(m("moderation_admin",true)));ok(!ModeratorManagementState.superAllowed(m("super_admin","true")));
  ok(ModeratorManagementState.superAllowed(m("super_admin",true,"moderation_admin",true)));
  ok(ModeratorManagementState.lookup(" person@example.com ").equals(m("email","person@example.com")));
  ok(ModeratorManagementState.lookup("full-uid").equals(m("uid","full-uid")));
  ok(ModeratorManagementState.lookup("uid:person@example.com").equals(m("uid","person@example.com")));
  rejects(()->ModeratorManagementState.lookup(" "));rejects(()->ModeratorManagementState.lookup("email:partial"));
  ModeratorManagementState s=new ModeratorManagementState();s.acceptLookup(m("user",u("target",false,false)));
  ok(s.selected.role().equals("Normal Kullanıcı"));ok(s.selected.canChange());
  ok(ModeratorManagementState.target(s.selected).equals(m("target_uid","target")));
  s.acceptChange(s.selected,true,m("uid","target","moderation_admin",true));
  ok(s.selected.role().equals("Moderatör"));ok(s.users.get("target").moderator);
  s.acceptChange(s.users.get("target"),false,m("uid","target","moderation_admin",false));
  ok(s.selected.role().equals("Normal Kullanıcı"));ok(s.users.isEmpty());
  String cursor=String.join("",Collections.nCopies(64,"a"));
  s.acceptList(m("moderators",Arrays.asList(u("one",true,false),u("super",true,true)),"next_cursor",cursor));
  ok(s.listRequest(true).get("cursor").equals(cursor));ok(!s.listRequest(false).containsKey("cursor"));
  ok(s.users.get("super").role().equals("Süper Admin"));ok(!s.users.get("super").canChange());
  rejects(()->ModeratorManagementState.target(s.users.get("super")));
  rejects(()->s.acceptChange(s.users.get("super"),false,m("uid","super","moderation_admin",false)));
  s.acceptList(m("moderators",Arrays.asList(u("one",true,false),u("two",true,false)),"next_cursor",null));ok(s.users.size()==3&&s.cursor==null);
  rejects(()->s.acceptList(m("moderators",Arrays.asList(u("extra",true,false),"bad"),"next_cursor",null)));ok(s.users.size()==3);
  rejects(()->s.acceptList(m("moderators",Arrays.asList(u("extra",false,false)),"next_cursor",null)));ok(s.users.size()==3);
  rejects(()->s.acceptChange(s.selected,true,m("uid","wrong","moderation_admin",true)));ok(!s.selected.moderator);
  s.resetList();s.acceptList(m("moderators",Collections.emptyList(),"next_cursor",cursor));ok(s.users.isEmpty()&&s.cursor.equals(cursor));
  s.clear();ok(s.selected==null&&s.cursor==null&&s.users.isEmpty());
  System.out.println("Management model PASS");
 }
}`);
 fs.writeFileSync(path.join(tmp,'Compile.java'),'import javax.tools.ToolProvider; public class Compile {public static void main(String[] a){System.exit(ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,a));}}');
 cp.execFileSync('java',[path.join(tmp,'Compile.java'),'-source','8','-target','8','-d',tmp,path.join(java,'ModeratorManagementState.java'),path.join(tmp,'ManagementCheck.java')],{stdio:'pipe'});
 assert.match(cp.execFileSync('java',['-cp',tmp,'com.zeytindefteri.app.ManagementCheck'],{encoding:'utf8'}),/PASS/);
 }finally{fs.rmSync(tmp,{recursive:true,force:true});}
});
test('06.3D Android wiring: private activity, force refresh, no client role writes, lifecycle and confirmations',()=>{
 const activity=fs.readFileSync(path.join(java,'ModeratorManagementActivity.java'),'utf8'),access=fs.readFileSync(path.join(java,'ModerationAccess.java'),'utf8');
 const settings=fs.readFileSync(path.join(java,'ProducerFeedActivity.java'),'utf8'),manifest=fs.readFileSync(path.join(root,'app/src/main/AndroidManifest.xml'),'utf8');
 assert.match(manifest,/<activity android:name="\.ModeratorManagementActivity" android:exported="false"/);
 assert.ok(settings.includes('new SettingsContentHost(this, this::settingsFingerprint, this::buildProfileSection)'));
 assert.ok(settings.includes('new SettingsView(this, avatar'));
 assert.ok(fs.readFileSync(path.join(java,'SettingsView.java'),'utf8').includes('ModerationAccess.settingsSection(activity, access)'));
 assert.match(access,/if \(!access.hasManagement\(\)\) return null/);
 const host=fs.readFileSync(path.join(java,'SettingsContentHost.java'),'utf8');
 assert.match(access,/superAdmin = ModeratorManagementState.superAllowed/);
 assert.ok(!access.includes('getIdToken'));
 assert.match(host,/requests.accept\(request, scope\(\)\)/);
 for(const source of [host,activity]){assert.ok(source.includes('user.getIdToken(true)'));assert.ok(!source.includes('setCustomUserClaims'));assert.ok(!source.includes('FirebaseFirestore'));}
 assert.match(host,/onWindowFocusChanged/);assert.match(host,/removeAuthStateListener/);assert.match(host,/requests.invalidate/);
 assert.match(activity,/if\(!ModeratorManagementState.superAllowed/);assert.match(activity,/generation==request/);assert.match(activity,/owner.equals\(u.getUid\(\)\)/);
 assert.match(activity,/ModerationBackendClient.getInstance\(\)/);
 for(const name of ['moderationLookupUser','moderationGrantModerator','moderationRevokeModerator','moderationListModerators'])assert.ok(activity.includes('"'+name+'"'));
 for(const text of ['Bu kullanıcıya moderatör yetkisi verilsin mi?','Bu kullanıcının moderatör yetkisi kaldırılsın mı?','Moderatör yetkisi verildi.','Moderatör yetkisi kaldırıldı.','Kullanıcı aranıyor…','Moderatörler yükleniyor…','İşlem tamamlanamadı. Tekrar deneyin.'])assert.ok(activity.includes(text));
 assert.match(activity,/if\(u.canChange\(\)\)/);assert.match(activity,/PERMISSION_DENIED/);assert.match(activity,/UNAUTHENTICATED/);
 assert.match(activity,/state.acceptChange\(u,grant,raw\);renderResult\(\);renderList\(\)/);
 assert.match(activity,/if\(!current\(request\)\)return/);assert.match(activity,/state.clear\(\);gate\(\);super.onStop\(\)/);
 assert.ok(!activity.includes('getException().getMessage'));assert.match(activity,/catch\(RuntimeException e\)/);
});
