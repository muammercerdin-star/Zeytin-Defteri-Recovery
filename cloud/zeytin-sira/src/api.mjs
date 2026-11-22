// Yağhane sırası API'si. Kişisel veri: sunucu adı yalnız şifreli görür, telefonu hiç görmez.
// Hata cevapları sebebi ayrıntılandırmaz (bilinmeyen token ve yanlış kod aynı 404'ü alır).
import {
  BUL_PENCERE_MS,
  BUL_SINIRI,
  GIRIS_HATA_SINIRI,
  GIRIS_PENCERE_MS,
  PBKDF2_VARSAYILAN,
  bulOzeti,
  b64u,
  denemeleriSil,
  denemeSayisi,
  denemeYaz,
  esit,
  ipOzeti,
  kodBicimi,
  oturumAc,
  oturumBul,
  oturumCerezi,
  oturumKapat,
  oturumSilCerezi,
  rastgele,
  sifreDogru,
  sifreOzeti,
  tokenBicimi,
  yeniKod,
  yeniToken,
} from "./guvenlik.mjs";
import { gosterilenSaat, onundekiler, tahminEt } from "./tahmin.mjs";
import { GUN_MS, gecerliSaat, oncekiGun, saatDakika, utcGunu, yaghaneGunu } from "./zaman.mjs";

const GOVDE_SINIRI = 4096;
const AKTIF = "('sirada', 'isleniyor')";
const TOKEN_OMRU_MS = GUN_MS;
const YEDEK_EN_COK = 10;
const AD_DESENI = /^1\.[A-Za-z0-9_-]{24,150}$/;
const OP_DESENI = /^[A-Za-z0-9_-]{8,40}$/;
const KULLANICI_DESENI = /^[a-z0-9._-]{3,40}$/;
const IZ_DESENI = /^[A-Za-z0-9_-]{11}$/;

export class Hata extends Error {
  constructor(durum, kod, mesaj, ek) {
    super(kod);
    this.durum = durum;
    this.kod = kod;
    this.mesaj = mesaj;
    this.ek = ek || {};
  }
}

export function json(body, durum = 200, basliklar = {}) {
  const headers = {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
    "Referrer-Policy": "no-referrer",
    ...basliklar,
  };
  return new Response(JSON.stringify(body), { status: durum, headers });
}

function hataCevabi(h) {
  return json({ hata: h.kod, mesaj: h.mesaj, ...h.ek }, h.durum);
}

async function govdeOku(request) {
  const tur = (request.headers.get("Content-Type") || "").toLowerCase();
  if (!tur.startsWith("application/json")) {
    throw new Hata(415, "GECERSIZ_ISTEK", "İstek biçimi geçersiz.");
  }
  const metin = await request.text();
  if (metin.length > GOVDE_SINIRI) {
    throw new Hata(413, "GECERSIZ_ISTEK", "İstek çok büyük.");
  }
  try {
    const veri = metin ? JSON.parse(metin) : {};
    if (!veri || typeof veri !== "object" || Array.isArray(veri)) {
      throw new Error("nesne");
    }
    return veri;
  } catch {
    throw new Hata(400, "GECERSIZ_ISTEK", "İstek okunamadı.");
  }
}

/** Tarayıcı isteği başka bir siteden gelmiş olamaz (SameSite=Strict çereze ek ikinci kilit). */
function kaynakDenetle(request, env) {
  if (request.method === "GET") {
    return;
  }
  const origin = request.headers.get("Origin");
  if (!origin) {
    return;
  }
  const izinli = new Set([new URL(request.url).origin]);
  if (env.SITE_ORIGIN) {
    izinli.add(env.SITE_ORIGIN);
  }
  if (!izinli.has(origin)) {
    throw new Hata(403, "YETKI_YOK", "Bu istek kabul edilmedi.");
  }
}

function gizliTuz(env) {
  const tuz = env.GIZLI_TUZ;
  if (typeof tuz !== "string" || tuz.length < 32) {
    throw new Hata(503, "YAPILANDIRILMADI", "Sunucu ayarı eksik (GIZLI_TUZ).");
  }
  return tuz;
}

