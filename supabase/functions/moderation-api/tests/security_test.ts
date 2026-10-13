import {generateKeyPair,SignJWT,exportJWK,createLocalJWKSet,exportPKCS8,jwtVerify} from 'npm:jose@5.10.0';
import {verifyFirebase,PROJECT,createAuthApi,createOAuth} from '../firebase.ts';
import {createHandler,createLimiter} from '../handler.ts';
// Unit fixtures reuse the proven 06.3C database and Auth doubles; production never imports them.
import {createRequire} from 'node:module';
const {fixture}=createRequire(import.meta.url)('../../../../functions/test/role-fixture.js');
function ok(v:unknown,m='assertion'){if(!v)throw Error(m);}
async function reject(fn:()=>Promise<unknown>){try{await fn();}catch{return;}throw Error('expected rejection');}
const keys=await generateKeyPair('RS256',{extractable:true});const publicJwk={...await exportJWK(keys.publicKey),kid:'test'};
const local=createLocalJWKSet({keys:[publicJwk]});
async function token(claims:Record<string,unknown>={},options:Record<string,unknown>={}){
 return await new SignJWT({auth_time:Math.floor(Date.now()/1000),moderation_admin:true,super_admin:true,...claims})
  .setProtectedHeader({alg:'RS256',kid:'test'}).setIssuer(String(options.issuer||`https://securetoken.google.com/${PROJECT}`))
  .setAudience(String(options.audience||PROJECT)).setSubject(String(options.uid||'super')).setIssuedAt().setExpirationTime(String(options.exp||'5m')).sign(keys.privateKey);
}
function request(action:string,data:unknown={},bearer?:string){return new Request('https://example.test/functions/v1/moderation-api',{method:'POST',headers:{'Content-Type':'application/json',...(bearer?{Authorization:'Bearer '+bearer}:{})},body:JSON.stringify({action,data})});}
Deno.test('cryptographic verification rejects forged, wrong-project, expired, missing claims, future auth time and tenant tokens',async()=>{
 const good=await token();ok((await verifyFirebase(good,local)).uid==='super');
 for(const bad of [good.slice(0,-12)+'AAAAAAAAAAAA',await token({}, {audience:'other'}),await token({}, {issuer:'https://securetoken.google.com/other'}),await token({}, {exp:'-1m'}),await token({auth_time:undefined}),await token({auth_time:Math.floor(Date.now()/1000)+3600}),await token({firebase:{tenant:'other'}})])await reject(()=>verifyFirebase(bad,local));
});
Deno.test('HTTP gate denies missing/forged token, methods, media type, oversized body and extra role fields',async()=>{
 const h=fixture(),handle=createHandler(h.deps,t=>verifyFirebase(t,local)),valid=await token();
 ok((await handle(request('moderationListModerators'))).status===401);
 ok((await handle(request('moderationListModerators',{},'fake.jwt.token'))).status===401);
 ok((await handle(new Request('https://x.test'))).status===405);
 const media=new Request('https://x.test',{method:'POST',headers:{Authorization:'Bearer '+valid,'Content-Type':'text/plain'},body:'{}'});ok((await handle(media)).status===400);
 const large=new Request('https://x.test',{method:'POST',headers:{Authorization:'Bearer '+valid,'Content-Type':'application/json'},body:'x'.repeat(8193)});ok((await handle(large)).status===413);
 const extra=new Request('https://x.test',{method:'POST',headers:{Authorization:'Bearer '+valid,'Content-Type':'application/json'},body:JSON.stringify({action:'moderationListModerators',data:{},super_admin:true})});ok((await handle(extra)).status===400);
 const response=await handle(request('moderationListModerators',{},valid));ok(!response.headers.has('Access-Control-Allow-Origin'));
});
Deno.test('live super role, disabled and session checks protect lookup/grant/revoke/list; claims and audit survive',async()=>{
 const h=fixture(),handle=createHandler(h.deps,t=>verifyFirebase(t,local));
 for(const uid of ['normal','mod'])for(const action of ['moderationLookupUser','moderationGrantModerator','moderationRevokeModerator','moderationListModerators']){
  const t=await token({super_admin:uid==='mod'?false:undefined},{uid});ok((await handle(request(action,{target_uid:'target'},t))).status===403);
 }
 const t=await token();
 ok((await handle(request('moderationLookupUser',{email:'target@example.com'},t))).status===200);
 ok((await handle(request('moderationLookupUser',{uid:'target'},t))).status===200);
 ok((await handle(request('moderationGrantModerator',{target_uid:'target'},t))).status===200);
 ok(h.users.get('target').customClaims.moderation_admin===true&&h.users.get('target').customClaims.other==='keep');
 ok((await handle(request('moderationListModerators',{},t))).status===200);
 ok((await handle(request('moderationRevokeModerator',{target_uid:'target'},t))).status===200);
 ok(h.users.get('target').customClaims.moderation_admin===undefined&&h.users.get('target').customClaims.league_operator===true);
 ok(h.revoked.includes('target'));
 const audit=[...h.rows.values()].filter((v:any)=>v.action==='REVOKE');ok(audit.some((v:any)=>v.actor_uid==='super'&&v.target_uid==='target'&&v.status==='COMPLETED'));
 for(const action of ['moderationGrantModerator','moderationRevokeModerator'])ok((await handle(request(action,{target_uid:'super'},t))).status===409);
 ok((await handle(request('bootstrap-super-admin',{},t))).status===400);
 h.users.get('super').disabled=true;ok((await handle(request('moderationListModerators',{},t))).status===403);
 h.users.get('super').disabled=false;h.users.get('super').tokensValidAfterTime='2040-01-01T00:00:00Z';ok((await handle(request('moderationListModerators',{},t))).status===403);
 h.users.get('super').tokensValidAfterTime=undefined;h.users.get('super').customClaims={};ok((await handle(request('moderationListModerators',{},t))).status===403);
});
Deno.test('server OAuth assertion signed and scoped, cached, and Admin REST claim merge/revoke payload',async()=>{
 let exchanges=0;const privateKey=await exportPKCS8(keys.privateKey);
 const fetcher=async(_url:any,init:any)=>{
  exchanges++;const form=new URLSearchParams(init.body);const {payload}=await jwtVerify(form.get('assertion')!,keys.publicKey,{audience:'https://oauth2.googleapis.com/token'});
  ok(payload.iss==='server@example.test'&&payload.scope==='https://www.googleapis.com/auth/cloud-platform');
  return new Response(JSON.stringify({access_token:'memory-only-test-token',expires_in:3600}),{headers:{'Content-Type':'application/json'}});
 };
 const access=createOAuth('server@example.test',privateKey,fetcher as typeof fetch);await Promise.all([access(),access()]);ok(exchanges===1);
 const writes:any[]=[];const auth=createAuthApi(async(url,body:any)=>{writes.push({url,body});return url.endsWith('lookup')?{users:[{localId:'u',customAttributes:'{"other":true}',validSince:'100'}]}:{};});
 ok((await auth.getUser('u')).customClaims.other===true);await auth.setCustomUserClaims('u',{other:true,moderation_admin:true});await auth.revokeRefreshTokens('u');
 ok(writes[1].body.customAttributes==='{"other":true,"moderation_admin":true}');ok(writes[2].body.validSince);ok(writes.every(w=>w.url.includes('/projects/zeytin-defteri/')));
});
Deno.test('bounded per-caller mutation throttling and safe internal errors',async()=>{
 const limit=createLimiter(()=>0);for(let i=0;i<10;i++)limit('super','moderationGrantModerator');let denied=false;try{limit('super','moderationRevokeModerator');}catch{denied=true;}ok(denied);
 const h=fixture();h.auth.getUser=async()=>{throw Error('PRIVATE KEY SHOULD NOT LEAK');};
 const response=await createHandler(h.deps,t=>verifyFirebase(t,local))(request('moderationListModerators',{},await token()));ok(!(await response.text()).includes('PRIVATE KEY'));
});
