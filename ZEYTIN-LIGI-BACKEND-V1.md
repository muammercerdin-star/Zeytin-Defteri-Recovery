> Güncel Release Candidate denetimi, test sonuçları ve deployment gate için
> ZEYTIN-LIGI-RELEASE-GATE.md dosyasını okuyun. Aşağıdaki V1 sonuçları tarihseldir.

# Zeytin Ligi Backend V1 — finalizasyon / deployment rehberi

## A. Devralınan durum

Önceki çalışma functions/league/policy.js, runtime.js, functions.js dosyalarını,
11 politika testi ve 7 pipeline testi ile mevcut sosyal trigger test uyarlamasını
oluşturmuştu. Mevcut 25 test yeniden çalıştırıldı ve geçti. Sıfırdan başlanmadı.
Android kaynakları ve V2 UI bu backend çalışmasında değiştirilmedi.

## B. Tamamlanan işler

- Gerçek, değiştirilmemiş Java ZeytinLeagueScore derlenerek 40 farklı girdide
  backend ile kalite/fiyat/hacim/puan/uygunluk/aykırılık eşitliği doğrulandı.
- 100 üretici, #37 kişisel sıra, yarım generation, eski snapshot'ın korunması ve
  hatadan sonra duplicate üretmeden yeniden deneme testi tamamlandı.
- Gerçek firestore.rules dosyasını kullanan emülatör izin testi ve yalnızca yerel
  emülatöre bağlanan ayrı test yapılandırması eklendi.
- Backend sözdizimi kontrolü tüm yeni runtime dosyalarını kapsayacak şekilde genişletildi.
- Deployment, güven kaynağı, sınırlamalar ve işletim talimatları tamamlandı.

## C. Backend mimarisi

Cloud Functions v2, mevcut Firebase Admin SDK ve Cloud Tasks kullanılır.
`zeytinLeagueSchedule` Europe/Istanbul saatine göre 6 saatte bir etkin sezonlar
 için iş başlatır. `zeytinLeagueWorker` sıradaki küçük işi yürütür.
Ekran açılması veya her telefon kaydı global hesap başlatmaz.

Pipeline: scan -> owners -> regions -> finish.

- scan: sezonun sunucu onay belgelerini 100'er okuyup, her belge için mevcut
  özel Elek, bahçe ve satış kaynaklarını okur. Puan/sıra alanlarını okumaz.
- owners: aynı UID'nin uygun kayıtlarını tüm bahçelerinden toplar.
- regions: her görev bir bölgenin üretici özetlerini işler. Sıralama ve medyan
  backend'dedir; telefon ham kullanıcı verilerini indirmez.
- finish: tamamlanan yeni sürüme işaretçiyi tek transaction ile taşır.

Kaynaklar zaten senkronize ediliyor:
`users/{uid}/gardens/{gardenId}/elek_records/{recordId}` ve
`users/{uid}/gardens/{gardenId}/sales/{saleId}`. Elek/satış belgelerindeki `grades`
dizisi yerel elek_grades / sale_grades satırlarından gelir. Bahçelerde il/ilçe
vardır. Bunlar ikinci bir kalıcı ham veri koleksiyonuna kopyalanmaz.
İş sırasında oluşturulan rows/owners belgeleri geçici sunucu türetilmiş özetleridir.
Özel not, e-posta, telefon, alıcı veya koordinatlar bu özetlere taşınmaz.

## D. Firestore modeli

Mevcut Android çıktı sözleşmesi aynen kullanılır:

- `zeytin_leagues/{year}`: `current_snapshot`, `updated_at`.
- `zeytin_leagues/{year}/snapshots/{generation}`: `complete`, `policy_version=1`,
  `published_at`, `source_cutoff`.
- `.../regions/{regionKey}`: `name`, `region_id`, `producer_count`, `kg`, `amount`,
  `weighted_average`, `dominant`, `confidence`, `season`, `updated_at`, `top`.
- `.../members/{uid}`: yalnızca bu kullanıcının tam sıra özeti.

