# Üyelik ve giriş incelemesi — 13 Ekim 2026

## Başlangıç ve kapsam
- Repo: muammercerdin-star/Zeytin-Defteri-Recovery
- Başlangıç main: `8a2a5919e0f17f53c082f65e555298e56566f76b`
- Temiz çalışma ağacıyla yeni klon üzerinden başlandı.
- Uzak yedek: `backup/before-auth-2026-10-13`
- Çalışma branch'i: `work/auth-review-2026-10-13`
- Main'e birleştirme yapılmadı. Diğer repolar değiştirilmedi.
- Mevcut APK/ZIP dosyaları değiştirilmedi veya silinmedi. Yeni APK üretilemedi.

## Değişiklikler
- Gerçek sağlayıcı bağlantısı olmayan Facebook/Apple düğmeleri ve yalnızca “yakında” diyen işleyicileri ekrandan kaldırıldı. İkon kaynakları silinmedi.
- Google düğmesi, koyu lacivert/yeşil renkler, kayıt/giriş sekmeleri korundu.
- Ana form ve mevcut şifre sıfırlama penceresi ScrollView içine alındı; klavye için adjustResize eklendi. Görsel/cihaz doğrulaması bekliyor.
- Kayıt, giriş ve sıfırlamada Android e-posta biçim kontrolü kullanılıyor.
- Boş şifre ayrı ele alınıyor; altı karakter alt sınırı yalnız kayıtta uygulanıyor. Konsoldaki daha güçlü parola politikası sunucuda uygulanmaya devam eder.
- Gecikmeli buton animasyonundan gelen yinelenen e-posta gönderimleri actionRunning ile engelleniyor.
- E-posta kayıt/giriş başarısında yerel oturuma gerçek Firebase UID doğrudan veriliyor. Önceden UID, sonraki yönlendirmede düzeltiliyordu; ilk kayıtta eski/boş UID kullanılabiliyordu. Firebase UID'leri değiştirilmez.
- Kayıt profil-adı güncelleme hatası kullanıcıya bildiriliyor; oluşturulmuş hesap silinmiyor veya yeniden oluşturulmuyor.
- Ham Firebase kayıt hataları yerine Türkçe mesajlar kullanılıyor. Ağ, hız sınırı, sağlayıcı kapalı, zayıf şifre ve kimlik bilgisi hataları ayrılıyor.
- Sıfırlama yanıtı bir hesabın var olduğunu veya e-postanın kesin teslim edildiğini iddia etmiyor.

## Koddan doğrulanan mevcut davranış
- FirebaseAuth createUserWithEmailAndPassword, signInWithEmailAndPassword ve sendPasswordResetEmail çağrıları mevcut.
- Şifremi Unuttum bağlantısı zaten vardı; yeni bir ikinci akış eklenmedi.
- Açılışta Firebase getCurrentUser kontrol ediliyor; mevcut UID ile oturum ve senkronizasyon sürdürülüyor.
- Çıkışta Firebase signOut ve SessionManager.logout çağrıları var; bu kod değişmedi.
- Kayıtta sendEmailVerification veya isEmailVerified erişim engeli yok. Mevcut ürün davranışını korumak için zorunlu doğrulama eklenmedi.
- Google sağlayıcı akışı ve giriş sonrası yönlendirme/senkronizasyon bölümleri başlangıçla birebir aynı.
- Navigation, Sohbet, Supabase, Zeytin Ligi, AndroidManifest, Messaging, Firestore, SessionManager ve Gradle dosyaları değiştirilmedi.

## Önemli kaynak farkı
Bu main sürümünde MarketBackendConfig hâlâ REPLACE_ME yer tutucuları içeriyor. app/build.gradle içinde ilgili BuildConfig alanları yok. Telefonda sonradan yapılan güvenli Gradle/BuildConfig ayarlarının GitHub main'de olduğu doğrulanamadı. Bu görev bunları değiştirmedi. Bu branch'i telefonda kullanmadan önce commit diff'i ve yerel Gradle/MarketBackendConfig değişiklikleri karşılaştırılmalı; yerel yapılandırmaların üzerine yazılmamalı.

