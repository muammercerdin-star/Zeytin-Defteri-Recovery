'use strict';
// Moderatör içerik kaldırma (moderationGetReportedContent / moderationRemoveContent).
// Gerçek service.js ile bellekte çalışır; Firestore emülatörü veya canlı proje kullanmaz.
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {createService}=require('../moderation/service');
const auth={uid:'mod',token:{moderation_admin:true,auth_time:2000000000}};
const code=c=>e=>e.code===c;
function fixture(){
 const rows=new Map(),writes=[],stamp={server:true};let user={customClaims:{moderation_admin:true}},auto=0;
 const snap=p=>({ref:{path:p},exists:rows.has(p),data:()=>rows.get(p)});
 const ref=p=>({path:p,get:async()=>snap(p)});
 const db={doc:p=>ref(p),collection:name=>({doc:()=>ref(name+'/a'+(++auto))}),
  runTransaction:async fn=>{
   const ops=[];
   const result=await fn({get:async r=>snap(r.path),
    update:(r,d)=>{if(!rows.has(r.path))throw Error('missing doc');ops.push(()=>rows.set(r.path,{...rows.get(r.path),...d}));writes.push({op:'update',path:r.path,data:d});},
    set:(r,d)=>{ops.push(()=>rows.set(r.path,d));writes.push({op:'set',path:r.path,data:d});}});
   for(const op of ops)op();return result;   // hata fırlarsa hiçbir yazım uygulanmaz (işlem bütünlüğü)
  }};
 const service=createService({db,getUser:async()=>user,documentId:()=>'__name__',serverTimestamp:()=>stamp,timestamp:()=>null});
 const report=(type,id,{post='p1',comment='',reported='B',state='OPEN',reporter='A'}={})=>{
  const p='reports/'+reporter+'/items/'+id;
  rows.set(p,{reporter_uid:reporter,reported_uid:reported,target_type:type,target_id:comment||post,post_cloud_id:post,
   comment_cloud_id:comment,reason:'ABUSE',status:state,created_at:{seconds:1,nanoseconds:0,toDate:()=>new Date(1000)}});return p;};
 rows.set('social_posts/p1',{cloud_id:'p1',user_id:'B',user_name:'Bora',content:'argo mesaj',is_active:1,created_at:1700000000000});
 rows.set('social_posts/p1/comments/c1',{cloud_id:'c1',user_id:'C',user_name:'Cem',content:'argo yanıt',is_active:1,created_at:1700000001000});
 rows.set('social_posts/legacy',{user_id:'B',user_name:'Bora',content:'eski'});  // is_active alanı yok
 return {service,rows,writes,stamp,report,user:u=>user=u};
}
const req=(data,who=auth)=>({auth:who,data});

test('yetkisiz, misafir, rolü geri alınmış ve kapatılmış hesap içerik okuyamaz/kaldıramaz; hiçbir yazım olmaz',async()=>{
 for(const who of [null,{uid:'A',token:{}},{uid:'A',token:{moderation_admin:'true'}}]){
  const h=fixture();h.report('POST','POST|p1|p1');
  for(const fn of ['content','remove'])await assert.rejects(h.service[fn](req({reporter_uid:'A',report_id:'POST|p1|p1'},who)),code(who?'permission-denied':'unauthenticated'));
  assert.equal(h.writes.length,0);assert.equal(h.rows.get('social_posts/p1').is_active,1);
 }
 for(const u of [{customClaims:{}},{disabled:true,customClaims:{moderation_admin:true}}]){
  const h=fixture();h.report('POST','POST|p1|p1');h.user(u);
  await assert.rejects(h.service.remove(req({reporter_uid:'A',report_id:'POST|p1|p1'})),code('permission-denied'));
  assert.equal(h.writes.length,0);
 }
});

test('moderatör şikâyet edilen gönderinin metnini görür; hedef istemciden alınmaz',async()=>{
 const h=fixture();h.report('POST','POST|p1|p1');
 const c=await h.service.content(req({reporter_uid:'A',report_id:'POST|p1|p1'}));
 assert.deepEqual(c,{target_type:'POST',exists:true,is_active:true,content:'argo mesaj',author_name:'Bora',author_uid:'B',created_at:1700000000000,moderated:false});
 // İstemci başka bir hedef adı gönderemez.
 await assert.rejects(h.service.content(req({reporter_uid:'A',report_id:'POST|p1|p1',post_cloud_id:'x'})),code('invalid-argument'));
 await assert.rejects(h.service.remove(req({reporter_uid:'A',report_id:'POST|p1|p1',target_path:'social_posts/x'})),code('invalid-argument'));
 // Kullanıcı şikâyetinde kaldırılacak içerik yok.
 h.report('USER','USER||B');
 await assert.rejects(h.service.content(req({reporter_uid:'A',report_id:'USER||B'})),code('failed-precondition'));
 await assert.rejects(h.service.remove(req({reporter_uid:'A',report_id:'USER||B'})),code('failed-precondition'));
 await assert.rejects(h.service.content(req({reporter_uid:'A',report_id:'POST|yok|yok'})),code('not-found'));
});