function siteOrigin(env, request) {
  if (env.SITE_ORIGIN) {
    return env.SITE_ORIGIN;
  }
  return new URL(request.url).origin;
}

function tamSayi(deger, alt, ust) {
  const n = Number(deger);
  return Number.isInteger(n) && n >= alt && n <= ust ? n : null;
}

function sayi(deger, alt, ust) {
  const n = Number(deger);
  return Number.isFinite(n) && n >= alt && n <= ust ? n : null;
}

async function istekSay(env, ctx, simdi) {
  const is = env.DB.prepare("INSERT INTO sayac (gun, n) VALUES (?, 1) ON CONFLICT(gun) DO UPDATE SET n = n + 1")
    .bind(utcGunu(simdi))
    .run()
    .catch(() => null);
  if (ctx && typeof ctx.waitUntil === "function") {
    ctx.waitUntil(is);
    return;
  }
  await is;
}

// ===================================================================== GİRİŞ

async function giris(request, env, simdi) {
  const veri = await govdeOku(request);
  const ad = String(veri.kullanici_adi || "").trim().toLowerCase();
  const sifre = String(veri.sifre || "");
  const ipKey = "i:" + (await ipOzeti(request, gizliTuz(env), simdi));
  const adKey = "k:" + ad;
  for (const anahtar of [adKey, ipKey]) {
    const d = await denemeSayisi(env.DB, anahtar, simdi, GIRIS_PENCERE_MS);
    if (d.n >= GIRIS_HATA_SINIRI) {
      const bekle = Math.max(1, Math.ceil((d.ilk + GIRIS_PENCERE_MS - simdi) / 1000));
      throw new Hata(429, "COK_DENEME", "Çok fazla hatalı deneme. Biraz bekle.", { bekle_sn: bekle });
    }
  }
  let kullanici = null;
  if (KULLANICI_DESENI.test(ad) && sifre.length >= 8 && sifre.length <= 200) {
    kullanici = await env.DB.prepare("SELECT id, sifre_ozeti, rol FROM kullanici WHERE kullanici_adi = ?").bind(ad).first();
  }
  // Kullanıcı yoksa da özet hesaplanır: cevap süresinden kullanıcı adının varlığı anlaşılmasın.
  const kayit = kullanici ? kullanici.sifre_ozeti : "pbkdf2-sha256$" + PBKDF2_VARSAYILAN + "$AAAAAAAAAAAAAAAAAAAAAA$x";
  const dogru = await sifreDogru(sifre, kayit);
  if (!kullanici || !dogru) {
    await denemeYaz(env.DB, adKey, simdi);
    await denemeYaz(env.DB, ipKey, simdi);
    throw new Hata(401, "GIRIS", "Kullanıcı adı ya da şifre yanlış.");
  }
  await denemeleriSil(env.DB, adKey);
  const deger = await oturumAc(env.DB, kullanici.id, simdi);
  const ben = await env.DB.prepare(
    "SELECT y.ad, y.kisa_ad FROM kullanici k JOIN yaghane y ON y.id = k.yaghane_id WHERE k.id = ?"
  ).bind(kullanici.id).first();
  const body = { rol: kullanici.rol, kullanici_adi: ad, yaghane: { ad: ben.ad, kisa_ad: ben.kisa_ad } };
  return json(body, 200, { "Set-Cookie": oturumCerezi(deger, simdi) });
}

async function cikis(request, env) {
  await oturumKapat(env.DB, request);
  return new Response(null, { status: 204, headers: { "Set-Cookie": oturumSilCerezi(), "Cache-Control": "no-store" } });
}

async function oturumGerekli(request, env, simdi) {
  const o = await oturumBul(env.DB, request, simdi);
  if (!o) {
    throw new Hata(401, "OTURUM_YOK", "Önce giriş yap.");
  }
  return o;
}

function ofisGerekli(o) {
  if (o.rol !== "ofis") {
    throw new Hata(403, "YETKI_YOK", "Bu ayarı yalnız ofis değiştirebilir.");
  }
}

// ===================================================================== GÜN LİSTESİ

