'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const {fixture}=require('./role-fixture');
function load(disabled=false){const h=fixture();h.users.get('mod').disabled=disabled;h.users.get('mod').customClaims={league_operator:true,other:'keep',moderation_admin:true};
 const modules={'./project-guard':{assertProject:()=>{}},'firebase-admin/app':{initializeApp:()=>({}),deleteApp:async()=>{}},'firebase-admin/auth':{getAuth:()=>h.auth},
  'firebase-admin/firestore':{getFirestore:()=>h.db,FieldValue:{serverTimestamp:()=>({server:true})}},'../moderation/roles':require('../moderation/roles')};
 const context={module:{exports:{}},require:n=>modules[n],console:{log:()=>{}},process:{argv:[]}};
 vm.runInNewContext(fs.readFileSync(require.resolve('../operator/moderation-role'),'utf8'),context);
 return {...h,main:context.module.exports.main};
}
test('trusted role helper preserves unrelated claims on grant/revoke; import has no writes',async()=>{
 const h=load();assert.equal(h.writes.length,0);
 await h.main(['demo-zeytin','mod','grant','--apply']);assert.equal(h.writes[0].claims.moderation_admin,true);assert.equal(h.writes[0].claims.league_operator,true);
 await h.main(['demo-zeytin','mod','revoke','--apply']);assert.equal(h.writes[1].claims.moderation_admin,undefined);assert.equal(h.writes[1].claims.other,'keep');assert.deepEqual(h.revoked,['mod']);
});
test('role helper rejects disabled grant and missing explicit apply',async()=>{
 const h=load(true);await assert.rejects(h.main(['demo-zeytin','mod','grant','--apply']));assert.equal(h.writes.length,0);
 await assert.rejects(h.main(['demo-zeytin','mod','grant']));
});
test('bootstrap by UID/email and server-only legacy moderator index backfill',async()=>{
 for(const identity of ['mod','email:mod@example.com']){
  const h=load();await h.main(['demo-zeytin',identity,'bootstrap-super-admin','--apply']);assert.equal(h.users.get('mod').customClaims.super_admin,true);assert.equal(h.users.get('mod').customClaims.other,'keep');
  await assert.rejects(h.main(['demo-zeytin','mod','revoke','--apply']));
 }
 const h=load();await h.main(['demo-zeytin','ALL','sync-index','--apply']);assert.equal(h.writes.length,0);
 assert.deepEqual((await h.service.list(h.request())).moderators.map(u=>u.uid).sort(),['mod','super']);
});
