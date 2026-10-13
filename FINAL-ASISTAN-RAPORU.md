# Zeytin Defteri — FINAL Zeytin Asistanı

Bu paket, yüklenen V9 tam projesinin devamıdır. Kaynak kod uygulandı; tam Android derlemesi ve fiziksel cihaz doğrulaması bu ortamda tamamlanamadı. Dosya adındaki FINAL, bu teslim paketinin adıdır; cihazda doğrulanmış yayın sertifikası anlamına gelmez.

## 1. Önce incelenen dosyalar

Java kaynak kökü: `app/src/main/java/`. Aşağıdaki dosyalar özellik kapsamındaki bağlantıları doğrulamak için incelendi:

- `com/zeytindefteri/app/ProducerFeedActivity.java`: mikrofon/uzun basma, mevcut navigasyon Host'u, hava kartları ve render akışı, lifecycle.
- `com/zeytindefteri/app/ZeytinAssistantController.java`: V9 dialog, SpeechRecognizer/partial/final, TTS, izin, ortak submit ve owner/sezon/aktif bahçe kontrolü.
- `com/zeytindefteri/app/ZeytinAssistantCommand.java`: mevcut parser ve kayıtlı bahçe adı çözümleme.
- `com/zeytindefteri/app/ZeytinAssistantConversation.java`: mevcut merkezi konuşma durumları ve tur koruması.
- `com/zeytindefteri/app/ZeytinAssistantData.java`: mevcut salt okunur DatabaseHelper ve SaleRepository erişimi.
- `com/zeytindefteri/app/ZeytinAssistantWeather.java`: hava cevap biçimlendirmesi.
- `com/zeytindefteri/app/WeatherForecastScrollView.java`: kart ölçümü, viewport ve ölçüm önbelleği.
- `com/zeytindefteri/app/WeatherRepository.java`: mevcut DTO alanları ve mevcut yükleme bağlantısı; değiştirilmedi.
- `com/zeytindefteri/app/GardenTodayAdvisor.java`: mevcut günlük öneri girdileri/çıktıları; değiştirilmedi.
- `DatabaseHelper.java`, `DatabaseSchema.java`: gerçek kolonlar ve mevcut owner/sezon/bahçe kapsamında okuyan metotlar; değiştirilmedi.
- `SaleRepository.java`: mevcut `getSale` ve satış elek satırları; değiştirilmedi.
- `HarvestActivity.java`, `SaleActivity.java`: mevcut garden_id Intent desteği; değiştirilmedi.
- `app/src/main/AndroidManifest.xml`, `app/build.gradle` ve wrapper: mikrofon/servis görünürlüğü, minSdk 24 ve derleme giriş noktası; değiştirilmedi.
- `tests/assistant`, `tests/assistant_ux`, `tests/today`: mevcut regresyonlar.

## 2. Değiştirilen dosyalar

| Dosya | Neden gerekliydi? |
| --- | --- |
| ProducerFeedActivity.java | Alt orta düğme aynı konumda mikrofon/küre görünümüne bağlandı. Mevcut Host'a durum/RMS bildirimleri eklendi. Kısa dokunma ve uzun basma korunuyor; uzun basma önce ses oturumunu kapatıyor. |
| ZeytinAssistantController.java | V9 dialog kompakt hale getirildi, nav üstüne yerleştirildi. Küre dokunması, durum/RMS aktarımı, mevcut günlük öneri motoruna çağrı ve kapsam kontrolleri bağlandı. Ses/yazı aynı submit hattında. |
| ZeytinAssistantCommand.java | Mevcut parser genişletildi: merkezi normalizasyon/sözcük kökleri, yeni anlam aileleri, tarih/son kayıt/sayı/karşılaştırma nitelikleri ve yeni soru biçimlerinde bilinmeyen bahçe koruması. |
| ZeytinAssistantData.java | Mevcut sorguların döndürdüğü gerçek kayıtlara tarih filtresi, son kayıt, sayım, sıralama, bahçe karşılaştırması, alıcı ve günlük öneri adaptasyonu eklendi. Yeni SQL veya veri yazma yolu yok. |
| ZeytinAssistantWeather.java | Mevcut hava DTO'larından bugün yağış/min/max ve mevcut beş günlük tahmin cevapları eklendi. Geçmiş hava sorusunda veri yokluğu açıkça belirtiliyor. |
| WeatherForecastScrollView.java | Gerçek ölçülen viewport, child ölçüm önbelleği geçersizliği ve piksel kalanlarının dağıtılması düzeltildi. |
| tests/assistant/run_host_tests.py | Eski testleri koruyarak yeni üretim kaynaklarını, yeni testleri ve mevcut Today testini çalıştıracak şekilde genişletildi. Yeni okuma metotları için test doubles eklendi. |

