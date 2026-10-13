> Satış Doğrulama V1 güncellemesi: operatör onay servisi eklenmiştir. Güncel akış,
> rules ve test bilgisi ZEYTIN-LIGI-SATIS-DOGRULAMA-V1.md dosyasındadır.
> Aşağıdaki RC raporu tarihsel durumdur; Android/bağımlılık gate engelleri sürer.

# Zeytin Ligi Backend V1 — Release Candidate

RELEASE GATE: **BLOCKED** — 8 Eylül 2026.
Canlı Firebase deployment, canlı test verisi veya gerçek onay belgesi yazılmadı.
Bu rapor V1 dokümanındaki eski test/ortam durumlarının yerine geçer.

## Zorunlu engeller

1. Android assembleDebug tamamlanmadı: Gradle 8.7 indirmesinde
   java.net.UnknownHostException: services.gradle.org. Kaynak compile hatası
   tespit edilmiş değildir; tam Android compile/resource/link doğrulaması yoktur.
   Gradle/Android SDK erişimli AndroidIDE veya CI üzerinde build ve cihaz smoke
   testi tamamlanmalıdır.
2. Bağımsız gerçek satış kanıtı sağlayıcısı/operatör doğrulama süreci mevcut değil.
   Backend onayı tüketir, kanıt üretmez. Bu süreç olmadan kesin lig etkinleştirilmemeli.
3. npm audit --omit=dev: 11 moderate paket bulgusu, 0 high, 0 critical.
   Bunlar 11 bağımsız açık değildir; qs/uuid uyarıları bağımlı paketlere yayılır.
   Production öncesi sürüm düzeltmesi veya belgelenmiş uygulanabilirlik/risk
   değerlendirmesi gerekir. Mevcut sosyal trigger'ın kullandığı Firebase SDK'yı
   plansız major yükseltmemek için production package.json/lock değiştirilmedi.
   qs: GHSA-x5fp-wj9c-mxmx, GHSA-4mjr-xmp4-gh2g;
   uuid: GHSA-w5hq-g745-h8pq. npm önerisinin bir kısmı firebase-admin 14.3.0
   major sürümüne çıkıyor; körlemesine npm audit fix --force kullanma.
4. Hedef projede IAM, secret, API/faturalandırma, çalışan kurallar ve indeks
   ayarları doğrulanmadı; canlı Cloud Tasks/Scheduler ve gerçek cihaz uçtan uca
   testleri yapılmadı. Bunlar ayrıca onaylı deployment kontrolünün parçasıdır.

## Bu denetimde yapılanlar

Başlangıç çalışma ağacı yüklenen FINAL Backend V1 ZIP ile byte düzeyinde aynıydı.
Git deposu yok; diff, ZIP'teki gerçek dosyalarla karşılaştırılarak çıkarıldı.
Backend yeniden yazılmadı. İki gerçek Android sözleşme hatası düzeltildi:

- Yerel bölge kimliği SHA256 iken sunucu province_district kullanıyordu.
  Tam il/ilçe varsa Android artık aynı Türkçe/ASCII normalize anahtarı kullanır.
  Örneğin Alaşehir / Manisa ve ALASEHIR / MANISA -> manisa_alasehir.
  Eksik konumların yerel fallback kimliği korunur. Bu anahtar sadece lig içindir;
  DB, sync, bahçe konum kaydı ve sezon sistemi değişmez.
- Sunucu dört karşılaştırılabilir üreticiyle sıralama üretebilirken bölge kartı
  beş üretici arıyordu. Etiket artık yayımlanmış gerçek Top 20'nin varlığını kullanır.
  Kart tasarımı, boş durum, puan formülü ve minimum veri kuralı değişmedi.

## Android ↔ backend gerçek sözleşme karşılaştırması

