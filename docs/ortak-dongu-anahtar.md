# Bilgi paketi imza anahtarı (İP8-B'de kullanılacak)

İndirilen bilgi paketlerini telefon, uygulamaya gömülü **açık** anahtarla doğrular (ECDSA P-256, SHA256withECDSA).
Paketi imzalayan **özel** anahtar hiçbir zaman sunucuda, depoda (git), telefonda ya da bulutta durmaz.
Gömülü başlangıç paketi imzasızdır; uygulamanın kendi Play imzası ve SHA-256 özeti onu korur.

## 1. Anahtarı üretmek

İnternete bağlı olmayan, güvendiğin bir bilgisayarda:

```sh
umask 077
openssl ecparam -name prime256v1 -genkey -noout -out zd-paket-1.pem
openssl ec -in zd-paket-1.pem -pubout -outform DER | xxd -p | tr -d '\n' > zd-paket-1.pub.hex
```

- `zd-paket-1.pem`: ÖZEL anahtar. Gizli.
- `zd-paket-1.pub.hex`: açık anahtar (X.509 DER, onaltılık). Bu metin `KnowledgePack.PUBLIC_KEYS` içindeki
  yuvaya yazılır; gizli değildir.

## 2. Saklamak ve yedeklemek

- Özel anahtarı parolayla şifrele: `openssl pkcs8 -topk8 -v2 aes-256-cbc -in zd-paket-1.pem -out zd-paket-1.p8`,
  sonra şifresiz `zd-paket-1.pem` dosyasını sil.
- Şifreli dosyanın **iki** kopyası olsun: biri çevrimdışı bir USB bellekte, biri ayrı bir yerde
  (ör. kasada ikinci bir USB). Parola ayrı yerde, kâğıtta saklansın.
- Depoya (git), e-postaya, mesajlaşma uygulamalarına, Google Drive'a ya da sunucuya hiçbir zaman konmaz.
- Yılda bir, yedekten anahtarın açılabildiği ve bir deneme paketinin imzalanabildiği denenir.

## 3. Paket imzalamak

1. Sunucuda paket yöneticisi `select ortak.paket_uret(<sürüm>)` çalıştırır; çıktıdaki `imzalanacak` metin
   ve `sozler` alınır (imza atılmadan).
2. Çevrimdışı bilgisayarda: `imzalanacak` metni dosyaya yazılır (sonunda satır sonu olmadan) ve imzalanır:
   `openssl dgst -sha256 -sign zd-paket-1.pem imzalanacak.txt | xxd -p | tr -d '\n'`.
3. Paket JSON'una `"anahtar": 1` ve `"imza": "<onaltılık>"` eklenir, `imzalanacak` alanı çıkarılır.
4. Yayından önce paket, testteki gibi `KnowledgePack.read` ile ve kontrol bankasıyla (`PackGate`) denenir.

## 4. İki yuva ile anahtar değiştirmek

`PUBLIC_KEYS` iki yuvalıdır; anahtar değişirken eskisi ve yenisi birlikte durur:

1. Yeni anahtar (`zd-paket-2`) üretilir ve yedeklenir; açık anahtarı **yuva 2**'ye yazılır, uygulama yayınlanır.
2. Çiftçilerin çoğu bu sürüme geçene kadar paketler eski anahtarla (`"anahtar": 1`) imzalanmaya devam eder.
3. Sonra paketler yeni anahtarla (`"anahtar": 2`) imzalanır.
4. Bir sonraki sürümde yuva 1 temizlenir (ya da bir sonraki değişim için yeni anahtar yazılır).

Özel anahtar kaybolursa ya da çalınırsa: o yuva bir sonraki uygulama sürümünde hemen boşaltılır, diğer yuvadaki
anahtarla devam edilir. Çalınma şüphesinde o sürüme kadar paket yayını durdurulur; telefonlar ellerindeki son
geçerli paketle (ya da gömülü paketle) çalışmaya devam eder.