async function gorunenSatirlar(db, o, gun) {
  const sql =
    "SELECT id, gun, sira_no, konum, kod, ad_sifreli, kasa, kg, durum, gelis, baslama, bitis, yag_kg FROM sira " +
    "WHERE yaghane_id = ? AND (gun = ? OR durum IN " + AKTIF + ")";
  const r = await db.prepare(sql).bind(o.yaghane_id, gun).all();
  return r.results || [];
}

function sirala(satirlar) {
  const sira = { isleniyor: 0, sirada: 1, bitti: 2, iptal: 3 };
  return satirlar.slice().sort((a, b) => {
    const fark = sira[a.durum] - sira[b.durum];
    if (fark !== 0) {
      return fark;
    }
    if (a.durum === "sirada") {
      return Number(a.konum) - Number(b.konum);
    }
    if (a.durum === "isleniyor") {
      return Number(a.baslama) - Number(b.baslama);
    }
    return Number(b.bitis || 0) - Number(a.bitis || 0);
  });
}

async function gunListesi(request, env, simdi) {
  const o = await oturumGerekli(request, env, simdi);
  const gun = yaghaneGunu(simdi, o.gun_baslangic);
  const satirlar = await gorunenSatirlar(env.DB, o, gun);
  const t = tahminEt(satirlar, o.kapasite_kg_saat, simdi);
  let islenenKg = 0;
  let yagKg = 0;
  for (const s of satirlar) {
    if (s.durum === "bitti" && s.gun === gun) {
      islenenKg += Number(s.kg);
      yagKg += Number(s.yag_kg || 0);
    }
  }
  const sira = sirala(satirlar).map((s) => {
    const tah = t.byId[s.id];
    let tahmini = null;
    if (tah && s.durum === "sirada") {
      tahmini = gosterilenSaat(tah.baslangic);
    }
    if (tah && s.durum === "isleniyor") {
      tahmini = gosterilenSaat(tah.bitis);
    }
    return { ...s, tahmini };
  });
  const sayac = await env.DB.prepare("SELECT n FROM sayac WHERE gun = ?").bind(utcGunu(simdi)).first();
  return json({
    gun,
    simdi,
    saat: saatDakika(simdi),
    ben: { kullanici_adi: o.kullanici_adi, rol: o.rol },
    yaghane: { ad: o.ad, kisa_ad: o.kisa_ad },
    ayar: { kapasite_kg_saat: o.kapasite_kg_saat, gun_baslangic: o.gun_baslangic, anahtar_izi: o.anahtar_izi },
    ozet: {
      bekleyen: t.bekleyen,
      bekleyen_kg: t.bekleyenKg,
      islenen_kg: islenenKg,
      yag_kg: yagKg,
      biter: t.bekleyen > 0 || satirlar.some((s) => s.durum === "isleniyor") ? gosterilenSaat(t.kuyrukSonu) : null,
    },
    istek_bugun: Number(sayac?.n || 0),
    sira,
  });
}

// ===================================================================== İŞLEMLER (çevrimdışı kuyruk: op_id ile tek sefer)

async function tekSefer(env, o, veri, simdi, is) {
  const op = veri.op_id;
  if (typeof op !== "string" || !OP_DESENI.test(op)) {
    throw new Hata(400, "GECERSIZ_ISTEK", "İşlem kimliği eksik.");
  }
  const once = await env.DB.prepare("SELECT durum, sonuc FROM islem_kaydi WHERE yaghane_id = ? AND op_id = ?")
    .bind(o.yaghane_id, op)
    .first();
  if (once) {
    return json({ ...JSON.parse(once.sonuc), tekrar: true }, Number(once.durum));
  }
  let durum = 200;
  let sonuc;
  try {
    const r = await is();
    durum = r.durum;
    sonuc = r.sonuc;
  } catch (e) {
    if (!(e instanceof Hata) || e.durum >= 500 || e.durum === 401) {
      throw e;
    }
    durum = e.durum;
    sonuc = { hata: e.kod, mesaj: e.mesaj, ...e.ek };
  }
  await env.DB.prepare("INSERT OR IGNORE INTO islem_kaydi (yaghane_id, op_id, durum, sonuc, zaman) VALUES (?, ?, ?, ?, ?)")
    .bind(o.yaghane_id, op, durum, JSON.stringify(sonuc), simdi)
    .run();
  return json(sonuc, durum);
}

