# Zeytin Ligi Dinamik UI V1

Ana kaynak: gönderilen Zeytin-Defteri-FINAL-Zeytin-Ligi-Satis-Dogrulama-V1.zip.
Mevcut çalışma ağacındaki tek farklı onboarding görseli de bu ZIP'teki sürümüyle
alındı; son değişiklik listesi gönderilen ZIP'e göre hazırlanmıştır.
Canlı Firebase deployment, veri yazımı veya kullanıcı kaydı silme yapılmadı.

## UI ve dinamik veri bağlantısı

Mevcut kompakt hero, sezon seçici, premium renkler, kupa/vector kaynakları ve
bölge carousel korunur. Yükleme başlangıcında Bölge / Top 20 / Senin Sıran iskeleti
sayısal sahte değerler olmadan görünür. Hiç bölge yokken de Top 20 boş durum alanı
ve kişisel kart kaybolmaz. Veri geldikçe mevcut render akışı aynı alanları doldurur.

Bölgelere Göre aktif görünümdür. Tüm Türkiye ve Harita açıkça pasiftir; mevcut
backend ulusal sıra/harita yayımlamadığı için Android'de ulusal lig hesaplanmaz.
Bölge puanı alanı backend'de yoktur; uydurulmadı. Üç üreticilik yetersiz bir grubun
ham sayısını Android indiremez; backend summary ne yayımlıyorsa yalnızca onu
kullanır. Yayımlanan producer_count=0, uygun kesin sıra bulunmadığı anlamındadır;
backend dışındaki toplam katılımcı sayısını ifade etmez.

Top 20 backend sırasıyla gösterilir; Android sort/puan hesaplama eklenmedi.
Satırda #, anonim Üretici {rank}, puan ve ikinci kompakt satırda baskın elek,
ağırlıklı ortalama TL/kg, kg bulunur. İlk üçte mevcut altın/gümüş/bronz korunur.
Kişisel sonuç Top 20 dışında veya ilk bölge sayfasında bulunmayan bölgede olsa bile
kendi private member belgesi geldiyse ayrı kartta görünür. Başka bölge seçildiğinde
kullanıcının gerçek kişisel sırası kaybolmaz; bölgesinin farklı olduğu açıklanır.

## Otomatik güncelleme ve hata davranışı

Sadece zeytin_leagues/{year} aktif-generation işaretçisine listener eklenir.
Tüm kullanıcıları dinleyen/indiren sorgu yoktur. Sunucudan yeni pointer veya
silinmiş pointer geldiğinde ekran mevcut read-only published akışını tekrar çağırır.
Yeni pointer için önceki sıra kesin yeni sonuçmuş gibi kullanılmaz. Metadata cache
olayı veya listener hatasında yerel önizleme gösterilir. Sunucuya yeniden bağlanınca
aynı pointer da yeniden okunabilir. Sonuçları yenile düğmesi okuma ve listener'ı
yeniden başlatır; terminal listener hatasında da tekrar deneme mümkündür.

15 saniyede yanıt alınamazsa UI yerel önizlemeye döner. Bu, Firestore isteğini
sunucuda iptal ettiği iddiası değildir; gecikmiş callback request serial ile
reddedilir. Ekran detach olduğunda listener, bekleyen Handler işleri ve worker
kapatılır. Callback'ler view-generation, attachment ve aktif SessionManager UID
ile sınırlandırılır. Aynı anda başlayan eski yenileme, daha yeni sonucu ezemez.
Yerel DB erişimi mevcut executor'da kalır; yeni UI-thread SQL sorgusu yoktur.

Snapshot complete=true, policy_version=1 ve yayımlanma zamanı mevcut 24 saatlik
sözleşmeye uymalıdır. Başta ve sayfa cevabı alınırken kontrol edilir. Ekran açık
kalırken süre dolunca yerel önizlemeye dönüp yeniden okuma denenir. Yenileme
başarısızsa stale sıra kesin sonuç olarak gösterilmez. Yanlış metadata türleri
fallback'e döner. Pagination önceki snapshot nesnesine bağlıdır; yenilemeden
sonra gelen eski sayfa callback'i ekrana uygulanmaz.

## Değişmeyen sözleşme

