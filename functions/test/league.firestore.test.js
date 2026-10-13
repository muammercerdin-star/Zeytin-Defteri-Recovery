'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const host=process.env.FIRESTORE_EMULATOR_HOST;
test('real Firestore SDK pipeline publishes complete generation and private #21',{skip:!host},async()=>{
 if(!/^(localhost|127\.0\.0\.1):\d+$/.test(host))throw new Error('Local emulator only');
 const {initializeApp,deleteApp}=require('firebase-admin/app');
 const {getFirestore,FieldPath,Timestamp}=require('firebase-admin/firestore');
 const {createRuntime}=require('../league/runtime'),P=require('../league/policy');
 const app=initializeApp({projectId:'demo-zeytin-pipeline'},'league-pipeline-test'),db=getFirestore(app);
 try{
  // Isolated demo project, never the application's Firebase project.
  const response=await fetch(`http://${host}/emulator/v1/projects/demo-zeytin-pipeline/databases/(default)/documents`,{method:'DELETE'});
  assert.equal(response.ok,true);
  const batch=db.batch(),now=Date.now();
  for(let i=0;i<25;i++)for(let j=0;j<(i===0?3:1);j++){
   const uid='test'+i,gid='garden'+j,g={owner_uid:uid,district:'Alaşehir',province:'Manisa'};
   const raw={owner_uid:uid,cloud_id:'e',garden_cloud_id:gid,season_year:2026,record_date:'2026-09-01',grades:[{grade:'DOUBLE',kg:1000,price_per_kg:100,line_total:100000}]};
   const sale={...raw,cloud_id:'s',sale_kind:'SALE',sale_date:'2026-09-01',market_consent:1};
   const base=`users/${uid}/gardens/${gid}`;
   batch.set(db.doc(base),g);batch.set(db.doc(base+'/elek_records/e'),raw);batch.set(db.doc(base+'/sales/s'),sale);
   batch.set(db.doc('league_sale_attestations/'+P.proofId(uid,gid,'e')),{schema_version:1,status:'verified',uid,garden_id:gid,record_id:'e',sale_garden_id:gid,sale_id:'s',season:2026,
    evidence_id:`emulator-only-evidence-${uid}-${gid}`,verifier:'emulator-only',verified_at_ms:now-1000,expires_at_ms:now+30*86400000,
    record_digest:P.recordDigest(raw),sale_digest:P.saleDigest(sale),region_digest:P.regionDigest(g)});
  }
  await batch.commit();
  const queue=[],r=createRuntime({db,FieldPath,Timestamp,enqueue:async t=>queue.push(t),identitySecret:()=> 'emulator-only-secret-value-32-chars'});
  const job=await r.start(2026),first=queue.shift();await r.work(first);
  assert.equal((await db.doc('zeytin_leagues/2026').get()).exists,false);
  await r.work(first); // checkpoint replay with real SDK
  let steps=0;while(queue.length){assert.ok(++steps<100);await r.work(queue.shift());}
  const base=`zeytin_leagues/2026/snapshots/${job}`;
  assert.equal((await db.doc('zeytin_leagues/2026').get()).get('current_snapshot'),job);
  const meta=await db.doc(base).get();assert.equal(meta.get('complete'),true);assert.ok(meta.get('published_at') instanceof Timestamp);
  const region=(await db.doc(base+'/regions/manisa_alasehir').get()).data();
  assert.equal(region.top.length,20);assert.equal(region.producer_count,25);assert.equal(region.kg,27000);
  const members=await db.collection(base+'/members').get();assert.equal(members.size,25);assert.ok(members.docs.some(d=>d.get('rank')===21));
  assert.equal((await db.doc(base+'/members/test0').get()).get('kg'),3000);
 }finally{await db.terminate();await deleteApp(app);}
});
