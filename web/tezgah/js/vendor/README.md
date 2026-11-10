# Yerel QR kodlayıcı

- Proje: Kazuhiko Arase **qrcode-generator 2.0.4**.
- Resmî kaynak: https://github.com/kazuhikoarase/qrcode-generator
- Sabit kaynak commit'i: `83b7e8fe3fddd3b0368dbafd6ce56995bd25e3c8`.
- Alınan dosya: `js/dist/qrcode.mjs` → burada `qrcode.js`; içerik aynen korunmuştur.
- Lisans: **MIT**, tam metin [LICENSE](LICENSE). Kaynaktaki telif başlığı da korunur.
- Runtime yükleme/CDN/npm yok. Yalnız Byte mode, ECC M, `getModuleCount/isDark` kullanılır; canvas uygulama kodunda çizilir.
- Test: `tests/web_page/qr-vectors.json`, bağımsız Project Nayuki Python QR-Code-generator ile byte mode/ECC M/boost kapalı olarak üretilen kısa ve uzun metin matrislerinin SHA-256 değerleri; sekiz maskenin her biri fixture'dadır. Otomatik maske seçiminin bu geçerli matrislerden biri olduğu ve boyutu kontrol edilir. Nayuki kaynağı test fixture üretiminde kullanılmıştır; üretim bağımlılığı değildir. Referans kaynak: https://github.com/nayuki/QR-Code-generator (Python `qrcodegen.py`, commit `bcfe1b018cec5f1f91b5705eca5bb4cdd34e9f8d`).
- Gerçek telefon kamerasıyla tarama bu ortamda **doğrulanmadı**; DEMO listesinde yer alır.