## 3. Yeni dosyalar

- `app/src/main/java/com/zeytindefteri/app/ZeytinAssistantOrbView.java`: Canvas tabanlı özgün görünüm.
- `app/src/main/java/com/zeytindefteri/app/ZeytinAssistantPeriod.java`: İstanbul takvimine göre gün/hafta/ay aralığı.
- `tests/final_assistant/FinalCommandTest.java`: doğal Türkçe ve bahçe çözümleme regresyonları.
- `tests/final_assistant/FinalDataTest.java`: gerçek üretim adaptörünü fixture verileriyle çalıştıran testler.
- `tests/final_assistant/run_weather_measure_tests.py`: üretim hava scroll sınıfını ölçüm/önbellek modeliyle çalıştırır.
- Bu rapor.

## 4. Küre, tasarım ve hareket

Küre nav düğmesinin kendi View'ıdır; başka bir ekrandan getirilen resim değildir. Mikrofon çizimi ile küre arasında 240 ms yumuşak geçiş uygulanır. 66dp dokunma alanında yaklaşık 55–62dp çizim kullanılır. Zeytin yeşili, orman yeşili, turkuaz ve kehribar renklerinde üç yaprak benzeri organik yüzey; merkezde yumuşak ışık ve ince kenar bulunur. Referans görsel kopyalanmadı; dış görsel veya yeni dependency kullanılmadı.

Paint, Path, RectF ve shader nesneleri önceden oluşturulur. onDraw içinde bitmap veya yeni nesne oluşturulmaz. Aktif hareket yaklaşık 32 ms çizim aralığıyla sınırlıdır; rastgele animasyon yoktur.

| Mevcut konuşma durumu | Küre davranışı |
| --- | --- |
| IDLE | Normal mikrofon. Kapanış geçişinden sonra sürekli çizim yok. |
| LISTENING | Hafif nefes, gerçek RMS ile sınırlı genişleme ve merkez ışığı. |
| PROCESSING | Sakin organik akış. |
| ANSWERING | Dengeli merkez ışığı ve yavaş hareket; ses seviyesini taklit eden rastgele dalga yok. |
| ERROR | Soluk ve durağan görünüm; kırmızı alarm/parlama yok. |

Ayrı konuşma state machine'i oluşturulmadı; `ZeytinAssistantConversation.State` kullanılır. RMS, V9 `onRmsChanged` callback'inden Host üzerinden küreye gelir. Mevcut normalizasyon üzerine frame bazlı 0,18 katsayılı yumuşatma uygulanır. Yeni ses seviyesi gelmezse hedef sıfıra iner. ContentDescription yalnız durum değişiminde güncellenir.

Panel kapanınca mevcut release/pause/destroy hattı recognizer ve TTS'yi temizler, görünüm IDLE'a döner. Kısa kapanış geçişi sonrası sürekli pulse durur. View detach olduğunda görünüm sıfırlanır. Arka planda aktif çizim döngüsü sürdürülmez. Eski waveform sınıfı silinmedi; görünmeyen waveform için ikinci bir animasyon çalıştırılmaz.

## 5. Kompakt konuşma deneyimi

