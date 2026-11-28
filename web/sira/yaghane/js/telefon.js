// Telefon numarası SUNUCUYA GİTMEZ. Yalnız havuzcunun tarayıcısında, o günün kaydında durur; gün başlangıcında
// silinir. WhatsApp ve SMS linkleri havuzcunun telefonunda açılır, mesajı havuzcu kendisi gönderir.

/** "0532 111 22 33", "+90 532…", "532…" → "905321112233"; geçersizse null. */
export function telefonDuzelt(text) {
  let rakam = String(text || "").replace(/\D/g, "");
  if (rakam.startsWith("90") && rakam.length === 12) {
    rakam = rakam.slice(2);
  }
  if (rakam.startsWith("0") && rakam.length === 11) {
    rakam = rakam.slice(1);
  }
  if (!/^5[0-9]{9}$/.test(rakam)) {
    return null;
  }
  return "90" + rakam;
}

/** Elle girişte sorulan ikinci bilgi; sunucu bunu yalnız tuzlu özet olarak saklar. */
export function sonIki(tel) {
  return tel ? tel.slice(-2) : null;
}

/** Çiftçiye giden mesaj: isim yok, yalnız fabrika, sıra no, kod ve link. */
export function sirraMesaji(fabrika, siraNo, kod, link) {
  const no = siraNo ? "Sıranız " + siraNo + " (kod " + kod + ")." : "Sıra kodunuz " + kod + ".";
  return fabrika + ": zeytininiz sıraya alındı. " + no + " Sıranızı buradan takip edebilirsiniz: " + link;
}

export function whatsappLinki(tel, mesaj) {
  return "https://wa.me/" + tel + "?text=" + encodeURIComponent(mesaj);
}

/** "?&body=" hem Android hem iPhone'da çalışır. */
export function smsLinki(tel, mesaj) {
  return "sms:+" + tel + "?&body=" + encodeURIComponent(mesaj);
}

const ON_EK = "zs_tel_";

/** Günün telefon defteri (kod → numara). Başka günlerin kayıtları açılışta silinir. */
export class TelefonDefteri {
  constructor(depo, gun) {
    this.depo = depo;
    this.gun = gun;
    this.anahtar = ON_EK + gun;
  }
  temizle() {
    const silinecek = [];
    for (let i = 0; i < this.depo.length; i++) {
      const k = this.depo.key(i);
      if (k && k.startsWith(ON_EK) && k !== this.anahtar) {
        silinecek.push(k);
      }
    }
    for (const k of silinecek) {
      this.depo.removeItem(k);
    }
    return silinecek.length;
  }
  _oku() {
    try {
      return JSON.parse(this.depo.getItem(this.anahtar) || "{}");
    } catch {
      return {};
    }
  }
  kaydet(kod, tel) {
    const d = this._oku();
    d[kod] = tel;
    this.depo.setItem(this.anahtar, JSON.stringify(d));
  }
  al(kod) {
    return this._oku()[kod] || null;
  }
}
