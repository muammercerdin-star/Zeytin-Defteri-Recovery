'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const host=process.env.FIREBASE_AUTH_EMULATOR_HOST;
test('real Auth emulator: signed operator claim is server controlled and removed role rejects old token',{skip:!host},async()=>{
 if(!/^(localhost|127\.0\.0\.1):\d+$/.test(host))throw new Error('Local Auth emulator only');
 const {initializeApp,deleteApp}=require('firebase-admin/app'),{getAuth}=require('firebase-admin/auth');
 const {operator}=require('../league/approval');
 const app=initializeApp({projectId:'demo-zeytin-league'},'operator-auth-test'),auth=getAuth(app);
 try{
  const email='operator-'+Date.now()+'@example.test',password='emulator-only-password-123';
  async function signIn(method){
   const response=await fetch(`http://${host}/identitytoolkit.googleapis.com/v1/accounts:${method}?key=demo-only`,{
    method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password,returnSecureToken:true})});
   const value=await response.json();assert.ok(response.ok);return value;
  }
  const initial=await signIn('signUp'),uid=initial.localId;
  async function context(token){const decoded=await auth.verifyIdToken(token);return {uid:decoded.uid,token:decoded};}
  await assert.rejects(operator(await context(initial.idToken),u=>auth.getUser(u)),e=>e.code==='permission-denied');
  await auth.setCustomUserClaims(uid,{league_operator:true,unrelated_role:'preserved'});
  const fresh=await signIn('signInWithPassword'),ctx=await context(fresh.idToken);
  assert.equal(await operator(ctx,u=>auth.getUser(u)),uid);
  await auth.setCustomUserClaims(uid,{unrelated_role:'preserved'});
  await assert.rejects(operator(ctx,u=>auth.getUser(u)),e=>e.code==='permission-denied');
  await auth.setCustomUserClaims(uid,{league_operator:true});await auth.updateUser(uid,{disabled:true});
  await assert.rejects(operator(ctx,u=>auth.getUser(u)),e=>e.code==='permission-denied');
  await auth.deleteUser(uid);
 }finally{await deleteApp(app);}
});
