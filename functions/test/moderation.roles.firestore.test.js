'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const host=process.env.FIRESTORE_EMULATOR_HOST,authHost=process.env.FIREBASE_AUTH_EMULATOR_HOST;
test('06.3C real Auth/Firestore: bootstrap, live roles, audit, index, concurrency and revoked sessions',{skip:!host||!authHost},async()=>{
 if(![host,authHost].every(h=>/^(localhost|127\.0\.0\.1):\d+$/.test(h)))throw Error('Local emulators only');
 const {initializeApp,deleteApp}=require('firebase-admin/app'),{getAuth}=require('firebase-admin/auth');
 const {getFirestore,FieldValue,FieldPath}=require('firebase-admin/firestore');
 const {createRoleService,changeRole,key}=require('../moderation/roles');const {authorize}=require('../moderation/service');
 const app=initializeApp({projectId:'demo-zeytin-role-backend'},'roles-integration'),auth=getAuth(app),db=getFirestore(app);
 try{
  for(const c of ['moderation_role_audit','moderation_role_members','moderation_role_locks'])await db.recursiveDelete(db.collection(c));
  for(const id of ['super','mod','normal','target']){try{await auth.deleteUser(id);}catch(e){if(e.code!=='auth/user-not-found')throw e;}await auth.createUser({uid:id,email:id+'@example.com'});}
  const deps={db,auth,documentId:()=>FieldPath.documentId(),serverTimestamp:()=>FieldValue.serverTimestamp()},service=createRoleService(deps);
  await auth.setCustomUserClaims('target',{other:'keep',league_operator:true});await auth.setCustomUserClaims('mod',{moderation_admin:true});
  await changeRole(deps,'super','BOOTSTRAP_SUPER_ADMIN','TRUSTED_OPERATOR',true);
  const req=(data={},id='super')=>({auth:{uid:id,token:{super_admin:id==='super',moderation_admin:id!=='normal',auth_time:Math.floor(Date.now()/1000)+10}},data});
  for(const id of ['normal','mod'])for(const fn of ['lookup','grant','revoke','list'])await assert.rejects(service[fn](req({},id)),{code:'permission-denied'});
  assert.equal((await service.lookup(req({email:'target@example.com'}))).user.uid,'target');
  await service.grant(req({target_uid:'target'}));assert.equal((await auth.getUser('target')).customClaims.other,'keep');
  const stale={uid:'target',token:{moderation_admin:true,auth_time:Math.floor(Date.now()/1000)}};assert.equal(await authorize(stale,id=>auth.getUser(id)),'target');
  assert.equal((await service.list(req())).moderators.length,2);
  await service.revoke(req({target_uid:'target'}));assert.deepEqual((await auth.getUser('target')).customClaims,{other:'keep',league_operator:true});
  await assert.rejects(authorize(stale,id=>auth.getUser(id)),{code:'permission-denied'});
  for(const fn of ['grant','revoke'])await assert.rejects(service[fn](req({target_uid:'super'})),{code:'failed-precondition'});
  const rows=(await db.collection('moderation_role_audit').get()).docs.map(d=>d.data());
  for(const action of ['GRANT','REVOKE'])assert.ok(rows.some(r=>r.action===action&&r.actor_uid==='super'&&r.target_uid==='target'&&r.status==='COMPLETED'&&r.timestamp.toMillis()>0));
  // Hold one real transaction lock while another caller attempts the same UID.
  let release,entered;const gate=new Promise(r=>release=r),ready=new Promise(r=>entered=r);
  const pending=changeRole(deps,'target','GRANT','super',false,async()=>{entered();await gate;});await ready;
  await assert.rejects(service.revoke(req({target_uid:'target'})),{code:'failed-precondition'});release();await pending;
  assert.equal((await db.collection('moderation_role_locks').doc(key('target')).get()).exists,false);
  await auth.updateUser('super',{disabled:true});await assert.rejects(service.list(req()),{code:'permission-denied'});await auth.updateUser('super',{disabled:false});
  await auth.revokeRefreshTokens('super');const revoked=req();revoked.auth.token.auth_time=1;await assert.rejects(service.list(revoked),{code:'permission-denied'});
  await auth.setCustomUserClaims('super',{moderation_admin:true});await assert.rejects(service.list(req()),{code:'permission-denied'});
 }finally{await deleteApp(app);}
});
test('06.3C rules deny direct role index, locks and audit access to every client role',{skip:!host},async()=>{
 if(!/^(localhost|127\.0\.0\.1):\d+$/.test(host))throw Error('Local emulator only');
 const req=require('node:module').createRequire(path.join(__dirname,'rules/package.json'));
 const {initializeTestEnvironment,assertFails}=req('@firebase/rules-unit-testing');const {doc,setDoc,getDoc,getDocs,collection,deleteDoc}=req('firebase/firestore');
 const [hostname,port]=host.split(':');const env=await initializeTestEnvironment({projectId:'demo-zeytin-role-rules',firestore:{host:hostname,port:+port,rules:fs.readFileSync(path.resolve(__dirname,'../../firestore.rules'),'utf8')}});
 try{
  for(const claims of [null,{}, {moderation_admin:true},{moderation_admin:true,super_admin:true}]){
   const db=claims?env.authenticatedContext('caller',claims).firestore():env.unauthenticatedContext().firestore();
   for(const c of ['moderation_role_audit','moderation_role_members','moderation_role_locks']){
    await assertFails(setDoc(doc(db,c,'target'),{uid:'caller',super_admin:true}));await assertFails(getDoc(doc(db,c,'target')));await assertFails(getDocs(collection(db,c)));await assertFails(deleteDoc(doc(db,c,'target')));
   }
  }
 }finally{await env.cleanup();}
});
