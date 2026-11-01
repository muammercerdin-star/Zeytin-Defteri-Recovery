/**
 * Zeytin Defteri – "zor cümle" ara sunucusu (Cloudflare Worker, tek dosya).
 *
 * Ne yapar: Uygulamanın kurallarla anlayamadığı TEK bir bahçe işi cümlesini Gemma 4 (Cloudflare Workers AI)
 * ile JSON'a çevirir. Kayıt yapmaz; sonucu uygulama denetler, kullanıcıya okur, "evet" denince kaydeder.
 *
 * Güvenlik:
 * - Yalnız Firebase ile giriş yapmış Zeytin Defteri kullanıcıları (Firebase ID token, RS256) kabul edilir.
 * - API anahtarı yok: model, Worker'a bağlı "AI" bağlamasıyla çağrılır; uygulamada hiçbir sır yoktur.
 * - Kullanıcı başına dakikada en fazla 6 istek (RATE bağlaması varsa).
 * - Cümle ve cevap hiçbir yere yazılmaz/loglanmaz. Cloudflare Workers AI içerikle model eğitmez.
 * - Ücretsiz kota (günde 10.000 nöron) dolarsa model hata verir; uygulama kurallarla devam eder.
 *
 * Kurulum: cloud/zeytin-ai/KURULUM.md
 */

const MODEL = "@cf/google/gemma-4-26b-a4b-it";
const DEFAULT_FIREBASE_PROJECT = "zeytin-defteri";
const JWKS_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
const MAX_TEXT = 300;
const MAX_GARDENS = 20;
const MAX_GARDEN_NAME = 40;

const OPS = ["sulama", "gubre", "ilaclama", "budama", "tuzak", "toprak_isleme", "iscilik", "mazot", "nakliye"];
const UNITS = ["saat", "kg", "g", "L", "ml", "agac", "adet", "dekar", "kisi", "sefer", "km", "ton"];

export default {
  async fetch(request, env) {
    try {
      return await handle(request, env);
    } catch (e) {
      // İçerik loglanmaz; yalnız hata türü.
      console.log("hata", e && e.name);
      return json({ ok: false, reason: "internal" }, 500);
    }
  },
};

export async function handle(request, env, now = Date.now()) {
  const url = new URL(request.url);
  if (request.method === "GET" && url.pathname === "/") return json({ ok: true, service: "zeytin-ai" });
  if (request.method !== "POST" || url.pathname !== "/parse") return json({ ok: false, reason: "not_found" }, 404);

  // ---- kimlik
  const auth = request.headers.get("Authorization") || "";
  const token = auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
  const project = (env.FIREBASE_PROJECT_ID || DEFAULT_FIREBASE_PROJECT).trim();
  const uid = await verifyFirebaseToken(token, project, now, env.fetchJwks || fetchJwks);
  if (!uid) return json({ ok: false, reason: "unauthenticated" }, 401);

  // ---- kullanıcı başına sınır
  if (env.RATE && typeof env.RATE.limit === "function") {
    const { success } = await env.RATE.limit({ key: uid });
    if (!success) return json({ ok: false, reason: "rate_limited" }, 429);
  }

  // ---- girdi
  const len = Number(request.headers.get("Content-Length") || "0");
  if (len > 4096) return json({ ok: false, reason: "too_large" }, 413);
  let body;
  try { body = await request.json(); } catch { return json({ ok: false, reason: "bad_json" }, 400); }
  const text = typeof body.text === "string" ? body.text.replace(/\s+/g, " ").trim() : "";
  if (!text || text.length > MAX_TEXT) return json({ ok: false, reason: "bad_text" }, 400);
  const gardens = Array.isArray(body.gardens)
    ? body.gardens.filter((g) => typeof g === "string").map((g) => g.trim()).filter((g) => g && g.length <= MAX_GARDEN_NAME).slice(0, MAX_GARDENS)
    : [];

  // ---- model
  if (!env.AI || typeof env.AI.run !== "function") return json({ ok: false, reason: "no_model" }, 503);
  let out;
  try {
    out = await env.AI.run(MODEL, {
      messages: [
        { role: "system", content: systemPrompt(gardens) },
        { role: "user", content: text },
      ],
      temperature: 0,
      max_completion_tokens: 160,
    });
  } catch (e) {
    return json({ ok: false, reason: "model_error" }, 503);
  }
  const content = modelText(out);
  const result = cleanResult(extractJson(content));
  if (!result) return json({ ok: false, reason: "not_understood" }, 200);
  return json({ ok: true, result }, 200);
}

