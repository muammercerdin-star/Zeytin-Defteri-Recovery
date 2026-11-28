// Elle giriş: yağhane kısa adı + 4 haneli kod + (telefonun son 2 hanesi ya da kasa sayısı) → kendi sıra sayfası.
const form = document.getElementById("form");
const mesaj = document.getElementById("mesaj");
const parametre = new URLSearchParams(window.location.search).get("f");
if (parametre && /^[a-z0-9]{2,32}$/.test(parametre)) {
  form.fabrika.value = parametre;
  form.kod.focus();
}

form.addEventListener("submit", async (olay) => {
  olay.preventDefault();
  mesaj.classList.add("gizli");
  const govde = {
    fabrika: form.fabrika.value.trim().toLowerCase(),
    kod: form.kod.value.trim(),
    ek: form.ek.value.trim(),
  };
  let r = null;
  try {
    r = await fetch("/api/sira/bul", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(govde),
      credentials: "omit",
    });
  } catch {
    mesaj.textContent = "İnternet bağlantısı yok.";
    mesaj.classList.remove("gizli");
    return;
  }
  const body = await r.json().catch(() => null);
  if (r.ok && body && body.token) {
    window.location.href = "/" + body.token;
    return;
  }
  mesaj.textContent = (body && body.mesaj) || "Sıra bulunamadı.";
  mesaj.classList.remove("gizli");
});
