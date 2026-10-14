'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const {createService, authorize, CONFIRM_WORD, DELETED_NAME} = require('../account/service');

// Bellek içi Firestore: yol → veri. Sorgular yalnız '==' eşitliğini destekler (servisin kullandığı kadar).
function fakeDb(seed) {
  const rows = new Map(Object.entries(seed));
  const calls = [];
  const parent = p => p.split('/').slice(0, -1).join('/');
  const docRef = path => ({
    path, id: path.split('/').pop(),
    get: async () => ({exists: rows.has(path), data: () => rows.get(path)}),
    set: async d => { rows.set(path, {...d}); },
    update: async d => { assert.ok(rows.has(path), 'update of missing ' + path); rows.set(path, {...rows.get(path), ...d}); },
    delete: async () => { rows.delete(path); },
    collection: name => colRef(path + '/' + name)
  });
  const snap = path => ({id: path.split('/').pop(), ref: docRef(path), data: () => rows.get(path)});
  const query = (match, spec = {}) => ({
    where: (k, op, v) => { assert.equal(op, '=='); return query(match, {...spec, filters: [...(spec.filters || []), [k, v]]}); },
    limit: n => query(match, {...spec, limit: n}),
    startAfter: doc => query(match, {...spec, after: doc.ref.path}),
    get: async () => {
      let keys = [...rows.keys()].filter(match).filter(p => (spec.filters || []).every(([k, v]) => rows.get(p)[k] === v)).sort();
      if (spec.after) keys = keys.filter(p => p > spec.after);
      return {docs: keys.slice(0, spec.limit || 1e9).map(snap)};
    }
  });
  const colRef = path => ({
    ...query(p => parent(p) === path),
    doc: id => docRef(path + '/' + id),
    listDocuments: async () => [...new Set([...rows.keys()].filter(p => p.startsWith(path + '/'))
      .map(p => p.slice(path.length + 1).split('/')[0]))].map(id => docRef(path + '/' + id))
  });
  const db = {
    collection: name => colRef(name),
    collectionGroup: name => { calls.push('group:' + name); return query(p => p.split('/').slice(-2, -1)[0] === name); },
    doc: path => docRef(path)
  };
  const recursiveDelete = async ref => { calls.push('rdel:' + ref.path); for (const k of [...rows.keys()]) if (k === ref.path || k.startsWith(ref.path + '/')) rows.delete(k); };
  return {db, rows, calls, recursiveDelete};
}

const NOW = 1_800_000_000_000;
const req = (uid = 'U', extra = {}) => ({auth: {uid, token: {auth_time: NOW / 1000 - 60}}, data: {confirm: CONFIRM_WORD}, ...extra});

function world() {
  return {
    'users/U': {name: 'U'}, 'users/U/gardens/g1': {owner_uid: 'U'}, 'users/U/gardens/g1/elek_records/e1': {owner_uid: 'U'},
    'users/U/devices/d1': {token: 't'}, 'users/U/market_settings/anonymous': {enabled: true},
    'users/V': {name: 'V'}, 'users/V/gardens/g9': {owner_uid: 'V'},
    'social_posts/p1': {user_id: 'U'}, 'social_posts/p1/comments/c1': {user_id: 'V', content: 'V on U post'},
    'social_posts/p2': {user_id: 'V'},
    'social_posts/p2/comments/c2': {user_id: 'U', user_name: 'Ahmet', content: 'U on V post', is_active: 1, parent_comment_id: ''},
    'social_posts/p2/comments/c3': {user_id: 'V', reply_to_uid: 'U', reply_to_name: 'Ahmet', content: 'reply'},
    'user_blocks/U/items/V': {blocker_uid: 'U', blocked_uid: 'V'},
    'user_blocks/V/items/U': {blocker_uid: 'V', blocked_uid: 'U', display_name: 'Ahmet'},
    'user_blocks/V/items/W': {blocker_uid: 'V', blocked_uid: 'W'},
    'reports/U/items/r1': {reporter_uid: 'U', reported_uid: 'V'},
    'reports/V/items/r2': {reporter_uid: 'V', reported_uid: 'U', blocked_uid: 'U'},
    'league_sale_attestations/a1': {uid: 'U'}, 'league_sale_attestations/a2': {uid: 'V'},
    'league_verification_claims/k1': {attestation_id: 'a1'}, 'league_verification_claims/k2': {attestation_id: 'a2'},
    'league_backend_jobs/j1/rows/x': {uid: 'U'}, 'league_backend_jobs/j1/owners/U': {uid: 'U'}, 'league_backend_jobs/j1/owners/V': {uid: 'V'},
    'zeytin_leagues/2026': {}, 'zeytin_leagues/2026/snapshots/s1': {}, 'zeytin_leagues/2026/snapshots/s1/members/U': {rank: 1},
    'zeytin_leagues/2026/snapshots/s1/members/V': {rank: 2}
  };
}

