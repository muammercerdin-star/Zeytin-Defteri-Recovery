// Yağhane sırası sunucu testleri: gerçek schema.sql + Node SQLite üstünde D1 benzeri.
// İstenen başlıklar: tahmini saat, token/kod ayrımı, çiftçi ucunun başkasının verisini vermemesi, 24 saat silme,
// giriş sınırı (çevrimdışı kuyruk tarayıcı tarafında: tests/sira_web).
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { D1 } from "./d1.mjs";

const root = join(dirname(fileURLToPath(import.meta.url)), "../..");
const dir = join(root, "cloud/zeytin-sira");
const { handle } = await import(join(dir, "worker.js"));
const { tahminEt, yukariYuvarla, gosterilenSaat, gosterilenGun, gunOneki, onundekiler, kalanKg } = await import(join(dir, "src/tahmin.mjs"));
const { yaghaneGunu, saatDakika, oncekiGun, SAAT, DAKIKA, GUN_MS } = await import(join(dir, "src/zaman.mjs"));
const { sifreOzeti, sifreDogru, yeniKod, yeniToken, tokenBicimi, PBKDF2_EN_COK } = await import(join(dir, "src/guvenlik.mjs"));
const { temizle } = await import(join(dir, "src/temizlik.mjs"));
const tohum = await import(join(dir, "tohum.mjs"));

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

// ---------------------------------------------------------------- saat ve gün
const T = Date.parse("2026-11-22T08:00:00Z"); // 11:00 Türkiye
esit(saatDakika(T), "11:00", "Türkiye saati UTC+3");
esit(yaghaneGunu(T, "05:00"), "2026-11-22", "gündüz bugün");
esit(yaghaneGunu(Date.parse("2026-11-22T01:30:00Z"), "05:00"), "2026-11-21", "04:30 bir önceki yağhane günü");
esit(yaghaneGunu(Date.parse("2026-11-22T02:00:00Z"), "05:00"), "2026-11-22", "05:00'te yeni gün");
esit(yaghaneGunu(Date.parse("2026-11-21T21:30:00Z"), "00:00"), "2026-11-22", "00:30 gece yarısı başlangıçlı gün");
esit(oncekiGun("2026-03-01"), "2026-02-28", "önceki gün");

// ---------------------------------------------------------------- tahmin (İP9 §4)
{
  const kap = 1200;
  const satirlar = [
    { id: 1, durum: "isleniyor", konum: 1, kg: 600, baslama: T - 15 * DAKIKA },
    { id: 2, durum: "sirada", konum: 2, kg: 480 },
    { id: 3, durum: "sirada", konum: 3, kg: 1200 },
    { id: 4, durum: "bitti", konum: 0, kg: 900 },
  ];
  esit(kalanKg(satirlar[0], kap, T), 300, "işlenen kalan kg (15 dk × 1200 kg/sa = 300 işlendi)");
  const t = tahminEt(satirlar, kap, T);
  // tahmini = şimdi + (kalan 300 + öndekiler) / 1200
  esit(t.byId[2].baslangic, T + (300 / 1200) * SAAT, "ilk sıradaki: şimdi + kalan/kapasite");
  esit(t.byId[3].baslangic, T + ((300 + 480) / 1200) * SAAT, "ikinci: + öndekinin kilosu");
  esit(t.byId[1].bitis, T - 15 * DAKIKA + 30 * DAKIKA, "işlenenin bitişi: başlama + kg/kapasite");
  esit(t.bekleyen, 2, "bekleyen kişi");
  esit(t.bekleyenKg, 1680, "bekleyen kilo");
  check(!(4 in t.byId), "bitmiş satır tahmine girmez");
  esit(onundekiler(satirlar, satirlar[2]), 1, "önündeki sırada bekleyen (işlenen sayılmaz)");
  esit(yukariYuvarla(Date.parse("2026-11-22T08:01:00Z")), Date.parse("2026-11-22T08:10:00Z"), "10 dakikaya yukarı");
  esit(yukariYuvarla(Date.parse("2026-11-22T08:10:00Z")), Date.parse("2026-11-22T08:10:00Z"), "tam saat değişmez");
  esit(gosterilenSaat(T + 23 * DAKIKA), "11:30", "çiftçiye gösterilen yuvarlanmış saat");
  // Çok günlü tahmin: 3 gün biriken sırada "12:00" değil "Perşembe 12:00" demeli (T = 22 Kasım Pazar).
  esit(gunOneki(T + 2 * SAAT, T), "", "aynı gün: gün eki yok");
  esit(gunOneki(T + 20 * SAAT, T), "yarın", "ertesi gün: yarın");
  esit(gunOneki(T + 44 * SAAT, T), "Salı", "iki gün sonra hafta günü");
  esit(gunOneki(T + 4 * GUN_MS, T), "Perşembe", "dört gün sonra hafta günü");
  esit(gunOneki(T + 9 * GUN_MS, T), "1 Ara", "bir haftadan uzak: tarih");
  esit(gosterilenGun(T + 2 * SAAT, T), "13:00", "aynı gün yalnız saat");
  esit(gosterilenGun(T + 20 * SAAT + 23 * DAKIKA, T), "yarın 07:30", "ertesi gün: gün + saat");
  // Gece: Pazar 23:00'te tahmin Pazartesi 01:00 ise çiftçiye "yarın" demeli (takvim günü, yağhane günü değil).
  const gece = Date.parse("2026-11-22T20:00:00Z");
  esit(gunOneki(Date.parse("2026-11-22T22:00:00Z"), gece), "yarın", "gece yarısını aşan tahmin yarın");
  const gecikmis = [{ id: 9, durum: "isleniyor", konum: 1, kg: 100, baslama: T - 2 * SAAT }];
  esit(kalanKg(gecikmis[0], kap, T), 0, "süresi geçen işlemin kalanı eksiye düşmez");
  esit(tahminEt(gecikmis, 0, T).byId[9].bitis, T, "geçmiş bitiş şimdiye çekilir; kapasite 0 ise varsayılan");
}

