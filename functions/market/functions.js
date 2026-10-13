'use strict';

const {onDocumentWritten}=require('firebase-functions/v2/firestore');
const {onSchedule}=require('firebase-functions/v2/scheduler');
const {getFirestore,FieldPath,Timestamp}=require('firebase-admin/firestore');
const P=require('./policy');

const region='europe-west1';
const db=()=>getFirestore();
const SOURCE='market_private_sources';
const DIRTY='market_dirty_regions';
const BACKFILL='market_backfills';
const CATALOG='market_region_catalog';
const PUBLIC_STATS='zeytin_market_stats';
const PAGE=200;

function consentRef(uid){return db().doc(`users/${uid}/market_settings/anonymous`);}
function sourceRef(uid,gardenId,recordId){return db().collection(SOURCE).doc(P.sourceId(uid,gardenId,recordId));}
function dirtyId(season,regionId){return P.hash(`${season}|${regionId}`);}
function dirtyRef(season,regionId){return db().collection(DIRTY).doc(dirtyId(season,regionId));}
function backfillRef(uid){return db().collection(BACKFILL).doc(uid);}

async function consentEnabled(uid){
  const s=await consentRef(uid).get();
  return s.exists&&s.get('enabled')===true&&s.get('policy_version')===1;
}

async function markDirty(source){
  if(!source||!Number.isInteger(source.season)||!source.region_id)return;
  const now=Date.now();
  const data={season:source.season,region_id:source.region_id,region_name:source.region_name,
    district:source.district,province:source.province,dirty_at_ms:now};
  await Promise.all([
    dirtyRef(source.season,source.region_id).set(data,{merge:true}),
    db().collection(CATALOG).doc(dirtyId(source.season,source.region_id)).set({
      season:source.season,region_id:source.region_id,region_name:source.region_name,
      district:source.district,province:source.province,updated_at_ms:now
    },{merge:true})
  ]);
}

async function applySource(uid,gardenId,recordId,raw,garden,enabled){
  const ref=sourceRef(uid,gardenId,recordId), oldSnap=await ref.get(), old=oldSnap.exists?oldSnap.data():null;
  const next=enabled?P.normalizeSource(uid,gardenId,recordId,raw,garden):null;
  if(!next){
    if(old){await ref.delete();await markDirty(old);}
    return;
  }
  if(old&&old.source_digest===next.source_digest&&old.region_id===next.region_id&&old.season===next.season)return;
  await ref.set(next);
  if(old&&(old.region_id!==next.region_id||old.season!==next.season))await markDirty(old);
  await markDirty(next);
}

exports.zeytinMarketElekWritten=onDocumentWritten({
  document:'users/{uid}/gardens/{gardenId}/elek_records/{recordId}',region,
  timeoutSeconds:120,memory:'256MiB',maxInstances:20,concurrency:20,retry:true
},async event=>{
  const {uid,gardenId,recordId}=event.params;
  const after=event.data&&event.data.after&&event.data.after.exists?event.data.after.data():null;
  if(!after){await applySource(uid,gardenId,recordId,null,null,false);return;}
  const [enabled,gardenSnap]=await Promise.all([consentEnabled(uid),db().doc(`users/${uid}/gardens/${gardenId}`).get()]);
  await applySource(uid,gardenId,recordId,after,gardenSnap.exists?gardenSnap.data():null,enabled);
});

exports.zeytinMarketConsentWritten=onDocumentWritten({
  document:'users/{uid}/market_settings/anonymous',region,
  timeoutSeconds:60,memory:'256MiB',maxInstances:5,concurrency:5,retry:true
},async event=>{
  const uid=event.params.uid;
  const after=event.data&&event.data.after&&event.data.after.exists?event.data.after.data():null;
  await backfillRef(uid).set({uid,cursor_path:'',requested_at_ms:Date.now(),enabled:!!(after&&after.enabled===true&&after.policy_version===1)});
});

exports.zeytinMarketGardenWritten=onDocumentWritten({
  document:'users/{uid}/gardens/{gardenId}',region,
  timeoutSeconds:60,memory:'256MiB',maxInstances:5,concurrency:5,retry:true
},async event=>{
  const before=event.data&&event.data.before&&event.data.before.exists?event.data.before.data():null;
  const after=event.data&&event.data.after&&event.data.after.exists?event.data.after.data():null;
  const b=before?`${before.district||''}|${before.province||''}`:'';
  const a=after?`${after.district||''}|${after.province||''}`:'';
  if(b===a)return;
  const uid=event.params.uid;
  await backfillRef(uid).set({uid,cursor_path:'',requested_at_ms:Date.now(),enabled:await consentEnabled(uid)});
});

async function purgeUserSources(uid,marker){
  const page=await db().collection(SOURCE).where('uid','==',uid).limit(PAGE).get();
  for(const d of page.docs){await markDirty(d.data());await d.ref.delete();}
  if(page.size<PAGE)await marker.ref.delete();
  else await marker.ref.set({cursor_path:'',requested_at_ms:Date.now(),enabled:false},{merge:true});
}

