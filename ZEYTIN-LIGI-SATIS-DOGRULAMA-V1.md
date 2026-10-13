# Zeytin Ligi — Satış Doğrulama V1

Bu çalışma Backend V1 Release Candidate üzerine uygulanmıştır. Android ağacı,
ZeytinLeagueScore, policy.js, runtime.js ve mevcut lig snapshot sözleşmesi korunur.
Canlı Firebase'e deploy, custom claim veya onay belgesi yazılmadı.

## Ne tamamlandı?

Var olan league_sale_attestations modeli için güvenilir operatör onay/iptal servisi,
Firebase Auth rol kontrolü, atomik tek kullanım koruması, değişiklik geçmişi ve
operatör komut araçları eklendi. Yeni ham satış kopyası veya ayrı lig kayıt sistemi yok.
Android'de form, fotoğraf/belge yükleme, doğrulama düğmesi veya UI değişikliği yoktur.

Bu bir SERVER / OPERATOR APPROVAL sistemidir. Kod, faturanın veya alıcı teyidinin
sahada gerçek olduğunu kendi başına kanıtlayamaz. Yetkili operatör bağımsız kanıtı
kontrol eder; sunucu operatör yetkisini ve kanıtla uygulama kaydının eşleşmesini
zorunlu tutar. Sadece SQLite/Firestore kullanıcı beyanını okuyup otomatik onaylayan
trigger eklenmedi. market_consent=1 yalnızca paylaşım iznidir.

## Akış

1. Kullanıcı mevcut satış/Elek ekranını kullanır. Mevcut private sync kaynakları:
   users/{uid}/gardens/{gardenId}/elek_records/{recordId} ve .../sales/{saleId}.
   grades dizileri SQLite elek_grades / sale_grades kaynaklarından gelir.
2. Gerçek operatör bağımsız belgeyi veya alıcı teyidini inceler. Belgede doğrulanamayan
   sahip, sezon, bölge, kg, elek dağılımı veya tutar varsa onay vermez.
   Kullanıcının ekran görüntüsü, paylaşım izni veya kendi yazdığı fiyat tek başına yeterli değildir.
3. Operatör zeytinLeagueApproveSale callable servisine incelenmiş değerleri yollar.
   Firebase SDK giriş token'ını doğrular. Servis hem imzalı token'daki league_operator
   claim'ini hem Firebase Auth getUser ile güncel sunucu rolünü kontrol eder.
   Kapatılmış hesap, kaldırılmış rol, iptal edilmiş eski oturum ve kendi satışını
   onaylama reddedilir. request.data.isAdmin veya profil alanı kullanılmaz.
4. Tek Firestore transaction içinde kaynaklar, sahip/kimlik/sezon, mevcut digest
   politikası, iki bahçenin bölgesi ve operatörün incelenmiş miktar/tutar/elek
   dağılımı karşılaştırılır. Uyumsuzlukta hiçbir onay/claim/audit yazılmaz.
5. Onay, iki tek kullanım kilidi ve audit kaydı aynı transaction ile yazılır.
   Tekrar gelen aynı request_id + içerik + operatör, aynı sonucu döndürür.
   Eski expected_revision ile gelen farklı işlem reddedilir.
6. Mevcut planlı lig işi onayı okur ve kaynakları yeniden doğrular. En az 100 kg,
   geçerli fiyat, aynı bölge/sezon ve yeterli karşılaştırılabilir üretici şartları
   sağlanırsa global snapshot'a girer. Onay tek başına #1 oluşturmaz.

## Kim yetkili?

Normal Android kullanıcıları hiçbir attestation/claim/audit yazamaz veya okuyamaz.
Custom claim sahibi operatör dahi Firestore istemci SDK'sıyla doğrudan yazamaz;
backend callable işlemini kullanır. Backend Admin SDK, kendi IAM yetkisiyle yazar.

league_operator=true yalnızca güvenilir yönetici ortamında Firebase Admin SDK ile
verilir. Profil belgesindeki isAdmin/role/verified alanları yetki kaynağı değildir.
Her istek getUser yaptığı için rol kaldırıldığında eski token yeni onay veremez.
İşlem kabulü ile transaction'ın bitişi arasındaki kısa süre için rol kontrolü bir
Auth+Firestore ortak transaction değildir; acil durumda endpoint de durdurulmalıdır.

Rol yönetimi için functions/operator/role.js eklendi. Yönetici ADC/Firebase Auth
kullanıcı yönetme yetkisi gerekir; bu yetki normal operatöre veya Android'e verilmez.
Mevcut diğer custom claim'ler korunur. Rol kaldırmada refresh token'lar iptal edilir.
Aynı hesapta başka claim yöneticileriyle eşzamanlı rol yazımı yapma.
Hiçbir credential, özel anahtar veya gerçek ID token ZIP içinde yoktur.

