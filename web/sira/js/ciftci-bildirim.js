// Çiftçi bildirimi: "sıram gelince haber ver". Web Push aboneliği; telefon numarası/isim istenmez.
// Abonelik yalnız sıra token'ına bağlanır. Desteklemeyen tarayıcıda (örn. bazı iPhone'lar) düğme yerine
// "sayfayı açık tut" notu gösterilir. Gönderimi sunucu yapar; bu dosya yalnız izni alıp aboneliği kaydeder.

const $ = (id) => document.getElementById(id);
let kuruldu = false;
let aktifToken = null;
let acikAnahtar = null;

function destekli() {
  return "serviceWorker" in navigator && "PushManager" in window && "Notification" in window && window.isSecureContext;
}

/** base64url (VAPID açık anahtarı) → Uint8Array (applicationServerKey için). */
function u8(b64) {
  const taban = b64.replaceAll("-", "+").replaceAll("_", "/");
  const pad = "=".repeat((4 - (taban.length % 4)) % 4);
  const raw = atob(taban + pad);
  return Uint8Array.from(raw, (c) => c.charCodeAt(0));
}

async function abonelikBul() {
  const reg = await navigator.serviceWorker.getRegistration();
  if (!reg) {
    return null;
  }
  return reg.pushManager.getSubscription();
}

async function durumGoster() {
  try {
    const sub = await abonelikBul();
    if (sub) {
      $("bildirim-durum").textContent = "Açık: sıran yaklaşınca telefonuna haber geleceğiz.";
      $("bildirim-dugme").textContent = "Bildirimi kapat";
      $("bildirim-dugme").dataset.acik = "1";
    } else {
      $("bildirim-durum").textContent = "";
      $("bildirim-dugme").textContent = "Sıram gelince haber ver";
      $("bildirim-dugme").dataset.acik = "0";
    }
  } catch {
    // Durum okunamazsa düğme varsayılan metinde kalır.
  }
}

async function ac() {
  const izin = await Notification.requestPermission();
  if (izin !== "granted") {
    $("bildirim-durum").textContent = "Telefon bildirim iznini vermedi. Ayarlardan açabilir ya da sayfayı açık tutabilirsin.";
    return;
  }
  const reg = await navigator.serviceWorker.register("/sw-bildirim.js");
  await navigator.serviceWorker.ready;
  const sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: u8(acikAnahtar) });
  const j = sub.toJSON();
  const govde = { endpoint: sub.endpoint, p256dh: j.keys.p256dh, auth: j.keys.auth };
  const r = await fetch("/api/sira/" + aktifToken + "/bildirim", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(govde),
  }).catch(() => null);
  if (!r || !r.ok) {
    $("bildirim-durum").textContent = "Bildirim kurulamadı, biraz sonra tekrar dene.";
    return;
  }
  await durumGoster();
}

async function kapat() {
  try {
    const sub = await abonelikBul();
    if (sub) {
      await fetch("/api/sira/" + aktifToken + "/bildirim", {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ endpoint: sub.endpoint }),
      }).catch(() => {});
      await sub.unsubscribe().catch(() => {});
    }
  } catch {
    // Kapatma hatası görünümü bozmaz; durum yeniden okunur.
  }
  await durumGoster();
}

function kur() {
  if (kuruldu) {
    return;
  }
  kuruldu = true;
  $("bildirim-dugme").addEventListener("click", async () => {
    const dugme = $("bildirim-dugme");
    dugme.disabled = true;
    try {
      if (dugme.dataset.acik === "1") {
        await kapat();
      } else {
        await ac();
      }
    } finally {
      dugme.disabled = false;
    }
  });
  durumGoster();
}

/** Her yenilemede çağrılır: sırada/işleniyorken bloğu gösterir, bitti/iptalde gizler. */
export function bildirimKur(token, bildirim, durum) {
  const kutu = $("bildirim-kutu");
  if (!kutu) {
    return;
  }
  const gosterilsin = durum === "sirada" || durum === "isleniyor";
  if (!gosterilsin || !bildirim || !bildirim.acik) {
    kutu.classList.add("gizli");
    return;
  }
  aktifToken = token;
  acikAnahtar = bildirim.anahtar;
  kutu.classList.remove("gizli");
  if (!destekli()) {
    $("bildirim-dugme").classList.add("gizli");
    $("bildirim-durum").textContent = "";
    $("bildirim-not").textContent =
      "Bu telefonun tarayıcısı bildirimi desteklemiyor. Sayfayı açık tutarsan sıranı buradan takip edebilirsin. (iPhone'da: Paylaş → Ana Ekrana Ekle, sonra oradan aç.)";
    return;
  }
  $("bildirim-dugme").classList.remove("gizli");
  kur();
}
