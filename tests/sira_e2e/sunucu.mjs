// Yerel deneme sunucusu: Cloudflare'in davranışını taklit eder (statik web/sira + _headers + tek sayfa geri
// düşüşü; /api/* Worker'a). Veritabanı bellekteki SQLite (tests/sira_worker/d1.mjs). Yalnız testler içindir.
import { createServer } from "node:http";
import { existsSync, readFileSync, statSync } from "node:fs";
import { dirname, extname, join, normalize } from "node:path";
import { fileURLToPath } from "node:url";
import { D1 } from "../sira_worker/d1.mjs";

const root = join(dirname(fileURLToPath(import.meta.url)), "../..");
const web = join(root, "web/sira");
const { handle } = await import(join(root, "cloud/zeytin-sira/worker.js"));
const tohum = await import(join(root, "cloud/zeytin-sira/tohum.mjs"));

const TURLER = { ".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".css": "text/css; charset=utf-8", ".txt": "text/plain" };

function basliklar(yol) {
  const metin = readFileSync(join(web, "_headers"), "utf8");
  const out = {};
  let uygun = false;
  for (const satir of metin.split("\n")) {
    if (!satir.trim()) {
      continue;
    }
    if (!satir.startsWith(" ")) {
      const desen = satir.trim().replace("*", "");
      uygun = yol.startsWith(desen);
      continue;
    }
    if (uygun) {
      const at = satir.indexOf(":");
      out[satir.slice(0, at).trim()] = satir.slice(at + 1).trim();
    }
  }
  return out;
}

export async function baslat(port = 0) {
  const db = new D1();
  db.exec(readFileSync(join(root, "cloud/zeytin-sira/schema.sql"), "utf8"));
  const a = tohum.argumanlar(["--fabrika", "[FABRİKA ADI]", "--kisa", "ornekyaghane", "--ofis", "ofis1", "--havuzcu", "havuz1", "--tekrar", "2000"]);
  db.exec(await tohum.tohumSql(a, { ofis: "ofis-sifresi-1", havuzcu: "havuz-sifresi-1" }, Date.now()));
  const env = { DB: db, GIZLI_TUZ: "yerel-deneme-tuzu-0123456789abcdefghijkl", SITE_ORIGIN: null };
  const sunucu = createServer(async (req, res) => {
    const url = new URL(req.url, "http://" + req.headers.host);
    if (url.pathname.startsWith("/api/")) {
      const parcalar = [];
      for await (const p of req) {
        parcalar.push(p);
      }
      const govde = parcalar.length ? Buffer.concat(parcalar) : undefined;
      const headers = { ...req.headers, "cf-connecting-ip": "127.0.0.1" };
      const istek = new Request(url, { method: req.method, headers, body: req.method === "GET" ? undefined : govde });
      const cevap = await handle(istek, env, null, Date.now());
      const h = {};
      cevap.headers.forEach((v, k) => {
        h[k] = v;
      });
      res.writeHead(cevap.status, h);
      res.end(Buffer.from(await cevap.arrayBuffer()));
      return;
    }
    let yol = normalize(decodeURIComponent(url.pathname)).replace(/^(\.\.[/\\])+/, "");
    let dosya = join(web, yol);
    if (existsSync(dosya) && statSync(dosya).isDirectory()) {
      if (!yol.endsWith("/")) {
        res.writeHead(307, { Location: yol + "/" });
        res.end();
        return;
      }
      dosya = join(dosya, "index.html");
    }
    if (!existsSync(dosya) || yol.includes("_headers")) {
      dosya = join(web, "index.html");
      yol = "/";
    }
    res.writeHead(200, { "Content-Type": TURLER[extname(dosya)] || "application/octet-stream", ...basliklar(url.pathname) });
    res.end(readFileSync(dosya));
  });
  await new Promise((r) => sunucu.listen(port, "127.0.0.1", r));
  return { sunucu, db, port: sunucu.address().port };
}

if (import.meta.url === "file://" + process.argv[1]) {
  const s = await baslat(Number(process.argv[2] || 8787));
  process.stdout.write("http://127.0.0.1:" + s.port + "\n");
}
