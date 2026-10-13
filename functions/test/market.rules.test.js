'use strict';
const test=require('node:test'),fs=require('node:fs'),path=require('node:path');
const host=process.env.FIRESTORE_EMULATOR_HOST;
test('Firestore emulator: anonymous market is read-only and private sources stay server-only',{skip:!host},async()=>{
 if(!/^(localhost|127\.0\.0\.1):\d+$/.test(host))throw new Error('Local emulator only; never target production');
 const req=require('node:module').createRequire(path.join(__dirname,'rules/package.json'));
 const {initializeTestEnvironment,assertSucceeds,assertFails}=req('@firebase/rules-unit-testing');
 const {doc,getDoc,setDoc}=req('firebase/firestore');
 const [hostname,port]=host.split(':');
 const env=await initializeTestEnvironment({projectId:'demo-zeytin-market',firestore:{host:hostname,port:+port,
   rules:fs.readFileSync(path.resolve(__dirname,'../../firestore.rules'),'utf8')}});
 try{
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async c=>{
   const db=c.firestore();
   await setDoc(doc(db,'zeytin_markets/2026/regions/manisa_alasehir'),{schema_version:1,policy_version:1});
   await setDoc(doc(db,'zeytin_market_stats/2026__manisa_alasehir__2__30d'),{schema_version:1,policy_version:1,median:80});
   await setDoc(doc(db,'market_private_sources/private'),{uid:'owner',median:80});
  });
  const me=env.authenticatedContext('owner').firestore(),other=env.authenticatedContext('other').firestore();
  await assertSucceeds(getDoc(doc(me,'zeytin_markets/2026/regions/manisa_alasehir')));
  await assertSucceeds(getDoc(doc(me,'zeytin_market_stats/2026__manisa_alasehir__2__30d')));
  await assertFails(setDoc(doc(me,'zeytin_market_stats/fake'),{median:999}));
  await assertFails(getDoc(doc(me,'market_private_sources/private')));
  await assertFails(setDoc(doc(me,'market_private_sources/private2'),{uid:'owner'}));
  await assertSucceeds(setDoc(doc(me,'users/owner/market_settings/anonymous'),{enabled:true,policy_version:1}));
  await assertFails(setDoc(doc(other,'users/owner/market_settings/anonymous'),{enabled:true,policy_version:1}));
 }finally{await env.cleanup();}
});
