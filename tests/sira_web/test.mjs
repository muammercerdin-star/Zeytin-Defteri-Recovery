// Yağhane sırası web tarafı: fabrika anahtarı (şifreleme), telefon (sunucuya gitmez), çevrimdışı kuyruk,
// yeni müşteri gövdesi ve sayfaların statik denetimi. Tarayıcıdaki kodun aynısı Node'da çalışır.
import { readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "../..");
const web = join(root, "web/sira");
const js = join(web, "yaghane/js");
const A = await import(join(js, "anahtar.js"));
const Tel = await import(join(js, "telefon.js"));
const K = await import(join(js, "kuyruk.js"));
const M = await import(join(js, "musteri.js"));
globalThis.document = globalThis.document || undefined;
const F = await import(join(js, "fis.js"));
const R = await import(join(js, "rapor.js"));

let n = 0;
let bad = 0;
function check(ok, m) {
  n++;
  if (!ok) {
    bad++;
    console.log("FAIL " + m);
  }
}
function esit(a, b, m) {
  check(JSON.stringify(a) === JSON.stringify(b), m + ": " + JSON.stringify(a) + " beklenen " + JSON.stringify(b));
}

class Depo {
  constructor() {
    this.m = new Map();
  }
  get length() {
    return this.m.size;
  }
  key(i) {
    return [...this.m.keys()][i] || null;
  }
  getItem(k) {
    return this.m.has(k) ? this.m.get(k) : null;
  }
  setItem(k, v) {
    this.m.set(k, String(v));
  }
  removeItem(k) {
    this.m.delete(k);
  }
}

// ---------------------------------------------------------------- fabrika anahtarı
const SUNUCU_AD_DESENI = /^1\.[A-Za-z0-9_-]{24,150}$/;
{
  const sir = A.yeniSir();
  const parola = A.parolaYaz(sir);
  check(/^([0-9A-Z]{4}-){8}[0-9A-Z]$/.test(parola), "parola 8 × 4 harf + denetim harfi: " + parola);
  esit([...A.parolaOku(parola)], [...sir], "parola geri okunur");
  const kucuk = parola.toLowerCase().replaceAll("-", " ").replaceAll("0", "o");
  esit([...(A.parolaOku(kucuk) || [])], [...sir], "küçük harf, boşluk ve O/0 karışıklığı düzeltilir");
  let yakalanan = 0;
  let deneme = 0;
  const temiz = parola.replaceAll("-", "");
  const alfabe = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
  for (let i = 0; i < 32; i++) {
    for (const c of alfabe) {
      if (c === temiz[i]) {
        continue;
      }
      deneme++;
      if (A.parolaOku(temiz.slice(0, i) + c + temiz.slice(i + 1)) === null) {
        yakalanan++;
      }
    }
  }
  esit(yakalanan, deneme, "tek harflik her yazım hatası yakalanır");
  esit(A.parolaOku("ABCD"), null, "kısa parola");
  esit(A.parolaOku(null), null, "boş parola");
  const iz1 = await A.iz(sir, "ornekyaghane");
  check(/^[A-Za-z0-9_-]{11}$/.test(iz1), "iz 11 karakter (sunucu deseni)");
  esit(await A.iz(sir, "ornekyaghane"), iz1, "iz sabit");
  check((await A.iz(sir, "ikinci")) !== iz1, "iz fabrikaya bağlı");
  check((await A.iz(A.yeniSir(), "ornekyaghane")) !== iz1, "başka anahtarın izi farklı");
  const anahtar = await A.adAnahtari(sir, "ornekyaghane");
  const s1 = await A.adSifrele(anahtar, "ornekyaghane", "Örnek Üretici");
  check(SUNUCU_AD_DESENI.test(s1), "şifreli ad sunucunun kabul ettiği biçimde");
  check(!s1.includes("rnek") && !s1.includes("retici"), "şifreli metinde ad okunmaz");
  esit(await A.adCoz(anahtar, "ornekyaghane", s1), "Örnek Üretici", "ad çözülür");
  check((await A.adSifrele(anahtar, "ornekyaghane", "Örnek Üretici")) !== s1, "aynı ad her seferinde farklı şifrelenir");
  const baskaAnahtar = await A.adAnahtari(A.yeniSir(), "ornekyaghane");
  esit(await A.adCoz(baskaAnahtar, "ornekyaghane", s1), null, "yanlış anahtarla çözülmez");
  const baskaFabrika = await A.adAnahtari(sir, "ikinci");
  esit(await A.adCoz(baskaFabrika, "ikinci", s1), null, "başka fabrikada çözülmez");
  const bozuk = s1.slice(0, -3) + (s1.endsWith("A") ? "BBB" : "AAA");
  esit(await A.adCoz(anahtar, "ornekyaghane", bozuk), null, "değiştirilmiş metin reddedilir");
  esit(await A.adCoz(anahtar, "ornekyaghane", "Örnek Üretici"), null, "şifresiz metin ad sayılmaz");
  check(SUNUCU_AD_DESENI.test(await A.adSifrele(anahtar, "ornekyaghane", "A")), "tek harfli ad da sunucu biçiminde");
  const uzun = await A.adSifrele(anahtar, "ornekyaghane", "Şükrü Çağlar Öztürkoğlu İğneciğüşöçŞĞÜ ".repeat(4) + "😀😀😀");
  check(SUNUCU_AD_DESENI.test(uzun), "en uzun ad da sunucu sınırında (80 bayta kesilir)");
  esit(await A.adSifrele(anahtar, "ornekyaghane", "   "), null, "boş ad gönderilmez");
  esit(A.kisalt("Ayşe Demir"), "Ayşe D.", "TV kısaltması");
  esit(A.kisalt("ayşe ilkay  işçi"), "ayşe İ.", "Türkçe büyük harf");
  esit(A.kisalt("Mehmet"), "Mehmet", "tek kelime");
}