Üretici satırı mevcut istemcinin beklediği alanlardır:
`producer_id`, `region_id`, `rank`, `kg`, `amount`, `score`, `grades`.
Public kimlik UID değildir: Secret Manager anahtarıyla HMAC-SHA256 türetilir.
UI isimleri mevcut anonim “Üretici {rank}” biçiminde gösterir.

Yeni, istemcilere tamamen kapalı sunucu alanları:

- `league_sale_attestations/{proofId}`: güvenilir satış kanıtı bağlantısı.
- `league_backend_config/main`: enabled ve seasons.
- `league_backend_locks/{year}`: etkin iş / başlangıç / durum.
- `league_backend_jobs/{generation}` ve rows/owners/regions/claims/steps alt
  koleksiyonları: sürümlü çalışma verisi, tekrar kullanım kontrolü ve checkpoint.

## E. Gerçek onay / kanıt sınırı

`market_consent=1` SADECE paylaşım tercihi olarak değerlendirilir.
SALE kaydı, Firebase Auth girişi, tahmini tutar veya kullanıcının yazdığı
`verified=true` gerçek satış kanıtı değildir.
Projede bağımsız satış kanıtı sağlayıcısı veya doğrulama servisi BULUNMUYOR.
Bu paket bunları varmış gibi taklit etmez ve otomatik onay dağıtmaz.

Güvenilir bir sunucu/işletmeci, gerçek dış kanıtı doğruladıktan sonra,
istemcinin yazamadığı aşağıdaki onayı oluşturabilir. Bunun hangi fatura,
alıcı teyidi veya bağımsız veri sağlayıcısıyla yapılacağı production ön koşuludur.
Bu paket onayı tüketen katmanı ve eşleşme kurallarını tamamlar; dış kanıtı icat etmez.

Onay şeması:

- schema_version: 1, status: verified (iptal için revoked)
- uid, garden_id, record_id, sale_garden_id, sale_id, season
- evidence_id: aynı fiziksel satış için kanıt sağlayıcısının TEK, kararlı kimliği
  (16–256 karakter); bir kez kullanılabilir. Bölünmüş satışlar V1 kapsamı dışındadır.
- verifier: gerçek onayı veren güvenilir kaynak/operatör kimliği
- verified_at_ms, expires_at_ms: sunucunun belirlediği süre; en fazla 366 gün.
- record_digest, sale_digest, region_digest: policy.js içindeki digest
  fonksiyonlarıyla yalnızca kanıtla eşleştirilen veriden üretilir.
- belge ID: policy.proofId(uid,garden_id,record_id).

Hash üretmek doğrulama değildir. Hash, bağımsız doğrulanan belgeyle mevcut
uygulama kaydının daha sonra değiştirilmediğini kontrol eder. Onayı yalnızca
kendi yazılabilir yedeğinden türeten bir trigger eklenmedi.

V1 eşleşmesi açık ve bire birdir: satılan altı elek miktarı ve tutarları Elek
kaydıyla eşleşmeli; uid, kaynak kimlikleri, sezon, paylaşım izni ve bahçe bölgesi
onayla uyuşmalıdır. Eksik kaynak, farklı sahip, değiştirilmiş fiyat/bölge,
süresi dolmuş veya iptal edilmiş kanıt dışlanır. İstemci total_kg/gross_total/
rank/league_score yerine doğrulanan grades satırları yeniden hesaplanır.
Sale ve evidence tekrar kullanımı sunucu claims transaction'ıyla yakalanır;
çakışan iki katılımcı da geçersiz olur.

Global toplamlar SADECE onaylı uygun kayıt alt kümesidir. Bu nedenle kısmen
onaylı bir kullanıcının global toplamı yerel arşiv toplamından düşük olabilir.
Yerel 522 kg / 31.276 TL / 59,92 TL hesabı ve kodu değiştirilmedi. Tüm kayıtlar
onaylıysa aynı kaynak satırlarından aynı toplam çıkar. Üretici ayrıca lig kaydı girmez.

## F. Generation / retry güvenliği

