'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {operator,command}=require('../league/approval');
const good={uid:'operator',token:{league_operator:true,auth_time:1700000000}};
const record={disabled:false,customClaims:{league_operator:true},tokensValidAfterTime:'2023-01-01T00:00:00Z'};
test('operator authorization rejects normal, forged body role, removed role, disabled and revoked sessions',async()=>{
 await assert.rejects(operator(null,async()=>record),e=>e.code==='unauthenticated');
 await assert.rejects(operator({uid:'user',token:{},isAdmin:true},async()=>record),e=>e.code==='permission-denied');
 assert.equal(await operator(good,async()=>record),'operator');
 for(const r of [{...record,customClaims:{}},{...record,disabled:true},{...record,tokensValidAfterTime:'2024-01-01T00:00:00Z'}])
  await assert.rejects(operator(good,async()=>r),e=>e.code==='permission-denied');
});
test('approval command rejects injected rank/score and missing independent review',()=>{
 for(const c of [{},{action:'approve',uid:'u',garden_id:'g',record_id:'e',request_id:'r',expected_revision:0,rank:1},
  {action:'approve',uid:'u',garden_id:'g',record_id:'e',request_id:'r',expected_revision:0}])
  assert.throws(()=>command(c),e=>e.code==='invalid-argument');
});