// ---------------------------------------------------------------- şifre özeti, token, kod
{
  const oz = await sifreOzeti("dogru-sifre-123");
  check(oz.startsWith("pbkdf2-sha256$100000$"), "varsayılan PBKDF2 100.000 tekrar, özetin içinde");
  check(await sifreDogru("dogru-sifre-123", oz), "doğru şifre");
  check(!(await sifreDogru("yanlis-sifre-123", oz)), "yanlış şifre reddedilir");
  const az = await sifreOzeti("dogru-sifre-123", 2000);
  check(await sifreDogru("dogru-sifre-123", az), "tekrar sayısı özetten okunur");
  let firlatti = false;
  try {
    await sifreOzeti("x", PBKDF2_EN_COK + 1);
  } catch {
    firlatti = true;
  }
  check(firlatti, "Workers sınırının üstünde tekrar kabul edilmez");
  check(!(await sifreDogru("a", "pbkdf2-sha256$200000$AAAA$BBBB")), "sınır dışı kayıt doğrulanmaz");
  const tokenlar = new Set();
  for (let i = 0; i < 200; i++) {
    tokenlar.add(yeniToken());
  }
  check(tokenlar.size === 200 && [...tokenlar].every(tokenBicimi), "token 22 karakter, tekil");
  const kodlar = [];
  for (let i = 0; i < 300; i++) {
    kodlar.push(Number(yeniKod()));
  }
  check(kodlar.every((k) => k >= 1000 && k <= 9999), "kod 4 hane");
  let ardisik = 0;
  for (let i = 1; i < kodlar.length; i++) {
    if (kodlar[i] === kodlar[i - 1] + 1) {
      ardisik++;
    }
  }
  check(ardisik < 5, "kodlar ardışık değil (rastgele)");
}

// ---------------------------------------------------------------- tohum
{
  const a = tohum.argumanlar(["--fabrika", "[FABRİKA ADI]", "--kisa", "ornekyaghane", "--ofis", "ofis1", "--havuzcu", "havuz1"]);
  esit(tohum.denetle(a), [], "tohum argümanları geçerli");
  check(tohum.denetle({ ...a, kisa: "Örnek Yağhane" }).length === 1, "kısa ad yalnız küçük harf/rakam");
  check(tohum.denetle({ ...a, havuzcu: "ofis1" }).length === 1, "iki kullanıcı adı farklı");
  check(!tohum.sifreYeterli("kisa"), "kısa şifre reddedilir");
  const kaynak = readFileSync(join(dir, "tohum.mjs"), "utf8");
  check(!/writeFile|appendFile/.test(kaynak), "tohum hiçbir dosyaya yazmaz");
  check(!/--sifre/.test(kaynak), "şifre komut satırı argümanı olarak alınmaz");
}

// ---------------------------------------------------------------- sunucu
const SITE = "https://sira.zeytindefteri.tr";
const TUZ = "test-gizli-tuz-0123456789abcdef0123456789";
const db = new D1();
db.exec(readFileSync(join(dir, "schema.sql"), "utf8"));
const env = { DB: db, GIZLI_TUZ: TUZ, SITE_ORIGIN: SITE };
const args = tohum.argumanlar(["--fabrika", "[FABRİKA ADI]", "--kisa", "ornekyaghane", "--ofis", "ofis1", "--havuzcu", "havuz1", "--tekrar", "2000"]);
db.exec(await tohum.tohumSql(args, { ofis: "ofis-sifresi-1", havuzcu: "havuz-sifresi-1" }, T));
// İkinci fabrika: çiftçi ucu ve bul, öbür fabrikanın satırını asla vermez.
const args2 = tohum.argumanlar(["--fabrika", "[İKİNCİ FABRİKA]", "--kisa", "ikinci", "--ofis", "ofis2", "--havuzcu", "havuz2", "--tekrar", "2000"]);
db.exec(await tohum.tohumSql(args2, { ofis: "ofis-sifresi-2", havuzcu: "havuz-sifresi-2" }, T));

let simdi = T;
async function istek(yontem, yol, govde, ekBaslik = {}) {
  const headers = { "CF-Connecting-IP": "203.0.113.7", ...ekBaslik };
  const init = { method: yontem, headers };
  if (govde !== undefined) {
    if (!headers["Content-Type"]) {
      headers["Content-Type"] = "application/json";
    }
    init.body = JSON.stringify(govde);
  }
  const res = await handle(new Request(SITE + yol, init), env, null, simdi);
  const text = await res.text();
  let body = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = text;
  }
  return { status: res.status, body, text, headers: res.headers };
}
async function girisYap(ad, sifre, ip = "203.0.113.7") {
  const r = await istek("POST", "/api/yaghane/giris", { kullanici_adi: ad, sifre }, { "CF-Connecting-IP": ip, Origin: SITE });
  const cerez = (r.headers.get("set-cookie") || "").split(";")[0];
  return { r, cerez };
}
let opSayaci = 0;
function op() {
  opSayaci++;
  return "op-test-" + String(opSayaci).padStart(4, "0");
}
const SIFRELI = "1." + "A".repeat(40);

