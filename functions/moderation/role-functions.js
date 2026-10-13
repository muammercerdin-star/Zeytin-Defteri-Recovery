'use strict';
const {onCall,HttpsError}=require('firebase-functions/v2/https');
const {getFirestore,FieldPath,FieldValue}=require('firebase-admin/firestore');
const {getAuth}=require('firebase-admin/auth');
const {createRoleService}=require('./roles');
function endpoint(method){return onCall({region:'europe-west1',maxInstances:3,timeoutSeconds:30,memory:'256MiB'},async request=>{
 try{return await createRoleService({db:getFirestore(),auth:getAuth(),documentId:()=>FieldPath.documentId(),serverTimestamp:()=>FieldValue.serverTimestamp()})[method](request);}
 catch(e){if(['unauthenticated','permission-denied','invalid-argument','not-found','failed-precondition'].includes(e.code))throw new HttpsError(e.code,e.message);
  throw new HttpsError('internal','Role operation could not be completed');}
});}
exports.moderationLookupUser=endpoint('lookup');
exports.moderationGrantModerator=endpoint('grant');
exports.moderationRevokeModerator=endpoint('revoke');
exports.moderationListModerators=endpoint('list');
