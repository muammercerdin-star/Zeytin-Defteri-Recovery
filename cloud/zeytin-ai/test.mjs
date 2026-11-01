// Ara sunucu testleri (node test.mjs). Gerçek RSA imzalı belgelerle kimlik, sınır, girdi ve model cevabı denetimi.
// worker.js Cloudflare'e yapıştırılır ve yalnız "export default" içerir; testler iç işlevlere geçici bir kopyadan ulaşır.
import { readFileSync, writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
const src = readFileSync(join(dirname(fileURLToPath(import.meta.url)), "worker.js"), "utf8");
const tmp = join(mkdtempSync(join(tmpdir(), "zai-")), "w.mjs");
writeFileSync(tmp, src + "\nexport { handle, verifyFirebaseToken, extractJson, cleanResult, modelText, systemPrompt, resetJwksCache };\n");
const { handle, verifyFirebaseToken, extractJson, cleanResult, modelText, systemPrompt, resetJwksCache } = await import(pathToFileURL(tmp).href);
const exportsInWorker = src.match(/^export /gm) || [];

let n = 0, bad = 0;
const check = (ok, m) => { n++; if (!ok) { bad++; console.log("FAIL " + m); } };

const { privateKey, publicKey } = await crypto.subtle.generateKey(
  { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
const other = await crypto.subtle.generateKey(
  { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
const jwk = { ...(await crypto.subtle.exportKey("jwk", publicKey)), kid: "k1", alg: "RS256", use: "sig" };
const loader = async () => ({ keys: [jwk], ttl: 3600e3 });

const b64u = (bytes) => Buffer.from(bytes).toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const NOW = 1_800_000_000_000, SEC = NOW / 1000;
async function token(claims = {}, { kid = "k1", key = privateKey, alg = "RS256" } = {}) {
  const h = b64u(new TextEncoder().encode(JSON.stringify({ alg, kid, typ: "JWT" })));
  const p = b64u(new TextEncoder().encode(JSON.stringify({
    iss: "https://securetoken.google.com/zeytin-defteri", aud: "zeytin-defteri", sub: "uid-123",
    iat: SEC - 60, exp: SEC + 3000, auth_time: SEC - 600, ...claims })));
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(h + "." + p));
  return h + "." + p + "." + b64u(new Uint8Array(sig));
}

// ---- kimlik
resetJwksCache();
check(await verifyFirebaseToken(await token(), "zeytin-defteri", NOW, loader) === "uid-123", "geçerli belge");
check(await verifyFirebaseToken(await token({ exp: SEC - 1 }), "zeytin-defteri", NOW, loader) === null, "süresi geçmiş");
check(await verifyFirebaseToken(await token({ aud: "baska-proje" }), "zeytin-defteri", NOW, loader) === null, "başka proje (aud)");
check(await verifyFirebaseToken(await token({ iss: "https://evil" }), "zeytin-defteri", NOW, loader) === null, "yanlış iss");
check(await verifyFirebaseToken(await token({}, { key: other.privateKey }), "zeytin-defteri", NOW, loader) === null, "sahte imza");
check(await verifyFirebaseToken(await token({}, { kid: "yok" }), "zeytin-defteri", NOW, loader) === null, "bilinmeyen anahtar");
check(await verifyFirebaseToken(await token({ sub: "" }), "zeytin-defteri", NOW, loader) === null, "boş kullanıcı");
const t = await token(); const parts = t.split(".");
const noneTok = b64u(new TextEncoder().encode(JSON.stringify({ alg: "none", kid: "k1" }))) + "." + parts[1] + ".";
check(await verifyFirebaseToken(noneTok, "zeytin-defteri", NOW, loader) === null, "alg none reddedilir");
check(await verifyFirebaseToken("", "zeytin-defteri", NOW, loader) === null, "boş belge");

// ---- uç nokta
function env({ reply, limitOk = true, calls } = {}) {
  return {
    fetchJwks: loader,
    RATE: { limit: async ({ key }) => { if (calls) calls.rate.push(key); return { success: limitOk }; } },
    AI: { run: async (model, input) => {
      if (calls) calls.ai.push({ model, input });
      return typeof reply === "function" ? reply(input) : reply;
    } },
  };
}
const req = async (body, tok, path = "/parse") => new Request("https://zeytin-ai.example.workers.dev" + path, {
  method: "POST", headers: { "Authorization": tok ? "Bearer " + tok : "", "Content-Type": "application/json" }, body: JSON.stringify(body) });
const call = async (r, e) => { const res = await handle(r, e, NOW); return { status: res.status, body: await res.json() }; };

resetJwksCache();
const good = await token();
const calls = { rate: [], ai: [] };
let r = await call(await req({ text: "Dün Kuyu dibinde damlamayı açtık 4 saat", gardens: ["Girelli", "Kuyu dibi"] }, good),
  env({ calls, reply: { choices: [{ message: { content: "```json\n{\"islem\":\"sulama\",\"bahce\":\"Kuyu dibi\",\"gun\":-1,\"miktar\":4,\"birim\":\" saat\",\"tutar\":null}\n```" } }] } }));
check(r.status === 200 && r.body.ok && r.body.result.islem === "sulama" && r.body.result.birim === "saat" && r.body.result.tutar === null, "başarılı çözüm: " + JSON.stringify(r.body));
check(calls.rate[0] === "uid-123", "sınır kullanıcı başına");
check(calls.ai[0].model === "@cf/google/gemma-4-26b-a4b-it" && calls.ai[0].input.temperature === 0, "model ve sıcaklık");
check(calls.ai[0].input.messages[0].content.includes("\"Girelli\", \"Kuyu dibi\""), "bahçe listesi talimatta");

r = await call(await req({ text: "merhaba" }, ""), env({ reply: {} }));
check(r.status === 401, "belgesiz istek reddedilir");
r = await call(await req({ text: "x" }, good), env({ limitOk: false, reply: {} }));
check(r.status === 429, "sınır aşılınca 429");
r = await call(await req({ text: "a".repeat(301) }, good), env({ reply: {} }));
check(r.status === 400, "uzun metin reddedilir");
r = await call(await req({ text: "yarın ilaçlasak mı" }, good), env({ reply: { choices: [{ message: { content: "{\"islem\":null}" } }] } }));
check(r.status === 200 && r.body.ok === false && r.body.reason === "not_understood", "kayıt değil → not_understood");
r = await call(await req({ text: "x" }, good), env({ reply: () => { throw new Error("quota"); } }));
check(r.status === 503 && r.body.reason === "model_error", "kota/model hatası → 503 (uygulama kurallarla devam eder)");
r = await call(new Request("https://x.dev/", { method: "GET" }), env({}));
check(r.status === 200 && r.body.ok, "sağlık kontrolü");
r = await call(await req({ text: "x" }, good, "/baska"), env({}));
check(r.status === 404, "bilinmeyen yol");

// düşünme alanı ve teşhis
r = await call(await req({ text: "Dün Girelli'ye traktörle iki sefer zeytin götürdük, 800 tuttu", gardens: ["Girelli Zeytinliği"] }, good),
  env({ reply: { choices: [{ finish_reason: "stop", message: { content: null, reasoning_content: "Kullanıcı nakliye diyor. {\"islem\":\"nakliye\",\"bahce\":\"Girelli Zeytinliği\",\"gun\":-1,\"miktar\":2,\"birim\":\"sefer\",\"tutar\":800}" } }] } }));
check(r.body.ok && r.body.result.islem === "nakliye" && r.body.result.tutar === 800, "içerik boş, düşünme alanındaki JSON okunur: " + JSON.stringify(r.body));
r = await call(await req({ text: "x y z" }, good), env({ reply: { choices: [{ finish_reason: "length", message: { content: "Hmm, bu cümlede" } }] } }));
check(!r.body.ok && r.body.diag === "len=15,finish=length,json=yok" && !JSON.stringify(r.body).includes("Hmm"), "teşhis içerik içermez: " + JSON.stringify(r.body));
check(calls.ai[0].input.max_completion_tokens >= 500 && calls.ai[0].input.chat_template_kwargs.enable_thinking === false, "geniş pay, düşünme kapalı");

// ---- yardımcılar
check(extractJson("düşünüyorum... {\"a\":{\"b\":1}} son") .a.b === 1, "iç içe JSON");
check(extractJson("{bozuk} {\"islem\":\"budama\"}").islem === "budama", "bozuktan sonra geçerli JSON");
check(cleanResult({ islem: "uçurtma" }) === null, "bilinmeyen işlem");
check(cleanResult({ islem: "iscilik", miktar: "kisi", tutar: 300 }).miktar === null, "sayı alanına söz (denemedeki hata) → null");
check(cleanResult({ islem: "sulama", gun: 5 }).gun === 0, "geçersiz gün → 0");
check(modelText({ response: "{\"islem\":\"mazot\"}" }).includes("mazot"), "eski cevap biçimi");
check(!systemPrompt([]).includes("undefined"), "bahçesiz talimat");

check(exportsInWorker.length === 1 && /^export default \{/m.test(src), "worker.js yalnız tek giriş noktası (export default)");
console.log((bad === 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " zeytin-ai worker");
if (bad) process.exit(1);
