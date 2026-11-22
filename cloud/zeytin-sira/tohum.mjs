#!/usr/bin/env node
// Pilot fabrika ve iki kullanıcı (ofis + havuzcu) için SQL üretir. Canlı veritabanına KULLANICI uygular.
// Şifreler ekrana yazılmadan sorulur; komut satırı argümanına yazılmaz (kabuk geçmişinde kalmasın),
// hiçbir dosyaya yazılmaz. Çıktıda yalnız PBKDF2 özetleri vardır.
//
//   node tohum.mjs --fabrika "[FABRİKA ADI]" --kisa ornekyaghane --ofis ofis1 --havuzcu havuz1 > tohum.sql
//   npx wrangler@4 d1 execute zeytin-sira --remote --file tohum.sql
//   (sonra tohum.sql dosyasını sil)
import { PBKDF2_VARSAYILAN, sifreOzeti } from "./src/guvenlik.mjs";

function sqlMetin(text) {
  return "'" + String(text).replaceAll("'", "''") + "'";
}

export function argumanlar(argv) {
  const out = { kapasite: 1200, tekrar: PBKDF2_VARSAYILAN };
  for (let i = 0; i < argv.length; i += 2) {
    const ad = argv[i];
    const deger = argv[i + 1];
    if (ad === "--fabrika") {
      out.fabrika = deger;
    } else if (ad === "--kisa") {
      out.kisa = deger;
    } else if (ad === "--ofis") {
      out.ofis = deger;
    } else if (ad === "--havuzcu") {
      out.havuzcu = deger;
    } else if (ad === "--kapasite") {
      out.kapasite = Number(deger);
    } else if (ad === "--tekrar") {
      out.tekrar = Number(deger);
    } else {
      throw new Error("Bilinmeyen seçenek: " + ad);
    }
  }
  return out;
}

export function denetle(a) {
  const hatalar = [];
  if (!a.fabrika || a.fabrika.length > 80) {
    hatalar.push("--fabrika gerekli (en çok 80 harf)");
  }
  if (!/^[a-z0-9]{2,32}$/.test(a.kisa || "")) {
    hatalar.push("--kisa yalnız küçük harf ve rakam (2-32), örn. ornekyaghane");
  }
  for (const rol of ["ofis", "havuzcu"]) {
    if (!/^[a-z0-9._-]{3,40}$/.test(a[rol] || "")) {
      hatalar.push("--" + rol + " kullanıcı adı: küçük harf, rakam, . _ - (3-40)");
    }
  }
  if (a.ofis && a.ofis === a.havuzcu) {
    hatalar.push("ofis ve havuzcu kullanıcı adları farklı olmalı");
  }
  if (!Number.isInteger(a.kapasite) || a.kapasite < 100 || a.kapasite > 20000) {
    hatalar.push("--kapasite 100-20000");
  }
  return hatalar;
}

export function sifreYeterli(sifre) {
  return typeof sifre === "string" && sifre.length >= 10 && sifre.length <= 200;
}

/** Tohum SQL'i (testler de bunu kullanır). */
export async function tohumSql(a, sifreler, simdi) {
  const ofisOzet = await sifreOzeti(sifreler.ofis, a.tekrar);
  const havuzOzet = await sifreOzeti(sifreler.havuzcu, a.tekrar);
  const satirlar = [
    "INSERT INTO yaghane (ad, kisa_ad, kapasite_kg_saat, gun_baslangic, olusturma) VALUES (" +
      [sqlMetin(a.fabrika), sqlMetin(a.kisa), a.kapasite, "'05:00'", simdi].join(", ") +
      ");",
    "INSERT INTO kullanici (yaghane_id, kullanici_adi, sifre_ozeti, rol) VALUES ((SELECT id FROM yaghane WHERE kisa_ad = " +
      sqlMetin(a.kisa) + "), " + sqlMetin(a.ofis) + ", " + sqlMetin(ofisOzet) + ", 'ofis');",
    "INSERT INTO kullanici (yaghane_id, kullanici_adi, sifre_ozeti, rol) VALUES ((SELECT id FROM yaghane WHERE kisa_ad = " +
      sqlMetin(a.kisa) + "), " + sqlMetin(a.havuzcu) + ", " + sqlMetin(havuzOzet) + ", 'havuzcu');",
  ];
  return satirlar.join("\n") + "\n";
}

async function gizliSor(soru) {
  process.stderr.write(soru);
  const stdin = process.stdin;
  if (!stdin.isTTY) {
    // Boru ile verilirse ilk satır okunur (örn. bir parola yöneticisinden).
    const satir = await new Promise((resolve) => {
      let tampon = "";
      const oku = (parca) => {
        tampon += parca;
        const at = tampon.indexOf("\n");
        if (at >= 0) {
          stdin.off("data", oku);
          stdin.pause();
          resolve(tampon.slice(0, at));
        }
      };
      stdin.setEncoding("utf8");
      stdin.on("data", oku);
      stdin.resume();
    });
    process.stderr.write("\n");
    return satir.replace(/\r$/, "");
  }
  return new Promise((resolve) => {
    let sifre = "";
    stdin.setRawMode(true);
    stdin.setEncoding("utf8");
    stdin.resume();
    const oku = (karakter) => {
      if (karakter === "\r" || karakter === "\n") {
        stdin.setRawMode(false);
        stdin.pause();
        stdin.off("data", oku);
        process.stderr.write("\n");
        resolve(sifre);
        return;
      }
      if (karakter === "\u0003") {
        process.exit(1);
      }
      if (karakter === "\u007f") {
        sifre = sifre.slice(0, -1);
        return;
      }
      sifre += karakter;
    };
    stdin.on("data", oku);
  });
}

async function ana() {
  const a = argumanlar(process.argv.slice(2));
  const hatalar = denetle(a);
  if (hatalar.length) {
    process.stderr.write("Hata:\n- " + hatalar.join("\n- ") + "\n");
    process.exit(2);
  }
  const sifreler = {};
  for (const rol of ["ofis", "havuzcu"]) {
    const s1 = await gizliSor(rol + " (" + a[rol] + ") şifresi (en az 10 karakter): ");
    const s2 = await gizliSor("Tekrar: ");
    if (s1 !== s2 || !sifreYeterli(s1)) {
      process.stderr.write("Şifreler aynı değil ya da 10 karakterden kısa.\n");
      process.exit(2);
    }
    sifreler[rol] = s1;
  }
  process.stdout.write(await tohumSql(a, sifreler, Date.now()));
  process.stderr.write("Tamam. SQL'i D1'e uyguladıktan sonra dosyayı sil.\n");
}

if (import.meta.url === "file://" + process.argv[1]) {
  ana();
}
