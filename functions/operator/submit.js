'use strict';
// A small operator-only client. Firebase ID token is read from a protected file,
// never supplied as a command-line token or printed. The server is authority.
async function main(args){
 const [projectId,commandFile,tokenFile,apply]=args;
 if(args.length!==4||!/^[a-z][a-z0-9-]{4,62}$/.test(projectId)||apply!=='--apply')
  throw new Error('Usage: node operator/submit.js PROJECT_ID COMMAND_JSON TOKEN_FILE --apply');
 const fs=require('node:fs');
 if(fs.statSync(commandFile).size>16384||fs.statSync(tokenFile).size>16384)throw new Error('Input too large');
 const data=JSON.parse(fs.readFileSync(commandFile,'utf8')),token=fs.readFileSync(tokenFile,'utf8').trim();
 if(!/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(token))throw new Error('Firebase ID token required');
 const r=await fetch(`https://europe-west1-${projectId}.cloudfunctions.net/zeytinLeagueApproveSale`,{
  method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+token},body:JSON.stringify({data}),signal:AbortSignal.timeout(65000)});
 const result=await r.json();
 if(!r.ok||result.error)throw new Error(result.error?.status||'Approval failed');
 console.log(JSON.stringify(result.result));
}
if(require.main===module)main(process.argv.slice(2)).catch(e=>{console.error(e.message);process.exitCode=1;});
module.exports={main};
