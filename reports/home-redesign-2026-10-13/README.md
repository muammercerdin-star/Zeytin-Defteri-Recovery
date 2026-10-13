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
