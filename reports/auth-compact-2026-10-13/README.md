# Son referansa göre kompakt auth düzeni

- Tek referans: bu görevde eklenen 1000179318.jpg. Önceki görseller tasarım girdisi olarak kullanılmadı.
- Repo/branch: muammercerdin-star/Zeytin-Defteri-Recovery / work/auth-review-2026-10-13.
- Başlangıç HEAD: e5d13a607de53e71feea0fc880cd715b0147ff71. Çalışma ağacı temizdi; uzak HEAD ile aynıydı.
- Yerel geri dönüş etiketi: safety/auth-compact-before-2026-10-13.
- Main'e ve diğer repolara yazılmadı.

## Yapılan düzeltmeler
| Ölçü | Önce | Sonra |
|---|---:|---:|
| Üst/alt form boşluğu | 24dp | 18dp |
| Logo | 40dp | 36dp |
| Marka yazısı | 17sp | 15sp |
| Başlık | 26sp | 22sp |
| Açıklama | 14sp | 13sp |
| Başlığın üst boşluğu | 24dp | 20dp |
| Sekmelerin üst boşluğu | 26dp | 20dp |
| Sekme yazıları | 14sp | 13sp |
| Google minimum yüksekliği | 54dp | 50dp |
| Google üst boşluğu | 24dp | 18dp |
| Google yazısı | 16sp | 14sp |
| Alan minimum yüksekliği | 50dp | 48dp |
| Alan yazısı | 16sp | 14sp |
| Alan etiketi | 13sp | 12sp |
| Alan grupları alt boşluğu | 16dp | 12dp |
| Ana düğme minimum yüksekliği | 56dp | 52dp |
| Ana düğme yazısı | 16sp | 14sp |
| Güvenlik metni üst boşluğu | 24dp | 18dp |
| İçerik maksimum genişliği | 480dp | 360dp |

- Yatay dış boşluklar 20dp'den 24dp'ye çıkarıldı. Geniş ekranlarda form ortalanır.
- Gereksiz font üst/alt boşlukları includeFontPadding=false ile azaltıldı; fontFamily ve sp ölçeklendirmesi korunur.
- Belge bağlantıları iki ayrı dikey sıra yerine yan yana iki bağımsız dokunma alanına alındı. Büyük fontta metinler satır kırarak uzayabilir; yükseklik sabitlenmedi.
- Belge eksikliği mesajı kısaltıldı; mevcut kayıt engeli kaldırılmadı.
- Ad Soyad hint değeri tam olarak `Örn. Muammer Çerdin`. EditText.text veya kullanıcı profiline isim yazılmadı.
- ScrollView, adjustResize, göz ikonları, 48dp dokunma hedefleri, şifre gücü ve koyu lacivert/yeşil palet korundu.
- Java değişikliği yalnız içerik genişliği ve ana düğmenin işlem durumundaki padding değerlerinden oluşur. Auth, UID, oturum, şifre doğrulama, KVKK guard ve yönlendirme mantığı birebir korunur.

## Doğrulama
- `python tests/auth/run.py`: 18 hata mesajı + 17 kayıt politikası kontrolü GEÇTİ.
- LoginActivity Java sözdizimi ayrıştırması GEÇTİ (tam Android tip kontrolü/build değildir).
- Düzen ve değer XML dosyaları ayrıştırıldı; placeholder'ın yalnız hint olduğu doğrulandı.
- Renk değerleri önceki HEAD ile birebir aynı.
- Kaydırma, göz/checkbox ölçüleri ve sekme/belge bağlantılarının 48dp minimum dokunma alanı doğrulandı.
- Kaynak karşılaştırması: Java'da beklenen üç ölçü satırı dışında fark yok.
- `git diff --check` GEÇTİ.
- `bash gradlew :app:assembleDebug --console=plain` denendi; `java.net.UnknownHostException: services.gradle.org` nedeniyle Gradle indirilemedi. Yeni APK üretilmedi.

## Kalan sınırlar
Bu ortamda Android render/emülatör veya fiziksel cihaz testi yapılmadı. Ölçüler kaynakta küçültüldü; referansla gerçek cihaz üzerinde birebir eşleşme doğrulanmış sayılmaz. Küçük ekran, %200 font, klavye açıkken alt bölüme kaydırma, iki belge bağlantısına ayrı dokunma ve şifre göster/gizle cihazda kontrol edilmeli.

Onaylı KVKK belgesi eksikliği ve kayıt sekmesindeki mevcut kapalı durum değişmedi. Firebase/Google/e-posta ağ işlemleri bu görevde canlı test edilmedi. Ana yedi menü, Firebase/Supabase, Gradle ve özel yerel dosyalar değiştirilmedi.

## Değişen dosyalar
1. app/src/main/java/com/zeytindefteri/app/LoginActivity.java
2. app/src/main/res/layout/activity_auth_premium.xml
3. app/src/main/res/values/auth_premium.xml
4. reports/auth-compact-2026-10-13/README.md
