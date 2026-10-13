'use strict';
// Trusted ADC/IAM only. This module is never exported as a Cloud Function.
async function main(args){
 const [projectId,identity,action,apply]=args;
 if(args.length!==4||!/^[a-z][a-z0-9-]{4,62}$/.test(projectId)||!identity
  ||!['grant','revoke','bootstrap-super-admin','sync-index'].includes(action)||apply!=='--apply')
  throw new Error('Usage: node operator/moderation-role.js PROJECT_ID UID|email:FULL_EMAIL grant|revoke|bootstrap-super-admin|sync-index --apply (sync-index uses ALL as identity)');
 const result=await runOperation(projectId,identity,action,{inspect:u=>console.log('Firebase project: '+projectId+'; Action: '+action+'; Target: '+JSON.stringify({uid:u.uid,email:u.email||null}))});
 console.log('Role operation completed. Existing unrelated claims preserved.');
 return result;
}
// Shared ADC initialization and role/audit/index implementation for both CLIs.
async function runOperation(projectId,identity,action,options={}){
 if(!['grant','revoke','bootstrap-super-admin','revoke-super-admin','status-super-admin','sync-index'].includes(action))throw new Error('Invalid operator action');
 if(action.endsWith('super-admin'))require('./project-guard').assertProject(projectId);
 const {initializeApp,deleteApp}=require('firebase-admin/app'),{getAuth}=require('firebase-admin/auth');
 const {getFirestore,FieldValue}=require('firebase-admin/firestore');
 const {changeRole,key}=require('../moderation/roles');
 const app=initializeApp({projectId});
 try{
  const auth=getAuth(app),db=getFirestore(app);
  if(action==='sync-index'){
   if(identity!=='ALL')throw new Error('sync-index requires ALL');
   // One-time server-only backfill for existing 06.3A moderators. Never sent to client.
   let pageToken;
   do{const page=await auth.listUsers(500,pageToken);const batch=db.batch();
    for(const u of page.users){const ref=db.collection('moderation_role_members').doc(key(u.uid));
     if(u.customClaims?.moderation_admin===true)batch.set(ref,{uid:u.uid});else batch.delete(ref);}
    await batch.commit();pageToken=page.pageToken;
   }while(pageToken);
  }else{
   const user=options.uid!==undefined?await auth.getUser(options.uid):identity.startsWith('email:')?await auth.getUserByEmail(identity.slice(6)):await auth.getUser(identity);
   const id=user.uid;
   if(options.inspect)options.inspect(user);
   if(action==='status-super-admin'||options.preview)return user;
   await changeRole({db,auth,serverTimestamp:()=>FieldValue.serverTimestamp()},id,
    action==='revoke'?'REVOKE':action==='revoke-super-admin'?'REVOKE_SUPER_ADMIN':action==='bootstrap-super-admin'?'BOOTSTRAP_SUPER_ADMIN':'GRANT',
    'TRUSTED_OPERATOR',action==='bootstrap-super-admin'||action==='revoke-super-admin');
   return await auth.getUser(id);
  }
 }finally{await deleteApp(app);}
}
if(require.main===module)main(process.argv.slice(2)).catch(e=>{console.error(require('./project-guard').safeError(e));process.exitCode=1;});
module.exports={main,runOperation};
