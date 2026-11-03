// Optik anahtar sabitlemesi: oda kimliği B'nin 128 bit özetidir.
import * as C from './crypto.js';
import * as P from './protocol.js';

export async function prepare(browserPublicKey, secret) {
  await C.publicKey(browserPublicKey);
  P.require(secret.length === 32, 'GECERSIZ_QR');
  const digest = await C.sha256(C.concat(P.utf8('zd-web/compact-room/v1\n'), C.unbase64(browserPublicKey)));
  const roomBytes = digest.slice(0, 16);
  const room = C.base64(roomBytes);
  const cap = await C.hkdf(roomBytes, secret, P.utf8('zd-web/compact-phone-cap/v1'), 32);
  try {
    return {
      room,
      phoneCapabilityHash: C.base64(await C.sha256(P.utf8(C.base64(cap)))),
      text: 'ZD1.' + C.base64(C.concat(roomBytes, secret))
    };
  } finally {
    cap.fill(0);
  }
}

export async function bootstrap(room, secret, browserPublicKey, nb, expiresAt) {
  P.token(room, 16);
  P.token(nb, 32);
  const key = await C.hkdf(C.unbase64(room), secret, P.utf8('zd-web/compact-bootstrap/v1'), 32);
  const message = P.utf8('zd-web/compact-bootstrap/v1\n' + room + '\n' + expiresAt + '\n' + browserPublicKey + '\n' + nb);
  try {
    return { v: 1, type: 'bootstrap', room, browserPublicKey, nb, expiresAt,
      mac: C.base64(await C.hmac(key, message)) };
  } finally {
    key.fill(0);
  }
}
