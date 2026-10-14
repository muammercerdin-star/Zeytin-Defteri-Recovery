# Ana Sayfa "Saha Defteri" Yeniden Tasarımı — Çalışma Raporu

- Tarih: 13.10.2026
- Branch: `work/home-redesign-2026-10-13` (`work/auth-review-2026-10-13` @ `a885ede` üzerinden açıldı)
- `main` ve diğer depolara dokunulmadı.
- Tasarım kaynağı: "Zeytin Defteri Ana Ekran Konsepti" tuvali, uygulama paletine uyarlanmış sürüm.

## 1. Renkler — uygulama paletiyle birebir

Yeni ekranda yeni renk tanımı yok; tamamı mevcut `colors.xml` değerlerinden gelir.

| Kullanım | Kaynak |
|---|---|
| Zemin | `zd_bg` #0B1D28 |
| Kartlar | `zd_card` #132A38 + `zd_border` #29434D |
| İç satırlar (hava koşulları, yağış çubuğu zemini) | `zd_surface` #102632 |
| Vurgu / bağlantılar / geçmiş evreler | `zd_green` #79C95A |
| Takvimde güncel evre, dikkat tonu | `zd_warning` #E5B455 |
| Kritik ton | `zd_danger` #FF6B63 |
| Metin / ikincil metin | `zd_text`, `zd_muted`, `zd_text_soft` |
| Avatar zemini | `zd_green_chip` #274B39 |

İki yardımcı ton, mevcut hava panelindeki renklerle uyumlu olarak kodda tanımlıdır: yağış çubukları `#6FA7CF` ve bilgi tonu `#8FC0E0`. Yazı tipi uygulamanın sistem sans yazı tipi olarak kaldı; konseptteki serif yazı tipi, yazı tipi dosyası gerektirdiği için uygulanmadı.

## 2. Değişen / eklenen dosyalar

| Dosya | Değişiklik |
|---|---|
| `app/src/main/java/com/zeytindefteri/app/HomeDashboardModel.java` | **Yeni.** Android'den bağımsız: takvim evresi, ad gösterimi, tarih etiketleri, hava koşulu satırları, yağış çubuğu |
| `app/src/main/java/com/zeytindefteri/app/ProducerFeedActivity.java` | `buildHomeHero` yeniden yazıldı; `buildHomeSeasonCard`, `renderHomeConditions`, `renderHomeRainBars`, `buildHomeAssistantChips`, `askZeytinAssistant`, `ensureZeytinAssistant` eklendi; görev kartları yeni stile alındı; hava uyarılarında okunur tarih |
| `app/src/main/java/com/zeytindefteri/app/ZeytinAssistantController.java` | `ask(String)`: paneli açar, mikrofonu başlatmadan soruyu yazılı gönderir |
| `tests/home/HomeDashboardModelTest.java`, `tests/home/run.py` | **Yeni** host testleri (61 kontrol) |

## 3. Ekranda ne değişti

1. **Üst bölüm:** Büyük fotoğraflı alan (338dp) kaldırıldı. Yerine şunlar geldi:
   - tarih satırı ("Salı · 13 Ekim") ve "İyi akşamlar, Muammer" selamlaması;
   - Hatırlatmalar butonu: eski "♧" yerine zil ikonu (`ic_bell_line`), aynı ekranı açar;
   - profil avatarı (baş harfler), Profil'i açar.
2. **Bahçe seçici:** "Girelli Zeytinliği · 2026 ▾" hap butonu, aynı `showHomeGardenPicker` akışını açar.
3. **Zeytin takvimi:** Altı evre (Budama, Çiçek, Tutum, Çekirdek, Yağlanma, Hasat). Güncel evre altın renkle işaretli. Kartta son hasat kaydından bu yana geçen gün ve "Genel takvim; bölgene ve yıla göre değişebilir" notu var.
4. **Hava kartı:**
   - anlık sıcaklık ve durum;
   - üç koşul satırı: yağış, rüzgâr ve sıcaklık uyarısı varsa o, yoksa en kuru gün;
   - 5 günlük yağış çubukları;
   - "Saatlik ›" ile açılan **mevcut** 24 saat / 5 günlük panel (`renderWeatherForecastPanel`, aynen korundu).
5. **Bugün yapılacaklar:** `GardenTodayAdvisor` önerileri yeni kart stiliyle gösteriliyor:
   - etiket (HAVA / KAYIT), başlık, açıklama ve "Saatlik tahmin ›" / "Bahçeyi aç ›" bağlantısı;
   - "Bahçenin havası" kartı artık hava kartında olduğu için tekrar gösterilmiyor;
   - "2026-10-14 07:00 saatlik tahmin" yerine "Yarın 07:00" yazılıyor.
6. **Asistana sor:** "Bugün bahçede ne var?", "Yarın yağmur yağacak mı?", "Son sulama ne zaman?" Üçünün de asistanın tanıdığı komutlara denk geldiği testle doğrulandı.
7. **Korunanlar:** Hızlı işlem butonları (Sulama/Gübreleme/İlaçlama/Hasat), Piyasa Özeti, Bu Sezon ve yedi sekmeli alt menü değişmedi.

## 4. Konseptten bilinçli sapmalar

- **Hava satırları karar değil, koşul gösterir.** `GardenTodayAdvisor` projede bilinçli olarak tarımsal karar vermiyor ("No agronomic decisions"). Bu nedenle konseptteki "İlaçlama: uygun değil" / "Hasat: Cuma uygun" ifadeleri uygulanmadı. Yerine aynı dikkat eşikleriyle (yağış ≥ %60, rüzgâr ≥ 25 km/s, sıcaklık ≥ 35° / ≤ 0°) koşul ve "koşulları kontrol et" notu gösteriliyor. Testler karar ifadesi üretilmediğini kontrol eder.
- **Görev kutucukları yok.** Konseptteki "işaretle / tamamlandı" durumu hiçbir yerde saklanmadığı için sahte bir ilerleme göstermemek adına uygulanmadı.
- **Alt menü 7 sekme olarak kaldı.** 5 sekmeye indirme ayrı bir karar olarak bekliyor.
- **Ad gösterimi:** Görünen ad yalnızca baş harfse ("M.") artık tek başına gösterilmiyor; tam ad kullanılıyor.

## 5. Doğrulama

| Kontrol | Sonuç |
|---|---|
| `python3 tests/home/run.py` | 61 kontrol GEÇTİ |
| Değişen 3 Java dosyasının sözdizimi ayrıştırması | GEÇTİ (tip kontrolü değildir) |
| Çağrılan yardımcı yöntemlerin ve renk kaynaklarının varlığı | Elle doğrulandı |
| `GardenTodayAdvisorTest` (21), asistan host testleri (3 paket), auth testleri | GEÇTİ |
| Diğer Python testleri | 16 geçti, 4 başarısız — değişiklik öncesiyle aynı 4 eskimiş test |
| `assembleDebug` | **Çalıştırılamadı** (Gradle indirmesi ağ politikasıyla engelli) |

## 6. Cihazda kontrol edilmesi gerekenler

- Derleme ve ana sayfanın açılması.
- Bahçe yokken, hava yüklenirken ve hava hatasında görünüm.
- Bahçe değiştirince hava kartının yenilenmesi.
- "Saatlik ›" panelinin açılıp kapanması ve görevdeki "Saatlik tahmin ›" bağlantısının paneli açıp kaydırması.
- Zil (Hatırlatmalar) ve avatar (Profil) butonları.
- Asistan önerilerinin mikrofon izni istemeden cevap vermesi.
- Büyük yazı ölçeği.
- Yedi alt menünün çalışması.

## 7. Bahçelerim ekranı (aynı kart diliyle)

`ProducerFeedActivity.buildGardensSection` ve `gardenCard` yeniden yazıldı. Veri, sorgu ve gezinme değişmedi; hepsi mevcut `getGardenSummaries()` sorgusundan geliyor.

- **Başlık:** Ana Sayfa'daki gibi küçük üst satır ("2026 SEZONU · 3 BAHÇE") ve 24sp "Bahçelerim". "+ Bahçe ekle" büyük yeşil buton yerine Ana Sayfa'daki bahçe seçiciyle aynı çerçeveli hap butonu oldu.
- **Hatırlatmalar:** 🔔 emoji yerine Ana Sayfa'daki zil ikonu, kompakt satır kartı.
- **Bahçe kartları:**
  - Fotoğraf yoksa her bahçeye aynı stok manzara yerine sade bir zeytin karosu (56dp); kendi fotoğrafı olan bahçede fotoğraf gösterilir.
  - "🌳 100 ağaç 📅 10 yaş" yerine "Gemlik · 100 ağaç · 10 yaş".
  - Altta üç küçük gösterge: bu sezon **Hasat** (kg), **Masraf** (TL) ve **Son işlem**. Bu değerler sorguda zaten vardı, önceden gösterilmiyordu.
  - Ana Sayfa'da gösterilen bahçede altın renkli **AKTİF** rozeti var. Ayar boşsa Ana Sayfa'nın seçtiği ilk bahçe işaretlenir.