## Mevcut onay şeması ve ek alanlar

| Alan | Kaynak / anlam |
|---|---|
| uid | Mevcut owner_uid'nin Auth karşılığı; mevcut sözleşme adı korunur |
| garden_id, record_id | Eşleşen özel Elek kaydı |
| sale_garden_id, sale_id | Eşleşen özel satış kaydı |
| season | Gerçek kaynak sezonu; uyuşmazlık reddedilir |
| schema_version=1, status | verified / revoked |
| evidence_id | ev1_ + kanonik dış kanıt referansının 64 karakter SHA256 özeti |
| evidence_type | independent_document veya buyer_confirmation |
| verifier, verified_at_ms | Sunucunun belirlediği operatör UID'si ve zaman |
| expires_at_ms | 2–366 gün içinde operatörün belirlediği geçerlilik |
| record_digest, sale_digest, region_digest | Mevcut policy.js ile sunucuda hesaplanır |
| region_id | Mevcut normalize edilmiş il_ilçe anahtarı |
| matched_kg, matched_amount | Kaynaklarla eşleşen gerçek toplamlar |
| revision, last_request_id, last_command_hash, last_actor | Eski istek/duplicate koruması |
| revoked_at_ms, revoked_by, reason_code | İptal işlemi bilgisi |

Belge ID mevcut policy.proofId(uid,garden_id,record_id) olarak kalır.
Attestation ve audit herkese açık değildir. Telefon/e-posta/not/koordinat taşınmaz.
Public lig çıktısı yalnızca mevcut anonim producer_id ve lig özetidir.

Yeni league_verification_claims/{key} yalnızca attestation_id tutan kalıcı tek
kullanım indeksidir; ikinci bir satış veya evidence kopyası değildir.
Anahtarlar evidence hash'i ve uid+sale_garden_id+sale_id hash'idir. Transaction'da
bu belirli belgelerin okunması, eşzamanlı iki farklı hedefin aynı kanıtı kazanmasını
engeller. İptalde kilitler silinmez. Kanıt başka kullanıcıya yeniden atanmaz.
Audit yolu: league_sale_attestations/{id}/audit/{revision}; eylem, operatör,
zaman, request/digest ve durum tutar. İstemciye kapalıdır.

## Kanonik kanıt kimliği

Operatörler aynı fiziksel satış için aynı reference üretmelidir:

    evidence_id = "ev1_" + SHA256(policy.canonical([provider_namespace, external_sale_reference]))

provider_namespace ve external_sale_reference biçimi gerçek kanıt sağlayıcısı
bazında yönetici tarafından bir kez belirlenmelidir. Örneğin belgenin kararlı satış
referansı kullanılır; rastgele UUID, owner UID, yükleme zamanı veya dosyanın her
kopyasında değişen hash kullanılmaz. İki belge aynı satışı kanıtlıyorsa aynı kararlı
satış kimliğine bağlanmalıdır. Aynı fiziksel satışı farklı ID'lerle sunan kötü niyetli
operatörü yalnızca bir hash algoritması tespit edemez; operatör süreç denetimi gerekir.
Şifrelenmemiş belge/telefon/e-posta evidence_id yerine yazılmaz.

Daha önce manuel oluşturulmuş onaylar varsa legacy evidence_id ve satış sorguları
tekrar kullanımı kontrol eder. Eski farklı evidence adlandırmasının yeni ev1_ referansı
ile fiziksel eşdeğerliği otomatik bilinmez. Geçişten önce yetkili yönetici eski
kanıtları envanterlemeli; eski manuel onay yazıcıları durdurulmalı ve aynı kanonik
kimlik politikası uygulanmalıdır. Mevcut belgeler otomatik silinmez veya dönüştürülmez.

## Operatör komutu sözleşmesi

Onay komutundaki reviewed alanları mevcut kayıttan körlemesine kopyalanmamalı;
bağımsız kanıtta görülen değerlerle doldurulmalıdır. Tüm kg/tutar farkları en fazla
0,01; altı elek için hem kg hem tutar karşılaştırılır. Ham grade kg*fiyat=line_total
kontrolü mevcut policy toleransıyla ayrıca uygulanır. Grade sırası:
Double, 1 No, 2 No, 3 No, 4 No, Yağlık.

Gerekli alanlar:

- action: approve
- uid, garden_id, record_id, sale_garden_id, sale_id: gerçek kaynak kimlikleri
- season: tamsayı sezon
- request_id: işlem için tek kararlı kimlik; aynı retry'da değiştirme
- expected_revision: ilk onayda 0, yeniden incelemede mevcut revision
- evidence_id, evidence_type, expires_at_ms
- reviewed: region_id, kg, amount, grades[6] (kg), values[6] (TL)

