'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const host=process.env.FIRESTORE_EMULATOR_HOST;
test('moderation Admin SDK collection-group bounds, pagination and update on real emulator',{skip:!host},async()=>{
 if(!/^(localhost|127\.0\.0\.1):\d+$/.test(host))throw Error('Local emulator only');
 const {initializeApp,deleteApp}=require('firebase-admin/app');
 const {getFirestore,FieldPath,FieldValue,Timestamp}=require('firebase-admin/firestore');
 const {createService}=require('../moderation/service');
 const app=initializeApp({projectId:'demo-zeytin-moderation'},'moderation-test');const db=getFirestore(app);
 try{
  for(const collection of ['reports','users','user_blocks'])await db.recursiveDelete(db.collection(collection));
  const record={reporter_uid:'A',reported_uid:'B',target_type:'POST',target_id:'p',post_cloud_id:'p',comment_cloud_id:'',reason:'SPAM',status:'OPEN',created_at:Timestamp.now()};
  await db.doc('reports/A/items/POST|p|p').set(record);await db.doc('reports/A/items/POST|q|q').set({...record,target_id:'q',post_cloud_id:'q'});
  await db.doc('users/A/items/private').set({...record,private:'must not appear'});
  await db.doc('user_blocks/A/items/B').set({...record,blocker_uid:'A',blocked_uid:'B'});
  const service=createService({db,getUser:async()=>({customClaims:{moderation_admin:true}}),documentId:()=>FieldPath.documentId(),serverTimestamp:()=>FieldValue.serverTimestamp(),timestamp:(s,n)=>new Timestamp(s,n)});
  const auth={uid:'mod',token:{moderation_admin:true}};
  const first=await service.list({auth,data:{limit:1}});assert.equal(first.reports.length,1);assert.equal(first.reports[0].report_id,'POST|q|q');assert.ok(first.next_cursor);
  const second=await service.list({auth,data:{limit:1,cursor:first.next_cursor}});assert.equal(second.reports.length,1);assert.equal(second.reports[0].report_id,'POST|p|p');assert.equal(second.next_cursor,null);
  await service.update({auth,data:{reporter_uid:'A',report_id:'POST|p|p',status:'REVIEWED',expected_status:'OPEN'}});
  const after=(await db.doc('reports/A/items/POST|p|p').get()).data();assert.equal(after.reviewed_by,'mod');assert.ok(after.reviewed_at instanceof Timestamp);
  assert.equal((await service.list({auth,data:{}})).reports.length,1);
 }finally{await deleteApp(app);}
});