// giriş sınırı
{
  const yok = await istek("GET", "/api/yaghane/gun");
  esit(yok.status, 401, "oturumsuz liste yok");
  for (let i = 0; i < 5; i++) {
    const r = await istek("POST", "/api/yaghane/giris", { kullanici_adi: "havuz1", sifre: "yanlis-sifre-" + i }, { "CF-Connecting-IP": "198.51.100.1" });
    esit(r.status, 401, "yanlış şifre " + i);
    check(r.body.mesaj === "Kullanıcı adı ya da şifre yanlış.", "hangi bilginin yanlış olduğu söylenmez");
  }
  const kilit = await istek("POST", "/api/yaghane/giris", { kullanici_adi: "havuz1", sifre: "havuz-sifresi-1" }, { "CF-Connecting-IP": "198.51.100.2" });
  esit(kilit.status, 429, "5 hatadan sonra doğru şifre de 10 dakika bekler (kullanıcı adına göre)");
  check(kilit.body.bekle_sn > 0 && kilit.body.bekle_sn <= 600, "bekleme süresi söylenir");
  simdi = T + 11 * DAKIKA;
  const sonra = await girisYap("havuz1", "havuz-sifresi-1", "198.51.100.3");
  esit(sonra.r.status, 200, "10 dakika sonra giriş açılır");
  for (let i = 0; i < 5; i++) {
    await istek("POST", "/api/yaghane/giris", { kullanici_adi: "yok" + i, sifre: "olmayan-kullanici" }, { "CF-Connecting-IP": "198.51.100.9" });
  }
  const ipKilit = await istek("POST", "/api/yaghane/giris", { kullanici_adi: "ofis1", sifre: "ofis-sifresi-1" }, { "CF-Connecting-IP": "198.51.100.9" });
  esit(ipKilit.status, 429, "aynı IP'den 5 hata: o IP 10 dakika bekler");
  const ipler = db.prepare("SELECT anahtar FROM giris_deneme").bind().first();
  check(!JSON.stringify(await db.prepare("SELECT * FROM giris_deneme").all()).includes("198.51.100"), "IP veritabanına yazılmaz (yalnız tuzlu özet)");
  void ipler;
  simdi = T;
}

const havuz = await girisYap("havuz1", "havuz-sifresi-1", "192.0.2.10");
const ofis = await girisYap("ofis1", "ofis-sifresi-1", "192.0.2.11");
const ofis2 = await girisYap("ofis2", "ofis-sifresi-2", "192.0.2.12");
{
  const c = havuz.r.headers.get("set-cookie");
  check(/HttpOnly/.test(c) && /Secure/.test(c) && /SameSite=Strict/.test(c), "çerez HttpOnly; Secure; SameSite=Strict");
  check(/Max-Age=2592000/.test(c), "oturum 30 gün");
  esit(havuz.r.body.rol, "havuzcu", "rol");
  const kayit = await db.prepare("SELECT ozet FROM oturum").all();
  check(!kayit.results.some((r) => havuz.cerez.endsWith(r.ozet)), "oturum değeri veritabanında düz durmaz");
}
const H = { Cookie: havuz.cerez, Origin: SITE };
const O = { Cookie: ofis.cerez, Origin: SITE };
const O2 = { Cookie: ofis2.cerez, Origin: SITE };

// başka siteden istek
{
  const r = await istek("POST", "/api/yaghane/sira", { op_id: op(), kasa: 3, kg: 100 }, { Cookie: havuz.cerez, Origin: "https://kotu.example" });
  esit(r.status, 403, "başka siteden gelen istek reddedilir");
  const r2 = await istek("POST", "/api/yaghane/sira", { op_id: op(), kasa: 3, kg: 100 }, { Cookie: havuz.cerez, Origin: SITE, "Content-Type": "text/plain" });
  esit(r2.status, 415, "JSON olmayan istek reddedilir");
}

// yeni müşteriler
const m = [];
{
  const duz = await istek("POST", "/api/yaghane/sira", { op_id: op(), ad_sifreli: "Örnek Üretici", kasa: 6, kg: 390 }, H);
  esit(duz.status, 400, "şifresiz isim kabul edilmez");
  const eksik = await istek("POST", "/api/yaghane/sira", { op_id: op(), kasa: 6 }, H);
  esit(eksik.status, 400, "kilo gerekli");
  const veriler = [
    { ad_sifreli: SIFRELI, son2: "42", kasa: 9, kg: 620, telefon: "05001112233" },
    { kasa: 7, kg: 480 },
    { ad_sifreli: SIFRELI, kasa: 16, kg: 1150 },
    { son2: "07", kasa: 6, kg: 390 },
  ];
  for (const v of veriler) {
    const r = await istek("POST", "/api/yaghane/sira", { op_id: op(), ...v }, H);
    esit(r.status, 201, "müşteri eklendi");
    m.push(r.body);
  }
  esit(m.map((x) => x.sira_no), [1, 2, 3, 4], "sıra numaraları");
  check(new Set(m.map((x) => x.kod)).size === 4 && m.every((x) => /^[0-9]{4}$/.test(x.kod)), "kodlar 4 hane ve tekil");
  check(m.every((x) => x.link === SITE + "/" + x.token && tokenBicimi(x.token)), "çiftçi linki token'la");
  const kolonlar = (await db.prepare("PRAGMA table_info(sira)").all()).results.map((c) => c.name);
  check(!kolonlar.some((c) => /telefon|tel|ad$/.test(c) && c !== "ad_sifreli"), "sira tablosunda telefon ya da düz ad sütunu yok");
  const hepsi = JSON.stringify((await db.prepare("SELECT * FROM sira").all()).results);
  check(!hepsi.includes("05001112233") && !hepsi.includes("2233"), "telefon numarası sunucuya yazılmaz");
  const bul = await db.prepare("SELECT bul_tuz, bul_ozet FROM sira WHERE id = ?").bind(m[0].id).first();
  check(bul.bul_ozet && bul.bul_ozet.length === 43 && bul.bul_ozet !== "42", "son 2 hane yalnız tuzlu özet");
  const yok = await db.prepare("SELECT bul_ozet FROM sira WHERE id = ?").bind(m[1].id).first();
  esit(yok.bul_ozet, null, "telefonsuz müşteride özet yok");
}