function run(seed = world(), opts = {}) {
  const f = fakeDb(seed), storage = [], authDeleted = [];
  const service = createService({db: f.db, recursiveDelete: f.recursiveDelete, nowMs: () => NOW,
    deleteStoragePrefix: async p => storage.push(p),
    deleteAuthUser: opts.deleteAuthUser || (async uid => authDeleted.push(uid))});
  return {...f, service, storage, authDeleted};
}

test('anonim, onaysız veya eski oturum reddedilir; hiçbir şey silinmez', async () => {
  for (const [request, code] of [
    [{data: {confirm: CONFIRM_WORD}}, 'unauthenticated'],
    [{auth: {uid: ''}, data: {confirm: CONFIRM_WORD}}, 'unauthenticated'],
    [{auth: {uid: 'U', token: {auth_time: NOW / 1000 - 60}}, data: {}}, 'invalid-argument'],
    [{auth: {uid: 'U', token: {auth_time: NOW / 1000 - 60}}, data: {confirm: 'sil'}}, 'invalid-argument'],
    [{auth: {uid: 'U', token: {auth_time: NOW / 1000 - 11 * 60}}, data: {confirm: CONFIRM_WORD}}, 'failed-precondition'],
    [{auth: {uid: 'U', token: {}}, data: {confirm: CONFIRM_WORD}}, 'failed-precondition']]) {
    const h = run();
    const before = h.rows.size;
    await assert.rejects(h.service.deleteAccount(request), e => e.code === code);
    assert.equal(h.rows.size, before); assert.deepEqual(h.authDeleted, []); assert.deepEqual(h.storage, []);
  }
  assert.throws(() => authorize({auth: {uid: 'U', token: {auth_time: 1}}, data: {confirm: CONFIRM_WORD}}, 1 + 10 * 60 + 1),
    e => e.message === 'recent-login-required');
});

test('kişinin tüm verisi silinir, başka kullanıcınınki kalır', async () => {
  const h = run();
  const out = await h.service.deleteAccount(req());
  assert.equal(out.deleted, true);
  const keys = [...h.rows.keys()];
  assert.ok(!keys.some(k => k === 'users/U' || k.startsWith('users/U/')), 'özel yedek silinmeli');
  assert.ok(h.rows.has('users/V/gardens/g9'), 'başkasının yedeği kalmalı');
  assert.ok(!h.rows.has('social_posts/p1') && !h.rows.has('social_posts/p1/comments/c1'), 'gönderi ve altındaki yorumlar silinmeli');
  assert.ok(h.rows.has('social_posts/p2'), 'başkasının gönderisi kalmalı');
  assert.deepEqual(h.storage, ['social_posts/U/']);
  assert.ok(!h.rows.has('user_blocks/U/items/V') && !h.rows.has('user_blocks/V/items/U'), 'engel kayıtları silinmeli');
  assert.ok(h.rows.has('user_blocks/V/items/W'), 'ilgisiz engel kalmalı');
  assert.ok(h.rows.has('reports/V/items/r2'), 'kişi hakkındaki şikayet (moderasyon) kalmalı; items grubunda engel değil');
  assert.ok(!h.rows.has('reports/U/items/r1'), 'kişinin verdiği şikayet silinmeli');
  assert.ok(!h.rows.has('league_sale_attestations/a1') && !h.rows.has('league_verification_claims/k1'));
  assert.ok(h.rows.has('league_sale_attestations/a2') && h.rows.has('league_verification_claims/k2'));
  assert.ok(!h.rows.has('league_backend_jobs/j1/rows/x') && !h.rows.has('league_backend_jobs/j1/owners/U'));
  assert.ok(h.rows.has('league_backend_jobs/j1/owners/V'));
  assert.ok(!h.rows.has('zeytin_leagues/2026/snapshots/s1/members/U') && h.rows.has('zeytin_leagues/2026/snapshots/s1/members/V'));
  assert.deepEqual(h.rows.get('market_backfills/U'), {uid: 'U', cursor_path: '', requested_at_ms: NOW, enabled: false});
  assert.deepEqual(h.authDeleted, ['U']);
  assert.equal(out.counts.posts, 1); assert.equal(out.counts.leagueMembers, 1);
});

