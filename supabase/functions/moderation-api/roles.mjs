// Moderator operations only. Super Admin bootstrap stays in trusted CLI.
import {authorize} from './service.mjs';
import {sha256} from './sha256.mjs';
function fail(code,message){const e=new Error(message);e.code=code;throw e;}
function fields(d,allowed){if(!d||typeof d!=='object'||Array.isArray(d)||Object.keys(d).some(k=>!allowed.includes(k)))fail('invalid-argument','Unexpected request fields');}
function uid(value){if(typeof value!=='string'||!value.length||value.length>128||value.trim()!==value||/[\x00-\x1f\x7f]/.test(value))fail('invalid-argument','Full Firebase UID required');return value;}
const key=async value=>sha256(uid(value));
function summary(u){return {uid:u.uid,display_name:u.displayName||null,email:u.email||null,disabled:u.disabled===true,moderation_admin:u.customClaims?.moderation_admin===true,super_admin:u.customClaims?.super_admin===true};}
async function target(auth,id){try{return await auth.getUser(uid(id));}catch(e){if(e.code==='auth/user-not-found')fail('not-found','User not found');throw e;}}
// Auth and Firestore cannot share a transaction. Persist intent BEFORE changing
// claims; uncertain failures retain the lock and PENDING audit for operator review.
// No expiring lease: a slow writer must never resume over a newer role change.
async function changeRole({db,auth,serverTimestamp},id,action,actor,recheck=async()=>{}){
 uid(id);
 const lock=db.collection('moderation_role_locks').doc(await key(id));
 const audit=db.collection('moderation_role_audit').doc();
 const index=db.collection('moderation_role_members').doc(await key(id));
 await db.runTransaction(async tx=>{
  if((await tx.get(lock)).exists)fail('failed-precondition','Role operation pending; operator review required');
  tx.set(lock,{target_uid:id,audit_id:audit.id});
  tx.set(audit,{action,actor_uid:actor,target_uid:id,timestamp:serverTimestamp(),status:'PENDING',source:'ROLE_MANAGEMENT'});
 });
 let mutationStarted=false;
 try{
  await recheck();
  const user=await target(auth,id),claims={...user.customClaims};
  if(claims.super_admin===true)fail('failed-precondition','Super Admin is operator-managed');
  if(action!=='REVOKE'&&user.disabled)fail('failed-precondition','Disabled target account');
  if(action==='GRANT')claims.moderation_admin=true;
  else if(action==='REVOKE')delete claims.moderation_admin;
  else fail('invalid-argument','Invalid role action');
  mutationStarted=true;
  await auth.setCustomUserClaims(id,claims);
  if(action==='REVOKE')await auth.revokeRefreshTokens(id);
  const batch=db.batch();
  if(claims.moderation_admin===true)batch.set(index,{uid:id});else batch.delete(index);
  batch.update(audit,{status:'COMPLETED',completed_at:serverTimestamp()});batch.delete(lock);await batch.commit();
  return {uid:id,moderation_admin:claims.moderation_admin===true};
 }catch(e){
  if(!mutationStarted){const batch=db.batch();batch.update(audit,{status:'REJECTED',completed_at:serverTimestamp()});batch.delete(lock);await batch.commit();}
  throw e;
 }
}
function createRoleService(deps){
 const {db,auth,documentId}=deps;
 const check=request=>authorize(request.auth,id=>auth.getUser(id),'super_admin');
 async function change(request,action){
  const actor=await check(request);fields(request.data,['target_uid']);
  return changeRole(deps,uid(request.data.target_uid),action,actor,()=>check(request));
 }
 return {
  async lookup(request){
   await check(request);const d=request.data;fields(d,['uid','email']);
   if((d.uid!==undefined)===(d.email!==undefined))fail('invalid-argument','Supply exactly one full UID or email');
   let user;
   if(d.uid!==undefined)user=await target(auth,d.uid);
   else{
    if(typeof d.email!=='string'||d.email.length>254||! /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(d.email))fail('invalid-argument','Full email required');
    try{user=await auth.getUserByEmail(d.email);}catch(e){if(e.code==='auth/user-not-found')fail('not-found','User not found');throw e;}
   }
   return {user:summary(user)};
  },
  grant:request=>change(request,'GRANT'),revoke:request=>change(request,'REVOKE'),
  async list(request){
   await check(request);const d=request.data??{};fields(d,['limit','cursor']);
   const limit=d.limit===undefined?25:d.limit;
   if(!Number.isInteger(limit)||limit<1||limit>100)fail('invalid-argument','Limit must be 1..100');
   if(d.cursor!==undefined&&(typeof d.cursor!=='string'||! /^[a-f0-9]{64}$/.test(d.cursor)))fail('invalid-argument','Invalid cursor');
   let query=db.collection('moderation_role_members').orderBy(documentId());
   if(d.cursor)query=query.startAfter(d.cursor);
   const docs=(await query.limit(limit+1).get()).docs,users=[];
   // Bounded reads even with stale/deleted index entries. An empty page may
   // still have next_cursor: clients must follow it until null.
   for(const doc of docs.slice(0,limit)){
    const id=doc.data().uid;if(typeof id!=='string'||await key(id)!==doc.id)continue;
    try{const u=await target(auth,id);if(u.customClaims?.moderation_admin===true)users.push(summary(u));}
    catch(e){if(e.code!=='not-found')throw e;}
   }
   return {moderators:users,next_cursor:docs.length>limit?docs[limit-1].id:null};
  }
 };
}
export {createRoleService,changeRole,key,summary};
