'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const host=process.env.FIRESTORE_EMULATOR_HOST;
test('operator approval on real Firestore: source binding, atomic uniqueness, revoke, replay and league consumption',{skip:!host},async()=>{
 if(!/^(localhost|127\.0\.0\.1):\d+$/.test(host))throw new Error('Local emulator only');
 const {initializeApp,deleteApp}=require('firebase-admin/app'),{getFirestore,FieldPath,Timestamp}=require('firebase-admin/firestore');
 const {createApproval}=require('../league/approval'),P=require('../league/policy'),{createRuntime}=require('../league/runtime');
 const app=initializeApp({projectId:'demo-zeytin-approval'},'operator-approval-test'),db=getFirestore(app);
 const auth={uid:'operator-test',token:{league_operator:true,auth_time:1700000000}};
 const service=createApproval({db,getUser:async()=>({customClaims:{league_operator:true},disabled:false})});
 const run=data=>service.execute({auth,data});
 let serial=0;
 async function seed(uid){
  const g={owner_uid:uid,district:'Alaşehir',province:'Manisa'};
  const raw={owner_uid:uid,cloud_id:'e',garden_cloud_id:'g',season_year:2026,record_date:'2026-09-01',
   grades:[{grade:'DOUBLE',kg:1000,price_per_kg:100,line_total:100000}]};
  const sale={...raw,cloud_id:'s',sale_kind:'SALE',sale_date:'2026-09-01',market_consent:1};
  const base=`users/${uid}/gardens/g`,batch=db.batch();
  batch.set(db.doc(base),g);batch.set(db.doc(base+'/elek_records/e'),raw);batch.set(db.doc(base+'/sales/s'),sale);await batch.commit();
  return {action:'approve',uid,garden_id:'g',record_id:'e',sale_garden_id:'g',sale_id:'s',season:2026,
   request_id:'request'+(++serial),expected_revision:0,evidence_id:'ev1_'+P.hash('independent-emulator-only-'+uid),
   evidence_type:'buyer_confirmation',expires_at_ms:Date.now()+30*86400000,
   reviewed:{region_id:'manisa_alasehir',kg:1000,amount:100000,grades:[1000,0,0,0,0,0],values:[100000,0,0,0,0,0]}};
 }
 try{
  assert.ok((await fetch(`http://${host}/emulator/v1/projects/demo-zeytin-approval/databases/(default)/documents`,{method:'DELETE'})).ok);
  const c=await seed('owner0');
  await assert.rejects(service.execute({auth:{uid:'owner0',token:{}},data:{...c,isAdmin:true}}),e=>e.code==='permission-denied');
  await assert.rejects(service.execute({auth:{...auth,uid:'owner0'},data:c}),e=>e.code==='permission-denied');
  for(const patch of [{uid:'wrong-owner'},{sale_id:'wrong-sale'},{season:2025},{reviewed:{...c.reviewed,region_id:'manisa_akhisar'}},
   {reviewed:{...c.reviewed,kg:999}},{reviewed:{...c.reviewed,amount:99000}},
   {reviewed:{...c.reviewed,grades:[0,1000,0,0,0,0]}}])
    await assert.rejects(run({...c,...patch}),e=>e.code==='failed-precondition');
  const saleRef=db.doc('users/owner0/gardens/g/sales/s');
  const saved=(await saleRef.get()).data();
  for(const patch of [{owner_uid:'other'},{cloud_id:'wrong'},{season_year:2025},{market_consent:0},
   {grades:[{grade:'DOUBLE',kg:999,price_per_kg:100,line_total:99900}]}]){
   await saleRef.set({...saved,...patch});await assert.rejects(run(c),e=>e.code==='failed-precondition');
  }
  await saleRef.set(saved);
  const queue=[],runtime=createRuntime({db,FieldPath,Timestamp,enqueue:async t=>queue.push(t),identitySecret:()=> 'emulator-secret-at-least-32-characters'});
  async function calculate(){const job=await runtime.start(2026);let steps=0;while(queue.length){assert.ok(++steps<100);await runtime.work(queue.shift());}return `zeytin_leagues/2026/snapshots/${job}`;}
  const empty=await calculate();assert.equal((await db.collection(empty+'/members').get()).size,0); // consent alone is not proof
  const approved=await run(c);assert.equal(approved.status,'verified');
  assert.deepEqual(await run(c),approved);
  const proofRef=db.doc('league_sale_attestations/'+approved.attestation_id);
  assert.equal((await proofRef.collection('audit').get()).size,1);
  const proof=(await proofRef.get()).data();assert.equal(proof.verifier,'operator-test');assert.equal(proof.matched_kg,1000);
  const other=await seed('owner1');
  await assert.rejects(run({...other,evidence_id:c.evidence_id}),e=>e.code==='already-exists');
  // Two different operators' requests compete for one evidence: exactly one can commit.
  const a=await seed('raceA'),b=await seed('raceB');b.evidence_id=a.evidence_id;
  const race=await Promise.allSettled([run(a),run(b)]);assert.equal(race.filter(r=>r.status==='fulfilled').length,1);
  // Same sale assigned to a second Elek record is also rejected.
  const rawRef=db.doc('users/owner0/gardens/g/elek_records/e');const raw=(await rawRef.get()).data();
  await db.doc('users/owner0/gardens/g/elek_records/e2').set({...raw,cloud_id:'e2'});
  await assert.rejects(run({...c,record_id:'e2',request_id:'other-sale',evidence_id:'ev1_'+P.hash('other-sale')}),e=>e.code==='already-exists');
  await run(other);for(const uid of ['owner2','owner3'])await run(await seed(uid));
  const live=await calculate();assert.ok((await db.doc(live+'/members/owner0').get()).exists);
  // Raw edits invalidate the existing proof without altering local records or score policy.
  await saleRef.set({...saved,grades:[{grade:'DOUBLE',kg:1000,price_per_kg:101,line_total:101000}]});
  const mismatched=await calculate();assert.equal((await db.doc(mismatched+'/members/owner0').get()).exists,false);
  await saleRef.set(saved);
  const revoke={action:'revoke',uid:c.uid,garden_id:'g',record_id:'e',request_id:'revoke1',expected_revision:1,reason_code:'evidence_withdrawn'};
  assert.equal((await run(revoke)).status,'revoked');assert.deepEqual(await run(revoke),{attestation_id:approved.attestation_id,status:'revoked',revision:2});
  await assert.rejects(run({...c,request_id:'stale-review'}),e=>e.code==='failed-precondition');
  assert.equal((await proofRef.collection('audit').get()).size,2);
  const revoked=await calculate();assert.equal((await db.doc(revoked+'/members/owner0').get()).exists,false);
  // Revocation does not release evidence for another owner.
  await assert.rejects(run({...a,uid:'raceB',evidence_id:c.evidence_id,request_id:'reuse-revoked'}),e=>['already-exists','failed-precondition'].includes(e.code));
 }finally{await db.terminate();await deleteApp(app);}
});
