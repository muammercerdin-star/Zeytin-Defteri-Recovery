// Fabrika anahtarı: görünen ad tarayıcıda şifrelenir, sunucu (Cloudflare) yalnız şifreli metni görür.
// Ofis kurulumda üretir; havuzcu telefonuna QR (adres #anahtar=… parçası sunucuya gitmez) ya da parola ile
// bir kez aktarılır. Giriş şifresinden ayrıdır. Sunucuda yalnız parmak izi (iz) durur.
// Ağ, CDN ve depolama yok: saf WebCrypto (tarayıcı ve Node testleri aynı kodu çalıştırır).

export const SURUM = "1";
export const SIR_BAYT = 20;
export const AD_EN_COK_BAYT = 80;
const ALFABE = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
const enc = new TextEncoder();
const dec = new TextDecoder();

export function b64u(bytes) {
  let s = "";
  for (const b of bytes) {
    s += String.fromCharCode(b);
  }
  return btoa(s).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

export function unb64u(text) {
  const raw = atob(text.replaceAll("-", "+").replaceAll("_", "/"));
  return Uint8Array.from(raw, (c) => c.charCodeAt(0));
}

export function yeniSir() {
  return crypto.getRandomValues(new Uint8Array(SIR_BAYT));
}

function base32(bytes) {
  let out = "";
  let acc = 0;
  let bits = 0;
  for (const b of bytes) {
    acc = (acc << 8) | b;
    bits += 8;
    while (bits >= 5) {
      bits -= 5;
      out += ALFABE[(acc >>> bits) & 31];
    }
  }
  if (bits > 0) {
    out += ALFABE[(acc << (5 - bits)) & 31];
  }
  return out;
}

/** Tek ağırlıklar (2i+1): tek bir harfin yanlış yazılması her zaman yakalanır. */
function denetimHarfi(govde) {
  let t = 0;
  for (let i = 0; i < govde.length; i++) {
    t = (t + (2 * i + 1) * ALFABE.indexOf(govde[i])) % 32;
  }
  return ALFABE[t];
}

/** Elle yazılabilen parola: 32 harf + 1 denetim harfi, 4'lü gruplar ("ABCD-EFGH-…-K"). */
export function parolaYaz(sir) {
  const govde = base32(sir);
  const tam = govde + denetimHarfi(govde);
  return tam.match(/.{1,4}/g).join("-");
}

/** Yazılan parolayı okur (boşluk/tire/küçük harf, O→0, I/L→1 düzeltilir); yanlışsa null. */
export function parolaOku(text) {
  if (typeof text !== "string") {
    return null;
  }
  const temiz = text.toUpperCase().replace(/[\s-]/g, "").replaceAll("O", "0").replaceAll("I", "1").replaceAll("L", "1");
  if (temiz.length !== 33 || [...temiz].some((c) => !ALFABE.includes(c))) {
    return null;
  }
  const govde = temiz.slice(0, 32);
  if (denetimHarfi(govde) !== temiz[32]) {
    return null;
  }
  const out = new Uint8Array(SIR_BAYT);
  let acc = 0;
  let bits = 0;
  let n = 0;
  for (const c of govde) {
    acc = (acc << 5) | ALFABE.indexOf(c);
    bits += 5;
    if (bits >= 8) {
      bits -= 8;
      out[n] = (acc >>> bits) & 255;
      n += 1;
    }
  }
  return out;
}

async function hkdf(sir, bilgi, bitSayisi) {
  const anahtar = await crypto.subtle.importKey("raw", sir, "HKDF", false, ["deriveBits"]);
  const params = { name: "HKDF", hash: "SHA-256", salt: enc.encode("zeytin-sira"), info: enc.encode(bilgi) };
  const bits = await crypto.subtle.deriveBits(params, anahtar, bitSayisi);
  return new Uint8Array(bits);
}

/** Sunucuda duran parmak izi (11 karakter): anahtarı vermez, doğru anahtar mı diye bakmaya yarar. */
export async function iz(sir, kisaAd) {
  return b64u(await hkdf(sir, "zeytin-sira/iz/" + kisaAd, 64));
}

/** AES-GCM 256 ad anahtarı (dışarı aktarılamaz). */
export async function adAnahtari(sir, kisaAd) {
  const ham = await hkdf(sir, "zeytin-sira/ad/" + kisaAd, 256);
  return crypto.subtle.importKey("raw", ham, { name: "AES-GCM" }, false, ["encrypt", "decrypt"]);
}

/** Görünen adı kısaltıp temizler; çok uzunsa keser (en çok 80 bayt). */
export function adTemizle(ad) {
  let temiz = String(ad || "").replace(/\s+/g, " ").trim();
  while (enc.encode(temiz).length > AD_EN_COK_BAYT) {
    temiz = temiz.slice(0, -1);
  }
  return temiz;
}

export async function adSifrele(anahtar, kisaAd, ad) {
  const temiz = adTemizle(ad);
  if (!temiz) {
    return null;
  }
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const params = { name: "AES-GCM", iv, additionalData: enc.encode("zeytin-sira|" + kisaAd) };
  const ct = new Uint8Array(await crypto.subtle.encrypt(params, anahtar, enc.encode(temiz)));
  const birlesik = new Uint8Array(iv.length + ct.length);
  birlesik.set(iv, 0);
  birlesik.set(ct, iv.length);
  return SURUM + "." + b64u(birlesik);
}

/** Çözülemezse (yanlış anahtar, başka fabrika, bozuk) null. */
export async function adCoz(anahtar, kisaAd, metin) {
  if (typeof metin !== "string" || !metin.startsWith(SURUM + ".")) {
    return null;
  }
  try {
    const birlesik = unb64u(metin.slice(2));
    const iv = birlesik.slice(0, 12);
    const params = { name: "AES-GCM", iv, additionalData: enc.encode("zeytin-sira|" + kisaAd) };
    const acik = await crypto.subtle.decrypt(params, anahtar, birlesik.slice(12));
    return dec.decode(acik);
  } catch {
    return null;
  }
}

/** TV için kısaltma: "Ayşe Demir" → "Ayşe D." */
export function kisalt(ad) {
  const parca = String(ad || "").trim().split(/\s+/).filter(Boolean);
  if (parca.length === 0) {
    return "";
  }
  if (parca.length === 1) {
    return parca[0];
  }
  const son = parca[parca.length - 1];
  return parca[0] + " " + son.charAt(0).toLocaleUpperCase("tr-TR") + ".";
}
