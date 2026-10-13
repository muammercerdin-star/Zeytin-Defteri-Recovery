'use strict';
const P=require('./policy');
class ApprovalError extends Error {constructor(code,message){super(message);this.code=code;}}
const fail=(code,message)=>{throw new ApprovalError(code,message);};
const keys=(o,allowed)=>o&&typeof o==='object'&&!Array.isArray(o)&&Object.keys(o).every(k=>allowed.includes(k));
const id=s=>typeof s==='string'&&/^[A-Za-z0-9_-]{1,128}$/.test(s);
// Called only with onCall's SDK-validated auth context. Never trust request.data roles.
async function operator(auth,getUser){
 if(!auth?.uid)fail('unauthenticated','Operator sign-in required');
 if(auth.token?.league_operator!==true)fail('permission-denied','Operator role required');
 const user=await getUser(auth.uid);
 const revokedAfter=user.tokensValidAfterTime?Date.parse(user.tokensValidAfterTime)/1000:0;
 if(user.disabled||user.customClaims?.league_operator!==true||!Number.isFinite(auth.token.auth_time)
   ||!Number.isFinite(revokedAfter)||auth.token.auth_time<revokedAfter)
  fail('permission-denied','Operator authorization is no longer valid');
 return auth.uid;
}
function command(data){
 if(!keys(data,['action','uid','garden_id','record_id','sale_garden_id','sale_id','season','request_id','expected_revision',
   'evidence_id','evidence_type','expires_at_ms','reviewed','reason_code'])
   ||!['approve','revoke'].includes(data.action)||!id(data.uid)||!id(data.garden_id)||!id(data.record_id)
   ||!id(data.request_id)||!Number.isSafeInteger(data.expected_revision)||data.expected_revision<0)
  fail('invalid-argument','Invalid approval command');
 if(data.action==='revoke'){
  if(!['evidence_withdrawn','source_mismatch','operator_error'].includes(data.reason_code))fail('invalid-argument','Revocation reason required');
 }else{
  if(!id(data.sale_garden_id)||!id(data.sale_id)||!Number.isInteger(data.season)||data.season<2000||data.season>2100
    ||typeof data.evidence_id!=='string'||!/^ev1_[a-f0-9]{64}$/.test(data.evidence_id)
    ||!['independent_document','buyer_confirmation'].includes(data.evidence_type)
    ||!Number.isSafeInteger(data.expires_at_ms)
    ||!keys(data.reviewed,['region_id','kg','amount','grades','values'])
    ||typeof data.reviewed.region_id!=='string'||!/^[a-z0-9_]{3,150}$/.test(data.reviewed.region_id)
    ||!['kg','amount'].every(k=>Number.isFinite(data.reviewed[k])&&data.reviewed[k]>0)
    ||!['grades','values'].every(k=>Array.isArray(data.reviewed[k])&&data.reviewed[k].length===6
       &&data.reviewed[k].every(v=>typeof v==='number'&&Number.isFinite(v)&&v>=0)))
   fail('invalid-argument','Independent reviewed evidence is required');
 }
 return data;
}
function createApproval({db,getUser,clock=()=>Date.now()}){
 async function execute(request){
  const actor=await operator(request.auth,getUser),c=command(request.data);
  if(actor===c.uid)fail('permission-denied','Self-approval is prohibited');
  const proofId=P.proofId(c.uid,c.garden_id,c.record_id),proofRef=db.collection('league_sale_attestations').doc(proofId);
  const digest=P.hash(P.canonical(c));
  return db.runTransaction(async tx=>{
   const prior=await tx.get(proofRef),old=prior.exists?prior.data():null;
   if(old?.last_request_id===c.request_id){
    if(old.last_command_hash!==digest||old.last_actor!==actor)fail('already-exists','Request ID was already used');
    return {attestation_id:proofId,status:old.status,revision:old.revision};
   }
   const revision=old?.revision||0;
   if(revision!==c.expected_revision)fail('failed-precondition','Review version changed');
   const now=clock();let next;
   if(c.action==='revoke'){
    if(!old)fail('not-found','Attestation not found');
    next={...old,status:'revoked',revoked_at_ms:now,revoked_by:actor,reason_code:c.reason_code};
   }else{
    if(c.expires_at_ms<now+2*86400000||c.expires_at_ms>now+366*86400000)
     fail('invalid-argument','Validity must be between 2 and 366 days');
    // Permanent claims are uniqueness indexes, not another raw/evidence model.
    const claimRefs=['evidence_'+P.hash(c.evidence_id),'sale_'+P.hash(P.canonical([c.uid,c.sale_garden_id,c.sale_id]))]
     .map(k=>db.collection('league_verification_claims').doc(k));
    const claims=await Promise.all(claimRefs.map(r=>tx.get(r)));
    if(claims.some(d=>d.exists&&d.get('attestation_id')!==proofId))fail('already-exists','Evidence or sale already assigned');
    if(old&&(old.evidence_id!==c.evidence_id||old.sale_id!==c.sale_id||old.sale_garden_id!==c.sale_garden_id))
     fail('failed-precondition','Existing attestation cannot be rebound');
    // Compatibility with older server attestations created before the claim index.
    const legacyEvidence=await tx.get(db.collection('league_sale_attestations').where('evidence_id','==',c.evidence_id).limit(2));
    const legacySale=await tx.get(db.collection('league_sale_attestations').where('uid','==',c.uid)
      .where('sale_garden_id','==',c.sale_garden_id).where('sale_id','==',c.sale_id).limit(2));
    if([...legacyEvidence.docs,...legacySale.docs].some(d=>d.id!==proofId))fail('already-exists','Legacy evidence or sale already assigned');
    const base=`users/${c.uid}/gardens/`;
    const docs=await Promise.all([`${base}${c.garden_id}/elek_records/${c.record_id}`,`${base}${c.garden_id}`,
      `${base}${c.sale_garden_id}/sales/${c.sale_id}`,`${base}${c.sale_garden_id}`].map(p=>tx.get(db.doc(p))));
    if(docs.some(d=>!d.exists))fail('failed-precondition','Source record missing');
    const [raw,garden,sale,saleGarden]=docs.map(d=>d.data());
    try{
     next={schema_version:1,status:'verified',uid:c.uid,garden_id:c.garden_id,record_id:c.record_id,
      sale_garden_id:c.sale_garden_id,sale_id:c.sale_id,season:c.season,evidence_id:c.evidence_id,
      evidence_type:c.evidence_type,verifier:actor,verified_at_ms:now,expires_at_ms:c.expires_at_ms,
      record_digest:P.recordDigest(raw),sale_digest:P.saleDigest(sale),region_digest:P.regionDigest(garden)};
     const matched=P.validateProof(next,proofId,raw,garden,sale,saleGarden,now),v=c.reviewed;
     if(!matched||v.region_id!==matched.region_id||P.region(saleGarden.district,saleGarden.province).key!==matched.region_id
       ||Math.abs(v.kg-matched.kg)>.01||Math.abs(v.amount-matched.amount)>.01
       ||v.grades.some((n,i)=>Math.abs(n-matched.grades[i])>.01)
       ||v.values.some((n,i)=>Math.abs(n-matched.values[i])>.01))throw new Error('Mismatch');
     next.region_id=matched.region_id;next.matched_kg=matched.kg;next.matched_amount=matched.amount;
    }catch{fail('failed-precondition','Independent evidence does not match source records');}
    // No reads below here. Sources, uniqueness reservations, proof and audit commit atomically.
    for(const r of claimRefs)tx.set(r,{attestation_id:proofId});
   }
   next.revision=revision+1;next.last_request_id=c.request_id;next.last_command_hash=digest;next.last_actor=actor;
   tx.set(proofRef,next);
   tx.create(proofRef.collection('audit').doc(String(next.revision)),{
    action:c.action,actor,at_ms:now,revision:next.revision,request_id:c.request_id,
    command_hash:digest,reason_code:c.reason_code||'',record_digest:next.record_digest,sale_digest:next.sale_digest,
    region_digest:next.region_digest,status:next.status});
   return {attestation_id:proofId,status:next.status,revision:next.revision};
  });
 }
 return {execute};
}
module.exports={createApproval,operator,command,ApprovalError};
