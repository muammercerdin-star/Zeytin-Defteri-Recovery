'use strict';
const {onCall,HttpsError}=require('firebase-functions/v2/https');
const {getFirestore,FieldPath,FieldValue,Timestamp}=require('firebase-admin/firestore');
const {getAuth}=require('firebase-admin/auth');
const {createService}=require('./service');
const options={region:'europe-west1',maxInstances:3,timeoutSeconds:30,memory:'256MiB'};
function endpoint(method){return onCall(options,async request=>{
 try{
  const service=createService({db:getFirestore(),getUser:uid=>getAuth().getUser(uid),
   documentId:()=>FieldPath.documentId(),serverTimestamp:()=>FieldValue.serverTimestamp(),timestamp:(seconds,nanoseconds)=>new Timestamp(seconds,nanoseconds)});
  return await service[method](request);
 }catch(e){
  const safe=['unauthenticated','permission-denied','invalid-argument','not-found','failed-precondition'];
  if(safe.includes(e.code))throw new HttpsError(e.code,e.message);
  // Never return database paths, request payloads, or report contents in backend errors.
  throw new HttpsError('internal','Moderation operation could not be completed');
 }
});}
exports.moderationListReports=endpoint('list');
exports.moderationUpdateReportStatus=endpoint('update');
exports.moderationGetReportedContent=endpoint('content');
exports.moderationRemoveContent=endpoint('remove');
