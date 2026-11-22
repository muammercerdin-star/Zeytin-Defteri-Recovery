// Şifre özeti, oturum, deneme sınırı, token ve kod üretimi. Yalnız WebCrypto; loga hiçbir şey yazılmaz.
import { DAKIKA, GUN_MS } from "./zaman.mjs";

const encoder = new TextEncoder();

/** Cloudflare Workers PBKDF2'yi en çok 100.000 tekrarla çalıştırır. Tekrar sayısı özetin içinde saklanır. */
export const PBKDF2_EN_COK = 100000;
export const PBKDF2_VARSAYILAN = 100000;
export const OTURUM_GUN = 30;
export const OTURUM_CEREZI = "zs_oturum";
export const GIRIS_PENCERE_MS = 10 * DAKIKA;
export const GIRIS_HATA_SINIRI = 5;
export const BUL_PENCERE_MS = 10 * DAKIKA;
export const BUL_SINIRI = 10;

export function b64u(bytes) {
  let s = "";
  for (const b of bytes) {
    s += String.fromCharCode(b);
  }
  return btoa(s).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

export function unb64u(text) {
  const padded = text.replaceAll("-", "+").replaceAll("_", "/");
  const raw = atob(padded);
  return Uint8Array.from(raw, (c) => c.charCodeAt(0));
}

export function rastgele(n) {
  return crypto.getRandomValues(new Uint8Array(n));
}

export async function sha256(text) {
  const digest = await crypto.subtle.digest("SHA-256", encoder.encode(text));
  return b64u(new Uint8Array(digest));
}

export async function hmac(anahtar, text) {
  const key = await crypto.subtle.importKey("raw", encoder.encode(anahtar), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign("HMAC", key, encoder.encode(text));
  return b64u(new Uint8Array(sig));
}

/** Sabit süreli karşılaştırma (özetler aynı uzunlukta). */
export function esit(a, b) {
  if (typeof a !== "string" || typeof b !== "string" || a.length !== b.length) {
    return false;
  }
  let fark = 0;
  for (let i = 0; i < a.length; i++) {
    fark |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return fark === 0;
}

async function pbkdf2(sifre, tuz, tekrar) {
  const key = await crypto.subtle.importKey("raw", encoder.encode(sifre), "PBKDF2", false, ["deriveBits"]);
  const params = { name: "PBKDF2", hash: "SHA-256", salt: tuz, iterations: tekrar };
  const bits = await crypto.subtle.deriveBits(params, key, 256);
  return b64u(new Uint8Array(bits));
}

/** "pbkdf2-sha256$<tekrar>$<tuz>$<özet>" */
export async function sifreOzeti(sifre, tekrar = PBKDF2_VARSAYILAN) {
  if (!Number.isInteger(tekrar) || tekrar < 1000 || tekrar > PBKDF2_EN_COK) {
    throw new Error("PBKDF2_TEKRAR");
  }
  const tuz = rastgele(16);
  const ozet = await pbkdf2(sifre, tuz, tekrar);
  return "pbkdf2-sha256$" + tekrar + "$" + b64u(tuz) + "$" + ozet;
}

export async function sifreDogru(sifre, kayit) {
  if (typeof sifre !== "string" || typeof kayit !== "string") {
    return false;
  }
  const parca = kayit.split("$");
  if (parca.length !== 4 || parca[0] !== "pbkdf2-sha256") {
    return false;
  }
  const tekrar = Number(parca[1]);
  if (!Number.isInteger(tekrar) || tekrar < 1000 || tekrar > PBKDF2_EN_COK) {
    return false;
  }
  const ozet = await pbkdf2(sifre, unb64u(parca[2]), tekrar);
  return esit(ozet, parca[3]);
}

/** Çiftçi linkindeki token: 128 bit, 22 karakter. Tahmin edilemez. */
export function yeniToken() {
  return b64u(rastgele(16));
}

export function tokenBicimi(text) {
  return typeof text === "string" && /^[A-Za-z0-9_-]{22}$/.test(text);
}

/** 4 haneli RASTGELE kod (ardışık değil). Tekilliği veritabanı sağlar. */
export function yeniKod() {
  const sayi = new Uint32Array(1);
  crypto.getRandomValues(sayi);
  const n = 1000 + (sayi[0] % 9000);
  return String(n);
}

export function kodBicimi(text) {
  return typeof text === "string" && /^[0-9]{4}$/.test(text);
}

/** IP saklanmaz: gizli tuz + günle HMAC'lenmiş özeti yalnız deneme sınırı için 1 gün durur. */
export async function ipOzeti(request, gizliTuz, simdi) {
  const ip = request.headers.get("CF-Connecting-IP") || "yerel";
  const gun = Math.floor(simdi / GUN_MS);
  return hmac(gizliTuz, "ip|" + gun + "|" + ip);
}

/** Elle girişin ikinci bilgisi (telefonun son 2 hanesi): satıra özel tuz + gizli tuzla HMAC. Düz yazı saklanmaz. */
export async function bulOzeti(gizliTuz, satirTuzu, kod, ek) {
  return hmac(gizliTuz, "bul|" + satirTuzu + "|" + kod + "|" + ek);
}

/** Son pencerede kaç hata/deneme var? */
export async function denemeSayisi(db, anahtar, simdi, pencere) {
  const row = await db
    .prepare("SELECT COUNT(*) AS n, MIN(zaman) AS ilk FROM giris_deneme WHERE anahtar = ? AND zaman > ?")
    .bind(anahtar, simdi - pencere)
    .first();
  return { n: Number(row?.n || 0), ilk: Number(row?.ilk || simdi) };
}

export async function denemeYaz(db, anahtar, simdi) {
  await db.prepare("INSERT INTO giris_deneme (anahtar, zaman) VALUES (?, ?)").bind(anahtar, simdi).run();
}

export async function denemeleriSil(db, anahtar) {
  await db.prepare("DELETE FROM giris_deneme WHERE anahtar = ?").bind(anahtar).run();
}

export function oturumCerezi(deger, simdi) {
  const yas = OTURUM_GUN * 24 * 60 * 60;
  void simdi;
  return OTURUM_CEREZI + "=" + deger + "; Path=/; Max-Age=" + yas + "; HttpOnly; Secure; SameSite=Strict";
}

export function oturumSilCerezi() {
  return OTURUM_CEREZI + "=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Strict";
}

export function cerezOku(request) {
  const header = request.headers.get("Cookie") || "";
  for (const parca of header.split(";")) {
    const [ad, ...deger] = parca.trim().split("=");
    if (ad === OTURUM_CEREZI) {
      return deger.join("=");
    }
  }
  return "";
}

/** Oturum açar: tarayıcıya rastgele değer, veritabanına yalnız özeti. */
export async function oturumAc(db, kullaniciId, simdi) {
  const deger = b64u(rastgele(32));
  const ozet = await sha256(deger);
  const bitis = simdi + OTURUM_GUN * GUN_MS;
  await db.prepare("INSERT INTO oturum (ozet, kullanici_id, bitis) VALUES (?, ?, ?)").bind(ozet, kullaniciId, bitis).run();
  return deger;
}

/** Çerezden oturum: kullanıcı, rol ve yağhane ayarları. Yoksa null. */
export async function oturumBul(db, request, simdi) {
  const deger = cerezOku(request);
  if (!/^[A-Za-z0-9_-]{43}$/.test(deger)) {
    return null;
  }
  const ozet = await sha256(deger);
  const sql =
    "SELECT o.ozet, k.id AS kullanici_id, k.kullanici_adi, k.rol, y.id AS yaghane_id, y.ad, y.kisa_ad, " +
    "y.kapasite_kg_saat, y.gun_baslangic, y.anahtar_izi " +
    "FROM oturum o JOIN kullanici k ON k.id = o.kullanici_id JOIN yaghane y ON y.id = k.yaghane_id " +
    "WHERE o.ozet = ? AND o.bitis > ?";
  return db.prepare(sql).bind(ozet, simdi).first();
}

export async function oturumKapat(db, request) {
  const deger = cerezOku(request);
  if (!deger) {
    return;
  }
  const ozet = await sha256(deger);
  await db.prepare("DELETE FROM oturum WHERE ozet = ?").bind(ozet).run();
}