// liste
{
  const r = await istek("GET", "/api/yaghane/gun", undefined, H);
  esit(r.status, 200, "gün listesi");
  esit(r.body.gun, "2026-11-22", "yağhane günü");
  esit(r.body.ozet.bekleyen, 4, "bekleyen 4");
  esit(r.body.ozet.bekleyen_kg, 2640, "bekleyen kg");
  esit(r.body.sira[0].tahmini, "11:00", "ilk sıradakinin tahmini şimdi");
  esit(r.body.sira[1].tahmini, "11:40", "ikinci: 620 kg / 1200 = 31 dk → 11:40");
  esit(r.body.sira[0].ad_sifreli, SIFRELI, "panel şifreli adı alır (tarayıcı çözer)");
  esit(r.body.sira.map((s) => s.token).sort(), m.map((x) => x.token).sort(), "panel fiş için kendi fabrikasının linklerini alır");
  check(r.body.istek_bugun > 0, "günlük istek sayacı");
  const ikinci = await istek("GET", "/api/yaghane/gun", undefined, O2);
  esit(ikinci.body.sira.length, 0, "başka fabrika bu fabrikanın sırasını görmez");
}

// çiftçi ucu: yalnız kendi bilgisi
{
  const r = await istek("GET", "/api/sira/" + m[3].token);
  esit(r.status, 200, "çiftçi durumu");
  esit(
    Object.keys(r.body).sort(),
    ["biter", "durum", "fabrika", "gelis", "guncel", "kasa", "kg", "kod", "onunde", "sira_no", "tahmini", "yag_kg"],
    "çiftçi cevabındaki alanlar"
  );
  esit(r.body.onunde, 3, "önünde 3 kişi");
  esit(r.body.kod, m[3].kod, "kendi kodu");
  esit(r.body.kg, 390, "kendi kilosu");
  check(!("ad" in r.body) && !r.text.includes(SIFRELI), "isim (şifreli bile) gösterilmez");
  for (const baska of m.slice(0, 3)) {
    check(!r.text.includes(baska.kod) && !r.text.includes(baska.token), "başkasının kodu/token'ı yok");
  }
  check(!r.text.includes("620") && !r.text.includes("1150") && !r.text.includes("480"), "başkasının kilosu yok");
  esit((await istek("GET", "/api/sira/" + m[3].kod)).status, 404, "4 haneli kod tek başına hiçbir şey göstermez");
  esit((await istek("GET", "/api/sira/" + "A".repeat(22))).status, 404, "uydurma token");
  esit((await istek("GET", "/api/sira/" + m[3].token.slice(0, 21))).status, 404, "kısa token");
}

// elle giriş (bul)
{
  const ok = await istek("POST", "/api/sira/bul", { fabrika: "ornekyaghane", kod: m[0].kod, ek: "42" });
  esit(ok.body && ok.body.token, m[0].token, "fabrika + kod + telefonun son 2 hanesi");
  esit((await istek("POST", "/api/sira/bul", { fabrika: "ornekyaghane", kod: m[0].kod, ek: "43" })).status, 404, "yanlış son 2 hane");
  esit((await istek("POST", "/api/sira/bul", { fabrika: "ornekyaghane", kod: m[0].kod, ek: "9" })).status, 404, "telefonu olan müşteride kasa sayısı geçmez");
  const kasa = await istek("POST", "/api/sira/bul", { fabrika: "ornekyaghane", kod: m[1].kod, ek: "7" });
  esit(kasa.body && kasa.body.token, m[1].token, "telefonsuz müşteride kasa sayısı");
  esit((await istek("POST", "/api/sira/bul", { fabrika: "ikinci", kod: m[1].kod, ek: "7" })).status, 404, "başka fabrikanın koduyla bulunmaz");
  esit((await istek("POST", "/api/sira/bul", { fabrika: "ornekyaghane", kod: m[1].kod })).status, 404, "ikinci bilgi olmadan kod yetmez");
  let son = null;
  for (let i = 0; i < 6; i++) {
    son = await istek("POST", "/api/sira/bul", { fabrika: "ornekyaghane", kod: "1234", ek: "11" }, { "CF-Connecting-IP": "203.0.113.7" });
  }
  esit(son.status, 429, "elle girişte 10 dakikada 10 deneme sınırı");
  const baskaIp = await istek("POST", "/api/sira/bul", { fabrika: "ornekyaghane", kod: m[1].kod, ek: "7" }, { "CF-Connecting-IP": "203.0.113.99" });
  esit(baskaIp.status, 200, "sınır IP özetine göre");
}

