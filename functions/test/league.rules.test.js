'use strict';
const test=require('node:test'),fs=require('node:fs'),path=require('node:path');
const host=process.env.FIRESTORE_EMULATOR_HOST;
test('Firestore emulator: private ranks, global writes and evidence are protected',{skip:!host},async()=>{
 if(!/^(localhost|127\.0\.0\.1):\d+$/.test(host))throw new Error('Local emulator only; never target production');
 const req=require('node:module').createRequire(path.join(__dirname,'rules/package.json'));
 const {initializeTestEnvironment,assertSucceeds,assertFails}=req('@firebase/rules-unit-testing');
 const {doc,getDoc,setDoc,getDocs,collection}=req('firebase/firestore');
 const [hostname,port]=host.split(':');
 const env=await initializeTestEnvironment({projectId:'demo-zeytin-league',firestore:{host:hostname,port:+port,
   rules:fs.readFileSync(path.resolve(__dirname,'../../firestore.rules'),'utf8')}});
 const base='zeytin_leagues/2026/snapshots/test_generation';
 try{
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async c=>{
   const db=c.firestore();await setDoc(doc(db,base+'/members/owner'),{rank:37,score:75});
   await setDoc(doc(db,base+'/members/other'),{rank:1,score:99});
   await setDoc(doc(db,base+'/regions/manisa_alasehir'),{top:[],producer_count:100});
  });
  const me=env.authenticatedContext('owner').firestore(),guest=env.unauthenticatedContext().firestore();
  await assertSucceeds(getDoc(doc(me,base+'/members/owner')));
  await assertFails(getDoc(doc(me,base+'/members/other')));
  await assertFails(getDocs(collection(me,base+'/members')));
  await assertSucceeds(getDoc(doc(me,base+'/regions/manisa_alasehir')));
  await assertFails(getDoc(doc(guest,base+'/members/owner')));
  for(const target of ['zeytin_leagues/2026',base,base+'/regions/manisa_alasehir',base+'/members/owner',
    'league_sale_attestations/proof','league_backend_config/main','league_backend_jobs/job','league_backend_locks/2026']) {
   await assertFails(setDoc(doc(me,target),{rank:1,score:100,status:'verified'}));
  }
  await assertFails(getDoc(doc(me,'league_sale_attestations/proof')));
  await assertSucceeds(setDoc(doc(me,'users/owner/gardens/g'),{owner_uid:'owner'}));
  await assertFails(setDoc(doc(me,'users/other/gardens/g'),{owner_uid:'owner'}));
  const salePath='users/owner/gardens/g/sales/s';
  await assertSucceeds(setDoc(doc(me,salePath),{owner_uid:'owner',market_consent:1}));
  await assertSucceeds(getDoc(doc(me,salePath)));
  for(const key of ['verified','approved','attested','verified_sale','verification_status','verified_by']) {
   await assertFails(setDoc(doc(me,salePath),{owner_uid:'owner',market_consent:1,[key]:true}));
   await assertFails(setDoc(doc(me,salePath+'2'),{owner_uid:'owner',[key]:true}));
  }
  const operator=env.authenticatedContext('operator',{league_operator:true}).firestore();
  await assertFails(setDoc(doc(operator,'league_sale_attestations/proof'),{status:'verified'}));
  await assertFails(getDoc(doc(me,'league_sale_attestations/other-proof')));
  await assertFails(getDocs(collection(me,'league_sale_attestations')));
  await assertFails(getDoc(doc(me,'league_sale_attestations/proof/audit/1')));
  await assertFails(setDoc(doc(me,'league_verification_claims/key'),{attestation_id:'fake'}));
  await assertFails(getDoc(doc(me,'league_verification_claims/key')));

 }finally{await env.cleanup();}
});