async function kodKullanildi(db, yaghaneId, gun, kod) {
  const a = await db.prepare("SELECT 1 AS x FROM sira WHERE yaghane_id = ? AND gun = ? AND kod = ?").bind(yaghaneId, gun, kod).first();
  if (a) {
    return true;
  }
  const b = await db.prepare("SELECT 1 AS x FROM kod_yedegi WHERE yaghane_id = ? AND gun = ? AND kod = ?").bind(yaghaneId, gun, kod).first();
  return Boolean(b);
}

async function bosKod(db, yaghaneId, gun) {
  for (let i = 0; i < 40; i++) {
    const kod = yeniKod();
    if (!(await kodKullanildi(db, yaghaneId, gun, kod))) {
      return kod;
    }
  }
  throw new Hata(503, "KOD_YOK", "Bugün için boş kod kalmadı.");
}

async function yeniMusteri(request, env, simdi) {
  const o = await oturumGerekli(request, env, simdi);
  const veri = await govdeOku(request);
  return tekSefer(env, o, veri, simdi, async () => {
    const kasa = tamSayi(veri.kasa, 0, 500);
    const kg = sayi(veri.kg, 1, 100000);
    if (kasa === null || kg === null) {
      throw new Hata(400, "GECERSIZ_ISTEK", "Kasa ve kilo gerekli.");
    }
    let adSifreli = null;
    if (veri.ad_sifreli !== undefined && veri.ad_sifreli !== null && veri.ad_sifreli !== "") {
      if (typeof veri.ad_sifreli !== "string" || !AD_DESENI.test(veri.ad_sifreli)) {
        throw new Hata(400, "GECERSIZ_ISTEK", "İsim şifreli gelmeli.");
      }
      adSifreli = veri.ad_sifreli;
    }
    let son2 = null;
    if (veri.son2 !== undefined && veri.son2 !== null && veri.son2 !== "") {
      if (typeof veri.son2 !== "string" || !/^[0-9]{2}$/.test(veri.son2)) {
        throw new Hata(400, "GECERSIZ_ISTEK", "Telefonun son iki hanesi geçersiz.");
      }
      son2 = veri.son2;
    }
    const gun = yaghaneGunu(simdi, o.gun_baslangic);
    let kod = null;
    let token = null;
    let kodDegisti = false;
    if (veri.yedek) {
      const y = veri.yedek;
      if (!kodBicimi(y.kod) || !tokenBicimi(y.token)) {
        throw new Hata(400, "GECERSIZ_ISTEK", "Yedek kod geçersiz.");
      }
      const ayrilan = await env.DB.prepare("SELECT gun FROM kod_yedegi WHERE yaghane_id = ? AND kod = ? AND token = ?")
        .bind(o.yaghane_id, y.kod, y.token)
        .first();
      if (!ayrilan) {
        throw new Hata(409, "YEDEK_YOK", "Yedek kod bulunamadı; müşteriyi yeniden ekle.");
      }
      await env.DB.prepare("DELETE FROM kod_yedegi WHERE yaghane_id = ? AND token = ?").bind(o.yaghane_id, y.token).run();
      token = y.token;
      kod = y.kod;
      // Yedek dünkü güne ayrıldıysa ve kod bugün doluysa: yeni kod verilir, havuzcuya söylenir. Token aynı kalır.
      if (ayrilan.gun !== gun && (await kodKullanildi(env.DB, o.yaghane_id, gun, kod))) {
        kod = await bosKod(env.DB, o.yaghane_id, gun);
        kodDegisti = true;
      }
    }
    let bulTuz = null;
    let bulOz = null;
    for (let deneme = 0; deneme < 5; deneme++) {
      if (!token) {
        token = yeniToken();
      }
      if (!kod) {
        kod = await bosKod(env.DB, o.yaghane_id, gun);
      }
      if (son2) {
        bulTuz = b64u(rastgele(16));
        bulOz = await bulOzeti(gizliTuz(env), bulTuz, kod, son2);
      }
      const ust = await env.DB.prepare(
        "SELECT COALESCE(MAX(sira_no), 0) + 1 AS no, " +
          "(SELECT COALESCE(MAX(konum), 0) + 1 FROM sira WHERE yaghane_id = ? AND durum IN " + AKTIF + ") AS konum " +
          "FROM sira WHERE yaghane_id = ? AND gun = ?"
      ).bind(o.yaghane_id, o.yaghane_id, gun).first();
      try {
        const r = await env.DB.prepare(
          "INSERT INTO sira (yaghane_id, gun, sira_no, konum, kod, token, ad_sifreli, bul_tuz, bul_ozet, kasa, kg, durum, gelis) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'sirada', ?)"
        ).bind(o.yaghane_id, gun, ust.no, ust.konum, kod, token, adSifreli, bulTuz, bulOz, kasa, kg, simdi).run();
        const sonuc = {
          id: r.meta.last_row_id,
          sira_no: ust.no,
          kod,
          token,
          link: siteOrigin(env, request) + "/" + token,
          gun,
        };
        if (kodDegisti) {
          sonuc.kod_degisti = true;
        }
        return { durum: 201, sonuc };
      } catch (e) {
        if (!/UNIQUE/i.test(String(e && e.message))) {
          throw e;
        }
        // Aynı anda iki ekleme: numara, kod ya da token çakıştı; çakışan değer yenilenip yeniden denenir.
        // Yedek token korunur; yedek kod yalnız kod çakıştıysa değişir ve havuzcuya söylenir.
        const mesaj = String(e.message);
        if (mesaj.includes("sira.kod")) {
          kod = null;
          if (veri.yedek) {
            kodDegisti = true;
          }
        }
        if (mesaj.includes("sira.token") && !veri.yedek) {
          token = null;
        }
      }
    }
    throw new Hata(503, "TEKRAR_DENE", "Şu an eklenemedi, tekrar dene.");
  });
}