async function backfillUser(marker){
  const data=marker.data(),uid=data.uid||marker.id;
  const enabled=await consentEnabled(uid);
  if(!enabled){await purgeUserSources(uid,marker);return;}
  let q=db().collectionGroup('elek_records').where('owner_uid','==',uid).orderBy(FieldPath.documentId()).limit(PAGE);
  const cursor=typeof data.cursor_path==='string'?data.cursor_path:'';
  if(cursor)q=q.startAfter(cursor);
  const page=await q.get();
  const gardenRefs=[],unique=new Map();
  for(const doc of page.docs){
    const garden=doc.ref.parent.parent;
    if(garden&&!unique.has(garden.path)){unique.set(garden.path,garden);gardenRefs.push(garden);}
  }
  const gardenDocs=gardenRefs.length?await db().getAll(...gardenRefs):[];
  const gardens=new Map(gardenDocs.map(d=>[d.ref.path,d]));
  for(let i=0;i<page.docs.length;i+=20){
    await Promise.all(page.docs.slice(i,i+20).map(async doc=>{
      const gardenRef=doc.ref.parent.parent;if(!gardenRef)return;
      const g=gardens.get(gardenRef.path);
      await applySource(uid,gardenRef.id,doc.id,doc.data(),g&&g.exists?g.data():null,true);
    }));
  }
  const latest=await marker.ref.get();
  if(!latest.exists)return;
  if(latest.get('requested_at_ms')!==data.requested_at_ms)return;
  if(page.size<PAGE)await marker.ref.delete();
  else await marker.ref.set({cursor_path:page.docs.at(-1).ref.path},{merge:true});
}

exports.zeytinMarketBackfill=onSchedule({
  schedule:'every 5 minutes',timeZone:'Europe/Istanbul',region,
  timeoutSeconds:300,memory:'512MiB',maxInstances:1,retryCount:3
},async()=>{
  const markers=await db().collection(BACKFILL).limit(5).get();
  for(const marker of markers.docs)await backfillUser(marker);
});

async function collectSources(season,regionId){
  const out=[];let after=null;
  for(;;){
    let q=db().collection(SOURCE).where('season','==',season).where('region_id','==',regionId)
      .orderBy(FieldPath.documentId()).limit(500);
    if(after)q=q.startAfter(after);
    const page=await q.get();
    for(const d of page.docs)out.push(d.data());
    if(out.length>100000)throw new Error('Market cohort capacity exceeded');
    if(page.size<500)return out;
    after=page.docs.at(-1).id;
  }
}

async function publishDirty(marker){
  const d=marker.data(),startedDirty=d.dirty_at_ms;
  const season=d.season,regionId=d.region_id;
  if(!Number.isInteger(season)||season<2000||season>2100||typeof regionId!=='string'||!regionId)return;
  const sources=await collectSources(season,regionId);
  const meta={region_id:regionId,region_name:d.region_name||'',district:d.district||'',province:d.province||''};
  const now=Date.now(),stats=P.buildRegionStats(sources,meta,season,now);
  const available=new Set(stats.map(s=>P.statId(season,regionId,s.grade,s.period)));
  const batch=db().batch();
  for(const grade of P.GRADES)for(const period of P.PERIODS){
    const id=P.statId(season,regionId,grade,period.key),ref=db().collection(PUBLIC_STATS).doc(id);
    if(!available.has(id))batch.delete(ref);
  }
  for(const s of stats){batch.set(db().collection(PUBLIC_STATS).doc(P.statId(season,regionId,s.grade,s.period)),s);}
  const regionRef=db().doc(`zeytin_markets/${season}/regions/${regionId}`);
  if(stats.length){
    batch.set(regionRef,{schema_version:1,policy_version:1,season,region_id:regionId,region_name:meta.region_name,
      district:meta.district,province:meta.province,updated_at_ms:now,updated_at:Timestamp.fromMillis(now)});
    batch.set(db().doc(`zeytin_markets/${season}`),{schema_version:1,policy_version:1,updated_at_ms:now,updated_at:Timestamp.fromMillis(now)},{merge:true});
  }else {
    batch.delete(regionRef);
    if(!sources.length)batch.delete(db().collection(CATALOG).doc(dirtyId(season,regionId)));
  }
  await batch.commit();
  await db().runTransaction(async tx=>{
    const current=await tx.get(marker.ref);
    if(current.exists&&current.get('dirty_at_ms')===startedDirty)tx.delete(marker.ref);
  });
}

exports.zeytinMarketRefreshWindows=onSchedule({
  schedule:'10 0 * * *',timeZone:'Europe/Istanbul',region,
  timeoutSeconds:300,memory:'256MiB',maxInstances:1,retryCount:3
},async()=>{
  let after=null;
  for(;;){
    let q=db().collection(CATALOG).orderBy(FieldPath.documentId()).limit(400);
    if(after)q=q.startAfter(after);
    const page=await q.get();
    for(const doc of page.docs){
      const d=doc.data();
      await markDirty(d);
    }
    if(page.size<400)break;
    after=page.docs.at(-1).id;
  }
});

exports.zeytinMarketPublish=onSchedule({
  schedule:'every 5 minutes',timeZone:'Europe/Istanbul',region,
  timeoutSeconds:540,memory:'1GiB',maxInstances:1,retryCount:3
},async()=>{
  const markers=await db().collection(DIRTY).orderBy('dirty_at_ms').limit(3).get();
  for(const marker of markers.docs)await publishDirty(marker);
});

