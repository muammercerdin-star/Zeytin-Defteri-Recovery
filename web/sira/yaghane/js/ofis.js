// Ofis paneli (geniş ekran): aynı liste, kayıtlar (gün/hafta/ay/sezon), kapasite ve gün başlangıcı, fabrika anahtarı, TV'ye yansıtma.
import { adCoz, iz, kisalt, parolaYaz, yeniSir } from "./anahtar.js";
import { fisYazdir, qrTuvali } from "./fis.js";
import { islemKimligi } from "./kuyruk.js";
import { yeniMusteriGovdesi } from "./musteri.js";
import { raporKur } from "./rapor.js";
import { qrcode } from "./vendor/qrcode.js";
import { anahtariKaydet, api, cihazAnahtari, cikisYap, depo, dugme, el, kg, parolaKaydet, qrAnahtariniAl, randiman, saat } from "./ortak.js";

const YENILEME_MS = 15000;
const UCRETSIZ_SINIR = 100000;
const d = depo();
const durum = { veri: null, anahtar: null, anahtarDurumu: null, parola: null, adlar: new Map(), acik: null, qr: false, qrBilgi: null };
const $ = (id) => document.getElementById(id);

function mesajGoster(metin, tur = "bilgilendirme") {
  const kutu = el("div", tur, metin);
  $("mesajlar").appendChild(kutu);
  setTimeout(() => kutu.remove(), 12000);
}

async function yukle() {
  const r = await api("/api/yaghane/gun").catch(() => null);
  if (r && r.status === 401) {
    window.location.replace("/yaghane/");
    return;
  }
  if (!r || r.status !== 200) {
    mesajGoster("Sunucuya ulaşılamadı; liste birazdan yeniden denenecek.", "uyari");
    return;
  }
  if (r.body.ben.rol !== "ofis") {
    window.location.replace("/yaghane/havuzcu.html");
    return;
  }
  const izDegisti = !durum.veri || durum.veri.ayar.anahtar_izi !== r.body.ayar.anahtar_izi;
  durum.veri = r.body;
  if (izDegisti) {
    const a = await cihazAnahtari(d, r.body.yaghane.kisa_ad, r.body.ayar.anahtar_izi);
    durum.anahtar = a.anahtar;
    durum.anahtarDurumu = a.durum;
    durum.parola = a.parola || null;
    durum.adlar.clear();
    anahtarCiz();
  }
  await ciz();
}

async function adOf(s) {
  if (!s.ad_sifreli || !durum.anahtar) {
    return "";
  }
  if (!durum.adlar.has(s.ad_sifreli)) {
    durum.adlar.set(s.ad_sifreli, await adCoz(durum.anahtar, durum.veri.yaghane.kisa_ad, s.ad_sifreli));
  }
  return durum.adlar.get(s.ad_sifreli) || "";
}

async function islem(yol, govde, ozet) {
  govde.op_id = islemKimligi();
  const r = await api(yol, "POST", govde).catch(() => null);
  durum.acik = null;
  if (!r) {
    mesajGoster("İnternet yok: " + ozet + " uygulanmadı.", "hata");
  } else if (r.status >= 400) {
    mesajGoster("Uygulanmadı: " + ozet + ". " + ((r.body && r.body.mesaj) || ""), "uyari");
  }
  await yukle();
}

/** Tahmin isabeti metni: çiftçiye söylenen başlama ile gerçek başlama farkı. İşaretli pozitif = geç başladı. */
function isabetMetni(isabet) {
  if (!isabet || !isabet.n) {
    return "henüz ölçülmedi (bir müşteri başlayınca ölçülür)";
  }
  let metin = isabet.n + " işlemde ort. ±" + isabet.ort_mutlak_dk + " dk sapma";
  const yon = isabet.ort_isaretli_dk;
  if (yon >= 5) {
    metin += "; ortalama " + yon + " dk geç başladı";
  } else if (yon <= -5) {
    metin += "; ortalama " + Math.abs(yon) + " dk erken başladı";
  }
  return metin;
}