// işlemler, tek sefer, çakışma
{
  const id = m[0].id;
  const o1 = op();
  const b1 = await istek("POST", "/api/yaghane/sira/" + id + "/baslat", { op_id: o1 }, H);
  esit(b1.status, 200, "başlat");
  const tekrar = await istek("POST", "/api/yaghane/sira/" + id + "/baslat", { op_id: o1 }, H);
  check(tekrar.status === 200 && tekrar.body.tekrar === true, "aynı işlem kimliği iki kez uygulanmaz (tekrar cevabı)");
  const cak = await istek("POST", "/api/yaghane/sira/" + id + "/baslat", { op_id: op() }, O);
  esit(cak.status, 409, "başka cihazdan ikinci başlat: çakışma");
  esit(cak.body.simdiki, { durum: "isleniyor" }, "sunucudaki durum söylenir (sunucu kazanır)");
  check(/işleniyor/.test(cak.body.mesaj), "Türkçe çakışma mesajı");
  esit((await istek("POST", "/api/yaghane/sira/" + id + "/baslat", {}, H)).status, 400, "işlem kimliği şart");
  simdi = T + 10 * DAKIKA;
  const fazla = await istek("POST", "/api/yaghane/sira/" + id + "/bitti", { op_id: op(), yag_kg: 700 }, H);
  esit(fazla.status, 400, "yağ zeytinden fazla olamaz");
  const ciftci = await istek("GET", "/api/sira/" + m[0].token);
  esit(ciftci.body.durum, "isleniyor", "çiftçi işleniyor görür");
  esit(ciftci.body.biter, "11:40", "işlenen: başlama 11:00 + 31 dk → 11:40");
  const bitti = await istek("POST", "/api/yaghane/sira/" + id + "/bitti", { op_id: op(), yag_kg: 102 }, H);
  esit(bitti.status, 200, "bitti");
  esit((await istek("GET", "/api/sira/" + m[0].token)).body.yag_kg, 102, "çiftçi yağ kilosunu görür");
  esit((await istek("POST", "/api/yaghane/sira/" + id + "/iptal", { op_id: op() }, H)).status, 409, "biten iptal edilmez");
  // taşı: 4. müşteri yukarı
  const once = await istek("GET", "/api/sira/" + m[3].token);
  esit(once.body.onunde, 2, "önünde 2 kişi");
  esit((await istek("POST", "/api/yaghane/sira/" + m[3].id + "/tasi", { op_id: op(), yon: "yukari" }, H)).status, 200, "yukarı taşı");
  esit((await istek("GET", "/api/sira/" + m[3].token)).body.onunde, 1, "taşınınca önündeki azalır");
  esit((await istek("GET", "/api/sira/" + m[3].token)).body.sira_no, 4, "fiş numarası değişmez");
  esit((await istek("POST", "/api/yaghane/sira/" + m[3].id + "/tasi", { op_id: op(), yon: "yan" }, H)).status, 400, "geçersiz yön");
  // çevrimdışı iptal: havuzcu "sırada" görmüştü, bu arada ofis başlattı → iptal uygulanmaz
  await istek("POST", "/api/yaghane/sira/" + m[2].id + "/baslat", { op_id: op() }, O);
  const gec = await istek("POST", "/api/yaghane/sira/" + m[2].id + "/iptal", { op_id: op(), beklenen: "sirada" }, H);
  esit(gec.status, 409, "beklenen durum değiştiyse iptal uygulanmaz (sunucu kazanır)");
  const ipt = await istek("POST", "/api/yaghane/sira/" + m[2].id + "/iptal", { op_id: op(), beklenen: "isleniyor" }, O);
  esit(ipt.status, 200, "iptal");
  esit((await istek("POST", "/api/yaghane/sira/" + m[2].id + "/baslat", { op_id: op() }, O2)).status, 404, "başka fabrika bu satıra dokunamaz");
  const liste = await istek("GET", "/api/yaghane/gun", undefined, O);
  esit(liste.body.sira.map((s) => s.durum), ["sirada", "sirada", "bitti", "iptal"], "liste sırası: işlenen, sıradakiler, biten, iptal");
  esit(liste.body.ozet.islenen_kg, 620, "bugün işlenen");
  esit(liste.body.ozet.yag_kg, 102, "bugünkü yağ");
  simdi = T;
}

// ofis ayarları ve anahtar izi
{
  esit((await istek("PUT", "/api/yaghane/ayar", { kapasite_kg_saat: 1500 }, H)).status, 403, "havuzcu kapasite değiştiremez");
  esit((await istek("PUT", "/api/yaghane/ayar", { kapasite_kg_saat: 50 }, O)).status, 400, "kapasite sınırı");
  esit((await istek("PUT", "/api/yaghane/ayar", { gun_baslangic: "5:00" }, O)).status, 400, "gün başlangıcı SS:DD");
  const ok = await istek("PUT", "/api/yaghane/ayar", { kapasite_kg_saat: 1500, gun_baslangic: "06:00" }, O);
  esit(ok.body, { kapasite_kg_saat: 1500, gun_baslangic: "06:00" }, "ofis ayarı");
  await istek("PUT", "/api/yaghane/ayar", { kapasite_kg_saat: 1200, gun_baslangic: "05:00" }, O);
  esit((await istek("PUT", "/api/yaghane/anahtar", { iz: "abcdefghijk" }, H)).status, 403, "havuzcu anahtar kuramaz");
  esit((await istek("PUT", "/api/yaghane/anahtar", { iz: "abcdefghijk" }, O)).status, 200, "ofis anahtar izini yazar");
  esit((await istek("PUT", "/api/yaghane/anahtar", { iz: "zzzzzzzzzzz" }, O)).status, 409, "anahtar kazara değişmez");
  esit((await istek("PUT", "/api/yaghane/anahtar", { iz: "zzzzzzzzzzz", yenile: true }, O)).status, 200, "bilerek yenilenir");
  esit((await istek("GET", "/api/yaghane/gun", undefined, H)).body.ayar.anahtar_izi, "zzzzzzzzzzz", "cihazlar izi görür");
}

// çevrimdışı kod yedeği
{
  const y = await istek("POST", "/api/yaghane/yedek", { adet: 3 }, H);
  esit(y.status, 201, "yedek kod");
  esit(y.body.yedek.length, 3, "3 yedek");
  const kullanilan = new Set(m.map((x) => x.kod));
  check(y.body.yedek.every((x) => !kullanilan.has(x.kod)), "yedek kodlar bugünküyle çakışmaz");
  esit((await istek("POST", "/api/yaghane/yedek", { adet: 11 }, H)).status, 400, "en çok 10 yedek");
  const yed = y.body.yedek[0];
  const o1 = op();
  const r = await istek("POST", "/api/yaghane/sira", { op_id: o1, kasa: 4, kg: 250, yedek: { kod: yed.kod, token: yed.token } }, H);
  esit(r.status, 201, "çevrimdışı eklenen müşteri yedekle gelir");
  check(r.body.kod === yed.kod && r.body.token === yed.token, "WhatsApp'la gönderilen link/kod geçerli kalır");
  const yine = await istek("POST", "/api/yaghane/sira", { op_id: op(), kasa: 4, kg: 250, yedek: { kod: yed.kod, token: yed.token } }, H);
  esit(yine.status, 409, "aynı yedek iki kez kullanılmaz");
  const replay = await istek("POST", "/api/yaghane/sira", { op_id: o1, kasa: 4, kg: 250, yedek: { kod: yed.kod, token: yed.token } }, H);
  check(replay.status === 201 && replay.body.tekrar === true && replay.body.id === r.body.id, "kuyruk aynı işlemi yeniden gönderirse ikinci satır açılmaz");
  // dünkü yedek, bugün dolu bir kodla: yeni kod verilir, token aynı
  await db.prepare("INSERT INTO kod_yedegi (yaghane_id, gun, kod, token, ayrilma) VALUES (1, '2026-11-21', ?, ?, ?)")
    .bind(m[1].kod, "B".repeat(22), T - SAAT)
    .run();
  const dun = await istek("POST", "/api/yaghane/sira", { op_id: op(), kasa: 2, kg: 100, yedek: { kod: m[1].kod, token: "B".repeat(22) } }, H);
  check(dun.status === 201 && dun.body.kod_degisti === true && dun.body.kod !== m[1].kod && dun.body.token === "B".repeat(22), "dünkü yedeğin kodu doluysa değişir, söylenir");
}