- **Erişilebilirlik:** Her kart tek bir okunabilir açıklama taşır.
- **Testler:** `gardenMeta` ve `gardensOverline` için 4 yeni kontrol eklendi (toplam 65).
- **Eski ekran:** Kullanılmayan eski `GardensActivity` ekranına dokunulmadı (Manifest'te kayıtlı ama hiçbir yerden açılmıyor).
- **Cihazda kontrol:** Kart dokunuşu → bahçe detayı; "+ Bahçe ekle"; Hatırlatmalar; fotoğraflı ve fotoğrafsız bahçe; AKTİF rozetinin Ana Sayfa'da bahçe değiştirince güncellenmesi.

## 8. Bahçelerim — kabul edilen tasarım uygulandı

Kaynak: tasarım tuvalindeki "Bahçelerim · hayal edilen" ekranı. Yayıncı bu ekranı proje tasarımı olarak kabul etti.

### Eklenenler
- **Sezon özeti kartı:**
  - büyük altın renkte **kilogram başına maliyet** (bu sezonun toplam masrafı ÷ toplam hasadı); hasat veya masraf yoksa "—" ve "kayıt girildikçe hesaplanır" notu;
  - toplam ağaç, hasat ve masraf;
  - "Rapor ›" bağlantısı mevcut `GardenAnalyticsActivity`'yi tüm bahçeler için açar.
- **Kayıt uyarısı:** "N bahçe kayıt bekliyor". Eşik `GardenTodayAdvisor.RECORD_REVIEW_DAYS` (30 gün); hiç kaydı olmayan bahçe de sayılır. Dokununca liste "Kayıt bekleyen" sırasına geçer.
- **Sıralama:** Aktif önce / Ada göre (Türkçe alfabe) / Verime göre / Kayıt bekleyen. Seçim `gardens_sort` ayarında saklanır; yenilemede kaydırma konumu korunur.
- **Bahçe kartı:**
  - **Verim** (kg/ağaç) eklendi.
  - Son kayıt artık tarihli: "Hasat · 40 gün önce". Son işlem ile son hasattan hangisi yeniyse o gösterilir.
  - 30 gün ve üzerinde **Kayıt bekliyor** rozeti.
  - Yalnızca aktif bahçede, Ana Sayfa'nın zaten yüklediği hava verisiyle anlık sıcaklık ve yağış; **ek ağ isteği yok**.
  - **+ İşlem** menüsü (Sulama, Gübreleme, İlaçlama, Hasat) mevcut işlem ekranlarını o bahçe için açar.
  - **Ana Sayfa'da göster**, Ana Sayfa'daki bahçe seçiciyle aynı ayarı yazar.

### Veri değişikliği
- `DatabaseHelper.getGardenSummaries()` sorgusuna iki sütun eklendi: `last_operation_date` ve `last_harvest_date`. İkisi de aktif sezon ve sahip filtresiyle sınırlı.
- Mevcut sütunlar, sıralama ve sahip filtresi değişmedi. Bu, `tests/gardens/check_summary_query.py` ile gerçek SQL'in SQLite üzerinde çalıştırılmasıyla doğrulandı.

### Yeni dosyalar ve testler
- `GardenListModel.java` (saf Java): tarih, son kayıt, kayıt bekliyor, verim, kg başına maliyet, sıralama.
- `tests/gardens/`: 36 model kontrolü ve 9 SQL kontrolü.

### Tasarımdan farklar
- Diğer bahçelerin kartında hava gösterilmez; her bahçe için ayrı istek atmamak için bilinçli bir tercih.
- Tasarımdaki `[kg]`, `[TL]` yer tutucuları uygulamada gerçek kayıtlardan hesaplanır; veri yoksa "—" görünür.

### Cihazda kontrol
- Sıralama düğmeleri ve kaydırma konumu.
- "Kayıt bekliyor" satırı.
- + İşlem menüsünden işlem ve hasat açma.
- Ana Sayfa'da göster → Ana Sayfa'ya dönünce doğru bahçe ve hava.
- Rapor bağlantısı.
- Hiç bahçe olmayan hesap.

## 9. Elek Kaydı — kabul edilen tasarım uygulandı

Kaynak: tasarım tuvalindeki "Elek Kaydı · yeni tasarım" ekranı.

### Ne değişti
- **`activity_elek.xml` yeniden yazıldı.**
  - Önceki 56 kimliğin tamamı korundu; 4 yeni kimlik eklendi: `elekDistributionBar`, `tvElekDistributionLegend`, `tvElekSaveHint`, `tvElekGardenLabel`.
  - Gizli fiyat paneli (`elekPricePanel`) ve gizli hesaplama alanları birebir korundu.
- **Kaldırılanlar:** "Emeğin bereketi" fotoğraflı afişi ve siyah zeminli kantar/para/trend ikonları.
- **Özet kartı:**
  - Büyük toplam tutar; değer varsa altın, sıfırsa soluk.
  - Toplam kg ve "ort." TL/kg.
  - Sınıf renkleriyle **dağılım şeridi** ve "Double %54 · 1 %45 · Yağlık %1" açıklaması; ekran okuyucu için tam cümle.
- **Sınıf satırları (iki katlı):**
  - Rozet, sınıf adı (sıkıştırılmış yazı tipi kaldırıldı) ve satır tutarı. Satır tutarı kilo girilmemişse soluk.
  - Boş gelen kg alanı ("0 kg" yalnızca ipucu).
  - Kesik çerçeveli **fiyat çipi** ("120 TL/kg"). Dokununca mevcut fiyat düzenleyici o sınıfın alanı seçili açılır.
- **Bahçe:**
  - Ana Sayfa alt menüsünden Elek açılırken aktif bahçe `garden_id` ile iletilir ve seçili gelir; etikette "Bahçe · aktif" yazar. Kullanıcı yine değiştirebilir.
  - Çift "▼" sorunu giderildi.
- **Kaydet:**
  - Düz uygulama yeşili, tam genişlik. Hiç kilo yokken pasif ve altında "Kaydetmek için en az bir sınıfa kilo gir." yazar.
  - Hazırken "Edremit · 13 Ekim 2026 olarak kaydedilecek." yazar.
  - "Varsayılan fiyatları düzenle" nötr çerçeveli; kırmızı çerçeve kalktı. "Geçmiş" hap stilinde.

### Korunanlar
- `calculateAll`, `updatePrices`, `loadSavedPrices`, `saveElekRecord`, bulut eşitleme, arşiv, tarih seçici, durum geri yükleme.
- Hesaplama mantığı değişmedi. `calculateAll` sonunda yalnızca gösterim yapan `refreshElekInsights()` çağrılıyor.
- Fiyat çiplerinin metnine "/kg" eklendi; değer aynı.

### Tasarımdan fark
- **Bölge fiyatı satırı eklenmedi.** Piyasa verisi farklı bir ekrandan, ağ üzerinden geliyor; uydurma değer göstermemek için ayrı bir adıma bırakıldı.
- **Fiyat düzenleme:** Fiyat satır içinde ayrı bir alan olarak değil, çipe dokununca açılan mevcut düzenleyiciyle değiştiriliyor. Böylece kayıtlı varsayılan fiyat mantığı bozulmuyor.

### Testler
- `tests/elek_insights` (yeni): 19 kontrol (paylar, yuvarlama, açıklama, kaydet ipucu, bahçe etiketi).
- `tests/elek_ui/check_form.py`: yeni görünüm kimlikleri ve afiş görselinin kaldırılması kontrol ediliyor. `refreshElekInsights` için boş yer tutucu eklendi; gerçek hesaplama kontrolleri aynen geçiyor.
- `tests/elek_popup/check_prices.py` değişmeden geçiyor.

### Cihazda kontrol
- Kg girince şerit, renkler ve kaydet durumunun güncellenmesi.
- Fiyat çipinden düzenleyicinin doğru alanla açılması.
- Kaydetme ve arşive düşmesi.
- Alt menüden açınca aktif bahçenin seçili gelmesi.
- Ekran döndürme ve geri dönüşte seçimlerin korunması.

## 10. Elek sekmesi — özet sayfası (alt menü görünür)

Sorun: Alt menüdeki "Elek" sekmesi doğrudan `ElekActivity` formunu açıyordu. Alt menü kayboluyor, diğer sekmelere dönmek için geri tuşu gerekiyordu.

Çözüm (kabul edilen "Elek sekmesi · özet sayfası" tasarımı):
- **Sekme davranışı:** Alt menüdeki Elek artık `showSection("ELEK")` ile `ProducerFeedActivity` içinde bir sekme açıyor. Alt menü ve üst çubuk görünür kalıyor; Elek sekmesi seçili görünüyor.
- **Sekme içeriği (`buildElekSection`):**
  - Başlık "2026 SEZONU · N KAYIT" / "Elek" ve "Geçmiş" hap butonu (`ElekArchiveActivity`).
  - Tam genişlikte **"+ Yeni elek kaydı"** butonu, `ElekActivity` formunu tam ekran açar. Bahçe filtresi seçiliyse o bahçe, değilse Ana Sayfa'nın aktif bahçesi önceden seçilir.
  - **Bahçe filtresi** (birden fazla bahçe varsa): "Tüm bahçeler" + bahçe adları; seçim `elek_overview_garden` ayarında saklanır.
  - **Sezon toplamı:** tutar (altın), kg, ortalama TL/kg, sınıf dağılım şeridi ve açıklaması, kayıt sayısı, en çok çıkan sınıf, son kayıt tarihi.
  - **Son kayıtlar** (en fazla 5): tarih, bahçe, kg, tutar, TL/kg ve kayda ait ince dağılım şeridi. Dokununca ve "Tümünü gör ›" ile Geçmiş açılır.
  - Kayıt yoksa açıklayıcı boş durum kartı.
- **Veri kaynağı:** Geçmiş ekranıyla birebir aynı `ElekRepository.getArchiveRecords(season, gardenId, owner)` sorgusu kullanılıyor; ayrı bir toplam hesabı yok.
- **Yenileme:** Form kaydedilip geri dönülünce mevcut `onResume → refreshCurrentSection` akışı sekmeyi yeniden çizer; yeni kayıt listenin başında görünür.
- **Form:** Form ekranı alt menüsüz kaldı (bilinçli). Asistanın "Elek aç" komutu da hâlâ formu açıyor.

Testler:
- `tests/elek_insights` 30 kontrole çıktı: en çok çıkan sınıf, tarih biçimleri, TL/kg, kayıt sayısı.
- `check_archive_query.py`: arşiv SQL'i Java'daki parçalardan birebir yeniden kuruluyor ve SQLite'ta çalıştırılıyor. Sahip ve sezon kapsamı, tarih sırası, ekran görüntüsündeki kaydın 98 kg / 4.910 TL toplamı, sınıf sütunları, bahçe filtresi ve özetin kayıtların toplamına eşitliği kontrol ediliyor (5 kontrol).
- Diğer tüm paketler önceki sonuçlarla aynı.

Cihazda kontrol:
- Alt menüden Elek'e geçiş ve diğer sekmelere dönüş.
- Yeni kayıt → kaydet → geri dönünce listenin güncellenmesi.
- Bahçe filtresi.
- Geçmiş / Tümünü gör.
- Kayıt olmayan sezon.

## 11. Elek geçmişi — koyu arşiv listesi + kayıt makbuzu

Kaynak: "Elek geçmişi · arşiv listesi" ve "Elek geçmişi · kayıt makbuzu" tasarımları.

### Arşiv listesi (`ElekArchiveActivity`, yeniden yazıldı)
- Kâğıt temalı başlık, damga ve el yazısı rakamlar kaldırıldı. Ekran koyu temaya geçti:
  - geri butonu, "Elek geçmişi" başlığı, "2026 ▾" yıl seçici;
  - bahçe filtre çipleri (yatay kaydırma Java'da kuruluyor);
  - sezon kartı: tutar (altın), kg, ort. TL/kg, sınıf dağılım şeridi ve açıklama;
  - ay başlıkları ("EKİM 2026");
  - kayıt satırları: gün kutusu (13 / SAL), bahçe, "98 kg · 50,1 TL/kg", tutar, kayda ait dağılım şeridi.
- Ondalık ayracı virgül, para birimi her yerde "TL", sınıf adları "1 Numara" (formla aynı).
- Elek sekmesinden açılırken seçili bahçe filtresi `garden_id` ile geliyor.
- Veri sorgusu değişmedi: `new ElekRepository(databaseHelper).getArchiveRecords(...)`.

### Makbuz (`ElekReceiptActivity`, yeni)
- Koyu zemin üzerinde krem "Elek makbuzu" fişi:
  - "ZEYTİN DEFTERİ" başlığı ve eğik "SEZON 2026" damgası;
  - bahçe ve tarih (gün adıyla);
  - Sınıf / Kg / Fiyat / Tutar tablosu (hizalı rakamlar) ve toplam satırı;
  - dağılım şeridi ve not alanı.
- **Paylaş:** düz metin (WhatsApp vb.); metin `ElekInsights.receiptShareText` ile üretiliyor ve test ediliyor.
- **Not ekle / düzenle:** `ElekRepository.updateElekRecordNote` (en fazla 300 karakter), ardından `ProductionCloudRepository.syncElekByLocalId`.
- **Kaydı sil:**
  - Onay penceresinden sonra `ElekRepository.deleteElekRecord` çağrılır. Bu yöntem, bahçe ve işlem silmeyle aynı şekilde bir silme işareti bırakır; işaret bir sonraki senkronda buluta gider. Sınıf satırları `ON DELETE CASCADE` ile silinir.
  - **Dikkat:** Bu iki depo yöntemi daha önce hiçbir ekranda kullanılmıyordu; cihazda ilk kez çalışacaklar.
- **Kullanıcı ayrımı:** Kaydın `owner_uid` değeri aktif kullanıcıyla eşleşmezse makbuz açılmaz.
- **Elek sekmesi:** "Son kayıtlar" satırları artık doğrudan ilgili makbuzu açıyor.

### Testler
- `tests/elek_archive/check_archive.py` **artık tamamen geçiyor**. Önceden eski bir yazım kalıbı yüzünden başarısızdı. Gerçek repository SQL'ini SQLite'ta çalıştırıp kullanıcı, sezon ve bahçe ayrımını, eski tarih biçimini, sıralamayı ve toplamları doğruluyor.
- `tests/elek_insights` 45 kontrole çıktı: paylaşım metni, gün kısaltmaları (Cuma ≠ Cumartesi) ve ay başlıkları eklendi.
- Python test paketlerinde başarısız sayısı 4'ten 3'e indi. Kalan 3 test eski ve bu işle ilgisiz.

### Bahçe şüphesi (Edremit / Girelli)
Kodda form açılışında seçili bahçenin kendiliğinden değiştiği bir yol bulunamadı. Bahçe yalnızca şu durumlarda değişiyor:
- tek bahçe varsa otomatik seçim;
- Elek sekmesinden gelen `garden_id`;
- ekran döndürmede durum geri yükleme;
- kullanıcının seçici ile değiştirmesi.

Cihazda tekrar görülürse adımlar ile birlikte bildirilmeli.

### Cihazda kontrol
- Geçmiş listesi: yıl değiştirme, bahçe filtresi, ay başlıkları.
- Makbuz açma, paylaşma, not ekleme ve not sonrası görünüm.
- Kayıt silme: silince listeden ve Elek sekmesinden düşmesi, diğer cihazda senkron sonrası kaybolması.

## 12. Zeytin Ligi — gizlilik eşiği + görselsiz tasarım

### 12.1 Gizlilik düzeltmesi (ayrı commit)
- **Sorun:** Ekran görüntüsünde 2 üreticili bölgelerde toplam kg ve ortalama TL/kg görünüyordu (ör. Edremit 456 kg · 56,89 TL/kg). İki üreticili bölgede biri kendi verisini çıkararak diğerinin kilosunu ve fiyatını tam bulabilir.
- **Neden:** İstemci, sunucunun `published` bayrağına güveniyordu. Repoda `ZEYTIN-LIGI-*-TEST-2.sql` (eşik 2) ve `*-PROD-5.sql` (eşik 5) var; canlı veritabanında TEST-2'nin çalıştığı **tahmin ediliyor** (canlı DB buradan görülemedi).
- **Uygulamada yapılan:**
  - Bölge dizini: `published && producer_count >= 5` değilse kg/fiyat/baskın elek tutulmuyor.
  - Bölge raporu: `enforceReportPrivacy` eşik altı bölgede tüm rakamları; eşik üstü bölgede bile 5'ten az üreticili elek satırlarını, trend pencerelerini ve grafik günlerini temizliyor.
  - "Tek üreticili fiyat ön izlemesi" kaldırıldı (o üreticinin fiyatını herkese açıyordu).
- **Senin yapman gereken:** Supabase'de PROD-5 SQL dosyalarını uygulamak. Uygulama artık 5 altını göstermese de verinin sunucudan hiç çıkmaması gerekir.

### 12.2 Görselsiz tasarım (tuvaldeki "Zeytin Ligi · görselsiz (önerilen)")
- **Başlık:** Fotoğraf yok; koyu yeşil geçiş, soluk kupa, altta yeşil→altın çizgi, tek açıklama satırı. Yükseklik 150 → 112dp.
- **"Senin sıran" şeridi:** Başlığın altında. Kupa sıra yokken gri/kesik çizgili, doğrulanmış sırada altın. Dokununca alttaki "Senin Sıran" kartına kayar.
- **Elek fiyatı + sezon** aynı satırda.
- **Durum satırı:** "4 bölge • Bölge rakamları 5 üreticiyle açılır" / "Top 20 yayında".
- **Bölge kartları (fotoğrafsız, 2,5 kart görünür):** Üst şeritte baş harf rozeti (aynı harfle başlayan bölgelerde iki harf: AY/AK) ve durum etiketi (EŞİK / ANONİM / TOP 20). Eşik altında yalnızca "2 / 5 üretici", ilerleme çubuğu, "3 üretici daha gerekli". Eşik üstünde fiyat büyük, altında kg ve baskın elek. En küçük yazı 12sp.
- **Top 20:** "ⓘ Puan nasıl hesaplanır?" açılır açıklaması (%50 kalite, %35 fiyat, %15 hacim — `functions/league/policy.js` ile aynı). Boşken "<Bölge> kupası henüz sahipsiz" + sunucunun gerçek kuralı (en az 4 sıralanabilir üretici). Doluyken ilk üç için kürsü (altın/gümüş/bronz), 4. ve sonrası tabloda; kendi satırın yeşil.
- **Korunanlar:** Lig | Piyasa sekmesi, sezon seçici, elek filtreleri, bölge kartına dokununca bölge raporu, "Diğer bölgeleri yükle", yerel önizleme / doğrulanmış sıra mantığı, kişisel raporlar bağlantısı, kaydırma konumu koruma.
- **Performans:** Her kartta yüklenen 2,3 MB'lık fotoğraf kalktı (drawable Ana Sayfa'da kullanılmaya devam ediyor).

### 12.3 Doğrulama
- `tests/league/check_privacy.py`: gerçek `enforceReportPrivacy` gövdesi çıkarılıp çalıştırılıyor — 18 kontrol + kaynak kontrolleri.
- `tests/league/LeagueBoardModelTest.java`: 34 kontrol.
- `tests/league/check_view.py`: 14 kaynak kontrolü.
- **Yeni:** Bu oturumda Android API 33 `android.jar` ile **tip denetimi** yapılabildi (Firebase/Glide kütüphaneleri olmadan). `ZeytinLeagueView`, `MarketCloudRepository`, `ZeytinLeagueRegionReportDialog` ve önceki turlarda değişen `ProducerFeedActivity`, `ElekActivity`, `ElekArchiveActivity`, `ElekReceiptActivity` dosyalarında Firebase/Glide dışında hata kalmadı (bir değişken çakışması bu denetimle yakalanıp düzeltildi). Bu tam bir `assembleDebug` değildir; APK üretilmedi, cihazda çalıştırılmadı.

### 12.4 Cihazda kontrol
- 2 üreticili bölgelerde kg/fiyatın artık "—" yerine görünmediği, kartta "2 / 5 üretici" yazdığı.
- Bölge raporu penceresinde eşik altı rakamların gizlendiği.
- Kart genişliği (ekranda ~2,5 kart), büyük yazı tipi ayarında başlık ve kartların taşmadığı.
- "Senin sıran" şeridine dokununca alttaki karta kayması.
- Top 20 yayımlandığında kürsü ve kendi satırının vurgusu.

## 13. Piyasaya anonim katkı artık izinli (varsayılan kapalı) + lig izleyicisi düzeltmesi

### 13.1 Sorun
- `localContributionEnabled()` yalnızca `canUseCloud()` döndürüyordu. Supabase bağlantısı hazır her kullanıcının fiyatlı tüm elek kayıtları, Lig/Piyasa ekranı açılınca **izin sorulmadan** Supabase'e gönderiliyordu.
- Geri çekme kodu (`withdrawAllTracked`) yazılmıştı ama hiçbir yerden çağrılmıyordu.
- KVKK ve Gizlilik metinleri (sürüm 1.0) ise "yalnızca paylaşımı açarsan, satış kayıtlarından" diyordu.

### 13.2 Yapılan (yayıncının "A" kararı)
- **İzin anahtarı:** Kullanıcıya özel `market_contribution_opt_in_<uid>`, varsayılan `0` (kapalı). Katkı yalnızca `canUseCloud() && isContributionOptedIn()` olduğunda gönderiliyor.
- **Kapatınca:** Bu cihazın izlediği tüm katkılar `market_withdraw` ile geri çekiliyor. Geri çekme başarısız olursa bir sonraki Lig/Piyasa açılışında yeniden deneniyor.
- **Eski otomatik katkılar:** İzin vermemiş kullanıcıda, önceki sürümün gönderdiği katkılar ilk Lig/Piyasa açılışında otomatik geri çekiliyor. Bu yalnızca bu cihazda izi tutulan katkılar için çalışır; uygulama yeniden kurulduysa iz kaybolmuştur.
- **Tekrar açınca:** Senkron izi (epoch) sıfırlandığı için kayıtlar baştan gönderiliyor.
- **Arayüz:**
  - Ayarlar → ÜRETİM → **Piyasaya Anonim Katkı** (Açık/Kapalı). Dokununca ne gönderildiğini, 5 üretici eşiğini ve kapatınca ne olacağını anlatan bir onay penceresi açılıyor.
  - Zeytin Ligi'nde izin kapalıyken "Bölge istatistiklerine anonim katkın kapalı · Aç" satırı görünüyor.
  - Kapalıyken bölge istatistiklerini görmek mümkün.
- **Hukuki metinler 1.1:** Gizlilik ve KVKK artık gerçek davranışı anlatıyor:
  - Bölge istatistikleri: elek kayıtları, Ayarlar'daki izinle, varsayılan kapalı, 5 üretici eşiği.
  - Zeytin Ligi sıralaması: satış kaydındaki piyasa paylaşımı onay kutusu (varsayılan işaretsiz), ad yerine sıra etiketi, en az 4 üretici.
  - Geri çekme ve silme talebi.
  - Metinler bir hukukçu tarafından incelenmedi.

### 13.3 Lig izleyicisi
`ZeytinLeagueRepository.watch` önbellekten gelen ilk olayı "bağlantı yok" sayıyordu. Bu yüzden ekran açılırken yayımlanmış sonuçları bir an yerel önizlemeye düşürüyor ve her şeyi iki kez indiriyordu. Artık önbellek olayları yok sayılıyor; gerçek bağlantı sorununu mevcut 15 saniyelik zaman aşımı ve hata dalları yakalıyor.

### 13.4 Doğrulama
- `tests/league/check_consent.py`: 15 kaynak kontrolü.
- `tests/auth`: yasal metin testleri 1.1'e güncellendi, 69 kontrol geçiyor.
- Değişen dosyalar `android.jar` ile tip denetiminden geçti (Firebase hariç).
- Canlı Supabase'de `market_withdraw` çağrısı denenmedi.

### 13.5 Cihazda kontrol
- Ayarlar'da satırın "Kapalı" görünmesi; açıp kapatınca değerin ve Toast mesajının değişmesi.
- İzin kapalıyken Lig/Piyasa açıldığında Supabase'e yeni katkı gitmemesi (Supabase panelinde `market_contributions` satır sayısı).
- Kapatınca önceki katkıların panelde silinmesi.
- Ekran açılırken artık "Bölgesel bağlantı kullanılamıyor" yazısının görünmemesi.

### 13.6 Güncelleme — katkı varsayılan AÇIK (yayıncı kararı, 14.10.2026)
- **Neden:** Yayıncı, kullanıcının ayarı hemen bulamayabileceği için katkının başlangıçta açık olmasını istedi.
- **Kodda:** `isContributionOptedIn()` artık ayar yoksa `true` döndürüyor; yalnızca kullanıcı "Kapat" derse (`0`) katkı duruyor ve geri çekme çalışıyor. Önceki sürümün gönderdiği katkılar artık **geri çekilmiyor**, çünkü ayar varsayılan olarak açık sayılıyor.
- **Şeffaflık:** Zeytin Ligi ilk açıldığında bir kez "Anonim katkın açık" kartı gösteriliyor. Kart neyin paylaşıldığını, 5 üretici eşiğini ve Ayarlar'dan kapatılabileceğini anlatıyor; "Tamam" ve "Ayarı değiştir" düğmeleri var. Kart kullanıcıya özel, bir kez gösteriliyor.
- **Hukuki metinler (1.1):** "varsayılan olarak açıktır, ilk kullanımda bildirilir, Ayarlar'dan kapatılabilir" şeklinde güncellendi. Hukuki dayanak maddesi (açık rıza / meşru menfaat) metinde **adlandırılmadı**. Varsayılan açık bir seçenek KVKK anlamında açık rıza sayılmayabilir; dayanağın yayıncı veya bir hukukçu tarafından belirlenmesi gerekir.
- **Cihazda kontrol (13.5'in yerine):**
  - Yeni kullanıcıda Ayarlar satırının "Açık" görünmesi.
  - Lig'de bilgilendirme kartının bir kez çıkması.
  - Kapatınca panelde katkıların silinmesi.
  - Kapalıyken Lig'de "katkın kapalı · Aç" satırının görünmesi.

## 14. Ana Sayfa · Zeytin Ligi kartı (tuvaldeki "Ana Sayfa · Zeytin Ligi kartı (4 hal)")

### Yerleşim ve davranış
- Kart, Ana Sayfa'da hava kartının altında, "Bugün yapılacaklar"ın üstünde duruyor.
- Veri gelene kadar görünmüyor. Bölge bilinmiyorsa hiç gösterilmiyor.
- Dokununca Zeytin Ligi sekmesi açılıyor.
- Basılı tutunca "Gizle" seçeneği çıkıyor. Bu ayar kullanıcıya özel. Gizlenen kart, Zeytin Ligi ekranının altındaki "Ana Sayfa'daki Zeytin Ligi kartını yeniden göster" bağlantısıyla geri açılıyor.

### Dört hal (`HomeLeagueModel`, saf sınıf)
1. **Kupa açılmadı:** "<İlçe> kupası sahipsiz · Yarış bölgede 4 üretici sıralamaya girince başlar".
   - Bölgedeki üretici sayısı biliniyorsa ilerleme çubuğu gösteriliyor.
   - **Davet et** düğmesi düz metin paylaşıyor; uydurma bağlantı eklenmiyor.
2. **Sıralamada:** "<İlçe>'de N. sıradasın".
   - İlk üçteyse alt satır "Kürsüdesin · liderliğe X puan".
   - Daha gerideyse "Kürsüye X puan kaldı".
   - Türkçe bulunma eki doğru üretiliyor: Edremit'te, Alaşehir'de, Ayvalık'ta.
3. **Lider:** Altın kupa ve "<İlçe> kupası şu an sende".
4. **Şart eksik:** Yarış başlamış ama kullanıcı listede değil. "✓/· En az 100 kg" ve "· Paylaşılan satış" etiketleri gösteriliyor.

### Kurallar
- Yalnızca kullanıcının kendi sırası, puanı ve bölgesinin Top 20 puanları okunuyor. Başka üreticinin kilosu ya da tutarı karta hiç girmiyor.
- Sıra yalnızca 24 saat içinde yayımlanmış sunucu sonucundan alınıyor. Telefondaki ön puan sıra gibi gösterilmiyor.
- Firestore okumaları 10 dakika önbellekleniyor, Ana Sayfa'nın her açılışında yeniden indirilmiyor. Geç gelen eski cevaplar yok sayılıyor.

### Bölge kartları
Sabit 150dp yükseklik kaldırıldı. Kartlar satırdaki en uzun karta eşitleniyor; eşik altındaki kartların altında artık boşluk kalmıyor.

### Doğrulama
- `tests/home_league/run.sh`: 34 kontrol.
- `tests/home_league/check_wiring.py`: 11 kaynak kontrolü.
- Ana Sayfa (65) ve lig testleri geçiyor.
- `ProducerFeedActivity`, `ZeytinLeagueView` ve `HomeLeagueModel` `android.jar` ile tip denetiminden geçti (Firebase ve Glide hariç).
- APK derlenmedi.

### Cihazda kontrol
- Kartın bugünkü durumda "Alaşehir kupası sahipsiz" olarak çıkması.
- Davet paylaşım penceresi.
- Basılı tutup gizleme ve Lig ekranından geri açma.
- Bölge kartlarının eşit yükseklikte olması.

## 15. Ana Sayfa "atlama" düzeltmesi

Video gelmedi; sebep koddan bulundu. Ana Sayfa'da içeriği sonradan aşağı iten veya kaydırma konumunu bozan dört kaynak vardı:

1. **Hava kartı büyüyordu.** Kart tek satırlık "hava tahmini hazırlanıyor…" yazısıyla açılıyor, tahmin gelince 3 satır ve yağış çubuklarıyla yaklaşık 200dp büyüyordu. Altındaki her şey aşağı kayıyordu.
   → Artık yükleme sırasında gerçek satırlarla **aynı yükseklikte** yer tutucular gösteriliyor: 3 satır ve 5 boş yağış sütunu. Hata durumunda yağış sütunları gizleniyor.
2. **Zeytin Ligi kartı sonradan beliriyordu (bu turdaki yeni kart).** Kart, ağ cevabı gelince görünür olup içeriği aşağı itiyordu. Ayrıca Ana Sayfa her yeniden kurulduğunda yeni bir istek başlatıyor, öncekini iptal ediyordu.
   → Kart artık sayfa çizilmeden **önce** çiziliyor. Sırasıyla bellekteki sonuç, kayıtlı son durum (24 saatten eskiyse sıra bilgisi atılıyor) ya da yerel arşiv kullanılıyor.
   → Ağ sonucu yalnızca içerik değiştiyse kartı yerinde güncelliyor.
   → Sayfa kaç kez yeniden kurulursa kurulsun tek bir istek yürüyor.
3. **Kaydırma konumu bir kare kaçıyordu.** Ana Sayfa onResume'da, gönderi senkronundan sonra ve özel veri senkronundan sonra baştan kuruluyor. Kaydırma konumu `post()` ile geri yüklendiği için bir kare sayfanın en üstü görünüyordu.
   → Konum artık ilk çizimden **önce** geri yükleniyor (`OnPreDrawListener`).
4. **Davet et → paylaş penceresini kapatma (yayıncının en belirgin bulduğu an):** Pencere kapanınca `onResume` çalışıyor ve Ana Sayfa baştan kuruluyordu. Kaydırma konumu bir kare kaçıyor, lig kartı yeniden yükleniyordu.
   → Davet penceresinden dönüşte Ana Sayfa artık yeniden kurulmuyor (`skipNextHomeRefresh`).
   → Senkron geri çağrıları yine yenileyebilir; o durumda 2. ve 3. maddelerdeki düzeltmeler kaymayı önlüyor.

Bunların toplamı, uygulamaya dönüşte ve ilk açılışta görülen "zıplama" hissini veriyordu.

### Doğrulama
- `tests/home_league/run.sh`: 43 kontrol (kayıtlı durum kodlama, 24 saat kuralı, imza karşılaştırması eklendi).
- `check_wiring.py`: 16 kontrol.
- `tests/home`: 65 kontrol.
- `ProducerFeedActivity` tip denetiminden geçti (Firebase ve Glide hariç).

### Cihazda kontrol
- Uygulamayı açınca ve başka ekrandan Ana Sayfa'ya dönünce sayfanın kaymaması.
- Hava kartının yer tutucudan gerçek veriye geçerken boyunun değişmemesi.
- Lig kartının sayfa açılırken hazır görünmesi.

## 16. Sezon Raporum (Zeytin Ligi → "Kişisel raporlar ve işlem geçmişi")

### Yaklaşım: temiz yeniden yazım
- Yeni ekran ayrı dosyada: `SeasonReportView` (görünüm) ve `SeasonReportModel` (kurallar, Android'siz, testli).
- `ProducerFeedActivity` içindeki eski `buildLegacyReportsSection`, `reportBlock` ve `buildGardenPerformanceBlock` **silindi**. Dosya 9.218 satırdan 8.804 satıra indi.
- Veri katmanına dokunulmadı. Mevcut sezon ve sahip filtreli `DatabaseHelper` toplamları aynen kullanılıyor. Elek toplamı, arşiv ve ligin de kullandığı ortak `ElekRepository.getArchiveRecords` sorgusundan geliyor.

### Düzeltilen hatalar
- **Başlık:** "Zeytin Ligi" yazıyordu; artık "Sezon Raporum · yalnızca senin kayıtların".
- **Geri dönüş:** Geri oku eklendi. Android geri tuşu artık uygulamadan çıkmıyor, Zeytin Ligi'ne dönüyor.
- **Butonlar:** İki butonun hizasızlığı, "İşlem geçmişi" butonuna yanlışlıkla verilmiş 14dp alt boşluktan kaynaklanıyordu. Butonların yerini artık liste satırları alıyor.
- **Satış sonrası dönüş:** "Satış kaydı ekle" ile satış girip geri dönünce rapor açık kalıyor ve rakamlar güncelleniyor (`refreshCurrentSection`). Sezon değiştirince de rapor açık kalıyor.

### İçerik
- **Özet:** Masraf, hasat, elek (tahmini tutarıyla) ve satış dört kutuda. Satış kaydı yoksa satış kutusu altın renkte.
- **"Sezon nerede?":** Hasat → elek → satış kilo çubukları.
  - Elek var ama satış yoksa "Eksik halka: satış kaydı" uyarısı ve "+ Satış kaydı ekle" butonu çıkıyor.
  - Hiç kayıt yoksa "Elek kaydı ekle" önerisi çıkıyor.
  - Elek hasattan fazlaysa nedenini açıklayan not gösteriliyor.
- **Para:** Masraf, satış geliri, tahsil edilen / açık alacak ve net sonuç. Satış yokken net sonuç kırmızı değil gri, "satış girilmediği için eksik" notuyla.
- **Salamura:** Giren, hazır, maliyet ve aktif parti.
- **Bahçelere göre:** Her bahçe için elek kg, hasat kg ve işlem masrafı.
- **Ayrıntılı rapor ve İşlem geçmişi:** Liste satırları olarak duruyor.

### Tasarımdan bilinçli sapma
Tuvaldeki **bahçe filtresi uygulanmadı**. Mevcut veritabanında bahçe bazında satış ve tahsilat toplamı yok. Filtreyi bu haliyle eklemek bazı rakamların filtreye uymadığı yanıltıcı bir ekran üretirdi. Bahçe kırılımı "Bahçelere göre" kartında. Filtre istenirse bahçe bazlı sorgular ayrıca eklenmeli.

### Doğrulama
- `tests/season_report/run.sh`: 31 kontrol.
- `tests/season_report/check_wiring.py`: 12 kontrol.
- Ana Sayfa (65), lig kartı (43) ve bağlantı (17) testleri geçiyor.
- Yeni dosyalar ve `ProducerFeedActivity` tip denetiminden geçti (Firebase ve Glide hariç). APK derlenmedi.

### Cihazda kontrol
- Lig ekranında "Kişisel raporlar ve işlem geçmişi"ne dokununca yeni ekranın açılması.
- Geri oku ve Android geri tuşunun Lig'e döndürmesi.
- Satış ekleyip dönünce raporun güncel olması.
- Bahçe satırlarının ve salamura rakamlarının doğruluğu.

## 17. Elekten satışa (yayıncı kararı: anahtar her zaman varsayılan AÇIK)

### Akış
- **Elek formu:** Kaydet düğmesinin üstünde **"Bu zeytini sattım"** anahtarı var. Form her açıldığında anahtar açık başlıyor.
  - Açıkken "Parayı aldın mı?" sorusu çıkıyor (Tamamı / Bir kısmı / Henüz değil). "Bir kısmı" seçilince alınan tutar soruluyor. Alıcı adı isteğe bağlı.
  - Düğme "Kaydet ve satışı oluştur" oluyor, altında "Elek kaydı + 4.910 TL satış · bahçe · tarih" ipucu çıkıyor.
  - Elek kaydı kaydedilince aynı sınıf, kilo ve fiyatlarla satış oluşturuluyor.
  - Satış oluşturulamazsa elek kaydı yine de kalıyor; kullanıcıya makbuzdan tekrar dönüştürebileceği söyleniyor.
- **Elek makbuzu:** Satışı olmayan elekte **"Satışa dönüştür"** düğmesi var; tahsilat bir pencerede soruluyor. Dönüştürülmüş elekte "✓ Satışa dönüştürüldü · 4.910 TL · tahsilat: …" satırı görünüyor.
- **Sezon Raporum:** "Eksik halka" kartının düğmesi artık **"Elekleri satışa dönüştür"**. Satışı olmayan elekler çoklu seçimli bir listede açılıyor ve seçilenler toplu dönüştürülüyor. Toplu dönüşümde tahsilat "henüz değil" olarak kaydediliyor. Önceki "+ Satış kaydı ekle" düğmesi ve kullanılmaz hale gelen `addSale` eylemi kaldırıldı.

### Kurallar
- Satış, mevcut `SaleRepository.addSaleWithGrades` ile yazılıyor. Kalan tutar için açık alacak kaydı da aynı kurallarla oluşuyor.
- Tarih elekten dönüştürülüyor (ISO → dd.MM.yyyy). Bölge ilçe, yoksa il oluyor. Çeşit bahçeden geliyor. Not: "Elek kaydından oluşturuldu".
- **Piyasa paylaşımı kapalı başlıyor.** Tahmini elek fiyatları kendiliğinden Piyasa ve Lig verisine karışmıyor; kullanıcı satış kaydından açabilir.
- **Çift dönüşüm engeli:** Satışa `source_elek_id` (yerel) ve `source_elek_cloud_id` (bulut) yazılıyor.
  - Kontrol, eklemeden önce ve aynı veritabanı işleminin (transaction) içinde yapılıyor.
  - Buluttan geri yüklenen satışlar da bulut kimliğiyle tanınıyor.
  - Satış silinirse elek yeniden dönüştürülebiliyor.
- **Veritabanı 19 → 20:** İki sütun ve iki indeks ekleniyor. Ekleme idempotent (`addColumnIfMissing`); hem yeni kurulumda hem yükseltmede çalışıyor.

### Bilinen sınırlar
- Satış oluşturulduktan sonra elek kaydı düzenlenirse satış kendiliğinden güncellenmiyor; satış kaydından düzenlenmesi gerekiyor.
- Nakliye, kesinti ve vade elek formunda sorulmuyor; satış kaydından düzenleniyor.

### Doğrulama
- `tests/elek_sale/run.sh`: 26 model kontrolü ve 16 kontrol (şema, bulut alanı, işlem sırası, piyasa paylaşımının kapalı başlaması). Ayrıca bağlantı sorgusu kaynaktan çıkarılıp gerçek SQLite'ta çalıştırılıyor: aynı cihaz, buluttan gelen, başka hesap ve elle girilen satış durumları.
- Elek formu, fiyat penceresi, arşiv, Sezon Raporum (31 + 13), Ana Sayfa ve lig testleri geçiyor.
- `tests/league/check_view.py` içindeki eski bir kontrol, 13.6'da eklenen bilgilendirme kartını hesaba katmıyordu ve başarısızdı. Bu turda düzeltildi.
- Değişen dosyalar tip denetiminden geçti (Firebase ve Glide hariç). APK derlenmedi.

### Cihazda kontrol
1. Elek kaydedince Satış kutusunun ve Net sonucun güncellenmesi.
2. "Henüz değil" seçilince açık alacak oluşması.
3. Makbuzdan dönüştürme.
4. Rapordan toplu dönüştürme.
5. Aynı eleğin ikinci kez dönüştürülememesi.
6. Uygulama güncellemesinde mevcut verinin korunması (veritabanı 20'ye geçiş).

## 18. Ana Sayfa alt bölümü (tuvalsiz, doğrudan uygulandı)

### Neler değişti
- **"Piyasa Özeti" → "Senin elek fiyatların".**
  - Eski kartlar bölge piyasası gibi görünüyordu, oysa yalnızca kullanıcının kendi elek kayıtlarını bahçe ilçesine göre gösteriyordu. Ayrıca elek kayıtlarına "satış" diyordu.
  - Yeni kart, kilosu en yüksek iki sınıfın kendi ortalamasını gösteriyor. Örnek: "2 No ortalaman · 60 TL/kg · 11 elek kaydı · 235 kg".
  - Altında "Yalnızca senin elek kayıtların; bölge piyasası değildir" notu var. "Bölge fiyatları ›" bağlantısı Piyasa sekmesini açıyor.
- **Hızlı işlemler:** Emojili Sulama · Gübreleme · İlaçlama · Hasat kutularının yerini çizgi ikonlu **İşlem ekle · Hasat · Elek · Satış** kutuları aldı.
  - "İşlem ekle", NewOperationActivity'deki türlerden birini seçtiriyor.
  - **Eski hata:** Eski "Gübreleme" kutusu `operation_type=Gübreleme` gönderiyordu. İşlem ekranının listesinde bu tür "Gübre" olarak geçtiği için tür hiç seçili gelmiyordu. Yeni seçici, işlem ekranının listesindeki türleri birebir kullanıyor.
- **Bu Sezon:**
  - Kartta Hasat · Elek · Masraf · Satış var; Elek yeni eklendi.
  - Masraf artık kırmızı değil, nötr renkte. Satış yoksa altın renkte görünüyor.
  - Karta ya da "Sezon Raporum ›" bağlantısına dokununca Sezon Raporum açılıyor.
- **Başlık stili:** Tüm bölüm başlıkları "ASİSTANA SOR" ile aynı küçük, büyük harfli üst başlık (overline) stiline geçti.
- **Sıra:** Asistan → Hızlı işlem → Bu Sezon → Senin elek fiyatların. Zeytin Ligi kartı, daha önce kabul edilen tasarımdaki yerinde bırakıldı: hava kartının altı, "Bugün yapılacaklar"ın üstü.

### Temizlik
- Yeni bölüm ayrı dosyalarda: `HomeLowerSections` (görünüm) ve `HomeSummaryModel` (kurallar, testli).
- `ProducerFeedActivity` içinden eski kod silindi: `quickButton`, `homeQuickCard`, `homeQuickCardWithLeft`, `homeMiniMetric`, `loadHomeMarketSnapshots`, `buildHomeMarketCard`, `HomeMarketSnapshot`.
- Dosya 8.461 satırdan 7.858 satıra indi. Bunun bir kısmı önceki Elek ve rapor işlerinden geliyor.

### Doğrulama
- `tests/home_lower/run.sh`: 15 model kontrolü ve 10 kaynak kontrolü. Kaynak kontrolleri işlem türlerinin işlem ekranıyla birebir eşleştiğini de doğruluyor.
- Ana Sayfa, lig, Sezon Raporum ve elek→satış testleri geçiyor.
- Değişen dosyalar tip denetiminden geçti (Firebase ve Glide hariç). APK derlenmedi.

## 19. Aşama 1 — eski ekranlarda görünüm uyumu (3 commit)

Kural: ekranların **düzenine, alanlarına, akışlarına ve kayıt mantığına dokunulmadı**. Yalnızca görünüm değişti.

### 1/3 — Ortak çizimler ve renkler (`e4afb69`)
- **XML ekranlarının ortak arka plan dosyaları (drawable):**
  - `bg_card`, `bg_market` ve `bg_soft_circle` gradyandan düz renge geçti; köşe yarıçapı 20dp.
  - `bg_green_button` düz parlak yeşil, 26dp köşe ve koyu yazı oldu. Bu düğmenin kullanıldığı 9 yerin yazı rengi güncellendi.
  - Etkilenen ekranlar: Bahçe ekle, Bahçe detayı, İşlem ekle, Hasat, Satış, Gönderi yaz, Konum seçici.
- **Eski koyu zeytin yeşili vurgular → uygulama paleti.** Bu tonlar (#416A25, #2F6B3F, #3C7A4B, #4A7441, #52713B…) koyu zeminde okunmuyordu.
  - Değişen ekranlar: Rapor, Salamura, Profil, eski Piyasa, İşlem geçmişi, tepkiler, fiyat grafiği.
  - Yeşil düğmelerdeki beyaz yazı koyu yazıya çevrildi.
- **Ayarlar ve Hatırlatmalar:** Kendilerine özgü tonların yerine uygulama paleti kullanılıyor.
- **Konum seçici:** Yeşil başlık çubuğu ve **beyaz liste satırları** koyu palete çekildi.
- **Piyasa sekmesi başlığı:** Fotoğrafın yerine Zeytin Ligi ile aynı yeşil geçiş geldi.
- **ZdNav silindi.** Eski Piyasa ekranında ve İşlem geçmişinde hâlâ görünen, metin simgeli eski 5 öğeli alt menüydü.

### 2/3 — Emojiler (`e477676`)
- Başlık, düğme, ipucu, menü ve bildirimlerdeki süs emojileri kaldırıldı. Örnek: "🌿  Bahçemi Kaydet" → "Bahçemi Kaydet". "✅" yerine "✓" kullanıldı.
- Durum işaretleri korundu: ✓ ⚠ ve oklar.
- Profil fotoğrafı menüsünde seçim, menü metinleriyle karşılaştırılarak yapılıyordu (`startsWith`). Bu karşılaştırma yeni metinlere göre güncellendi.
- **Bilerek bırakılanlar (Aşama 2'de çizgi ikonlarla değişecek):**
  - Liste ve avatar simgesi olarak kullanılan tek emojiler: İşlem geçmişi ve Bahçe detayındaki işlem türü simgeleri, profil ve bahçe yer tutucuları, Bahçe ekle ve tanıtım ekranlarındaki büyük simgeler.
  - Hava simgeleri ve Meydan tepkileri.
  - Ana Sayfa "Bugün" kartındaki öneri başlıkları. Bu kart daha önce kabul edildi.

### 3/3 — Ölü kod (`46b760d`)
- `GardenHomeManager` (1.901 satır) ve `activity_main.xml` silindi.
- `GardensActivity`, `SubscriptionActivity` ve `SubscriptionManager` silindi; manifest kayıtları da kaldırıldı. Bu ekranlara hiçbir yerden geçiş yoktu.
- Artık kullanılmayan `ui_*` çizim dosyaları ve `onboarding_garden_1.png` (3 MB) silindi.
- Java kodu toplamı 105.481 satırdan **100.718** satıra indi.

### Doğrulama
- `android.jar` tip denetiminde yeni hata yok. Kalan hatalar yalnızca burada bulunmayan Firebase ve Glide kütüphanelerinden.
- Python test paketlerinde sonuç değişmedi: eski 3 hata dışında hepsi geçiyor. Java test paketlerinin hepsi geçiyor.
- **APK derlenmedi.** Ekranların görünümü telefonda kontrol edilmeli.

### Cihazda kontrol
- Bahçe ekle, Bahçe detayı, İşlem ekle, Hasat ve Satış: kartlar düz mü, ana düğme parlak yeşil ve yazısı okunuyor mu.
- Konum seçici: liste satırları koyu ve okunaklı mı.
- Rapor, Salamura, Profil ve İşlem geçmişi: yeşil vurgular okunuyor mu.
- İşlem geçmişi ve eski Piyasa ekranında eski alt menü artık görünmemeli; geri tuşu çalışmalı.
- Piyasa sekmesi başlığı.

## 20. Aşama 2 — en çok kullanılan dört ekran (4 commit)

Tuvaldeki onaylanan tasarımlar ("Aşama 2 · …" panoları) uygulandı. Her ekranda eski düzen ve eski kod aynı commit'te silindi.

### Bahçe detayı
- Başlıkta bahçe adı, "çeşit · mahalle / ilçe / il" ve Düzenle düğmesi var. Altında ağaç, yaş, dekar ve sulama kutuları duruyor.
- Beş hızlı eylem var: İşlem, Hasat, Elek, Satış, Not. Hepsi bahçe kimliğiyle açılıyor.
- "Bu Sezon" kartında Masraf, Hasat, **Elek** (yeni), **Satış** (yeni), ağaç başına verim ve kg başına maliyet var. Değerler Bahçe Karnesi ile aynı kaynaktan geliyor.
- Son işlemin üzerinden 30 gün veya daha fazla geçtiyse sarı bir uyarı çıkıyor. En yeni tarih, sezonun bütün işlemleri taranarak bulunuyor; liste kayıt sırasına göre geldiği için ilk satır her zaman en yeni tarih olmuyor.
- Son işlemler emoji yerine çizgi ikonlarla gösteriliyor. Notlar ayrı kutularda.
- `GardenDetailActivity` 2.277 satırdan 450 satıra indi. Davranış aynı: sahip kontrolü, not penceresi ve taslağı, sezon/bahçe yarış koruması.

### İşlem ekle
- Tür, açılır liste yerine 10 ikonlu kutudan seçiliyor (4 sütun). Seçili kutu yeşil çerçeveli.
- Ayrıntı kartının başlığı, miktar etiketi, birimleri ve ipucu seçilen türe göre değişiyor. Bu kurallar 250 satırlık if-zincirinden saf `OperationKinds.form()` sınıfına taşındı; birimler ve metinler aynı kaldı.
- Kaydet düğmesi altta sabit; üstünde "Bahçe · Tür · Tarih" satırı var. Klavye açılınca düğme klavyenin üstüne çıkıyor (`adjustResize`).
- Kayıt yolu değişmedi. Tür, tarih ve birim artık ekran döndürülünce kaybolmuyor.
- Ana sayfadaki işlem seçici de aynı listeyi kullanıyor (tek kaynak: `OperationKinds.TYPES`).

### Hasat ve Satış
- İki form aynı boy satırlarını kullanıyor (`include_grade_rows`: Boy | Kilo | Birim fiyat | Tutar).
- **Hata düzeltmesi:** Satış formu "2.400" yazılınca 2,4 TL okuyordu. Artık hasat formundaki Türkçe sayı okuyucusunu kullanıyor ve 2400 okuyor. Bu okuyucu saf `GradeFormModel.parse` sınıfına taşındı ve test edildi.
- Hasat: özet kartı (toplam kilo, hasat masrafı, tahmini değer) ve katlanabilir masraf kartı var. Kayıt yine tek işlemde yapılıyor (hasat + boylar + "Hasat" hareketi). Son fiyat önerisi korundu.
- Satış: "Gerçek satış | Fiyat teklifi" seçimi var. Ödeme için üç seçenek var: **Tamamı alındı / Bir kısmı / Henüz değil** (varsayılan "Henüz değil"; eski boş tahsilat ile aynı). "Bir kısmı" seçilince tutar girmek zorunlu. Tamamı alınan satışta vade kaydedilmiyor.
- Piyasa izni artık anahtar (Switch) biçiminde. **Varsayılan kapalı** ve metni aynı. Bütün doğrulamalar, alıcı oluşturma ve geri alma, eşitleme korundu.

### Emoji ikonlar
- İşlem geçmişi, bahçe detayı ve profilde emoji kalmadı.
- Profilde kapak, avatar yer tutucusu, kamera rozeti, kart ikonları ve "Hakkında" satırları çizgi ikonlarla değişti. Gönderi etiketleri " · " ile ayrılıyor.
- Uygulamanın diğer yerlerinde (Meydan tepkileri, hava durumu, giriş ekranı vb.) hâlâ emoji var. Bunlar Aşama 2 kapsamında değildi.

### Silinenler
- Silinen kaynaklar: `ic_water`, `ic_profile_line`, `ic_search_line` (zaten kullanılmıyordu), `bg_market`.
- Ekranların eski düzenleri ve emoji ikon metotları silindi.
- Java toplamı: **97206 satır**.

### Doğrulama
- `tests/stage2/run.sh`: GardenDetailModelTest (75), GradeFormModelTest (34), check_wiring (215) geçti.
- Ana sayfa, lig, sezon raporu, elek→satış, bahçe listesi, bahçe detayı SQLite ve bahçe analitiği takımları da geçti.
- `android.jar` tip denetiminde yeni hata yok. 463 hatanın tamamı Firebase, Glide ve androidx kaynaklı; bu sayı önceki taban değeriyle aynı.
- **APK derlenmedi ve telefonda denenmedi.**

### Cihazda kontrol
- Bahçe detayı: beş eylem doğru ekranı doğru bahçeyle açıyor mu? Elek ve Satış rakamları Bahçe Karnesi ile aynı mı? 30 günden eski işlemi olan bahçede uyarı çıkıyor mu?
- İşlem ekle: kutu seçilince birimler değişiyor mu? Ana sayfadan "Gübre" ile gelince doğru kutu seçili mi? Klavye açıkken Kaydet görünüyor mu?
- Hasat: boy satırları dar ekranda sığıyor mu? Masraf kartı katlanıyor mu? Kayıttan sonra hasat, boylar ve işlem hareketi oluşuyor mu?
- Satış: "2.400" fiyatı 2400 olarak hesaplanıyor mu? Ödeme seçenekleri kalan alacağı doğru gösteriyor mu? Fiyat teklifinde alıcı, ödeme ve izin kartları gizleniyor mu?
- Profil: misafir oturumunda avatar yerinde zeytin ikonu, kamera rozeti ve "Hakkında" ikonları görünüyor mu?

## 21. Aşama 3 — eski ekran kalıntıları ve dört ekran daha (6 commit)

### 1. adım: yeni ekranlardaki kalıntılar
- Elek formu, Elek geçmişi, Elek makbuzu ve Sezon Raporum'daki metinle çizilmiş "‹" geri düğmesi çizgi ok ikonuyla değiştirildi. Makbuz bildirimindeki "✓" kaldırıldı.
- Makbuzun krem kâğıt renkleri ve içindeki "✓" onaylı ElekMakbuz tasarımının parçası; kalıntı değil, değiştirilmedi.

### Bahçe ekle / düzenle (tek form)
- Ad, tek satır konum (seçici), çeşit / ağaç / yaş / alan, 2 sütunlu sulama kutuları, küçük resimli fotoğraf satırı ve not alanı var. Kaydet altta sabit.
- `AddGardenActivity` 2.840 satırdan 625 satıra indi. Üç modu var: ilk bahçe, yeni bahçe, düzenleme. Kurallar saf `GardenFormModel` sınıfında.
- `GardenEditActivity` silindi; Bahçe detayındaki "Düzenle" bu formu açıyor.
  - Eski kayıtlardaki serbest metin konum, değiştirilmediği sürece kabul ediliyor.
  - Listede olmayan eski sulama değeri ek seçenek olarak korunuyor.
- Geçersiz sayı artık her modda hata gösteriyor. Eskiden ekleme ekranı sessizce 0 kaydediyordu.
- Konum seçicideki eski "sokak" uyumluluk kodu silindi.

### Bahçe Karnesi
- `GardenAnalyticsActivity` 2.064 satırdan 547 satıra indi. Ekranda şunlar var:
  - Bahçe seçim düğmeleri.
  - "Kayıtlı net sonuç" kartı.
  - Verim / maliyet ve satış ızgaraları; tahsilat çubuğu.
  - Boy dağılımı çubukları.
  - Hasat–elek uyum kartı.
  - Renkli ↑/↓ karşılaştırma rozetleri; masraf ve maliyet artışı kırmızı.
- Veri yolu, korumalar, bütün ölçüler, 7 karşılaştırma satırı, boy karşılaştırmaları ve açıklama notları korundu.
- Sezon etiketi tıklanmıyor: ekran her zaman aktif sezonu gösteriyor.

### İşlem geçmişi
- `OperationHistoryActivity` 1.958 satırdan 484 satıra indi. Ekranda şunlar var:
  - Sayı ve masraf kutuları.
  - Bahçe seçici ve ikonlu tür düğmeleri.
  - Aylara göre gruplanmış liste; detay alttan açılan panelde.
  - Altta sabit "+ İşlem ekle".
- `GardenOperationHistoryActivity` silindi; Bahçe detayındaki "Tümü ›" bu ekranı o bahçe seçili açıyor.
- **Hata düzeltmesi:** "Sinek Tuzağı" filtresi kayıtlardaki "Sinek tuzağı" ile hiç eşleşmiyordu; eski "Gübreleme" kayıtları da "Gübre" filtresine düşmüyordu. Filtre artık Türkçe büyük/küçük harfe duyarlı karşılaştırıyor.
- Liste artık işlem tarihine göre sıralanıyor. Eski "yyyy-MM-dd" tarihler de okunuyor; bu düzeltme Bahçe detayındaki "son işlem" uyarısına da yansıdı.
- `getOperationsFiltered` sorgusuna, bahçe geçmişindeki kayıt sahibi kuralı eklendi: başka hesaba ait işlem gösterilmiyor. SQLite testi 61 kontrolle bunu ve "iki ekran aynı kayıtları gösterir" şartını doğruluyor.

### Profil
- Görünüm kodu yenilendi; dosya 4.985 satırdan 3.247 satıra indi. Ekranda şunlar var:
  - Tek bilgi kartı: fotoğraf, ad, konum, "Profili düzenle" (misafirde "Giriş yap"), "+ Gönderi" ve üç sayı.
  - Bölmeli sekmeler.
  - Çizgi ikonlu gönderi kartları.
- Dekoratif kapak ve tekrar eden "Üretici bilgileri" kartı kalktı.
- Fotoğraf, düzenleme, çıkış, gönderi silme ve paylaşma mantığı aynen korundu.
- "Faydalı" tepki seçicisi Meydan ile ortak `ReactionManager` sınıfından geliyor; içindeki emojiler Meydan aşamasına bırakıldı.

### Doğrulama
- `tests/stage3/run.sh`: GardenFormModelTest (40), GardenReportModelTest (28), OperationHistoryModelTest (20), check_wiring (94).
- Mevcut 21 test takımının hepsi geçiyor; aralarında bahçe detayı SQLite (61), bahçe analitiği, konum ve hatırlatıcı var.
- `android.jar` tip denetiminde yeni hata yok; 463 hatanın tamamı Firebase, Glide ve androidx kaynaklı.
- Java toplamı **90247 satır**.
- **APK derlenmedi ve telefonda denenmedi.**

### Kalan eski ekranlar
- Salamura, Detaylı rapor, eski Piyasa ekranı, Gönderi oluştur, Yorumlar, Konum seçici, Hatırlatıcılar ve Ayarlar.
- Meydan ve Piyasa sekmeleri.
- Giriş, tanıtım ve yasal metin ekranları.

### Cihazda kontrol
- Bahçe ekle: konum seçimi satırı güncelliyor mu? Sulama kutuları ve fotoğraf küçük resmi doğru mu?
- İlk bahçe: kurulumdan sonra "Bahçen hazır" ekranı çıkıyor mu?
- Bahçe düzenle: eski bir bahçe konumu değiştirmeden kaydediliyor mu?
- Bahçe Karnesi: bahçe düğmeleri arasında geçiş anında mı? Çubuklar ve rozet renkleri doğru mu?
- İşlem geçmişi: "Sinek tuzağı" filtresi kayıt gösteriyor mu? Ay grupları doğru mu? Alttan açılan panel düzgün mü? Bahçe detayından "Tümü ›" o bahçeyle açılıyor mu?
- Profil: misafirde zeytin ikonu ve "Giriş yap" görünüyor mu? Sekmeler çalışıyor mu? Gönderi menüsü, silme ve paylaşma çalışıyor mu?

## 22. Play Store hazırlığı — 1. adım (2 commit)

### Hedef Android 16 (API 36)
- Google Play, 31 Ağustos 2026'dan beri yeni uygulamalarda API 36 hedefini zorunlu tutuyor. Proje 34'teydi.
- Yükseltmeler:
  - `compileSdk` / `targetSdk`: 34 → 36.
  - Android Gradle Plugin: 8.5.2 → 8.10.0 (API 36'yı destekleyen sürüm).
  - Gradle: 8.7 → 8.11.1. SHA-256 özeti gradle.org/release-checksums sayfasından alındı ve doğrulandı.
- **Kenardan kenara ekran:** Android 15+ cihazlarda içerik durum ve gezinme çubuklarının altına uzanıyor; Android 16'da bu kapatılamıyor.
  - Yeni `SystemBarInsets` sınıfı her ekrana çubuk ve klavye yüksekliği kadar boşluk veriyor. `ZeytinApplication` tek bir yerden kuruyor.
  - Çubukların arkasında koyu arka plan görünüyor; ekranlar eskisi gibi duruyor. Android 14 ve altında bir şey değişmiyor.
- **Geri tuşu:** Android 16'da `onBackPressed` çağrılmıyor.
  - Yeni `BackCompat` sınıfı Android 13+ için geri dinleyicisi kaydediyor.
  - Sezon Raporum açıkken geri tuşu Lig'e dönüyor; dinleyici yalnızca rapor açıkken kayıtlı, böylece sistemin geri animasyonu korunuyor.
  - Konum seçici adım adım geri gidiyor.
- `tests/release/check_target_sdk.py`: 11 kontrol.

### Uygulama içi hesap silme
- **Düzeltme:** Daha önce "hesap silme yok" demiştim. Aslında Ayarlar'da e-posta ile silme talebi açan bir satır vardı. Play'in istediği web bağlantısı ise yoktu.
- **Ayarlar > "Hesabımı Sil"** akışı:
  1. Uyarı gösteriliyor ve kullanıcı "SİL" yazarak onaylıyor.
  2. Son giriş zamanı kontrol ediliyor.
  3. Telefonun bildiği piyasa katkıları Supabase'den geri çekiliyor; bağlantı yoksa hiçbir şey silinmeden duruluyor.
  4. Sunucu fonksiyonu `deleteAccount` çağrılıyor.
  5. Telefondaki veri siliniyor, oturum kapatılıyor ve giriş ekranı açılıyor.
- Misafir oturumunda yalnızca telefondaki misafir verisi siliniyor. E-posta ile talep yedek yol olarak duruyor.
- **Sunucu (`functions/account`):**
  - Silinenler: gönderiler, fotoğraflar, engelleme ve şikayet kayıtları, lig kayıtları, `users/{uid}` altındaki her şey ve en son giriş hesabı.
  - Başka gönderilere yazılmış yorumlar "Silinmiş kullanıcı" olarak boşaltılıyor; konuşma dizisi bozulmuyor.
  - Piyasa kaynaklarını mevcut tetikleyiciler temizliyor.
  - Son 10 dakikada giriş yapılmamışsa silme reddediliyor. İşlem tekrar çağrılabiliyor.
  - Testler: 6 yeni test; toplam 84 geçti, 0 başarısız, 10 emülatör testi atlandı.
- **Telefon (`LocalAccountWipe`):**
  - Tek veritabanı işleminde yalnızca silinen hesaba ait satırlar siliniyor; aynı telefondaki diğer hesapların verisi korunuyor.
  - Gerçek SQLite üzerinde 18 kontrol yapıldı. Test bir hata yakaladı ve düzeltildi: misafir silmesi başka hesabın eski kayıtlarına dokunuyordu.
- **Web sayfası:** `hosting/hesap-silme/index.html`. `firebase deploy` sonrası `https://<proje-kimliği>.web.app/hesap-silme/` adresinde yayınlanır.
- **Gizlilik politikası:** 10. bölüm uygulamanın gerçek davranışına göre güncellendi; metin sürümü 1.2. Yayıncı 14.10.2026 tarihinde onayladı (hukukçu incelemesi değildir).

### Senin yapman gerekenler
1. Android Studio'da Gradle eşitlemesi ve derleme. `gradle.properties` içindeki `org.gradle.java.home=/home/test/java17` satırı senin bilgisayarında JDK 17'nin yoluyla eşleşmeli (AGP 8.10 JDK 17 ister).
2. Firebase'e dağıtım: `firebase deploy --only functions:deleteAccount,firestore:indexes,hosting`.
3. Play Console > Veri güvenliği > Hesap silme alanına web sayfasının adresini yaz.
4. Supabase: başka bir cihazdan ya da yeniden kurulumdan önce gönderilmiş katkıları sunucu tarafında silen bir RPC gerekiyor. V7 tanımları depoda olmadığı için yazılmadı.
5. Cihaz testi:
   - Android 15 ve 16'da durum çubuğu çakışması, klavye ve geri tuşu.
   - Test hesabıyla hesap silme: yakın giriş yokken uyarı çıkıyor mu, varken silme tamamlanıyor mu? Silmeden sonra giriş yapılamıyor mu? Firestore'da veri kalmamış mı?

### Doğrulama
- Mevcut 24 test takımının hepsi geçiyor. `android.jar` tip denetiminde yeni hata yok; yeni 10 hata yalnızca bu ortamda bulunmayan Firebase sınıflarından.
- **Hiçbir şey canlı Firebase'e dağıtılmadı, APK derlenmedi, cihazda denenmedi.**

## 23. Güvenlik sertleştirmesi — 1. aşama (1 commit)

Kapsam: Meydan gönderi kuralları, Firebase Storage kuralları ve moderasyon istemcisinin yapılandırma koruması. Canlı Firebase/Supabase'e dağıtım yapılmadı.

**Meydan gönderileri (`firestore.rules` → `social_posts`):**
- Gerçek veri modeli: buluta tek yazan `SocialCloudRepository.publishLocalPost`, `set(..., merge)` ile yazıyor. Bu yüzden aynı istek ilk seferde create, sonra update kuralına düşüyor.
- Oluşturma kuralı:
  - Zorunlu alanlar ve alan beyaz listesi.
  - `user_id` oturumdaki kullanıcıyla aynı olmalı; `cloud_id` belge kimliğiyle aynı olmalı.
  - `created_at` sunucu saatinden en fazla 10 dakika ileride olabilir.
- Güncelleme kuralı:
  - Yalnızca beyaz listedeki alanlar değişebilir.
  - Sahip (`user_id`) hiç değişmez. `cloud_id` ve `created_at` eski belgede yoksa bir kez eklenebilir, sonra değişmez.
- Görünen ad: `request.auth.token.name` ile aynı olmalı, ad yoksa "Üretici". İstemci de adı aynı oturum belgesinden alır. Ad değişince ve kayıtta oturum belgesi yenilenir.
- Metin: boş olamaz, en fazla 5.000 karakter. İl, ilçe ve mahalle 100; bahçe adı 200; çeşit 100; elek 20 karakterle sınırlı.
- Türler: gönderi türleri sabit bir listeden seçilir. `is_real_sale` istemciden yalnızca 0 olabilir.
- Okuma, silme ve yorum kuralları değişmedi.
- Eski belgelerde dokunulmayan alanlar (`photo_uri`, `media_urls`) korunur.
- İstemci: boş ya da 5.000 karakterden uzun gönderi buluta hiç gönderilmez. Gönderi oluşturma ekranı 5.000 karakterle sınırlı. Meydan'a fotoğraf yüklenmediği için medya eklenmiş olsa da metin istenir.

**Storage:** Uygulama Storage kullanmıyor (Senaryo A). İstemcilerin okuma ve yazma erişimi tamamen kapatıldı. Hesap silmedeki `social_posts/{uid}/` temizliği Admin SDK ile çalışır, kurallardan etkilenmez.

**Moderasyon:**
- Yeni `ModerationTransport` sınıfı (saf Java) adresi doğrular. Boş, `REPLACE_ME` içeren, HTTPS olmayan, `*.supabase.co` dışında olan, kullanıcı bilgisi / sorgu / farklı port / yol içeren adresler reddedilir.
- Adres geçersizse oturum belgesi hiç istenmez ve bağlantı açılmaz. Yönlendirme (3xx) hata sayılır.
- Ağ hatasında kullanıcıya şu gösterilir: "Moderasyon servisine şu anda bağlanılamıyor. Lütfen daha sonra tekrar deneyin."
- `MarketBackendConfig` ve yetki kodu değişmedi.

**Testler:**

| Test | Durum |
|---|---|
| `tests/security/run.sh` (masaüstü Java + statik) | 15 + 100 + 49 kontrol, geçti |
| `functions` (`npm test`) | 96 test: 84 geçti, 0 başarısız, 12 atlandı (emülatör yok) |
| `posts.rules.test.js`, `storage.rules.test.js` | Yazıldı, emülatör olmadığı için **çalıştırılmadı** |
| `blocks.rules.test.js`, `reports.rules.test.js` | Eksik alanlı gönderi yerine kurala uygun gönderi kullanacak şekilde güncellendi (emülatör yok, çalıştırılmadı) |

Değişiklikten önce de başarısız olan üç test aynı kaldı: `tests/blocks/check_blocks.py`, `tests/feed_thread/check_preview.py`, `tests/market-production-five-java.py` (`JSON_JAR` yok).

## 24. Supabase ayarı: local.properties → BuildConfig (1 commit)

Sorun (14.10.2026, cihazda görüldü): Bu daldan derlenen APK'larda Supabase adresi `REPLACE_ME` kalıyordu, çünkü gerçek değerler bilerek repoda tutulmuyor. Çalışan eski APK'lar, elle düzenlenmiş yerel kopyalardan derlenmişti. Ayrıca canlı veritabanında `market_league_regions` için `anon` çalıştırma izni eksikti. Bu izni yayıncı, repodaki SQL tanımıyla aynı olacak şekilde verdi (canlı değişiklik; bu commit'in parçası değil).

**Çözüm:**
- `app/build.gradle`, `SUPABASE_URL` ve `SUPABASE_PUBLISHABLE_KEY` değerlerini proje kökündeki `local.properties` dosyasından (git'e gitmez) ya da aynı adlı ortam değişkenlerinden okuyup `BuildConfig`'e yazar.
- `MarketBackendConfig` bu değerleri `BuildConfig`'ten okur. Değer yoksa yer tutucuya düşer; `isConfigured()` false döner ve Supabase'e istek gitmez. `BuildConfig`'e doğrudan bağlı olmadığı için masaüstü testleri değişmeden çalışır.
- Kod küçültme açılırsa diye `proguard-rules.pro`'ya `BuildConfig` için koruma kuralı eklendi.
- `gradle.properties` içindeki makineye özel `org.gradle.java.home=/home/test/java17` satırı kaldırıldı. Gerekirse bu ayar `~/.gradle/gradle.properties` içine yazılır.

**Testler:**
- `tests/security/MarketBackendConfigTest`: BuildConfig yok / boş / dolu (uydurma deneme değerleri) durumlarında 16 kontrol.
- `check_static`: 56 kontrol. Repoda gerçek Supabase anahtarı ya da uygulama kodunda gerçek proje adresi olmadığını tarar.
- Diğer test takımlarının sonuçları değişmedi.

## 25. Zeytin Ligi bölge raporu: elek fiyatları (1 commit)

Onaylanan tasarım ("Zeytin Ligi · Bölge raporu (elek fiyatları)" panosu) uygulandı.

**Ekran:**
- Bölge kartına dokununca açılan raporun en üstünde artık "Elek fiyatları • medyan" bölümü var. Double, 1 No, 2 No, 3 No, 4 No ve Yağlık için bölgedeki medyan kg fiyatı 3×2 kutuda büyük yazılıyor. Altında son 7 gün değişimi (▲/▼) ya da "trend yok" görünüyor.
- Eski "Elek Dağılımı" kg kutuları ve çubukları silindi. 30 günlük grafik "Detaylı liste" penceresine taşındı; pencere kaydırılabilir.

**Gizlilik:**
- Bir elekte 5 farklı üretici yoksa o eleğin fiyatı gösterilmiyor; kutuda "— · n/5 üretici" yazıyor.
- Bu kural `RegionGradePrice` sınıfında tanımlı. Veri katmanı (`MarketCloudRepository`) da eşik altındaki fiyatı hiç tutmuyor.
- Sunucu tanımında (`ZEYTIN-LIGI-BOLGE-RAPORU-PROD-5.sql` / `TEST-2`) elek başına `average_price` ve `median_price` 1 üretici varken bile dönüyordu. Bu açık `v_threshold` ile kapatıldı.
- Canlı veritabanı için `supabase/PATCH-BOLGE-RAPORU-ELEK-FIYAT-ESIGI.sql` hazırlandı: kontrol, düzeltme ve doğrulama adımları. Canlıya uygulamak yayıncının kararı.

**Testler:**
- `tests/league_region`: 35 + 13 kontrol.
- `check_static`: moderasyon değişiklik kontrolü yalnız moderasyon dosyalarına daraltıldı.
- Diğer test sonuçları değişmedi.

## 26. Sohbet (Meydan) yeniden tasarımı (1 commit)

Onaylanan "Sohbet (Meydan) · öneri" panosu uygulandı. Sohbet, Firebase üzerinde çalışmaya devam ediyor; Supabase yalnızca bölge piyasası şeridi ve moderasyon için kullanılıyor.

**Ekran:**
- **Başlık:** Büyük "Zeytin Defteri" başlığı ve sezon düğmesi Sohbet'te gizlendi. Yerine sade "Sohbet" başlığı ve profil düğmesi geldi.
- **Kategoriler:** Emoji yerine çizgi ikonlu çipler.
- **Yazma alanı:** ☰ düğmesi yerine seçili konuyu gösteren "#Genel ▾" düğmesi var; gönder düğmesi çizgi ikonlu.
- **Mesaj kartı:** Yazar, "bölge · tarih saat", konu etiketi ve şikâyet menüsü. Metin çerçevesiz, en fazla 5 satır. Son yanıt önizlemesi kartın içinde. Altta "Faydalı" ve "Yanıtla · n". Saat artık bir kez yazılıyor; "YANITLA" ve "Tüm yanıtları gör" kalktı.

**Bölge piyasası şeridi:**
- Eski şerit ("Piyasa • Bölgem 1 No 81 TL/kg") yalnızca kullanıcının kendi elek ortalamasıydı; kaldırıldı.
- Yeni şerit, kullanıcının bölgesini Zeytin Ligi ile aynı kuralla (kendi elek kayıtlarından) buluyor. Fiyatı Supabase'teki bölge istatistiğinden alıyor (`market_league_regions`, 1 No).
- Bölgede 5 üretici yoksa fiyat yazılmıyor: "Fiyatlar 5 üreticiyle açılır · şu an n/5". Metin kuralı `MeydanMarketStrip` sınıfında.
- Dokununca Zeytin Ligi → Piyasa açılıyor (`ZeytinLeagueContainerView.openMarketTab`).

**"Faydalı" kararı:**
- Tepkiler yalnızca telefonda tutulduğu (buluta yazılmadığı) için sayı artık hiçbir ekranda gösterilmiyor. `ReactionManager` yalnızca etiketi yazıyor; çizgi ikon seçilince yeşile dönüyor.
- Ortak sayaç, ayrı bir sunucu özelliği olarak sonraya bırakıldı.

**Kaldırılanlar:**
- Hiç gösterilmeyen "Meydan nabzı" kodu (`buildMeydanPulse`, özet/bölge/bekleyen kartları, `calculateMeydanStats`, `MeydanStats`; yaklaşık 600 satır).
- `MarketRepository.getMarketAveragePrice` ve `ReactionManager.getReactionEmoji`.
- Sunucuda doğrulanmayan "gerçek satış ✓" yazar işareti.

**Testler:**
- `tests/meydan_redesign`: 27 mantık + 43 bağlantı kontrolü.
- Diğer test sonuçları değişmedi.
- Cihazda denenmedi.
