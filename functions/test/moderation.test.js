'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const {createService,authorize}=require('../moderation/service');
const auth={uid:'mod',token:{moderation_admin:true,auth_time:2000000000}};
function time(seconds=1789344000,nanoseconds=0){return {seconds,nanoseconds,toDate:()=>new Date(seconds*1000+nanoseconds/1e6)};}
function fixture(){
 const rows=new Map(),calls=[],stamp={server:true};let user={customClaims:{moderation_admin:true}};
 const snap=p=>({ref:{path:p},exists:rows.has(p),data:()=>rows.get(p)});
 const cmp=(a,b)=>a[0]===null?(b[0]===null?0:-1):b[0]===null?1:a[0].seconds-b[0].seconds||a[0].nanoseconds-b[0].nanoseconds||(a[1]<b[1]?-1:a[1]>b[1]?1:0);
 const db={doc:path=>({path}),collectionGroup:name=>{
  calls.push(name);
  const build=(spec={orders:[]})=>{
   const q={where:(k,op,v)=>{assert.equal(k,'status');assert.equal(op,'==');return build({...spec,status:v});},
    orderBy:(k,dir)=>build({...spec,orders:[...spec.orders,[k,dir]]}),
    startAfter:(...args)=>build({...spec,after:args.length===1?[args[0].data().created_at,args[0].ref.path]:[args[0],args[1].path]}),
    limit:n=>build({...spec,limit:n}),
    get:async()=>{
     assert.deepEqual(spec.orders,[['created_at','desc'],['__name__','desc']]);
     const keys=[...rows.keys()].filter(p=>rows.get(p).status===spec.status&&rows.get(p).created_at!==undefined);
     keys.sort((a,b)=>-cmp([rows.get(a).created_at,a],[rows.get(b).created_at,b]));
     return {docs:keys.filter(p=>!spec.after||cmp([rows.get(p).created_at,p],spec.after)<0).slice(0,spec.limit).map(snap)};
    }};return q;
  };return build();
 },runTransaction:async fn=>fn({get:async r=>snap(r.path),update:(r,d)=>{calls.push(d);rows.set(r.path,{...rows.get(r.path),...d});}})};
 const service=createService({db,getUser:async()=>user,documentId:()=>'__name__',serverTimestamp:()=>stamp,timestamp:time});
 const seed=(id='POST|p|p',state='OPEN',created=time(),owner='A')=>{const p='reports/'+owner+'/items/'+id;rows.set(p,{reporter_uid:owner,reported_uid:'B',target_type:'POST',target_id:'p',post_cloud_id:'p',reason:'SPAM',status:state,created_at:created});return p;};
 return {service,rows,calls,stamp,seed,user:u=>user=u};
}
const code=c=>e=>e.code===c;
test('normal/guest/league/string claims cannot list or update; no DB access',async()=>{
 for(const who of [null,{uid:'A',token:{}},{uid:'A',token:{league_operator:true}},{uid:'A',token:{moderation_admin:'true'}}]){
  const h=fixture();for(const fn of ['list','update'])await assert.rejects(h.service[fn]({auth:who,data:{}}),code(who?'permission-denied':'unauthenticated'));assert.equal(h.calls.length,0);
 }
});
test('live role revocation, disabled account and invalidated session deny old token',async()=>{
 for(const u of [{customClaims:{}},{disabled:true,customClaims:{moderation_admin:true}},{customClaims:{moderation_admin:true},tokensValidAfterTime:'2040-01-01T00:00:00Z'}]){
  const h=fixture();h.user(u);await assert.rejects(h.service.list({auth,data:{}}),code('permission-denied'));
 }
 await assert.rejects(authorize(auth,async()=>{throw Error();}),code('permission-denied'));
});
test('admin OPEN list includes date/identities, excludes unrelated items and pages stably',async()=>{
 const h=fixture();h.seed('POST|a|a');h.seed('POST|b|b');h.seed('POST|c|c','REJECTED');h.rows.set('users/A/items/private',{status:'OPEN'});
 const first=await h.service.list({auth,data:{limit:1}});assert.equal(first.reports.length,1);assert.ok(first.next_cursor);
 assert.equal(first.reports[0].created_at,'2026-09-14T00:00:00.000Z');assert.equal(first.reports[0].reported_uid,'B');
 const second=await h.service.list({auth,data:{limit:1,cursor:first.next_cursor}});assert.equal(second.reports[0].report_id,'POST|a|a');assert.equal(second.next_cursor,null);
 assert.equal((await h.service.list({auth,data:{status:'REJECTED'}})).reports.length,1);
});
test('invalid bounds/status/body roles/cursor cannot alter query scope',async()=>{
 for(const data of [{limit:0},{limit:101},{limit:1.5},{status:'BAD'},{admin:true},{cursor:'users/A/items/x'}])await assert.rejects(fixture().service.list({auth,data}),code('invalid-argument'));
});
test('admin can set all statuses; reviewer and time are server owned; content untouched',async()=>{
 const h=fixture(),p=h.seed();h.rows.set('social_posts/p',{content:'unchanged'});
 for(const status of ['REVIEWED','ACTIONED','REJECTED','OPEN']){
  const result=await h.service.update({auth,data:{reporter_uid:'A',report_id:'POST|p|p',status}});
  assert.equal(result.reviewed_by,'mod');assert.equal(h.rows.get(p).reviewed_at,h.stamp);assert.equal(h.rows.get(p).reason,'SPAM');assert.equal(h.rows.get(p).status,status);
 }
 assert.deepEqual(h.rows.get('social_posts/p'),{content:'unchanged'});
 assert.ok(h.calls.every(x=>Object.keys(x).sort().join(',')==='reviewed_at,reviewed_by,status'));
});
test('invalid status and caller-supplied reviewer/time rejected without writes',async()=>{
 for(const extra of [{status:'BAN'},{reviewed_by:'fake'},{reviewed_at:1},{reported_uid:'C'}]){
  const h=fixture(),p=h.seed();await assert.rejects(h.service.update({auth,data:{reporter_uid:'A',report_id:'POST|p|p',status:'REVIEWED',...extra}}),code('invalid-argument'));assert.equal(h.rows.get(p).status,'OPEN');
 }
});
test('missing record and stale expected_status never update',async()=>{
 const h=fixture();const data={reporter_uid:'A',report_id:'POST|p|p',status:'REVIEWED'};
 await assert.rejects(h.service.update({auth,data}),code('not-found'));h.seed();
 await assert.rejects(h.service.update({auth,data:{...data,expected_status:'ACTIONED'}}),code('failed-precondition'));
});
test('client rules still deny moderation access; existing report/block contracts retained',()=>{
 const rules=fs.readFileSync(require('node:path').join(__dirname,'../../firestore.rules'),'utf8');
 assert.ok(!rules.includes('moderation_admin'));assert.ok(rules.includes('allow list, update, delete: if false;'));
 assert.ok(rules.includes('allow create: if reportingUser() && validReport();'));assert.ok(rules.includes('match /user_blocks/{blockerUid}/items/{blockedUid}'));
});