// gün devri: dünden kalan sıradaki görünür
{
  await db.prepare(
    "INSERT INTO sira (yaghane_id, gun, sira_no, konum, kod, token, kasa, kg, durum, gelis) VALUES (1, '2026-11-21', 40, 0.5, '5555', ?, 3, 200, 'sirada', ?)"
  ).bind("C".repeat(22), T - 10 * SAAT).run();
  const r = await istek("GET", "/api/yaghane/gun", undefined, H);
  check(r.body.sira.some((s) => s.sira_no === 40 && s.gun === "2026-11-21"), "gece kalan sıradaki yeni günde de listede");
  check(r.body.sira.find((s) => s.durum === "sirada").sira_no === 40, "konumu en önde olduğu için ilk sırada");
}

// temizlik (24 saat ve 90 gün)
{
  simdi = T + 25 * SAAT;
  esit((await istek("GET", "/api/sira/" + m[0].token)).status, 404, "biten sıranın linki 24 saat sonra kapanır");
  esit((await istek("GET", "/api/sira/" + m[3].token)).status, 200, "süren sıranın linki açık kalır");
  await temizle(db, simdi);
  const s = await db.prepare("SELECT COUNT(*) AS n FROM sira WHERE ad_sifreli IS NOT NULL OR bul_ozet IS NOT NULL OR bul_tuz IS NOT NULL").first();
  esit(s.n, 0, "24 saat sonra şifreli ad ve elle giriş özeti silinir");
  const sayi = await db.prepare("SELECT COUNT(*) AS n FROM sira").first();
  check(sayi.n > 0, "satırların kendisi 90 güne kadar durur");
  await db.prepare("INSERT INTO oturum (ozet, kullanici_id, bitis) VALUES ('eski', 1, ?)").bind(simdi - 1).run();
  simdi = T + 91 * GUN_MS;
  await temizle(db, simdi);
  esit((await db.prepare("SELECT COUNT(*) AS n FROM sira").first()).n, 0, "90 gün sonra satırlar silinir");
  const oz = await db.prepare("SELECT * FROM gun_ozet WHERE yaghane_id = 1 AND gun = '2026-11-22'").first();
  check(oz && oz.toplam_kg === 620 && oz.toplam_yag_kg === 102 && oz.islem_sayisi === 1, "isimsiz gün toplamı kalır: " + JSON.stringify(oz));
  esit(Object.keys(oz).sort(), ["gun", "islem_sayisi", "toplam_kg", "toplam_yag_kg", "yaghane_id"], "gün toplamında kişisel alan yok");
  esit((await db.prepare("SELECT COUNT(*) AS n FROM oturum WHERE ozet = 'eski'").first()).n, 0, "süresi dolan oturum silinir");
  esit((await db.prepare("SELECT COUNT(*) AS n FROM giris_deneme").first()).n, 0, "deneme kayıtları 1 gün sonra silinir");
  esit((await db.prepare("SELECT COUNT(*) AS n FROM kod_yedegi").first()).n, 0, "kullanılmayan yedek kodlar silinir");
  await temizle(db, simdi + SAAT);
  const oz2 = await db.prepare("SELECT toplam_kg FROM gun_ozet WHERE yaghane_id = 1 AND gun = '2026-11-22'").first();
  esit(oz2.toplam_kg, 620, "sonraki temizlik toplamı küçültmez");
  simdi = T;
}

