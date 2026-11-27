// Çiftçi bildirimi için VAPID anahtar çifti üretir. Çalıştır: node vapid-uret.mjs
// Çıktıdaki iki değeri Cloudflare'de Secret olarak ekle (KURULUM.md 4b). Hiçbir dosyaya yazma.
// Yalnız WebCrypto kullanır; dış bağımlılık/paket kurulumu gerekmez.

const k = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign"]);
const pub = new Uint8Array(await crypto.subtle.exportKey("raw", k.publicKey));
const jwk = await crypto.subtle.exportKey("jwk", k.privateKey);

function b64u(bytes) {
  let s = "";
  for (const b of bytes) {
    s += String.fromCharCode(b);
  }
  return btoa(s).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

console.log("VAPID_ACIK_ANAHTAR =", b64u(pub));
console.log("VAPID_GIZLI_ANAHTAR =", jwk.d);
