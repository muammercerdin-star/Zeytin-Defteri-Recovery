// Gerçek tarayıcıda (Chrome, Playwright) uçtan uca: ofis anahtar kurar, havuzcu QR linkiyle anahtarı alır,
// müşteri ekler, çiftçi linki yalnız kendi sırasını gösterir, internet kopunca işlemler kuyruğa girer,
// bağlantı gelince gönderilir, çakışma söylenir, elle giriş çalışır, CSP ihlali olmaz.
// Playwright ya da Chrome yoksa SKIP (çıkış 0).
import { createRequire } from "node:module";
import { mkdirSync } from "node:fs";
import { baslat } from "./sunucu.mjs";

let chromium;
try {
  const require = createRequire(import.meta.url);
  const yollar = [process.env.PLAYWRIGHT_YOLU, "/opt/npm-tools/node_modules/playwright", "playwright"].filter(Boolean);
  for (const y of yollar) {
    try {
      chromium = require(y).chromium;
      break;
    } catch {
      chromium = null;
    }
  }
} catch {
  chromium = null;
}
if (!chromium) {
  console.log("SKIP Playwright yok");
  process.exit(0);
}

let n = 0;
let bad = 0;
function check(ok, m) {
  n++;
  if (!ok) {
    bad++;
    console.log("FAIL " + m);
  }
}

const cikti = process.env.SIRA_EKRAN || "/tmp/sira-ekran";
mkdirSync(cikti, { recursive: true });
const { sunucu, db } = await baslat(0);
const port = sunucu.address().port;
const SITE = "http://localhost:" + port;
let tarayici;
try {
  tarayici = await chromium.launch({ executablePath: process.env.CHROME_YOLU || "/usr/bin/google-chrome", args: ["--no-sandbox"] });
} catch (e) {
  console.log("SKIP Chrome açılamadı: " + String(e.message).split("\n")[0]);
  sunucu.close();
  process.exit(0);
}
const cspHatalari = [];
function izle(sayfa, ad) {
  sayfa.on("console", (m) => {
    if (m.type() === "error" && /Content Security Policy|Refused/.test(m.text())) {
      cspHatalari.push(ad + ": " + m.text());
    }
  });
  sayfa.on("pageerror", (e) => cspHatalari.push(ad + " JS: " + e.message));
}

