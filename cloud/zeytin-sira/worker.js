// Yağhane sırası — Cloudflare Worker (sira.zeytindefteri.tr).
// Sayfalar statik dosyadır (web/sira, Worker çalıştırmadan sunulur); yalnız /api/* bu koda gelir.
// Sunucu okunabilir kişisel veri görmez: ad fabrika anahtarıyla tarayıcıda şifrelenir, telefon hiç gelmez.
import { apiIstegi, json } from "./src/api.mjs";
import { temizle } from "./src/temizlik.mjs";

// Android App Links: ofis QR'ı (sira.zeytindefteri.tr/<token>) uygulamayı açsın diye uygulamanın imza parmak
// izleri. Worker'dan açıkça sunulur (doğru application/json + SPA yönlendirmesine kapılmaz). Play App Signing'e
// geçerken Play Console'daki parmak izi de buraya eklenir.
const ASSETLINKS = JSON.stringify([
  {
    relation: ["delegate_permission/common.handle_all_urls"],
    target: {
      namespace: "android_app",
      package_name: "com.zeytindefteri.app",
      sha256_cert_fingerprints: [
        "FE:7C:1F:2E:5F:E0:56:0F:BE:7B:88:9C:ED:60:0A:A9:CE:42:E0:1A:A6:40:12:F4:CC:72:46:33:B6:1F:DD:54",
        "90:E1:A2:67:B3:02:B6:60:E8:FB:91:E1:37:1C:58:BF:87:F1:83:A0:FF:89:C1:8C:3F:68:CA:36:E2:D9:AA:F8",
      ],
    },
  },
]);

export async function handle(request, env, ctx, simdi) {
  const url = new URL(request.url);
  if (url.pathname.startsWith("/api/")) {
    return apiIstegi(request, env, ctx, simdi);
  }
  if (url.pathname === "/.well-known/assetlinks.json") {
    return new Response(ASSETLINKS, {
      headers: { "Content-Type": "application/json", "Cache-Control": "public, max-age=3600" },
    });
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
