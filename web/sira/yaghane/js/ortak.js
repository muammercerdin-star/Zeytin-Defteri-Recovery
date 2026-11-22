// Panel sayfalarının ortak parçaları: API çağrısı, güvenli depolama, fabrika anahtarı, küçük DOM yardımcıları.
// Veri ekrana yalnız textContent ile yazılır (HTML olarak hiçbir zaman).
import { adAnahtari, iz, parolaOku, parolaYaz } from "./anahtar.js";

export function depo() {
  try {
    const d = window.localStorage;
    d.setItem("zs_deneme", "1");
    d.removeItem("zs_deneme");
    return d;
  } catch {
    const bellek = new Map();
    return {
      get length() {
        return bellek.size;
      },
      key(i) {
        return [...bellek.keys()][i] || null;
      },
      getItem(k) {
        return bellek.has(k) ? bellek.get(k) : null;
      },
      setItem(k, v) {
        bellek.set(k, String(v));
      },
      removeItem(k) {
        bellek.delete(k);
      },
    };
  }
}

export async function api(yol, yontem = "GET", govde) {
  const init = { method: yontem, credentials: "same-origin", headers: {} };
  if (govde !== undefined) {
    init.headers["Content-Type"] = "application/json";
    init.body = JSON.stringify(govde);
  }
  const res = await fetch(yol, init);
  let body = null;
  try {
    body = await res.json();
  } catch {
    body = null;
  }
  return { status: res.status, body };
}

export function el(etiket, sinif, metin) {
  const e = document.createElement(etiket);
  if (sinif) {
    e.className = sinif;
  }
  if (metin !== undefined && metin !== null) {
    e.textContent = String(metin);
  }
  return e;
}

export function dugme(metin, sinif, tik) {
  const b = el("button", sinif, metin);
  b.type = "button";
  b.addEventListener("click", tik);
  return b;
}

export function kg(n) {
  return Number(n || 0).toLocaleString("tr-TR", { maximumFractionDigits: 1 }) + " kg";
}

export function saat(ms) {
  if (!ms) {
    return "";
  }
  return new Date(Number(ms)).toLocaleTimeString("tr-TR", { hour: "2-digit", minute: "2-digit", timeZone: "Europe/Istanbul" });
}

export function randiman(zeytinKg, yagKg) {
  if (!yagKg) {
    return "";
  }
  return (Number(zeytinKg) / Number(yagKg)).toLocaleString("tr-TR", { maximumFractionDigits: 1 });
}

// ---------------------------------------------------------------- fabrika anahtarı (bu cihazda)

const ANAHTAR_ON_EK = "zs_anahtar_";
const BEKLEYEN = "zs_bekleyen_anahtar";

/** QR ile gelen "#anahtar=…" parçası: adres çubuğundan hemen silinir, doğrulanana kadar oturumda bekler. */
export function qrAnahtariniAl() {
  const m = /[#&]anahtar=([0-9A-Za-z-]+)/.exec(window.location.hash || "");
  if (!m) {
    return;
  }
  try {
    window.sessionStorage.setItem(BEKLEYEN, m[1]);
  } catch {
    // oturum deposu yoksa parola elle girilir
  }
  window.history.replaceState(null, "", window.location.pathname + window.location.search);
}

/** Cihazdaki anahtarı (ya da QR'dan bekleyeni) sunucudaki izle doğrular. Dönen: {anahtar, durum}. */
export async function cihazAnahtari(d, kisaAd, sunucuIzi) {
  let bekleyen = null;
  try {
    bekleyen = window.sessionStorage.getItem(BEKLEYEN);
    window.sessionStorage.removeItem(BEKLEYEN);
  } catch {
    bekleyen = null;
  }
  if (bekleyen && sunucuIzi) {
    const sir = parolaOku(bekleyen);
    if (sir && (await iz(sir, kisaAd)) === sunucuIzi) {
      d.setItem(ANAHTAR_ON_EK + kisaAd, parolaYaz(sir));
    }
  }
  const kayitli = d.getItem(ANAHTAR_ON_EK + kisaAd);
  if (!kayitli) {
    return { anahtar: null, durum: sunucuIzi ? "yok" : "kurulmadi" };
  }
  const sir = parolaOku(kayitli);
  if (!sir || !sunucuIzi || (await iz(sir, kisaAd)) !== sunucuIzi) {
    return { anahtar: null, durum: sunucuIzi ? "eski" : "kurulmadi" };
  }
  return { anahtar: await adAnahtari(sir, kisaAd), durum: "tamam", parola: kayitli };
}

/** Elle yazılan parolayı doğrulayıp kaydeder. */
export async function parolaKaydet(d, kisaAd, sunucuIzi, yazilan) {
  const sir = parolaOku(yazilan);
  if (!sir) {
    return "Parola hatalı yazılmış; harfleri kontrol et.";
  }
  if ((await iz(sir, kisaAd)) !== sunucuIzi) {
    return "Bu parola bu fabrikanın anahtarı değil.";
  }
  d.setItem(ANAHTAR_ON_EK + kisaAd, parolaYaz(sir));
  return null;
}

export function anahtariKaydet(d, kisaAd, parola) {
  d.setItem(ANAHTAR_ON_EK + kisaAd, parola);
}

export function anahtariSil(d, kisaAd) {
  d.removeItem(ANAHTAR_ON_EK + kisaAd);
}

/** Çıkış: bu cihazdaki oturum biter; anahtar ve telefonlar da silinir (ortak bilgisayarda iz kalmasın). */
export async function cikisYap(d, kisaAd) {
  await api("/api/yaghane/cikis", "POST", {}).catch(() => null);
  anahtariSil(d, kisaAd);
  const sil = [];
  for (let i = 0; i < d.length; i++) {
    const k = d.key(i);
    if (k && k.startsWith("zs_")) {
      sil.push(k);
    }
  }
  for (const k of sil) {
    d.removeItem(k);
  }
  window.location.href = "/yaghane/";
}
