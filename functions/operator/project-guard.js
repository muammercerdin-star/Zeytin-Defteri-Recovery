'use strict';
const fs=require('node:fs'),path=require('node:path');
function guardError(message){const e=new Error(message);e.code='operator/guard';throw e;}
function validateProject(config,android,env,credential){
 const project=config?.projects?.default;
 if(typeof project!=='string'||! /^[a-z][a-z0-9-]{4,62}$/.test(project)||android?.project_info?.project_id!==project)
  guardError('Local Firebase project configurations are missing or inconsistent.');
 if(env.FIREBASE_AUTH_EMULATOR_HOST||env.FIRESTORE_EMULATOR_HOST||env.FIREBASE_EMULATOR_HUB)
  guardError('Emulator environment detected. This operator CLI requires a trusted production environment.');
 for(const name of ['GOOGLE_CLOUD_PROJECT','GCLOUD_PROJECT'])if(env[name]&&env[name]!==project)guardError('Environment project differs from the configured project.');
 if(credential?.project_id&&credential.project_id!==project)guardError('Credential project differs from the configured project.');
 return project;
}
function configuredProject(env=process.env){
 try{
  const root=path.resolve(__dirname,'../..');
  const config=JSON.parse(fs.readFileSync(path.join(root,'.firebaserc'),'utf8'));
  const android=JSON.parse(fs.readFileSync(path.join(root,'app/google-services.json'),'utf8'));
  const credential=env.GOOGLE_APPLICATION_CREDENTIALS?JSON.parse(fs.readFileSync(env.GOOGLE_APPLICATION_CREDENTIALS,'utf8')):null;
  if(env.FIREBASE_CONFIG){
   const raw=env.FIREBASE_CONFIG.trim();
   const firebase=JSON.parse(raw.startsWith('{')?raw:fs.readFileSync(raw,'utf8'));
   if(firebase.projectId&&firebase.projectId!==config?.projects?.default)guardError('FIREBASE_CONFIG project differs from the configured project.');
  }
  return validateProject(config,android,env,credential);
 }catch(e){if(e.code==='operator/guard')throw e;guardError('Project or credential configuration could not be read. No operation performed.');}
}
function assertProject(project){if(configuredProject()!==project)guardError('Requested project differs from the configured project.');}
function safeError(e){
 if(e?.code==='operator/guard')return e.message;
 if(e?.code==='auth/user-not-found'||e?.code==='not-found')return 'User not found. No account was created.';
 if(e?.code==='failed-precondition')return 'Role change rejected or pending. Inspect account status and role audit/lock in the trusted environment.';
 return 'Operator operation failed. Verify ADC/IAM and pending role audit before retrying. No secret details are logged.';
}
module.exports={configuredProject,assertProject,validateProject,guardError,safeError};
