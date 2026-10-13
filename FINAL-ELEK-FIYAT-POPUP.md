# Elek fiyat popup

Mevcut fiyat paneli açılırken ana ScrollView'den çıkarılıp custom android.app.Dialog içindeki ScrollView'e taşınır. Kapanırken GONE olarak aynı yerine döner. Ana ekran genişlemez; kg alanları aynı view'lar olarak korunur. Bordo/kırmızı panel, 6 mevcut fiyat inputu, rozetler, TL etiketleri, gerçek son güncelleme metni, X/İptal/Kaydet ve hafif fade eklendi. Pencere genişliği %92, dim %65; adjustResize ve pencere içi scroll kullanılır. Dış dokunma kapatmaz.

Mevcut updatePrices() sadece başarılı işlemin sonunda dialog.dismiss() eklenerek kullanılır. Fiyat doğrulaması, elek_price_settings anahtarları, kayıt, updatePriceLabels ve calculateAll aynıdır. Boş/geçersiz fiyat popup'ı kapatmaz. İptal/X/geri mevcut kayıtlı fiyatları tekrar yükler, taslağı atar. onSaveInstanceState fiyat taslağını korur, onPostCreate açılışı geri yükler, onDestroy dialog'u kapatır.

Değişen mevcut dosyalar: ElekActivity.java, activity_elek.xml (yalnızca eski gizli fiyat paneli). Yeni popup renk/style, üç drawable, iki animasyon ve test dosyası eklendi. Ana ekranın diğer XML elemanları, hero/ikonlar, hesaplama/kayıt metotları karşılaştırıldı; Database/Firebase/Repository/Manifest/Gradle değişmedi.

Test: Java 8 sözdizimi parse; XML/resource/ID kontrolü; gerçek hesaplama ve fiyat metotlarının JVM testleri geçti. Geçersiz girişin kaydetmemesi/kapatmaması, başarılı fiyat kaydı, kg korunması, toplam/ortalama refresh ve iptal taslağı kontrol edildi. Test Android servislerini stub kullanarak sınar; gerçek Android Dialog/klavye/rotasyon testi değildir.

assembleDebug denendi; services.gradle.org UnknownHostException nedeniyle Gradle indirilemedi. Tam Android compile/resource linking ve cihaz üzerinde popup, klavye, geri tuşu, küçük ekran, font scaling ve lifecycle davranışı doğrulanamadı. Build başarılı iddiası yoktur.