/** Model talimatı: telefondaki denemede kullandığımızın, kullanıcının gerçek bahçeleriyle doldurulmuş hâli. */
export function systemPrompt(gardens) {
  const list = gardens.length ? gardens.map((g) => `"${g.replace(/"/g, "")}"`).join(", ") : "(bahçe listesi yok)";
  return [
    "Sen bir zeytin bahçesi defterinin kayıt yardımcısısın. Kullanıcının Türkçe cümlesinden yapılmış bir bahçe işini çıkar.",
    "SADECE tek satır JSON yaz, açıklama yazma.",
    "",
    "Alanlar:",
    "islem: " + OPS.join(", ") + " (damlama, su salmak, motorla sulamak = sulama; ilaç vurmak, kükürt, bakır = ilaclama)",
    "bahce: şu listeden biri: " + list + "; cümlede bahçe adı GEÇMİYORSA null",
    "gun: bugün 0, dün -1, evvelsi gün -2; söylenmediyse 0",
    "miktar: sayı ya da null",
    "birim: " + UNITS.join(", ") + " ya da null",
    "tutar: lira olarak sayı ya da null (kişi başı söylenirse kişi sayısıyla çarp)",
    "",
    "Cümle yapılmış bir bahçe işi değilse (soru, sohbet, plan) şunu yaz: {\"islem\":null}",
    "Sayı uydurma. Cümlede olmayan bilgiyi null bırak. Bahçe adını tahmin etme.",
    "",
    "Örnek: \"Bugün Girelli'ye 18 kilo gübre attık 900 lira\"",
    "{\"islem\":\"gubre\",\"bahce\":\"Girelli\",\"gun\":0,\"miktar\":18,\"birim\":\"kg\",\"tutar\":900}",
    "Örnek: \"bahçeye ilaç vurduk 2 litre\"",
    "{\"islem\":\"ilaclama\",\"bahce\":null,\"gun\":0,\"miktar\":2,\"birim\":\"L\",\"tutar\":null}",
    "Örnek: \"yarın ilaçlama yapsak mı\"",
    "{\"islem\":null}",
  ].join("\n");
}

/** Workers AI cevabı: OpenAI biçimi (choices) ya da eski biçim (response). */
export function modelText(out) {
  if (!out) return "";
  if (typeof out === "string") return out;
  if (typeof out.response === "string") return out.response;
  if (out.response && typeof out.response === "object") return JSON.stringify(out.response);
  const c = out.choices && out.choices[0];
  if (c && c.message && typeof c.message.content === "string") return c.message.content;
  return "";
}

/** Metindeki ilk {...} JSON nesnesi (```json blokları ve düşünme metni atlanır). */
export function extractJson(text) {
  if (!text) return null;
  const s = String(text);
  for (let start = s.indexOf("{"); start >= 0; start = s.indexOf("{", start + 1)) {
    let depth = 0, inStr = false, esc = false;
    for (let i = start; i < s.length; i++) {
      const ch = s[i];
      if (inStr) { if (esc) esc = false; else if (ch === "\\") esc = true; else if (ch === '"') inStr = false; continue; }
      if (ch === '"') inStr = true;
      else if (ch === "{") depth++;
      else if (ch === "}") { depth--; if (depth === 0) { try { return JSON.parse(s.slice(start, i + 1)); } catch { break; } } }
    }
  }
  return null;
}