async function satirGetir(db, o, id) {
  return db.prepare("SELECT id, durum, konum, kg FROM sira WHERE id = ? AND yaghane_id = ?").bind(id, o.yaghane_id).first();
}

const DURUM_ADI = { sirada: "sırada", isleniyor: "işleniyor", bitti: "bitti", iptal: "iptal edildi" };

async function cakisma(db, o, id) {
  const s = await satirGetir(db, o, id);
  if (!s) {
    throw new Hata(404, "YOK", "Bu müşteri bulunamadı.");
  }
  const mesaj = "Bu müşteri şu an " + DURUM_ADI[s.durum] + "; işlem uygulanmadı.";
  throw new Hata(409, "CAKISMA", mesaj, { simdiki: { durum: s.durum } });
}

async function durumIslemi(request, env, simdi, id, islem) {
  const o = await oturumGerekli(request, env, simdi);
  const veri = await govdeOku(request);
  return tekSefer(env, o, veri, simdi, async () => {
    if (islem === "baslat") {
      const r = await env.DB.prepare(
        "UPDATE sira SET durum = 'isleniyor', baslama = ? WHERE id = ? AND yaghane_id = ? AND durum = 'sirada'"
      ).bind(simdi, id, o.yaghane_id).run();
      if (r.meta.changes !== 1) {
        await cakisma(env.DB, o, id);
      }
      return { durum: 200, sonuc: { id, durum: "isleniyor" } };
    }
    if (islem === "bitti") {
      const s = await satirGetir(env.DB, o, id);
      const yag = s ? sayi(veri.yag_kg, 0.1, Number(s.kg)) : null;
      if (s && s.durum === "isleniyor" && yag === null) {
        throw new Hata(400, "GECERSIZ_ISTEK", "Yağ kilosu 0 ile zeytin kilosu arasında olmalı.");
      }
      const r = await env.DB.prepare(
        "UPDATE sira SET durum = 'bitti', bitis = ?, yag_kg = ? WHERE id = ? AND yaghane_id = ? AND durum = 'isleniyor'"
      ).bind(simdi, yag, id, o.yaghane_id).run();
      if (r.meta.changes !== 1) {
        await cakisma(env.DB, o, id);
      }
      return { durum: 200, sonuc: { id, durum: "bitti", yag_kg: yag } };
    }
    if (islem === "iptal") {
      const r = await env.DB.prepare(
        "UPDATE sira SET durum = 'iptal', bitis = ? WHERE id = ? AND yaghane_id = ? AND durum IN " + AKTIF
      ).bind(simdi, id, o.yaghane_id).run();
      if (r.meta.changes !== 1) {
        await cakisma(env.DB, o, id);
      }
      return { durum: 200, sonuc: { id, durum: "iptal" } };
    }
    return tasi(env, o, veri, id);
  });
}

