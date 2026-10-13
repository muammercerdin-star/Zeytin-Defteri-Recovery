# Premium kayıt/giriş ekranı — 13 Ekim 2026

## Durum
Referans görsele göre native Android UI kodu uygulandı. APK build ve cihazda görsel/işlevsel doğrulama tamamlanmadı. Bu teslim, doğrulanmış bir APK veya yayın onayı değildir.

- Repo: `muammercerdin-star/Zeytin-Defteri-Recovery`
- Tek çalışma branch'i: `work/auth-review-2026-10-13`
- Başlangıç: `832a3be9750dffa65faabae3174de38c75b3abcd`
- Yerel geri dönüş etiketi: `safety/auth-premium-before-2026-10-13`
- Main başlangıcı: `8a2a5919e0f17f53c082f65e555298e56566f76b`; main'e yazılmadı.
- Önceki auth iyileştirmeleri korundu. Diğer repolara, APK'lara ve yerel yapılandırmalara dokunulmadı.

## Başlangıç incelemesi
Mevcut uygulama native Java/Android. LoginActivity UI'ı Java içerisinde oluşturuyordu. Renkler LoginActivity sabitleri ile GreenTheme ve res/values/colors.xml üzerinden kullanılıyordu. Mevcut font Android sans ailesi; mevcut logo ic_olive_logo. AuthErrorMessages önceki Türkçe hata mesajı katmanı. createFirebaseAccount, signInWithEmail, openGoogleAccountChooser/handleGoogleCredential/firebaseAuthWithGoogle gerçek Firebase çağrılarını taşıyor.

## Uygulanan tasarım ve etkileşim
- Ekran görüntüsünü arka plan olarak kullanmadan gerçek ScrollView, LinearLayout, EditText, ImageButton, CheckBox ve ProgressBar bileşenleri oluşturuldu.
- Verilen renk paleti auth ekranına özel kaynaklarda tanımlandı. Uygulamanın genel temasına dokunulmadı.
- 40dp mevcut zeytin logosu; ortalı marka, belirgin başlık ve açıklama. Büyük dış kart yok.
- Hesap Oluştur/Giriş Yap sekmeleri; aktif yeşil, pasif koyu zemin. Seçili sekme Activity yeniden oluşturulurken korunur.
- Açık zeminli Google düğmesinin yazısı sekmeye göre değişir. Facebook/Apple yok.
- Kalıcı alan etiketleri, 50dp minimum alan yüksekliği, 12dp köşe, odakta yeşil çizgi, e-posta klavyesi ve IME geçişleri.
- İki şifre alanı aynı görünürlük durumunu kullanır. TransformationMethod değiştirilerek metin ve seçim aralığı korunur; sans-serif açıkça uygulanır. 48dp göz/çizili göz ikonları ve erişilebilirlik açıklamaları.
- Dört parçalı güç göstergesi yazarken güncellenir. Kayıt koşulu: en az 8 Unicode karakteri, rakam ve büyük harf. Daha uzun uygun şifre dördüncü parçayı doldurur. Bu bir parola entropisi ölçümü değildir.
- Yeni koşul yalnız kayıt formunda uygulanır. Mevcut hesap girişinde sadece boş parola yerel olarak reddedilir; parola doğrulamasını Firebase yapar.
- 56dp minimum ana düğme, işlem metni ve yerel ProgressBar; gönderim sırasında alanlar ve form kontrolleri kilitlenir.
- Alt tekrarlanan hesap değiştirme bağlantısı kaldırıldı; istenen mevcut güven metni vektör kilitle sunulur. Yeni güvenlik garantisi yazılmadı.
- Form 20dp yan boşluklu, en çok 480dp genişlikte. Metinler sp; alanlar ve metin düğmeleri wrap_content + minimum yükseklik kullanır. Klavye için adjustResize korunur.
- Şifreler Activity durum paketine kaydedilmez; cihaz döndürülünce yeniden girilmeleri gerekebilir. Göz düğmesiyle göster/gizle metni silmez.

## Belge eksikliği ve kayıt davranışı — önemli
Repo içinde TermsActivity/activity_terms.xml ve PrivacyActivity/activity_privacy.xml bulunuyor. Ancak ayrı, onaylı KVKK Aydınlatma Metni yok; genel gizlilik metnindeki resmi iletişim bölümü de ileride tamamlanacağını söylüyor. Mevcut metinlere hukuki uygunluk/onay verilmedi ve yeni hukuki belge üretilmedi.

- Kullanım Koşulları bağlantısı mevcut gerçek belgeyi açar.
- KVKK bağlantısı eksikliği açıklar; isterse kullanıcı mevcut Gizlilik Politikası'na ayrıca ulaşır. Bu politika KVKK metni olarak yeniden adlandırılmaz.
- Aydınlatmayı okuma bildirimi ile koşulları kabul etme ifadesi ayrıldı. İki belge bağlantısı ayrı, en az 48dp dokunma alanına sahip olduğu için referanstaki tek satırlık metinden biraz daha fazla yer kullanır.
- Onay kutusu işaretli değildir ve etkin değildir. Kayıt sekmesindeki e-posta/Google kayıt düğmeleri belgeler tamamlanana kadar devre dışıdır. IME üzerinden kayıt denemesi de guard ile engellenir.
- Giriş sekmesindeki mevcut e-posta ve Google giriş akışları korunur.
- Bu yalnız yerel kayıt ekranı kontrolüdür; sunucu tarafında onay kaydı veya yeni kullanıcı oluşturma engeli değildir. Mevcut Google signInWithCredential davranışı, ilk kez kullanılan Google hesabıyla Firebase hesabı oluşturabilir. Bu davranışı değiştirmek Google akışını koruma kapsamına dahil edilmedi. Tüm sağlayıcılarda zorunlu hukuki onay için ayrı ürün/backend çalışması gerekir.
- Belgeler sağlandığında yalnız bir boolean'ı değiştirmek yeterli değildir: gerçek ve sürümlü belge gösterimi, belge bağlantıları ve gerekli onay kayıt tasarımı tamamlanmalıdır.