function durumEtiketi(s) {
  const adlar = { sirada: "Sırada", isleniyor: "İşleniyor", bitti: "Bitti", iptal: "İptal" };
  return el("span", "durum " + s.durum, adlar[s.durum]);
}

/** Fiş: ad yalnız bu bilgisayarda çözüldüyse yazılır; fiş sunucuya bir şey göndermez. */
function fisBas(bilgi) {
  const v = durum.veri;
  fisYazdir({
    fabrika: v.yaghane.ad,
    kisaAd: v.yaghane.kisa_ad,
    siraNo: bilgi.siraNo,
    kod: bilgi.kod,
    kasa: bilgi.kasa,
    kg: bilgi.kg,
    zaman: bilgi.zaman,
    ad: bilgi.ad,
    link: window.location.origin + "/" + bilgi.token,
    site: window.location.host,
  });
}

/** Büyük QR: çiftçi ofis ekranından kendi telefonuyla okutur; telefon numarası ve yazıcı gerekmez. */
function qrGoster(bilgi) {
  const yer = $("qr-yer");
  yer.textContent = "";
  yer.append(qrTuvali(window.location.origin + "/" + bilgi.token));
  $("qr-kod").textContent = "Kod " + bilgi.kod + (bilgi.siraNo ? " · " + bilgi.siraNo + ". sıra" : "");
  durum.qrBilgi = bilgi;
  $("qr-panel").classList.remove("gizli");
  $("qr-panel").scrollIntoView({ block: "center", behavior: "smooth" });
}

function qrKapat() {
  $("qr-panel").classList.add("gizli");
  $("qr-yer").textContent = "";
  durum.qrBilgi = null;
}

function islemHucresi(s, ilk, son, ad) {
  const td = el("td");
  const kutu = el("div", "islemler");
  td.append(kutu);
  const yol = "/api/yaghane/sira/" + s.id + "/";
  if (s.durum === "sirada") {
    const yukari = dugme("↑", "kucuk", () => islem(yol + "tasi", { yon: "yukari" }, "↑ kod " + s.kod));
    yukari.setAttribute("aria-label", "Yukarı taşı");
    yukari.disabled = ilk;
    const asagi = dugme("↓", "kucuk", () => islem(yol + "tasi", { yon: "asagi" }, "↓ kod " + s.kod));
    asagi.setAttribute("aria-label", "Aşağı taşı");
    asagi.disabled = son;
    const iptal = dugme("İptal", "kucuk tehlike", () => {
      if (iptal.dataset.emin === "1") {
        islem(yol + "iptal", { beklenen: s.durum }, "İptal · kod " + s.kod);
        return;
      }
      iptal.dataset.emin = "1";
      iptal.textContent = "Evet, iptal";
    });
    kutu.append(dugme("Başlat", "ana kucuk", () => islem(yol + "baslat", {}, "Başlat · kod " + s.kod)), yukari, asagi, iptal);
    const satirBilgi = { siraNo: s.sira_no, kod: s.kod, kasa: s.kasa, kg: s.kg, zaman: s.gelis, ad, token: s.token };
    kutu.append(dugme("QR", "kucuk", () => qrGoster(satirBilgi)), dugme("Fiş", "kucuk", () => fisBas(satirBilgi)));
  }
  if (s.durum === "isleniyor") {
    // Tek dokunuş: yağ kilosu bitenler satırından sonra yazılır.
    kutu.append(dugme("Bitti", "ana kucuk", () => islem(yol + "bitti", {}, "Bitti · kod " + s.kod)));
  }
  if (s.durum === "bitti") {
    if (durum.acik === s.id) {
      const form = el("form", "islemler");
      const giris = el("input");
      giris.inputMode = "decimal";
      giris.placeholder = "yağ kg";
      giris.setAttribute("aria-label", "Çıkan yağ kilosu, kod " + s.kod);
      giris.style.width = "110px";
      if (s.yag_kg) {
        giris.value = String(s.yag_kg).replace(".", ",");
      }
      const kaydet = el("button", "ana kucuk", "Kaydet");
      kaydet.type = "submit";
      form.append(giris, kaydet, dugme("Vazgeç", "kucuk", () => {
        durum.acik = null;
        ciz();
      }));
      form.addEventListener("submit", (olay) => {
        olay.preventDefault();
        const yag = Number(giris.value.replace(",", "."));
        if (!Number.isFinite(yag) || yag <= 0 || yag > Number(s.kg)) {
          mesajGoster("Yağ kilosu 0 ile " + s.kg + " arasında olmalı.", "hata");
          return;
        }
        islem(yol + "yag", { yag_kg: yag }, "Yağ · kod " + s.kod);
      });
      kutu.append(form);
      setTimeout(() => giris.focus(), 0);
    } else {
      kutu.append(dugme(s.yag_kg ? "Düzelt" : "Yağ yaz", "kucuk", () => {
        durum.acik = s.id;
        ciz();
      }));
    }
  }
  return td;
}

