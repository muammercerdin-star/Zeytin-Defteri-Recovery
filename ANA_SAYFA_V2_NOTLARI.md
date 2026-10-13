# Zeytin Defteri – Ana Sayfa V2

Bu sürüm, telefonda test edilen Ana Sayfa V1 ile hedef referans ekran karşılaştırılarak düzenlendi.

## V2 değişiklikleri
- Yeni ana görsel `app/src/main/res/drawable/home_hero_olive.png` olarak projeye eklendi.
- Ana hero alanı kenardan kenara çalışacak şekilde düzenlendi.
- Hero yüksekliği ve iç boşlukları referans tasarıma yaklaştırıldı.
- Üst görsele koyu sinematik gradient eklendi; başlık ve alıntı okunabilirliği artırıldı.
- Uzun kullanıcı adlarının üç satıra bölünmesi engellendi; tek satır + ellipsis kullanıldı.
- Hava/konum alanı daraltılıp sağ üst hizası düzenlendi.
- Bugün Yapılacaklar başlığı, bahçe kartı ve hızlı işlem kartlarının dikey ölçüleri sıkılaştırıldı.
- Bahçe kartındaki görsel, yazılar ve Sulama Yap butonu daha kompakt hale getirildi.
- Hızlı işlemler 96dp yüksekliğe çekildi.
- Piyasa Özeti alanı yukarı taşındı; piyasa kartları 116dp yüksekliğe indirildi.
- Ana sayfadaki gerçek kullanıcı/bahçe/sulama/piyasa veri bağlantıları korunmuştur.
- Firebase, google-services.json, SQLite şeması ve mevcut kullanıcı verilerine dokunulmamıştır.

## Build notu
Bu çalışma ortamında Gradle wrapper `https://services.gradle.org/distributions/gradle-8.7-bin.zip` dosyasını indirmeye çalışırken ağ erişimi olmadığı için `UnknownHostException` ile durdu. Bu nedenle gerçek Android build bu ortamda doğrulanamadı. Bu hata kaynak kodundan önce, Gradle dağıtımı indirme aşamasında oluştu.

## AndroidIDE
AndroidIDE'de projeyi açtıktan sonra normal Build/Run işlemi yapılmalıdır. Ana sayfa V2 için yeni drawable dosyası paketin içinde hazırdır; ayrıca elle ekleme gerekmez.