## Gerçek çalıştırılan kontroller
`python tests/auth/run.py`
- 18 hata mesajı testi: GEÇTİ.
- 17 kayıt politikası testi: GEÇTİ. Boş/kısa parola, rakam/büyük harf yokluğu, Türkçe harfler, Unicode uzunluğu, güç puanı ve belgesiz onayın reddi kontrol edildi.
- LoginActivity Java sözdizimi ayrıştırması: GEÇTİ. Android tip kontrolü veya uygulama build'i değildir.

Kaynak/XML karşılaştırmaları:
- 6 yeni XML dosyası ayrıştırıldı; auth Java ID'leri, layout ID'leri ve yeni kaynak referansları doğrulandı.
- E-posta Firebase metotları, Google sağlayıcı akışı ve giriş sonrası UID/oturum/senkronizasyon bölümleri başlangıçla birebir aynı.
- AuthErrorMessages, navigasyon, SessionManager, Supabase, MarketBackendConfig, Gradle, AndroidManifest, Messaging ve Firestore dosyaları değişmedi.
- `git diff --check`: GEÇTİ.

Kontrast hesapları (düz renkler, normal metin):
| Eşleşme | Oran |
|---|---:|
| Başlık / arka plan | 15.86:1 |
| Gövde / arka plan | 8.10:1 |
| Placeholder / alan | 5.87:1 |
| Etiket / arka plan | 11.32:1 |
| Vurgu / arka plan | 9.27:1 |
| Başlık rengi / yeşil düğme | 5.64:1 |
| Odak çizgisi / alan | 6.24:1 |
| Normal ince kenarlık / alan | 1.60:1 |
Metin çiftleri 4.5:1 üzerinde; normal ince kenarlık referans paletindeki düşük kontrastıyla korundu. Bu hesaplar tüm uygulama için erişilebilirlik uygunluk belgesi değildir.

## Build ve bekleyen testler
`bash gradlew :app:assembleDebug --console=plain` denendi. Gradle 8.7 indirilemedi: `java.net.UnknownHostException: services.gradle.org`. Android derlemesine ulaşılmadı. Yerel app/google-services.json yok; uydurma yapılandırma eklenmedi.

Aşağıdakiler cihazda çalıştırılmadı:
- Gerçek Google/e-posta girişi, hatalı parola, oturum devamlılığı ve çıkış.
- Şifre sıfırlama e-postası teslimi.
- Kayıt: belge eksikliği nedeniyle ayrıca bilinçli UI engeli var. Başarılı kayıt testi tamamlanmış sayılmadı.
- İki şifre gözünün gerçek cihazda metin/imleç koruması; şifre tekrarı ve güç göstergesi etkileşimi.
- Sekmeler, geri tuşu, klavye, TalkBack; 320/360dp, yatay ekran ve %200 font ölçeği.
- Referansla cihaz ekran görüntüsü karşılaştırması.
- Yedi menü ve gerçek Firebase/Supabase uçtan uca regression.

## Değişen dosyalar
- app/src/main/java/com/zeytindefteri/app/LoginActivity.java
- app/src/main/java/com/zeytindefteri/app/AuthRegistrationPolicy.java
- app/src/main/res/layout/activity_auth_premium.xml
- app/src/main/res/values/auth_premium.xml
- app/src/main/res/drawable/auth_input_background.xml
- app/src/main/res/drawable/auth_eye.xml
- app/src/main/res/drawable/auth_eye_off.xml
- app/src/main/res/drawable/auth_lock.xml
- tests/auth/AuthRegistrationPolicyTest.java
- tests/auth/run.py
- reports/auth-premium-2026-10-13/README.md

## Termux'ta güvenli güncelleme
Telefondaki gerçek proje klasöründe çalıştırın. Önce yerel değişiklikleri görün:

```bash
cd ~/zeytin-recovery-upload
git status --short --branch
```

İsteğe bağlı yerel kaynak/APK yedeği (özel yapılandırmalar da yerel arşivde kalır; bu arşivi public repoya yüklemeyin):

```bash
backup_path="$HOME/zeytin-auth-yedek-$(date +%Y%m%d-%H%M%S).tar.gz"
tar --exclude='./.git' --exclude='./.gradle' -czf "$backup_path" .
```

Ardından aşağıdaki blok hata olursa durur. Force/reset/clean kullanılmaz; branch geçişi veya merge yerel dosyalarla çakışırsa otomatik silmez:

```bash
(
  set -e
  git fetch origin work/auth-review-2026-10-13:refs/remotes/origin/work/auth-review-2026-10-13
  git switch work/auth-review-2026-10-13
  git merge --ff-only origin/work/auth-review-2026-10-13
  git log -1 --oneline
  bash gradlew :app:assembleDebug
)
```

Mevcut app/google-services.json, local.properties ve telefonda düzenlenen güvenli Gradle/MarketBackendConfig bağlantıları korunmalı. Bu branch'deki kaynakta yer tutucular bulunduğuna dair önceki rapor hâlâ geçerli. Yerel dosyaları uzak dosyalarla zorla değiştirmeyin. Build başarılıysa APK: app/build/outputs/apk/debug/app-debug.apk. Var olan kurulu uygulamayı kaldırmak veya verilerini temizlemek gerekmez; mevcut imza korunmalıdır.
