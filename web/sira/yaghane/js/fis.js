// Yazdırılabilir sıra fişi (58 mm termal yazıcı, telefonun ya da bilgisayarın tarayıcısından yazdırılır).
// İçerik: fabrika adı, sıra no, kod, QR (çiftçi linki), kasa ve kg, tarih ve saat; elle giriş için kısa adres.
// Çiftçinin adı yalnız bu cihazda çözülmüşse yazılır; fiş sunucuya hiçbir şey göndermez.
import { qrcode } from "./vendor/qrcode.js";

/** Türkiye saatiyle "22.11.2026 21:14". */
export function fisZamani(ms) {
  const t = new Date(Number(ms));
  const gun = t.toLocaleDateString("tr-TR", { day: "2-digit", month: "2-digit", year: "numeric", timeZone: "Europe/Istanbul" });
  const saat = t.toLocaleTimeString("tr-TR", { hour: "2-digit", minute: "2-digit", timeZone: "Europe/Istanbul" });
  return gun + " " + saat;
}

function kgMetni(kg) {
  return Number(kg || 0).toLocaleString("tr-TR", { maximumFractionDigits: 1 }) + " kg";
}

/**
 * Fişin satırları (saf; testler bunu denetler).
 * b: {fabrika, kisaAd, siraNo, kod, kasa, kg, zaman, ad, link, site}
 */
export function fisSatirlari(b) {
  const satirlar = [{ tur: "fabrika", metin: b.fabrika }];
  satirlar.push({ tur: "etiket", metin: "SIRA NO" });
  satirlar.push({ tur: "no", metin: b.siraNo ? String(b.siraNo) : "–" });
  if (!b.siraNo) {
    satirlar.push({ tur: "not", metin: "Sıra no bağlantı gelince verilecek" });
  }
  satirlar.push({ tur: "kod", metin: "Kod " + b.kod });
  if (b.ad) {
    satirlar.push({ tur: "ad", metin: b.ad });
  }
  satirlar.push({ tur: "bilgi", metin: b.kasa + " kasa · " + kgMetni(b.kg) });
  satirlar.push({ tur: "bilgi", metin: fisZamani(b.zaman) });
  if (b.link) {
    satirlar.push({ tur: "qr", metin: b.link });
    satirlar.push({ tur: "not", metin: "Sıranı görmek için QR'ı okut" });
  }
  const site = (b.site || "sira.zeytindefteri.tr").replace(/^https?:\/\//, "");
  satirlar.push({ tur: "not", metin: "Ya da: " + site + "/bul" });
  satirlar.push({ tur: "not", metin: "Yağhane: " + b.kisaAd + " · Kod: " + b.kod });
  satirlar.push({ tur: "not", metin: "Son 2 hane: telefonun (yoksa kasa sayın)" });
  return satirlar;
}

/** Linkin QR kodunu tuvale çizer (fiş ve ofis ekranındaki büyük QR ortak kullanır). */
export function qrTuvali(link) {
  const qr = qrcode(0, "M");
  qr.addData(link);
  qr.make();
  const n = qr.getModuleCount();
  const olcek = 8;
  const kenar = 2 * olcek;
  const tuval = document.createElement("canvas");
  tuval.className = "fis-qr";
  tuval.width = n * olcek + 2 * kenar;
  tuval.height = n * olcek + 2 * kenar;
  tuval.setAttribute("role", "img");
  tuval.setAttribute("aria-label", "Sıra linki QR kodu");
  const c = tuval.getContext("2d");
  c.fillStyle = "#fff";
  c.fillRect(0, 0, tuval.width, tuval.height);
  c.fillStyle = "#000";
  for (let y = 0; y < n; y++) {
    for (let x = 0; x < n; x++) {
      if (qr.isDark(y, x)) {
        c.fillRect(kenar + x * olcek, kenar + y * olcek, olcek, olcek);
      }
    }
  }
  return tuval;
}

/** Fişi #fis-alan içine kurar ve yazdırma penceresini açar. */
export function fisYazdir(b) {
  const alan = document.getElementById("fis-alan");
  if (!alan) {
    return false;
  }
  alan.textContent = "";
  for (const s of fisSatirlari(b)) {
    if (s.tur === "qr") {
      alan.append(qrTuvali(s.metin));
      continue;
    }
    const p = document.createElement("p");
    p.className = "fis-" + s.tur;
    p.textContent = s.metin;
    alan.append(p);
  }
  window.print();
  return true;
}