// ---------------------------------------------------------------- telefon
{
  esit(Tel.telefonDuzelt("0532 111 22 33"), "905321112233", "0'lı numara");
  esit(Tel.telefonDuzelt("+90 (532) 111-22-33"), "905321112233", "+90'lı numara");
  esit(Tel.telefonDuzelt("5321112233"), "905321112233", "çıplak numara");
  esit(Tel.telefonDuzelt("0212 111 22 33"), null, "sabit hat (WhatsApp için cep gerekir)");
  esit(Tel.telefonDuzelt("123"), null, "kısa");
  esit(Tel.sonIki("905321112233"), "33", "son iki hane");
  const mesaj = Tel.sirraMesaji("[FABRİKA ADI]", 7, "4827", "https://sira.zeytindefteri.tr/" + "A".repeat(22));
  check(mesaj.includes("Sıranız 7") && mesaj.includes("4827") && mesaj.includes("https://sira.zeytindefteri.tr/"), "mesaj: sıra, kod, link");
  check(!/Ayşe|Üretici/.test(mesaj), "mesajda isim yok");
  check(Tel.whatsappLinki("905321112233", mesaj).startsWith("https://wa.me/905321112233?text="), "WhatsApp linki");
  check(Tel.smsLinki("905321112233", mesaj).startsWith("sms:+905321112233?&body="), "SMS linki (Android + iPhone)");
  check(Tel.sirraMesaji("F", null, "1234", "L").includes("Sıra kodunuz 1234"), "sıra no yokken (çevrimdışı) yalnız kod");
  const depo = new Depo();
  depo.setItem("zs_tel_2026-11-21", JSON.stringify({ 1111: { tel: "905001112233" } }));
  depo.setItem("zs_kuyruk", "[]");
  const defter = new Tel.TelefonDefteri(depo, "2026-11-22");
  esit(defter.temizle(), 1, "dünün telefonları gün başında silinir");
  esit(depo.getItem("zs_tel_2026-11-21"), null, "dünkü defter yok");
  check(depo.getItem("zs_kuyruk") !== null, "başka kayıtlara dokunulmaz");
  defter.kaydet("4827", { tel: "905321112233", link: "L" });
  esit(defter.al("4827").tel, "905321112233", "tekrar gönder için numara bugün durur");
  esit(defter.al("0000"), null, "bilinmeyen kod");
}

