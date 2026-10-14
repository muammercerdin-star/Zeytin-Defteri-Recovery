// Port of 06.3E service; wire schema and cursor remain unchanged.
const STATUSES=['OPEN','REVIEWED','ACTIONED','REJECTED'];
const CLAIM='moderation_admin';
function fail(code,message){const e=new Error(message);e.code=code;throw e;}
function object(data,allowed){
 if(!data||typeof data!=='object'||Array.isArray(data)||Object.keys(data).some(k=>!allowed.includes(k)))fail('invalid-argument','Unexpected request fields');
}
function status(value){if(!STATUSES.includes(value))fail('invalid-argument','Invalid report status');return value;}
function reportPath(reporterUid,reportId){
 if(typeof reporterUid!=='string'||! /^[A-Za-z0-9_-]{1,128}$/.test(reporterUid)
 ||typeof reportId!=='string'||! /^(?:(?:POST|COMMENT|REPLY)\|[A-Za-z0-9_-]{1,128}\|[A-Za-z0-9_-]{1,128}|USER\|\|[A-Za-z0-9_-]{1,128})$/.test(reportId))fail('invalid-argument','Invalid report identity');
 return `reports/${reporterUid}/items/${reportId}`;
}
function parsePath(path){
 const parts=typeof path==='string'?path.split('/'):[];
 if(parts.length!==4||parts[0]!=='reports'||parts[2]!=='items')fail('invalid-argument','Invalid report cursor');
 reportPath(parts[1],parts[3]);return parts;
}
async function authorize(auth,getUser,claim=CLAIM){
 if(!auth||!auth.uid)fail('unauthenticated','Sign in required');
 if(auth.token?.[claim]!==true)fail('permission-denied','Moderator role required');
 let user;try{user=await getUser(auth.uid);}catch(e){fail('permission-denied','Moderator account unavailable');}
 if(!user||user.disabled||user.customClaims?.[claim]!==true)fail('permission-denied','Moderator role revoked or disabled');
 const validAfter=Date.parse(user.tokensValidAfterTime||'');
 if(Number.isFinite(validAfter)&&(!Number.isFinite(auth.token.auth_time)||auth.token.auth_time<Math.floor(validAfter/1000)))
  fail('permission-denied','Session revoked; sign in again');
 return auth.uid;
}
function iso(value){return value&&typeof value.toDate==='function'?value.toDate().toISOString():null;}
function summary(doc){
 const parts=parsePath(doc.ref.path),d=doc.data();
 if(!d||d.reporter_uid!==parts[1]||!STATUSES.includes(d.status))fail('failed-precondition','Malformed report record');
 return {reporter_uid:d.reporter_uid,report_id:parts[3],reported_uid:d.reported_uid,
  target_type:d.target_type,target_id:d.target_id,post_cloud_id:d.post_cloud_id,
  comment_cloud_id:d.comment_cloud_id||'',reason:d.reason,status:d.status,created_at:iso(d.created_at),
  reviewed_at:iso(d.reviewed_at),reviewed_by:d.reviewed_by||null};
}
function validTime(t){return t&&Number.isInteger(t.seconds)&&t.seconds>=-62135596800&&t.seconds<=253402300799
 &&Number.isInteger(t.nanoseconds)&&t.nanoseconds>=0&&t.nanoseconds<1000000000;}