Her sezonun etkin işi kilit belgesiyle takip edilir. Queue'da bir dispatch,
fonksiyonda bir instance/concurrency tutulur; bu V1'in idempotency varsayımıdır.
Bunu artırmadan önce kilit/fencing tasarımını genişletmek gerekir.

Yeni iş benzersiz generation'da hazırlanır. scan/owners/regions adımları
checkpoint tutar. Chunk tamamlanıp checkpoint yazıldıktan sonra sıradaki görev
kuyruğa alınır; enqueue başarısızsa aynı checkpoint üzerinden yeniden denenir.
Kuyruğa almadan önce başlangıçta çökme olursa scheduler aynı işi sürdürebilir.
Duplicate task aynı adımı yeniden toplamaya yol açmaz.

Kaynak cutoff'u iş başlangıcıdır; bundan sonra değişmiş kayıt/onaylar sonraki
hesaba bırakılır. Kayıtların birbirinden bağımsız hash doğrulaması yapılır;
Firestore'un tüm veritabanı için tarihsel bir transaction snapshot'ı iddia edilmez.

Tüm bölgeler done olduktan sonra complete metadata ve current_snapshot aynı
transaction'da yayımlanır. Kısmi yazılar önceki aktif generation'ı değiştirmez.
Eski/superseded iş yeni işaretçiyi ezemez. 4 saatten uzun iş yayımlanmaz.
İstemci mevcut 24 saatlik tazelik kontrolünü kullanır; eski sonuçlarda yerel
önizlemeye döner. Revocation/veri değişimi en geç sonraki başarılı 6 saatlik
hesapta yansır. Acil geri çekmede yönetici aktif işaretçiyi kaldırmalıdır.

## G. Puan, Top 20, kişisel sıra

Java dosyası değiştirilmedi. Node politikası aynı katsayıları uygular:
0.50 kalite + 0.35 fiyat + 0.15 hacim.
Double/1/2/3/4/Yağlık kalite ağırlıkları: 100/90/75/55/30/10.
Hacim: min(100,100*ln(1+kg)/ln(10001)). Minimum: 100 kg ve geçerli fiyatlı veri.

Bölge aynı üreticinin uygun kayıt kg'sinin en çok geldiği il/ilçedir.
Türkçe harf, ASCII yazım, harf büyüklüğü ve boşluk farkları normalize edilir;
Alaşehir/ALAŞEHİR/alasehir aynı manisa_alasehir anahtarına gider.
UI adı ayrıca tutulur. Bahçe bölgesi de onay digest'ine bağlıdır.

Fiyat: aynı sezon/bölgede her elek için en az üç BAŞKA onaylı üretici gerekir.
Üretici/elek başına tek ağırlıklı gözlem kullanılır. Enterpolasyonlu çeyreklik,
1.5 IQR ve medyan, kişinin kendisi hariç hesaplanır. Beklenen tutar,
üreticinin elek kg dağılımıyla ağırlıklandırılır. Mevcut oran eşiği <0.25 veya >3
ise üretici dışlanır. Fiyat=clamp(100*(oran-0.5),0,100).

En az dört karşılaştırılabilir üretici olmadan kesin sıra yoktur. Bu yüzden
bir veya iki üretici kendi yerel kalite/hacim/ön puanını görür; sahte #1 üretilmez.
Aykırı/yetersiz referanslı üreticiler çıkarılarak kohort kararlı hale getirilir;
12 turda kararlılaşmazsa kesin sıra üretilmez. Puan katsayıları değişmez.
Sıra: puan azalan, kalite azalan, takma üretici kimliği artan.
Public top en fazla 20; tüm uygun üreticiler için private members belgesi yazılır.
#21, #37 ve sonrası bu belge üzerinden mevcut “Senin Sıran” kartına gelir.

## H. Security rules / IAM

firestore.rules birebir korundu. Mevcut zeytin_leagues kuralları istemci
write işlemlerini reddeder; members için yalnızca kendi UID'sine get izni vardır,
list ve başka UID erişimi kapalıdır. Bölgesel sonuçlar giriş yapanlara okunur.
Yeni sunucu koleksiyonları mevcut default-deny altında kalır.
Mevcut özel yedek/sosyal/Auth/Storage izinlerine müdahale edilmedi.

