# Zeytin Defteri V9 — Asistan konuşma deneyimi

## Kapsam ve önce incelenen dosyalar

Kaynak: Zeytin-Defteri-V8-Gercek-Zeytin-Asistani.zip. Çalışma mevcut asistanın konuşma paneli ve yaşam döngüsüyle sınırlandırıldı.

- `app/src/main/java/com/zeytindefteri/app/ZeytinAssistantController.java`: mevcut dialog, SpeechRecognizer, RecognitionListener, izin, yazılı giriş, sorgu yönlendirmesi ve TTS. V8 partial ve RMS callback'leri boştu.
- `app/src/main/java/com/zeytindefteri/app/ProducerFeedActivity.java`: alt orta mikrofon, uzun basma işlem menüsü, Host bağlantısı, izin sonucu ve lifecycle.
- `app/src/main/java/com/zeytindefteri/app/ZeytinAssistantCommand.java`: mevcut intent parser ve bahçe çözümleme.
- `app/src/main/java/com/zeytindefteri/app/ZeytinAssistantData.java`: mevcut gerçek kayıt sorguları, owner/sezon kapsamı ve cevaplar.
- `app/src/main/java/com/zeytindefteri/app/ZeytinAssistantWeather.java`: mevcut hava cevabı üretimi.
- `app/src/main/AndroidManifest.xml`: mevcut mikrofon izni ve servis görünürlüğü.
- `app/src/main/java/AssistantOrbDrawable.java`: mevcut sürekli animasyon. Değiştirilmedi; ses seviyesi için sürekli animasyon yerine RMS tabanlı görünüm kullanıldı.
- `tests/assistant/run_host_tests.py` ve mevcut asistan test kaynakları: V8 regresyon doğrulaması.
- Gradle wrapper/yapılandırması: derleme giriş noktası. XML kaynakları yalnız parse edildi.

## Değişen dosyalar ve nedenleri

| Dosya | Değişiklik ve gerekçe |
| --- | --- |
| ZeytinAssistantController.java | Mevcut panel cilalandı; merkezi UI durumu, partial metin, RMS bağlantısı, izin açıklaması, hata mesajları, oturum ve TTS temizliği eklendi. Mevcut sorgu/Intent hattı kullanıldı. |
| ProducerFeedActivity.java | Mikrofon erişilebilirlik açıklaması ve onPause temizleme çağrısı eklendi. Diğer Activity kodu V8 ile aynı. |

Yeni dosyalar:

- `app/src/main/java/com/zeytindefteri/app/ZeytinAssistantConversation.java`: yalnız UI durumu, tur kimliği, tek sefer okuma kontrolü, hata metinleri ve RMS normalizasyonu; yeni veri motoru değildir.
- `app/src/main/java/com/zeytindefteri/app/AssistantVoiceWaveView.java`: gerçek RMS ile beslenen beş çubuk; sessizlikte sönümleme, kapanışta callback temizliği.
- `tests/assistant_ux/ConversationTest.java`: saf konuşma modeli testleri.
- `tests/assistant_ux/run_ux_tests.py`: Android bağımlılığı olmadan model testlerini çalıştırır.
- Bu rapor.

## Durum akışı ve panel

Tek `transition` metodu durum yazısını, progress görünürlüğünü, dalgayı ve mikrofon anlamını yönetir. Normal akış LISTENING → PROCESSING → ANSWERING. Duraklatma/durdurma IDLE, ses hataları ERROR; tekrar soru yeni LISTENING turudur. Tanınan ancak desteklenmeyen komut ANSWERING durumunda örneklerle açıklanır; konuşma metni korunur.

Dialog, recognizer başlatılmadan açılır ve Dinliyorum gösterir. Mikrofon hazır olunca konuşma ipucu değişir. Ek yapay bekleme yoktur. Koyu panelde kaydırılabilir içerik ve kullanılabilir yüksekliğe göre sınır bulunur; klavye için resize kullanılır. Tekrar Sor/Durdur, Kapat ve mevcut yazılı soru alanı korunur. Haptic mevcut View API'sini kullanır; yeni izin yoktur. Durum değişimlerinde erişilebilirlik duyurusu yapılır; partial metin sürekli TalkBack duyurusu üretmez.

## SpeechRecognizer ve partial sonuçlar