async function tasi(env, o, veri, id) {
  if (veri.yon !== "yukari" && veri.yon !== "asagi") {
    throw new Hata(400, "GECERSIZ_ISTEK", "Yön yukarı ya da aşağı olmalı.");
  }
  const s = await satirGetir(env.DB, o, id);
  if (!s || s.durum !== "sirada") {
    await cakisma(env.DB, o, id);
  }
  const yukari = veri.yon === "yukari";
  const sql = yukari
    ? "SELECT id, konum FROM sira WHERE yaghane_id = ? AND durum = 'sirada' AND konum < ? ORDER BY konum DESC LIMIT 1"
    : "SELECT id, konum FROM sira WHERE yaghane_id = ? AND durum = 'sirada' AND konum > ? ORDER BY konum ASC LIMIT 1";
  const komsu = await env.DB.prepare(sql).bind(o.yaghane_id, s.konum).first();
  if (!komsu) {
    return { durum: 200, sonuc: { id, degisti: false } };
  }
  await env.DB.batch([
    env.DB.prepare("UPDATE sira SET konum = ? WHERE id = ? AND yaghane_id = ?").bind(komsu.konum, id, o.yaghane_id),
    env.DB.prepare("UPDATE sira SET konum = ? WHERE id = ? AND yaghane_id = ?").bind(s.konum, komsu.id, o.yaghane_id),
  ]);
  return { durum: 200, sonuc: { id, degisti: true } };
}

async function kodYedegi(request, env, simdi) {
  const o = await oturumGerekli(request, env, simdi);
  const veri = await govdeOku(request);
  const adet = tamSayi(veri.adet, 1, YEDEK_EN_COK);
  if (adet === null) {
    throw new Hata(400, "GECERSIZ_ISTEK", "En çok " + YEDEK_EN_COK + " yedek kod alınır.");
  }
  const gun = yaghaneGunu(simdi, o.gun_baslangic);
  const liste = [];
  for (let i = 0; i < adet; i++) {
    const kod = await bosKod(env.DB, o.yaghane_id, gun);
    const token = yeniToken();
    await env.DB.prepare("INSERT INTO kod_yedegi (yaghane_id, gun, kod, token, ayrilma) VALUES (?, ?, ?, ?, ?)")
      .bind(o.yaghane_id, gun, kod, token, simdi)
      .run();
    liste.push({ kod, token, gun, link: siteOrigin(env, request) + "/" + token });
  }
  return json({ yedek: liste }, 201);
}

async function ayarDegistir(request, env, simdi) {
  const o = await oturumGerekli(request, env, simdi);
  ofisGerekli(o);
  const veri = await govdeOku(request);
  let kapasite = o.kapasite_kg_saat;
  let baslangic = o.gun_baslangic;
  if (veri.kapasite_kg_saat !== undefined) {
    kapasite = tamSayi(veri.kapasite_kg_saat, 100, 20000);
    if (kapasite === null) {
      throw new Hata(400, "GECERSIZ_ISTEK", "Kapasite saatte 100 ile 20.000 kg arasında olmalı.");
    }
  }
  if (veri.gun_baslangic !== undefined) {
    if (!gecerliSaat(veri.gun_baslangic)) {
      throw new Hata(400, "GECERSIZ_ISTEK", "Gün başlangıcı SS:DD biçiminde olmalı.");
    }
    baslangic = veri.gun_baslangic;
  }
  await env.DB.prepare("UPDATE yaghane SET kapasite_kg_saat = ?, gun_baslangic = ? WHERE id = ?")
    .bind(kapasite, baslangic, o.yaghane_id)
    .run();
  return json({ kapasite_kg_saat: kapasite, gun_baslangic: baslangic });
}