// tek dokunuş Bitti: yağ kilosu isteğe bağlı, sonradan "yag" ile yazılır
{
  simdi = T + 2 * GUN_MS;
  const hv = await girisYap("havuz1", "havuz-sifresi-1");
  const HV = { Cookie: hv.cerez, Origin: SITE };
  const o2 = await girisYap("ofis2", "ofis-sifresi-2");
  const OF2 = { Cookie: o2.cerez, Origin: SITE };
  const yeni = await istek("POST", "/api/yaghane/sira", { op_id: op(), kasa: 5, kg: 300 }, HV);
  esit(yeni.status, 201, "yeni müşteri (bitti testi)");
  const id = yeni.body.id;
  esit((await istek("POST", "/api/yaghane/sira/" + id + "/yag", { op_id: op(), yag_kg: 40 }, HV)).status, 409, "sıradaki müşteriye yağ yazılmaz");
  await istek("POST", "/api/yaghane/sira/" + id + "/baslat", { op_id: op() }, HV);
  esit((await istek("POST", "/api/yaghane/sira/" + id + "/bitti", { op_id: op(), yag_kg: "abc" }, HV)).status, 400, "dolu ama geçersiz yağ reddedilir");
  const tek = await istek("POST", "/api/yaghane/sira/" + id + "/bitti", { op_id: op() }, HV);
  esit(tek.status, 200, "yağ yazmadan tek dokunuş Bitti");
  esit(tek.body.yag_kg, null, "yağ boş kalır");
  const bos = await istek("POST", "/api/yaghane/sira/" + id + "/bitti", { op_id: op(), yag_kg: "" }, HV);
  esit(bos.status, 409, "biten ikinci kez bitmez (boş yağ alanı da kabul edilir, durum çakışır)");
  const cf = await istek("GET", "/api/sira/" + yeni.body.token);
  check(cf.body.durum === "bitti" && cf.body.yag_kg === null, "çiftçi bitti görür, yağ henüz yok");
  esit((await istek("POST", "/api/yaghane/sira/" + id + "/yag", { op_id: op(), yag_kg: 400 }, HV)).status, 400, "sonradan yağ da zeytinden fazla olamaz");
  esit((await istek("POST", "/api/yaghane/sira/" + id + "/yag", { op_id: op() }, HV)).status, 400, "yag işleminde kilo şart");
  const sonra = await istek("POST", "/api/yaghane/sira/" + id + "/yag", { op_id: op(), yag_kg: "48,5".replace(",", ".") }, HV);
  esit(sonra.status, 200, "yağ sonradan yazılır");
  esit((await istek("GET", "/api/sira/" + yeni.body.token)).body.yag_kg, 48.5, "çiftçi sonradan yazılan yağı görür");
  esit((await istek("POST", "/api/yaghane/sira/" + id + "/yag", { op_id: op(), yag_kg: 50 }, HV)).status, 200, "yağ düzeltilebilir");
  esit((await istek("POST", "/api/yaghane/sira/" + id + "/yag", { op_id: op(), yag_kg: 50 }, OF2)).status, 404, "başka fabrika yağ yazamaz");
  const gun = await istek("GET", "/api/yaghane/gun", undefined, HV);
  esit(gun.body.ozet.yag_kg, 50, "günlük yağ toplamı sonradan yazılanı sayar");

  // kayıtlar (rapor): yalnız ofis, isimsiz gün toplamları; 90 günden eskisi gun_ozet'ten
  const of1 = await girisYap("ofis1", "ofis-sifresi-1");
  const OF1 = { Cookie: of1.cerez, Origin: SITE };
  const iptalEdilen = await istek("POST", "/api/yaghane/sira", { op_id: op(), kasa: 2, kg: 100 }, HV);
  await istek("POST", "/api/yaghane/sira/" + iptalEdilen.body.id + "/iptal", { op_id: op() }, HV);
  await istek("POST", "/api/yaghane/sira", { op_id: op(), kasa: 4, kg: 250 }, HV);
  esit((await istek("GET", "/api/yaghane/rapor", undefined, HV)).status, 403, "havuzcu kayıtları göremez");
  esit((await istek("GET", "/api/yaghane/rapor")).status, 401, "oturumsuz kayıt yok");
  const bugun = await istek("GET", "/api/yaghane/rapor", undefined, OF1);
  esit(bugun.status, 200, "ofis bugünün kaydını alır");
  esit(bugun.body.gunler.length, 1, "bugün tek gün");
  const g0 = bugun.body.gunler[0];
  esit([g0.gelen, g0.iptal, g0.gelen_kg, g0.islenen, g0.islenen_kg, g0.yag_kg, g0.yagli_kg, g0.baslayan], [2, 1, 550, 1, 300, 50, 300, 1], "gün toplamı: gelen, iptal, kilo, işlenen, yağ, bekleme sayısı");
  check(g0.ozet === false && g0.bekleme_ms >= 0, "canlı gün");
  const json1 = JSON.stringify(bugun.body);
  check(!/kod|token|ad_sifreli|bul_/.test(json1), "kayıtta kod, token, ad yok: " + json1.slice(0, 120));
  const sezon = await istek("GET", "/api/yaghane/rapor?bas=2026-09-01&son=" + bugun.body.bugun, undefined, OF1);
  esit(sezon.status, 200, "sezon aralığı");
  const eskiGun = sezon.body.gunler.find((g) => g.gun === "2026-11-22");
  check(eskiGun && eskiGun.ozet === true && eskiGun.islenen === 1 && eskiGun.islenen_kg === 620 && eskiGun.yag_kg === 102 && eskiGun.gelen === null, "silinen günler isimsiz gün toplamından gelir: " + JSON.stringify(eskiGun));
  esit(sezon.body.toplam.islenen_kg, 920, "sezon toplamı canlı + eski günler");
  esit(sezon.body.gunler[0].gun, bugun.body.bugun, "yeni gün üstte");
  const baska = await istek("GET", "/api/yaghane/rapor?bas=2026-09-01&son=" + bugun.body.bugun, undefined, OF2);
  check(!baska.body.gunler.some((g) => g.gun === "2026-11-22" && g.islenen_kg === 620), "başka fabrikanın kayıtları görünmez");
  esit((await istek("GET", "/api/yaghane/rapor?bas=2026-13-01&son=2026-12-01", undefined, OF1)).status, 400, "geçersiz tarih");
  esit((await istek("GET", "/api/yaghane/rapor?bas=2026-12-01&son=2026-11-01", undefined, OF1)).status, 400, "ters aralık");
  esit((await istek("GET", "/api/yaghane/rapor?bas=2025-01-01&son=2026-12-01", undefined, OF1)).status, 400, "400 günden uzun aralık");
  esit((await istek("GET", "/api/yaghane/rapor?bas=2026-11-01'--&son=2026-12-01", undefined, OF1)).status, 400, "biçim dışı tarih reddedilir");
  simdi = T;
}

