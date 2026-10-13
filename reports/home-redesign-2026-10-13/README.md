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