rank/score/isAdmin gibi fazladan alanlar reddedilir. Servis sadece
attestation_id, status, revision döndürür; başka kullanıcının özel kaydını döndürmez.
Mevcut attestation başka satış/evidence kimliğine bağlanamaz. Yeniden inceleme aynı
kaynağa aynı kanıtla, yeni request_id ve güncel revision ile yapılabilir.

İptal komutu:
action=revoke, uid, garden_id, record_id, request_id, expected_revision,
reason_code=evidence_withdrawn | source_mismatch | operator_error.

İptal sonraki tamamlanmış lig hesabına yansır. Önceden yayımlanmış immutable snapshot
anında değiştirilmez. Acil geri çekme için önceki release raporundaki pointer geri
çekme prosedürü kullanılır; eski snapshot'ı taze göstermek için tarih değiştirilmez.

## Deployment ve kullanım — bu oturumda çalıştırılmadı

1. Önceki RELEASE GATE engelleri olan Android build/cihaz kontrolü, bağımlılık
   güvenlik bulguları, IAM ve hedef proje ayarlarını çöz. Bu görev SDK major sürümü
   yükseltmez; önceki audit bulgularının çözüldüğünü iddia etmez.
2. Gerçek operatörü, bağımsız kanıt standardını ve kanonik satış kimliği kuralını
   belirle. Kendi satışını onaylayamayacağı için gerçek ayrı operatör hesabı gerekir.
   Otomatik sahte operatör/kullanıcı oluşturma kodu yoktur.
3. Ayrı canlı onaydan sonra, hedef project ID'yi kontrol ederek tam güncel
   firestore.rules dosyasını deploy et. Sosyal/device/private kuralları korunur.
   firebase --project <MEVCUT_PROJECT_ID> deploy --only firestore:rules
4. Yalnızca yeni callable fonksiyonu deploy et:
   firebase --project <MEVCUT_PROJECT_ID> deploy --only functions:zeytinLeagueApproveSale
   Önceki lig worker/scheduler henüz deploy edilmediyse önceki deployment sırasını
   ayrıca uygula. Mevcut sohbet trigger'ını yeniden deploy etmek gerekmez.
5. Backend service account'a gereken Firestore veri erişimi ve Firebase Auth
   kullanıcı okuma yetkisini ver; rol verme yetkisini sadece yöneticiye ayır.
   Callable HTTP taşıması erişilebilir olsa da Firebase token+sunucu rolü zorunludur.
   Cloud Tasks worker'ının ayrı IAM invoker kısıtlarını gevşetme. Operator script'i
   App Check token'ı kullanmaz; endpoint App Check zorunluluğu bu V1'de etkin değildir.
6. Yönetici güvenilir ortamında, functions dizininde ve ayrı claim onayından sonra:
   node operator/role.js <MEVCUT_PROJECT_ID> <OPERATOR_UID> grant --apply
   Geri almak için grant yerine revoke. Gerçek claim işlemleri bu oturumda yapılmadı.
7. Operatör aynı Firebase projesinde kendi gerçek hesabıyla giriş yapar ve Firebase
   Auth SDK currentUser.getIdToken(true) ile güncel ID token alır. Service account
   OAuth token'ı veya kullanıcının gönderdiği token kullanılmaz. Kısa ömürlü ID token'ı
   yalnızca güvenilir cihazda erişimi kısıtlı bir dosyada tut; log/mesaj/ZIP'e koyma.
   Token edinmek için Android uygulamasına debug/token gösterme kodu eklenmedi.
8. Dış kanıttan hazırlanmış JSON komut dosyası ile:
   node operator/submit.js <MEVCUT_PROJECT_ID> <COMMAND_JSON> <TOKEN_FILE> --apply
   Araç onay/iptal için aynı callable protokolünü kullanır; otomatik tarayıp onay vermez.
   TOKEN_FILE içeriği komut satırı veya log'a basılmaz. İş bitince token dosyasını
   güvenilir ortamda kaldır. Yetki/kanıt kontrolü her zaman sunucudadır.
9. Yeni sorgular için evidence_id, uid, sale_garden_id, sale_id single-field
   indekslerini kontrol et. Üç equality filtreli legacySale sorgusu index merging
   kullanır; ek composite index dosyası eklenmedi. Canlı exemption varsa ayrıca
   değerlendir; emülatör canlı indeks varlığını kanıtlamaz.
10. Onaylı denemeden sonra claim/audit atomikliği, normal kullanıcı reddi, rol
    kaldırma ve sonraki lig snapshot'ını kontrol et. Sahte üreticileri production'a
    ekleme. Raw kayıtlar değişirse mevcut digest kontrolü sıradan çıkarmalıdır.