İlk açılışta küçük yüzeyde Dinliyorum ve canlı partial metin görülür; küre aynı nav konumundadır. Büyük başlık/yazı formu ilk anda zorunlu değildir. Yazarak sor düğmesi yazılı girişi açar; hata ve cevap durumlarında yazılı giriş erişilebilir kalır. Cevapla birlikte yüzey büyüyebilir ve kaydırılır. Arka plan hafif karartılır; ana sayfa görünür.

Küreye dinlerken dokunma `stopListening` ile konuşmayı bitirir; final sonuç mevcut işlem hattına gider. Cevap sırasında dokunma önce TTS'yi durdurup yeni dinlemeyi başlatır. İşlenirken üst üste yeni recognizer oluşturulmaz. Normal uzun basma mevcut hızlı işlem menüsünü açar.

V9 SpeechRecognizer, tr-TR, partialResults, final submit, izin açıklaması/ret/ayarlar, TTS tek sefer okuma ve eski callback/tur kontrolleri korunur. Partial yalnız metindir; veri sorgusu çalıştırmaz. Ekran cevabı ve TTS aynı cevaptır. Teknik konuşma metinleri üretim log'una yazılmaz.

## 6. Anlam aileleri ve veriler

| Aile | Kullanılan mevcut veri / davranış |
| --- | --- |
| Navigasyon / form açma | V9 Host ve mevcut Intent hedefleri |
| Aktif bahçe / ağaç / yaş / çeşit | Sahip olunan gardens kayıtları |
| Alan / konum / sulama tipi / not | area_decare, province/district/neighborhood, irrigation_type, notes |
| Son işlem / işlem sayısı | getGardenOperations; gerçek işlem tarihi ve türü |
| Sulama / gübre / ilaçlama | Aynı işlem kayıtları; tarih ve tür filtresi |
| Hasat / son hasat | getGardenHarvests; harvest_date, total_kg |
| Masraf / son / en büyük masraf | İşlem maliyetleri, hasat işçilik/nakliye/diğer, salamura başlangıç ve olay maliyetleri |
| Satış geliri / kg / son satış | SALE kayıtları; gelir nakliye/kesinti sonrası kayıtlı net satış tutarıdır, kâr değildir |
| Son alıcı / telefon | Önce kapsamlı son satış bulunur, ardından mevcut SaleRepository.getSale |
| Satılan elek / kendi birim fiyatı | Mevcut satış elek satırları; fiyat miktarla ağırlıklandırılır |
| Elek miktarı / dağılım / en çok | getGardenElekRecords + getElekGrades |
| Salamura / hazır / stok | Kayıtlı olive_kg ve ready_kg. Giriş toplamı kalan stok gibi sunulmaz. |
| Piyasa | V9'un mevcut kendi paylaşım izinli gerçek satış verisi; piyasa geneli gibi sunulmaz |
| Hava | Değişmeyen WeatherRepository DTO'ları |
| Sezon/bahçe/dönem özeti | Aynı kapsamlı kayıtların kısa özeti |
| Bugün ne yapacağım? | Değişmeyen GardenTodayAdvisor; mevcut kayıtlar ve varsa mevcut hava girdisi |
| İki bahçe karşılaştırması / en yüksek bahçe | Sahip olunan bahçelerin aynı sezon/dönemdeki gerçek kayıtları |
| UNKNOWN | Açıklama ve örnekler; rastgele Intent yok |

Yeni sorular tam cümleleri tek tek eşleştiren ayrı motorla yapılmadı. Mevcut parser'daki merkezi sözcük/kök eşleştirmesi genişletildi; tarih, son kayıt, sayım ve karşılaştırma nitelikleri aynı komut nesnesinden taşınır. Bahçe adı kesin eşleşmezse aktif bahçeye sessizce geçilmez. Yeni alan ve kayıt başlatma biçimleri için bu koruma da genişletildi.

## 7. Tarih ve izolasyon

