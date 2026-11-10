// Bağımlılıksız ES module. Eşleştirme UI'si ve SQLite köprüsü adım 3'te bağlanacak.
import { unbase64, publicKey } from './crypto.js';
export const VERSION = 1, MAX_FRAME_BYTES = 65536, MAX_PLAIN_BYTES = 32768, RESUME_WINDOW_MS = 300000;
export const utf8 = s => new TextEncoder().encode(s);
export function require(ok, code) { if (!ok) throw new Error(code); }
export function token(s, n) { require(typeof s === 'string' && unbase64(s).length === n, 'GECERSIZ_ALAN'); }
export function direction(d) { require(d === 'p2w' || d === 'w2p', 'GECERSIZ_YON'); }
export function sequence(s) {
  require(typeof s === 'string' && /^[1-9][0-9]{0,18}$/.test(s), 'GECERSIZ_SAYAC');
  const n = BigInt(s); require(n <= 9223372036854775807n, 'GECERSIZ_SAYAC'); return n;
}
export async function transcript(room, expiresAt, nb, np, b, p) {
  token(room, 16); token(nb, 32); token(np, 32);
  require(Number.isSafeInteger(expiresAt) && expiresAt > 0, 'GECERSIZ_ZAMAN');
  await publicKey(b); await publicKey(p);
  return utf8(JSON.stringify(['zd-web', 1, room, expiresAt, nb, np, b, p]));
}
export function resumeTranscript(session, oldEpoch, epoch, nb, np) {
  token(session, 16); token(oldEpoch, 16); token(epoch, 16); token(nb, 32); token(np, 32);
  require(oldEpoch !== epoch, 'ESKI_EPOCH');
  return utf8(JSON.stringify(['zd-web-resume', 1, session, oldEpoch, epoch, nb, np]));
}
export function aad(session, epoch, dir, seq) {
  token(session, 16); token(epoch, 16); direction(dir); sequence(seq);
  return utf8(JSON.stringify(['zd-web', 1, session, epoch, dir, seq]));
}
export function frame(session, epoch, dir, seq, ct) {
  token(session, 16); token(epoch, 16); direction(dir); sequence(seq);
  const n = unbase64(ct).length; require(n >= 16 && n <= MAX_PLAIN_BYTES + 16, 'BOYUT_SINIRI');
  return { v: VERSION, type: 'encrypted', session, epoch, dir, seq, ct };
}
export function parseFrame(json) {
  require(typeof json === 'string' && utf8(json).length <= MAX_FRAME_BYTES, 'BOYUT_SINIRI');
  // JSON.parse yinelenen anahtarı gizler; zarf yalnız düz ASCII değerler taşır.
  require(/^\s*\{\s*"[A-Za-z]+"\s*:\s*(?:1|"[A-Za-z0-9_-]*")(?:\s*,\s*"[A-Za-z]+"\s*:\s*(?:1|"[A-Za-z0-9_-]*"))*\s*\}\s*$/.test(json), 'GECERSIZ_ZARF');
  const names = [...json.matchAll(/"([A-Za-z]+)"\s*:/g)].map(m => m[1]);
  require(names.length === 7 && new Set(names).size === 7, 'GECERSIZ_ZARF');
  const f = JSON.parse(json);
  require(f.v === 1 && f.type === 'encrypted' && Object.keys(f).sort().join(',') === 'ct,dir,epoch,seq,session,type,v', 'GECERSIZ_ZARF');
  return frame(f.session, f.epoch, f.dir, f.seq, f.ct);
}
export class Approval {
  #confirmed = false; #approved = false; #closed = false;
  peerConfirmed() { require(!this.#closed, 'OTURUM_IPTAL'); this.#confirmed = true; }
  phoneApproved() { require(this.#confirmed && !this.#closed, 'ONAY_GEREKLI'); this.#approved = true; }
  canRead() { return this.#confirmed && this.#approved && !this.#closed; }
  close() { this.#closed = true; this.#confirmed = this.#approved = false; }
}
