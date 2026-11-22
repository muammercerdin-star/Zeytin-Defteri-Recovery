// Türkiye saati: 2016'dan beri yıl boyu UTC+3, yaz saati yok.
// Yağhane günü ayarlanabilir bir saatte başlar (varsayılan 05:00): gece mesaisi bir önceki güne yazılır.

export const TR_OFFSET_MS = 3 * 60 * 60 * 1000;
export const DAKIKA = 60 * 1000;
export const SAAT = 60 * DAKIKA;
export const GUN_MS = 24 * SAAT;

const SAAT_DESENI = /^([01][0-9]|2[0-3]):([0-5][0-9])$/;

/** "05:00" gibi bir saat mi? */
export function gecerliSaat(text) {
  return typeof text === "string" && SAAT_DESENI.test(text);
}

/** "05:30" → 330 dakika. */
export function dakikaOf(text) {
  const m = SAAT_DESENI.exec(text);
  if (!m) {
    return 5 * 60;
  }
  return Number(m[1]) * 60 + Number(m[2]);
}

function isoTarih(ms) {
  return new Date(ms).toISOString().slice(0, 10);
}

/** Yağhane günü (YYYY-AA-GG): Türkiye saatinden gün başlangıcı çıkarılınca kalan takvim günü. */
export function yaghaneGunu(ms, baslangic) {
  const kaydir = dakikaOf(baslangic) * DAKIKA;
  return isoTarih(ms + TR_OFFSET_MS - kaydir);
}

/** Bir önceki yağhane günü. */
export function oncekiGun(gun) {
  const ms = Date.parse(gun + "T00:00:00Z");
  return isoTarih(ms - GUN_MS);
}

/** Türkiye saatiyle "SS:DD". */
export function saatDakika(ms) {
  return new Date(ms + TR_OFFSET_MS).toISOString().slice(11, 16);
}

/** Bulut kotası UTC gece yarısı sıfırlanır: istek sayacı UTC takvim gününe göre tutulur. */
export function utcGunu(ms) {
  return isoTarih(ms);
}
