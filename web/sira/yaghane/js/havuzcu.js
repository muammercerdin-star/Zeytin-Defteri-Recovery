// Havuzcu ekranı (telefon): yeni müşteri, Başlat, Bitti (yağ kg), ↑↓, iptal. İnternet giderse işlemler
// kuyruğa girer, ekranda hemen görünür, bağlantı gelince sırayla gönderilir.
import { adCoz } from "./anahtar.js";
import { Kuyruk, YedekKodlar, islemKimligi, yerelGorunum } from "./kuyruk.js";
import { yeniMusteriGovdesi } from "./musteri.js";
import { TelefonDefteri, sirraMesaji, smsLinki, whatsappLinki } from "./telefon.js";
import { api, cihazAnahtari, cikisYap, depo, dugme, el, kg, parolaKaydet, qrAnahtariniAl, saat } from "./ortak.js";

const YENILEME_MS = 15000;
const d = depo();
const kuyruk = new Kuyruk(d);
const yedekler = new YedekKodlar(d);
const durum = {
  veri: null,
  cevrimici: navigator.onLine !== false,
  anahtar: null,
  anahtarDurumu: null,
  adlar: new Map(),
  gonderiliyor: false,
  acik: null,
};

const $ = (id) => document.getElementById(id);

function mesajGoster(metin, tur = "bilgilendirme") {
  const kutu = el("div", tur, metin);
  $("mesajlar").appendChild(kutu);
  setTimeout(() => kutu.remove(), 12000);
}

function oncekiGun(gun) {
  const ms = Date.parse(gun + "T00:00:00Z") - 86400000;
  return new Date(ms).toISOString().slice(0, 10);
}

function defter() {
  return new TelefonDefteri(d, durum.veri ? durum.veri.gun : "bilinmiyor");
}

async function yukle() {
  let r = null;
  try {
    r = await api("/api/yaghane/gun");
  } catch {
    r = null;
  }
  if (r && r.status === 401) {
    window.location.replace("/yaghane/");
    return;
  }
  if (r && r.status === 200) {
    durum.cevrimici = true;
    durum.veri = r.body;
    d.setItem("zs_son_liste", JSON.stringify(r.body));
    defter().temizle();
    if (durum.anahtarDurumu === null || durum.anahtarIzi !== r.body.ayar.anahtar_izi) {
      durum.anahtarIzi = r.body.ayar.anahtar_izi;
      const a = await cihazAnahtari(d, r.body.yaghane.kisa_ad, r.body.ayar.anahtar_izi);
      durum.anahtar = a.anahtar;
      durum.anahtarDurumu = a.durum;
      anahtarUyarisi();
    }
    yedekTamamla();
  } else {
    durum.cevrimici = false;
    if (!durum.veri) {
      try {
        durum.veri = JSON.parse(d.getItem("zs_son_liste") || "null");
      } catch {
        durum.veri = null;
      }
    }
  }
  await ciz();
}

async function yedekTamamla() {
  if (!durum.veri) {
    return;
  }
  const eksik = yedekler.eksik(durum.veri.gun);
  if (eksik < 5) {
    return;
  }
  const r = await api("/api/yaghane/yedek", "POST", { adet: eksik }).catch(() => null);
  if (r && r.status === 201) {
    yedekler.ekle(r.body.yedek);
  }
}

