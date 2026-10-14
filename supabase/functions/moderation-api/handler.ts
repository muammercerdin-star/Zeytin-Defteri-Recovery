import {ServiceError} from './firebase.ts';
import {createService} from './service.mjs';
import {createRoleService} from './roles.mjs';
const codes:Record<string,[number,string]>={
 'unauthenticated':[401,'UNAUTHENTICATED'],UNAUTHENTICATED:[401,'UNAUTHENTICATED'],
 'permission-denied':[403,'FORBIDDEN'],FORBIDDEN:[403,'FORBIDDEN'],
 'invalid-argument':[400,'INVALID_ARGUMENT'],INVALID_ARGUMENT:[400,'INVALID_ARGUMENT'],
 'not-found':[404,'NOT_FOUND'],'auth/user-not-found':[404,'NOT_FOUND'],
 'failed-precondition':[409,'CONFLICT'],ABORTED:[409,'CONFLICT'],
 RATE_LIMITED:[429,'RATE_LIMITED'],METHOD_NOT_ALLOWED:[405,'METHOD_NOT_ALLOWED'],BODY_TOO_LARGE:[413,'INVALID_ARGUMENT']
};
function json(status:number,body:unknown){return new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});}
export function createLimiter(now=()=>Date.now()){
 const buckets=new Map<string,{start:number,count:number}>();
 return (uid:string,action:string)=>{
  const time=now();for(const [key,b] of buckets)if(time-b.start>=60000)buckets.delete(key);
  const key=uid+':'+(['moderationGrantModerator','moderationRevokeModerator','moderationRemoveContent'].includes(action)?'write':'read');
  let bucket=buckets.get(key);
  if(!bucket){if(buckets.size>=2048)throw new ServiceError('RATE_LIMITED');bucket={start:time,count:0};buckets.set(key,bucket);}
  if(++bucket.count>(key.endsWith(':write')?10:60))throw new ServiceError('RATE_LIMITED');
 };
}
async function body(request:Request){
 const rawLength=request.headers.get('content-length');
 if(rawLength&&(!/^\d+$/.test(rawLength)||Number(rawLength)>8192))throw new ServiceError('BODY_TOO_LARGE');
 if(request.headers.get('content-type')?.split(';')[0].trim().toLowerCase()!=='application/json')throw new ServiceError('INVALID_ARGUMENT');
 const reader=request.body?.getReader();if(!reader)throw new ServiceError('INVALID_ARGUMENT');
 const chunks:Uint8Array[]=[];let size=0;
 try{while(true){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>8192){await reader.cancel();throw new ServiceError('BODY_TOO_LARGE');}chunks.push(value);}}finally{reader.releaseLock();}
 const bytes=new Uint8Array(size);let pos=0;for(const c of chunks){bytes.set(c,pos);pos+=c.length;}
 try{return JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(bytes));}catch{throw new ServiceError('INVALID_ARGUMENT');}
}
export function createHandler(deps:any,verify:(token:string)=>Promise<any>,limit=createLimiter()){
 const reports=createService({...deps,getUser:(uid:string)=>deps.auth.getUser(uid)}),roles=createRoleService(deps);
 const routes:Record<string,(r:any)=>Promise<any>>={
  moderationListReports:reports.list,moderationUpdateReportStatus:reports.update,
  moderationGetReportedContent:reports.content,moderationRemoveContent:reports.remove,
  moderationLookupUser:roles.lookup,moderationGrantModerator:roles.grant,
  moderationRevokeModerator:roles.revoke,moderationListModerators:roles.list
 };
 return async(request:Request)=>{
  try{
   if(request.method!=='POST')throw new ServiceError('METHOD_NOT_ALLOWED');
   const bearer=request.headers.get('authorization')||'';
   if(!/^Bearer [A-Za-z0-9_.-]{1,8192}$/.test(bearer))throw new ServiceError('UNAUTHENTICATED');
   const input=await body(request);
   if(!input||typeof input!=='object'||Array.isArray(input)||Object.keys(input).some(k=>!['action','data'].includes(k))
    ||typeof input.action!=='string'||!Object.hasOwn(routes,input.action)||!input.data||typeof input.data!=='object'||Array.isArray(input.data))throw new ServiceError('INVALID_ARGUMENT');
   const auth=await verify(bearer.slice(7));limit(auth.uid,input.action);
   return json(200,{data:await routes[input.action]({auth,data:input.data})});
  }catch(e){const pair=codes[(e as ServiceError)?.code]||[500,'INTERNAL'];return json(pair[0] as number,{error:{code:pair[1]}});}
 };
}