test('gönderiyi kaldırma: is_active=0, moderatör izi, şikâyet İşlem Yapıldı, denetim kaydı; belge silinmez',async()=>{
 const h=fixture(),p=h.report('POST','POST|p1|p1');
 const r=await h.service.remove(req({reporter_uid:'A',report_id:'POST|p1|p1',expected_status:'OPEN'}));
 assert.deepEqual(r,{reporter_uid:'A',report_id:'POST|p1|p1',status:'ACTIONED',removed:true,reviewed_by:'mod'});
 const post=h.rows.get('social_posts/p1');
 assert.equal(post.is_active,0);assert.equal(post.moderated_by,'mod');assert.equal(post.moderated_at,h.stamp);
 assert.ok(Number.isInteger(post.updated_at)&&post.updated_at>0);assert.equal(post.content,'argo mesaj');  // kanıt korunur
 assert.equal(h.rows.get(p).status,'ACTIONED');assert.equal(h.rows.get(p).reviewed_by,'mod');assert.equal(h.rows.get(p).reason,'ABUSE');
 const audit=[...h.rows.entries()].filter(([k])=>k.startsWith('moderation_content_audit/')).map(([,v])=>v);
 assert.equal(audit.length,1);
 assert.deepEqual({...audit[0],timestamp:null},{action:'REMOVE_CONTENT',actor_uid:'mod',target_uid:'B',target_type:'POST',
  target_path:'social_posts/p1',report_path:p,was_active:true,timestamp:null});
 const after=await h.service.content(req({reporter_uid:'A',report_id:'POST|p1|p1'}));
 assert.equal(after.is_active,false);assert.equal(after.moderated,true);
});

test('yanıtı kaldırma yalnızca o yanıtı kapatır; ana gönderi yayında kalır',async()=>{
 const h=fixture();h.report('REPLY','REPLY|p1|c1',{comment:'c1',reported:'C'});
 const r=await h.service.remove(req({reporter_uid:'A',report_id:'REPLY|p1|c1'}));
 assert.equal(r.removed,true);
 assert.equal(h.rows.get('social_posts/p1/comments/c1').is_active,0);assert.equal(h.rows.get('social_posts/p1').is_active,1);
});

test('eşzamanlılık ve tutarlılık: durum değişmişse, sahibi tutmuyorsa ya da içerik yoksa hiçbir şey yazılmaz',async()=>{
 let h=fixture();h.report('POST','POST|p1|p1',{state:'REVIEWED'});
 await assert.rejects(h.service.remove(req({reporter_uid:'A',report_id:'POST|p1|p1',expected_status:'OPEN'})),code('failed-precondition'));
 assert.equal(h.rows.get('social_posts/p1').is_active,1);assert.equal(h.writes.length,0);
 h=fixture();h.report('POST','POST|p1|p1',{reported:'Z'});   // şikâyet başka kişiyi gösteriyor
 await assert.rejects(h.service.remove(req({reporter_uid:'A',report_id:'POST|p1|p1'})),code('failed-precondition'));
 assert.equal(h.writes.length,0);
 h=fixture();h.report('POST','POST|gone|gone',{post:'gone'});
 await assert.rejects(h.service.remove(req({reporter_uid:'A',report_id:'POST|gone|gone'})),code('not-found'));
 assert.equal(h.writes.length,0);
 h=fixture();
 await assert.rejects(h.service.remove(req({reporter_uid:'A',report_id:'POST|p1|p1',expected_status:'BAD'})),code('invalid-argument'));
 await assert.rejects(h.service.remove(req({reporter_uid:'../x',report_id:'POST|p1|p1'})),code('invalid-argument'));
});

test('zaten kapalı içerik: içerik yeniden yazılmaz, şikâyet kapanır; is_active alanı olmayan eski gönderi kaldırılabilir',async()=>{
 const h=fixture();h.rows.get('social_posts/p1').is_active=0;h.report('POST','POST|p1|p1');
 const r=await h.service.remove(req({reporter_uid:'A',report_id:'POST|p1|p1'}));
 assert.equal(r.removed,false);assert.equal(h.rows.get('social_posts/p1').moderated_by,undefined);
 assert.equal(h.rows.get('reports/A/items/POST|p1|p1').status,'ACTIONED');
 h.report('POST','POST|legacy|legacy',{post:'legacy'});
 assert.equal((await h.service.content(req({reporter_uid:'A',report_id:'POST|legacy|legacy'}))).is_active,true);
 assert.equal((await h.service.remove(req({reporter_uid:'A',report_id:'POST|legacy|legacy'}))).removed,true);
 assert.equal(h.rows.get('social_posts/legacy').is_active,0);
});

test('Supabase kopyası Firebase kopyasıyla birebir aynı; yeni işlemler iki tarafta da bağlı',()=>{
 const root=path.resolve(__dirname,'../..');
 const js=fs.readFileSync(path.join(root,'functions/moderation/service.js'),'utf8').split('\n').slice(1).join('\n')
  .replace('module.exports={createService,authorize,CLAIM,STATUSES};','export {createService,authorize,CLAIM,STATUSES};');
 const mjs=fs.readFileSync(path.join(root,'supabase/functions/moderation-api/service.mjs'),'utf8').split('\n').slice(1).join('\n');
 assert.equal(mjs,js);
 const handler=fs.readFileSync(path.join(root,'supabase/functions/moderation-api/handler.ts'),'utf8');
 assert.match(handler,/moderationGetReportedContent:reports\.content,moderationRemoveContent:reports\.remove/);
 assert.match(handler,/'moderationRemoveContent'\]\.includes\(action\)\?'write'/);   // yazma sınırına tabi
 const fns=fs.readFileSync(path.join(root,'functions/moderation/functions.js'),'utf8');
 assert.match(fns,/exports\.moderationRemoveContent=endpoint\('remove'\)/);
 const client=fs.readFileSync(path.join(root,'app/src/main/java/com/zeytindefteri/app/ModerationBackendClient.java'),'utf8');
 for(const a of ['moderationGetReportedContent','moderationRemoveContent'])assert.ok(client.includes('"'+a+'"'));
 const rules=fs.readFileSync(path.join(root,'firestore.rules'),'utf8');
 assert.equal((rules.match(/resource\.data\.get\('moderated_by', ''\) == ''/g)||[]).length,2);
});