| Veri | Backend yazımı | Android okuması / kullanım |
|---|---|---|
| Sezon kökü | zeytin_leagues/{year} | Aynı; aktif uygulama sezonu |
| Aktif sürüm | current_snapshot | Aynı; sürüm kimliği doğrulanır |
| Sürüm yolu | snapshots/{generation} | path(year, version), aynı |
| Tamlık / politika | complete=true, policy_version=1 | Aynı; uyumsuzsa fallback |
| Tazelik | published_at Timestamp | Aynı; 24 saatten eskiyse fallback |
| Bölge | regions/{province_district} | Belge ID r.id; yerel anahtar düzeltildi |
| Görünen ad | name | getString("name") |
| Üretici sayısı | producer_count | integer("producer_count") |
| Toplam kg / tutar | kg / amount | number("kg") / number("amount") |
| Ortalama | weighted_average=amount/kg | UI amount/kg hesaplar; eşdeğer |
| Baskın elek | dominant | Bölge kartı aynı alanı okur |
| Top 20 | top listesi, en fazla 20 | Aynı; sıra, miktar, tekrar ve bölge kontrolü |
| Kişisel sıra | members/{auth UID} | Aynı; yalnızca aktif UID get |
| Güvenli public kimlik | producer_id | p.id; gerçek UID yayımlanmaz |
| Üretici bölgesi | region_id | p.region |
| Sıra / puan | rank / score | Aynı; rank>=1, score 0–100 |
| Üretici toplamları | kg / amount | p.kg / p.amount; average()=amount/kg |
| Elek dağılımı | grades[6] | Aynı sıra; toplam kg ile tutarlılık kontrolü |
| Üretici baskın elek | grades'den türetilir | dominant() ile aynı kalite sırası |
| Uygunluk | Sadece uygun üretici için member/top yazılır | Geçerli satır p.eligible=true |
| Bölge durumu | confidence verified/insufficient | UI yayımlanmış top varlığıyla durum türetir |
| Ek metadata | season, updated_at, source_cutoff | Tanısal; Android'in okuması gerekmiyor |

Backend totalKg/averagePrice adında farklı alan yazmıyor. kg/amount sözleşmesi
iki tarafta ortak. Eski dokümandaki örnek alan isimleri gerçek sözleşmenin yerine
geçmez. Sunucu güven bilgisi yalnızca tam ve güncel manifest arkasında kullanılır.
Public bölgeler ve Top 20 oturum açmış kullanıcılara açıktır; anonim internet
okuması yoktur. SDK metotları derlenip yerel olarak yüklenebildi; bu deploy değildir.

## Test sonuçları

Node 22.23.2, JDK 17, Firebase CLI 14.15.2, Firestore Emulator 1.19.8.
Production bağımlılıkları npm ci ile mevcut lock'tan kuruldu.

- Emülatör içinde tam suite: **30 PASS, 0 FAIL, 0 SKIPPED**.
- Backend syntax npm run check: PASS.
- Gerçek ZeytinLeagueScore.java sınıfıyla 40 durum puan/kalite/fiyat/hacim eşitliği: PASS.
- Gerçek Android parser ve regionKey metotları Java'ya derlenerek backend satırı,
  #37, ortalama, baskın elek, bozuk sıra reddi ve bölge anahtarları: PASS.
  Bu test gerçek metot gövdelerini kaynak dosyadan alır; Android/Firebase callback
  katmanını taklit ederek cihaz testi yaptığını iddia etmez.
- Gerçek Firestore Admin SDK üzerinden pipeline: 25 üretici, üç bahçe tek üretici,
  checkpoint replay, complete generation, 20 public satır, private #21: PASS.
- Mevcut hata enjeksiyonlu runtime testleri: 100 üretici, #37, kesilen yazma,
  önceki sağlam pointer, retry, superseded iş, duplicate evidence: PASS.
- 99/100 kg, sezon/bölge/owner ayrımı, outlier, kalite avantajı, hacim sınırı,
  deterministik tie-break, istemci rank/score'una güvenmeme: PASS.
- Security Emulator: kendi private sıra get açık; başkasınınki/list kapalı;
  public bölge okuma açık; pointer/meta/region/member ve evidence/config/job/lock
  yazma kapalı; kendi private sync açık, diğer sahibine yazma kapalı: PASS.
- Mevcut sohbet/notification testleri: PASS.
- 77 Java dosyası syntax parse: PASS; 104 XML parse: PASS.
- assembleDebug: BLOCKED, yukarıdaki ağ/Gradle hatası. APK üretilmedi.

Test fixtures yalnızca localhost emülatörde demo-zeytin-league ve
 demo-zeytin-pipeline projelerinde oluşturulur. İkinci proje pipeline testinin
 security testindeki clearFirestore ile çakışmasını önler. singleProjectMode=false
 sadece yerel emulator ayarıdır. Production projesi bu testlerde kullanılmaz.

Tekrar çalıştırma (Node 22, JDK 17, Firebase CLI kurulu ortam):

```sh
cd functions
npm ci
npm run check
cd ..
npm --prefix functions/test/rules ci
firebase --config firebase.emulator.json --project demo-zeytin-league emulators:exec --only firestore "node --test functions/test/*.test.js"
./gradlew assembleDebug
```

Emülatörsüz npm test çalıştırılırsa iki emulator testi SKIPPED olur. Release için
emülatörlü 30/30 sonucu aranmalıdır. Test-only dependency lock bu RC'ye eklendi.

## Veri ve regression sınırı

