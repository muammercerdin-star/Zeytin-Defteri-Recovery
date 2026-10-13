'use strict';
const {onSchedule}=require('firebase-functions/v2/scheduler');
const {onTaskDispatched}=require('firebase-functions/v2/tasks');
const {defineSecret}=require('firebase-functions/params');
const {getFirestore,FieldPath,Timestamp}=require('firebase-admin/firestore');
const {getFunctions}=require('firebase-admin/functions');
const {createRuntime}=require('./runtime');
const secret=defineSecret('ZEYTIN_LEAGUE_ID_SECRET');
const region='europe-west1';
function runtime(){return createRuntime({db:getFirestore(),FieldPath,Timestamp,identitySecret:()=>secret.value(),
  enqueue:data=>getFunctions().taskQueue(`locations/${region}/functions/zeytinLeagueWorker`).enqueue(data,{dispatchDeadlineSeconds:1800})});}
exports.zeytinLeagueSchedule=onSchedule({schedule:'0 */6 * * *',timeZone:'Europe/Istanbul',region,
  timeoutSeconds:300,memory:'256MiB',maxInstances:1,retryCount:3},()=>runtime().schedule());
// One dispatch at a time makes resumable chunk writes deterministic on retries.
// IAM-authenticated task queue only; this is not a public callable HTTP API.
exports.zeytinLeagueWorker=onTaskDispatched({region,timeoutSeconds:1800,memory:'2GiB',maxInstances:1,concurrency:1,
  secrets:[secret],retryConfig:{maxAttempts:8,minBackoffSeconds:30,maxBackoffSeconds:300},
  rateLimits:{maxConcurrentDispatches:1,maxDispatchesPerSecond:1}},request=>runtime().work(request.data));

// SDK verifies the Firebase ID token; the service rechecks the current server role.
const {onCall,HttpsError}=require('firebase-functions/v2/https');
const {getAuth}=require('firebase-admin/auth');
const {createApproval,ApprovalError}=require('./approval');
exports.zeytinLeagueApproveSale=onCall({region,timeoutSeconds:60,memory:'256MiB',maxInstances:2,concurrency:4},async request=>{
  try{return await createApproval({db:getFirestore(),getUser:uid=>getAuth().getUser(uid)}).execute(request);}
  catch(e){if(e instanceof ApprovalError)throw new HttpsError(e.code,e.message);
    throw new HttpsError('internal','Approval could not be completed; retry with the same request ID');}
});
