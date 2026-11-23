// Havuzcu ekranı internet yokken yeniden açılabilsin diye yalnız sayfa dosyaları önbellekte tutulur.
// API (/api/*) hiçbir zaman önbelleğe alınmaz. Sürüm değişince eski önbellek silinir.
const SURUM = "zs-4";
const DOSYALAR = [
  "/stil.css",
  "/yaghane/",
  "/yaghane/havuzcu.html",
  "/yaghane/js/havuzcu.js",
  "/yaghane/js/giris.js",
  "/yaghane/js/ortak.js",
  "/yaghane/js/anahtar.js",
  "/yaghane/js/kuyruk.js",
  "/yaghane/js/musteri.js",
  "/yaghane/js/telefon.js",
  "/yaghane/js/fis.js",
  "/yaghane/js/vendor/qrcode.js",
];

self.addEventListener("install", (olay) => {
  olay.waitUntil(caches.open(SURUM).then((c) => c.addAll(DOSYALAR)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (olay) => {
  const temizle = caches.keys().then((adlar) => Promise.all(adlar.filter((a) => a !== SURUM).map((a) => caches.delete(a))));
  olay.waitUntil(temizle.then(() => self.clients.claim()));
});

// Önce ağ (güncel sayfa), ağ yoksa önbellek.
self.addEventListener("fetch", (olay) => {
  const url = new URL(olay.request.url);
  if (olay.request.method !== "GET" || url.origin !== self.location.origin || url.pathname.startsWith("/api/")) {
    return;
  }
  olay.respondWith(
    fetch(olay.request)
      .then((cevap) => {
        if (cevap.ok && DOSYALAR.includes(url.pathname)) {
          const kopya = cevap.clone();
          caches.open(SURUM).then((c) => c.put(olay.request, kopya));
        }
        return cevap;
      })
      .catch(() => caches.match(olay.request).then((c) => c || Response.error()))
  );
});