Dinleme yalnız kullanıcı etkileşiminde başlar. Yeni tur önce TTS'yi durdurur, önceki recognizer'ı cancel/destroy eder, sonra tek recognizer oluşturur. Callback sürüm kontrolü eski session sonucunu reddeder. Dinlerken mikrofon düğmesi durdurur; sonraki dokunuş yeni tur başlatır.

Dil tr-TR, EXTRA_PARTIAL_RESULTS true. Partial yalnız duyulan metni günceller; parser/veri sorgusu çalıştırmaz. Final sonuç mevcut ortak submit hattına girer. Partial vermeyen servislerde final akışı devam eder. onEndOfSpeech PROCESSING durumuna geçer. RMS gerçek callback değerinden gelir; rastgele animasyon yoktur. Sönümleme kısa süre sonra biter; panel kapanınca ve detach sırasında callback kaldırılır.

onPause turu geçersiz kılar, recognizer ve TTS durur. onStop paneli kapatır. onDestroy worker, handler ve ses kaynaklarını temizler. Recreate sonrasında panel otomatik açılmaz; kullanıcı yeniden açar. Eski panel konuşması yeni Activity'ye taşınmaz.

## TTS

Mevcut Android TextToSpeech kullanılır, Türkçe dil desteği kontrol edilir. Cevap ekranda gösterilen aynı metinden okunur; sayılar yeniden üretilmez veya değiştirilmez. QUEUE_FLUSH ve stop ile önceki utterance temizlenir. Her cevap turunda otomatik okuma bir kez yapılır; geç gelen TTS hazırlığı dinleme sırasında konuşma başlatmaz. Tekrar Sor önce TTS'yi keser. Panel kapanınca stop/shutdown; destroy sırasında cleanup yapılır. Dil/servis yoksa yazılı cevap kullanılabilir. Navigasyon komutu kısa görsel geri bildirimden sonra mevcut Intent'e gider; uzun TTS beklemez.

## Hatalar ve izin

- No match: Seni tam anlayamadım; yeniden deneme mümkün.
- Speech timeout: Seni duyamadım; yeniden deneme mümkün. Servisin doğal timeout'u korunur.
- Network/network timeout: ses tanıma bağlantısı sorunu açıklanır.
- Permission: mikrofon izni olmadan sesli komut kullanılamadığı açıklanır.
- Busy: asistanın meşgul olduğu, tekrar denenebileceği belirtilir.
- Diğer hata veya servis yok: sesli komutun kullanılamadığı açıklanır; yazılı giriş kalır.
- Bilinmeyen komut: speech error değildir; duyulan metin, cevap ve örnekler gösterilir.

İlk izin eksikliğinde panel neden mikrofon gerektiğini açıklar. Kullanıcı İzin Ver'e dokununca sistem izni istenir. Reddedildiğinde yazılı giriş çalışır; kalıcı ret durumunda Ayarlar düğmesi vardır. Otomatik tekrarlayan izin döngüsü yoktur. Teknik hata kodları yalnız debug log'undadır; tam konuşma metinleri loglanmaz.

## Korunan V8 işlevleri ve izolasyon

Parser, veri/hava cevap sınıfları, repository'ler, Manifest, Firebase dosyaları, veritabanı şeması/migrationlar ve mevcut testler kaynak ZIP ile byte düzeyinde aynı bırakıldı. Veri motoru yeniden yazılmadı. Mevcut owner, season ve aktif bahçe kontrolleri korunur; ek tur kontrolü eski cevabın yeni konuşmaya yazılmasını engeller. Ses ve yazı aynı parser/veri hattına gider. Yeni veri yazma/silme kodu yoktur; uygulama kullanıcı verilerine erişilmedi veya veri silinmedi.

Ana sayfa, hava, Bugün Yapılacaklar, uzun basma işlem menüsü ve navigasyon kodu korunmuştur. ProducerFeedActivity'nin iki belirtilen değişikliği çıkarıldığında kalan içerik V8 ile aynıdır.

## Test sonuçları

