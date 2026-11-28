// Ekrana yansıt (TV): büyük yazı, isimler kısaltılmış ("Ayşe D."), kilo yok. Ofis oturumuyla açılır.
import { adCoz, kisalt } from "./anahtar.js";
import { api, cihazAnahtari, depo, el } from "./ortak.js";

const YENILEME_MS = 20000;
const GOSTERILEN = 8;
const d = depo();
const durum = { anahtar: null, iz: undefined };
const $ = (id) => document.getElementById(id);

async function kisaAd(v, s) {
  if (s.ad_sifreli && durum.anahtar) {
    const ad = await adCoz(durum.anahtar, v.yaghane.kisa_ad, s.ad_sifreli);
    if (ad) {
      return kisalt(ad);
    }
  }
  return "Kod " + s.kod;
}

async function yukle() {
  const r = await api("/api/yaghane/gun").catch(() => null);
  if (r && r.status === 401) {
    window.location.replace("/yaghane/");
    return;
  }
  if (!r || r.status !== 200) {
    $("not").textContent = "Bağlantı yok; son liste gösteriliyor.";
    return;
  }
  const v = r.body;
  if (durum.iz !== v.ayar.anahtar_izi) {
    durum.iz = v.ayar.anahtar_izi;
    durum.anahtar = (await cihazAnahtari(d, v.yaghane.kisa_ad, v.ayar.anahtar_izi)).anahtar;
  }
  $("baslik").textContent = v.yaghane.ad + " · sıra";
  const isleniyor = v.sira.filter((s) => s.durum === "isleniyor");
  const parcalar = [];
  for (const s of isleniyor) {
    parcalar.push(s.sira_no + " · " + (await kisaAd(v, s)));
  }
  $("isleniyor").textContent = parcalar.length ? "Şimdi işleniyor: " + parcalar.join(", ") : "Şu an işlenen yok";
  const liste = $("liste");
  liste.textContent = "";
  const sirada = v.sira.filter((s) => s.durum === "sirada").slice(0, GOSTERILEN);
  for (const s of sirada) {
    const satir = el("div", "tv-satir");
    satir.append(el("span", null, s.sira_no + " · " + (await kisaAd(v, s))), el("span", "tv-saat", s.tahmini ? "~" + s.tahmini : ""));
    liste.append(satir);
  }
  const kalan = v.sira.filter((s) => s.durum === "sirada").length - sirada.length;
  if (kalan > 0) {
    liste.append(el("div", "tv-satir", "+ " + kalan + " kişi daha"));
  }
  $("not").textContent = "Sıranı telefonundan da takip edebilirsin: sana gelen linki aç ya da sira.zeytindefteri.tr/bul · " + v.saat;
}

$("tam-ekran").addEventListener("click", () => {
  document.documentElement.requestFullscreen?.().catch(() => null);
  $("tam-ekran").classList.add("gizli");
});
setInterval(yukle, YENILEME_MS);
yukle();