Bugün, dün, bu hafta, geçen hafta, bu ay ve geçen ay İstanbul takvimine göre hesaplanır. Hafta pazartesi başlar, bitiş sınırı dahil değildir. Tarih filtresi mevcut seçili sezon sorgusunun sonucuna uygulanır; sezon değiştirilmez. Bu sezon/bu yıl/bu sene ifadeleri seçili sezon kapsamını kullanır. Örneğin seçili sezon geçmiş yılsa, bugünün takvim aralığında kayıt bulunmayabilir; bunun için başka sezona erişilmez.

Bahçe belirtilmezse V9'un aktif bahçe davranışı korunur. Toplam ve genel sezon özeti mevcut tüm bahçeler kapsamını, açık bahçe adıysa o bahçeyi kullanır. Karşılaştırma komutları yalnız mevcut kullanıcıya ait bahçeler arasında yapılır. Owner/sezon/aktif bahçe değiştiğinde eski cevabı reddeden kontroller ve tur kimliği korunur.

Yeni tablo, kolon, migration veya repository refactor yoktur. Firebase, Firestore, Storage, google-services.json, DatabaseHelper, DatabaseSchema, repository kaynakları, Manifest, hava veri çekme motoru, Today motoru ve Conversation modeli V9 ZIP'iyle byte düzeyinde aynı kalmıştır. Kullanıcı veritabanı açılmadı; veri silinmedi veya yazılmadı. Test sayıları yalnız test dosyalarındaki fixture'lardır; uygulama cevaplarına sabit sayı eklenmedi.

## 8. Kayıt başlatma ve açık sınırlar

Sulama/gübre/ilaçlama/hasat/satış komutları mevcut formu açar. Bahçe adı eşleştiğinde mevcut garden_id aktarılır. “Bugün 200 kilo hasat ekle” ve “Girelli’ye 250 kilo hasat kaydı aç” hasat formuna gider; incelenen formda miktar/tarih için ön doldurma Intent desteği olmadığından bu alanlar otomatik doldurulmaz. Kullanıcı formda kontrol edip kaydeder. “2 numara satış aç” mevcut satış formunu açar; elek ön seçimi için yeni paralel form akışı kurulmadı.

Net stok, gerçek stok hareketleriyle hesaplanamıyorsa giriş toplamı olarak açıklanır. Piyasa geneliyle kendi fiyatı arasında güvenilir karşılaştırma kaynağı bulunmadığında açıkça veri eksikliği söylenir. Genel “Piyasa nasıl?” V9'daki varsayılan 2 numara kapsamında cevap verir ve bunu metinde belirtir. Geçmiş hava, mevcut beş günlük tahminden uydurulmaz. Kayıt eksikliği “kayıt bulunamadı” olarak bildirilir.

## 9. Hava cold-start teknik bulgusu ve düzeltme

V9 kodunda ilk genişlik hesabı `MeasureSpec.getSize(widthSpec)` üzerinden yapılıyordu; AT_MOST durumda bu, gerçek viewport değil üst sınırdır. Ayrıca çocukların `LayoutParams.width` değerleri doğrudan değiştiriliyor, çocuk/strip ölçüm önbelleği geçersiz kılınmıyordu. Aynı ölçüm özellikleriyle tekrar çağrıldığında eski ölçülen genişlik korunabiliyordu. Bunlar kaynak kodda saptanan ölçüm kusurlarıdır; bildirilen cihazdaki olayın aynı sırayla oluştuğuna ilişkin cihaz izi bu ortamda alınmadı.

Yeni akış:

1. Super ölçümüyle gerçek viewport genişliği çözülür.
2. Scroll ve strip yatay padding'leri, dört kart aralığı çıkarılır.
3. Beş kart genişliği hesaplanır; tam sayı bölmesinden kalan pikseller dağıtılır.
4. Genişliği/margini değişen kartlarda ve strip'te forceLayout kullanılarak ölçüm önbelleği geçersiz kılınır.
5. Aynı ölçüm turunda kesin viewport genişliğiyle yeniden ölçülür; yanlış genişlikte ilk çizim yapılması beklenmez.