try {
  // ------------------------------------------------ ofis: giriş, anahtar oluştur
  const ofisCtx = await tarayici.newContext({ viewport: { width: 1366, height: 900 }, locale: "tr-TR", timezoneId: "Europe/Istanbul" });
  const ofis = await ofisCtx.newPage();
  izle(ofis, "ofis");
  await ofis.goto(SITE + "/yaghane/");
  await ofis.fill("#ad", "ofis1");
  await ofis.fill("#sifre", "yanlis-sifre");
  await ofis.click("#gir");
  await ofis.waitForSelector("#mesaj:not(.gizli)");
  check((await ofis.textContent("#mesaj")).includes("yanlış"), "yanlış şifre mesajı");
  await ofis.fill("#sifre", "ofis-sifresi-1");
  await ofis.click("#gir");
  await ofis.waitForURL("**/yaghane/ofis.html");
  await ofis.waitForSelector("button:has-text(\"Fabrika anahtarı oluştur\")");
  await ofis.click("button:has-text(\"Fabrika anahtarı oluştur\")");
  await ofis.waitForSelector("canvas.qr");
  const parola = (await ofis.textContent("p.parola")).trim();
  check(/^([0-9A-Z]{4}-){8}[0-9A-Z]$/.test(parola), "ofis parolayı gösterir");
  const iz = await db.prepare("SELECT anahtar_izi FROM yaghane").first();
  check(/^[A-Za-z0-9_-]{11}$/.test(iz.anahtar_izi), "sunucuda yalnız iz");
  check(!JSON.stringify(iz).includes(parola.replaceAll("-", "").slice(0, 8)), "sunucuda anahtar yok");
  await ofis.screenshot({ path: cikti + "/1-ofis-anahtar.png", fullPage: true });

  // ------------------------------------------------ havuzcu: QR linkiyle gelir
  const havCtx = await tarayici.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, locale: "tr-TR", timezoneId: "Europe/Istanbul" });
  const hav = await havCtx.newPage();
  izle(hav, "havuzcu");
  await hav.goto(SITE + "/yaghane/#anahtar=" + parola.replaceAll("-", ""));
  check(!hav.url().includes("anahtar="), "QR'daki anahtar adres çubuğundan hemen silinir");
  await hav.fill("#ad", "havuz1");
  await hav.fill("#sifre", "havuz-sifresi-1");
  await hav.click("#gir");
  await hav.waitForURL("**/yaghane/havuzcu.html");
  await hav.waitForSelector("#yeni-ac");
  await hav.waitForTimeout(500);
  check(await hav.isHidden("#anahtar-uyari"), "anahtar havuzcu telefonuna aktarıldı (uyarı yok)");

  async function musteriEkle(ad, tel, kasa, kg) {
    await hav.click("#yeni-ac");
    await hav.fill("#y-ad", ad);
    await hav.fill("#y-tel", tel);
    await hav.fill("#y-kasa", kasa);
    await hav.fill("#y-kg", kg);
    await hav.click("#yeni-form button[type=submit]");
    await hav.waitForSelector("#eklendi:not(.gizli)");
    await hav.waitForFunction(() => /\d+\. sıra/.test(document.getElementById("eklendi-baslik").textContent), null, { timeout: 5000 }).catch(() => null);
    const kod = (await hav.textContent("#eklendi-kod")).trim();
    const baslik = await hav.textContent("#eklendi-baslik");
    let wa = null;
    if (tel) {
      wa = await hav.getAttribute("#eklendi-gonder a.ana", "href");
    }
    await hav.click("#eklendi-kapat");
    return { kod, baslik, wa };
  }
  const m1 = await musteriEkle("Örnek Üretici", "0532 111 22 33", "6", "390");
  check(/^\d{4}$/.test(m1.kod), "kod gösterildi: " + m1.kod);
  check(m1.baslik.includes("1. sıra"), "sıra no: " + m1.baslik);
  check(m1.wa && m1.wa.startsWith("https://wa.me/905321112233?text="), "WhatsApp linki havuzcunun telefonunda");
  const mesaj = decodeURIComponent(m1.wa.split("text=")[1]);
  check(!mesaj.includes("Örnek"), "WhatsApp mesajında isim yok");
  const link = /(http\S+)$/.exec(mesaj)[1];
  check(link.includes("/") && /\/[A-Za-z0-9_-]{22}$/.test(link), "mesajda çiftçi linki");
  const m2 = await musteriEkle("", "", "7", "480");
  const m3 = await musteriEkle("Deneme Kişi", "", "16", "1150");
  const satirlar = (await db.prepare("SELECT * FROM sira ORDER BY id").all()).results;
  check(satirlar.length === 3, "3 satır");
  const hepsi = JSON.stringify(satirlar);
  check(!hepsi.includes("Örnek") && !hepsi.includes("Deneme") && !hepsi.includes("1112233"), "sunucuda isim ve telefon düz yok");
  await hav.waitForSelector("text=Örnek Üretici");
  check((await hav.textContent("#sirada")).includes("Deneme Kişi"), "havuzcu isimleri çözerek görür");
  await hav.screenshot({ path: cikti + "/2-havuzcu.png", fullPage: true });

  // ------------------------------------------------ fiş (havuzcu): yazdırma penceresi yerine sayaç
  await hav.evaluate(() => {
    window.__yazdir = 0;
    window.print = () => {
      window.__yazdir += 1;
    };
  });
  const ilkSatir = hav.locator("#sirada .satir").filter({ hasText: "Örnek Üretici" });
  await ilkSatir.locator("button[aria-label='Diğer işlemler']").click();
  await hav.click("#sirada button:has-text(\"Fiş yazdır\")");
  check((await hav.evaluate(() => window.__yazdir)) === 1, "havuzcu fişi yazdırma penceresini açar");
  const fisMetni = await hav.textContent("#fis-alan");
  check(fisMetni.includes("[FABRİKA ADI]") && fisMetni.includes("Kod " + m1.kod) && fisMetni.includes("6 kasa · 390 kg"), "fişte fabrika, kod, kasa ve kg: " + fisMetni);
  check(fisMetni.includes("Örnek Üretici"), "ad havuzcu cihazında çözülüp fişe yazıldı");
  check(/\d{2}\.\d{2}\.\d{4} \d{2}:\d{2}/.test(fisMetni), "fişte tarih ve saat");
  check((await hav.locator("#fis-alan canvas.fis-qr").count()) === 1, "fişte QR");
  await hav.emulateMedia({ media: "print" });
  const genislik = await hav.evaluate(() => document.getElementById("fis-alan").getBoundingClientRect().width);
  check(Math.abs(genislik - (48 / 25.4) * 96) < 2, "fiş 48 mm genişlikte basılır: " + genislik);
  check(await hav.locator("main").isHidden(), "yazdırırken ekranın geri kalanı basılmaz");
  await hav.screenshot({ path: cikti + "/7-fis.png", clip: { x: 0, y: 0, width: 390, height: 640 } });
  await hav.emulateMedia({ media: "screen" });
  await hav.locator("#sirada .satir").filter({ hasText: "Örnek Üretici" }).locator("button[aria-label='Diğer işlemler']").click();

  // ------------------------------------------------ çiftçi linki
  const ciftciCtx = await tarayici.newContext({ viewport: { width: 390, height: 844 }, locale: "tr-TR" });
  const ciftci = await ciftciCtx.newPage();
  izle(ciftci, "çiftçi");
  const ciftciLink = SITE + "/" + link.split("/").pop();
  await ciftci.goto(ciftciLink);
  await ciftci.waitForSelector("#icerik:not(.gizli)");
  const ciftciMetin = await ciftci.textContent("body");
  check(ciftciMetin.includes("Kod " + m1.kod) && (await ciftci.textContent("#no")).trim() === "1", "çiftçi kendi kodunu ve sırasını görür");
  check(ciftciMetin.includes("Sıradaki sizsiniz"), "önünde kimse yok");
  check(!/Örnek|Deneme|480|1\.150|1150/.test(ciftciMetin) && !ciftciMetin.includes(m2.kod) && !ciftciMetin.includes(m3.kod), "çiftçi başkasının adını/kilosunu/kodunu görmez");
  await ciftci.screenshot({ path: cikti + "/3-ciftci.png", fullPage: true });
  await ciftci.goto(SITE + "/" + m1.kod);
  await ciftci.waitForSelector("#yok:not(.gizli)");
  check(true, "kod tek başına sırayı açmaz");

  // ------------------------------------------------ ofis ve TV
  await ofis.reload();
  await ofis.waitForSelector("text=Örnek Üretici");
  check((await ofis.textContent("#tablo")).includes("Deneme Kişi"), "ofis isimleri çözer");
  await ofis.evaluate(() => {
    window.__yazdir = 0;
    window.print = () => {
      window.__yazdir += 1;
    };
  });
  await ofis.locator("#tablo tr").filter({ hasText: "Deneme Kişi" }).locator("button", { hasText: "Fiş" }).click();
  const ofisFis = await ofis.textContent("#fis-alan");
  check((await ofis.evaluate(() => window.__yazdir)) === 1 && ofisFis.includes("Deneme Kişi") && ofisFis.includes("Kod " + m3.kod) && ofisFis.includes("16 kasa"), "ofis de fiş basar: " + ofisFis);
  check((await ofis.locator("#fis-alan canvas.fis-qr").count()) === 1, "ofis fişinde QR");
  const tv = await ofisCtx.newPage();
  izle(tv, "tv");
  await tv.goto(SITE + "/yaghane/tv.html");
  await tv.waitForSelector("text=Örnek Ü.");
  const tvMetin = await tv.textContent("body");
  check(tvMetin.includes("Deneme K.") && !tvMetin.includes("Üretici"), "TV'de isimler kısaltılmış");
  check(!/390|480|kg/.test(tvMetin), "TV'de kilo yok");
  await tv.screenshot({ path: cikti + "/4-tv.png" });

  // ------------------------------------------------ çevrimdışı kuyruk
  await havCtx.setOffline(true);
  await hav.evaluate(() => window.dispatchEvent(new Event("offline")));
  const ilkBaslat = hav.locator("#sirada .satir").first().locator("button", { hasText: "Başlat" });
  await ilkBaslat.click();
  await hav.waitForFunction(() => document.getElementById("cevrim").textContent.includes("Çevrimdışı, 1 işlem bekliyor"));
  check(true, "çevrimdışı: 1 işlem bekliyor");
  check((await hav.textContent("#isleniyor")).includes("Örnek Üretici"), "işlem ekranda hemen görünür");
  const m4 = await musteriEkle("", "0533 222 33 44", "3", "200");
  check(/^\d{4}$/.test(m4.kod), "çevrimdışı yeni müşteriye yedek kod: " + m4.kod);
  check(m4.baslik.includes("bağlantı gelince"), "sıra no bağlantı gelince");
  check(m4.wa && m4.wa.includes("wa.me/905332223344"), "çevrimdışıyken de WhatsApp linki hazır (WhatsApp gönderimi bekletir)");
  await hav.waitForFunction(() => document.getElementById("cevrim").textContent.includes("2 işlem bekliyor"));
  await hav.screenshot({ path: cikti + "/5-cevrimdisi.png", fullPage: true });
  esit((await db.prepare("SELECT COUNT(*) AS n FROM sira").first()).n, 3, "çevrimdışıyken sunucuya bir şey gitmedi");
  // çakışma: havuzcu çevrimdışıyken ikinci müşteriyi iptal eder, ofis aynı anda başlatır
  const ikinci = hav.locator("#sirada .satir").filter({ hasText: "Kod " + m2.kod });
  await ikinci.locator("button[aria-label='Diğer işlemler']").click();
  await hav.click("text=İptal et");
  await hav.click("text=Evet, iptal et");
  await ofis.reload();
  await ofis.waitForSelector("#tablo tr");
  const ofisSatir = ofis.locator("#tablo tr").filter({ hasText: m2.kod });
  await ofisSatir.locator("button", { hasText: "Başlat" }).click();
  await ofis.waitForTimeout(600);
  const ofisSonrasi = await db.prepare("SELECT durum FROM sira WHERE kod = ?").bind(m2.kod).first();
  check(ofisSonrasi.durum === "isleniyor", "ofis aynı müşteriyi başlattı: " + JSON.stringify(ofisSonrasi) + " " + (await ofis.textContent("#mesajlar")));
  await havCtx.setOffline(false);
  await hav.evaluate(() => window.dispatchEvent(new Event("online")));
  await hav.waitForFunction(() => document.getElementById("cevrim").textContent.trim() === "● Bağlı", null, { timeout: 8000 });
  check(true, "bağlantı gelince kuyruk boşaldı");
  const mesajlar = await hav.textContent("#mesajlar");
  check(mesajlar.includes("Uygulanmadı") && mesajlar.includes("işleniyor"), "çakışmada sunucu kazanır, havuzcuya söylenir: " + mesajlar);
  const son = (await db.prepare("SELECT kod, durum FROM sira ORDER BY id").all()).results;
  check(son.length === 4 && son[0].durum === "isleniyor" && son[1].durum === "isleniyor", "sunucu durumu doğru: " + JSON.stringify(son));
  check(son.some((s) => s.kod === m4.kod), "yedek kod sunucuda aynen kullanıldı");
  await hav.screenshot({ path: cikti + "/6-baglandi.png", fullPage: true });

  // ------------------------------------------------ bitti ve çiftçi görünümü
  const isleniyorKart = hav.locator("#isleniyor .kart").filter({ hasText: "Örnek Üretici" });
  // Tek dokunuş Bitti; yağ kilosu sonra "Biten" listesinden yazılır.
  await isleniyorKart.locator("button", { hasText: /^Bitti$/ }).click();
  await hav.waitForSelector("#biten >> text=yağ yazılmadı");
  await ciftci.goto(ciftciLink);
  await ciftci.waitForSelector("text=Bitti · yağınız hazır");
  check((await ciftci.textContent("body")).includes("Yağ kilosu birazdan"), "yağ yazılmadan çiftçi bitti görür");
  const bitenSatir = hav.locator("#biten .satir").filter({ hasText: "Örnek Üretici" });
  await bitenSatir.locator("button", { hasText: "Yağ yaz" }).click();
  await hav.fill("#biten .yag-form input", "64");
  await hav.click("#biten .yag-form button[type=submit]");
  await hav.waitForSelector("#biten >> text=64 kg yağ");
  await ciftci.waitForFunction(() => document.body.textContent.includes("64 kg yağ"), null, { timeout: 90000 }).catch(() => null);
  if (!(await ciftci.textContent("body")).includes("64 kg yağ")) {
    await ciftci.reload();
    await ciftci.waitForSelector("text=Bitti · yağınız hazır");
  }
  check((await ciftci.textContent("body")).includes("64 kg yağ"), "sonradan yazılan yağ kilosunu çiftçi görür");
  await hav.screenshot({ path: cikti + "/7-bitti-yag.png", fullPage: true });

  // ------------------------------------------------ elle giriş
  await ciftci.goto(SITE + "/bul/?f=ornekyaghane");
  await ciftci.fill("#kod", m4.kod);
  await ciftci.fill("#ek", "44");
  await ciftci.click("button[type=submit]");
  await ciftci.waitForSelector("#icerik:not(.gizli)");
  check((await ciftci.textContent("#kod")).includes(m4.kod), "fabrika + kod + son 2 haneyle bulunur");
  await ciftci.goto(SITE + "/bul/?f=ornekyaghane");
  await ciftci.fill("#kod", m4.kod);
  await ciftci.fill("#ek", "45");
  await ciftci.click("button[type=submit]");
  await ciftci.waitForSelector("#mesaj:not(.gizli)");
  check(true, "yanlış son 2 haneyle bulunmaz");

  // ------------------------------------------------ ofiste kayıt sonrası büyük QR
  await ofis.click("#yeni-ac");
  await ofis.fill("#y-kasa", "3");
  await ofis.fill("#y-kg", "150");
  await ofis.click("#yeni-form button[type=submit]");
  await ofis.waitForSelector("#qr-panel:not(.gizli) canvas");
  const qrKod = await ofis.textContent("#qr-kod");
  check(/^Kod \d{4}/.test(qrKod), "müşteri eklenince ofis ekranında büyük QR ve kod: " + qrKod);
  await ofis.screenshot({ path: cikti + "/8-ofis-qr.png", fullPage: true });
  await ofis.click("#qr-kapat");
  await ofis.waitForSelector("#qr-panel.gizli", { state: "attached" });
  check(true, "QR paneli kapanır");

  // ------------------------------------------------ çıkış izi bırakmaz
  await hav.click("#cikis");
  await hav.waitForURL("**/yaghane/");
  const kalan = await hav.evaluate(() => Object.keys(localStorage).filter((k) => k.startsWith("zs_")));
  check(kalan.length === 0, "çıkışta anahtar ve telefonlar cihazdan silinir: " + kalan);
  check(cspHatalari.length === 0, "CSP ihlali ya da JS hatası yok: " + cspHatalari.join(" | "));
} catch (e) {
  bad++;
  console.log("FAIL beklenmeyen hata: " + e.message.split("\n")[0]);
  for (const ctx of tarayici.contexts()) {
    for (const p of ctx.pages()) {
      console.log("  sayfa: " + p.url());
      await p.screenshot({ path: cikti + "/hata-" + ctx.pages().indexOf(p) + "-" + tarayici.contexts().indexOf(ctx) + ".png", fullPage: true }).catch(() => null);
    }
  }
  if (cspHatalari.length) {
    console.log("  hatalar: " + cspHatalari.join(" | "));
  }
} finally {
  await tarayici.close();
  sunucu.close();
}

function esit(a, b, m) {
  check(JSON.stringify(a) === JSON.stringify(b), m + ": " + JSON.stringify(a) + " beklenen " + JSON.stringify(b));
}

console.log((bad ? "FAIL " : "PASS ") + (n - bad) + "/" + n + " Yağhane sırası tarayıcıda uçtan uca (ekranlar: " + cikti + ")");
process.exit(bad ? 1 : 0);
