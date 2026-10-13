# İş Emri 06.3C — Süper Admin + Moderatör Yetki Backendi

## Teslim durumu

06.3B ana kaynak üzerine backend uygulandı. 06.3D Android Yetki Yönetimi ekranına başlanmadı. Production deploy ve gerçek hesaba Super Admin ataması yapılmadı; ZIP'in teslim edilmesi canlı sistemi etkinleştirmez.

Kaynak: Zeytin-Defteri-IS-EMRI-06-3B-MODERASYON-MERKEZI-FINAL.zip.

## Güvenlik modeli

- Normal kullanıcı: rol yönetimi yok.
- Moderatör: `moderation_admin === true`; mevcut Moderasyon Merkezi korunur, rol yönetimi yapamaz.
- Super Admin: `super_admin === true` ve `moderation_admin === true` birlikte bootstrap edilir.
- Dört rol callable'ı, ortak 06.3A `authorize` kontrolünü `super_admin` claim'iyle kullanır. İmzalı callable oturumu, token claim'i, güncel Admin SDK kullanıcı kaydı, disabled durumu ve `tokensValidAfterTime` / `auth_time` kontrol edilir. Rol değişiminde yazımdan hemen önce aktör tekrar kontrol edilir.
- Eski token'da yetki bulunsa bile güncel Auth claim'i kaldırılmışsa çağrı reddedilir. Mevcut moderasyon çağrıları aynı canlı kontrolü kullanmaya devam eder.
- Callable girdileri izin verilen alanlarla sınırlıdır; istemciden claim nesnesi veya aktör kimliği alınmaz. Hiçbir callable bootstrap seçeneği sunmaz. Super Admin hedefi, grant/revoke ile kendisi dahil değiştirilemez.
- Custom claims önce Auth'tan okunur, yalnız `moderation_admin` eklenir veya silinir. Diğer claim'ler korunur. Revoke ayrıca refresh token'ları iptal eder.

## Callable sözleşmeleri

Bölge: `europe-west1`. Hepsi yalnız Super Admin içindir.

| İşlev | Girdi | Sonuç |
|---|---|---|
| moderationLookupUser | `{uid:"FULL_UID"}` veya `{email:"full@example.com"}` | `{user:{uid,display_name,email,disabled,moderation_admin,super_admin}}` |
| moderationGrantModerator | `{target_uid:"FULL_UID"}` | `{uid,moderation_admin:true}` |
| moderationRevokeModerator | `{target_uid:"FULL_UID"}` | `{uid,moderation_admin:false}` |
| moderationListModerators | `{limit:25,cursor:"..."}`; alanlar isteğe bağlı | `{moderators:[...],next_cursor:null veya "..."}` |

Lookup tam UID/e-posta ile tek Auth kaydı arar; isim, kısmi arama ve genel kullanıcı dizini yoktur. Her iki arama alanı birlikte kabul edilmez. Sonuçta yalnız yukarıdaki alanlar vardır.

Liste `moderation_role_members` sunucu indeksinden en fazla `limit+1` kayıt okur; limit 1–100, varsayılan 25'tir. En fazla `limit` kullanıcı Auth'tan tekrar doğrulanır. İndeks yetki kaynağı değildir. Silinmiş veya artık moderatör olmayan hesaplar sonuçtan çıkarılır; disabled hesaplar mevcut rolüyle ve disabled bilgisiyle görünür. Super Admin, moderation_admin rolünü de taşıdığı için listede super_admin=true olarak görünür ve düzenlenemez. Eski indeks kayıtları yüzünden boş bir sayfada next_cursor bulunabilir; istemci null olana kadar devam etmelidir. Tüm kullanıcılar callable içinde taranmaz.

## Güvenilir bootstrap ve geçiş

Node.js 22 ve Firebase Admin SDK kullanılır. Script uygulamaya gömülü değildir, Cloud Function olarak export edilmez. ADC/IAM erişimi bulunan güvenilir yönetim ortamından, `functions` dizininde:

```bash
npm ci
node operator/moderation-role.js PROJECT_ID FULL_UID bootstrap-super-admin --apply
# Alternatif: UID yerine açık email: öneki
node operator/moderation-role.js PROJECT_ID email:full@example.com bootstrap-super-admin --apply
```

