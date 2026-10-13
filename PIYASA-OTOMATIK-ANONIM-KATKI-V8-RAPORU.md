# Zeytin Defteri - Piyasa Otomatik Anonim Katkı V8

## Amaç
Piyasa ekranındaki "Anonim bölgesel Piyasa / Katkıyı Aç" akışını tamamen kaldırmak ve yeni kullanıcının Zeytin Ligi'ne önce girmesi durumunda da Elek kayıtlarını anonim bölgesel ağa otomatik yetiştirmek.

## Yapılan değişiklikler
- Piyasa açılışındaki katkı popupı kaldırıldı.
- "Katkıyı Aç / Katkıyı Kapat / Şimdi Değil" kullanıcı akışı kaldırıldı.
- "Veriler nasıl hesaplanıyor?" penceresindeki "Anonim Katkı" düğmesi kaldırıldı.
- Firebase ile oturum açmış ve Supabase bağlantısı hazır kullanıcıların Elek kayıtları otomatik anonim senkronizasyona dahil edildi.
- Zeytin Ligi bölge dizini okunmadan önce güncel Elek kayıtları anonim havuza senkronize edilir.
- Piyasa verisi okunmadan önce de aynı otomatik senkronizasyon çalışır.
- İsim, Firebase UID, bahçe adı ve tekil ham kayıtlar istemcilere yayınlanmaz.
- Mevcut Supabase RPC, bölgesel eşikler, elek filtreleri ve Zeytin Ligi tasarımı korunmuştur.

## Dokunulmayanlar
- Firebase/Google giriş
- SQLite verileri
- Elek ekranı ve kayıt yapısı
- Zeytin Ligi elek bazlı bölge fiyatları
- Supabase URL/publishable key
- Piyasa güven eşiği ve testteki üretici sayısı
- SaleActivity içindeki ayrı satış/piyasa onay alanları

## Kontrol
- Kullanıcı arayüzünde Katkıyı Aç/Kapat metinleri kalmadı.
- Değiştirilen Java dosyalarında parantez/süslü parantez dengesi kontrol edildi.
- Tam Android Gradle build bu çalışma ortamında doğrulanmadı; Android IDE'de Build alınmalıdır.