test('başka gönderideki yorum anonimleşir, dizi bozulmaz; yanıtlardaki ad değişir', async () => {
  const h = run();
  await h.service.deleteAccount(req());
  const c2 = h.rows.get('social_posts/p2/comments/c2');
  assert.deepEqual([c2.content, c2.is_active, c2.user_name, c2.parent_comment_id], ['', 0, DELETED_NAME, '']);
  assert.equal(h.rows.get('social_posts/p2/comments/c3').reply_to_name, DELETED_NAME);
  assert.equal(h.rows.get('social_posts/p2/comments/c3').content, 'reply', 'başkasının yanıt metni korunmalı');
});

test('giriş hesabı en son silinir; tekrar çağrı (yarıda kalma sonrası) güvenlidir', async () => {
  const order = [];
  const h = run(world(), {deleteAuthUser: async uid => { order.push('auth'); assert.ok(!h.rows.has('users/U')); }});
  await h.service.deleteAccount(req());
  assert.deepEqual(order, ['auth']);
  const again = run(new Map(), {deleteAuthUser: async () => { const e = new Error('x'); e.code = 'auth/user-not-found'; throw e; }});
  assert.equal((await again.service.deleteAccount(req())).deleted, true);
  const broken = run(world(), {deleteAuthUser: async () => { const e = new Error('x'); e.code = 'auth/internal-error'; throw e; }});
  await assert.rejects(broken.service.deleteAccount(req()));
});

test('çok sayfalı sonuçlar eksiksiz işlenir (200+ gönderi ve yorum)', async () => {
  const seed = {};
  for (let i = 0; i < 450; i++) seed[`social_posts/u${String(i).padStart(4, '0')}`] = {user_id: 'U'};
  for (let i = 0; i < 430; i++) seed[`social_posts/v/comments/c${String(i).padStart(4, '0')}`] = {user_id: 'U', content: 'x', is_active: 1};
  seed['social_posts/v'] = {user_id: 'V'};
  const h = run(seed);
  const out = await h.service.deleteAccount(req());
  assert.equal(out.counts.posts, 450); assert.equal(out.counts.comments, 430);
  assert.ok([...h.rows.keys()].every(k => !k.startsWith('social_posts/u')));
  assert.ok([...h.rows.entries()].filter(([k]) => k.includes('/comments/')).every(([, v]) => v.user_name === DELETED_NAME));
});

test('uç nokta yalnız güvenli hata kodlarını iletir', () => {
  const src = require('node:fs').readFileSync(require.resolve('../account/functions.js'), 'utf8');
  assert.match(src, /safe = \['unauthenticated', 'invalid-argument', 'failed-precondition'\]/);
  assert.match(src, /region: 'europe-west1'/);
  assert.match(src, /db\.recursiveDelete\(ref\)/);
  assert.match(require('node:fs').readFileSync(require.resolve('../index.js'), 'utf8'), /require\('\.\/account\/functions'\)/);
});
