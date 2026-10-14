'use strict';
const {onCall, HttpsError} = require('firebase-functions/v2/https');
const {getFirestore} = require('firebase-admin/firestore');
const {getAuth} = require('firebase-admin/auth');
const {getStorage} = require('firebase-admin/storage');
const {createService} = require('./service');

// Uzun sürebilir (çok gönderili hesaplar); tek örnek yeterli, istek başına bir kullanıcı.
const options = {region: 'europe-west1', maxInstances: 5, timeoutSeconds: 540, memory: '512MiB'};

exports.deleteAccount = onCall(options, async request => {
  try {
    const db = getFirestore();
    const service = createService({
      db,
      recursiveDelete: ref => db.recursiveDelete(ref),
      deleteStoragePrefix: prefix => getStorage().bucket().deleteFiles({prefix, force: true}),
      deleteAuthUser: uid => getAuth().deleteUser(uid)
    });
    return await service.deleteAccount(request);
  } catch (e) {
    const safe = ['unauthenticated', 'invalid-argument', 'failed-precondition'];
    if (safe.includes(e.code)) throw new HttpsError(e.code, e.message);
    // Yol, kimlik veya içerik sızdırma; istemci tekrar deneyebilir (silme idempotenttir).
    throw new HttpsError('internal', 'Account deletion could not be completed; try again');
  }
});