/** Yalnız bilinen alanlar, doğru türlerle. Asıl denetim uygulamada (cümleyle karşılaştırma) yapılır. */
export function cleanResult(r) {
  if (!r || typeof r !== "object") return null;
  const islem = typeof r.islem === "string" ? r.islem.trim().toLowerCase() : null;
  if (!islem || !OPS.includes(islem)) return null;
  const num = (v) => {
    const n = typeof v === "number" ? v : typeof v === "string" && /^\s*\d+([.,]\d+)?\s*$/.test(v) ? Number(v.replace(",", ".")) : NaN;
    return Number.isFinite(n) && n > 0 && n < 1e9 ? n : null;
  };
  const birimRaw = typeof r.birim === "string" ? r.birim.trim() : "";
  const birim = UNITS.find((u) => u.toLowerCase() === birimRaw.toLowerCase()) || null;
  const gun = [0, -1, -2].includes(Number(r.gun)) ? Number(r.gun) : 0;
  const bahce = typeof r.bahce === "string" && r.bahce.trim() ? r.bahce.trim().slice(0, MAX_GARDEN_NAME) : null;
  return { islem, bahce, gun, miktar: num(r.miktar), birim, tutar: num(r.tutar) };
}

// ===================================================================== Firebase ID token (RS256)

let jwksCache = { keys: null, until: 0 };

async function fetchJwks() {
  const res = await fetch(JWKS_URL, { cf: { cacheTtl: 3600 } });
  if (!res.ok) throw new Error("jwks");
  const maxAge = /max-age=(\d+)/.exec(res.headers.get("Cache-Control") || "");
  const body = await res.json();
  return { keys: body.keys || [], ttl: maxAge ? Number(maxAge[1]) * 1000 : 3600 * 1000 };
}

function b64urlBytes(s) {
  const pad = s.length % 4 ? "=".repeat(4 - (s.length % 4)) : "";
  const bin = atob(s.replace(/-/g, "+").replace(/_/g, "/") + pad);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

/** Geçerliyse kullanıcı kimliği (uid), değilse null. */
export async function verifyFirebaseToken(token, project, now, jwksLoader) {
  if (!token || token.length > 4096) return null;
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  let header, payload;
  try {
    header = JSON.parse(new TextDecoder().decode(b64urlBytes(parts[0])));
    payload = JSON.parse(new TextDecoder().decode(b64urlBytes(parts[1])));
  } catch { return null; }
  if (header.alg !== "RS256" || !header.kid) return null;
  const sec = Math.floor(now / 1000);
  if (payload.aud !== project) return null;
  if (payload.iss !== "https://securetoken.google.com/" + project) return null;
  if (typeof payload.sub !== "string" || !payload.sub || payload.sub.length > 128) return null;
  if (!(payload.exp > sec) || !(payload.iat <= sec + 300) || (payload.auth_time && payload.auth_time > sec + 300)) return null;

  if (!jwksCache.keys || jwksCache.until < now) {
    try {
      const { keys, ttl } = await jwksLoader();
      jwksCache = { keys, until: now + ttl };
    } catch { return null; }
  }
  const jwk = jwksCache.keys.find((k) => k.kid === header.kid);
  if (!jwk) return null;
  try {
    const key = await crypto.subtle.importKey("jwk", { kty: jwk.kty, n: jwk.n, e: jwk.e, alg: "RS256", ext: true },
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
    const ok = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, b64urlBytes(parts[2]),
      new TextEncoder().encode(parts[0] + "." + parts[1]));
    return ok ? payload.sub : null;
  } catch { return null; }
}

/** Testler için önbelleği sıfırlar. */
export function resetJwksCache() { jwksCache = { keys: null, until: 0 }; }

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}
