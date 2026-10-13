'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const {fixture}=require('./role-fixture');
const {authorize}=require('../moderation/service');
const {changeRole,key}=require('../moderation/roles');
const code=c=>e=>e.code===c;
for(const method of ['lookup','grant','revoke','list']){
 for(const who of ['normal','mod'])test(`${who} cannot ${method}`,async()=>{
  const h=fixture();await assert.rejects(h.service[method](h.request({target_uid:'target'},who)),code('permission-denied'));assert.equal(h.rows.size,0);assert.equal(h.writes.length,0);
 });
 test(`guest, stale, disabled and revoked sessions cannot ${method}`,async()=>{
  const h=fixture();await assert.rejects(h.service[method]({data:{}}),code('unauthenticated'));
  for(const extra of [{disabled:true},{customClaims:{moderation_admin:true}},{tokensValidAfterTime:'2040-01-01T00:00:00Z'}]){
   h.seed('super',{super_admin:true,moderation_admin:true},extra);
   await assert.rejects(h.service[method](h.request({},'super',{super_admin:true,auth_time:2000000000})),code('permission-denied'));
  }
  h.seed('super',{super_admin:true});await assert.rejects(h.service[method](h.request({},'super',{super_admin:'true'})),code('permission-denied'));
  h.seed('super',{super_admin:true},{tokensValidAfterTime:'2026-01-01T00:00:00Z'});
  await assert.rejects(h.service[method](h.request({},'super',{super_admin:true})),code('permission-denied'));
  h.users.delete('super');await assert.rejects(h.service[method](h.request({},'super',{super_admin:true})),code('permission-denied'));
  assert.equal(h.writes.length,0);
 });
}
test('super lookup exact UID and email exposes only allowed fields',async()=>{
 const h=fixture();h.users.get('target').passwordHash='secret';
 for(const d of [{uid:'target'},{email:'target@example.com'}]){
  const {user}=await h.service.lookup(h.request(d));assert.equal(user.uid,'target');assert.equal(user.email,'target@example.com');
  assert.deepEqual(Object.keys(user).sort(),['disabled','display_name','email','moderation_admin','super_admin','uid']);
 }
 for(const d of [{},{name:'target'},{uid:'target',email:'target@example.com'},{email:'target'},{uid:''},{uid:'target',super_admin:true}])await assert.rejects(h.service.lookup(h.request(d)),code('invalid-argument'));
 await assert.rejects(h.service.lookup(h.request({uid:'missing'})),code('not-found'));
 await assert.rejects(h.service.lookup(h.request({email:'missing@example.com'})),code('not-found'));
});
test('grant/revoke preserve claims, audit identity, index and immediate stale moderator denial',async()=>{
 const h=fixture();await h.service.grant(h.request({target_uid:'target'}));
 assert.deepEqual(h.users.get('target').customClaims,{league_operator:true,other:'keep',moderation_admin:true});
 assert.equal((await h.service.list(h.request())).moderators[0].uid,'target');
 const stale={uid:'target',token:{moderation_admin:true,auth_time:2000000000}};
 assert.equal(await authorize(stale,h.auth.getUser),'target');
 await h.service.revoke(h.request({target_uid:'target'}));
 assert.deepEqual(h.users.get('target').customClaims,{league_operator:true,other:'keep'});
 await assert.rejects(authorize(stale,h.auth.getUser),code('permission-denied'));
 assert.deepEqual(h.revoked,['target']);assert.equal((await h.service.list(h.request())).moderators.length,0);
 const audit=[...h.rows.entries()].filter(([p])=>p.startsWith('moderation_role_audit/')).map(([,d])=>d);
 assert.deepEqual(audit.map(a=>a.action),['GRANT','REVOKE']);
 for(const a of audit){assert.equal(a.actor_uid,'super');assert.equal(a.target_uid,'target');assert.equal(a.status,'COMPLETED');assert.deepEqual(a.timestamp,{server:true});}
});
test('super targets including self protected; injected claims always rejected',async()=>{
 const h=fixture();for(const fn of ['grant','revoke']){
  await assert.rejects(h.service[fn](h.request({target_uid:'super'})),code('failed-precondition'));
  for(const extra of [{super_admin:true},{claims:{super_admin:true}},{actor_uid:'super'},{moderation_admin:true}])await assert.rejects(h.service[fn](h.request({target_uid:'target',...extra})),code('invalid-argument'));
 }
 assert.equal(h.writes.length,0);assert.equal(h.users.get('super').customClaims.super_admin,true);
});
test('disabled target grant and missing UID rejected; disabled target revoke allowed',async()=>{
 const h=fixture();h.users.get('target').disabled=true;
 await assert.rejects(h.service.grant(h.request({target_uid:'target'})),code('failed-precondition'));
 await h.service.revoke(h.request({target_uid:'target'}));
 await assert.rejects(h.service.grant(h.request({target_uid:'missing'})),code('not-found'));
});
test('bounded moderator pagination rechecks claims, skips removed/deleted users, accepts arbitrary Firebase UID safely',async()=>{
 const h=fixture();for(const id of ['A','B','C','special/uid']){h.seed(id);await h.service.grant(h.request({target_uid:id}));}
 let cursor,ids=[];do{const page=await h.service.list(h.request({limit:1,...(cursor?{cursor}:{})}));ids.push(...page.moderators.map(u=>u.uid));cursor=page.next_cursor;}while(cursor);
 assert.deepEqual(ids.sort(),['A','B','C','special/uid'].sort());
 h.users.delete('A');h.users.get('B').customClaims={};
 const page=await h.service.list(h.request());assert.deepEqual(page.moderators.map(u=>u.uid).sort(),['C','special/uid'].sort());
 for(const d of [{limit:0},{limit:101},{limit:1.5},{cursor:'bad'},{name:'all'}])await assert.rejects(h.service.list(h.request(d)),code('invalid-argument'));
});
test('pending lock rejects concurrent writes; uncertain failure retains durable audit and lock',async()=>{
 const h=fixture();h.rows.set('moderation_role_locks/'+key('target'),{audit_id:'old'});
 await assert.rejects(h.service.grant(h.request({target_uid:'target'})),code('failed-precondition'));assert.equal(h.writes.length,0);
 h.rows.clear();const original=h.auth.setCustomUserClaims;h.auth.setCustomUserClaims=async(...args)=>{await original(...args);h.failCommit();};
 await assert.rejects(h.service.grant(h.request({target_uid:'target'})));
 assert.ok(h.rows.has('moderation_role_locks/'+key('target')));
 assert.equal([...h.rows.values()].find(v=>v.action==='GRANT').status,'PENDING');
});
test('failed initial audit prevents Auth mutation; actor rechecked just before write',async()=>{
 const h=fixture();h.failCommit();await assert.rejects(h.service.grant(h.request({target_uid:'target'})));assert.equal(h.writes.length,0);
 const f=fixture();let reads=0;const get=f.auth.getUser;f.auth.getUser=async id=>{if(id==='super'&&++reads===2)return {uid:id,customClaims:{}};return get(id);};
 await assert.rejects(f.service.grant(f.request({target_uid:'target'})),code('permission-denied'));assert.equal(f.writes.length,0);
});
test('trusted bootstrap alone sets both claims and preserves unrelated claims',async()=>{
 const h=fixture();await changeRole(h.deps,'target','BOOTSTRAP_SUPER_ADMIN','TRUSTED_OPERATOR',true);
 assert.deepEqual(h.users.get('target').customClaims,{league_operator:true,other:'keep',super_admin:true,moderation_admin:true});
});
test('all four exported callable wrappers authorize and cannot bootstrap',async()=>{
 const h=fixture(),context={exports:{},require:name=>({
  'firebase-functions/v2/https':{onCall:(_,fn)=>fn,HttpsError:class extends Error{constructor(code,m){super(m);this.code=code;}}},
  'firebase-admin/firestore':{getFirestore:()=>h.db,FieldPath:{documentId:()=>'__name__'},FieldValue:{serverTimestamp:()=>({server:true})}},
  'firebase-admin/auth':{getAuth:()=>h.auth},'./roles':require('../moderation/roles')
 }[name])};
 vm.runInNewContext(fs.readFileSync(require.resolve('../moderation/role-functions'),'utf8'),context);
 assert.deepEqual(Object.keys(context.exports).sort(),['moderationGrantModerator','moderationListModerators','moderationLookupUser','moderationRevokeModerator']);
 for(const fn of Object.values(context.exports))await assert.rejects(fn(h.request({},'normal')),code('permission-denied'));
 await context.exports.moderationGrantModerator(h.request({target_uid:'target'}));assert.equal(h.users.get('target').customClaims.super_admin,undefined);
 await context.exports.moderationRevokeModerator(h.request({target_uid:'target'}));assert.equal(h.users.get('target').customClaims.super_admin,undefined);
});
