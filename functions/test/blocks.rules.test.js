'use strict';
const test=require('node:test'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const host=process.env.FIRESTORE_EMULATOR_HOST;
test('Blocks: owner privacy, self/spoof protection, duplicates, delete, offline rollback', {skip:!host},async()=>{
 if(!/^(localhost|127\.0\.0\.1):\d+$/.test(host))throw Error('Local emulator only');
 const req=require('node:module').createRequire(path.join(__dirname,'rules/package.json'));
 const {initializeTestEnvironment,assertSucceeds,assertFails}=req('@firebase/rules-unit-testing');
 const {doc,getDoc,setDoc,getDocs,deleteDoc,updateDoc,collection,serverTimestamp,runTransaction,disableNetwork,enableNetwork}=req('firebase/firestore');
 const [hostname,port]=host.split(':');
 const env=await initializeTestEnvironment({projectId:'demo-zeytin-blocks',firestore:{host:hostname,port:+port,rules:fs.readFileSync(path.resolve(__dirname,'../../firestore.rules'),'utf8')}});
 try{
  await env.clearFirestore();
  const a=env.authenticatedContext('A').firestore(),b=env.authenticatedContext('B').firestore(),c=env.authenticatedContext('C').firestore(),guest=env.unauthenticatedContext().firestore();
  const ref=(db,owner='A',blocked='B')=>doc(db,`user_blocks/${owner}/items/${blocked}`);
  const data={blocker_uid:'A',blocked_uid:'B',created_at:serverTimestamp(),display_name:'B',region:'Alaşehir'};
  const block=()=>runTransaction(a,async tx=>{const target=ref(a);if((await tx.get(target)).exists())return false;tx.set(target,data);return true;});
  assert.deepEqual((await Promise.all([block(),block()])).sort(),[false,true]);
  assert.equal((await getDocs(collection(a,'user_blocks/A/items'))).size,1);
  await assertFails(getDoc(ref(b)));await assertFails(getDoc(ref(c)));await assertFails(getDoc(ref(guest)));
  await assertFails(getDocs(collection(b,'user_blocks/A/items')));
  await assertFails(deleteDoc(ref(b)));await assertFails(deleteDoc(ref(c)));
  await assertFails(updateDoc(ref(a),{blocker_uid:'B'}));
  await assertFails(setDoc(ref(b,'A','C'),{...data,blocked_uid:'C'}));
  await assertFails(setDoc(ref(a,'A','C'),{...data,blocker_uid:'B',blocked_uid:'C'}));
  await assertFails(setDoc(ref(a,'A','A'),{...data,blocked_uid:'A'}));
  await assertFails(setDoc(ref(a,'A','C'),{...data,blocked_uid:'B'}));
  await assertFails(setDoc(ref(a,'A','C'),{...data,blocked_uid:'C',created_at:1}));
  await assertFails(setDoc(ref(a,'A','C'),{...data,blocked_uid:'C',extra:true}));
  await disableNetwork(a);
  await assertFails(runTransaction(a,async tx=>{await tx.get(ref(a));tx.delete(ref(a));}));
  await enableNetwork(a);assert.equal((await getDoc(ref(a))).exists(),true);
  await assertSucceeds(runTransaction(a,async tx=>{await tx.get(ref(a));tx.delete(ref(a));}));
  assert.equal((await getDoc(ref(a))).exists(),false);
  await assertSucceeds(block());
  // A block is personal presentation state, not destruction or a social permission change.
  await assertSucceeds(setDoc(doc(b,'social_posts/b-post'),{user_id:'B',is_active:1,content:'korunur'}));
  assert.equal((await getDoc(doc(a,'social_posts/b-post'))).data().content,'korunur');
  await assertSucceeds(setDoc(ref(c,'C','B'),{...data,blocker_uid:'C'}));
  assert.equal((await getDocs(collection(c,'user_blocks/C/items'))).size,1);
 }finally{await env.cleanup();}
});
