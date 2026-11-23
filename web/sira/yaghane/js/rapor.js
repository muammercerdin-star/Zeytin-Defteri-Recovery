// Ofis "Kayıtlar" bölümü: gün gün gelen müşteri, zeytin, işlenen, yağ, randıman, bekleme.
// Kayıtlarda isim, kod ya da link yoktur. Saf yardımcılar (aralık, süre, CSV) testte DOM'suz çalışır.
import { api, dugme, el, kg, randiman } from "./ortak.js";

export const ARALIKLAR = [
  { tur: "bugun", ad: "Bugün" },
  { tur: "hafta", ad: "Son 7 gün" },
  { tur: "ay", ad: "Son 30 gün" },
  { tur: "sezon", ad: "Bu sezon" },
];

const GUN_MS = 24 * 60 * 60 * 1000;

function gunEkle(gun, fark) {
  return new Date(Date.parse(gun + "T00:00:00Z") + fark * GUN_MS).toISOString().slice(0, 10);
}

/** Yağhane gününe göre aralık; sezon 1 Eylül'de başlar (Ocak–Ağustos önceki yılın sezonudur). */
export function aralikHesapla(bugun, tur) {
  if (tur === "hafta") {
    return { bas: gunEkle(bugun, -6), son: bugun };
  }
  if (tur === "ay") {
    return { bas: gunEkle(bugun, -29), son: bugun };
  }
  if (tur === "sezon") {
    const yil = Number(bugun.slice(0, 4));
    const ay = Number(bugun.slice(5, 7));
    return { bas: (ay >= 9 ? yil : yil - 1) + "-09-01", son: bugun };
  }
  return { bas: bugun, son: bugun };
}

/** Ortalama bekleme (geliş → işleme başlama). */
export function sureYaz(toplamMs, adet) {
  if (!adet) {
    return "–";
  }
  const dk = Math.round(Number(toplamMs) / Number(adet) / 60000);
  if (dk < 60) {
    return dk + " dk";
  }
  return Math.floor(dk / 60) + " sa " + (dk % 60) + " dk";
}

export function gunYaz(gun) {
  const t = new Date(gun + "T12:00:00Z");
  return t.toLocaleDateString("tr-TR", { day: "numeric", month: "short", weekday: "short", timeZone: "UTC" });
}

function sayiCsv(n) {
  if (n === null || n === undefined) {
    return "";
  }
  return String(Math.round(Number(n) * 10) / 10).replace(".", ",");
}

/** Excel'in Türkçe ayarıyla açılan CSV: noktalı virgül, ondalık virgül, UTF-8 BOM. */
export function csvOlustur(veri) {
  const baslik = ["Gün", "Gelen müşteri", "Gelen zeytin kg", "İşlenen müşteri", "İşlenen zeytin kg", "Çıkan yağ kg", "Randıman (kg zeytin / 1 kg yağ)", "Ort. bekleme dk", "İptal"];
  const satirlar = [baslik];
  const ekle = (g, ad) => {
    const rand = g.yag_kg ? Number(g.yagli_kg) / Number(g.yag_kg) : null;
    const bekleme = g.baslayan ? Number(g.bekleme_ms) / Number(g.baslayan) / 60000 : null;
    satirlar.push([ad, sayiCsv(g.gelen), sayiCsv(g.gelen_kg), sayiCsv(g.islenen), sayiCsv(g.islenen_kg), sayiCsv(g.yag_kg), sayiCsv(rand), sayiCsv(bekleme), sayiCsv(g.iptal)]);
  };
  for (const g of veri.gunler) {
    ekle(g, g.gun);
  }
  ekle(veri.toplam, "TOPLAM");
  return "﻿" + satirlar.map((s) => s.join(";")).join("\r\n") + "\r\n";
}

// ---------------------------------------------------------------- ekran

const durum = { tur: "bugun", veri: null, bugun: null, kisaAd: "yaghane", yukleniyor: false };
const $ = (id) => document.getElementById(id);

function kutu(etiket, deger) {
  const k = el("div", "kutu");
  k.append(el("div", "etiket", etiket), el("div", "deger", deger));
  return k;
}