async function anahtarIzi(request, env, simdi) {
  const o = await oturumGerekli(request, env, simdi);
  ofisGerekli(o);
  const veri = await govdeOku(request);
  if (typeof veri.iz !== "string" || !IZ_DESENI.test(veri.iz)) {
    throw new Hata(400, "GECERSIZ_ISTEK", "Anahtar izi geçersiz.");
  }
  if (o.anahtar_izi && veri.yenile !== true) {
    throw new Hata(409, "ANAHTAR_VAR", "Fabrika anahtarı zaten var.");
  }
  await env.DB.prepare("UPDATE yaghane SET anahtar_izi = ? WHERE id = ?").bind(veri.iz, o.yaghane_id).run();
  return json({ anahtar_izi: veri.iz });
}

// ===================================================================== ÇİFTÇİ (yalnız token; başkasının hiçbir bilgisi yok)

async function tokenSatiri(db, token, simdi) {
  if (!tokenBicimi(token)) {
    return null;
  }
  const sql =
    "SELECT s.id, s.yaghane_id, s.sira_no, s.konum, s.kod, s.kasa, s.kg, s.durum, s.gelis, s.baslama, s.bitis, s.yag_kg, " +
    "y.ad AS fabrika, y.kapasite_kg_saat FROM sira s JOIN yaghane y ON y.id = s.yaghane_id WHERE s.token = ?";
  const s = await db.prepare(sql).bind(token).first();
  if (!s) {
    return null;
  }
  const bittiMi = s.durum === "bitti" || s.durum === "iptal";
  if (bittiMi && simdi - Number(s.bitis || s.gelis) > TOKEN_OMRU_MS) {
    return null;
  }
  return s;
}

async function ciftciDurumu(env, token, simdi) {
  const s = await tokenSatiri(env.DB, token, simdi);
  if (!s) {
    throw new Hata(404, "YOK", "Bu sıra bulunamadı ya da süresi doldu.");
  }
  // Tahmin için yalnız sayılar okunur: başka satırların adı, kodu ya da kilosu cevaba girmez.
  const aktif = await env.DB.prepare("SELECT id, durum, konum, kg, baslama FROM sira WHERE yaghane_id = ? AND durum IN " + AKTIF)
    .bind(s.yaghane_id)
    .all();
  const satirlar = aktif.results || [];
  const t = tahminEt(satirlar, s.kapasite_kg_saat, simdi);
  const tah = t.byId[s.id];
  const body = {
    fabrika: s.fabrika,
    kod: s.kod,
    sira_no: s.sira_no,
    durum: s.durum,
    onunde: onundekiler(satirlar, s),
    tahmini: null,
    biter: null,
    kasa: s.kasa,
    kg: s.kg,
    gelis: saatDakika(Number(s.gelis)),
    yag_kg: s.durum === "bitti" ? s.yag_kg : null,
    guncel: saatDakika(simdi),
  };
  if (tah && s.durum === "sirada") {
    body.tahmini = gosterilenSaat(tah.baslangic);
  }
  if (tah && s.durum === "isleniyor") {
    body.biter = gosterilenSaat(tah.bitis);
  }
  return json(body);
}

