// Çiftçi bildirimi service worker'ı: push gelince bildirimi gösterir, tıklanınca sıra sayfasını açar.
// İçerik uçtan uca şifrelidir; burada yalnız başlık, kısa metin ve link bulunur (kişisel veri yok).

self.addEventListener("push", (olay) => {
  let veri = { baslik: "Yağhane sırası", metin: "Sıranda güncelleme var.", url: "/" };
  try {
    if (olay.data) {
      veri = Object.assign(veri, olay.data.json());
    }
  } catch {
    // Biçim beklenmedikse varsayılan metinle gösterilir.
  }
  const secenek = {
    body: veri.metin,
    tag: "yaghane-sira",
    renotify: true,
    data: { url: veri.url },
  };
  olay.waitUntil(self.registration.showNotification(veri.baslik, secenek));
});

self.addEventListener("notificationclick", (olay) => {
  olay.notification.close();
  const url = (olay.notification.data && olay.notification.data.url) || "/";
  olay.waitUntil(
    (async () => {
      const pencereler = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
      for (const c of pencereler) {
        if (c.url === url && "focus" in c) {
          return c.focus();
        }
      }
      if (self.clients.openWindow) {
        return self.clients.openWindow(url);
      }
      return null;
    })()
  );
});
