import {generateKeyPair,SignJWT,exportJWK,createLocalJWKSet} from 'npm:jose@5.10.0';
import {createGoogleClient,createAuthApi,verifyFirebase,PROJECT} from '../firebase.ts';
import {RestFirestore,Time,serverTimestamp} from '../firestore.ts';
import {createHandler} from '../handler.ts';
const fhost=Deno.env.get('FIRESTORE_EMULATOR_HOST'),ahost=Deno.env.get('FIREBASE_AUTH_EMULATOR_HOST');
function ok(v:unknown,m='assert'){if(!v)throw Error(m);}
Deno.test({name:'real REST Auth/Firestore: all six actions, descending cursor ties/deletion, server review fields, claims audit locks and stale access',ignore:!fhost||!ahost,fn:async()=>{
 for(const h of [fhost,ahost])if(!/^(localhost|127\.0\.0\.1):\d+$/.test(h!))throw Error('Local only');
 const project='demo-edge-moderation',post=createGoogleClient(async()=>'owner');
 const db=new RestFirestore(post,project,'http://'+fhost),auth=createAuthApi(post,`http://${ahost}/identitytoolkit.googleapis.com/v1/projects/${project}`);
 const deps={db,auth,documentId:()=>'__name__',serverTimestamp,timestamp:(s:number,n:number)=>new Time(s,n)};
 const keys=await generateKeyPair('RS256'),jwk={...await exportJWK(keys.publicKey),kid:'rest-test'};const jwks=createLocalJWKSet({keys:[jwk]});
 async function token(uid:string,claims:Record<string,unknown>){return new SignJWT({...claims,auth_time:Math.floor(Date.now()/1000)})
  .setProtectedHeader({alg:'RS256',kid:'rest-test'}).setSubject(uid).setIssuer(`https://securetoken.google.com/${PROJECT}`).setAudience(PROJECT).setIssuedAt().setExpirationTime('5m').sign(keys.privateKey);}
 const adminToken=await token('edge-super',{moderation_admin:true,super_admin:true}),modToken=await token('edge-mod',{moderation_admin:true}),normalToken=await token('edge-normal',{});
 const handler=createHandler(deps,t=>verifyFirebase(t,jwks));
 async function call(action:string,data:unknown={},t=adminToken){const response=await handler(new Request('https://edge.test',{method:'POST',headers:{Authorization:'Bearer '+t,'Content-Type':'application/json'},body:JSON.stringify({action,data})}));return {status:response.status,value:await response.json()};}
 function record(id:string,t:Time){return {reporter_uid:'edge-normal',reported_uid:'edge-target',target_type:'POST',target_id:id,post_cloud_id:id,reason:'SPAM',status:'OPEN',created_at:t};}
 const seed=db.batch();seed.set(db.doc('reports/edge-normal/items/POST|old|old'),record('old',new Time(100,0)));
 seed.set(db.doc('reports/edge-normal/items/POST|a|a'),record('a',new Time(200,2)));
 seed.set(db.doc('reports/edge-normal/items/POST|b|b'),record('b',new Time(200,2)));
 seed.set(db.doc('users/edge-normal/items/private'),{status:'OPEN',created_at:new Time(300,0),secret:'not returned'});await seed.commit();
 ok((await call('moderationListReports',{},normalToken)).status===403);
 const first=await call('moderationListReports',{limit:1},modToken);ok(first.status===200,JSON.stringify(first));ok(first.value.data.reports[0].report_id==='POST|b|b');
 const del=db.batch();del.delete(db.doc('reports/edge-normal/items/POST|b|b'));await del.commit();
 const next=await call('moderationListReports',{limit:1,cursor:first.value.data.next_cursor},modToken);ok(next.value.data.reports[0].report_id==='POST|a|a');
 const last=await call('moderationListReports',{limit:1,cursor:next.value.data.next_cursor},modToken);ok(last.value.data.reports[0].report_id==='POST|old|old'&&last.value.data.next_cursor===null);
 const update={reporter_uid:'edge-normal',report_id:'POST|a|a',status:'REVIEWED',expected_status:'OPEN'};
 ok((await call('moderationUpdateReportStatus',update,normalToken)).status===403);
 ok((await call('moderationUpdateReportStatus',update,modToken)).status===200);
 const reviewed=(await db.doc('reports/edge-normal/items/POST|a|a').get()).data()!;ok(reviewed.reviewed_by==='edge-mod'&&reviewed.reviewed_at instanceof Time);ok(reviewed.reason==='SPAM');
 ok((await call('moderationUpdateReportStatus',update,modToken)).status===409);
 for(const status of ['REVIEWED','ACTIONED','REJECTED']){
  ok((await call('moderationListReports',{status},modToken)).status===200);
 }
 ok((await call('moderationLookupUser',{email:'edge-target@example.com'})).value.data.user.uid==='edge-target');
 ok((await call('moderationGrantModerator',{target_uid:'edge-target'})).status===200);
 ok((await auth.getUser('edge-target')).customClaims.other==='keep');
 const list=await call('moderationListModerators');ok(list.value.data.moderators.some((u:any)=>u.uid==='edge-target'));
 const oldToken=await token('edge-target',{moderation_admin:true});ok((await call('moderationListReports',{},oldToken)).status===200);
 ok((await call('moderationRevokeModerator',{target_uid:'edge-target'})).status===200);
 ok((await call('moderationListReports',{},oldToken)).status===403);
 const target=await auth.getUser('edge-target');ok(target.customClaims.league_operator===true&&target.customClaims.moderation_admin===undefined);
 // Audit query via REST: exact target and server timestamps; existing collection names.
 const rows=await post(db.url+':runQuery',{structuredQuery:{from:[{collectionId:'moderation_role_audit'}],where:{fieldFilter:{field:{fieldPath:'target_uid'},op:'EQUAL',value:{stringValue:'edge-target'}}}}});
 const audit=rows.filter((r:any)=>r.document).map((r:any)=>db.snapshot(r.document).data());ok(audit.some((a:any)=>a.action==='REVOKE'&&a.actor_uid==='edge-super'&&a.timestamp instanceof Time&&a.status==='COMPLETED'));
 ok((await call('moderationRevokeModerator',{target_uid:'edge-super'})).status===409);
 // Real transaction serialization: two contenders cannot both acquire the same missing lock.
 const ref=db.doc('moderation_role_locks/rest-race');
 const outcomes=await Promise.allSettled([1,2].map(()=>db.runTransaction(async tx=>{if((await tx.get(ref)).exists)throw Error('locked');tx.set(ref,{test:true});})));
 ok(outcomes.filter(r=>r.status==='fulfilled').length===1);
 const cleanup=db.batch();cleanup.delete(ref);await cleanup.commit();
}});
