'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),policy=require('../policy');
function fixture(options={}) {
  const records=new Map();let sends=0,body='';
  const post={user_id:'owner'};
  const comment={user_id:options.self?'owner':'author',user_name:'untrusted',content:'Merhaba',is_active:1};
  records.set('social_posts/post',post);
  records.set('social_posts/post/comments/comment',comment);
  records.set('users/owner/devices/device',{token:'token',platform:'android',session_id:'session'});
  const ref=path=>({path,id:path.split('/').pop(),
    get:async()=>snapshot(path),set:async(data,opt)=>records.set(path,opt?.merge?{...records.get(path),...data}:data),
    delete:async()=>records.delete(path),collection:name=>collection(path+'/'+name)});
  const snapshot=path=>({id:path.split('/').pop(),exists:records.has(path),ref:ref(path),data:()=>records.get(path),get:key=>records.get(path)?.[key]});
  const collection=path=>({doc:id=>ref(path+'/'+id),where:()=>query(path)});
  const query=path=>({orderBy(){return this},limit(){return this},startAfter(){return this},async get(){
    const docs=[...records.keys()].filter(k=>k.startsWith(path+'/')&&k.split('/').length===path.split('/').length+1).map(snapshot);
    return {docs,size:docs.length,empty:!docs.length};
  }});
  const db={doc:ref,collection,runTransaction:async fn=>fn({get:r=>r.get(),set:(r,d)=>r.set(d),delete:r=>r.delete()})};
  const Timestamp={fromMillis:ms=>({toMillis:()=>ms})};
  const modules={
    'firebase-functions/v2/firestore':{onDocumentCreated:(_,fn)=>fn},
    'firebase-admin/app':{initializeApp(){}},
    'firebase-admin/firestore':{getFirestore:()=>db,Timestamp,FieldValue:{serverTimestamp:()=>Timestamp.fromMillis(Date.now())}},
    'firebase-admin/auth':{getAuth:()=>({getUser:async()=>({displayName:'Auth Name'})})},
    'firebase-admin/messaging':{getMessaging:()=>({send:async m=>{
      sends++;body=m.data.body;
      assert.equal(m.notification,undefined);assert.equal(m.data.recipient_uid,'owner');assert.equal(m.data.session_id,'session');
      if(options.invalid)throw Object.assign(new Error('invalid'),{code:'messaging/registration-token-not-registered'});
      if(options.transient&&sends===1)throw Object.assign(new Error('retry'),{code:'messaging/internal-error'});
      return 'message-id';
    }})},'./policy':policy,'./league/functions':{},'./market/functions':{},'./moderation/functions':{},'./moderation/role-functions':{},'./account/functions':{}
  };
  const context={exports:{},require:n=>{if(!modules[n])throw new Error(n);return modules[n]},Date,Promise,Error};
  vm.runInNewContext(fs.readFileSync(require.resolve('../index'),'utf8'),context);
  return {run:()=>context.exports.meydanReplyCreated({params:{postId:'post',commentId:'comment'},data:{ref:ref('social_posts/post/comments/comment')}}),
    sends:()=>sends,body:()=>body,records};
}
test('actual trigger sends once on replay, resolves Auth display name',async()=>{
 const f=fixture();await f.run();await f.run();assert.equal(f.sends(),1);assert.match(f.body(),/^Auth Name/);
});
test('actual trigger skips own reply',async()=>{const f=fixture({self:true});await f.run();assert.equal(f.sends(),0)});
test('actual trigger retries transient failure without losing comment',async()=>{
 const f=fixture({transient:true});await assert.rejects(f.run());await f.run();await f.run();assert.equal(f.sends(),2);
 assert.ok(f.records.has('social_posts/post/comments/comment'));
});
test('actual trigger removes invalid token, preserves comment',async()=>{
 const f=fixture({invalid:true});await f.run();assert.ok(!f.records.has('users/owner/devices/device'));
 assert.ok(f.records.has('social_posts/post/comments/comment'));
});