PROJECT_ID ve hesap yerine gerçek değerleri operatör girer; kaynakta hesap veya kimlik bilgisi hardcode edilmemiştir. Disabled hesaba bootstrap/grant reddedilir. Aynı bootstrap tekrar çalıştırılabilir; diğer claim'leri silmez.

Mevcut 06.3A moderatörlerini liste indeksine almak için, rol yazımları durdurulmuş bakım aralığında bir kez:

```bash
node operator/moderation-role.js PROJECT_ID ALL sync-index --apply
```

Bu yalnız güvenilir sunucuda 500'er kullanıcı okuyarak indeksi doldurur; istemciye kullanıcı dizini göndermez ve claim'leri değiştirmez. Bundan sonraki grant/revoke/bootstrap indeks güncellemesini kendisi yapar. Başka bir Admin SDK aracıyla yapılan değişikliklerden sonra bakım aralığında sync-index yeniden çalıştırılır. Sync sırasında silinmiş kullanıcılardan kalmış indeks belgeleri güvenlik yetkisi vermez; liste bunları Auth kontrolünde atlar.

Eski operator kullanım biçimi korunur:

```bash
node operator/moderation-role.js PROJECT_ID FULL_UID grant --apply
node operator/moderation-role.js PROJECT_ID FULL_UID revoke --apply
```

Super Admin kaldırma/düşürme callable'ı veya genel operator komutu eklenmedi. Böyle bir değişiklik yalnız ayrı, güvenilir Admin SDK yönetim işlemiyle yapılabilir. Yeni yetkilerin telefondaki token'a gelmesi için token yenileme/yeniden giriş gerekir; kaldırılan yetki backend'de token yenilenmeden reddedilir.

## Audit, eşzamanlılık ve hata yönetimi