function ciz() {
  const v = durum.veri;
  for (const b of $("rapor-aralik").querySelectorAll("button")) {
    b.setAttribute("aria-pressed", String(b.dataset.tur === durum.tur));
  }
  const ust = $("rapor-ozet");
  ust.textContent = "";
  const tablo = $("rapor-tablo");
  tablo.textContent = "";
  if (!v) {
    return;
  }
  const t = v.toplam;
  const eskiVar = v.gunler.some((g) => g.ozet);
  ust.append(
    kutu("Gelen müşteri", eskiVar ? String(t.gelen) + "+" : String(t.gelen)),
    kutu("Gelen zeytin", kg(t.gelen_kg)),
    kutu("İşlenen zeytin", kg(t.islenen_kg)),
    kutu("Çıkan yağ", kg(t.yag_kg)),
    kutu("Randıman", t.yag_kg ? randiman(t.yagli_kg, t.yag_kg) + " kg / 1 kg yağ" : "–"),
    kutu("Ort. bekleme", sureYaz(t.bekleme_ms, t.baslayan))
  );
  if (!v.gunler.length) {
    const tr = el("tr");
    const td = el("td", "not", "Bu aralıkta kayıt yok.");
    td.colSpan = 8;
    tr.append(td);
    tablo.append(tr);
  }
  for (const g of v.gunler) {
    const tr = el("tr");
    const bos = (x) => (x === null || x === undefined ? "–" : x);
    tr.append(
      el("td", "", gunYaz(g.gun)),
      el("td", "", bos(g.gelen === null ? null : String(g.gelen))),
      el("td", "", g.gelen_kg === null ? "–" : kg(g.gelen_kg)),
      el("td", "", String(g.islenen) + " · " + kg(g.islenen_kg)),
      el("td", "", g.yag_kg ? kg(g.yag_kg) : "–"),
      el("td", "", g.yag_kg ? randiman(g.yagli_kg, g.yag_kg) : "–"),
      el("td", "", sureYaz(g.bekleme_ms, g.baslayan)),
      el("td", "", bos(g.iptal === null ? null : String(g.iptal)))
    );
    tablo.append(tr);
  }
  $("rapor-not").textContent = eskiVar
    ? "90 günden eski günler yalnız toplam olarak saklanır; o günlerin gelen müşteri, bekleme ve iptal sayısı yoktur."
    : "Kayıtlarda isim ve kod yoktur; yalnız gün toplamları.";
}

async function yukle(tur) {
  if (durum.yukleniyor) {
    return;
  }
  durum.yukleniyor = true;
  durum.tur = tur || durum.tur;
  let yol = "/api/yaghane/rapor";
  if (durum.bugun) {
    const a = aralikHesapla(durum.bugun, durum.tur);
    yol += "?bas=" + a.bas + "&son=" + a.son;
  }
  const r = await api(yol).catch(() => null);
  durum.yukleniyor = false;
  if (!r || r.status !== 200) {
    $("rapor-not").textContent = "Kayıtlar alınamadı; birazdan yeniden denenecek.";
    return;
  }
  const ilk = !durum.bugun;
  durum.bugun = r.body.bugun;
  durum.veri = r.body;
  if (ilk && durum.tur !== "bugun") {
    yukle(durum.tur);
    return;
  }
  ciz();
}

function indir() {
  if (!durum.veri) {
    return;
  }
  const blob = new Blob([csvOlustur(durum.veri)], { type: "text/csv;charset=utf-8" });
  const a = el("a");
  a.href = URL.createObjectURL(blob);
  a.download = durum.kisaAd + "-kayitlar-" + durum.veri.bas + "_" + durum.veri.son + ".csv";
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 5000);
}

/** Ofis sayfası açılınca bir kez çağrılır; dakikada bir tazelenir. */
export function raporKur(kisaAdVer) {
  const grup = $("rapor-aralik");
  for (const a of ARALIKLAR) {
    const b = dugme(a.ad, "kucuk", () => yukle(a.tur));
    b.dataset.tur = a.tur;
    grup.append(b);
  }
  $("rapor-indir").addEventListener("click", () => {
    durum.kisaAd = kisaAdVer() || "yaghane";
    indir();
  });
  yukle("bugun");
  setInterval(() => {
    if (!document.hidden) {
      yukle();
    }
  }, 60000);
}