async function ciz() {
  const v = durum.veri;
  if (!v) {
    return;
  }
  $("fabrika").textContent = v.yaghane.ad;
  $("tarih").textContent = new Date(v.simdi).toLocaleDateString("tr-TR", { day: "numeric", month: "long", weekday: "long", timeZone: "Europe/Istanbul" }) + " · " + v.ben.kullanici_adi + " (ofis)";
  $("bekleyen").textContent = v.ozet.bekleyen + " kişi";
  $("bekleyen-kg").textContent = kg(v.ozet.bekleyen_kg);
  $("islenen").textContent = kg(v.ozet.islenen_kg);
  $("biter").textContent = v.ozet.biter ? "~" + v.ozet.biter : "–";
  $("isabet-deger").textContent = isabetMetni(v.isabet);
  $("istek").textContent = "Bugünkü API isteği: " + v.istek_bugun.toLocaleString("tr-TR") + " / " + UCRETSIZ_SINIR.toLocaleString("tr-TR") + " (ücretsiz plan, UTC gün)";
  const ms = d.getItem("zs_giris_ms");
  $("giris-suresi").textContent = ms ? "Son giriş süresi (bu tarayıcıda, şifre özeti + ağ): " + ms + " ms" : "";
  if (document.activeElement && document.activeElement.closest && document.activeElement.closest("#ayar-form")) {
    // kullanıcı yazarken ayar alanlarına dokunulmaz
  } else {
    $("kapasite").value = v.ayar.kapasite_kg_saat;
    $("baslangic").value = v.ayar.gun_baslangic;
  }
  if (document.activeElement && document.activeElement.closest && document.activeElement.closest("#tablo") && durum.acik) {
    return;
  }
  const adlar = await Promise.all(v.sira.map(adOf));
  const tablo = $("tablo");
  tablo.textContent = "";
  const sirada = v.sira.filter((s) => s.durum === "sirada");
  v.sira.forEach((s, i) => {
    const tr = el("tr");
    const ad = adlar[i];
    let tahmini = "";
    if (s.durum === "sirada" && s.tahmini) {
      tahmini = "~" + s.tahmini;
    }
    if (s.durum === "isleniyor" && s.tahmini) {
      tahmini = s.tahmini + " biter";
    }
    if (s.durum === "bitti") {
      tahmini = s.yag_kg ? kg(s.yag_kg) + " yağ · " + randiman(s.kg, s.yag_kg) : "yağ yazılmadı";
    }
    const at = sirada.indexOf(s);
    tr.append(
      el("td", "no", s.sira_no),
      el("td", "ad", ad || (s.ad_sifreli ? "(okunamadı)" : "–")),
      el("td", null, s.kod),
      el("td", null, s.kasa + " · " + kg(s.kg)),
      el("td", null, saat(s.gelis)),
      el("td", null, tahmini),
      el("td")
    );
    tr.lastChild.append(durumEtiketi(s));
    tr.append(islemHucresi(s, at === 0, at === sirada.length - 1, ad));
    tablo.append(tr);
  });
  if (!v.sira.length) {
    const tr = el("tr");
    const td = el("td", "not", "Bugün sıra boş.");
    td.colSpan = 8;
    tr.append(td);
    tablo.append(tr);
  }
}