ElekRepository, DatabaseHelper, SessionManager, ZeytinLeagueScore, tüm sync
sınıfları, ProducerFeedActivity, Manifest, Android resource dosyaları,
Firebase Auth, google-services.json, firestore.rules, storage.rules ve mevcut
backend production kodu başlangıç ZIP'ine göre byte düzeyinde değişmedi.
Ana Sayfa/Bahçeler/Elek/Asistan/Sohbet/Ayarlar navigasyon ve listener kodları aynı.
Yerel arşiv sorgusu owner ve sezon filtrelerini, grade SUM'larını koruyor.
522 kg / 31.276 TL / 59,92 hesabı hard-code edilmedi; kayıtlar cihazda yeniden
çalıştırılmadığı için bu gerçek hesabın cihaz smoke kontrolü yine gereklidir.

## Gerçek satış kanıtı ve yayın akışı

users/{uid}/gardens altındaki Elek/satış kaynakları tekrar kopyalanmaz.
league_sale_attestations/{proofId} yalnızca güvenilir Admin yetkisiyle yazılabilir.
Gerekli alanlar: schema_version=1, status=verified, uid, garden_id, record_id,
sale_garden_id, sale_id, season, evidence_id, verifier, verified_at_ms,
expires_at_ms, record_digest, sale_digest, region_digest.
proofId ve digest'ler functions/league/policy.js fonksiyonlarından üretilir.
Hash veya market_consent, bağımsız doğrulama değildir. Kanıt, gerçek fiziksel
satışın miktar/fiyat/bölge/sahip bilgisini bağımsız şekilde doğrulamalıdır.
V1 bire bir Elek–satış grade kg/tutar eşleşmesini arar; parçalı satış eşleştirmesi
icat edilmedi. Global toplam sadece onaylı alt küme; yerel önizleme kişisel arşivdir.

Scheduler 6 saatte bir etkin sezonları işler. scan -> owners -> regions -> finish.
Ara çalışma league_backend_jobs/{generation} altındadır. Tamamlanmış bölgeler
ve kişisel sıralar yazıldıktan sonra complete ve current_snapshot tek transaction
ile değişir. Android yarım generation'a geçmez. Eski generation'lar korunur.
Rules mevcut haliyle eski/staged public bölge belgelerinin doğrudan bilinen yoldan
okunmasını engellemez; bunlar anonim özetlerdir. UI yayın sınırı active pointer +
complete kontrolüdür. Özel kaynaklar ve başkasının member belgesi açılmaz.

## Onay SONRASI deployment sırası

Aşağıdaki işlemler bu oturumda YAPILMADI. Canlı değişiklik ayrı kullanıcı onayı ister.

1. BLOCKED maddelerini çöz. Mevcut Firebase project ID'yi google-services.json ile
   karşılaştır; hedefi her CLI komutunda açıkça belirt. Mevcut kuralların ve etkin
   generation işaretçilerinin güvenli yedeğini al. Yeni Firebase projesi oluşturma.
2. Gerekli Firestore/Cloud Functions v2/Cloud Tasks/Cloud Scheduler/Secret Manager
   API ve faturalandırma/bütçe koşullarını doğrula. Firestore konumu ile fonksiyon
   europe-west1 konumunun gecikme/maliyetini kontrol et; sessizce bölge değiştirme.
3. league_backend_config/main: enabled=false, seasons=[etkin gerçek sezonlar].
   En fazla 10 tamsayı sezon, 2000–2100; UI'nin mevcut sezonunu kullan.
4. Secret Manager ZEYTIN_LEAGUE_ID_SECRET: en az 32 karakter rastgele secret;
   kaynak/ZIP/log'a koyma. Worker runtime hesabına secretAccessor ve gereken
   Firestore veri erişimi; scheduler/worker enqueue hesabına Cloud Tasks enqueuer;
   task OIDC hesabına worker için invoker ve gereken serviceAccountUser yetkileri.
   allUsers/allAuthenticatedUsers invoker verme. Admin izinleri rules'u aşabildiği
   için güvenlik yalnızca firestore.rules'dan ibaret değildir.
5. Rules: proje kökündeki firestore.rules bu RC'de DEĞİŞMEDİ. Yayındaki sürümün
   aynı league deny/private member kurallarını içerdiğini kontrol et. Gerekirse
   mevcut sosyal/private bölümleri koruyan TAM dosyayı onayla deploy et:
   firebase --project <MEVCUT_PROJECT_ID> deploy --only firestore:rules
   storage.rules bu iş için deploy edilmez.