// ---------------------------------------------------------------- yeni müşteri gövdesi (sunucuya ne gider)
{
  const sir = A.yeniSir();
  const anahtar = await A.adAnahtari(sir, "ornekyaghane");
  const r = await M.yeniMusteriGovdesi({ ad: "Örnek Üretici", telefon: "0532 111 22 33", kasa: "6", kg: "390,5" }, anahtar, "ornekyaghane", "op-12345678", null);
  check(!r.hata, "geçerli form");
  check(Object.keys(r.govde).every((k) => M.IZINLI_ALANLAR.includes(k)), "gövdede yalnız izinli alanlar: " + Object.keys(r.govde));
  const metin = JSON.stringify(r.govde);
  check(!metin.includes("532") && !metin.includes("1112233") && !metin.includes("Örnek"), "telefon ve ad düz gitmez");
  esit(r.govde.son2, "33", "yalnız son iki hane");
  esit(r.govde.kg, 390.5, "virgüllü kilo");
  esit(r.tel, "905321112233", "numara yalnız tarayıcıda kalır");
  esit(await A.adCoz(anahtar, "ornekyaghane", r.govde.ad_sifreli), "Örnek Üretici", "ad şifreli");
  const anahtarsiz = await M.yeniMusteriGovdesi({ ad: "Örnek", kasa: "6", kg: "390" }, null, "ornekyaghane", "op-12345678", null);
  check(Boolean(anahtarsiz.hata), "anahtar yoksa isim gönderilmez (hata söylenir)");
  const isimsiz = await M.yeniMusteriGovdesi({ kasa: "6", kg: "390" }, null, "ornekyaghane", "op-12345678", { kod: "4827", token: "T".repeat(22), link: "x" });
  check(!isimsiz.hata && !("ad_sifreli" in isimsiz.govde) && !("son2" in isimsiz.govde), "isimsiz ve telefonsuz da olur");
  esit(isimsiz.govde.yedek, { kod: "4827", token: "T".repeat(22) }, "çevrimdışı yedek kod gövdede");
  check(Boolean((await M.yeniMusteriGovdesi({ kasa: "x", kg: "390" }, null, "a", "op", null)).hata), "kasa sayı olmalı");
  check(Boolean((await M.yeniMusteriGovdesi({ kasa: "1", kg: "0" }, null, "a", "op", null)).hata), "kilo gerekli");
  check(Boolean((await M.yeniMusteriGovdesi({ kasa: "1", kg: "5", telefon: "123" }, null, "a", "op", null)).hata), "hatalı telefon söylenir");
}