Admin SDK kuralları bypass eder; güven sınırı sunucu IAM erişimidir.
Onay yazma yetkisi ve worker çağırma yetkisi kullanıcıya verilmemelidir.
Worker public callable değildir: Cloud Tasks IAM kimliğiyle çağrılır.
Referanslar: https://firebase.google.com/docs/functions/task-functions
ve https://firebase.google.com/docs/firestore/security/get-started

## I–K. Doğrulama ve ortam sınırları

- Devralınan 25 test geçti.
- Final: 28 test; 27 geçti, 1 emülatör testi ATLANDI. Başarısız test yok.
- Java–Node eşitliği: gerçek Java sınıfıyla 40 durum geçti.
- Pipeline testleri bağımlılık enjeksiyonlu Firestore test doubles üzerinde
  gerçek runtime.js akışını yürütür; canlı Cloud Tasks/Firestore testi değildir.
- 100 üretici, Top20, #37, eski snapshot, retry ve aynı UID/3 bahçe senaryoları geçti.
- Emülatör testi hazır; emülatör/bağımlılıkları bu ortamda çalıştırılamadı.
- npm ci --offline: ENOTCACHED. Firebase Node bağımlılıkları kurulamadı;
  gerçek SDK'yla deploy discovery yapılmadı. package-lock bağımlılıkları korunur.
- Yerel Node 24.19.0; hedef runtime Node 22. Production öncesi Node 22'de test gerekir.
- Android assembleDebug: Gradle 8.7 indirme adresi services.gradle.org erişilemiyor.
  APK derlemesi doğrulanamadı. Android kaynakları birebir korundu.
- CANLI FIREBASE'E HİÇBİR DEPLOYMENT, ONAY BELGESİ VEYA TEST VERİSİ YAZILMADI.

## L. Production öncesi adımlar — ayrı kullanıcı onayından sonra

1. Tam proje ZIP'ini aç. Mevcut google-services.json içindeki project_id'yi
   Firebase CLI hedefiyle karşılaştır. Yeni Firebase projesi oluşturma.
2. Node 22 ve JDK 17 kur. functions dizininde `npm ci`, `npm run check`,
   `npm test` çalıştır. Offline testin SDK kurulumunu doğrulamadığını unutma.
3. Emülatör test araçları için `npm --prefix functions/test/rules install`.
   Firebase CLI kurulu ortamda, proje kökünde yalnızca demo hedefle:
   `firebase --config firebase.emulator.json --project demo-zeytin-league emulators:exec --only firestore "node --test functions/test/league.rules.test.js"`
   Test FIRESTORE_EMULATOR_HOST olmadan atlanır; deployment öncesi atlanmış değil,
   gerçekten geçmiş sonuç beklenmelidir. Harness production host'unu reddeder.
4. Cloud Functions, Cloud Tasks, Cloud Scheduler ve Secret Manager gereksinimlerini,
   proje faturalandırmasını ve bütçeyi kontrol et. Worker çağıran servis hesabına
   ilgili serviste invoker; enqueue yapan hesaba tasks.create/enqueuer ve gerekli
   service-account kullanım izinlerini sınırlandır. allUsers invoker verme.
5. En az 32 karakter rastgele ZEYTIN_LEAGUE_ID_SECRET değerini Secret Manager'a
   güvenli etkileşimli girişle ekle; kaynak/ZIP/log içinde saklama. Worker'a
   secret erişimi ver. Anahtarı değiştirmek public takma kimlikleri değiştirir.
6. Gerçek satış kanıtı sağlayıcısı/operatör sürecini tamamla. Onayı sadece
   kullanıcının beyanından üretme. Onay şemasını yukarıdaki digest fonksiyonlarıyla
   uygula. Önce kontrollü ayrı test projesi/emülatörde kaynak eşleşmesini dene.
