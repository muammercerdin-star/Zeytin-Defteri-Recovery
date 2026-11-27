// Saatlik temizlik (Cloudflare zamanlanmış tetikleyici). KVKK §3: şifreli ad ve elle giriş özeti 24 saat,
// satırlar 90 gün; ondan sonra yalnız isimsiz gün toplamı kalır. Loga kişisel veri yazılmaz.
import { GUN_MS } from "./zaman.mjs";

export const AD_OMRU_MS = GUN_MS;
export const BILDIRIM_OMRU_MS = GUN_MS;
export const SATIR_OMRU_MS = 90 * GUN_MS;
export const ISLEM_KAYDI_OMRU_MS = 3 * GUN_MS;
export const YEDEK_OMRU_MS = 2 * GUN_MS;
export const SAYAC_OMRU_GUN = 30;

export async function temizle(db, simdi) {
  const sinirAd = simdi - AD_OMRU_MS;
  const sinirSatir = simdi - SATIR_OMRU_MS;
  // Gün toplamı, o günün ilk satırı 90 günü doldurduğunda bir kez yazılır (o an günün bütün satırları duruyor).
  // Sonraki saatlerde kalan satırlarla yeniden yazılmaz: yoksa silinenler toplamdan düşerdi.
  const ozet =
    "INSERT INTO gun_ozet (yaghane_id, gun, toplam_kg, toplam_yag_kg, islem_sayisi) " +
    "SELECT yaghane_id, gun, COALESCE(SUM(kg), 0), COALESCE(SUM(yag_kg), 0), COUNT(*) FROM sira " +
    "WHERE durum = 'bitti' AND gun IN (SELECT DISTINCT gun FROM sira WHERE gelis <= ?) GROUP BY yaghane_id, gun " +
    "ON CONFLICT(yaghane_id, gun) DO NOTHING";
  await db.batch([
    db.prepare(ozet).bind(sinirSatir),
    db.prepare("UPDATE sira SET ad_sifreli = NULL, bul_tuz = NULL, bul_ozet = NULL WHERE gelis <= ? AND (ad_sifreli IS NOT NULL OR bul_ozet IS NOT NULL)").bind(sinirAd),
    db.prepare("DELETE FROM sira WHERE gelis <= ?").bind(sinirSatir),
    db.prepare("DELETE FROM oturum WHERE bitis <= ?").bind(simdi),
    db.prepare("DELETE FROM giris_deneme WHERE zaman <= ?").bind(simdi - GUN_MS),
    db.prepare("DELETE FROM islem_kaydi WHERE zaman <= ?").bind(simdi - ISLEM_KAYDI_OMRU_MS),
    db.prepare("DELETE FROM kod_yedegi WHERE ayrilma <= ?").bind(simdi - YEDEK_OMRU_MS),
    db.prepare("DELETE FROM sayac WHERE gun < ?").bind(new Date(simdi - SAYAC_OMRU_GUN * GUN_MS).toISOString().slice(0, 10)),
  ]);
  // Bildirim aboneleri ayrı silinir: göç henüz uygulanmadıysa (tablo yoksa) yukarıdaki toplu temizlik etkilenmesin.
  await db
    .prepare("DELETE FROM bildirim_abone WHERE olusturma <= ?")
    .bind(simdi - BILDIRIM_OMRU_MS)
    .run()
    .catch(() => {});
}