## Test sonuçları ve sınırları
| Kontrol | Sonuç |
|---|---|
| `python tests/auth/run.py` | GEÇTİ: 18 gerçek Java hata mesajı kontrolü |
| LoginActivity Java sözdizimi ayrıştırması | GEÇTİ; Android tip/bağımlılık derlemesi değildir |
| Google akışı ve giriş sonrası yönlendirme kaynak karşılaştırması | GEÇTİ; ilgili bölümler birebir aynı |
| Korunan diğer izlenen dosyaların karşılaştırması | GEÇTİ; uygulamadaki değişiklik LoginActivity ve yeni AuthErrorMessages ile sınırlı |
| Boş alan, e-posta biçimi, kısa şifre, şifre tekrarı dalları | Kod incelendi; cihaz üzerinde çalıştırılmadı |
| Gerçek kayıt/giriş/yanlış parola | BEKLİYOR: Firebase + cihaz gerekli |
| Gerçek şifre sıfırlama ve e-posta teslimi | BEKLİYOR: Firebase + cihaz gerekli |
| Oturum kalıcılığı, çıkış ve hesap değişimi | BEKLİYOR: cihaz gerekli |
| Google giriş regression | Kaynak korunması doğrulandı; gerçek giriş BEKLİYOR |
| Küçük ekran, yatay ekran, büyük yazı ve klavye | Kaydırma altyapısı eklendi; cihaz testi BEKLİYOR |
| APK build | BAŞARISIZ/ORTAM ENGELİ: Gradle indirilemedi |

`bash gradlew :app:assembleDebug --console=plain` denendi. Gradle 8.7 indirilirken `java.net.UnknownHostException: services.gradle.org` oluştu; Android derlemesine ulaşılamadı. Wrapper dosyasının mevcut çalıştırma izni olmadığından bash ile çağrıldı; dosya izinleri değiştirilmedi. app/google-services.json yerelde bulunmuyor. Uydurma Firebase yapılandırması eklenmedi.

## Manuel tamamlanacaklar
1. Firebase Console → Authentication → Sign-in method içinde Email/Password sağlayıcısının etkin olduğunu kontrol edin. Etkin değilse ürün sahibi açmalı; Google ayarları, hesaplar ve veriler korunmalı. Buradan konsola erişilmedi.
2. Mevcut gerçek google-services.json ve yerel güvenli Gradle/BuildConfig ayarlarını koruyarak derleyin. Google çalıştığı doğrulanmış imza/yapılandırmayı değiştirmeyin.
3. Yeni test hesabıyla kayıt; boş/geçersiz e-posta; boş/kısa şifre; uyuşmayan şifre tekrarı; hızlı çift tıklama kontrollerini yapın.
4. Yanlış parola ve internet kapalı girişte uygun hata; doğru parolada gerçek UID ve doğru hesaba ait verilerin açıldığını kontrol edin.
5. Şifre sıfırlama e-postasını alıp bağlantıyla şifreyi değiştirin; yeni parola çalışmalı, eski parola reddedilmeli. Google hesabında mevcut giriş yöntemini koruyun.
6. Uygulamayı kapatıp açın; oturum korunmalı. Çıkıştan sonra geri tuşu özel ekrana dönmemeli. A/B hesapları arasında veri ve bildirim sahipliğini kontrol edin.
7. Google ile mevcut hesaba yeniden girin; UID, bahçeler ve veriler değişmemeli. Yedi menü, sohbet gönderme/yanıtlama ve bölgesel Zeytin Ligi ayrıca cihazda kontrol edilmeli.
8. Küçük/yatay ekranda ve klavye açıkken kayıt düğmesi ile sıfırlama düğmesine kaydırarak ulaşılabildiğini kontrol edin.

Firebase referansları:
- https://firebase.google.com/docs/auth/android/password-auth
- https://firebase.google.com/docs/auth/android/manage-users
- https://firebase.google.com/docs/reference/android/com/google/firebase/auth/FirebaseAuth
