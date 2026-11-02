# Proje kuralları

- Türkçe iletişim ve rapor kullan.
- Android bağımlılıkları compileSdk 36, AGP 8.10.0 ve minSdk 24 ile uyumlu olmalı. Yeni/güncellenen AAR paketlerinde `META-INF/com/android/build/gradle/aar-metadata.properties` içindeki `minCompileSdk` ve varsa minimum AGP koşulunu denetle. JAR paketlerini, geçişli bağımlılıkları, Java/Kotlin gereksinimlerini de denetle. SDK veya AGP'yi kullanıcının açık onayı olmadan yükseltme.
- Yeni kodda bir satıra bir ifade yaz; uzun tek satırlık metotlar yazma.
- Mevcut Tezgâh davranışlarını koru; yeni koddan sonra tüm host testlerini çalıştır. Gerçekte çalıştırılmayan derleme veya cihaz testini geçmiş olarak raporlama.