- `moderation_role_audit`: `action` (GRANT/REVOKE; bootstrap için BOOTSTRAP_SUPER_ADMIN), `actor_uid`, `target_uid`, sunucu `timestamp`, `status`, `source`, tamamlanınca `completed_at`.
- Callable aktörü doğrulanmış UID'dir. Güvenilir CLI işlemleri `TRUSTED_OPERATOR` olarak işaretlenir; insan/servis kimliği IAM/Cloud audit üzerinden izlenmelidir. E-posta audit'te gereksiz yere saklanmaz.
- `moderation_role_members`: yalnız `{uid}`; belge kimliği UID'nin SHA-256 özetidir. UID içinde slash gibi karakterler yol oluşturmaya dönüşmez.
- `moderation_role_locks`: hedef başına kilit ve audit kimliği. Transaction ile kilit + PENDING audit, Auth yazımından önce oluşturulur. Aynı hedefe eşzamanlı rol yazımı reddedilir. Süresi dolan kilit kullanılmaz; geciken eski yazımın yeni rolü ezmesi engellenir.
- Başarıda indeks/audit tamamlama/kilit silme tek Firestore batch'idir. Auth ve Firestore tek atomik transaction olamaz: Auth yazımı başladıktan sonraki belirsiz hatalarda PENDING audit ve kilit korunur; başarı cevabı dönülmez. Yazım başlamadan reddedilen işlem REJECTED olur ve kilit kalkar.
- Takılmış işlemde: ilgili yazıcıları durdurun ve devam eden çağrı kalmadığını doğrulayın; kilidin audit_id kaydını ve güncel Auth claim'lerini inceleyin; Admin SDK ile indeksi güncel Auth rolüne göre uzlaştırın; audit'e ayrı operatör çözüm notu/durumu ekleyip kilidi aynı batch'te silin. Kör tekrar veya çalışan çağrı varken kilit silme yapılmamalıdır.
- Firebase Auth setCustomUserClaims karşılaştırmalı atomik güncelleme sunmadığından, bu kilit protokolünü kullanmayan diğer güvenilir claim yazıcıları (ör. başka rol script'leri) aynı kullanıcı üzerinde eşzamanlı çalıştırılmamalıdır.
- Mevcut Firestore catch-all deny kuralı üç koleksiyona da uygulanır. Normal kullanıcı, moderatör ve Super Admin dahil hiçbir client doğrudan read/list/write/delete yapamaz. Bu gerçek emulator Rules testinde doğrulandı; rules değiştirilmedi.

## Değişen dosyalar

Mevcut dosyalardan yalnız:

1. `functions/index.js`: dört yeni callable export bağlantısı.
2. `functions/moderation/service.js`: ortak authorize için varsayılanı mevcut moderatör claim'i olan parametre.
3. `functions/operator/moderation-role.js`: bootstrap, mevcut grant/revoke koruması, audit/kilit ve indeks geçişi.
4. `functions/package.json`: yeni backend dosyaları için syntax check.
5. `functions/test/moderation.role.test.js`: gerçek operator akışına uyarlama ve bootstrap testi; eski grant/revoke kontrolleri korunur.
6. `functions/test/trigger.test.js`: yeni modülün test fixture bağlantısı; bildirim iddiaları aynı.

Yeni kod/testler:

- `functions/moderation/roles.js`
- `functions/moderation/role-functions.js`
- `functions/test/role-fixture.js`
- `functions/test/moderation.roles.test.js`
- `functions/test/moderation.roles.firestore.test.js`
- `reports/06.3C/` rapor, doğrulama ve test kayıtları.

ZIP byte karşılaştırmasında kaynak dosyalardan 598'i değişmedi, 6'sı değişti, silinen dosya yok. Tüm Android Java/XML, Manifest, Gradle, SQLite, google-services.json, Firebase rules/indexes ve mevcut UI kaynakları aynıdır. Derlenmiş APK veya cihaz testi yapılmadı; Android build PASS iddiası yoktur. Mevcut Android moderasyon Java model testi ve statik bağlantı kontrolleri geçti.

## Test sonuçları ve sınırlama

Node.js v22.23.2; Firebase CLI 14.12.0; Auth + Firestore emülatörleri; Firestore emulator 1.19.8. Üretim hesabına erişilmedi.

| Doğrulama | Sonuç |
|---|---|
| Functions syntax (`npm run check`) | PASS |
| 06.3C tam Functions paketi, emülatörlerle | 86 test: 84 PASS, 2 FAIL, 0 SKIP |
| Değiştirilmemiş 06.3B aynı ortamda | 62 test: 60 PASS, aynı 2 FAIL, 0 SKIP |
| Eklenen 24 test | Tamamı PASS |
| Yeni rol gerçek Auth/Firestore + transaction/audit/list testleri | PASS |
| Üç sunucu koleksiyonu doğrudan client erişim engeli | PASS |
| Mevcut 06.3A ve 06.3B moderasyon testleri | PASS |
| Android kaynaklarının korunması | Byte karşılaştırması PASS |

İki hata hem orijinal ZIP'te hem teslim sürümünde aynıdır:

- `functions/test/blocks.rules.test.js:30`
- `functions/test/reports.rules.test.js:68`

İkisi de Web SDK `disableNetwork()` çağrısından sonra transaction'ın reddedilmesini beklerken transaction'ın başarılı olmasıdır. Bu ortamda tekrarlanmıştır; kesin SDK/ortam kök nedeni bu görevde çözülmedi. Önceki güvenlik assertions'ları bu noktaya kadar geçmiştir, fakat bu noktadan sonraki assertions bu iki testte çalışmaz. Bu nedenle 06.1/06.2 paketlerinin tamamı PASS veya offline davranış doğrulandı denemez. Bu sonuçları gizlemek için testler atlanmadı/değiştirilmedi; kullanıcı talebinin kapsamı dışındaki offline davranışa müdahale edilmedi. Production için tamamen yeşil test kapısı açık değildir.

Tam çıktılar: `functions-emulator-tests.log` ve `baseline-06.3B-emulator-tests.log`.

Tekrar çalıştırma: test araçlarında Firebase CLI kurulu ve Java 17 mevcutken functions için npm ci, ardından functions/test/rules için npm ci çalıştırın. Auth 9099 ve Firestore 8080 emülatörlerini demo-zeytin-league proje kimliğiyle açın; proje kökünde FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 ve FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 ortamıyla `node --test functions/test/*.test.js` çalıştırın. Testler kendi demo proje Rules ayarlarını yükler.

Firebase referansları: [Custom claims](https://firebase.google.com/docs/auth/admin/custom-claims), [oturum iptali](https://firebase.google.com/docs/auth/admin/manage-sessions). Claim koruması ve oturum kontrolü bu Admin SDK davranışına göre uygulanmıştır.
