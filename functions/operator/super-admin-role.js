'use strict';
// Trusted CLI only. No initialization or writes on import, no callable export.
const {configuredProject,guardError,safeError}=require('./project-guard');
const {runOperation}=require('./moderation-role');
function parse(args){
 const [action,...rest]=args;
 if(!['grant','status','revoke'].includes(action))guardError('Usage: super-admin-role.js grant|status|revoke --email EMAIL|--uid UID [--apply --confirm-project PROJECT_ID]');
 const flags={};
 for(let i=0;i<rest.length;i++){
  const flag=rest[i];
  if(!['--email','--uid','--apply','--confirm-project'].includes(flag)||Object.hasOwn(flags,flag))guardError('Unknown or duplicate option.');
  if(flag==='--apply')flags[flag]=true;
  else{const value=rest[++i];if(!value||value.startsWith('--'))guardError('Option value required.');flags[flag]=value;}
 }
 if((flags['--email']!==undefined)===(flags['--uid']!==undefined))guardError('Provide exactly one full email or UID.');
 if(flags['--email']!==undefined&&(flags['--email'].length>254||! /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(flags['--email'])))guardError('Full email required.');
 const uid=flags['--uid'];
 if(uid!==undefined&&(!uid.length||uid.length>128||uid.trim()!==uid||/[\x00-\x1f\x7f]/.test(uid)))guardError('Full UID required.');
 if(action==='status'&&(flags['--apply']||flags['--confirm-project']))guardError('Status is read-only; omit write options.');
 if(flags['--apply']&&!flags['--confirm-project'])guardError('--apply requires --confirm-project PROJECT_ID.');
 return {action,email:flags['--email'],uid,apply:flags['--apply']===true,confirmation:flags['--confirm-project']};
}
function status(user){return {uid:user.uid,email:user.email||null,disabled:user.disabled===true,super_admin:user.customClaims?.super_admin===true,moderation_admin:user.customClaims?.moderation_admin===true};}
async function main(args){
 const d=parse(args),project=configuredProject();
 if(d.confirmation!==undefined&&d.confirmation!==project)guardError('Confirmed project differs from the configured project.');
 console.log('Firebase project: '+project);
 console.log('Action: '+d.action.toUpperCase()+' SUPER ADMIN');
 const action={grant:'bootstrap-super-admin',status:'status-super-admin',revoke:'revoke-super-admin'}[d.action];
 const user=await runOperation(project,d.uid===undefined?'email:'+d.email:d.uid,action,{
  uid:d.uid,preview:d.action!=='status'&&!d.apply,
  inspect:u=>console.log('Target: '+JSON.stringify(status(u)))
 });
 if(d.action==='status'){console.log('Status: '+JSON.stringify(status(user)));return;}
 if(!d.apply){console.log('PREVIEW ONLY: no claims, tokens, audit or index changed. Use --apply --confirm-project '+project+' to perform this action.');return;}
 console.log('Completed: '+JSON.stringify(status(user)));
 console.log(d.action==='grant'?'Grant applied. Refresh ID token or reopen Settings; session was not revoked.':'Both admin claims removed; refresh sessions revoked. Sign in again on the device.');
}
if(require.main===module)main(process.argv.slice(2)).catch(e=>{console.error(safeError(e));process.exitCode=1;});
module.exports={main,parse,status};
