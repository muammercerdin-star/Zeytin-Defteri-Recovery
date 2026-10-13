const test = require('node:test');
const assert = require('node:assert/strict');
const p = require('../policy');
test('only active replies by another user notify', () => {
  const post={user_id:'a'}, c={user_id:'b',content:'Merhaba',is_active:1};
  assert.equal(p.shouldNotify(post,c),true);
  assert.equal(p.shouldNotify(post,{...c,user_id:'a'}),false);
  assert.equal(p.shouldNotify(post,{...c,is_active:0}),false);
  assert.equal(p.shouldNotify({...post,is_active:0},c),false);
  assert.equal(p.shouldNotify(post,{...c,content:'  '}),false);
  assert.equal(p.shouldNotify(null,c),false);
});
test('delivery identities deduplicate retries and isolate devices and sessions', () => {
  assert.equal(p.key('p','c','u','d','s'),p.key('p','c','u','d','s'));
  assert.notEqual(p.key('p','c','u','d','s'),p.key('p','c','u','e','s'));
  assert.notEqual(p.key('p','c','u','d','s'),p.key('p','c','u','d','new'));
});
test('bounded preview and invalid-token classification', () => {
  assert.ok(p.body('A'.repeat(500),'x'.repeat(2000)).length<=240);
  assert.ok(!p.body('', 'hello\nworld').includes('\n'));
  assert.equal(p.invalidToken('messaging/registration-token-not-registered'),true);
  assert.equal(p.invalidToken('messaging/internal-error'),false);
});
