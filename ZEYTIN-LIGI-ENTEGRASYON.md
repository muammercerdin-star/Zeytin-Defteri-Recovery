> Backend V1 finalizasyonu eklendi. Güncel sunucu kodu, güven sınırı, bölge anahtarı,
> test durumu ve deployment talimatları için **ZEYTIN-LIGI-BACKEND-V1.md** geçerlidir.
> Aşağıdaki metin ilk Android entegrasyonunun tarihsel notudur; “yayımlayıcı yok”
> ifadeleri ilk sürümü anlatır. Artık yayımlayıcı kodu vardır; gerçek dış satış kanıtı
> sağlayıcısı ve canlı deployment hâlâ gereklidir. Android/V2 UI değiştirilmemiştir.

# Zeytin Ligi — sürüm 1

## Teslim edilen davranış

REPORTS hedefi ZeytinLeagueView açar. Alt navigasyon, asistan, hızlı kayıt ve diğer
Intent bağlantıları değiştirilmemiştir. Eski rapor ekranı, ligin altındaki
“Kişisel raporlar ve işlem geçmişi” bağlantısıyla korunur.

Mevcut projede doğrulanmış ortak lig verisi veya güvenilir lig yayımlayıcısı yoktur.
Bu paket çalışan kişisel önizlemeyi ve sunucu sonuçları için salt okunur entegrasyon
katmanını içerir. Global lig yayına alınmış değildir. Firebase'e dağıtım yapılmadı.
Sunucu sonuçları yokken başka üretici, sıra numarası veya piyasa fiyatı uydurulmaz.
Yerel puan bölgesel sıra değildir. Üretici 100 kg altında ise veri yetersizdir.

## Tek veri kaynağı ve izolasyon

ElekRepository.archiveQuery, ElekArchiveActivity kayıt listesi,
DatabaseHelper.getElekSeasonArchive sezon özeti ve ligin ortak sorgusudur.
Toplam kg = SUM(elek_grades.kg), tutar = SUM(elek_grades.line_total).
Ortalama = toplam tutar / toplam kg. Kayıt ortalamalarının ortalaması alınmaz.
Satış kayıtları ayrıca eklenmez; çift sayım yapılmaz. Elek tutarları tahmini
olabilir; uygulama bunları doğrulanmış satış olarak tanıtmaz.

Sorgu g.owner_uid ile sabitlenir; farklı owner_uid taşıyan elek kaydı dışlanır.
Eski owner_uid boş elek kayıtları yalnızca bu kullanıcıya ait bahçeye bağlıysa
okunur. Misafir yalnızca owner_uid boş bahçeleri okuyabilir.
Sezon: er.season_year; eski sıfır/boş sezonlarda tarihin ilk/son dört hanesi.
Arşiv açılış yılı mevcut aktif sezonu kullanır; bahçe filtresi korunur.
Bu ortaklaştırma eski arşiv sorgusundaki eksik kullanıcı koşulunu da giderir.
Önceden yanlış hesaptan veya yalnızca ISO tarih filtresinden gelen toplamlar
bu nedenle düzelebilir. Kayıtlar değiştirilmez veya silinmez.
DB_VERSION, CREATE/ALTER TABLE ve migration kodu değişmemiştir.

Arka plan işi için owner_uid başta yakalanır. Sonuçlar ekran ayrılınca veya
kullanıcı değişince gösterilmez. Sezon değişimi mevcut sezon seçicisini kullanır
ve REPORTS içeriğini yeniden oluşturur. SQLite işi ayrı worker'da çalışır;
Cursor ve yalnızca bu işin açtığı DatabaseHelper kapanır.

## Bölge ve üretici

Bir kullanıcı seçilen sezonda TEK üreticidir; tüm bahçeleri birleştirilir.
Bölgesi, kg toplamı en büyük olan ilçe/il grubudur. Eşitlikte bölge anahtarı
sırası kullanılır. İlçe boşsa il, ikisi boşsa “Bölge belirtilmemiş” gösterilir.
Bilinen ilçe/il olmadan global sıralamaya yayın yapılmamalıdır.
Yerel bölge kartları sadece bu kullanıcıdan türetilir; cihazdaki başka hesaplara
ait veriler bölgesel veri gibi kullanılmaz.