| Doğrulama | Sonuç ve sınır |
| --- | --- |
| Mevcut V8 host testleri | 55 parser/bahçe ve 25 veri/hava fixture kontrolü: 80 geçti. Gerçek kaynaklar test doubles ile çalışır; canlı kullanıcı verisi/Android cihaz testi değildir. |
| Yeni UX model testleri | 30 kontrol geçti. Durum, tur iptali, tek okuma, hata metinleri, RMS sınırları. Android callback entegrasyonu simüle edilmez. |
| Java sözdizimi | Değişen/eklenen dört üretim Java dosyası JavaCompiler/JavacTask parse kontrolünden geçti. Tam Android tip/bağlama derlemesi değildir. |
| XML | 70 XML parse edildi, hata yok. |
| Kapsam karşılaştırması | Kaynak projede yalnız iki mevcut dosya değişti; mevcut dosya silinmedi. |
| Gradle assembleDebug | Başarısız: Gradle 8.7 dağıtımı yerelde yok; wrapper services.gradle.org için UnknownHostException verdi. APK üretilmedi. |

V8 regresyon kapsamı: Elek, Piyasa, Bahçelerim, Hasat, Satış, Sulama navigasyonları; sulama/gübreleme/ilaçlama geçmişi; hasat, masraf, satış, elek miktarı; bahçeli hava ve yarın yağış; aktif bahçe ve sezon özeti. Mevcut testler silinmedi. Sayısal fixture testleri canlı database doğrulaması olarak sunulmamıştır.

### İstenen yeni UX senaryoları

| Senaryo | Yapılan kontrol | Gerçek cihaz |
| --- | --- | --- |
| Tek mikrofon basışı | Panelin recognizer öncesinde açıldığı statik inceleme | Yapılmadı |
| Hızlı çift basış | Tur iptali model testi, tek recognizer kod incelemesi | Yapılmadı |
| Dinlerken kapatma | Tur geçersizliği model testi ve cleanup incelemesi | Yapılmadı |
| TTS sırasında tekrar mikrofon | Tek okuma/tur modeli; stop sırası statik inceleme | Yapılmadı |
| Partial result | Yalnız metin güncelleyen callback statik inceleme | Yapılmadı |
| Final result | Ortak submit hattı statik inceleme; V8 parser testleri | Yapılmadı |
| No match | Hata metni model testi | Yapılmadı |
| Speech timeout | Hata metni model testi | Yapılmadı |
| Network error | Hata metni model testi | Yapılmadı |
| Permission denied | Hata modeli ve izin akışı statik inceleme | Yapılmadı |
| Speech servisi yok | Availability/fallback statik inceleme | Yapılmadı |
| TTS dili yok | Dil sonucu/fallback statik inceleme | Yapılmadı |
| Rotate/recreate | Yeni model IDLE testi; Activity lifecycle statik inceleme | Yapılmadı |
| Destroy cleanup | Handler/worker/recognizer/TTS cleanup statik inceleme | Yapılmadı |
| Bilinmeyen komut | V8 parser testi ve ANSWERING model testi | Yapılmadı |
| Navigasyon | V8 parser ve mevcut Host bağlantısı incelemesi | Yapılmadı |
| Veri sorusu | V8 fixture testleri; pipeline korunma kontrolü | Yapılmadı |
| Yazılı soru | Ortak submit bağlantısı statik inceleme | Yapılmadı |
| Başka bahçe adı | V8 bahçe çözümleme testleri | Yapılmadı |
| Aktif bahçe | V8 çözümleme/kapsam testleri | Yapılmadı |

Fiziksel cihaz/emülatör kullanılmadı. Gerçek mikrofon/TTS echo, servis callback sırası, izin ekranı lifecycle farkları, TalkBack, haptic, 320dp ekran ve klavye ile panel yerleşimi cihazda doğrulanmalıdır. Bu rapor bu davranışları cihazda geçmiş kabul etmez.

Yeniden çalıştırma:

```sh
python3 tests/assistant/run_host_tests.py
python3 tests/assistant_ux/run_ux_tests.py
bash gradlew :app:assembleDebug --offline
```

Resmî API referansları: [RecognitionListener](https://developer.android.com/reference/android/speech/RecognitionListener), [UtteranceProgressListener](https://developer.android.com/reference/android/speech/tts/UtteranceProgressListener).

## Kapsam dışında fark edilenler

Proje genelinde ek hata taraması veya refactor yapılmadı. Kapsam dışında dosya değiştirilmedi. Gradle erişim engeli nedeniyle tam Android derlemesi ve cihaz doğrulaması teslimin açık sınırlamalarıdır.
