'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {createRuntime}=require('../league/runtime'),P=require('../league/policy');
function store(){
 const data=new Map(),times=new Map();let clock=1700000000000;
 const snap=path=>({id:path.split('/').at(-1),ref:ref(path),exists:data.has(path),data:()=>data.get(path),get:k=>data.get(path)?.[k],updateTime:{toMillis:()=>times.get(path)||0}});
 function ref(path){return {path,id:path.split('/').at(-1),collection:n=>query(path+'/'+n),get:async()=>snap(path),
  set:async d=>{data.set(path,structuredClone(d));times.set(path,clock);},delete:async()=>{data.delete(path);times.delete(path);}};}
 function query(path,filters=[],after=null,limit=Infinity){return {path,doc:id=>ref(path+'/'+id),
  where:(k,op,v)=>{assert.equal(op,'==');return query(path,[...filters,[k,v]],after,limit);},
  orderBy:()=>query(path,filters,after,limit),startAfter:id=>query(path,filters,id,limit),limit:n=>query(path,filters,after,n),
  get:async()=>({docs:[...data.keys()].filter(p=>p.startsWith(path+'/')&&p.split('/').length===path.split('/').length+1)
   .map(snap).filter(d=>(!after||d.id>after)&&filters.every(([k,v])=>d.get(k)===v)).sort((a,b)=>a.id<b.id?-1:1).slice(0,limit)})};}
 const db={doc:ref,collection:query,getAll:async(...refs)=>Promise.all(refs.map(r=>r.get())),
  batch:()=>{const ops=[];return {set:(r,d)=>ops.push(()=>r.set(d)),commit:async()=>{for(const f of ops)await f();}};},
  runTransaction:async f=>{const ops=[];let writes=false;const tx={get:async r=>{assert.equal(writes,false,'Firestore reads must precede writes');return r.get();},
   set:(r,d)=>{writes=true;ops.push(()=>r.set(d));}};const out=await f(tx);for(const op of ops)await op();return out;}};
 return {data,db,setClock:n=>clock=n,clock:()=>clock};
}
function seed(s,uid,n=1,options={}){
 const g={owner_uid:uid,district:options.district||'Alaşehir',province:'Manisa'},year=options.year||2026;
 for(let i=1;i<=n;i++){
  const id='e'+i,garden='g'+i,sale='s'+i,kg=options.kg??1000;
  const raw={owner_uid:uid,cloud_id:id,garden_cloud_id:garden,season_year:year,record_date:`${year}-09-01`,
   grades:[{grade:'DOUBLE',kg,price_per_kg:100,line_total:kg*100}]};
  const sold={...raw,cloud_id:sale,sale_kind:'SALE',sale_date:`${year}-09-01`,market_consent:1};
  const proof={uid,garden_id:garden,record_id:id,sale_garden_id:garden,sale_id:sale,season:year,schema_version:1,status:'verified',
   evidence_id:options.evidence||`evidence-${uid}-${i}-external`,verifier:'controlled-test-only',verified_at_ms:s.clock()-1000,expires_at_ms:s.clock()+30*86400000,
   record_digest:P.recordDigest(raw),sale_digest:P.saleDigest(sold),region_digest:P.regionDigest(g)};
  const base=`users/${uid}/gardens/${garden}`;
  s.data.set(base,g);s.data.set(base+'/elek_records/'+id,raw);s.data.set(base+'/sales/'+sale,sold);
  s.data.set('league_sale_attestations/'+P.proofId(uid,garden,id),proof);
 }
}
function runner(s){const queue=[];const r=createRuntime({db:s.db,FieldPath:{documentId:()=> '__name__'},Timestamp:{fromMillis:n=>n},clock:s.clock,
 enqueue:async t=>queue.push(t),identitySecret:()=> 'secret'.repeat(8)});
 return {...r,queue,async drain(){let steps=0;while(queue.length){assert.ok(++steps<200);await r.work(queue.shift());}}};}