Bölge anahtarı: district + (province varsa " / " + province), district boşsa
province. Boşlukları birleştir, trim, tr-TR küçük harf, Unicode NFC, UTF-8 SHA256
küçük hex. Aynı kanonik il/ilçe normalizasyonu sunucuda uygulanmalıdır.
Yazım hataları ve farklı idari adlar otomatik olarak doğrulanmış sayılmaz.

## Puan politikası v1

ZeytinLeagueScore saf Java politika sınıfıdır. Puan 0–100:

Puan = 0.50 × kalite + 0.35 × fiyat + 0.15 × hacim.

| Elek | Kalite ağırlığı |
|---|---:|
| DOUBLE | 100 |
| 1 | 90 |
| 2 | 75 |
| 3 | 55 |
| 4 | 30 |
| YAGLIK | 10 |

Kalite = SUM(elek kg × ağırlık) / toplam kg.
Hacim = min(100, 100 × ln(1 + kg) / ln(10001)); 10.000 kg'da doyar.
Fiyat için aynı bölge ve sezonda her elek için en az üç BAŞKA doğrulanmış
üreticinin medyanı gerekir; kişinin kendi kayıtları referansa girmez.
Her üretici/elek için tek ağırlıklı fiyat gözlemi alınır; çok kayıt avantaj vermez.
MarketActivity'deki enterpolasyonlu çeyreklik ve 1.5 IQR yaklaşımı kullanılır;
ZeytinLeagueScore.robustMedian bu yöntemi test edilebilir hale getirir.
Filtre sonrası en az üç gözlem gerekir. Sıfır IQR durumunda eşit medyanın
dışındaki uç değerler de ayıklanır.

Beklenen tutar = SUM(üreticinin elek kg × o eleğin bölgesel medyanı).
Oran = gerçek tutar / beklenen tutar.
Fiyat = clamp(100 × (oran - 0.5), 0, 100). Piyasa düzeyi 50 puandır.
Oran <0.25 veya >3 ise uç değer; global sıralamaya alınmaz, inceleme gerekir.
Referans eksikse fiyat 50 ve sonuç geçicidir; piyasa verisi varmış gibi gösterilmez.
Yerel önizlemede güvenilir başka üretici verisi bulunmadığından fiyat her zaman
nötrdür. Global puanlar sunucudan gelir; istemci bunları yazamaz.

Uygunluk: en az 100 kg, en az bir kayıt, pozitif sonlu tutar ve kg, negatif
olmayan altı elek miktarı ve bunların toplam kg ile uyumu. Bilinmeyen elek,
NaN/Infinity ve tutarsız dağılım uygun değildir. Minimum veri, gerçekliği
kanıtlamaz; global için ayrıca sunucu doğrulaması şarttır.
Sıralama: puan azalan, kalite azalan, güvenli üretici kimliği artan.
Aynı üretici iki kez geçerse politika sıralamayı reddeder.

## Firebase entegrasyon sözleşmesi — etkinleştirme ön koşulu

Mevcut users/{uid}/gardens/{gardenCloudId}/elek_records/{recordId} belgeleri
kişisel yedektir ve kullanıcı yazabilir. Bunlar tek başına güvenilir yarışma
kanıtı DEĞİLDİR. Mevcut sosyal koleksiyonlar da lig kaynağı yapılmadı.
İstemci hiçbir yeni lig yazısı veya özel kayıtların toplu paylaşımını yapmaz.

Güvenilir Admin SDK yayımlayıcısı aşağıdaki belgeleri oluşturduğunda adaptör
sonuçları okuyabilir. Bu pakette doğrulanmış veri sağlayıcısı/yayımlama işi yoktur;
kullanıcının kendi bildirdiği kayıtları otomatik doğrulayan bir trigger eklenmedi.
Dağıtımdan önce doğrulama/tekrar kayıt kontrolü, paylaşım izni, kanonik bölge ve
sunucu tarafı üretici toplulaştırması tanımlanmalıdır. Auth tek başına satış
kanıtı değildir. Puanı istemci özetinden veya istemcinin gönderdiği rank'ten alma.

Belge yolları ve alanları:

- zeytin_leagues/{year}: current_snapshot (1–100 karakter [A-Za-z0-9_-]).
- zeytin_leagues/{year}/snapshots/{version}: complete=true, policy_version=1,
  published_at (Firestore Timestamp). İstemci yalnızca 24 saat içinde yayımlanan
  tamamlanmış sürümü gösterir; eski sürüm yerel önizlemeye düşer.
- .../snapshots/{version}/regions/{regionId}: name, producer_count, kg, amount,
  dominant ve top (en fazla 20 üretici özeti). Bölge toplamları tüm uygun
  üreticilerden, yalnızca Top 20'den değil, hesaplanır.
- .../snapshots/{version}/members/{uid}: kullanıcının sıralama özeti; Top 20
  dışında da yayımlanır. İstemci yalnızca kendi belgesini okuyabilir, listeleyemez.

Üretici özeti: producer_id (sunucuda üretilmiş takma kimlik; ham UID/e-posta
DEĞİL), region_id, rank (1 tabanlı), kg, amount, score, grades (altı sayı;
DOUBLE/1/2/3/4/YAGLIK sırası). Top dizisi rank 1..min(20,producer_count)
sırasındadır. İsimler istemcide “Üretici {rank}” şeklinde anonim gösterilir.
Members belgesindeki producer_id, ilgili Top 20 kimliğiyle aynı olmalıdır.
Aynı kullanıcı sezon başına tek region_id/members belgesinde bulunmalıdır.

Önce değişmez sürümün tüm bölgelerini ve kişisel sıralarını yaz; sonra complete
ve en son current_snapshot işaretçisini yayımla. Böylece kart, Top 20 ve kişisel
sıra aynı sürümden gelir. Eski sürümü aktif okuyucular için bir süre sakla.
Sunucu doğrulanmış kayıtları üretici+sezon bazında artımlı toplulaştırmalı,
bölgesel medyan ve sıraları sunucuda hesaplamalıdır. Telefona ham global kayıtlar
indirilmez. Adaptör sayfa başına en fazla 50 bölge belgesi okur; daha fazla bölge
kullanıcı isteğiyle sayfalanır. Her bölge en fazla 20 özet taşır. Kişisel sıra
tek belge okumasıdır. 500 bin üretici telefonda sıralanmaz.

firestore.rules yeni lig kökünde tüm istemci yazılarını reddeder. Bölgeler giriş
yapan kullanıcıya okunur; members yalnızca ilgili UID tarafından get edilir.
Kurallar dağıtılmadan yeni kök eski default-deny kuralıyla kapalı kalır; uygulama
bu durumda kişisel önizleme gösterir. Mevcut kuralların izinleri değiştirilmedi.
Global devreye alma için kuralların emulator testleri ve gerçek sunucu veri
hattının doğrulanması gerekir. Bunlar bu ortamda çalıştırılmadı/dağıtılmadı.

## Yapılan kontroller

- 77 Java dosyası JDK parser ile: sözdizimi hatası yok (APK derlemesi değildir).
- Saf puan sınıfı gerçek Java derleyicisiyle derlendi; 66 doğrulama geçti.
- Repository gerçek sorgu üretimi Android API test doubles ile derlendi;
  çıkan SQL SQLite üzerinde çalıştırıldı. 522 kg / 31.276 TL / 59,92 TL/kg,
  arşiv/özet eşitliği, bahçe filtresi, kullanıcı/misafir/sezon ayrımı, eski tarih
  ve farklı owner taşıyan kayıt dışlama kontrolleri geçti.
- Firebase adaptörü test doubles ile derlenip çalıştırıldı: Top 20, Top 20 dışı
  kişisel #25, misafir/hesap uyuşmazlığı, yinelenen üretici, yanlış sıra ve eski
  snapshot reddi geçti. Bu kontroller canlı Firebase veya rules emulator testi değildir.
- XML ve yeni drawable referansları, Manifest hedefleri statik kontrol edildi.
- Alt menü/asistan kodu ve DB sürümü/migration değişmediği karşılaştırıldı.
- Gradle assembleDebug: services.gradle.org için UnknownHostException.
  Gradle 8.7 indirilemedi; Android/Firebase ile tam derleme, emulator, gerçek
  cihaz görünümü, Firestore canlı sonuçları doğrulanamadı.
- Geçici test/probe/stub dosyaları proje dışında tutuldu, FINAL ZIP'e eklenmedi.
