'use strict';
const test=require('node:test'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const host=process.env.FIRESTORE_EMULATOR_HOST;
test('Reports: ownership, targets, duplicate races, privacy, immutable status and social regression',
 {skip:!host},async()=>{
 if(!/^(localhost|127\.0\.0\.1):\d+$/.test(host))throw Error('Local emulator only');
 const req=require('node:module').createRequire(path.join(__dirname,'rules/package.json'));
 const {initializeTestEnvironment,assertSucceeds,assertFails}=req('@firebase/rules-unit-testing');
 const {doc,getDoc,getDocs,collection,setDoc,updateDoc,deleteDoc,serverTimestamp,runTransaction,disableNetwork,enableNetwork}=req('firebase/firestore');
 const [hostname,port]=host.split(':');
 const env=await initializeTestEnvironment({projectId:'demo-zeytin-reports',firestore:{host:hostname,port:+port,
 rules:fs.readFileSync(path.resolve(__dirname,'../../firestore.rules'),'utf8')}});
 try {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async ctx=>{
   const db=ctx.firestore();
   for(const [id,uid] of [['p','B'],['q','B'],['own','A']])await setDoc(doc(db,'social_posts/'+id),{user_id:uid,is_active:1});
   await setDoc(doc(db,'social_posts/p/comments/c'),{user_id:'B',parent_comment_id:'',is_active:1});
   await setDoc(doc(db,'social_posts/p/comments/r'),{user_id:'B',parent_comment_id:'c',is_active:1});
   await setDoc(doc(db,'social_posts/p/comments/gone'),{user_id:'B',parent_comment_id:'',is_active:0});
  });
  const a=env.authenticatedContext('A').firestore(),b=env.authenticatedContext('B').firestore();
  const guest=env.unauthenticatedContext().firestore();
  const anon=env.authenticatedContext('anon',{firebase:{sign_in_provider:'anonymous'}}).firestore();
  const data=(type,id,post='p',comment='')=>({reporter_uid:'A',reported_uid:'B',target_type:type,
    target_id:id,post_cloud_id:post,comment_cloud_id:comment,reason:'SPAM',created_at:serverTimestamp(),status:'OPEN'});
  const key=d=>d.target_type+'|'+(d.target_type==='USER'?'':d.post_cloud_id)+'|'+d.target_id;
  const ref=(db,d,uid='A')=>doc(db,'reports/'+uid+'/items/'+key(d));
  const post=data('POST','p');
  await assertSucceeds(getDoc(ref(a,post))); // own absent read is needed for transaction
  const submit=()=>runTransaction(a,async tx=>{const r=ref(a,post);if((await tx.get(r)).exists())return false;tx.set(r,post);return true;});
  assert.deepEqual((await Promise.all([submit(),submit()])).sort(),[false,true]);
  assert.equal(await submit(),false);
  await assertFails(setDoc(ref(a,post),post));
  await assertSucceeds(getDoc(ref(a,post)));
  await assertFails(getDoc(ref(b,post)));
  await assertFails(getDoc(ref(guest,post)));
  await assertFails(getDocs(collection(a,'reports/A/items')));
  await assertFails(getDocs(collection(a,'reports')));
  await assertFails(updateDoc(ref(a,post),{status:'CLOSED'}));
  await assertFails(deleteDoc(ref(a,post)));
  await assertFails(setDoc(ref(b,post),post));
  const comment=data('COMMENT','c','p','c'),reply=data('REPLY','r','p','r');
  await assertSucceeds(setDoc(ref(a,comment),comment));
  await assertSucceeds(setDoc(ref(a,reply),reply));
  const user=data('USER','B','p','r');await assertSucceeds(setDoc(ref(a,user),user));
  const anotherEvidence=data('USER','B','q');assert.equal(key(user),key(anotherEvidence));
  await assertFails(setDoc(ref(a,anotherEvidence),anotherEvidence));
  const invalid=[
    {...data('POST','q','q'),reporter_uid:'B'},
    {...data('POST','own','own'),reported_uid:'A'},
    {...data('POST','q','q'),reported_uid:'C'},
    data('POST','missing','missing'),data('COMMENT','gone','p','gone'),
    data('REPLY','c','p','c'),data('COMMENT','r','p','r'),
    data('COMMENT','c','q','c'),data('USER','C','p'),
    {...data('POST','q','q'),status:'CLOSED'},
    {...data('POST','q','q'),reason:'FAKE'},
    {...data('POST','q','q'),created_at:1},
    {...data('POST','q','q'),extra:'unexpected'}
  ];
  for(const d of invalid)await assertFails(setDoc(ref(a,d),d));
  const fresh=data('POST','q','q');
  await assertFails(setDoc(doc(a,'reports/A/items/random'),fresh));
  await assertFails(setDoc(doc(a,'reports/B/items/'+key(fresh)),fresh));
  await assertFails(setDoc(ref(guest,fresh),fresh));
  await assertFails(setDoc(ref(anon,{...fresh,reporter_uid:'anon'},'anon'),{...fresh,reporter_uid:'anon'}));
  await disableNetwork(a);
  await assertFails(runTransaction(a,async tx=>{const r=ref(a,fresh);await tx.get(r);tx.set(r,fresh);}));
  await enableNetwork(a);
  assert.equal((await getDoc(ref(a,fresh))).exists(),false);
  // Reporting must not change the original post or nested reply.
  assert.equal((await getDoc(doc(a,'social_posts/p'))).data().is_active,1);
  assert.equal((await getDoc(doc(a,'social_posts/p/comments/r'))).data().is_active,1);
  const now=Date.now();
  const root={cloud_id:'new-c',post_cloud_id:'p',user_id:'A',user_name:'A',content:'Yeni yorum',is_active:1,created_at:now,updated_at:now,
    parent_comment_id:'',root_comment_id:'new-c',reply_to_uid:'',reply_to_name:''};
  await assertSucceeds(setDoc(doc(a,'social_posts/p/comments/new-c'),root));
  await assertSucceeds(setDoc(doc(a,'social_posts/p/comments/new-r'),{...root,cloud_id:'new-r',parent_comment_id:'new-c',reply_to_uid:'A',reply_to_name:'A'}));
  // Gönderi kuralları sertleştirildi: eksik alanlı eski biçim artık reddedilir, istemcinin gerçek biçimi kabul edilir.
  await assertFails(setDoc(doc(a,'social_posts/new-post'),{user_id:'A',content:'Yeni gönderi',is_active:1}));
  await assertSucceeds(setDoc(doc(a,'social_posts/new-post'),{cloud_id:'new-post',user_id:'A',user_name:'Üretici',
    post_type:'GENERAL',content:'Yeni gönderi',created_at:now,updated_at:now,is_active:1},{merge:true}));
 }finally{await env.cleanup();}
});
