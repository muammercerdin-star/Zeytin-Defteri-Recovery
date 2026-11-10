// WebCrypto; CDN, Node, localStorage ve ağ çağrısı yok. Anahtarlar yalnız bellekte.
import * as P from './protocol.js';
const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';
const need = (ok, code) => P.require(ok, code);
const bytes = a => a instanceof Uint8Array ? a : new Uint8Array(a);
export function random(n) { need(Number.isInteger(n) && n > 0 && n <= 1024, 'GECERSIZ_BOYUT'); return crypto.getRandomValues(new Uint8Array(n)); }
export function base64(input) {
  const a = bytes(input); let out = '';
  for (let i = 0; i < a.length; i += 3) {
    const b = a[i], c = a[i+1] ?? 0, d = a[i+2] ?? 0;
    out += B64[b >> 2] + B64[((b & 3) << 4) | (c >> 4)];
    if (i+1 < a.length) out += B64[((c & 15) << 2) | (d >> 6)];
    if (i+2 < a.length) out += B64[d & 63];
  } return out;
}
export function unbase64(s) {
  need(typeof s === 'string' && s.length <= P.MAX_FRAME_BYTES && s.length % 4 !== 1 && /^[A-Za-z0-9_-]*$/.test(s), 'GECERSIZ_BASE64');
  const a = new Uint8Array(Math.floor(s.length * 6 / 8)); let acc = 0, bits = 0, n = 0;
  for (const c of s) { acc = (acc << 6) | B64.indexOf(c); bits += 6; if (bits >= 8) { bits -= 8; a[n++] = acc >>> bits; } }
  need(base64(a) === s, 'GECERSIZ_BASE64'); return a;
}
export function concat(...arrays) {
  const out = new Uint8Array(arrays.reduce((n, a) => n + a.length, 0)); let at = 0;
  for (const a of arrays) { out.set(a, at); at += a.length; } return out;
}
export async function sha256(a) { return bytes(await crypto.subtle.digest('SHA-256', a)); }
export async function hmac(key, a) {
  // WebCrypto boş HMAC key kabul etmez; RFC5869 boş salt 32 sıfır bayta dönüşür.
  const k = await crypto.subtle.importKey('raw', key, { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  return bytes(await crypto.subtle.sign('HMAC', k, a));
}
export async function extract(salt, ikm) { return hmac(salt?.length ? salt : new Uint8Array(32), ikm); }
export async function expand(prk, info, length) {
  need(Number.isInteger(length) && length >= 0 && length <= 255 * 32, 'HKDF_UZUNLUK');
  const out = new Uint8Array(length); let t = new Uint8Array(), at = 0;
  for (let i = 1; at < length; i++) { t = await hmac(prk, concat(t, info, new Uint8Array([i]))); const n = Math.min(32, length-at); out.set(t.subarray(0,n),at); at+=n; }
  t.fill(0); return out;
}
export async function hkdf(salt, ikm, info, n) { const prk = await extract(salt, ikm); try { return await expand(prk, info, n); } finally { prk.fill(0); } }
export function keyPair() { return crypto.subtle.generateKey({ name: 'ECDH', namedCurve: 'P-256' }, false, ['deriveBits']); }
export async function publicKey(spki) {
  const der = unbase64(spki); need(der.length === 91, 'GECERSIZ_P256');
  const k = await crypto.subtle.importKey('spki', der, { name: 'ECDH', namedCurve: 'P-256' }, true, []);
  need(base64(await crypto.subtle.exportKey('spki', k)) === spki, 'GECERSIZ_P256'); return k;
}
export async function ecdh(privateKey, peerSpki) { return bytes(await crypto.subtle.deriveBits({ name: 'ECDH', public: await publicKey(peerSpki) }, privateKey, 256)); }
// QR B anahtarı constructor'dan sonra değiştirilemez; ağdan yeni B kabul edilmez.
export class Pairing {
  #room; #expires; #secret; #b; #nb; #closed=false;
  constructor(room,expiresAt,secret,browserSpki,nb) {
    P.token(room,16);P.token(nb,32);need(secret.length===32 && Number.isSafeInteger(expiresAt)&&expiresAt>0,'GECERSIZ_QR');
    this.#room=room;this.#expires=expiresAt;this.#secret=secret.slice();this.#b=browserSpki;this.#nb=nb;
  }
  #live(now) { need(!this.#closed && Number.isSafeInteger(now)&&now>=0&&now<this.#expires,'QR_SURESI_DOLDU'); }
  async phoneKeys(phone,np,now) {
    this.#live(now);const t=await P.transcript(this.#room,this.#expires,this.#nb,np,this.#b,base64(await crypto.subtle.exportKey('spki',phone.publicKey)));
    const z=await ecdh(phone.privateKey,this.#b);
    try { this.#live(now);const k=await initial(z,this.#secret,t);if(this.#closed){k.close();throw new Error('OTURUM_IPTAL');}return k; } finally { z.fill(0); }
  }
  async browserKeys(browser,phoneSpki,np,now) {
    this.#live(now);need(base64(await crypto.subtle.exportKey('spki',browser.publicKey))===this.#b,'SABIT_B_UYUSMAZ');
    const t=await P.transcript(this.#room,this.#expires,this.#nb,np,this.#b,phoneSpki),z=await ecdh(browser.privateKey,phoneSpki);
    try { this.#live(now);const k=await initial(z,this.#secret,t);if(this.#closed){k.close();throw new Error('OTURUM_IPTAL');}return k; } finally { z.fill(0); }
  }
  close() { this.#closed=true;this.#secret.fill(0); }
}
export class Keys {
  #keys; #closed = false;
  constructor(keys, hash) { this.#keys = keys; this.transcriptHash = hash.slice(); }
  direction(dir) { need(!this.#closed, 'OTURUM_IPTAL'); P.direction(dir); return this.#keys[dir].slice(); }
  confirmation() { need(!this.#closed, 'OTURUM_IPTAL'); return this.#keys.confirm.slice(); }
  resumeRoot() { need(!this.#closed, 'OTURUM_IPTAL'); return this.#keys.resume.slice(); }
  close() { this.#closed = true; for (const a of Object.values(this.#keys)) a.fill(0); }
}
async function keys(prk, hash) {
  const out = {};
  for (const [name, label] of Object.entries({p2w:'phone-to-web',w2p:'web-to-phone',confirm:'confirm',resume:'resume'}))
    out[name] = await expand(prk, concat(P.utf8('zd-web/v1/' + label),hash),32);
  return new Keys(out,hash);
}
export async function initial(shared, s, t) {
  need(shared.length === 32 && s.length === 32, 'GECERSIZ_ANAHTAR');
  const prk = await extract(s,shared); try { return await keys(prk,await sha256(t)); } finally { prk.fill(0); }
}
export async function resume(root, t) {
  need(root.length === 32, 'GECERSIZ_ANAHTAR'); const h = await sha256(t), prk = await extract(h,root);
  try { return await keys(prk,h); } finally { prk.fill(0); }
}
export async function proof(key, role, hash) {
  need(['phone-hello','web-finished','resume-phone','resume-browser','device-phone','device-browser'].includes(role),'GECERSIZ_KANIT');
  return hmac(key,concat(P.utf8(role),hash));
}
export async function verify(key, role, hash, candidate) {
  need(candidate.length === 32,'KANIT_RED'); const good = await proof(key,role,hash);
  let diff = 0; for (let i = 0; i < 32; i++) diff |= good[i] ^ candidate[i]; need(diff === 0,'KANIT_RED');
}
function iv(seq) {
  const n = P.sequence(String(seq)), a = new Uint8Array(12); new DataView(a.buffer).setBigUint64(4,n,false); return a;
}
export async function encrypt(key, seq, aad, plain) {
  need(key.length === 32,'GECERSIZ_ANAHTAR');
  const k = await crypto.subtle.importKey('raw',key,'AES-GCM',false,['encrypt']);
  return bytes(await crypto.subtle.encrypt({name:'AES-GCM',iv:iv(seq),additionalData:aad,tagLength:128},k,plain));
}
export async function decrypt(key, seq, aad, ct) {
  need(key.length === 32,'GECERSIZ_ANAHTAR');
  const k = await crypto.subtle.importKey('raw',key,'AES-GCM',false,['decrypt']);
  return bytes(await crypto.subtle.decrypt({name:'AES-GCM',iv:iv(seq),additionalData:aad,tagLength:128},k,ct));
}
export class Channel {
  #session; #epoch; #sendDir; #recvDir; #tx; #rx; #sent=0n; #received=0n; #closed=false; #tail=Promise.resolve();
  constructor(keys,session,epoch,sendDir) {
    P.token(session,16); P.token(epoch,16); P.direction(sendDir);
    this.#session=session;this.#epoch=epoch;this.#sendDir=sendDir;this.#recvDir=sendDir==='p2w'?'w2p':'p2w';
    this.#tx=keys.direction(sendDir);this.#rx=keys.direction(this.#recvDir);
  }
  // WebCrypto await'leri iki eşzamanlı open ile replay kontrolünü atlatmasın.
  #serial(fn) { const next=this.#tail.then(fn);this.#tail=next.catch(()=>{});return next; }
  seal(plain) { return this.#serial(async()=>{
    need(!this.#closed && this.#sent<9223372036854775807n,'OTURUM_IPTAL'); need(plain.length<=P.MAX_PLAIN_BYTES,'BOYUT_SINIRI');
    const seq=String(++this.#sent), ct=await encrypt(this.#tx,seq,P.aad(this.#session,this.#epoch,this.#sendDir,seq),plain);
    need(!this.#closed,'OTURUM_IPTAL'); return P.frame(this.#session,this.#epoch,this.#sendDir,seq,base64(ct));
  }); }
  open(input) { return this.#serial(async()=>{
    const f=P.parseFrame(JSON.stringify(input));
    need(!this.#closed && f.session===this.#session && f.epoch===this.#epoch && f.dir===this.#recvDir,'ESKI_EPOCH_VEYA_YON');
    const n=P.sequence(f.seq);need(n>this.#received,'TEKRAR_SAYAC');
    const p=await decrypt(this.#rx,n,P.aad(f.session,f.epoch,f.dir,f.seq),unbase64(f.ct));
    need(!this.#closed,'OTURUM_IPTAL');this.#received=n;return p;
  }); }
  close() { this.#closed=true;this.#tx.fill(0);this.#rx.fill(0); }
}
export class ResumeGuard {
  #session; #epoch; #root; #lost=-1; #tail=Promise.resolve();
  constructor(session,epoch,root) { P.token(session,16);P.token(epoch,16);need(root.length===32,'GECERSIZ_ANAHTAR');this.#session=session;this.#epoch=epoch;this.#root=root.slice(); }
  disconnected(now) { need(Number.isSafeInteger(now)&&now>=0,'GECERSIZ_ZAMAN');if(this.#lost<0)this.#lost=now; }
  accept(now,old,next,nb,np,phoneProof,webProof) {
    const fn=async()=>{
      need(this.#root && this.#lost>=0 && now>=this.#lost && now-this.#lost<=P.RESUME_WINDOW_MS,'RESUME_SURESI_DOLDU');
      need(this.#epoch===old,'ESKI_EPOCH');const t=P.resumeTranscript(this.#session,old,next,nb,np),h=await sha256(t),root=this.#root.slice();
      try {
        await verify(root,'resume-phone',h,phoneProof);await verify(root,'resume-browser',h,webProof);
        const k=await resume(root,t);need(this.#root!==null,'OTURUM_IPTAL');this.#root.fill(0);this.#root=k.resumeRoot();this.#epoch=next;this.#lost=-1;return k;
      } finally { root.fill(0); }
    };
    const promise=this.#tail.then(fn);this.#tail=promise.catch(()=>{});return promise;
  }
  close() { this.#root?.fill(0);this.#root=null;this.#lost=-1; }
}
