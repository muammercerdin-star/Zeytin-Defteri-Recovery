import { api, qrAnahtariniAl } from "./ortak.js";

function hedef(rol) {
  return rol === "ofis" ? "/yaghane/ofis.html" : "/yaghane/havuzcu.html";
}

async function basla() {
  qrAnahtariniAl();
  const acik = await api("/api/yaghane/gun").catch(() => null);
  if (acik && acik.status === 200) {
    window.location.replace(hedef(acik.body.ben.rol));
    return;
  }
  const form = document.getElementById("form");
  const mesaj = document.getElementById("mesaj");
  const gir = document.getElementById("gir");
  form.addEventListener("submit", async (olay) => {
    olay.preventDefault();
    mesaj.classList.add("gizli");
    gir.disabled = true;
    const bas = performance.now();
    const govde = { kullanici_adi: form.ad.value.trim().toLowerCase(), sifre: form.sifre.value };
    let r = null;
    try {
      r = await api("/api/yaghane/giris", "POST", govde);
    } catch {
      r = { status: 0, body: { mesaj: "İnternet bağlantısı yok. Giriş için bağlantı gerekir." } };
    }
    gir.disabled = false;
    if (r.status === 200) {
      // Pilot ölçümü: giriş süresi (şifre özeti + ağ). Yalnız bu tarayıcıda, ofis panelinde gösterilir.
      try {
        window.localStorage.setItem("zs_giris_ms", String(Math.round(performance.now() - bas)));
      } catch {
        // ölçü işi bozmaz
      }
      window.location.replace(hedef(r.body.rol));
      return;
    }
    let metin = (r.body && r.body.mesaj) || "Giriş yapılamadı.";
    if (r.status === 429 && r.body.bekle_sn) {
      metin += " (" + Math.ceil(r.body.bekle_sn / 60) + " dk)";
    }
    if (r.status === 503 || r.status >= 520) {
      metin = "Sunucu şu an cevap vermedi. Biraz sonra tekrar dene.";
    }
    mesaj.textContent = metin;
    mesaj.classList.remove("gizli");
  });
}

basla();