function readCursor(c,wanted){
 object(c,['version','status','created_at','path']);
 if(c.version!==1||c.status!==wanted)fail('invalid-argument','Invalid cursor version or status');
 parsePath(c.path);object(c.created_at,['seconds','nanoseconds']);
 if(!validTime(c.created_at))fail('invalid-argument','Invalid cursor timestamp');
 return c;
}
function cursorFor(doc,wanted){const t=doc.data().created_at;
 return {version:1,status:wanted,created_at:{seconds:t.seconds,nanoseconds:t.nanoseconds},path:doc.ref.path};
}
// Moderatör içerik kaldırma: hedef istemciden alınmaz, şikâyet kaydından türetilir. Böylece
// moderatör yalnızca şikâyet edilmiş içeriği kaldırabilir; belge silinmez, is_active=0 olur.
function contentId(value){if(typeof value!=='string'||! /^[A-Za-z0-9_-]{1,128}$/.test(value))fail('failed-precondition','Report has no content target');return value;}
function contentPath(report){
 if(report.target_type==='POST')return 'social_posts/'+contentId(report.post_cloud_id);
 if(report.target_type==='COMMENT'||report.target_type==='REPLY')
  return 'social_posts/'+contentId(report.post_cloud_id)+'/comments/'+contentId(report.comment_cloud_id);
 fail('failed-precondition','Report has no content target');
}
function activeValue(value){return value===undefined||value===null?true:Number(value)!==0;}
function millis(value){return Number.isFinite(value)&&value>0?Math.trunc(value):null;}
function createService({db,getUser,documentId,serverTimestamp,timestamp}){
 return {
  async list(request){
   await authorize(request.auth,getUser);
   const d=request.data||{};object(d,['status','limit','cursor']);
   const wanted=status(d.status===undefined?'OPEN':d.status);
   const limit=d.limit===undefined?25:d.limit;
   if(!Number.isInteger(limit)||limit<1||limit>100)fail('invalid-argument','Limit must be 1..100');
   const cursor=d.cursor===undefined?null:readCursor(d.cursor,wanted);
   const base=db.collectionGroup('items').where('status','==',wanted)
    .orderBy('created_at','desc').orderBy(documentId(),'desc');
   let query=cursor?base.startAfter(timestamp(cursor.created_at.seconds,cursor.created_at.nanoseconds),db.doc(cursor.path)):base;
   const docs=[],batchSize=limit+1;
   // Other trees also use "items". Filter their paths before serializing, and
   // keep scanning so unrelated/invalid-date rows cannot truncate a report page.
   // Missing created_at fields are excluded by Firestore orderBy (no migration).
   while(docs.length<=limit){
    const snapshot=await query.limit(batchSize).get();
    for(const doc of snapshot.docs){
     try{parsePath(doc.ref.path);}catch(e){continue;}
     const t=doc.data().created_at;
     if(!validTime(t)||typeof t.toDate!=='function')continue;
     docs.push(doc);if(docs.length>limit)break;
    }
    if(docs.length>limit||snapshot.docs.length<batchSize)break;
    query=base.startAfter(snapshot.docs[snapshot.docs.length-1]);
   }
   return {reports:docs.slice(0,limit).map(summary),next_cursor:docs.length>limit?cursorFor(docs[limit-1],wanted):null};
  },
  async update(request){
   const uid=await authorize(request.auth,getUser);
   const d=request.data;object(d,['reporter_uid','report_id','status','expected_status']);
   const path=reportPath(d.reporter_uid,d.report_id),next=status(d.status);
   if(d.expected_status!==undefined)status(d.expected_status);
   const ref=db.doc(path);
   await db.runTransaction(async tx=>{
    const doc=await tx.get(ref);if(!doc.exists)fail('not-found','Report not found');
    const before=summary(doc);
    if(d.expected_status!==undefined&&before.status!==d.expected_status)fail('failed-precondition','Report status changed; reload');
    tx.update(ref,{status:next,reviewed_by:uid,reviewed_at:serverTimestamp()});
   });
   return {reporter_uid:d.reporter_uid,report_id:d.report_id,status:next,reviewed_by:uid};
  },
  async content(request){
   await authorize(request.auth,getUser);
   const d=request.data;object(d,['reporter_uid','report_id']);
   const report=await db.doc(reportPath(d.reporter_uid,d.report_id)).get();
   if(!report.exists)fail('not-found','Report not found');
   const before=summary(report),path=contentPath(before);
   const target=await db.doc(path).get();
   if(!target.exists)return {target_type:before.target_type,exists:false,is_active:false,content:'',author_name:'',author_uid:'',created_at:null,moderated:false};
   const t=target.data()||{};
   return {target_type:before.target_type,exists:true,is_active:activeValue(t.is_active),
    content:typeof t.content==='string'?t.content.slice(0,5000):'',author_name:typeof t.user_name==='string'?t.user_name.slice(0,200):'',
    author_uid:typeof t.user_id==='string'?t.user_id:'',created_at:millis(t.created_at),moderated:typeof t.moderated_by==='string'&&t.moderated_by.length>0};
  },
  async remove(request){
   const uid=await authorize(request.auth,getUser);
   const d=request.data;object(d,['reporter_uid','report_id','expected_status']);
   const path=reportPath(d.reporter_uid,d.report_id);
   if(d.expected_status!==undefined)status(d.expected_status);
   const reportRef=db.doc(path),audit=db.collection('moderation_content_audit').doc();
   return db.runTransaction(async tx=>{
    const report=await tx.get(reportRef);if(!report.exists)fail('not-found','Report not found');
    const before=summary(report);
    if(d.expected_status!==undefined&&before.status!==d.expected_status)fail('failed-precondition','Report status changed; reload');
    const targetPath=contentPath(before),targetRef=db.doc(targetPath),target=await tx.get(targetRef);
    if(!target.exists)fail('not-found','Content not found');
    const t=target.data()||{};
    // Şikâyet kaydı başka bir kişinin içeriğini göstermemeli.
    if(t.user_id!==before.reported_uid)fail('failed-precondition','Report does not match content owner');
    const wasActive=activeValue(t.is_active);
    if(wasActive)tx.update(targetRef,{is_active:0,updated_at:Date.now(),moderated_by:uid,moderated_at:serverTimestamp()});
    tx.update(reportRef,{status:'ACTIONED',reviewed_by:uid,reviewed_at:serverTimestamp()});
    tx.set(audit,{action:'REMOVE_CONTENT',actor_uid:uid,target_uid:before.reported_uid,target_type:before.target_type,
     target_path:targetPath,report_path:path,was_active:wasActive,timestamp:serverTimestamp()});
    return {reporter_uid:d.reporter_uid,report_id:d.report_id,status:'ACTIONED',removed:wasActive,reviewed_by:uid};
   });
  }
 };
}
export {createService,authorize,CLAIM,STATUSES};
