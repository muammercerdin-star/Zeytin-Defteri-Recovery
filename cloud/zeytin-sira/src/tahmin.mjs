// Tahmini saat (İP9 §4): tahmini = şimdi + (işlenen kalan kg + öndekilerin kg toplamı) / kapasite.
// Saf hesap: veritabanı ve saat dışarıdan gelir. Gerçek saatlerle kendini düzeltme ikinci aşamadır.
import { SAAT, DAKIKA, saatDakika } from "./zaman.mjs";

export const YUVARLAMA_MS = 10 * DAKIKA;

/** Çiftçiye "civarı" diye gösterilen saat: 10 dakikaya YUKARI yuvarlanır (erken gelip beklemesin). */
export function yukariYuvarla(ms) {
  return Math.ceil(ms / YUVARLAMA_MS) * YUVARLAMA_MS;
}

function kapasiteOf(kapasite) {
  const k = Number(kapasite);
  if (!Number.isFinite(k) || k <= 0) {
    return 1200;
  }
  return k;
}

/** İşlenen satırın kalan kilosu: başlamadan bu yana kapasite kadar işlendiği varsayılır; eksiye düşmez. */
export function kalanKg(satir, kapasite, simdi) {
  const k = kapasiteOf(kapasite);
  const gecen = Math.max(0, simdi - Number(satir.baslama || simdi));
  const islenen = (gecen / SAAT) * k;
  return Math.max(0, Number(satir.kg) - islenen);
}

/**
 * Sıradaki ve işlenen satırlar için tahmin.
 * satirlar: {id, durum, konum, kg, baslama} (bitti/iptal yok sayılır).
 * Dönen: { byId: {id: {baslangic, bitis}}, kuyrukSonu, bekleyen, bekleyenKg }
 */
export function tahminEt(satirlar, kapasite, simdi) {
  const k = kapasiteOf(kapasite);
  const isleniyor = satirlar.filter((s) => s.durum === "isleniyor");
  const sirada = satirlar.filter((s) => s.durum === "sirada");
  sirada.sort((a, b) => Number(a.konum) - Number(b.konum));
  const byId = {};
  let kalan = 0;
  for (const s of isleniyor) {
    kalan += kalanKg(s, k, simdi);
    const bitis = Number(s.baslama || simdi) + (Number(s.kg) / k) * SAAT;
    byId[s.id] = { baslangic: Number(s.baslama || simdi), bitis: Math.max(simdi, bitis) };
  }
  let imlec = simdi + (kalan / k) * SAAT;
  let bekleyenKg = 0;
  for (const s of sirada) {
    const sure = (Number(s.kg) / k) * SAAT;
    byId[s.id] = { baslangic: imlec, bitis: imlec + sure };
    imlec += sure;
    bekleyenKg += Number(s.kg);
  }
  return { byId, kuyrukSonu: imlec, bekleyen: sirada.length, bekleyenKg };
}

/** Bir satırın önünde sırada bekleyen kişi sayısı (işlenen sayılmaz; taslaktaki "önünüzde 2 kişi"). */
export function onundekiler(satirlar, satir) {
  if (satir.durum !== "sirada") {
    return 0;
  }
  let n = 0;
  for (const s of satirlar) {
    if (s.durum === "sirada" && Number(s.konum) < Number(satir.konum)) {
      n += 1;
    }
  }
  return n;
}

/** Ekranda gösterilecek yuvarlanmış saat. */
export function gosterilenSaat(ms) {
  return saatDakika(yukariYuvarla(ms));
}
