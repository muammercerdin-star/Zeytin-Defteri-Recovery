'use strict';
const {onDocumentCreated} = require('firebase-functions/v2/firestore');
const {initializeApp} = require('firebase-admin/app');
const {getFirestore, Timestamp, FieldValue} = require('firebase-admin/firestore');
const {getMessaging} = require('firebase-admin/messaging');
const {getAuth} = require('firebase-admin/auth');
const policy = require('./policy');
initializeApp();
const db = getFirestore();

// Data-only: Android checks the intended UID/session before showing any message text.
// FCM and Firestore are not one atomic transaction. A crash after send can retry;
// the Android event key additionally deduplicates these ambiguous deliveries.
exports.meydanReplyCreated = onDocumentCreated({
  document: 'social_posts/{postId}/comments/{commentId}',
  region: 'europe-west1', retry: true, maxInstances: 5, concurrency: 10,
  timeoutSeconds: 120, memory: '256MiB'
}, async event => {
  if (!event.data) return;
  const {postId, commentId} = event.params;
  const [postSnap, currentComment] = await Promise.all([
    db.doc(`social_posts/${postId}`).get(), event.data.ref.get()
  ]);
  const post = postSnap.data(), comment = currentComment.data();
  if (!policy.shouldNotify(post, comment)) return;
  // Resolve display name from Firebase Auth, not a client-supplied user_name field.
  let author;
  try { author = await getAuth().getUser(comment.user_id); }
  catch (e) { if (e.code === 'auth/user-not-found') return; throw e; }
  if (author.disabled) return;
  const body = policy.body(author.displayName, comment.content);
  const devices = db.collection('users').doc(post.user_id).collection('devices');
  const cutoff = Timestamp.fromMillis(Date.now() - 90 * 86400000);
  let last = null;
  for (;;) {
    let query = devices.where('updated_at', '>=', cutoff).orderBy('updated_at').limit(100);
    if (last) query = query.startAfter(last);
    const page = await query.get();
    if (page.empty) break;
    // Bounded concurrency; one recipient's large device list is paginated.
    for (let offset = 0; offset < page.size; offset += 10) {
      const results = await Promise.allSettled(page.docs.slice(offset, offset + 10).map(async device => {
        const data = device.data();
        if (data.platform !== 'android' || typeof data.token !== 'string' || !data.token ||
            typeof data.session_id !== 'string' || !data.session_id) return;
        const id = policy.key(postId, commentId, post.user_id, device.id, data.session_id);
        const receipt = db.collection('push_deliveries').doc(id);
        const claim = await db.runTransaction(async tx => {
          const existing = await tx.get(receipt);
          if (existing.exists && ['sent', 'invalid'].includes(existing.get('state'))) return 'done';
          if (existing.exists && existing.get('lease_until')?.toMillis() > Date.now()) return 'busy';
          tx.set(receipt, {state:'sending', lease_until:Timestamp.fromMillis(Date.now()+180000),
            expires_at:Timestamp.fromMillis(Date.now()+30*86400000), updated_at:FieldValue.serverTimestamp()});
          return 'claimed';
        });
        if (claim === 'done') return;
        if (claim === 'busy') throw new Error('Delivery lease busy; retry later');
        let messageId;
        try {
          messageId = await getMessaging().send({token:data.token,
            data:{type:'meydan_reply', post_id:postId, comment_id:commentId,
              recipient_uid:post.user_id, session_id:data.session_id, body},
            android:{priority:'high', ttl:86400000}
          });
        } catch (error) {
          const invalid = policy.invalidToken(error.code);
          await receipt.set({state:invalid?'invalid':'pending', lease_until:Timestamp.fromMillis(0),
            updated_at:FieldValue.serverTimestamp()}, {merge:true});
          if (invalid) {
            // Do not delete a token/session refreshed while this send was running.
            await db.runTransaction(async tx => {
              const now = await tx.get(device.ref);
              if (now.exists && now.get('token')===data.token && now.get('session_id')===data.session_id) tx.delete(device.ref);
            });
            return;
          }
          throw error;
        }
        await receipt.set({state:'sent', message_id:messageId, lease_until:Timestamp.fromMillis(0),
          updated_at:FieldValue.serverTimestamp()}, {merge:true});
      }));
      const failure = results.find(r => r.status === 'rejected');
      if (failure) throw failure.reason;
    }
    last = page.docs[page.size-1];
    if (page.size < 100) break;
  }
});

// League has an independent schedule/task pipeline; existing social trigger stays intact.
Object.assign(exports, require('./league/functions'));

// Anonymous multi-user regional market publisher. Raw user records remain private.
Object.assign(exports, require('./market/functions'));

// Moderation access is backend-only; client report rules remain unchanged.
Object.assign(exports, require('./moderation/functions'));

Object.assign(exports, require('./moderation/role-functions'));
// Play account deletion: user-initiated, deletes the caller's own data and auth account.
Object.assign(exports, require('./account/functions'));
