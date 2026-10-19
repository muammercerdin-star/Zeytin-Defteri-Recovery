'use strict';
/**
 * Hesap silme (Google Play "app account deletion" şartı).
 *
 * Bağımlılıklar dışarıdan verilir; böylece tüm akış canlı Firebase olmadan test edilir.
 * Silme sırası, yarıda kalırsa tekrar çağrıldığında kaldığı yerden devam edecek şekilde idempotenttir;
 * Firebase Auth kullanıcısı en son silinir (önce silinirse kalan veriyi silecek kimlik kalmaz).
 *
 * Silinen / anonimleştirilen:
 *  - users/{uid}/** (bahçe, işlem, hasat, elek, satış yedekleri; cihazlar; piyasa izni)
 *    → piyasa tetikleyicileri bu silmeyle kişinin anonim piyasa kaynaklarını da temizler; ayrıca
 *      market_backfills/{uid} enabled:false yazılarak temizlik açıkça istenir.
 *  - social_posts: kişinin gönderileri (altındaki yorumlarla birlikte) ve Storage social_posts/{uid}/
 *  - başkalarının gönderilerindeki yorumları: içerik boşaltılır, "Silinmiş kullanıcı" olarak işaretlenir
 *    (konuşma dizisi kırılmasın diye belge silinmez; uygulamanın kendi yorum silmesi de böyledir);
 *    kişiye verilen yanıtlardaki reply_to_name de değiştirilir.
 *  - user_blocks/{uid}/** ve başkalarının engel listelerindeki bu kişi kayıtları
 *  - reports/{uid}/** (kişinin verdiği şikayetler)
 *  - lig: league_sale_attestations (uid), bunlara bağlı league_verification_claims, yayımlanmış
 *    zeytin_leagues/{sezon}/snapshots/{snap}/members/{uid} belgeleri, işlem tablosundaki rows/owners kayıtları
 *  - Firebase Auth kullanıcısı
 * Saklanan (web sayfasında açıklanır): başkalarının bu kişi hakkında verdiği şikayet kayıtları (moderasyon),
 * kimlik içermeyen toplu piyasa istatistikleri. Supabase'deki piyasa katkıları uygulama tarafından geri çekilir.
 */
const RECENT_LOGIN_SECONDS = 10 * 60;
const CONFIRM_WORD = 'SİL';
const DELETED_NAME = 'Silinmiş kullanıcı';
const PAGE = 200;

function fail(code, message) { const e = new Error(message); e.code = code; throw e; }

function authorize(request, nowSeconds) {
  const auth = request && request.auth;
  if (!auth || typeof auth.uid !== 'string' || !auth.uid) fail('unauthenticated', 'Sign in required');
  const data = request.data || {};
  if (data.confirm !== CONFIRM_WORD) fail('invalid-argument', 'Confirmation word missing');
  const authTime = auth.token && Number(auth.token.auth_time);
  if (!Number.isFinite(authTime) || nowSeconds - authTime > RECENT_LOGIN_SECONDS) {
    fail('failed-precondition', 'recent-login-required');
  }
  return auth.uid;
}

function createService({db, recursiveDelete, deleteStoragePrefix, deleteAuthUser, nowMs = () => Date.now()}) {
  // Sorgu sonuçlarını sayfa sayfa işler; her sayfada işlenen belge sorgudan düşer ya da imleçle geçilir.
  async function each(query, fn, {advancing = false} = {}) {
    let count = 0, after = null;
    for (;;) {
      let q = query.limit(PAGE);
      if (advancing && after) q = q.startAfter(after);
      const page = await q.get();
      for (const doc of page.docs) { await fn(doc); count++; }
      if (page.docs.length < PAGE) return count;
      after = page.docs[page.docs.length - 1];
    }
  }

  async function deleteAccount(request) {
    const uid = authorize(request, Math.floor(nowMs() / 1000));
    const counts = {};

    // 1) Meydan gönderileri (altındaki tüm yorumlarla) ve gönderi fotoğrafları.
    counts.posts = await each(db.collection('social_posts').where('user_id', '==', uid),
      doc => recursiveDelete(doc.ref));
    await deleteStoragePrefix(`social_posts/${uid}/`);

    // 2) Başkalarının gönderilerindeki yorumları ve kişiye verilen yanıtlarda görünen adı.
    counts.comments = await each(db.collectionGroup('comments').where('user_id', '==', uid),
      doc => doc.ref.update({content: '', is_active: 0, user_name: DELETED_NAME, updated_at: nowMs()}),
      {advancing: true});
    counts.replyNames = await each(db.collectionGroup('comments').where('reply_to_uid', '==', uid),
      doc => doc.ref.update({reply_to_name: DELETED_NAME}), {advancing: true});

    // 3) Engellemeler ve şikayetler.
    await recursiveDelete(db.doc(`user_blocks/${uid}`));
    await db.doc(`social_typing/${uid}`).delete().catch(() => {});
    counts.blockedBy = await each(db.collectionGroup('items').where('blocked_uid', '==', uid),
      doc => (doc.ref.path.startsWith('user_blocks/') ? doc.ref.delete() : null));
    await recursiveDelete(db.doc(`reports/${uid}`));

    // 4) Zeytin Ligi kayıtları.
    const attestationIds = [];
    counts.attestations = await each(db.collection('league_sale_attestations').where('uid', '==', uid), async doc => {
      attestationIds.push(doc.id);
      await doc.ref.delete();
    });
    for (const id of attestationIds) {
      await each(db.collection('league_verification_claims').where('attestation_id', '==', id), doc => doc.ref.delete());
    }
    counts.leagueRows = await each(db.collectionGroup('rows').where('uid', '==', uid), doc => doc.ref.delete());
    counts.leagueOwners = await each(db.collectionGroup('owners').where('uid', '==', uid), doc => doc.ref.delete());
    counts.leagueMembers = 0;
    const seasons = await db.collection('zeytin_leagues').listDocuments();
    for (const season of seasons) {
      const snapshots = await season.collection('snapshots').listDocuments();
      for (const snapshot of snapshots) {
        const member = snapshot.collection('members').doc(uid);
        if ((await member.get()).exists) { await member.delete(); counts.leagueMembers++; }
      }
    }

    // 5) Özel yedekler ve piyasa kaynaklarının temizliği.
    await db.doc(`market_backfills/${uid}`).set({uid, cursor_path: '', requested_at_ms: nowMs(), enabled: false});
    await recursiveDelete(db.doc(`users/${uid}`));

    // 6) En son: giriş hesabı. Zaten silinmişse (tekrar çağrı) sorun değil.
    try { await deleteAuthUser(uid); }
    catch (e) { if (e && e.code !== 'auth/user-not-found') throw e; }

    return {deleted: true, counts};
  }

  return {deleteAccount};
}

module.exports = {createService, authorize, CONFIRM_WORD, DELETED_NAME, RECENT_LOGIN_SECONDS};