// ---------------------------------------------------------------- fabrika anahtarı

function qrCiz(metin) {
  const qr = qrcode(0, "M");
  qr.addData(metin);
  qr.make();
  const n = qr.getModuleCount();
  const olcek = 6;
  const canvas = el("canvas", "qr");
  canvas.width = n * olcek;
  canvas.height = n * olcek;
  canvas.setAttribute("role", "img");
  canvas.setAttribute("aria-label", "Fabrika anahtarı QR kodu");
  const c = canvas.getContext("2d");
  c.fillStyle = "#fff";
  c.fillRect(0, 0, canvas.width, canvas.height);
  c.fillStyle = "#000";
  for (let y = 0; y < n; y++) {
    for (let x = 0; x < n; x++) {
      if (qr.isDark(y, x)) {
        c.fillRect(x * olcek, y * olcek, olcek, olcek);
      }
    }
  }
  return canvas;
}

async function anahtarOlustur(yenile) {
  const v = durum.veri;
  const sir = yeniSir();
  const parola = parolaYaz(sir);
  const r = await api("/api/yaghane/anahtar", "PUT", { iz: await iz(sir, v.yaghane.kisa_ad), yenile });
  if (!r || r.status !== 200) {
    mesajGoster((r && r.body && r.body.mesaj) || "Anahtar kaydedilemedi.", "hata");
    return;
  }
  anahtariKaydet(d, v.yaghane.kisa_ad, parola);
  durum.veri = null;
  durum.qr = true;
  await yukle();
  mesajGoster("Fabrika anahtarı oluşturuldu. Şimdi havuzcunun telefonuna aktar ve parolayı güvenli bir yere yaz.");
}

function anahtarCiz() {
  const kutu = $("anahtar-icerik");
  const uyari = $("anahtar-uyari");
  kutu.textContent = "";
  uyari.classList.add("gizli");
  const v = durum.veri;
  if (durum.anahtarDurumu === "kurulmadi") {
    kutu.append(el("p", null, "Henüz anahtar yok. Oluşturunca müşteri isimleri şifreli yazılabilir."));
    kutu.append(dugme("Fabrika anahtarı oluştur", "ana", () => anahtarOlustur(false)));
    uyari.textContent = "Fabrika anahtarı oluşturulmadı: isimler girilemez, sıra kodlarla çalışır.";
    uyari.classList.remove("gizli");
    return;
  }
  if (durum.anahtarDurumu !== "tamam") {
    uyari.textContent = "Bu bilgisayarda fabrika anahtarı yok: isimler okunamaz. Parolayı aşağıya yaz.";
    uyari.classList.remove("gizli");
    const form = el("form");
    const etiket = el("label", null, "Anahtar parolası");
    const giris = el("input", "parola");
    giris.id = "ofis-parola";
    etiket.htmlFor = giris.id;
    giris.autocomplete = "off";
    const kaydet = el("button", "ana", "Kaydet");
    kaydet.type = "submit";
    form.append(etiket, giris, el("div", "dugmeler"));
    form.lastChild.append(kaydet);
    form.addEventListener("submit", async (olay) => {
      olay.preventDefault();
      const hata = await parolaKaydet(d, v.yaghane.kisa_ad, v.ayar.anahtar_izi, giris.value);
      if (hata) {
        mesajGoster(hata, "hata");
        return;
      }
      durum.veri = null;
      await yukle();
    });
    kutu.append(form);
    kutu.append(yenileDugmesi());
    return;
  }
  kutu.append(el("p", "bilgilendirme", "Anahtar bu bilgisayarda. İsimler şifreli yazılıp okunuyor."));
  if (durum.qr) {
    const link = window.location.origin + "/yaghane/#anahtar=" + durum.parola.replaceAll("-", "");
    kutu.append(el("p", null, "Havuzcu telefonunun kamerasıyla okutsun (yalnız onun telefonuna; QR'ın fotoğrafını kimseyle paylaşma):"));
    kutu.append(qrCiz(link));
    kutu.append(el("p", "not", "Ya da parolayı elle yazsın:"));
    kutu.append(el("p", "parola", durum.parola));
    kutu.append(dugme("Gizle", "", () => {
      durum.qr = false;
      anahtarCiz();
    }));
  } else {
    kutu.append(dugme("Havuzcu telefonuna aktar (QR / parola)", "ana", () => {
      durum.qr = true;
      anahtarCiz();
    }));
  }
  kutu.append(yenileDugmesi());
}