async function ciftciBul(request, env, simdi) {
  const veri = await govdeOku(request);
  const anahtar = "b:" + (await ipOzeti(request, gizliTuz(env), simdi));
  const d = await denemeSayisi(env.DB, anahtar, simdi, BUL_PENCERE_MS);
  if (d.n >= BUL_SINIRI) {
    const bekle = Math.max(1, Math.ceil((d.ilk + BUL_PENCERE_MS - simdi) / 1000));
    throw new Hata(429, "COK_DENEME", "Çok fazla deneme. Biraz bekle.", { bekle_sn: bekle });
  }
  await denemeYaz(env.DB, anahtar, simdi);
  const fabrika = String(veri.fabrika || "").trim().toLowerCase();
  const kod = String(veri.kod || "").trim();
  const ek = String(veri.ek || "").trim();
  const bulunamadi = new Hata(404, "YOK", "Bu bilgilerle sıra bulunamadı.");
  if (!/^[a-z0-9]{2,32}$/.test(fabrika) || !kodBicimi(kod) || !/^[0-9]{1,3}$/.test(ek)) {
    throw bulunamadi;
  }
  const y = await env.DB.prepare("SELECT id, gun_baslangic FROM yaghane WHERE kisa_ad = ?").bind(fabrika).first();
  if (!y) {
    throw bulunamadi;
  }
  const gun = yaghaneGunu(simdi, y.gun_baslangic);
  const adaylar = await env.DB.prepare(
    "SELECT token, kasa, bul_tuz, bul_ozet FROM sira WHERE yaghane_id = ? AND kod = ? AND gun IN (?, ?)"
  ).bind(y.id, kod, gun, oncekiGun(gun)).all();
  for (const a of adaylar.results || []) {
    let uyar = false;
    if (a.bul_ozet) {
      const oz = await bulOzeti(gizliTuz(env), a.bul_tuz, kod, ek);
      uyar = esit(oz, a.bul_ozet);
    } else {
      uyar = ek === String(a.kasa);
    }
    if (uyar && (await tokenSatiri(env.DB, a.token, simdi))) {
      return json({ token: a.token });
    }
  }
  throw bulunamadi;
}

// ===================================================================== YÖNLENDİRME

export async function apiIstegi(request, env, ctx, simdi) {
  const url = new URL(request.url);
  const yol = url.pathname;
  const yontem = request.method;
  try {
    if (!env.DB) {
      throw new Hata(503, "YAPILANDIRILMADI", "Veritabanı bağlı değil.");
    }
    kaynakDenetle(request, env);
    await istekSay(env, ctx, simdi);
    if (yol === "/api/saglik" && yontem === "GET") {
      return json({ ok: true });
    }
    if (yol === "/api/yaghane/giris" && yontem === "POST") {
      return await giris(request, env, simdi);
    }
    if (yol === "/api/yaghane/cikis" && yontem === "POST") {
      return await cikis(request, env);
    }
    if (yol === "/api/yaghane/gun" && yontem === "GET") {
      return await gunListesi(request, env, simdi);
    }
    if (yol === "/api/yaghane/sira" && yontem === "POST") {
      return await yeniMusteri(request, env, simdi);
    }
    if (yol === "/api/yaghane/yedek" && yontem === "POST") {
      return await kodYedegi(request, env, simdi);
    }
    if (yol === "/api/yaghane/ayar" && yontem === "PUT") {
      return await ayarDegistir(request, env, simdi);
    }
    if (yol === "/api/yaghane/anahtar" && yontem === "PUT") {
      return await anahtarIzi(request, env, simdi);
    }
    const islem = /^\/api\/yaghane\/sira\/([0-9]{1,12})\/(baslat|bitti|iptal|tasi)$/.exec(yol);
    if (islem && yontem === "POST") {
      return await durumIslemi(request, env, simdi, Number(islem[1]), islem[2]);
    }
    if (yol === "/api/sira/bul" && yontem === "POST") {
      return await ciftciBul(request, env, simdi);
    }
    const ciftci = /^\/api\/sira\/([A-Za-z0-9_-]{1,64})$/.exec(yol);
    if (ciftci && yontem === "GET") {
      return await ciftciDurumu(env, ciftci[1], simdi);
    }
    throw new Hata(404, "YOK", "Bulunamadı.");
  } catch (e) {
    if (e instanceof Hata) {
      return hataCevabi(e);
    }
    // İç hata: ayrıntı (ve olası kişisel veri) cevaba ya da loga yazılmaz.
    return json({ hata: "SUNUCU", mesaj: "Bir sorun oldu, tekrar dene." }, 500);
  }
}