6. Indexes: mevcut sorgular tek eşitlik filtresi + documentId ASC kullanır.
   league_sale_attestations.season, rows.uid, owners.region_id için varsayılan
   ascending single-field index gerekir; composite index veya collectionGroup
   sorgusu eklenmedi. Canlı index exemption varsa kaldırma/değiştirme ayrıca
   incelenmeli. Emülatör testi production index varlığını kanıtlamaz. Mevcut
   indexes'i boş bir dosyayla ezme; indeks deployment komutu gereksiz eklenmedi.
7. Önce worker, sonra scheduler deploy et:
   firebase --project <MEVCUT_PROJECT_ID> deploy --only functions:zeytinLeagueWorker
   firebase --project <MEVCUT_PROJECT_ID> deploy --only functions:zeytinLeagueSchedule
   Mevcut meydanReplyCreated trigger'ını yeniden deploy etme.
   Worker queue: europe-west1/zeytinLeagueWorker, tek dispatch/instance/concurrency,
   retry 8, timeout 1800 s. Scheduler 0 */6 * * *, Europe/Istanbul.
8. Gerçek dış kanıttan yetkili onay belgelerini oluştur. En az dört karşılaştırılabilir
   üretici ve her dolu elek için üç diğer üretici şartını doğrula. Kanıt yokken
   enabled=false kalsın. Kontrollü açılış onayından sonra enabled=true yap.
9. Cloud Tasks invoker/dispatch, secret erişimi, scheduler tetikleme, lock ve
   checkpoint ilerlemesini izle. Her bölüm done, complete=true, policy_version=1,
   güncel published_at sonrası pointer'ın tek seferde ilerlediğini doğrula.

Task queue ve varsayılan indeks davranışı için resmi kaynaklar:
https://firebase.google.com/docs/functions/task-functions
https://firebase.google.com/docs/firestore/query-data/index-overview
Admin SDK güvenlik sınırı:
https://firebase.google.com/docs/firestore/security/get-started

## Deployment sonrası smoke testi ve rollback

- Gerçek uygulamada doğru sezon/bölge, ilk 20, #21+ kendi sıra, anonim ad,
  amount/kg, baskın elek ve ilk üç vurgu. Hesap değiştirip başka hesabın sıra/verisi
  sızmadığını kontrol et. Diğer sezon ve bölgeye geç.
- Kanıtsız/100 kg altı kullanıcı kesin sıralamada görünmemeli. Kanıt iptali sonraki
  tamamlanmış snapshot'a yansımalı; acil iptalde pointer geri çekilmeli.
- Ana Sayfa, Bahçelerim, Elek/Arşiv, Asistan kısa/uzun basma, Sohbet, Ayarlar,
  Google giriş ve private sync; gerçek 522/31.276/59,92 yerel önizleme smoke testi.
- Yetkisiz istemci global yazma, başka member get/list denemeleri reddedilmeli.
  Gerçek kullanıcı verisini silme; canlı smoke verisi ayrıca yetkilendirilmelidir.

Rollback: önce config.enabled=false, Scheduler'ı ve worker Cloud Tasks kuyruğunu
pause et. Devam eden worker'ın bitmesini bekle veya kontrollü olarak durdur;
config tek başına başlamış işi durdurmaz. Sonra Admin ortamında pointer'ı yalnızca
önceki TAM, hâlâ geçerli/taze generation'a taşı. Kanıtları iptal edilmiş veya
24 saatten eski generation'ı taze göstermek için published_at değiştirme.
Güvenli eski sonuç yoksa current_snapshot alanını kaldır; Android yerel fallback'e
geçer. Gerekirse önceki fonksiyon kaynak sürümünü yeniden deploy et. Queue'daki
eski görevleri yeniden açmadan job/lock durumunu kontrol et; eski running job'ın
rollback pointer'ını tekrar ezmesine izin verme. SQLite, private raw kaynakları,
aktif generation ve kullanıcı kayıtları silinmez. Rules geri dönüşünde global
write deny ve private member izolasyonu mutlaka korunur.

## Bu RC’deki tam değişiklik listesi

Yüklenen FINAL Backend V1 ZIP esas alınmıştır. Silinen proje dosyası yoktur.

Değiştirilen:

- `ZEYTIN-LIGI-BACKEND-V1.md`
- `app/src/main/java/com/zeytindefteri/app/ZeytinLeagueRepository.java`
- `app/src/main/java/com/zeytindefteri/app/ZeytinLeagueView.java`
- `firebase.emulator.json`

Eklenen:

- `ZEYTIN-LIGI-RELEASE-GATE.md`
- `functions/test/league.contract.test.js`
- `functions/test/league.firestore.test.js`
- `functions/test/rules/package-lock.json`

node_modules, emülatör debug logları ve çalışma yardımcıları paketten çıkarıldı.
Gerekli backend, kalıcı test kaynakları ve test dependency lock dosyası korundu.