function yenileDugmesi() {
  const b = dugme("Anahtarı yenile", "tehlike", () => {
    if (b.dataset.emin === "1") {
      anahtarOlustur(true);
      return;
    }
    b.dataset.emin = "1";
    b.textContent = "Emin misin? Bugünkü isimler okunamaz olur, havuzcuya yeniden aktarman gerekir";
  });
  const kap = el("div", "dugmeler");
  kap.append(b);
  return kap;
}

// ---------------------------------------------------------------- formlar

async function yeniEkle(olay) {
  olay.preventDefault();
  const v = durum.veri;
  const form = { ad: $("y-ad").value, kasa: $("y-kasa").value, kg: $("y-kg").value };
  const g = await yeniMusteriGovdesi(form, durum.anahtar, v.yaghane.kisa_ad, islemKimligi(), null);
  $("y-hata").classList.add("gizli");
  $("y-sonuc").classList.add("gizli");
  if (g.hata) {
    $("y-hata").textContent = g.hata;
    $("y-hata").classList.remove("gizli");
    return;
  }
  const r = await api("/api/yaghane/sira", "POST", g.govde).catch(() => null);
  if (!r || r.status !== 201) {
    $("y-hata").textContent = (r && r.body && r.body.mesaj) || "İnternet yok; müşteri eklenmedi.";
    $("y-hata").classList.remove("gizli");
    return;
  }
  const ad = String(form.ad || "").trim();
  $("y-sonuc").textContent = "✓ " + r.body.sira_no + ". sıra" + (ad ? " · " + kisalt(ad) : "") + " · kod " + r.body.kod + " ";
  const bilgi = { siraNo: r.body.sira_no, kod: r.body.kod, kasa: g.govde.kasa, kg: g.govde.kg, zaman: Date.now(), ad, token: r.body.token };
  $("y-sonuc").append(dugme("Fiş yazdır", "kucuk", () => fisBas(bilgi)));
  $("y-sonuc").classList.remove("gizli");
  qrGoster(bilgi);
  $("y-ad").value = "";
  $("y-kasa").value = "";
  $("y-kg").value = "";
  await yukle();
}

async function ayarKaydet(olay) {
  olay.preventDefault();
  const govde = { kapasite_kg_saat: Number($("kapasite").value), gun_baslangic: $("baslangic").value };
  const r = await api("/api/yaghane/ayar", "PUT", govde).catch(() => null);
  if (!r || r.status !== 200) {
    mesajGoster((r && r.body && r.body.mesaj) || "Ayar kaydedilemedi.", "hata");
    return;
  }
  document.activeElement.blur();
  mesajGoster("Ayarlar kaydedildi.");
  await yukle();
}

function basla() {
  qrAnahtariniAl();
  $("yeni-ac").addEventListener("click", () => {
    $("yeni-form").classList.remove("gizli");
    $("y-kasa").focus();
  });
  $("yeni-vazgec").addEventListener("click", () => $("yeni-form").classList.add("gizli"));
  $("yeni-form").addEventListener("submit", yeniEkle);
  raporKur(() => (durum.veri ? durum.veri.yaghane.kisa_ad : ""));
  $("qr-kapat").addEventListener("click", qrKapat);
  $("qr-fis").addEventListener("click", () => {
    if (durum.qrBilgi) {
      fisBas(durum.qrBilgi);
    }
  });
  $("ayar-form").addEventListener("submit", ayarKaydet);
  $("cikis").addEventListener("click", () => cikisYap(d, durum.veri ? durum.veri.yaghane.kisa_ad : ""));
  setInterval(() => {
    if (document.visibilityState === "visible") {
      yukle();
    }
  }, YENILEME_MS);
  yukle();
}

basla();