// ---------------------------------------------------------------- çevrimdışı kuyruk
{
  const depo = new Depo();
  const q = new K.Kuyruk(depo);
  const id1 = q.ekle({ tur: "baslat", id: 5, yol: "/api/yaghane/sira/5/baslat", govde: {}, ozet: "Başlat · kod 4825" });
  q.ekle({ tur: "tasi", id: 6, yol: "/api/yaghane/sira/6/tasi", govde: { yon: "yukari" }, ozet: "↑ kod 4826" });
  q.ekle({ tur: "bitti", id: 4, yol: "/api/yaghane/sira/4/bitti", govde: { yag_kg: 100 }, ozet: "Bitti · kod 4824" });
  check(/^[A-Za-z0-9_-]{22}$/.test(id1), "işlem kimliği sunucu desenine uyar");
  esit(q.sayi(), 3, "3 işlem bekliyor");
  const gorulen = [];
  const agYok = await q.gonderHepsi(async (yol, govde) => {
    gorulen.push(govde.op_id);
    throw new TypeError("Failed to fetch");
  });
  esit(agYok.durdu, "ag", "internet yok: durur");
  esit(q.sayi(), 3, "işlemler kaybolmaz");
  const cevaplar = [
    { status: 200, body: {} },
    { status: 409, body: { hata: "CAKISMA", mesaj: "Bu müşteri şu an işleniyor; işlem uygulanmadı." } },
    { status: 200, body: {} },
  ];
  const sirali = [];
  const sonuc = await q.gonderHepsi(async (yol, govde) => {
    sirali.push(yol);
    if (sirali.length === 1) {
      esit(govde.op_id, gorulen[0], "yeniden denemede aynı işlem kimliği (sunucu iki kez uygulamaz)");
    }
    return cevaplar.shift();
  });
  esit(sirali, ["/api/yaghane/sira/5/baslat", "/api/yaghane/sira/6/tasi", "/api/yaghane/sira/4/bitti"], "sırayla gönderilir");
  esit(q.sayi(), 0, "kuyruk boşaldı");
  esit(sonuc.gonderilen, 3, "3 gönderildi");
  esit(sonuc.mesajlar.length, 1, "çakışma söylenir");
  check(sonuc.mesajlar[0].includes("↑ kod 4826") && sonuc.mesajlar[0].includes("işleniyor"), "hangi işlemin neden uygulanmadığı: " + sonuc.mesajlar[0]);
  q.ekle({ tur: "baslat", id: 1, yol: "/a", govde: {}, ozet: "x" });
  esit((await q.gonderHepsi(async () => ({ status: 401, body: {} }))).durdu, "oturum", "oturum kapanınca durur");
  esit(q.sayi(), 1, "oturum yokken işlem silinmez");
  esit((await q.gonderHepsi(async () => ({ status: 503, body: null }))).durdu, "sunucu", "sunucu hatasında durur");
  esit((await q.gonderHepsi(async () => ({ status: 429, body: null }))).durdu, "sunucu", "kota/sınırda durur");
  esit(q.sayi(), 1, "işlem bekler");
  q.ekle({ tur: "yeni", id: null, yol: "/api/yaghane/sira", govde: { kasa: 1, kg: 5 }, ozet: "Yeni müşteri 1 kasa" });
  const degisti = await q.gonderHepsi(async (yol) => (yol === "/a" ? { status: 200, body: {} } : { status: 201, body: { kod: "9999", kod_degisti: true } }));
  check(degisti.mesajlar.some((m) => m.includes("9999")), "çevrimdışı yedek kod değiştiyse yeni kod söylenir");
  depo.setItem("zs_kuyruk", "bozuk");
  esit(q.liste(), [], "bozuk depo kuyruğu çökertmez");
}

// ---------------------------------------------------------------- ekranda hemen görünme
{
  const sira = [
    { id: 4, durum: "isleniyor", konum: 1, kod: "4824", kg: 620, kasa: 9 },
    { id: 5, durum: "sirada", konum: 2, kod: "4825", kg: 480, kasa: 7 },
    { id: 6, durum: "sirada", konum: 3, kod: "4826", kg: 1150, kasa: 16 },
  ];
  const l = K.yerelGorunum(
    sira,
    [
      { tur: "bitti", id: 4, govde: { yag_kg: 100 } },
      { tur: "tasi", id: 6, govde: { yon: "yukari" } },
      { tur: "baslat", id: 6, govde: {} },
      { tur: "yeni", id: null, govde: { op_id: "o1", kasa: 2, kg: 50, yedek: { kod: "7777", token: "t" } } },
    ],
    1000
  );
  esit(l.map((s) => s.durum + ":" + s.kod), ["isleniyor:4826", "sirada:4825", "sirada:7777", "bitti:4824"], "bekleyen işlemler ekranda");
  check(l.find((s) => s.kod === "7777").gecici === true, "çevrimdışı eklenen geçici görünür");
  check(l.find((s) => s.kod === "4824").bekliyor === true, "gönderilmeyen işaretli");
  esit(sira[0].durum, "isleniyor", "sunucu listesi değişmez");
}

