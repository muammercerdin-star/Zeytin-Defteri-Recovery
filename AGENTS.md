# Proje kuralları

- Türkçe iletişim ve rapor kullan.
- Android bağımlılıkları compileSdk 36, AGP 8.10.0 ve minSdk 24 ile uyumlu olmalı. Yeni/güncellenen AAR paketlerinde `META-INF/com/android/build/gradle/aar-metadata.properties` içindeki `minCompileSdk` ve varsa minimum AGP koşulunu denetle. JAR paketlerini, geçişli bağımlılıkları, Java/Kotlin gereksinimlerini de denetle. SDK veya AGP'yi kullanıcının açık onayı olmadan yükseltme.
- Yeni kodda bir satıra bir ifade yaz; uzun tek satırlık metotlar yazma.
- Mevcut Tezgâh davranışlarını koru; yeni koddan sonra tüm host testlerini çalıştır. Gerçekte çalıştırılmayan derleme veya cihaz testini geçmiş olarak raporlama.

# Akıllı özellik kuralları

Zeytin Defteri, çiftçiyi tanıyan, öğrenen ve öğrendiğini uygulayan bir uygulama olarak geliştirilir. Asistan,
Tanecik, ana sayfa ve formlar aynı öğrenen temeli kullanan yüzlerdir; temel ayrı bir ekran değildir.
Her akıllı özellik şu döngüye uyar: **topla → iyileştir → ölç → güvenle yay → tekrarla.**

## Üç gelişme döngüsü

- **Kişisel (telefonda, anında):** Çiftçinin dili ("bakırladık" = ilaçlama), alışkanlıkları ve düzeltmeleri
  öğrenilir. Yalnız o telefonda kalır, internet istemez.
- **Ortak (bütün çiftçiler, sezon sonrası):** Yalnız ayrı ve açık izinle; isim, rakam ve tutar silinmiş cümle +
  seçilen anlam gönderilir. Yeterince çiftçide tekrarlanan bilgi, uygulama güncellemesi beklemeden indirilen
  "bilgi paketi" ile herkese yayılır. Türkiye sunucusuna taşınmadan kurulmaz.
- **Sürüm (geliştirici):** Gerçek cümle bankası, testler ve karne ile her sürüm bir öncekinden iyi olmalı.

## Öğrenme

- Ayrıca izleme yapılmaz (ekranda kalma süresi, dokunuş takibi yok). Öğrenilen her şey girilen kayıtlardan ve
  çiftçinin açık düzeltmelerinden çıkar; kayıt silinince ondan öğrenilen de düşer, model her an kayıtlardan
  baştan hesaplanabilir.
- **Öğretilen** bilgi (çiftçi açıkça söyledi) her zaman **sezilen** bilgiden (model fark etti) üstündür ve
  kendiliğinden unutulmaz.
- Her öğrenilen bilgi aynı kalıpta tutulur: ne, değer, kanıt (örn. "son 4 satışın 3'ü"), güven, son görülme,
  tür (öğretilen / sezilen). Sezilen bilgi kullanılmadıkça zayıflar, yeni kanıtla değişir.
- Öğrenilen her şey "Öğrendiklerim" ekranında görülür ve silinebilir.

## Uygulama (öğrenileni işe dökme)

- Uygulamanın işleri tek bir eylem listesinde tanımlanır (Elek gir, satış, tahsilat, işlem, hatırlatma...).
  Ses, yazı ve ileride fotoğraf aynı kapıdan girer; aynı işin ikinci bir yolu yazılmaz.
- "Beyin" (kurallar, öğrenilen sözler, ileride küçük model veya denetlenen dil modeli) değiştirilebilir bir
  parçadır. Çıkışı hep aynıdır: hangi eylem, hangi bilgilerle, ne kadar emin. Beyin değişse de onay kuralları,
  geri al, ekranlar ve Tanecik aynı kalır.
- Dil modeli yalnız tercümandır; cevabı cümleyle ve kayıtlarla karşılaştırılır, uydurduğu bahçe, sayı veya
  tutar atılır. Çiftçinin onayladığı çözüm yerel sözlüğe öğretilir.
- Her işin kendi izin basamağı vardır: önerme → formu hazır doldurma → çiftçi izin verdiyse zararsız işi
  (hatırlatma gibi) kendiliğinden yapıp haber verme.
- **Kayıt, satış, ödeme ve silme her zaman çiftçinin onayını ister**, model ne kadar emin olursa olsun.
  Her kendiliğinden işin "Geri al" yolu vardır.
  Not: Bu kural çiftçinin kayıtları (bahçe, elek, satış, işlem, hasat, alıcı) içindir; asistanın telefondaki
  kendi notları (anlamadıklarım, konuşma kaydı, karne) çiftçinin açık komutuyla onaysız silinebilir.
- Her önerinin yanında nedeni yazar ve tek dokunuşla "Bu yanlış" denebilir; düzeltme öğrenilir.
- Emin değilse susar. Sürekli reddedilen öneri türü kendiliğinden bırakılır. Ürkütücü kişisel gözlem
  söylenmez. Gece (22:00–05:00) konuşmaz ve bildirim göndermez.
- Ekranın iskeleti sabittir: öğrenme düğmelerin yerini değil yalnız içini değiştirir. Uygulama kendi
  kendini kullanırken (form doldurma gibi) bunu görünür yapar; isteyen için hızlı mod olabilir.

## Ölçü ve test

- Gerçek çiftçi cümlelerinden oluşan cümle bankası tutulur (telefonda yerel; ortak olanı yalnız izinle ve
  anonim). Her değişiklik bu bankayla sınanır; önceden anlaşılan bir cümleyi bozan değişiklik yayınlanmaz.
- Karne tutulur: anlaşılan cümle oranı, düzeltme oranı, öneri kabul oranı, kayıt süresi.
- Yeni beyin önce gölgede çalışır (karar vermeden tahmin eder, eskisiyle karşılaştırılır), sonra kademeli
  yayılır. Bilgi paketi sorun çıkarırsa bir öncekine dönülür.
- Hasat döneminde öğrenen yapıda kullanıcının gördüğü büyük değişiklik yayınlanmaz; büyük işler sezon
  dışında yapılır.

# Yayın günü (Play'e yeni sürüm çıkarken unutulmayacaklar)

- Yasal metin sürümü değiştiyse site aynı gün güncellenir; uygulama çıkmadan önce `work/site`'a birleştirilmez.
  Metin değişirse site dalı yeniden üretilir: raw metinler + LegalDocuments kopyalanır,
  `python3 -B site/tools/build_legal.py`, `python3 -B tests/site/check_site.py`.
- Sürüm sırası (yasal metin sürümü yayın sırasını izler):
  - **Ocak sürümü (1.6.0):** `release/1.6.0` dalı **work/ip6-ogrendiklerim**'den açılır (İP1–İP4 + İP6).
    İP5 ve İP7 bu sürüme GİRMEZ; release dalı work/ip5-* ya da work/ip7-* dallarından açılmaz.
    Yasal metin 2.4 (İP6 metni), site dalı `work/site-2.4`.
  - **Sonraki sürüm (İP5 + İP7):** yasal metin **2.5**. İP6'nın 2.4 metnindeki değişiklikler (Öğrendiklerim ekranı,
    Sil / Hepsini unut) bu metne de birleştirilir; site dalı `work/site-2.5` o zaman üretilir.
- Sitede "Sürüm" yazısı uygulamadaki LegalDocuments.VERSION ile aynı olmalı.
