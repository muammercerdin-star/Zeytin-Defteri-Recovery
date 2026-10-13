'use strict';
const policy=require('./policy');
const crypto=require('node:crypto');
const PAGE=100, WRITE_BATCH=200;
// Kept dependency-injected so the full pipeline can be exercised without live Firebase.
function createRuntime({db,FieldPath,Timestamp,enqueue,identitySecret,clock=()=>Date.now()}) {
  const idField=()=>FieldPath.documentId();
  const jobRef=id=>db.collection('league_backend_jobs').doc(id);
  const pointer=year=>db.collection('zeytin_leagues').doc(String(year));
  async function page(collection,after,limit=PAGE){let q=collection.orderBy(idField()).limit(limit);if(after)q=q.startAfter(after);return (await q.get()).docs;}
  async function all(collection,limit=500000){const out=[];let after=null;for(;;){const docs=await page(collection,after,500);out.push(...docs);if(out.length>limit)throw new Error('Partition limit exceeded; no publication');if(docs.length<500)return out;after=docs.at(-1).id;}}
  async function writeMany(operations){for(let i=0;i<operations.length;i+=WRITE_BATCH){const batch=db.batch();for(const [ref,data] of operations.slice(i,i+WRITE_BATCH))batch.set(ref,data);await batch.commit();}}
  async function start(year) {
    if(!Number.isInteger(year)||year<2000||year>2100)throw new Error('Invalid season');
    const now=clock(), id=`${year}_${crypto.randomUUID().replaceAll('-','')}`;
    const lock=db.collection('league_backend_locks').doc(String(year));
    const created=await db.runTransaction(async tx=>{
      const existing=await tx.get(lock);
      if(existing.exists&&existing.get('state')==='running'&&existing.get('started_at_ms')>now-4*3600000)return existing.get('job');
      tx.set(lock,{job:id,state:'running',started_at_ms:now});
      tx.set(jobRef(id),{year,state:'running',started_at_ms:now});return id;
    });
    if(created)await enqueue({job:created,stage:'scan',after:null});
    return created;
  }
  async function schedule() {
    const config=await db.doc('league_backend_config/main').get();
    if(!config.exists||config.get('enabled')!==true)return;
    const years=config.get('seasons');
    if(!Array.isArray(years)||years.length>10||years.some(y=>!Number.isInteger(y)||y<2000||y>2100))throw new Error('Invalid backend configuration');
    for(const y of new Set(years))await start(y);
  }
  async function safeReadProof(proofDoc,year,started) {
    const proof=proofDoc.data();
    if(proof.season!==year||proof.status!=='verified')return null;
    for(const key of ['uid','garden_id','record_id','sale_garden_id','sale_id'])
      if(typeof proof[key]!=='string'||!proof[key]||proof[key].includes('/')||proof[key].length>128)return null;
    const base=`users/${proof.uid}/gardens/`;
    const refs=[db.doc(`${base}${proof.garden_id}/elek_records/${proof.record_id}`),db.doc(`${base}${proof.garden_id}`),
      db.doc(`${base}${proof.sale_garden_id}/sales/${proof.sale_id}`),db.doc(`${base}${proof.sale_garden_id}`)];
    const docs=await db.getAll(...refs);
    // Snapshot window is immutable. Changed or newly granted evidence waits for next run.
    if([proofDoc,...docs].some(d=>d.updateTime && d.updateTime.toMillis()>started))return null;
    if(proof.verified_at_ms>started || proof.expires_at_ms<started+86400000)return null;
    return policy.validateProof(proof,proofDoc.id,...docs.map(d=>d.exists?d.data():null),clock());
  }
  async function scan(ref,job,after) {
    const docs=await page(db.collection('league_sale_attestations').where('season','==',job.year),after);
    for(const doc of docs){
      const row=await safeReadProof(doc,job.year,job.started_at_ms);
      const rowRef=ref.collection('rows').doc(doc.id);
      if(!row){await rowRef.delete();continue;}
      // Claims prevent reusing a sale/evidence across distinct Elek records or owners.
      await db.runTransaction(async tx=>{
        const claims=['sale_'+row.sale_key,'evidence_'+row.evidence_key].map(key=>ref.collection('claims').doc(key));
        const prior=await Promise.all(claims.map(c=>tx.get(c)));
        const conflicts=prior.filter(p=>p.exists&&p.get('row')!==doc.id);
        const priorRows=await Promise.all(conflicts.map(c=>tx.get(ref.collection('rows').doc(c.get('row')))));
        row.invalid=conflicts.length>0;
        const current=await tx.get(rowRef);
        for(const old of priorRows)if(old.exists)tx.set(old.ref,{...old.data(),invalid:true});
        for(let i=0;i<claims.length;i++)if(!prior[i].exists)tx.set(claims[i],{row:doc.id});
        // Replay must not clear a conflict flag written by a later proof in this chunk.
        if(current.exists&&current.get('invalid'))row.invalid=true;
        tx.set(rowRef,row);tx.set(ref.collection('owners').doc(row.uid),{uid:row.uid,pending:true});
      });
    }
    return docs.length===PAGE?{stage:'scan',after:docs.at(-1).id}:{stage:'owners',after:null};
  }
  async function owners(ref,job,after){
    const docs=await page(ref.collection('owners'),after);
    for(const doc of docs){
      const rows=await all(ref.collection('rows').where('uid','==',doc.id),10000);
      const p=policy.aggregate(rows.map(d=>d.data()),doc.id,job.year);
      if(!p){await doc.ref.set({uid:doc.id,eligible:false,pending:false});continue;}
      p.producer_id=policy.publicId(p.uid,identitySecret());
      await doc.ref.set({...p,eligible:policy.score(p).eligible,pending:false});
      await ref.collection('regions').doc(p.region_id).set({name:p.region_name,done:false});
    }
    return docs.length===PAGE?{stage:'owners',after:docs.at(-1).id}:{stage:'regions',after:null};
  }
  async function regions(ref,job,after){
    // One geographic partition per task; no whole-country arrays on a phone or function.
    const docs=await page(ref.collection('regions'),after,1);
    if(!docs.length)return {stage:'finish',after:null};
    const region=docs[0], people=[];
    let cursor=null;
    for(;;){
      const chunk=await page(ref.collection('owners').where('region_id','==',region.id),cursor,500);
      for(const d of chunk)people.push(d.data());
      if(people.length>500000)throw new Error('Region capacity exceeded; no publication');
      if(chunk.length<500)break;cursor=chunk.at(-1).id;
    }
    const ranked=policy.rankRegion(people);
    const snapshot=pointer(job.year).collection('snapshots').doc(ref.id);
    // Await bounded batches. Any partial write remains hidden until the manifest commit.
    for(let i=0;i<ranked.length;i+=WRITE_BATCH){
      await writeMany(ranked.slice(i,i+WRITE_BATCH).map(p=>[snapshot.collection('members').doc(p.uid),policy.publicRow(p)]));
    }
    const regionSummary=policy.summary(ranked,region.id,region.get('name'));
    regionSummary.season=job.year;regionSummary.updated_at=Timestamp.fromMillis(clock());
    await snapshot.collection('regions').doc(region.id).set(regionSummary);
    await region.ref.set({name:region.get('name'),done:true});
    return {stage:'regions',after:region.id};
  }
  async function finish(ref,job){
    const regions=await all(ref.collection('regions'),10000);
    if(regions.some(d=>d.get('done')!==true))throw new Error('Incomplete regions');
    const lock=db.collection('league_backend_locks').doc(String(job.year));
    const root=pointer(job.year), snapshot=root.collection('snapshots').doc(ref.id);
    await db.runTransaction(async tx=>{
      const current=await tx.get(lock), state=await tx.get(ref);
      if(current.get('job')!==ref.id||state.get('state')==='published')return;
      if(clock()-job.started_at_ms>4*3600000)throw new Error('Snapshot job expired');
      const now=Timestamp.fromMillis(clock());
      tx.set(snapshot,{complete:true,policy_version:1,published_at:now,source_cutoff:Timestamp.fromMillis(job.started_at_ms)});
      tx.set(root,{current_snapshot:ref.id,updated_at:now});
      tx.set(ref,{...job,state:'published',published_at:now});
      tx.set(lock,{job:ref.id,state:'published',started_at_ms:job.started_at_ms});
    });return null;
  }
  async function work(task){
    if(!task||typeof task.job!=='string'||!/^\d{4}_[a-f0-9]{32}$/.test(task.job)
      || !['scan','owners','regions','finish'].includes(task.stage)
      || (task.after!==null&&(typeof task.after!=='string'||task.after.includes('/'))))throw new Error('Invalid task');
    const ref=jobRef(task.job), snap=await ref.get();if(!snap.exists)return;
    const job=snap.data();if(job.state!=='running')return;
    const lock=await db.collection('league_backend_locks').doc(String(job.year)).get();
    if(lock.get('job')!==task.job || clock()-job.started_at_ms>4*3600000)return;
    const checkpoint=ref.collection('steps').doc(policy.hash(policy.canonical([task.stage,task.after])));
    const done=await checkpoint.get();
    let next;
    if(done.exists)next=done.get('next');
    else {
      const action={scan,owners,regions,finish}[task.stage];
      next=await action(ref,job,task.after);
      await checkpoint.set({next:next||null});
    }
    // Checkpoint then enqueue: a failed enqueue retries without reapplying the chunk.
    if(next)await enqueue({job:task.job,...next});
  }
  return {schedule,start,work};
}
module.exports={createRuntime};
