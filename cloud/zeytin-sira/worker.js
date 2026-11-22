// Yağhane sırası — Cloudflare Worker (sira.zeytindefteri.tr).
// Sayfalar statik dosyadır (web/sira, Worker çalıştırmadan sunulur); yalnız /api/* bu koda gelir.
// Sunucu okunabilir kişisel veri görmez: ad fabrika anahtarıyla tarayıcıda şifrelenir, telefon hiç gelmez.
import { apiIstegi, json } from "./src/api.mjs";
import { temizle } from "./src/temizlik.mjs";

export async function handle(request, env, ctx, simdi) {
  const url = new URL(request.url);
  if (url.pathname.startsWith("/api/")) {
    return apiIstegi(request, env, ctx, simdi);
  }
  if (env.ASSETS) {
    return env.ASSETS.fetch(request);
  }
  return json({ hata: "YOK", mesaj: "Bulunamadı." }, 404);
}

export default {
  async fetch(request, env, ctx) {
    return handle(request, env, ctx, Date.now());
  },
  async scheduled(event, env, ctx) {
    ctx.waitUntil(temizle(env.DB, Date.now()));
  },
};
