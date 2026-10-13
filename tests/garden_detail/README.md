# İş Emri 01 doğrulaması

`python3 tests/garden_detail/check_sqlite.py`

Python 3 ve compiler modülü bulunan Java 17 gerekir. Test, production DatabaseHelper metotlarını
ve GardenRepository.updateGarden delegasyonunu değiştirmeden geçici JVM sınıflarına alır.
Aktif owner/sezon test oturumundan verilir; Android SQLiteDatabase çağrıları kalıcı bir
Python sqlite3 bağlantısına köprülenir. Production DatabaseSchema CREATE TABLE ifadeleri
gerçek fixture dosyasını oluşturur. SQL veya iş davranışı ikinci kez elle yazılmaz.
Her JVM çağrısı veritabanını yeniden açar; transaction ve foreign key denetimleri gerçektir.

52 kontrol; geçerli/geçersiz düzenleme, ID/owner/cloud kimliği, child ve torun kayıtları,
not kalıcılığı/boş giriş/izolasyon, guest/null-owner davranışı, sezon fallback'i,
explicit yabancı child owner reddi, sıralama ve limit kapsamını doğrular.

Android Cursor/ContentValues API'leri test köprüsüdür. Activity çizimi, Android lifecycle,
gerçek disk doluluğu ve Firebase ağ senkronizasyonu bu testin kapsamında değildir.
Tam DatabaseHelper sınıfı veya APK derlenmiş sayılmaz.

`validation/` dizini baseline öncesi/sonrası komut sonuçlarını ve build/statik kontrol notlarını içerir.
`before-0.log` ortam kaynaklı ilk javac hatasıdır; `before-0-retry.log` değişiklik öncesi başarılı tekrardır.

Cihazda doğrulanması gereken akış: uçak modunda not kaydet → ekranı kapat/aç;
bağlı hasat/Elek/satış bulunan bahçeyi düzenle → listeden tekrar aç;
iki kullanıcı ve iki sezonla tarihçeyi kontrol et; fotoğraf seçimini iptal et/değiştir;
uygulamayı yeniden başlat; ağa bağlanıp mevcut ana ekran senkronizasyonunu kontrol et.
Bunlar bu ortamda çalıştırılmış cihaz testleri olarak sunulmaz.
