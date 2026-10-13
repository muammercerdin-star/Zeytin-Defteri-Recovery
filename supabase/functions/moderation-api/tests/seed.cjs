'use strict';
// Test-only, local emulators required. This file is never imported by the Edge entrypoint.
const path=require('node:path');
const req=require('node:module').createRequire(path.resolve(__dirname,'../../../../functions/package.json'));
for(const host of [process.env.FIRESTORE_EMULATOR_HOST,process.env.FIREBASE_AUTH_EMULATOR_HOST])if(!/^(localhost|127\.0\.0\.1):\d+$/.test(host||''))throw Error('Local emulators required');
const {initializeApp,deleteApp}=req('firebase-admin/app'),{getAuth}=req('firebase-admin/auth');
(async()=>{const app=initializeApp({projectId:'demo-edge-moderation'});try{
 const auth=getAuth(app);
 for(const [uid,claims] of [['edge-super',{super_admin:true,moderation_admin:true}],['edge-mod',{moderation_admin:true}],['edge-normal',{}],['edge-target',{league_operator:true,other:'keep'}]]){
  try{await auth.deleteUser(uid);}catch(e){if(e.code!=='auth/user-not-found')throw e;}
  await auth.createUser({uid,email:uid+'@example.com'});await auth.setCustomUserClaims(uid,claims);
 }
}finally{await deleteApp(app);}})().catch(e=>{console.error(e.code||'seed failed');process.exitCode=1;});