function anahtarUyarisi() {
  const kutu = $("anahtar-uyari");
  const form = $("anahtar-form");
  const metin = $("anahtar-metni");
  kutu.classList.add("gizli");
  form.classList.add("gizli");
  if (durum.anahtarDurumu === "kurulmadi") {
    metin.textContent = "Ofis henüz fabrika anahtarını oluşturmadı. İsimsiz çalışabilirsin; isimler kod olarak görünür.";
    kutu.classList.remove("gizli");
  }
  if (durum.anahtarDurumu === "yok" || durum.anahtarDurumu === "eski") {
    metin.textContent = "Bu telefonda fabrika anahtarı yok: isimler görünmez ve yazılamaz. Ofis panelindeki QR'ı telefonun kamerasıyla okut ya da parolayı yaz.";
    kutu.classList.remove("gizli");
    form.classList.remove("gizli");
  }
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

function islem(tur, id, yol, govde, ozet) {
  govde.op_id = islemKimligi();
  kuyruk.ekle({ tur, id, yol, govde, ozet });
  durum.acik = null;
  ciz();
  gonder();
}

async function gonder() {
  if (durum.gonderiliyor || kuyruk.sayi() === 0) {
    return null;
  }
  durum.gonderiliyor = true;
  const sonuc = await kuyruk.gonderHepsi((yol, govde) => api(yol, "POST", govde));
  durum.gonderiliyor = false;
  for (const m of sonuc.mesajlar) {
    mesajGoster(m, "uyari");
  }
  if (sonuc.durdu === "oturum") {
    mesajGoster("Oturum kapanmış. Bekleyen işlemler duruyor; yeniden giriş yap.", "hata");
  }
  if (sonuc.durdu === "ag") {
    durum.cevrimici = false;
  }
  if (sonuc.gonderilen > 0) {
    for (const s of sonuc.sonuclar) {
      eklendiGuncelle(s);
    }
    await yukle();
  } else {
    await ciz();
  }
  return sonuc;
}

// ---------------------------------------------------------------- yeni müşteri

function formAc(ac) {
  $("yeni-form").classList.toggle("gizli", !ac);
  $("eklendi").classList.add("gizli");
  $("y-hata").classList.add("gizli");
  if (ac) {
    $("yeni-form").reset();
    $("y-kasa").focus();
  }
}

async function yeniEkle(olay) {
  olay.preventDefault();
  const v = durum.veri;
  if (!v) {
    return;
  }
  const yedek = yedekler.al(v.gun, oncekiGun(v.gun));
  if (!yedek && !durum.cevrimici) {
    $("y-hata").textContent = "İnternet yok ve yedek kod kalmadı. Kodu fişe elle yaz, bağlantı gelince müşteriyi ekle.";
    $("y-hata").classList.remove("gizli");
    return;
  }
  const form = { ad: $("y-ad").value, telefon: $("y-tel").value, kasa: $("y-kasa").value, kg: $("y-kg").value };
  const opId = islemKimligi();
  const g = await yeniMusteriGovdesi(form, durum.anahtar, v.yaghane.kisa_ad, opId, yedek);
  if (g.hata) {
    if (yedek) {
      yedekler.ekle([yedek]);
    }
    $("y-hata").textContent = g.hata;
    $("y-hata").classList.remove("gizli");
    return;
  }
  kuyruk.ekle({ tur: "yeni", id: null, yol: "/api/yaghane/sira", govde: g.govde, ozet: "Yeni müşteri " + form.kasa + " kasa" });
  durum.sonEklenen = { opId, tel: g.tel, kod: yedek ? yedek.kod : null, link: yedek ? yedek.link : null, siraNo: null };
  if (g.tel && yedek) {
    defter().kaydet(yedek.kod, { tel: g.tel, link: yedek.link, siraNo: null });
  }
  formAc(false);
  eklendiGoster();
  await ciz();
  await gonder();
}

function eklendiGuncelle(s) {
  const son = durum.sonEklenen;
  if (!son || s.islem.govde.op_id !== son.opId || s.status !== 201) {
    return;
  }
  son.kod = s.body.kod;
  son.link = s.body.link;
  son.siraNo = s.body.sira_no;
  if (son.tel) {
    defter().kaydet(son.kod, { tel: son.tel, link: son.link, siraNo: son.siraNo });
  }
  eklendiGoster();
}

function gonderDugmeleri(kutu, kayit, kod) {
  kutu.textContent = "";
  if (!kayit || !kayit.tel || !kayit.link) {
    return;
  }
  const mesaj = sirraMesaji(durum.veri.yaghane.ad, kayit.siraNo, kod, kayit.link);
  const wa = el("a", "dugme ana", "WhatsApp'la gönder");
  wa.href = whatsappLinki(kayit.tel, mesaj);
  wa.target = "_blank";
  wa.rel = "noopener noreferrer";
  const sms = el("a", "dugme", "SMS gönder");
  sms.href = smsLinki(kayit.tel, mesaj);
  kutu.append(wa, sms);
}

function eklendiGoster() {
  const son = durum.sonEklenen;
  if (!son) {
    return;
  }
  $("eklendi").classList.remove("gizli");
  $("eklendi-kod").textContent = son.kod || "····";
  const baslik = son.siraNo ? "✓ Sıraya eklendi · " + son.siraNo + ". sıra" : "✓ Sıraya alındı · sıra no bağlantı gelince";
  $("eklendi-baslik").textContent = baslik;
  gonderDugmeleri($("eklendi-gonder"), son.tel ? son : null, son.kod);
  if (!son.kod) {
    $("eklendi-gonder").textContent = "Kod bağlantı gelince çıkacak.";
  }
}

// ---------------------------------------------------------------- liste

function satirBilgisi(s, ad) {
  const b = el("div", "bilgi");
  b.append(el("div", "ad", ad || "Kod " + s.kod));
  let alt = kg(s.kg) + " · " + s.kasa + " kasa";
  if (ad) {
    alt += " · kod " + s.kod;
  }
  if (s.tahmini && s.durum === "sirada") {
    alt += " · ~" + s.tahmini;
  }
  if (s.gecici) {
    alt += " · gönderilmeyi bekliyor";
  } else if (s.bekliyor) {
    alt += " · bekliyor";
  }
  b.append(el("div", "alt", alt));
  return b;
}

function siradaSatiri(s, ad, ilk, son) {
  const satir = el("div", "satir");
  satir.append(el("div", "no", s.sira_no || "–"), satirBilgisi(s, ad));
  const is = el("div", "islemler");
  if (!s.gecici) {
    const yukari = dugme("↑", "kucuk", () => islem("tasi", s.id, "/api/yaghane/sira/" + s.id + "/tasi", { yon: "yukari" }, "↑ " + s.kod));
    yukari.setAttribute("aria-label", "Yukarı taşı");
    yukari.disabled = ilk;
    const asagi = dugme("↓", "kucuk", () => islem("tasi", s.id, "/api/yaghane/sira/" + s.id + "/tasi", { yon: "asagi" }, "↓ " + s.kod));
    asagi.setAttribute("aria-label", "Aşağı taşı");
    asagi.disabled = son;
    const diger = dugme("⋯", "kucuk", () => {
      durum.acik = durum.acik === s.id ? null : s.id;
      ciz();
    });
    diger.setAttribute("aria-label", "Diğer işlemler");
    is.append(dugme("Başlat", "ana kucuk", () => islem("baslat", s.id, "/api/yaghane/sira/" + s.id + "/baslat", {}, "Başlat · kod " + s.kod)), yukari, asagi, diger);
  }
  satir.append(is);
  const kap = el("div");
  kap.append(satir);
  if (durum.acik === s.id) {
    kap.append(digerIslemler(s));
  }
  return kap;
}

function digerIslemler(s) {
  const kutu = el("div", "dugmeler");
  const kayit = defter().al(s.kod);
  if (kayit) {
    const gonderKutu = el("div", "dugmeler");
    gonderDugmeleri(gonderKutu, { ...kayit, siraNo: kayit.siraNo || s.sira_no }, s.kod);
    kutu.append(gonderKutu);
  }
  const iptal = dugme("İptal et", "tehlike", () => {
    if (iptal.dataset.emin === "1") {
      islem("iptal", s.id, "/api/yaghane/sira/" + s.id + "/iptal", { beklenen: s.durum }, "İptal · kod " + s.kod);
      return;
    }
    iptal.dataset.emin = "1";
    iptal.textContent = "Evet, iptal et";
  });
  kutu.append(iptal);
  return kutu;
}

function isleniyorKarti(s, ad) {
  const k = el("div", "kart vurgu");
  k.append(el("div", "ad", (s.sira_no || "–") + " · " + (ad || "Kod " + s.kod)));
  k.append(el("div", "alt", kg(s.kg) + " · " + s.kasa + " kasa · başladı " + saat(s.baslama) + (s.tahmini ? " · tahmini bitiş " + s.tahmini : "")));
  if (durum.acik !== "bitti-" + s.id) {
    k.append(el("div", "dugmeler"));
    k.lastChild.append(
      dugme("Bitti · yağı yaz", "ana", () => {
        durum.acik = "bitti-" + s.id;
        ciz();
      })
    );
    return k;
  }
  const form = el("form");
  const etiket = el("label", null, "Çıkan yağ (kg)");
  const giris = el("input");
  giris.id = "yag-" + s.id;
  etiket.htmlFor = giris.id;
  giris.inputMode = "decimal";
  giris.autocomplete = "off";
  const hata = el("div", "hata gizli");
  hata.setAttribute("role", "alert");
  const kaydet = el("button", "ana", "Kaydet");
  kaydet.type = "submit";
  form.append(etiket, giris, hata, el("div", "dugmeler"));
  form.lastChild.append(
    dugme("Vazgeç", "", () => {
      durum.acik = null;
      ciz();
    }),
    kaydet
  );
  form.addEventListener("submit", (olay) => {
    olay.preventDefault();
    const yag = Number(giris.value.replace(",", "."));
    if (!Number.isFinite(yag) || yag <= 0 || yag > Number(s.kg)) {
      hata.textContent = "Yağ kilosu 0 ile " + s.kg + " arasında olmalı.";
      hata.classList.remove("gizli");
      return;
    }
    islem("bitti", s.id, "/api/yaghane/sira/" + s.id + "/bitti", { yag_kg: yag }, "Bitti · kod " + s.kod);
  });
  k.append(form);
  setTimeout(() => giris.focus(), 0);
  return k;
}

async function ciz() {
  const v = durum.veri;
  const bekleyen = kuyruk.sayi();
  const cevrim = $("cevrim");
  if (durum.cevrimici) {
    cevrim.textContent = bekleyen ? "● Bağlı · " + bekleyen + " işlem gönderiliyor" : "● Bağlı";
    cevrim.className = "cevrim acik";
  } else {
    cevrim.textContent = "● Çevrimdışı, " + bekleyen + " işlem bekliyor";
    cevrim.className = "cevrim kapali";
  }
  if (!v) {
    return;
  }
  $("fabrika").textContent = v.yaghane.ad;
  $("bekleyen").textContent = v.ozet.bekleyen + " kişi";
  $("bekleyen-kg").textContent = kg(v.ozet.bekleyen_kg);
  $("biter").textContent = v.ozet.biter ? "~" + v.ozet.biter : "–";
  const liste = yerelGorunum(v.sira, kuyruk.liste(), Date.now());
  const adlar = await Promise.all(liste.map(adOf));
  const isleniyor = $("isleniyor");
  const sirada = $("sirada");
  const biten = $("biten");
  isleniyor.textContent = "";
  sirada.textContent = "";
  biten.textContent = "";
  const siradakiler = liste.filter((s) => s.durum === "sirada");
  liste.forEach((s, i) => {
    if (s.durum === "isleniyor") {
      isleniyor.append(isleniyorKarti(s, adlar[i]));
    }
    if (s.durum === "sirada") {
      const at = siradakiler.indexOf(s);
      sirada.append(siradaSatiri(s, adlar[i], at === 0, at === siradakiler.length - 1));
    }
  });
  if (!isleniyor.childNodes.length) {
    isleniyor.append(el("p", "not", "Şu an işlenen yok."));
  }
  if (!siradakiler.length) {
    sirada.append(el("p", "not", "Sırada bekleyen yok."));
  }
  const bitenler = liste.filter((s) => s.durum === "bitti" || s.durum === "iptal").slice(0, 8);
  for (const s of bitenler) {
    const ad = adlar[liste.indexOf(s)];
    const satir = el("div", "satir");
    const alt = s.durum === "bitti" ? kg(s.kg) + " → " + kg(s.yag_kg) + " yağ" : "iptal";
    satir.append(el("div", "no", s.sira_no || "–"), el("div", "bilgi"));
    satir.lastChild.append(el("div", "ad", ad || "Kod " + s.kod), el("div", "alt", alt));
    biten.append(satir);
  }
  if (!bitenler.length) {
    biten.append(el("p", "not", "Bugün biten yok."));
  }
}

/** Önce bekleyenleri gönderir (gönderildiyse liste zaten yenilenir), yoksa yalnız listeyi yeniler. */
async function tazele() {
  const sonuc = await gonder();
  if (!sonuc || sonuc.gonderilen === 0) {
    await yukle();
  }
}

function basla() {
  qrAnahtariniAl();
  $("yeni-ac").addEventListener("click", () => formAc(true));
  $("yeni-vazgec").addEventListener("click", () => formAc(false));
  $("yeni-form").addEventListener("submit", yeniEkle);
  $("eklendi-kapat").addEventListener("click", () => {
    $("eklendi").classList.add("gizli");
    durum.sonEklenen = null;
  });
  $("anahtar-form").addEventListener("submit", async (olay) => {
    olay.preventDefault();
    const v = durum.veri;
    const hata = await parolaKaydet(d, v.yaghane.kisa_ad, v.ayar.anahtar_izi, $("parola").value);
    if (hata) {
      mesajGoster(hata, "hata");
      return;
    }
    durum.anahtarDurumu = null;
    durum.adlar.clear();
    await yukle();
    mesajGoster("Fabrika anahtarı bu telefona kaydedildi.");
  });
  $("cikis").addEventListener("click", () => {
    if (kuyruk.sayi() > 0) {
      mesajGoster("Gönderilmeyi bekleyen işlem var; bağlantı gelince çıkış yap.", "uyari");
      return;
    }
    cikisYap(d, durum.veri ? durum.veri.yaghane.kisa_ad : "");
  });
  window.addEventListener("online", tazele);
  window.addEventListener("offline", () => {
    durum.cevrimici = false;
    ciz();
  });
  setInterval(() => {
    if (document.visibilityState === "visible") {
      tazele();
    }
  }, YENILEME_MS);
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") {
      tazele();
    }
  });
  if ("serviceWorker" in navigator) {
    navigator.serviceWorker.register("/yaghane/sw.js", { scope: "/yaghane/" }).catch(() => null);
  }
  yukle().then(gonder);
}

basla();
