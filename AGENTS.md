# Proje kuralları

- Türkçe iletişim ve rapor kullan.
- Android bağımlılıkları compileSdk 36, AGP 8.10.0 ve minSdk 24 ile uyumlu olmalı. Yeni/güncellenen AAR paketlerinde `META-INF/com/android/build/gradle/aar-metadata.properties` içindeki `minCompileSdk` ve varsa minimum AGP koşulunu denetle. JAR paketlerini, geçişli bağımlılıkları, Java/Kotlin gereksinimlerini de denetle. SDK veya AGP'yi kullanıcının açık onayı olmadan yükseltme.
- Yeni kodda bir satıra bir ifade yaz; uzun tek satırlık metotlar yazma.
- Mevcut Tezgâh davranışlarını koru; yeni koddan sonra tüm host testlerini çalıştır. Gerçekte çalıştırılmayan derleme veya cihaz testini geçmiş olarak raporlama.

# Şifreli röle istisnası

- Şifreli röle istisnası: Web Tezgâh ve Yağhane sırası Cloudflare'i kullanabilir, şartı sunucunun kişisel veriyi
  okuyamamasıdır. Okunabilir kişisel veri yurt dışına yazılmaz. Bu istisna yalnız bu iki hizmet içindir.
- Yağhane sırası (`cloud/zeytin-sira`, `web/sira`, adres `sira.zeytindefteri.tr`): görünen ad tarayıcıda fabrika
  anahtarıyla şifrelenir, sunucuda yalnız anahtarın parmak izi durur; telefon numarası sunucuya gitmez (yalnız
  havuzcunun tarayıcısında, o gün); elle giriş için son 2 hanenin yalnız tuzlu özeti. Sunucuda düz yalnız: kod,
  token, sıra no, konum, kasa, kg, durum, saatler, yağ kg. Şifreli ad ve özet 24 saat, satırlar 90 gün sonra
  silinir. Web Tezgâh'ın `web.zeytindefteri.tr` Pages projesine route eklenmez.

# Yayın günü notu

- Yalnız web hizmetini kapsayan bir bölüm, uygulamadan önce sitede yayınlanabilir. (Örnek: yasal metin 2.6'nın
  "Yağhane sırası" bölümü pilottan önce sitede; uygulamadaki metin 2.6'ya İP9-B ile geçer.)