Geri alma: endpoint'i durdur, operatör claim'ini kaldır ve refresh token'ları iptal et.
Hatalı onayı revoke et; gerekiyorsa aktif lig pointer'ını güvenli şekilde geri çek.
Tek kullanım kayıtlarını silip kanıtı yeniden kullanılabilir yapma. Eski rules'a
körlemesine dönüp doğrulama alanlarını istemci yazımına açma. Ham kayıtlar silinmez.

## Security rules değişikliği

users/{uid} altındaki mevcut generic private backup kuralında doğrulama alanlarının
istemci create/update işlemleri sınırlandı. Alanlar: verified, approved, attested,
verified_sale, verification_status, evidence_status, verified_at, verified_by,
approved_at, approved_by, attestation_id. Var olan başka bir alanı güncellemek
serbesttir; server tarafından konmuş bir doğrulama alanını değiştirme/kaldırma yasaktır.
Mevcut Android sync bu alanları kullanmaz; onay private sale belgesine kopyalanmaz.
Normal satış okuma/yazma/silme izinleri, owner izolasyonu ve devices özel kuralları
korunur. Diğer private modellerde de aynı adlarla sahte onay alanı yazılamaz.
league_sale_attestations, audit ve yeni tek kullanım indeksi default-deny ile
kapalıdır. Operatör claim'i Firestore client'e doğrudan onay yazma yetkisi vermez.

## Kaynaklar

- https://firebase.google.com/docs/auth/admin/custom-claims
- https://firebase.google.com/docs/functions/callable
- https://firebase.google.com/docs/firestore/manage-data/transactions

## Son test ve build sonucu

Node 22.23.2, JDK 17, Firebase CLI 14.15.2 ve yerel Firestore/Auth emülatörleriyle:
**34 PASS / 0 FAIL / 0 SKIPPED**. npm run check PASS.

Yetki testi gerçek Auth emülatöründe token/claim/getUser kullanır. Onay servisi
Firestore emülatöründe gerçek transaction'larla test edilir. Callable'ın canlı
HTTPS/IAM uçtan uca deployment testi yapılmadı. SDK ile fonksiyon export'ları
yüklenebildi. İlk Auth testindeki demo proje audience ayarı düzeltildi; son tur
başarılıdır. Test hesapları/satışları yalnızca demo emülatör verileridir.

Senaryolar: normal kullanıcı reddi, client isAdmin reddi, kapatılmış/kaldırılmış
operatör ve iptal edilmiş oturum, self-approval reddi, owner/sale/season/region/
kg/tutar/grade uyuşmazlıkları, consent tek başına onay olmaması, eşzamanlı duplicate
evidence yarışında tek başarı, aynı sale'in iki Elek kaydına bağlanamaması,
revocation/retry/revision/audit, kaynak değişince sıralamadan çıkma ve gerçek
onaydan sonra mevcut lig pipeline'ına katılım. Eski lig/sohbet testleri de geçti.

Tekrar çalıştırma:

```sh
cd functions
npm ci
npm run check
cd ..
npm --prefix functions/test/rules ci
firebase --config firebase.emulator.json --project demo-zeytin-league emulators:exec --only firestore,auth "node --test functions/test/*.test.js"
./gradlew assembleDebug
```

Emülatörsüz testte dört emulator testi atlanır; release için emülatörlü sonuç
aranmalıdır. Android build Gradle 8.7 indirmesinde services.gradle.org
UnknownHostException ile engellendi; APK compile doğrulanamadı. 77 Java syntax
parse ve 104 XML parse kontrolü geçti. Android app/ ağacı yüklenen RC ZIP ile
byte düzeyinde aynıdır: UI, yerel 522/31.276/59,92 hesabı, DB, Auth, Asistan,
navigasyon ve kullanıcı/sezon kaynakları değişmedi. Gerçek cihaz verisi bu ortamda
çalıştırılmadı. Production açılışı için Android build/smoke, önceki dependency audit
bulguları, gerçek operatör/kanıt süreci ve ayrıca onaylı deployment tamamlanmalıdır.

## Tam dosya listesi

RC ZIP karşılaştırması. Silinen proje dosyası yoktur.

Değiştirilen:

- `ZEYTIN-LIGI-RELEASE-GATE.md`
- `firebase.emulator.json`
- `firestore.rules`
- `functions/league/functions.js`
- `functions/package.json`
- `functions/test/league.rules.test.js`

Eklenen:

- `ZEYTIN-LIGI-SATIS-DOGRULAMA-V1.md`
- `functions/league/approval.js`
- `functions/operator/role.js`
- `functions/operator/submit.js`
- `functions/test/league.approval.auth.test.js`
- `functions/test/league.approval.firestore.test.js`
- `functions/test/league.approval.test.js`

Geçici script, node_modules, debug log veya çalışma çıktısı FINAL pakete konmadı.
Operatör araçları ve testler kalıcı, gerekli proje kaynaklarıdır.
