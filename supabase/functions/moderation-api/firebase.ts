import {createRemoteJWKSet,jwtVerify,importPKCS8,SignJWT} from 'npm:jose@5.10.0';
export const PROJECT='zeytin-defteri';
export const SUPABASE_ORIGIN='https://vvazefsdejtzwzvcfgsc.supabase.co';
export class ServiceError extends Error {constructor(public code:string){super(code);}}
const jwks=createRemoteJWKSet(new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com'),{timeoutDuration:10000});
export async function verifyFirebase(token:string,keys:Parameters<typeof jwtVerify>[1]=jwks){
 try{
  const {payload:p}=await jwtVerify(token,keys,{algorithms:['RS256'],issuer:`https://securetoken.google.com/${PROJECT}`,audience:PROJECT,requiredClaims:['exp','iat','auth_time','sub','iss','aud']});
  const now=Math.floor(Date.now()/1000),f=p.firebase as Record<string,unknown>|undefined;
  if(p.aud!==PROJECT||typeof p.sub!=='string'||!p.sub.length||p.sub.length>128||p.sub.trim()!==p.sub||/[\x00-\x1f\x7f]/.test(p.sub)
   ||!Number.isInteger(p.iat)||Number(p.iat)>now||Number(p.iat)<0||!Number.isInteger(p.auth_time)||Number(p.auth_time)>now||Number(p.auth_time)<0
   ||(p.user_id!==undefined&&p.user_id!==p.sub)||f?.tenant!==undefined||f?.sign_in_provider==='anonymous')throw Error();
  return {uid:p.sub,token:p};
 }catch{throw new ServiceError('UNAUTHENTICATED');}
}
export type Fetcher=typeof fetch;
export function createOAuth(email:string,privateKey:string,fetcher:Fetcher=fetch){
 let cached='',expires=0,pending:Promise<string>|null=null;
 return async():Promise<string>=>{
  if(cached&&Date.now()<expires)return cached;
  if(pending)return pending;
  pending=(async()=>{
   const key=await importPKCS8(privateKey.replace(/\\n/g,'\n'),'RS256');
   const assertion=await new SignJWT({scope:'https://www.googleapis.com/auth/cloud-platform'})
    .setProtectedHeader({alg:'RS256',typ:'JWT'}).setIssuer(email).setSubject(email)
    .setAudience('https://oauth2.googleapis.com/token').setIssuedAt().setExpirationTime('1h').sign(key);
   const r=await fetcher('https://oauth2.googleapis.com/token',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},
    body:new URLSearchParams({grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',assertion}),redirect:'error',signal:AbortSignal.timeout(15000)});
   if(!r.ok)throw new ServiceError('INTERNAL');
   const v=await r.json();if(typeof v.access_token!=='string'||!v.access_token||!Number.isFinite(v.expires_in)||v.expires_in<=60)throw new ServiceError('INTERNAL');
   cached=v.access_token;expires=Date.now()+(Math.min(v.expires_in,3600)-60)*1000;return cached;
  })();
  try{return await pending;}finally{pending=null;}
 };
}
export function createGoogleClient(accessToken:()=>Promise<string>,fetcher:Fetcher=fetch){
 return async(url:string,body:unknown):Promise<any>=>{
  const response=await fetcher(url,{method:'POST',headers:{Authorization:`Bearer ${await accessToken()}`,'Content-Type':'application/json'},body:JSON.stringify(body),redirect:'error',signal:AbortSignal.timeout(15000)});
  const data=await response.json();
  if(!response.ok){
   const status=data?.error?.status;
   const code=status==='ABORTED'?'ABORTED':status==='NOT_FOUND'?'not-found':status==='ALREADY_EXISTS'?'failed-precondition':'INTERNAL';
   throw new ServiceError(code);
  }
  return data;
 };
}
export function createAuthApi(post:(url:string,body:unknown)=>Promise<any>,base=`https://identitytoolkit.googleapis.com/v1/projects/${PROJECT}`){
 async function lookup(body:unknown){
  const data=await post(base+'/accounts:lookup',body),u=data.users?.[0];
  if(!u)throw new ServiceError('auth/user-not-found');
  const claims=JSON.parse(u.customAttributes||'{}');
  if(!claims||typeof claims!=='object'||Array.isArray(claims)||!u.localId)throw new ServiceError('INTERNAL');
  const valid=u.validSince===undefined?0:Number(u.validSince);if(!Number.isFinite(valid)||valid<0)throw new ServiceError('INTERNAL');
  return {uid:u.localId,email:u.email,displayName:u.displayName,disabled:u.disabled===true,customClaims:claims,tokensValidAfterTime:new Date(valid*1000).toISOString()};
 }
 return {
  getUser:async(uid:string)=>{const u=await lookup({localId:[uid]});if(u.uid!==uid)throw new ServiceError('INTERNAL');return u;},
  getUserByEmail:(email:string)=>lookup({email:[email]}),
  setCustomUserClaims:async(uid:string,claims:Record<string,unknown>)=>{
   const serialized=JSON.stringify(claims);if(new TextEncoder().encode(serialized).length>1000)throw new ServiceError('failed-precondition');
   await post(base+'/accounts:update',{localId:uid,customAttributes:serialized});
  },
  revokeRefreshTokens:async(uid:string)=>{await post(base+'/accounts:update',{localId:uid,validSince:String(Math.floor(Date.now()/1000))});}
 };
}
