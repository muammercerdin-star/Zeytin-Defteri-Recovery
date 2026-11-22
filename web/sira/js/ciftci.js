// Çiftçi sayfası: yalnız linkteki token ile, yalnız kendi sırası. İsim gösterilmez.
// Sayfa ekrandayken 60 saniyede bir yenilenir; sıkım bitince ya da iptalde durur (bulut kotası korunur).

const YENILEME_MS = 60000;
const $ = (id) => document.getElementById(id);
let zamanlayici = null;
let bitti = false;

function token() {
  const yol = window.location.pathname.replace(/^\/+|\/+$/g, "");
  return /^[A-Za-z0-9_-]{22}$/.test(yol) ? yol : null;
}

function goster(id, acik) {
  $(id).classList.toggle("gizli", !acik);
}

function adimlar(durum) {
  const sira = ["teslim", "sirada", "isleniyor", "bitti"];
  const at = durum === "iptal" ? -1 : sira.indexOf(durum);
  for (const li of $("adimlar").children) {
    const i = sira.indexOf(li.dataset.adim);
    li.className = "";
    if (i < at || (durum === "bitti" && i === at)) {
      li.classList.add("tamam");
    } else if (i === at) {
      li.classList.add("simdi");
    }
  }
}

function ciz(v) {
  goster("yukleniyor", false);
  goster("yok", false);
  goster("icerik", true);
  $("fabrika").textContent = v.fabrika;
  $("kod").textContent = "Kod " + v.kod;
  $("no").textContent = v.sira_no;
  const kg = Number(v.kg).toLocaleString("tr-TR");
  $("teslim").textContent = kg + " kg · " + v.kasa + " kasa · teslim " + v.gelis;
  goster("sira-kutu", v.durum === "sirada");
  goster("saat-kutu", v.durum === "sirada" || v.durum === "isleniyor");
  if (v.durum === "sirada") {
    $("baslik").textContent = "Zeytininiz sırada";
    $("onunde").textContent = v.onunde === 0 ? "Sıradaki sizsiniz." : "Önünüzde " + v.onunde + " kişi var.";
    $("saat-etiket").textContent = "Tahmini işlenme";
    $("saat").textContent = v.tahmini + " civarı";
  } else if (v.durum === "isleniyor") {
    $("baslik").textContent = "Zeytininiz şu an işleniyor";
    $("onunde").textContent = "";
    $("saat-etiket").textContent = "Tahmini bitiş";
    $("saat").textContent = v.biter + " civarı";
  } else if (v.durum === "bitti") {
    $("baslik").textContent = "Bitti · yağınız hazır";
    const yag = Number(v.yag_kg || 0).toLocaleString("tr-TR");
    $("onunde").textContent = kg + " kg zeytinden " + yag + " kg yağ çıktı.";
  } else {
    $("baslik").textContent = "Bu sıra iptal edildi";
    $("onunde").textContent = "Bilgi için yağhaneye sor.";
  }
  adimlar(v.durum);
  $("guncel").textContent = "Son güncelleme " + v.guncel + (v.durum === "sirada" || v.durum === "isleniyor" ? " · sayfa kendiliğinden yenilenir" : "");
  bitti = v.durum === "bitti" || v.durum === "iptal";
}

async function yukle() {
  const t = token();
  if (!t) {
    goster("yukleniyor", false);
    goster("yok", true);
    return;
  }
  let r = null;
  try {
    r = await fetch("/api/sira/" + t, { credentials: "omit", cache: "no-store" });
  } catch {
    $("guncel").textContent = "İnternet yok; bağlantı gelince yenilenecek.";
    return;
  }
  if (r.status === 404) {
    goster("yukleniyor", false);
    goster("icerik", false);
    goster("yok", true);
    bitti = true;
    return;
  }
  if (!r.ok) {
    $("guncel").textContent = "Sunucu şu an cevap vermedi; biraz sonra yenilenecek.";
    return;
  }
  ciz(await r.json());
}

function zamanla() {
  clearInterval(zamanlayici);
  zamanlayici = setInterval(() => {
    if (document.visibilityState === "visible" && !bitti) {
      yukle();
    }
  }, YENILEME_MS);
}

document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible" && !bitti) {
    yukle();
  }
});
zamanla();
yukle();
