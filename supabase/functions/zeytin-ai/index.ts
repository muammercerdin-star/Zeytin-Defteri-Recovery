// Zeytin Defteri – internet yardımcısı aktarıcısı (Supabase Edge Function).
// Bazı Türk mobil ağlarında *.workers.dev adreslerine bağlanılamıyor; uygulama isteği buraya (supabase.co) gönderir,
// bu fonksiyon Cloudflare'deki zeytin-ai sunucusuna iletir. Anahtar/şifre yok; Firebase kimliğini Cloudflare denetler.
// Supabase panelinde bu fonksiyon için "Verify JWT" KAPALI olmalı (kimlik Firebase belgesidir, Supabase belgesi değil).
const TARGET = "https://zeytin-ai.muammercerdin.workers.dev/parse";
const H = { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" };

Deno.serve(async (req) => {
  if (req.method === "GET") return new Response(JSON.stringify({ ok: true, service: "zeytin-ai-proxy" }), { headers: H });
  if (req.method !== "POST") return new Response(JSON.stringify({ ok: false, reason: "not_found" }), { status: 404, headers: H });
  const body = await req.text();
  if (body.length > 4096) return new Response(JSON.stringify({ ok: false, reason: "too_large" }), { status: 413, headers: H });
  try {
    const r = await fetch(TARGET, {
      method: "POST",
      headers: { "content-type": "application/json", "authorization": req.headers.get("authorization") ?? "" },
      body,
      signal: AbortSignal.timeout(18000),
    });
    return new Response(await r.text(), { status: r.status, headers: H });
  } catch (_e) {
    return new Response(JSON.stringify({ ok: false, reason: "upstream" }), { status: 502, headers: H });
  }
});
