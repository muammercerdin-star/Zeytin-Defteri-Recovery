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
- **Ortak (bütün çiftçiler, sezon sonrası):** Yalnız ayrı ve açık izinle; cümle değil, çiftçinin öğrettiği tek söz
  (temizlenmiş: küçük harf, eksiz, ad olmayan çekimli fiil) + seçilen anlam (+ il, sürüm) gönderilir. Yeterince
  çiftçide tekrarlanan bilgi, uygulama güncellemesi beklemeden indirilen "bilgi paketi" ile herkese yayılır.
  Türkiye sunucusuna taşınmadan kurulmaz. Ayrıntı: "Ortak döngü kuralları" bölümü.
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

## Ortak döngü kuralları (İP8)

- **Veri yalnız Türkiye'deki sunucuya gider; yurt dışındaki hiçbir sunucuya (Supabase dahil) adres yazılmaz.**
- **Adres boşken ağ kapalıdır:** `CommunitySender.configured()` boş / https olmayan adreste hiçbir bağlantı açmaz
  (`AssistantCloudClient` deseni). Adres `ZEYTIN_COMMUNITY_URL` derleme ayarıyla gelir; İP8-A'da tanımlı değildir
  ve `CommunitySync.flush` hiçbir yerden çağrılmaz (tests/community/check_wiring.py bunu denetler).
- İzin ayrı, varsayılan KAPALI, açık izin olmadan kuyruğa bile girilmez. İzin ekranı İP8-B'ye kadar gizlidir
  (`CommunitySync.SETTINGS_VISIBLE = false`).
- Gönderilen alanlar yalnız `ContributionCleaner`'dan geçer: kök, söz, eylem, tür, not, il, sürüm + sezonluk takma ad
  (telefondaki gizli anahtardan HMAC, her 1 Ağustos'ta değişir) + silme belgesinin özeti. Söz, yerel bahçe / alıcı /
  kullanıcı adlarıyla karşılaştırılır; ad gibi görünen söz gönderilmez. Her söz-anlam bir kez gönderilir;
  "Unut" bekleyeni düşürür, gönderilmişi geri çeker.
- Kuyruk (zd_community.db) ve kapatılan paket sözleri (zd_pack.db) Google yedeğine, telefon aktarımına ve Yedekle
  dosyasına girmez; hesap silinince silinir.
- Bilgi paketi: çiftçinin kendi öğrettiği her zaman üstündür; "Herkesten öğrendiklerim"de her söz tek tek kapatılır.
  Gömülü paket (res/raw/bilgi_paketi.json) yalnız `res/raw/baslangic_sozleri.tsv`'den `bash tools/make_pack.sh` ile
  üretilir; elle düzenlenmez. Yeni söz eklemek için listeye bir satır eklenir ve araç çalıştırılır; söz kontrol
  bankasını (cümle bankasının doğru satırları) bozuyorsa paket üretilmez. İndirilen paket ECDSA P-256 imzası ister
  (iki açık anahtar yuvası, `KnowledgePack.PUBLIC_KEYS`); bozuk ya da bankayı bozan paket uygulanmaz, bir öncekiyle
  devam edilir. Anahtar: docs/ortak-dongu-anahtar.md.
- Sunucu: düz PostgreSQL + küçük API, `server/ortak-dongu/` (01 ön kontrol, 02 uygula, 03 geri al). Aday olmak için
  en az 20 farklı çiftçi (tek ayar: `ortak.ayar 'esik'`); aday pakete yalnız elle onayla girer. Katkılar iki sezon
  saklanır (`ortak.temizle`). Yerel denemesi: `tests/ortak_dongu/run.sh`. Canlıda yalnız İP8-B'de çalıştırılır.

### İP8-B'de yapılacaklar (izin açılmadan önce hepsi bitmiş olmalı)

1. Türkiye'deki sunucu ve küçük API (`/katki`, `/geri-cek`, `/paket`); bağlantı özeti IP'den tuzla türetilir, IP saklanmaz.
2. Canlı SQL: önce `01-on-kontrol.sql`, sonra `02-uygula.sql`; yedek alınır, geri dönüş `03-geri-al.sql`; temizlik zamanlanır.
3. `ZEYTIN_COMMUNITY_URL` derleme ayarı ve gönderimin tetiklenmesi (yalnız izin açıkken, gece 22–05 dışında, Wi-Fi'de).
4. İzin metni ve izin ekranı (`SETTINGS_VISIBLE = true`); metne "Sunucu Türkiye'de; yurt dışına aktarılmaz." eklenir.
5. KVKK aydınlatma metni yeni sürümle (ortak döngü bölümü); site dalı aynı sürümle yeniden üretilir.
6. Gölge deneme: indirilen paket önce gölgede (karar vermeden) çalıştırılır, karneyle karşılaştırılır.
7. İmza anahtarı: docs/ortak-dongu-anahtar.md'ye göre üretilir, açık anahtar yuva 1'e yazılır, paket indirme eklenir.

# Yayın günü (Play'e yeni sürüm çıkarken unutulmayacaklar)

- Yasal metin sürümü değiştiyse site aynı gün güncellenir; uygulama çıkmadan önce `work/site`'a birleştirilmez.
  Metin değişirse site dalı yeniden üretilir: raw metinler + LegalDocuments kopyalanır,
  `python3 -B site/tools/build_legal.py`, `python3 -B tests/site/check_site.py`.
- Tek paket kararı (21 Kasım 2026): Ocak/bahar ayrımı kaldırıldı. **1.6.0 tek pakettir**: İP1–İP7, İP8-A
  (topluluk izni gizli, ağ kapalı), Eğitim turu 1 ve Yönlendiren asistan + Tanecik sahneleri birlikte çıkar.
  `release/1.6.0` dalı `work/yonlendiren`'in son hâlinden açılır. Yasal metin **2.5** (21 Kasım 2026);
  site dalı `work/site-2.5`, Play'e yüklendiği gün `work/site`'a birleştirilir.
- Sonraki sürümde İP8-B (Türkiye sunucusu) açılırsa yasal metin yeni sürüm alır, site dalı yeniden üretilir.
- Sitede "Sürüm" yazısı uygulamadaki LegalDocuments.VERSION ile aynı olmalı.
