'use strict';
const {createRoleService}=require('../moderation/roles');
function fixture(){
 const rows=new Map(),users=new Map(),writes=[],revoked=[];let auto=0,failCommit=false;
 const snap=ref=>({id:ref.id,ref,exists:rows.has(ref.path),data:()=>rows.get(ref.path)});
 const ref=(collection,id)=>({id,path:collection+'/'+id});
 const makeBatch=()=>{const ops=[];const b={set:(r,d)=>{ops.push(()=>rows.set(r.path,d));return b;},
  update:(r,d)=>{ops.push(()=>rows.set(r.path,{...rows.get(r.path),...d}));return b;},
  delete:r=>{ops.push(()=>rows.delete(r.path));return b;},
  commit:async()=>{if(failCommit)throw Error('commit failed');for(const op of ops)op();},get:async r=>snap(r)};return b;};
 const db={collection:name=>({doc:(id)=>ref(name,id||'audit'+(++auto)),orderBy:()=>{
  let cursor='',limit=100;const q={startAfter:id=>{cursor=id;return q;},limit:n=>{limit=n;return q;},get:async()=>({docs:[...rows.keys()].filter(p=>p.startsWith(name+'/')&&p.slice(name.length+1)>cursor).sort().slice(0,limit).map(p=>snap(ref(name,p.slice(name.length+1))))})};return q;
 }}),batch:makeBatch,runTransaction:async fn=>{const b=makeBatch();await fn(b);await b.commit();}};
 const auth={getUser:async id=>{if(!users.has(id)){const e=Error('missing');e.code='auth/user-not-found';throw e;}return structuredClone(users.get(id));},
  getUserByEmail:async email=>{const u=[...users.values()].find(u=>u.email===email);return auth.getUser(u?.uid);},
  setCustomUserClaims:async(id,c)=>{writes.push({id,claims:c});users.get(id).customClaims={...c};},
  revokeRefreshTokens:async id=>revoked.push(id),listUsers:async()=>({users:[...users.values()]})};
 const deps={db,auth,documentId:()=>'__name__',serverTimestamp:()=>({server:true})};
 const seed=(id,claims={},extra={})=>users.set(id,{uid:id,email:id+'@example.com',customClaims:claims,...extra});
 seed('super',{super_admin:true,moderation_admin:true});seed('mod',{moderation_admin:true});seed('normal');seed('target',{league_operator:true,other:'keep'});
 const request=(data={},id='super',token)=>({auth:{uid:id,token:token||{...users.get(id)?.customClaims,auth_time:2000000000}},data});
 return {deps,db,auth,users,rows,writes,revoked,seed,request,service:createRoleService(deps),failCommit:()=>{failCommit=true;}};
}
module.exports={fixture};
