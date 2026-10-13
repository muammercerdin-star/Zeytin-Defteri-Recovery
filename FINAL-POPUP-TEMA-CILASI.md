# Popup tema cilası

Popup yüzeyi #14272C koyu petrol; 1dp border #A9444F, kaydet ve input focus #C74350. Input yüzeyi #0E1D24, normal kenarlık #536971. Ana Fiyatları Düzenle butonuna koyu bordo/petrol gradient ve ince kırmızı border eklendi. Ana yeşil kayıt butonu değişmedi.

İptal/Kaydet aynı 56dp yükseklik, 1:2 genişlik ağırlığı (boşluk hariç %33/%67), 12dp ara, ortalanmış metin, aynı 12dp radius. Kaydet üzerinde kalmış tek taraflı 8dp üst margin kaldırıldı; baseline hizalaması kapatıldı. Bu hizasızlığın doğrudan nedeniydi.

Popup pencere boyutları, panel padding, başlık, fiyat satırlarının yüksekliği, input ölçüleri, rozetler ve dim değişmedi. Paneli kısaltacak düzenleme yapılmadı. Yalnızca alt butonların ölçü ve yerleşimi eşitlendi.

Değişen mevcut dosyalar: activity_elek.xml ve values/elek_popup.xml. Eklenen drawable: elek_popup_cancel.xml, elek_price_edit_action.xml. Tüm Java, veritabanı, Firebase, migration ve repository dosyaları kaynak ZIP ile byte düzeyinde aynı.

XML parse, yeni resource referansları, ID/yerleşim kapsamı ve mevcut fiyat/hesaplama testleri geçti. JVM testleri geçersiz fiyatı, kayıt sonrası kapanışı, kg korunmasını, toplam/ortalama güncellenmesini ve iptal taslağını kontrol eder. Gerçek Android UI testinin yerini tutmaz.

assembleDebug services.gradle.org UnknownHostException nedeniyle Gradle indirme aşamasında durdu. Tam Android compile/resource linking ve gerçek cihazda görünüm, büyük font, aç/kapat ve klavye doğrulanamadı.
