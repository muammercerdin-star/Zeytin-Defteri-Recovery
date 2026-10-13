# Elek sabit dashboard

Ana XML boşlukları düzenlendi; root, ScrollView alt sınıfı ElekDashboardScrollView oldu. Mevcut elekFormScroll ID aynı kaldı. İçerik ölçülen viewport'a sığdığında dokunma/fling kaydırması yoktur ve offset sıfır tutulur. Alan klavyeyle daralırsa veya büyük yazı/küçük ekran yüzünden içerik sığmazsa native scroll güvenli erişim sağlar. Klavye kapanıp viewport büyüdüğünde geçici offset sıfırlanır. Fontları küçültme veya içeriği keserek kaydırmayı körlemesine kapatma yapılmadı.

Nominal dikey kazanç 50dp: body üst/alt 12→8 (8dp), header minimum 56→52 (4dp), tarih kartı üst/alt 8→6 (4dp), tarih üst margin 8→4 (4dp), hero yüksekliği 88→84 (4dp), hero üst/alt margin 8→6 (4dp), 6 satır minimum 52→50 (12dp), özet üst/alt padding 10→8 (4dp), özet üst margin 8→6 (2dp), alt aksiyon üst/alt margin 8→6 (4dp). Metin sarılması gerçek kazanımı etkileyebilir. Yatay padding'ler korunur. Ana aksiyon minHeight60dp, miktar input48dp, ikon36/36/24dp ve tüm textSize değerleri aynıdır.

Sistem çubukları: mevcut fitsSystemWindows, edge-to-edge olmayan window flags ve adjustResize korunur. Android'in ölçtüğü kullanılabilir içerik yüksekliği kullanılır; sabit navigation bar yüksekliği veya cihaz özelinde koordinat yoktur. Popup XML alt ağacı ve ElekActivity.java tamamen aynıdır.

Kontroller: XML parse, ID/metin/font/input karşılaştırması, Java 8 syntax parse, mevcut hesaplama/fiyat JVM testleri geçti. Tüm eski Java dosyaları, popup kaynakları, SQLite/DatabaseHelper/Firebase/Repository dosyaları kaynakla aynıdır. Tek mevcut değişen dosya activity_elek.xml; yeni dosya ElekDashboardScrollView.java.

Build denendi; Gradle services.gradle.org UnknownHostException nedeniyle indirilemedi. APK, Android resource linking ve gerçek cihazda ölçüm doğrulanamadı. Bu nedenle referans cihazda tüm butonların aynı anda göründüğü, sürükleme/klavye kapanışı davranışının cihaz testini geçtiği iddia edilmez. Çok küçük viewport veya büyük fontta güvenli scroll açık kalır. Normal fontta içerik sığan viewport sabittir.
