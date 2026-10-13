'use strict';
// Run only by the project administrator in a trusted environment with ADC.
// No credentials are included, and nothing runs when this module is imported.
async function main(args){
 const [projectId,uid,action,apply]=args;
 if(args.length!==4||!/^[a-z][a-z0-9-]{4,62}$/.test(projectId)||!uid||uid.includes('/')
   ||!['grant','revoke'].includes(action)||apply!=='--apply')
  throw new Error('Usage: node operator/role.js PROJECT_ID OPERATOR_UID grant|revoke --apply');
 const {initializeApp,deleteApp}=require('firebase-admin/app'),{getAuth}=require('firebase-admin/auth');
 const app=initializeApp({projectId});
 try{
  const auth=getAuth(app),user=await auth.getUser(uid),claims={...user.customClaims};
  if(action==='grant'){if(user.disabled)throw new Error('Disabled operator account');claims.league_operator=true;}
  else delete claims.league_operator;
  await auth.setCustomUserClaims(uid,claims);
  if(action==='revoke')await auth.revokeRefreshTokens(uid);
  console.log('Operator role updated. Existing unrelated claims preserved.');
 }finally{await deleteApp(app);}
}
if(require.main===module)main(process.argv.slice(2)).catch(e=>{console.error(e.message);process.exitCode=1;});
module.exports={main};