7. KULLANICI CANLI DEPLOYMENT'I AYRICA ONAYLADIKTAN SONRA, mevcut project ID ile
   yalnızca yeni iki fonksiyonu dağıt:
   `firebase --project <MEVCUT_PROJECT_ID> deploy --only functions:zeytinLeagueSchedule,functions:zeytinLeagueWorker`
   Mevcut sohbet trigger'ını yeniden dağıtmak gerekmez. Kurallar mevcut yayındaki
   sürümden farklıysa ayrıca gözden geçir ve onayla; bu ZIP'te rules değişmedi.
8. Güvenilir yönetim ortamından league_backend_config/main belgesini önce
   `{enabled:false,seasons:[2026]}` olarak hazırlayıp izinleri doğrula.
   Gerçek onaylı örnekler ve deployment kontrolü tamamlanınca enabled=true yap.
   Liste yoksa kanıt/uygunluk/3 diğer üretici şartını kontrol et; sahte üretici ekleme.
9. İlk başarılı generation'ın complete, regions, members ve current_snapshot
   değerlerini ve gerçek uygulamada #21+ hesabını kontrol et. Eski aktif generation
   geri dönüş için saklanabilir. Geri alma yalnızca mevcut tam generation'a yapılır.

## Ölçek, işletim ve kalan gerçek sınırlar

Hesap ekranda çalışmaz; altı saatlik sunucu işidir. Tarama 100 belge, sayfalı
okuma 500 belge, yazma 200 belgeyle sınırlıdır. Bir producer en fazla 10.000
onaylı kayıt; bir bölge en fazla 500.000 özet; sınır aşımında kısmi yayın yapılmaz.
Leave-one-out medyanlar her kişi için tüm listeyi kopyalamaz; sıralı diziler ve
ikili aramayla O(N log N) yaklaşımı kullanır.

Bu limitler 500.000 gerçek kullanıcı için yük/maliyet garantisi değildir.
V1 tek dispatch ile çalışır; ağ gecikmesi, kanıt sayısı, bölge yoğunluğu,
4 saatlik job ve 30 dakikalık görev sınırı production'da ölçülmelidir.
Gerekirse sonraki aşamada region görevlerini fencing ile paralelleştir, kapasite
ve çalışma aralığını ölçümlere göre ayarla. Şu aşamada sessizce limit artırma.
Mevcut Android adaptörü sayfa başına 50 bölge + her biri en fazla 20 özet ve tek
kişisel belge okur; tüm kullanıcılar indirilmez. V2 UI/kod korunmuştur.

Başarısız işler ve eski generation'lar ayrı private/public ağaçlarda kalır.
Operatör en az 48 saat geçmiş, aktif işaretçinin göstermediği ve çalışmayan
eski ağaçları Admin SDK recursiveDelete ile temizlemelidir. Parent'a TTL koymak
alt koleksiyonları silmez. Otomatik/destrüktif temizlik bu pakete eklenmedi.
Kanıt kimlikleri ve çalışma ağaçları için erişim/retention süresini işletim
politikasında belirle; ham kullanıcı kayıtlarını temizleme.

## Tam dosya değişiklik listesi ve son denetim

V2 UI kaynak ZIP karşılaştırması; silinen dosya yok.

Değiştirilen:

- `ZEYTIN-LIGI-ENTEGRASYON.md`
- `functions/index.js`
- `functions/package.json`
- `functions/test/trigger.test.js`

Eklenen:

- `ZEYTIN-LIGI-BACKEND-V1.md`
- `firebase.emulator.json`
- `functions/league/functions.js`
- `functions/league/policy.js`
- `functions/league/runtime.js`
- `functions/test/league.parity.test.js`
- `functions/test/league.policy.test.js`
- `functions/test/league.rules.test.js`
- `functions/test/league.runtime.test.js`
- `functions/test/rules/package.json`

Android app/ ağacının tamamı byte düzeyinde aynı. firestore.rules, Storage kuralları, Firebase yapılandırması ve mevcut dependency lock dosyası değişmedi. 77 Java dosyası parse kontrolünde sıfır hata; 104 XML dosyası parse edildi. Bu kontroller Android derlemesi değildir. Backend npm run check geçti. Geçici script, node_modules, build çıktısı veya test verisi FINAL ZIP’e eklenmedi.