// tahmin isabeti: çiftçiye söylenen başlama ile gerçek başlama farkı
{
  const db2 = new D1();
  db2.exec(readFileSync(join(dir, "schema.sql"), "utf8"));
  const a2 = tohum.argumanlar(["--fabrika", "[F]", "--kisa", "isabetfab", "--ofis", "ofisx", "--havuzcu", "havuzx", "--tekrar", "2000"]);
  db2.exec(await tohum.tohumSql(a2, { ofis: "ofis-sifresi-x", havuzcu: "havuz-sifresi-x" }, T));
  const env2 = { DB: db2, GIZLI_TUZ: TUZ, SITE_ORIGIN: SITE };
  let an = T;
  async function istek2(yontem, yol, govde, cerez) {
    const headers = { "CF-Connecting-IP": "203.0.113.50" };
    if (cerez) {
      headers.Cookie = cerez;
      headers.Origin = SITE;
    }
    const init = { method: yontem, headers };
    if (govde !== undefined) {
      headers["Content-Type"] = "application/json";
      init.body = JSON.stringify(govde);
    }
    const res = await handle(new Request(SITE + yol, init), env2, null, an);
    const txt = await res.text();
    return { status: res.status, body: txt ? JSON.parse(txt) : null };
  }
  const girisRes = await handle(new Request(SITE + "/api/yaghane/giris", { method: "POST", headers: { "Content-Type": "application/json", "CF-Connecting-IP": "203.0.113.51", Origin: SITE }, body: JSON.stringify({ kullanici_adi: "havuzx", sifre: "havuz-sifresi-x" }) }), env2, null, an);
  const cerez2 = (girisRes.headers.get("set-cookie") || "").split(";")[0];
  const bosGun = await istek2("GET", "/api/yaghane/gun", undefined, cerez2);
  esit(bosGun.body.isabet, { n: 0, ort_mutlak_dk: null, ort_isaretli_dk: null }, "henüz işlem yokken isabet ölçülmemiş");
  const A = await istek2("POST", "/api/yaghane/sira", { op_id: "is-a-0001", kasa: 10, kg: 600 }, cerez2);
  const B = await istek2("POST", "/api/yaghane/sira", { op_id: "is-b-0002", kasa: 10, kg: 600 }, cerez2);
  esit(A.status, 201, "A eklendi");
  esit(B.status, 201, "B eklendi");
  const satA = await db2.prepare("SELECT tahmin_baslama FROM sira WHERE id = ?").bind(A.body.id).first();
  const satB = await db2.prepare("SELECT tahmin_baslama FROM sira WHERE id = ?").bind(B.body.id).first();
  esit(Number(satA.tahmin_baslama), T, "boş kuyrukta A'nın tahmini başlaması şimdi");
  esit(Number(satB.tahmin_baslama), T + 30 * DAKIKA, "B'ye önündeki 600 kg / 1200 = 30 dk sonrası söylendi");
  // A 5 dk geç, B 10 dk geç başladı
  an = T + 5 * DAKIKA;
  esit((await istek2("POST", "/api/yaghane/sira/" + A.body.id + "/baslat", { op_id: "is-ba-0001" }, cerez2)).status, 200, "A başladı");
  an = T + 40 * DAKIKA;
  esit((await istek2("POST", "/api/yaghane/sira/" + B.body.id + "/baslat", { op_id: "is-bb-0001" }, cerez2)).status, 200, "B başladı");
  const g2 = await istek2("GET", "/api/yaghane/gun", undefined, cerez2);
  esit(g2.body.isabet.n, 2, "iki işlem ölçüldü");
  esit(g2.body.isabet.ort_isaretli_dk, 8, "ortalama 8 dk geç ((5+10)/2 yuvarlanmış)");
  esit(g2.body.isabet.ort_mutlak_dk, 8, "ortalama mutlak sapma 8 dk");
  // op_id tekrarı isabeti iki kez saymaz
  await istek2("POST", "/api/yaghane/sira/" + A.body.id + "/baslat", { op_id: "is-ba-0001" }, cerez2);
  const g3 = await istek2("GET", "/api/yaghane/gun", undefined, cerez2);
  esit(g3.body.isabet.n, 2, "aynı işlem kimliği isabeti tekrar saymaz");
  const kolon = (await db2.prepare("PRAGMA table_info(isabet)").all()).results.map((c) => c.name);
  check(!kolon.some((c) => /ad|kod|token|telefon|isim/.test(c)), "isabet tablosunda kişisel alan yok");
}

// genel
{
  esit((await istek("GET", "/api/yok")).status, 404, "bilinmeyen uç");
  const ayarsiz = await handle(new Request(SITE + "/api/sira/bul", { method: "POST", headers: { "Content-Type": "application/json" }, body: "{}" }), { DB: db }, null, T);
  esit(ayarsiz.status, 503, "GIZLI_TUZ yoksa elle giriş çalışmaz");
  const bozuk = await handle(new Request(SITE + "/api/yaghane/gun", { headers: { Cookie: "zs_oturum=" + "x".repeat(43) } }), { DB: { prepare() { throw new Error("gizli ayrıntı Ayşe"); } } , GIZLI_TUZ: TUZ }, null, T);
  const metin = await bozuk.text();
  check(bozuk.status === 500 && !metin.includes("Ayşe") && !metin.includes("gizli"), "iç hata ayrıntısı cevaba sızmaz");
  const kaynak = ["worker.js", "src/api.mjs", "src/guvenlik.mjs", "src/temizlik.mjs", "src/tahmin.mjs"].map((f) => readFileSync(join(dir, f), "utf8")).join("\n");
  check(!/console\.(log|error|warn|info)/.test(kaynak), "sunucu koduna log yazılmaz");
  check(!/telefon/i.test(readFileSync(join(dir, "schema.sql"), "utf8").replace(/--.*$/gm, "")), "şemada telefon sütunu yok");
}

console.log((bad ? "FAIL " : "PASS ") + (n - bad) + "/" + n + " Yağhane sırası sunucusu");
process.exit(bad ? 1 : 0);
