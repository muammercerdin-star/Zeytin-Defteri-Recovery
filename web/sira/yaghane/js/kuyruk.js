// Çevrimdışı kuyruk: internet gidince havuzcunun işlemleri tarayıcıda sıraya girer, gelince sırayla gönderilir.
// Her işlemin tekil kimliği (op_id) vardır; sunucu aynı kimliği ikinci kez uygulamaz. Çakışmada sunucu kazanır,
// işlem kuyruktan düşer ve havuzcuya Türkçe söylenir.

export function islemKimligi() {
  const b = crypto.getRandomValues(new Uint8Array(16));
  let s = "";
  for (const x of b) {
    s += String.fromCharCode(x);
  }
  return btoa(s).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

export class Kuyruk {
  constructor(depo, anahtar = "zs_kuyruk") {
    this.depo = depo;
    this.anahtar = anahtar;
  }
  liste() {
    try {
      const l = JSON.parse(this.depo.getItem(this.anahtar) || "[]");
      return Array.isArray(l) ? l : [];
    } catch {
      return [];
    }
  }
  _yaz(l) {
    this.depo.setItem(this.anahtar, JSON.stringify(l));
  }
  /** islem: {yol, govde:{op_id,…}, ozet:"Başlat · 5"} */
  ekle(islem) {
    const l = this.liste();
    if (!islem.govde.op_id) {
      islem.govde.op_id = islemKimligi();
    }
    l.push(islem);
    this._yaz(l);
    return islem.govde.op_id;
  }
  sayi() {
    return this.liste().length;
  }
  /**
   * Sırayla gönderir. gonder(yol, govde) → {status, body}; ağ hatasında fırlatır.
   * Dönen: {gonderilen, mesajlar[], sonuclar[], durdu: "ag"|"oturum"|"sunucu"|null}
   */
  async gonderHepsi(gonder) {
    const sonuc = { gonderilen: 0, mesajlar: [], sonuclar: [], durdu: null };
    for (;;) {
      const l = this.liste();
      if (l.length === 0) {
        return sonuc;
      }
      const islem = l[0];
      let cevap;
      try {
        cevap = await gonder(islem.yol, islem.govde);
      } catch {
        sonuc.durdu = "ag";
        return sonuc;
      }
      if (cevap.status === 401) {
        sonuc.durdu = "oturum";
        return sonuc;
      }
      if (cevap.status >= 500 || cevap.status === 429) {
        sonuc.durdu = "sunucu";
        return sonuc;
      }
      // Başarılı ya da kalıcı ret: kuyruktan düşer (aynı işlem sonsuza dek denenmez).
      this._yaz(this.liste().slice(1));
      sonuc.gonderilen += 1;
      sonuc.sonuclar.push({ islem, status: cevap.status, body: cevap.body });
      if (cevap.status >= 400) {
        const neden = cevap.body && cevap.body.mesaj ? cevap.body.mesaj : "sunucu kabul etmedi";
        sonuc.mesajlar.push("Uygulanmadı: " + islem.ozet + ". " + neden);
      }
      if (cevap.body && cevap.body.kod_degisti) {
        sonuc.mesajlar.push(islem.ozet + ": kod değişti, yeni kod " + cevap.body.kod + ". Çiftçiye yeni kodu söyle.");
      }
    }
  }
}

/** Çevrimdışı için ayrılan yedek kodlar (kod + token + link). */
export class YedekKodlar {
  constructor(depo, anahtar = "zs_yedek") {
    this.depo = depo;
    this.anahtar = anahtar;
  }
  liste() {
    try {
      const l = JSON.parse(this.depo.getItem(this.anahtar) || "[]");
      return Array.isArray(l) ? l : [];
    } catch {
      return [];
    }
  }
  ekle(yeni) {
    this.depo.setItem(this.anahtar, JSON.stringify(this.liste().concat(yeni)));
  }
  /** Bir yedek alır (kuyruğa girer, sunucuda bağlantı gelince kullanılır). Dünden eski yedekler atılır. */
  al(gun, oncekiGun) {
    const l = this.liste().filter((y) => y.gun === gun || y.gun === oncekiGun);
    const y = l.shift() || null;
    this.depo.setItem(this.anahtar, JSON.stringify(l));
    return y;
  }
  eksik(gun, hedef = 10) {
    const kalan = this.liste().filter((y) => y.gun === gun).length;
    return Math.max(0, hedef - kalan);
  }
}

/**
 * Sunucudan son gelen listeye, henüz gönderilmemiş işlemleri uygular (ekranda hemen görünsün).
 * islem: {tur: "yeni"|"baslat"|"bitti"|"yag"|"iptal"|"tasi", id, govde}. Sunucu cevabı gelince liste yenilenir.
 */
export function yerelGorunum(sira, islemler, simdi) {
  const l = sira.map((s) => ({ ...s }));
  for (const is of islemler) {
    if (is.tur === "yeni") {
      const enBuyuk = l.reduce((m, s) => Math.max(m, Number(s.konum) || 0), 0);
      l.push({
        id: "yerel-" + is.govde.op_id,
        gecici: true,
        sira_no: null,
        konum: enBuyuk + 1,
        kod: is.govde.yedek ? is.govde.yedek.kod : "····",
        token: is.govde.yedek ? is.govde.yedek.token : null,
        ad_sifreli: is.govde.ad_sifreli || null,
        kasa: is.govde.kasa,
        kg: is.govde.kg,
        durum: "sirada",
        gelis: simdi,
      });
      continue;
    }
    const s = l.find((x) => x.id === is.id);
    if (!s) {
      continue;
    }
    s.bekliyor = true;
    if (is.tur === "baslat" && s.durum === "sirada") {
      s.durum = "isleniyor";
      s.baslama = simdi;
    } else if (is.tur === "bitti" && s.durum === "isleniyor") {
      s.durum = "bitti";
      s.bitis = simdi;
      s.yag_kg = is.govde.yag_kg ?? null;
    } else if (is.tur === "yag" && s.durum === "bitti") {
      s.yag_kg = is.govde.yag_kg;
    } else if (is.tur === "iptal" && (s.durum === "sirada" || s.durum === "isleniyor")) {
      s.durum = "iptal";
      s.bitis = simdi;
    } else if (is.tur === "tasi" && s.durum === "sirada") {
      const sirada = l.filter((x) => x.durum === "sirada").sort((a, b) => a.konum - b.konum);
      const i = sirada.indexOf(s);
      const j = is.govde.yon === "yukari" ? i - 1 : i + 1;
      if (j >= 0 && j < sirada.length) {
        const k = sirada[j].konum;
        sirada[j].konum = s.konum;
        s.konum = k;
      }
    }
  }
  const oncelik = { isleniyor: 0, sirada: 1, bitti: 2, iptal: 3 };
  l.sort((a, b) => oncelik[a.durum] - oncelik[b.durum] || (a.durum === "sirada" ? a.konum - b.konum : 0));
  return l;
}