// ---------------------------------------------------------------- tek dokunuş Bitti, yağ sonradan
{
  const sira = [
    { id: 4, durum: "isleniyor", konum: 1, kod: "4824", kg: 620, kasa: 9 },
    { id: 7, durum: "bitti", konum: 0, kod: "4827", kg: 300, kasa: 5, yag_kg: null },
  ];
  const l = K.yerelGorunum(
    sira,
    [
      { tur: "bitti", id: 4, govde: {} },
      { tur: "yag", id: 7, govde: { yag_kg: 48.5 } },
      { tur: "yag", id: 4, govde: { yag_kg: 90 } },
    ],
    1000
  );
  const s4 = l.find((s) => s.id === 4);
  check(s4.durum === "bitti" && s4.yag_kg === 90, "yağsız Bitti sonrası çevrimdışı yazılan yağ ekranda: " + JSON.stringify(s4));
  esit(l.find((s) => s.id === 7).yag_kg, 48.5, "biten satıra sonradan yağ");
  const l2 = K.yerelGorunum([{ id: 5, durum: "sirada", konum: 1, kod: "4825", kg: 480, kasa: 7 }], [{ tur: "yag", id: 5, govde: { yag_kg: 10 } }], 1000);
  check(!l2[0].yag_kg, "sıradakine yağ yazılmaz");
  const l3 = K.yerelGorunum([{ id: 4, durum: "isleniyor", konum: 1, kod: "4824", kg: 620, kasa: 9 }], [{ tur: "bitti", id: 4, govde: {} }], 1000);
  esit(l3[0].yag_kg, null, "yağsız Bitti: yağ boş");
  const hv = readFileSync(join(js, "havuzcu.js"), "utf8");
  check(!hv.includes("Bitti · yağı yaz"), "havuzcu Bitti tek dokunuş (yağ formu açmaz)");
  check(hv.includes('"/yag"'), "havuzcu sonradan yağ yazar");
  const of = readFileSync(join(js, "ofis.js"), "utf8");
  check(of.includes('yol + "bitti", {}'), "ofis Bitti tek dokunuş");
  const cf = readFileSync(join(web, "js/ciftci.js"), "utf8");
  check(cf.includes("Yağ kilosu birazdan"), "çiftçi sayfası yağsız bitişi söyler");
}

// ---------------------------------------------------------------- ofiste büyük QR
{
  const of = readFileSync(join(js, "ofis.js"), "utf8");
  const html = readFileSync(join(web, "yaghane/ofis.html"), "utf8");
  check(html.includes('id="qr-panel"') && html.includes('id="qr-yer"') && html.includes('id="qr-kapat"'), "ofis sayfasında QR paneli");
  check(/qrGoster\(bilgi\)/.test(of), "müşteri eklenince QR kendiliğinden açılır");
  check(of.includes('dugme("QR"'), "sıradaki satırda QR düğmesi");
  check(/window\.location\.origin \+ "\/" \+ bilgi\.token/.test(of), "QR çiftçi linkini taşır (ad ya da telefon taşımaz)");
  check(/export function qrTuvali/.test(readFileSync(join(js, "fis.js"), "utf8")), "QR çizimi fiş ile ortak");
}

// ---------------------------------------------------------------- ofis kayıtları
{
  esit(R.aralikHesapla("2026-11-23", "bugun"), { bas: "2026-11-23", son: "2026-11-23" }, "bugün");
  esit(R.aralikHesapla("2026-11-23", "hafta"), { bas: "2026-11-17", son: "2026-11-23" }, "son 7 gün");
  esit(R.aralikHesapla("2026-03-01", "ay"), { bas: "2026-01-31", son: "2026-03-01" }, "son 30 gün ay sınırı");
  esit(R.aralikHesapla("2026-11-23", "sezon").bas, "2026-09-01", "sezon eylülde başlar");
  esit(R.aralikHesapla("2027-01-15", "sezon").bas, "2026-09-01", "ocakta önceki yılın sezonu");
  esit(R.sureYaz(0, 0), "–", "bekleme yok");
  esit(R.sureYaz(3 * 35 * 60000, 3), "35 dk", "ortalama dakika");
  esit(R.sureYaz(80 * 60000, 1), "1 sa 20 dk", "saat ve dakika");
  const veri = {
    bas: "2026-11-22", son: "2026-11-23",
    gunler: [
      { gun: "2026-11-23", ozet: false, gelen: 2, iptal: 1, gelen_kg: 550, islenen: 1, islenen_kg: 300, yag_kg: 50, yagli_kg: 300, baslayan: 1, bekleme_ms: 1800000 },
      { gun: "2026-11-22", ozet: true, gelen: null, iptal: null, gelen_kg: null, islenen: 1, islenen_kg: 620, yag_kg: 102.5, yagli_kg: 620, baslayan: 0, bekleme_ms: 0 },
    ],
    toplam: { gun_sayisi: 2, gelen: 2, iptal: 1, gelen_kg: 550, islenen: 2, islenen_kg: 920, yag_kg: 152.5, yagli_kg: 920, baslayan: 1, bekleme_ms: 1800000 },
  };
  const csv = R.csvOlustur(veri);
  check(csv.startsWith("\ufeffGün;Gelen müşteri;"), "CSV Excel için BOM ve noktalı virgül");
  const satirlar = csv.trim().split("\r\n");
  esit(satirlar.length, 4, "başlık + 2 gün + toplam");
  esit(satirlar[1], "2026-11-23;2;550;1;300;50;6;30;1", "gün satırı");
  esit(satirlar[2], "2026-11-22;;;1;620;102,5;6;;", "eski gün: gelen ve bekleme boş, ondalık virgül");
  check(satirlar[3].startsWith("TOPLAM;2;550;2;920;152,5;6"), "toplam satırı: " + satirlar[3]);
  check(!/kod|token|ad/i.test(satirlar.slice(1).join("")), "CSV'de isim, kod yok");
  const html = readFileSync(join(web, "yaghane/ofis.html"), "utf8");
  check(html.includes('id="rapor-aralik"') && html.includes('id="rapor-tablo"') && html.includes('id="rapor-indir"'), "ofis sayfasında Kayıtlar bölümü");
  check(readFileSync(join(js, "ofis.js"), "utf8").includes("raporKur("), "ofis kayıtları kurar");
  check(!readFileSync(join(web, "yaghane/havuzcu.html"), "utf8").includes("rapor"), "havuzcu ekranında kayıt yok");
}

