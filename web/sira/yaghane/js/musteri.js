// Yeni müşteri isteğinin gövdesi: sunucuya giden alanlar burada tek yerde kurulur.
// Ad yalnız şifreli; telefonun yalnız son 2 hanesi (sunucu tuzlu özetini saklar); numaranın kendisi hiç gitmez.
import { adSifrele } from "./anahtar.js";
import { sonIki, telefonDuzelt } from "./telefon.js";

export const IZINLI_ALANLAR = ["op_id", "ad_sifreli", "son2", "kasa", "kg", "yedek"];

/** form: {ad, telefon, kasa, kg}. anahtar yoksa ad gönderilmez (hata döner). */
export async function yeniMusteriGovdesi(form, adAnahtari, kisaAd, opId, yedek) {
  const kasa = Number(String(form.kasa || "").trim());
  const kg = Number(String(form.kg || "").replace(",", ".").trim());
  if (!Number.isInteger(kasa) || kasa < 0 || kasa > 500) {
    return { hata: "Kasa sayısını yaz." };
  }
  if (!Number.isFinite(kg) || kg < 1 || kg > 100000) {
    return { hata: "Kantar kilosunu yaz." };
  }
  let tel = null;
  if (String(form.telefon || "").trim()) {
    tel = telefonDuzelt(form.telefon);
    if (!tel) {
      return { hata: "Telefon numarası 05xx xxx xx xx biçiminde olmalı." };
    }
  }
  const govde = { op_id: opId, kasa, kg };
  const ad = String(form.ad || "").trim();
  if (ad) {
    if (!adAnahtari) {
      return { hata: "Bu cihazda fabrika anahtarı yok; ismi boş bırak ya da ofisten anahtarı aktar." };
    }
    govde.ad_sifreli = await adSifrele(adAnahtari, kisaAd, ad);
  }
  if (tel) {
    govde.son2 = sonIki(tel);
  }
  if (yedek) {
    govde.yedek = { kod: yedek.kod, token: yedek.token };
  }
  return { govde, tel };
}