test('newest first; timestamp nanoseconds and full path break ties without duplicate/omission',async()=>{
 const h=fixture();
 h.seed('POST|z|z','OPEN',time(100));
 h.seed('POST|a|a','OPEN',time(300));
 h.seed('POST|b|b','OPEN',time(200,2),'A');
 h.seed('POST|b|b','OPEN',time(200,2),'Z');
 h.seed('POST|c|c','OPEN',time(200,1));
 const expected=['reports/A/items/POST|a|a','reports/Z/items/POST|b|b','reports/A/items/POST|b|b','reports/A/items/POST|c|c','reports/A/items/POST|z|z'];
 for(const limit of [1,2,3,5,100]){
  let cursor,paths=[];
  do{const page=await h.service.list({auth,data:{limit,...(cursor?{cursor}: {})}});
   paths.push(...page.reports.map(x=>`reports/${x.reporter_uid}/items/${x.report_id}`));
   cursor=page.next_cursor;
   if(cursor){assert.equal(cursor.version,1);assert.equal(cursor.status,'OPEN');assert.ok(Number.isInteger(cursor.created_at.nanoseconds));}
  }while(cursor);
  assert.deepEqual(paths,expected);assert.equal(new Set(paths).size,5);
 }
});
test('each status filters independently and sorts descending',async()=>{
 const h=fixture();for(const state of ['OPEN','REVIEWED','ACTIONED','REJECTED']){
  h.seed('POST|old|'+state,state,time(100));h.seed('POST|new|'+state,state,time(200));
 }
 for(const status of ['OPEN','REVIEWED','ACTIONED','REJECTED']){
  const page=await h.service.list({auth,data:{status}});
  assert.deepEqual(page.reports.map(r=>r.report_id),['POST|new|'+status,'POST|old|'+status]);
  assert.ok(page.reports.every(r=>r.status===status));
 }
});
test('skips other items trees across batches; missing/null date does not crash',async()=>{
 const h=fixture();h.seed('POST|a|a','OPEN',time(100));h.seed('POST|b|b','OPEN',time(200));
 for(let i=0;i<8;i++)h.rows.set(`users/A/items/${i}`,{status:'OPEN',created_at:time(300+i)});
 const missing=h.seed('POST|missing|missing');delete h.rows.get(missing).created_at;
 // Invalid Timestamp-like legacy field is skipped, not migrated.
 h.seed('POST|invalid|invalid','OPEN',{seconds:400,nanoseconds:0});
 h.seed('POST|null|null','OPEN',null);
 const page=await h.service.list({auth,data:{limit:1}});
 assert.equal(page.reports[0].report_id,'POST|b|b');
 const next=await h.service.list({auth,data:{limit:1,cursor:page.next_cursor}});
 assert.equal(next.reports[0].report_id,'POST|a|a');assert.equal(next.next_cursor,null);
 assert.equal(h.rows.has(missing),true);
});
test('cursor survives boundary deletion and rejects legacy/invalid/mismatched status',async()=>{
 const h=fixture();h.seed('POST|a|a','OPEN',time(100));h.seed('POST|b|b','OPEN',time(200));
 const page=await h.service.list({auth,data:{limit:1}}),cursor=page.next_cursor;
 h.rows.delete(cursor.path);
 assert.equal((await h.service.list({auth,data:{cursor}})).reports[0].report_id,'POST|a|a');
 for(const bad of [null,cursor.path,{...cursor,version:0},{...cursor,status:'REVIEWED'},
  {...cursor,path:'users/A/items/private'}, {...cursor,created_at:{seconds:1,nanoseconds:1e9}},
  {...cursor,created_at:{seconds:1.5,nanoseconds:0}}, {...cursor,created_at:{seconds:253402300800,nanoseconds:0}}]){
  await assert.rejects(h.service.list({auth,data:{cursor:bad}}),code('invalid-argument'));
 }
});

test('chronological collection-group composite index is declared',()=>{
 const config=JSON.parse(fs.readFileSync(require('node:path').join(__dirname,'../../firestore.indexes.json'),'utf8'));
 assert.ok(config.indexes.some(i=>i.collectionGroup==='items'&&i.queryScope==='COLLECTION_GROUP'&&
 JSON.stringify(i.fields)===JSON.stringify([{fieldPath:'status',order:'ASCENDING'},{fieldPath:'created_at',order:'DESCENDING'},{fieldPath:'__name__',order:'DESCENDING'}])));
});