- zeytin_leagues/{year}: current_snapshot.
- snapshots/{generation}: complete, policy_version, published_at.
- regions/{regionId}: name, producer_count, kg, amount, dominant, top.
- members/{active uid}: producer_id, region_id, rank, kg, amount, score, grades.
- Ortalama UI'da aynı amount/kg'dır; yeni fiyat istatistiği hesaplanmaz.
- Yerel önizleme aynı ElekRepository archive sorgusundan gelir, global sıra değildir.
- market_consent tek başına doğrulama değildir. Attestation/operatör/policy aynı kalır.
- Public alanda e-posta, telefon, UID, evidence veya özel kayıt gösterilmez.
- Bölge kimliği normalize algoritması ve mevcut sezon mekanizması değişmedi.

## Test sonucu ve sınırlar

Node 22.23.2 ile Firestore + Auth emülatörlerinde: **34 PASS / 0 FAIL / 0 SKIPPED**.
Mevcut Java/backend score eşitliği, operator approval/revoke, 0/yetersiz/yeterli
üretici, 25 üreticiden Top 20, kendi #21/#37, çok bahçe tek üretici, sezon/bölge
ayrımı, duplicate, retry ve privacy testleri geçti.

Gerçek Android parser gövdelerini derleyen mevcut contract testine fresh snapshot
sınırları eklendi: güncel ve tam 24 saat geçerli; daha eski, sıfır tarih ve sözleşme
dışında gelecek tarih reddedilir. Bu gerçek cihaz UI testi değildir.
77 Java syntax parse, 104 XML parse kontrolü geçti.

Android assembleDebug: Gradle 8.7 indirmesinde UnknownHostException:
services.gradle.org. Tam Java compile/Android resource linking ve APK build bu
ortamda doğrulanamadı. Bu paketin build geçtiği iddia edilmez.

Gerçek telefonda henüz çalıştırılmamış kontroller: loading/empty görünümü,
320–430 dp ekranlarda ve büyük fontta taşma, listener bağlantı kaybı/geri geliş,
15 saniye timeout, gecikmiş callback, arka plana geçiş/dönüş, sezon/bölge seçimi,
Top 20 dışındaki kişisel kart ve açık ekranda yeni generation değişimi.
Bu davranışlar kodda ele alındı; cihaz/emülatör testinin yerine kaynak incelemesi
geçtiği iddia edilmez. Production-ready onayı Android build ve bu smoke testleri
geçtikten sonra verilebilir. Önceki deployment/IAM/evidence işletim adımları ve
bağımlılık audit engelleri devam eder. Bu UI değişikliği backend'i deploy etmez.

Tekrar test:

```sh
cd functions
npm ci
npm run check
cd ..
npm --prefix functions/test/rules ci
firebase --config firebase.emulator.json --project demo-zeytin-league emulators:exec --only firestore,auth "node --test functions/test/*.test.js"
./gradlew assembleDebug
```

## Koruma denetimi

Gönderilen kaynak ZIP'e göre functions/league, functions/operator, firestore.rules,
firebase.json, SQLite/DatabaseHelper, SessionManager, ElekRepository, satış/sync,
ZeytinLeagueScore, ProducerFeedActivity, Manifest, tüm Android kaynak resimleri ve
XML kaynakları byte düzeyinde korundu. Alt nav ve Asistan listener'ları değişmedi.
522 kg / 31.276 TL / 59,92 TL/kg hesap sorgusu değişmedi; kullanıcının cihazındaki
veritabanı bu ortamda okunmadı. Gerçek veri veya demo üretici uygulamaya eklenmedi.

## Tam dosya listesi

Değiştirilen:

- `app/src/main/java/com/zeytindefteri/app/ZeytinLeagueRepository.java`
- `app/src/main/java/com/zeytindefteri/app/ZeytinLeagueView.java`
- `functions/test/league.contract.test.js`

Eklenen:

- `ZEYTIN-LIGI-DINAMIK-UI-V1.md`

Repository: read-only pointer listener, freshness ve hata durumları.
View: loading/empty iskeleti, filtre durumları, yenileme ve bağımsız kişisel sıra.
Contract testi: gerçek Android freshness metodu için sınır kontrolleri.
Silinen proje dosyası yok. Debug logları, node_modules ve geçici çalışma dosyaları FINAL pakete alınmadı.