// ---------------------------------------------------------------- ortak tasarım dili (zeytindefteri.tr)
{
  const css = readFileSync(join(web, "stil.css"), "utf8");
  check(css.includes("--lacivert: #0B1D28") && css.includes("--yesil-dolu: #6CC24A"), "site renkleri");
  check(/button\.ana, \.dugme\.ana \{ background: var\(--yesil-dolu\)/.test(css), "ana düğme sitedeki yeşil");
  for (const f of ["index.html", "bul/index.html", "yaghane/index.html", "yaghane/havuzcu.html", "yaghane/ofis.html"]) {
    check(readFileSync(join(web, f), "utf8").includes('class="marka-cubuk"'), "lacivert üst çubuk: " + f);
  }
}

// ---------------------------------------------------------------- yedek kodlar
{
  const depo = new Depo();
  const y = new K.YedekKodlar(depo);
  y.ekle([{ kod: "1111", token: "a", gun: "2026-11-20" }, { kod: "2222", token: "b", gun: "2026-11-21" }, { kod: "3333", token: "c", gun: "2026-11-22" }]);
  esit(y.eksik("2026-11-22"), 9, "bugün için 9 eksik");
  esit(y.al("2026-11-22", "2026-11-21").kod, "2222", "dünkü yedek de kullanılabilir");
  esit(y.liste().map((x) => x.kod), ["3333"], "daha eski yedek atılır");
  esit(y.al("2026-11-22", "2026-11-21").kod, "3333", "sonraki");
  esit(y.al("2026-11-22", "2026-11-21"), null, "yedek bitti");
}

// ---------------------------------------------------------------- fiş (58 mm)
{
  const T = Date.parse("2026-11-22T08:00:00Z");
  esit(F.fisZamani(T), "22.11.2026 11:00", "fiş tarihi Türkiye saatiyle");
  const link = "https://sira.zeytindefteri.tr/" + "A".repeat(22);
  const b = { fabrika: "[FABRİKA ADI]", kisaAd: "ornekyaghane", siraNo: 7, kod: "4827", kasa: 6, kg: 390.5, zaman: T, ad: "", link, site: "sira.zeytindefteri.tr" };
  const satirlar = F.fisSatirlari(b);
  const metin = satirlar.map((x) => x.metin).join("\n");
  esit(satirlar[0], { tur: "fabrika", metin: "[FABRİKA ADI]" }, "fişin başında fabrika adı");
  check(satirlar.some((x) => x.tur === "no" && x.metin === "7"), "sıra no");
  check(satirlar.some((x) => x.tur === "kod" && x.metin === "Kod 4827"), "kod");
  check(metin.includes("6 kasa · 390,5 kg"), "kasa ve kg: " + metin);
  check(metin.includes("22.11.2026 11:00"), "tarih ve saat");
  esit(satirlar.filter((x) => x.tur === "qr").map((x) => x.metin), [link], "QR çiftçi linkini taşır");
  check(metin.includes("sira.zeytindefteri.tr/bul") && metin.includes("Yağhane: ornekyaghane · Kod: 4827"), "link açılmazsa elle giriş bilgisi");
  check(!satirlar.some((x) => x.tur === "ad"), "ad çözülmediyse fişte ad yok");
  const adli = F.fisSatirlari({ ...b, ad: "Örnek Üretici" });
  check(adli.some((x) => x.tur === "ad" && x.metin === "Örnek Üretici"), "ad bu cihazda çözüldüyse fişe yazılır");
  const cevrimdisi = F.fisSatirlari({ ...b, siraNo: null });
  check(cevrimdisi.some((x) => x.tur === "no" && x.metin === "–") && cevrimdisi.some((x) => /bağlantı gelince/.test(x.metin)), "çevrimdışı fişte sıra no yerine not");
  const fisKaynak = readFileSync(join(js, "fis.js"), "utf8");
  check(!/fetch|api\(|XMLHttpRequest|sendBeacon/.test(fisKaynak), "fiş sunucuya bir şey göndermez");
  check(/from "\.\/vendor\/qrcode\.js"/.test(fisKaynak), "QR yerel kodlayıcıyla (dış servis yok)");
  const css = readFileSync(join(web, "stil.css"), "utf8");
  check(/@page \{ size: 58mm auto; margin: 0; \}/.test(css), "58 mm kâğıt");
  check(/\.fis-alan \{ display: block; width: 48mm;/.test(css), "basılabilir alan 48 mm");
  check(/body > main \{ display: none !important; \}/.test(css), "yazdırırken yalnız fiş basılır");
  for (const sayfa of ["yaghane/havuzcu.html", "yaghane/ofis.html"]) {
    const t = readFileSync(join(web, sayfa), "utf8");
    check(/<\/main>\n<div id="fis-alan" class="fis-alan"><\/div>/.test(t), "fiş alanı ana içeriğin dışında: " + sayfa);
  }
  check(/fisYazdir/.test(readFileSync(join(js, "havuzcu.js"), "utf8")) && /fisYazdir/.test(readFileSync(join(js, "ofis.js"), "utf8")), "fiş havuzcu ve ofis ekranında");
  check(/\/yaghane\/js\/fis\.js/.test(readFileSync(join(web, "yaghane/sw.js"), "utf8")), "fiş internetsizken de basılır (önbellekte)");
}

// ---------------------------------------------------------------- statik denetim
{
  function dosyalar(dir) {
    const out = [];
    for (const ad of readdirSync(dir)) {
      const p = join(dir, ad);
      if (statSync(p).isDirectory()) {
        out.push(...dosyalar(p));
      } else {
        out.push(p);
      }
    }
    return out;
  }
  const hepsi = dosyalar(web).filter((p) => !p.includes("/vendor/"));
  const htmller = hepsi.filter((p) => p.endsWith(".html"));
  const jsler = hepsi.filter((p) => p.endsWith(".js"));
  for (const h of htmller) {
    const t = readFileSync(h, "utf8");
    check(!/<script(?![^>]*src="\/)/.test(t.replace(/<script type="module" src="\/[^"]+"><\/script>/g, "")), "satır içi script yok: " + h);
    check(!/style="/.test(t), "satır içi stil yok (CSP): " + h);
    // Yalnız iki bağlantıya izin var: KVKK sayfası ve üst çubuktaki marka (zeytindefteri.tr ana sayfası). Kaynak yüklenmez.
    check(!/https?:\/\/(?!zeytindefteri\.tr\/kvkk|zeytindefteri\.tr\/" rel="noopener")/.test(t.replace(/sira\.zeytindefteri\.tr/g, "")), "dış kaynak yok: " + h);
    check(/<html lang="tr">/.test(t), "Türkçe sayfa: " + h);
  }
  for (const f of jsler) {
    const t = readFileSync(f, "utf8");
    check(!/innerHTML|outerHTML|insertAdjacentHTML|document\.write|eval\(/.test(t), "veri HTML olarak yazılmaz: " + f);
    check(!/console\./.test(t), "konsola kişisel veri yazılmaz: " + f);
  }
  const basliklar = readFileSync(join(web, "_headers"), "utf8");
  check(/script-src 'self'/.test(basliklar) && /frame-ancestors 'none'/.test(basliklar) && /connect-src 'self'/.test(basliklar), "CSP");
  check(/Referrer-Policy: no-referrer/.test(basliklar), "token başka siteye yönlendiren olarak gitmez");
  const ciftci = readFileSync(join(web, "js/ciftci.js"), "utf8");
  check(!/ad_sifreli|\.ad\b/.test(ciftci), "çiftçi sayfası isim göstermez");
  check(/credentials: "omit"/.test(ciftci), "çiftçi sayfası çerez göndermez");
  check(/visibilityState === "visible"/.test(ciftci) && /60000/.test(ciftci), "çiftçi sayfası 60 sn'de bir, yalnız ekrandayken yenilenir");
  check(/bildirimKur\(/.test(ciftci), "çiftçi sayfası bildirimi kurar");
  const cbild = readFileSync(join(web, "js/ciftci-bildirim.js"), "utf8");
  check(/userVisibleOnly: true/.test(cbild) && /applicationServerKey/.test(cbild), "bildirim aboneliği görünür + VAPID açık anahtarıyla");
  check(/endpoint: sub\.endpoint/.test(cbild) && /p256dh: j\.keys\.p256dh/.test(cbild) && /auth: j\.keys\.auth/.test(cbild), "bildirim gövdesi yalnız push alanları (isim/telefon yok)");
  check(/Ana Ekrana Ekle/.test(cbild), "desteklemeyen tarayıcıya iPhone yolu anlatılır");
  const swb = readFileSync(join(web, "sw-bildirim.js"), "utf8");
  check(/addEventListener\("push"/.test(swb) && /showNotification/.test(swb), "bildirim SW push'u gösterir");
  check(/addEventListener\("notificationclick"/.test(swb), "bildirim SW tıklamayı sıra sayfasına açar");
  const sw = readFileSync(join(web, "yaghane/sw.js"), "utf8");
  check(/startsWith\("\/api\/"\)/.test(sw), "servis çalışanı API'yi önbelleğe almaz");
  const havuzcu = readFileSync(join(js, "havuzcu.js"), "utf8");
  check(/yeniMusteriGovdesi/.test(havuzcu) && !/telefon:\s*tel|tel:\s*g\.tel\s*}\s*\)/.test(havuzcu.replace(/defter\(\)\.kaydet[^;]*;/g, "")), "havuzcu numarayı yalnız tarayıcıdaki deftere yazar");
  const ofis = readFileSync(join(js, "ofis.js"), "utf8");
  check(!/whatsappLinki|smsLinki|TelefonDefteri/.test(ofis), "ofiste tekrar gönder düğmesi yok");
  const ofisHtml = readFileSync(join(web, "yaghane/ofis.html"), "utf8");
  check(!/y-tel/.test(ofisHtml), "ofis formunda telefon alanı yok");
  check(/id="isabet-deger"/.test(ofisHtml) && /isabetMetni\(v\.isabet\)/.test(ofis), "ofis paneli tahmin isabetini gösterir");
  check(/henüz ölçülmedi/.test(ofis) && /geç başladı/.test(ofis) && /erken başladı/.test(ofis), "isabet metni: ölçülmedi / geç / erken");
  const ortak = readFileSync(join(js, "ortak.js"), "utf8");
  check(/history\.replaceState/.test(ortak), "QR'daki anahtar adres çubuğundan silinir");
  check(/localStorage\.setItem\("zs_giris_ms"/.test(readFileSync(join(js, "giris.js"), "utf8")), "giriş süresi ölçülür (pilot)");
  check(!/wa\.me/.test(readFileSync(join(js, "ofis.js"), "utf8")), "ofis WhatsApp açmaz");
  esit(readFileSync(join(js, "vendor/qrcode.js"), "utf8"), readFileSync(join(root, "web/tezgah/js/vendor/qrcode.js"), "utf8"), "QR kodlayıcı Web Tezgâh'takiyle aynı");
}

console.log((bad ? "FAIL " : "PASS ") + (n - bad) + "/" + n + " Yağhane sırası web");
process.exit(bad ? 1 : 0);