test('actual pipeline publishes 25 producers; three gardens remain one; own #21 exists',async()=>{
 const s=store();for(let i=0;i<25;i++)seed(s,'user'+i,i===0?3:1);const r=runner(s),job=await r.start(2026);await r.drain();
 assert.equal(s.data.get('zeytin_leagues/2026').current_snapshot,job);
 const root=`zeytin_leagues/2026/snapshots/${job}`;
 assert.equal(s.data.get(root).complete,true);
 const region=s.data.get(root+'/regions/manisa_alasehir');assert.equal(region.producer_count,25);assert.equal(region.top.length,20);assert.equal(region.kg,27000);
 const members=[...s.data.entries()].filter(([k])=>k.startsWith(root+'/members/'));
 assert.equal(members.length,25);assert.ok(members.some(([,d])=>d.rank===21));
 assert.equal(s.data.get(root+'/members/user0').kg,3000);
 assert.ok(!JSON.stringify(region).includes('user0'));
});
test('incomplete snapshot is hidden; checkpoint retry does not duplicate producer totals',async()=>{
 const s=store();for(let i=0;i<4;i++)seed(s,'user'+i);const r=runner(s);await r.start(2026);
 const scan=r.queue.shift();await r.work(scan);await r.work(scan);
 assert.equal(s.data.has('zeytin_leagues/2026'),false);await r.drain();
 const job=s.data.get('zeytin_leagues/2026').current_snapshot;
 assert.equal(s.data.get(`zeytin_leagues/2026/snapshots/${job}/regions/manisa_alasehir`).kg,4000);
});
test('duplicate external evidence invalidates both claimants',async()=>{
 const s=store();for(let i=0;i<4;i++)seed(s,'good'+i);
 seed(s,'bad1',1,{evidence:'one-physical-sale-shared'});seed(s,'bad2',1,{evidence:'one-physical-sale-shared'});
 const r=runner(s),job=await r.start(2026);await r.drain();const root=`zeytin_leagues/2026/snapshots/${job}`;
 assert.equal(s.data.get(root+'/regions/manisa_alasehir').producer_count,4);
 assert.ok(!s.data.has(root+'/members/bad1')&&!s.data.has(root+'/members/bad2'));
});
test('seasons and regions isolated; one producer region has no global rank',async()=>{
 const s=store();for(let i=0;i<4;i++)seed(s,'normal'+i);seed(s,'other-year',1,{year:2025});seed(s,'akhisar',1,{district:'Akhisar'});
 const r=runner(s),job=await r.start(2026);await r.drain();const root=`zeytin_leagues/2026/snapshots/${job}`;
 assert.equal(s.data.get(root+'/regions/manisa_akhisar').producer_count,0);
 assert.ok(!s.data.has(root+'/members/other-year')&&!s.data.has(root+'/members/akhisar'));
});
test('no attestations means empty honest snapshot; disabled config does no work',async()=>{
 const s=store(),r=runner(s);await r.schedule();assert.equal(r.queue.length,0);
 const job=await r.start(2026);await r.drain();assert.equal(s.data.get('zeytin_leagues/2026').current_snapshot,job);
 assert.ok(![...s.data.keys()].some(k=>k.includes('/members/')));
});
test('failed initial enqueue can resume same job on scheduler retry',async()=>{
 const s=store(),r=runner(s),a=await r.start(2026),b=await r.start(2026);assert.equal(a,b);await r.drain();
 assert.equal(s.data.get('zeytin_leagues/2026').current_snapshot,a);
});
test('superseded job cannot publish over a newer season job',async()=>{
 const s=store(),r=runner(s),old=await r.start(2026);s.setClock(s.clock()+5*3600000);
 const next=await r.start(2026);assert.notEqual(old,next);await r.drain();assert.equal(s.data.get('zeytin_leagues/2026').current_snapshot,next);
});

test('failed generation preserves prior active snapshot, then retry publishes without duplicates',async()=>{
 const s=store();for(let i=0;i<100;i++)seed(s,'member'+i);
 const r=runner(s),old=await r.start(2026);await r.drain();
 const next=await r.start(2026);assert.notEqual(old,next);
 while(r.queue[0]?.stage!=='regions')await r.work(r.queue.shift());
 const task=r.queue.shift(),batch=s.db.batch;
 let once=true;s.db.batch=()=>{const b=batch();return {...b,commit:async()=>{if(once){once=false;throw new Error('simulated write failure');}return b.commit();}};};
 await assert.rejects(r.work(task),/simulated/);
 assert.equal(s.data.get('zeytin_leagues/2026').current_snapshot,old);
 await r.work(task);await r.drain();assert.equal(s.data.get('zeytin_leagues/2026').current_snapshot,next);
 const base=`zeytin_leagues/2026/snapshots/${next}`;
 const members=[...s.data.entries()].filter(([k])=>k.startsWith(base+'/members/'));
 assert.equal(members.length,100);assert.ok(members.some(([,d])=>d.rank===37));
 assert.equal(s.data.get(base+'/regions/manisa_alasehir').top.length,20);
});
