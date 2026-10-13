import {PROJECT,SUPABASE_ORIGIN,createOAuth,createGoogleClient,createAuthApi,verifyFirebase} from './firebase.ts';
import {RestFirestore,Time,serverTimestamp} from './firestore.ts';
import {createHandler} from './handler.ts';
// Only server-side secrets. No Google service credential is bundled with this function.
let handler:((request:Request)=>Promise<Response>)|undefined;
function configuredHandler(){
 if(handler)return handler;
 const project=Deno.env.get('FIREBASE_PROJECT_ID'),email=Deno.env.get('FIREBASE_CLIENT_EMAIL')||'',key=Deno.env.get('FIREBASE_PRIVATE_KEY')||'';
 if(project!==PROJECT||Deno.env.get('SUPABASE_URL')!==SUPABASE_ORIGIN||!email.endsWith('@'+PROJECT+'.iam.gserviceaccount.com')||!key.includes('BEGIN PRIVATE KEY'))throw Error('Configuration unavailable');
 const post=createGoogleClient(createOAuth(email,key));
 handler=createHandler({db:new RestFirestore(post),auth:createAuthApi(post),documentId:()=>'__name__',serverTimestamp,timestamp:(s:number,n:number)=>new Time(s,n)},verifyFirebase);
 return handler;
}
Deno.serve(async request=>{
 try{return await configuredHandler()(request);}
 catch{return new Response(JSON.stringify({error:{code:'INTERNAL'}}),{status:500,headers:{'Content-Type':'application/json','Cache-Control':'no-store'}});}
});
