// Çiftçiye Web Push bildirimi (İP9-A-10). VAPID (RFC 8292) + aes128gcm şifreli içerik (RFC 8291/8188).
// Yalnız WebCrypto; dış bağımlılık yok. Mesaj içeriği uçtan uca şifrelidir: ileten push servisi okuyamaz.
// Kişisel veri taşınmaz: mesaj yalnız kod + fabrika içerir.
import { b64u, rastgele, unb64u } from "./guvenlik.mjs";
import { TR_OFFSET_MS } from "./zaman.mjs";

const encoder = new TextEncoder();

export const BILDIRIM_TTL_SN = 1800;
const JWT_OMRU_SN = 12 * 60 * 60;
const KAYIT_BOYU = 4096;

/** Gece kuralı: Türkiye saatiyle 22:00–05:00 arasında bildirim gönderilmez. */
export function geceMi(simdi) {
  const saat = new Date(simdi + TR_OFFSET_MS).getUTCHours();
  return saat >= 22 || saat < 5;
}

function diziBirlestir(parcalar) {
  let boy = 0;
  for (const p of parcalar) {
    boy += p.length;
  }
  const hepsi = new Uint8Array(boy);
  let yer = 0;
  for (const p of parcalar) {
    hepsi.set(p, yer);
    yer += p.length;
  }
  return hepsi;
}

/** VAPID gizli (ham d, b64u) + açık anahtardan ECDSA P-256 imzalayıcı anahtar. */
async function vapidImzaAnahtari(env) {
  const acik = unb64u(env.VAPID_ACIK_ANAHTAR);
  const jwk = {
    kty: "EC",
    crv: "P-256",
    d: env.VAPID_GIZLI_ANAHTAR,
    x: b64u(acik.slice(1, 33)),
    y: b64u(acik.slice(33, 65)),
    ext: true,
  };
  return crypto.subtle.importKey("jwk", jwk, { name: "ECDSA", namedCurve: "P-256" }, false, ["sign"]);
}

/** Belirli bir push kaynağı (aud = endpoint origin) için VAPID JWT'si (ES256). */
export async function vapidJwt(env, endpoint, simdi) {
  const aud = new URL(endpoint).origin;
  const konu = env.VAPID_KONU || "mailto:destek@zeytindefteri.tr";
  const baslik = { typ: "JWT", alg: "ES256" };
  const govde = { aud, exp: Math.floor(simdi / 1000) + JWT_OMRU_SN, sub: konu };
  const taban = b64u(encoder.encode(JSON.stringify(baslik))) + "." + b64u(encoder.encode(JSON.stringify(govde)));
  const anahtar = await vapidImzaAnahtari(env);
  const imza = await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, anahtar, encoder.encode(taban));
  return taban + "." + b64u(new Uint8Array(imza));
}

/** aes128gcm gövdesi: abonenin açık anahtarı (p256dh) ve auth'u ile düz metni şifreler (RFC 8291/8188). */
export async function icerikSifrele(p256dh, auth, duzMetin) {
  const uaPublic = unb64u(p256dh);
  const authSecret = unb64u(auth);
  const tuz = rastgele(16);
  const efemer = await crypto.subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, true, ["deriveBits"]);
  const asPublic = new Uint8Array(await crypto.subtle.exportKey("raw", efemer.publicKey));
  const uaKey = await crypto.subtle.importKey("raw", uaPublic, { name: "ECDH", namedCurve: "P-256" }, false, []);
  const ecdhBits = await crypto.subtle.deriveBits({ name: "ECDH", public: uaKey }, efemer.privateKey, 256);
  const ecdhKey = await crypto.subtle.importKey("raw", new Uint8Array(ecdhBits), "HKDF", false, ["deriveBits"]);
  // 1) IKM = HKDF(salt=auth, ikm=ecdh, info="WebPush: info\0" || ua_public || as_public)
  const bilgi = diziBirlestir([encoder.encode("WebPush: info\0"), uaPublic, asPublic]);
  const ikmBits = await crypto.subtle.deriveBits({ name: "HKDF", hash: "SHA-256", salt: authSecret, info: bilgi }, ecdhKey, 256);
  const ikmKey = await crypto.subtle.importKey("raw", new Uint8Array(ikmBits), "HKDF", false, ["deriveBits"]);
  // 2) CEK ve NONCE: salt=tuz, ikm=IKM
  const cekBits = await crypto.subtle.deriveBits({ name: "HKDF", hash: "SHA-256", salt: tuz, info: encoder.encode("Content-Encoding: aes128gcm\0") }, ikmKey, 128);
  const nonceBits = await crypto.subtle.deriveBits({ name: "HKDF", hash: "SHA-256", salt: tuz, info: encoder.encode("Content-Encoding: nonce\0") }, ikmKey, 96);
  const cek = await crypto.subtle.importKey("raw", new Uint8Array(cekBits), "AES-GCM", false, ["encrypt"]);
  const kayit = diziBirlestir([duzMetin, Uint8Array.of(2)]);
  const sifreli = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv: new Uint8Array(nonceBits), tagLength: 128 }, cek, kayit));
  // Başlık: salt(16) || rs(4 big-endian) || idlen(1)=65 || keyid(as_public 65)
  const rs = new Uint8Array(4);
  new DataView(rs.buffer).setUint32(0, KAYIT_BOYU);
  const baslik = diziBirlestir([tuz, rs, Uint8Array.of(asPublic.length), asPublic]);
  return diziBirlestir([baslik, sifreli]);
}

/** Service worker'ın okuyacağı mesaj (kişisel veri yok: yalnız başlık, kısa metin, link). */
export function mesajGovdesi(baslik, metin, url) {
  return encoder.encode(JSON.stringify({ baslik, metin, url }));
}

/** Tek aboneye gönderim. Dönen: {durum, sil} — sil=true ise abonelik ölmüş (404/410), çağıran siler. */
export async function webPushGonder(env, abone, duzMetin, simdi) {
  const govde = await icerikSifrele(abone.p256dh, abone.auth, duzMetin);
  const jwt = await vapidJwt(env, abone.endpoint, simdi);
  const yanit = await fetch(abone.endpoint, {
    method: "POST",
    headers: {
      Authorization: "vapid t=" + jwt + ", k=" + env.VAPID_ACIK_ANAHTAR,
      "Content-Encoding": "aes128gcm",
      "Content-Type": "application/octet-stream",
      TTL: String(BILDIRIM_TTL_SN),
    },
    body: govde,
  });
  return { durum: yanit.status, sil: yanit.status === 404 || yanit.status === 410 };
}