postDelayed/rasgele refresh/gizleyip gösterme kullanılmadı. Cache veya network callback'inin geliş zamanı kart genişliği için varsayım değildir. Saatlik yatay kaydırma, seçili kart ve mevcut detay davranışları korunur.

Ölçüm modeli testinde eski V9 sınıfı `stale child cache` hatasıyla başarısız oldu; yeni sınıf geçti. Bu, kusuru model üzerinde tekrar üreten regresyon kanıtıdır; Android cihaz render testi değildir. [Android ölçüm akışı](https://developer.android.com/guide/topics/ui/how-android-draws) ve [View API](https://developer.android.com/reference/android/view/View) referans alındı.

## 10. Test sonuçları

| Test | Sonuç | Test türü |
| --- | --- | --- |
| Eski parser/bahçe testleri | 55 geçti | Host Java |
| Eski veri/hava adaptörü | 25 geçti | Fixture/test doubles |
| Yeni doğal komut/bahçe kontrolleri | 165 geçti | Host Java |
| Yeni veri/tarih/hava kontrolleri | 36 geçti | Fixture/test doubles |
| Mevcut GardenTodayAdvisor | 21 geçti | Saf Java |
| Mevcut V9 konuşma modeli | 30 geçti | Saf Java |
| Mevcut SQLite kapsam testleri | 14 geçti | Host SQLite fixture, mevcut sorgu ifadeleri |
| Hava viewport/önbellek | 36 geçti | Üretim sınıfı + ölçüm modeli |
| Java sözdizimi | 8 değişen/eklenen üretim dosyası geçti | JavacTask parse; Android bağlama derlemesi değil |
| XML | 70 dosya geçti | XML parse |

Parser/bahçe kontrolleri toplam 220'dir; bu sayı farklı tam komut sayısı anlamına gelmez. Eski test assertion'ları silinmedi. Yeni testler, gerçek kullanıcı/cihaz verisi yerine kontrollü fixture verileri kullanır. Alıcı telefonu fixture'ı gerçek kişi değildir.

Çalıştırma:

```sh
python3 tests/assistant/run_host_tests.py
python3 tests/assistant_ux/run_ux_tests.py
python3 tests/today/SQLiteScopeTest.py
python3 tests/final_assistant/run_weather_measure_tests.py
bash gradlew :app:assembleDebug --offline
```

## 11. Cold-start ve gerçek cihaz test matrisi

| Senaryo | Bu ortamda yapılan | Gerçek cihaz |
| --- | --- | --- |
| Force stop → cold start | İlk ölçüm modeli | Yapılmadı |
| Cache var / yok | Ölçümün veri zamanından bağımsızlığı kod incelemesi | Yapılmadı |
| Hızlı / yavaş internet, geç hava cevabı | Yeniden oluşturulan strip ölçüm modeli | Yapılmadı |
| Aktif bahçe değişimi | Scope fixture + yeni strip ölçümü | Yapılmadı |
| Background → foreground | Lifecycle statik inceleme | Yapılmadı |
| 24 Saat ↔ 5 Günlük | Aynı ölçüm sınıfı bağlantısı statik inceleme | Yapılmadı |
| Process recreate | Yeni scroll instance/strip modeli | Yapılmadı |
| 360 / 393 / 412dp | 1x/2x/3x yoğunluk, ölçüm/resize/AT_MOST modeli | Yapılmadı |
| Mikrofon → küre, RMS, gerçek speech | State/RMS bağlantısı statik inceleme | Yapılmadı |
| Küreye dokunma, hızlı tekrar dokunma | V9 tur modeli + callback bağlantı incelemesi | Yapılmadı |
| TTS sırasında kesme, izin reddi, servis yokluğu | V9 model testleri ve fallback kod incelemesi | Yapılmadı |
| Klavye, TalkBack, 320dp kompakt panel | Kod incelemesi | Yapılmadı |
| Gerçek hesap/bahçe/sezon değişimi | Host fixture testleri | Yapılmadı |

Özellikle OEM dialog/window davranışı, nav üstündeki küreye dokunmanın geçişi, klavyeyle panel yüksekliği, gerçek mikrofon-TTS yankısı ve font ölçeği telefonda doğrulanmalıdır. Görsel cihaz ekran görüntüsü veya FPS ölçümü üretilmedi.

## 12. Build sonucu

`bash gradlew :app:assembleDebug --offline` çalıştırıldı. Yerelde Gradle 8.7 dağıtımı yok; wrapper indirmeye çalışırken `java.net.UnknownHostException: services.gradle.org` verdi. Android SDK/emülatör de bu ortamda bulunamadı. Tam Gradle build başarıyla tamamlanmadı, APK üretilmedi. Java parse ve host testleri başarılı Android build yerine geçmez.

## 13. Kapsam dışında fark edilenler

Proje genelinde refactor veya ek ekran tasarımı yapılmadı. Mevcut form Intent'lerinin miktar/elek ön doldurma sınırı ve piyasa geneli/net stok verisi sınırları yukarıda açıklandı; bunlar yeni veritabanı veya piyasa altyapısıyla genişletilmedi. Önceki sürüm raporları tam proje korunması için pakette tutuldu; bu teslim için bu FINAL raporu geçerlidir.

## 14. Doğal Türkçe komut kataloğu

Aşağıdaki örnekler yeni parser testinde yer alır. Sayısal cevaplar yalnız uygulamanın gerçek kayıtlarından gelir; örneğin komutta söylenen 200/250 kg, kayıt formunu başlatma isteğidir ve kalıcı kayda dönüşmez.


**NAVIGATION**

- Elek aç
- Eleği aç
- Elek ekranına git
- Elek kayıtlarını göster
- Hasat aç
- Hasadı göster
- Hasat ekranına git
- Satış aç
- Satışlara git
- Sulama aç
- Sulama ekle
- Gübreleme aç
- Gübre ekle
- İlaçlama aç
- İlaç ekle
- Salamura aç
- Bahçelerimi aç
- Bahçeleri göster
- Piyasayı aç
- Fiyatları aç
- Meydanı aç
- Üretici akışına git
- Raporları aç
- Ana sayfaya dön
- Girelli’ye sulama ekle
- Bugün 200 kilo hasat ekle
- Girelli’ye 250 kilo hasat kaydı aç
- 2 numara satış aç

**ACTIVE**

- Şu an hangi bahçe seçili?
- Aktif bahçe hangisi?

**TREES**

- Girelli’de kaç ağaç var?

**AGE**

- Girelli kaç yaşında?

**VARIETY**

- Girelli’nin çeşidi ne?

**AREA**

- Kaç dekar?

**LOCATION**

- Bahçe nerede?

**WATER_TYPE**

- Sulama tipi ne?

**NOTE**

- Bahçe notu var mı?

**SUMMARY**

- Girelli ne durumda?
- Girelli’yi özetle.
- Bu sezon durumum nasıl?
- Sezon özeti ver.
- Bahçe ne alemde?
- Bu sene ne yaptık?

**OPERATION**

- En son ne yaptık?
- Son işlem ne?
- Bahçeye en son ne yaptım?
- Girelli’de en son ne yaptık?
- En son hangi işlem yapılmış?
- En son ne attık?
- Bu hafta ne yaptık?
- Bugün ne yaptık?
- Dün işlem yaptım mı?

**IRRIGATION**

- Son sulama ne zaman?
- En son ne zaman suladım?
- En son ne zaman su verdik?
- Kaç gün önce suladık?
- Bu sezon kaç kez suladım?
- Bu ay kaç kez suladım?
- Hangi bahçeyi en son suladım?
- Hangi bahçeyi uzun süredir sulamadım?
- En son suyu ne zaman verdik?

**FERTILIZER**

- Son gübreleme ne zaman?
- En son ne zaman gübre attım?
- Bu sezon kaç kez gübreleme yaptım?
- Bu ay gübre attım mı?

**SPRAY**

- Son ilaçlama ne zaman?
- En son ne zaman ilaç attım?
- Bu sezon kaç kez ilaçlama yaptım?
- Bu ay ilaçlama var mı?

**HARVEST**

- Bu sezon kaç kilo topladım?
- Bu sene ne kadar zeytin topladık?
- Toplam hasat kaç?
- Girelli’den kaç kilo çıktı?
- Bu ay kaç kilo hasat yaptım?
- Bu hafta kaç kilo topladım?
- Son hasat ne zaman?
- Son hasat kaç kilo?
- En çok hasat hangi bahçeden?
- Hangi bahçe daha çok verdi?
- En çok hangi bahçeden ürün aldım?
- Hangi bahçe daha çok hasat verdi?

**EXPENSE**

- Bu sezon masrafım kaç?
- Bu sene ne kadar harcadım?
- Cebimden ne kadar çıktı?
- Ne kadar para gömdük?
- Toplam gider kaç?
- Girelli ne kadar masraf yaptı?
- Bu ay ne kadar harcadım?
- Bu hafta masraf kaç?
- En çok masraf hangi bahçede?
- En büyük masraf ne?
- Son masraf neydi?
- En fazla masraf hangi bahçede?

**REVENUE**

- Toplam satışım kaç?
- Satış gelirim kaç?
- Bu sezon ne kadar kazandım?
- Bu ay kaç liralık satış yaptım?
- Son satış ne zaman?
- Son satış kaç para?
- Son satış kaç kilo?
- Son alıcı kim?
- Son alıcının telefonu ne?
- Hangi bahçeden daha çok satış yaptım?

**SOLD**

- Kaç kilo sattım?
- En son ne sattım?
- En çok hangi elek sattım?
- En son kime sattım?
- Ben malı kaça sattım?
- Malı kaça verdik?
- Geçen ay ne kadar sattım?

**ELEK**

- Double kaç kilo?
- 1 numara kaç kilo?
- 2 numara kaç kilo?
- 3 numara kaç kilo?
- 4 numara kaç kilo?
- Yağlık kaç kilo?
- Elek dağılımı nasıl?
- En çok hangi elek çıktı?
- Girelli’nin elek sonucu ne?
- Toplam elek kaç kilo?
- 2 numaradan ne kadar çıktı?

**BRINE**

- Salamurada kaç kilo var?
- Salamura ne durumda?
- Stokta ne kadar var?
- Ne kadar salamuraya ayırdım?

**READY**

- Hazır ürün kaç kilo?

**MARKET**

- Piyasa nasıl?
- 2 numara kaç para?
- 1 numara kaç para?
- Double kaç para?
- Yağlık kaç para?
- Akhisar’da fiyat ne?
- Benim fiyatımla piyasa arasında fark var mı?
- Piyasada mal kaç para?

**WEATHER**

- Hava nasıl?
- Bugün kaç derece?
- Yarın kaç derece?
- Yarın yağmur var mı?
- Bugün yağmur var mı?
- Yağış ihtimali kaç?
- Nem kaç?
- Rüzgâr kaç?
- Rüzgâr güçlü mü?
- Bugün en yüksek kaç?
- Bugün en düşük kaç?
- Yarın en yüksek kaç?
- 5 günlük hava nasıl?
- Bu hafta yağmur var mı?

**TODAY**

- Bugün ne yapmam lazım?
- Bugün bahçede ne var?
- Girelli için bugün ne var?
- Bugün ne öneriyorsun?

**COMPARISON**

- Girelli ile Afşar’ı karşılaştır.

**UNKNOWN**

- Traktörün radyosunu aç
- Kayıtları sil
- 200 kilo hasat kaydet
- Gübre dozu kaç gram?
