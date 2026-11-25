# Tezgâh "Defter görünümü" — Rapor

Alım defteri ekranına, alıcının kâğıt defterine benzeyen bir **Defter görünümü** eklendi. Bu iş yalnızca yeni
bir **görünüm**dür: kayıt yapısı, veritabanı ve mevcut liste ekranı değişmedi. Dal: `work/tezgah-defter-gorunumu`
(taban `origin/release/1.6.0`).

## Eklenen / değişen dosyalar

**Yeni:**
- `app/src/main/java/com/zeytindefteri/app/TezgahNotebookPage.java` — Android'e bağımsız saf model. Alımları
  20'şer bölümlük (4×5) sayfalara böler, eskiden yeniye dizer, numara satırları (D/1/2/3/4/Y) ve T toplamını
  verir, başlık değerlerini ve Türkçe arama eşleşmesini hesaplar.
- `app/src/main/java/com/zeytindefteri/app/TezgahNotebookView.java` — tek Canvas'lı çizim ve dokunuş (ölçek,
  kaydırma, dokunuşla alım açma, arama vurgusu).
- `tests/tezgah/TezgahNotebookPageTest.java` — model host testi (54 kontrol).

**Değişen:**
- `app/src/main/java/com/zeytindefteri/app/TezgahDayActivity.java` — üstte "Liste / Defter" geçişi, defter bloğu
  (arama, sayfa göstergesi, +/−/sığdır düğmeleri), bölüme dokununca mevcut `actions(p)` akışı. Liste görünümü ve
  diğer davranışlar aynen korundu.
- `tests/tezgah/run.sh` — yeni model testi ilk (saf) gruba eklendi.

## Kâğıt defterin uygulanışı

- Sayfa = 20 bölüm (4 sütun × 5 sıra), kalın dış çerçeve, ince iç çizgiler, kâğıt zemini `#FBFAF5`.
- Başlık iki satır: sol `GELEN KASA` / `GİDEN KASA`, sağ `TARİH` / `GELEN PARA`.
- Her bölüm: üstte küçük "Müstahsil" etiketi ve adı; başlık satırı (boş · `KG.` · `TUTARI`); sırayla
  `D 1 2 3 4 Y T` satırları, T'nin üstünde daha kalın çizgi.
- Mürekkep: normal yazı mavi tükenmez `#1F3A8A`; T satırı (kilo ve tutar) ile başlıktaki `GELEN PARA` kırmızı
  `#C62828` ve kalın; basılı kısımlar (çizgiler, D/1/.../T, KG./TUTARI, başlık etiketleri) koyu gri, düz yazı tipi.
- Numara numara satışta her satıra o numaranın kilosu ve tutarı; değeri olmayan satır boş. Numara ayrımı olmayan
  (toplu) satışta yalnız T satırı dolu.
- 20'den fazla alımda ek sayfa; sayfalar yana kaydırılarak (fit ölçeğinde fling) ya da "Önceki / Sonraki" ile
  geçilir, "Sayfa 1 / 2" göstergesi var.
- Sayılar Türkçe (`12.345`), mevcut biçimleyicilerle aynı yerelde; hücrelerde ₺ işareti yok (kâğıttaki gibi yalnız
  rakam). `GELEN PARA` başlığında ₺ gösterilir (mockup'taki gibi) — ama aşağıdaki karar gereği şimdilik boş.

## Alınan kararlar

### GELEN KASA
Görev tanımına göre **alımlardaki kasa toplamı** (sayfadaki bölümlerin kasa toplamı). Mavi, başlıkta.

### TARİH
Sayfadaki en yeni alımın günü (`record_date`), `GG / AA / YYYY` biçiminde. Z ekranında o Z'nin alımlarının günü
gelir. Hiç tarih yoksa bugünün günü.

### GİDEN KASA — **boş bırakıldı** (koddaki karşılığı net değil)
`TezgahCrates` kasa hareketlerini **aracı (tüccar/elek) bazında ve zamana yayılı** tutar (`GAVE` = giden,
`RETURNED` = gelen; bakiye = giden − gelen). Bu defter ise bir **döneme / Z'ye** aittir. Kasa hareketlerinin bir
döneme ya da Z'ye bağlanmış bir toplamı veri modelinde yok; alımdaki `kasa` yalnız "gelen" (dolu) kasayı sayar.
Dolayısıyla döneme özel bir "giden kasa" değeri güvenle üretilemiyor. Görev tanımındaki "net değilse tahmin etme,
boş bırak" kuralına uyarak alan boş bırakıldı.
- İstenirse iki seçenek var: (a) TezgahCrates'ten o güne/aralığa düşen `GAVE` toplamını ayrı bir sorguyla çekmek
  (ama alımlarla kesin eşleşmez), (b) alanı boş bırakmaya devam etmek. Kararı sana bırakıyorum.

### GELEN PARA — **boş bırakıldı** (anlamı belirsiz)
Alıcı için para **dışarı** çıkar (satıcıya ödeme). "GELEN PARA" (içeri gelen para) alıcı defterinde doğrudan bir
karşılığa oturmuyor. En yakın iki aday **toplam tutar** (`gross` toplamı) ya da **ödenen** (`amount_paid` toplamı),
ama ikisi de "gelen para" değil. Belirsiz olduğu için boş bırakıldı ve rapora yazıldı.
- Senin kararın: "GELEN PARA" = toplam tutar mı, ödenen mi, yoksa boş mu kalsın? Söylersen tek satırda bağlarım
  (model zaten alanı taşıyor; `TezgahNotebookPage` içinde `gelenPara` doldurmak yeter).

### T satırı (yeniden hesapla / kayıt farkı)
T her zaman **kayıttaki** değerleri gösterir: kilo = `Purchase.kg`, tutar = `Purchase.gross`. Numara satırları
`data` (encode) çözülerek `gradeNet` / `gradeAmount`'tan gelir. Numara toplamı kayıttan farklı çıksa bile T kayıt
değerini gösterir (görev tanımındaki kural). Host testinde doğrulandı.

### Sıralama
`open()` / `forZ()` alımları yeniden eskiye döndürür; model **eskiden yeniye** çevirir, böylece kâğıttaki gibi
soldan sağa, yukarıdan aşağıya ve eskiden yeniye dolar.

### Font (Caveat) — **yedek font kullanıldı**
Hedeflenen Google Fonts **Caveat** (SIL OFL) bu ortamda **indirilemedi** (geliştirme vekil sunucusu
`fonts.google.com`'u 403 ile engelliyor). Ayrıca proje saf framework (androidx.core / ResourcesCompat yok) ve
minSdk 24. Görev tanımındaki "indiremezsen yedek font kullan ve raporda yaz" kuralına uyarak el yazısı için
sistemin **"cursive" ailesi** kullanıldı (`Typeface.create("cursive", ...)`); cihazda yoksa normal yazı tipine
düşer. Türkçe karakterler (ş ğ ı İ ç ö ü) hem "cursive" hem normal yazı tipinde doğru görünür.
- **Gerçek Caveat'a geçiş (tek dosya + tek satır):** `app/src/main/res/font/caveat.ttf` eklenip `TezgahNotebookView`
  içindeki `handwriting()` metodu bu fontu yükleyecek şekilde değiştirilir (minSdk 24 için
  `Typeface.createFromFile`/asset ya da androidx.core eklenirse `ResourcesCompat.getFont`). Lisans dosyası
  (OFL.txt) de fontla birlikte `res/font/`'a konmalı. Font indirilemediği için bu adım yapılamadı.

## Etkileşim

- Üstte "Liste / Defter" geçişi; varsayılan **Liste**, son seçim `SharedPreferences`'ta (`zd_tezgah` →
  `notebook_mode`) hatırlanır. Liste görünümü ve mevcut tüm davranışlar aynen kalır.
- Fotoğraf gibi: iki parmakla büyüt/küçült, tek parmakla kaydır, çift dokunuşla yakınlaş/sığdır; köşede +, −,
  "sayfayı sığdır" düğmeleri. İlk açılışta sayfa ekrana sığar; en küçük ölçek = sığdırma, en büyük ≈ 3 kat; sayfa
  kenara sınırlanır.
- Arama (müstahsil adı): Türkçe büyük/küçük harf duyarsız (TezgahSearch ile aynı: `TurkishText.fold`). Eşleşen tüm
  bölümler kırmızı çerçeveye (`#E53935`, ~4dp) alınır; ekran ilk eşleşmeye yakınlaşıp ortalar, başka sayfadaysa o
  sayfaya geçer; "N sonuç" / "Bulunamadı" yazar; arama silinince çerçeveler kalkar ve sayfa sığar.
- Bir bölüme dokunuş → o alımın mevcut işlem sayfası (`actions`: QR · fiş · borç · sil). Z'si alınmış (kilitli)
  alımlarda mevcut kilit davranışı korunur.
- Sayfayı resim olarak paylaşma (TezgahShare) bu aşamada **eklenmedi** (isteğe bağlıydı); gerekirse sonraki adım.

## Mimari

- `TezgahNotebookPage` Android'e bağımsız, host testiyle sınanıyor. Purchase→Entry çevirimi (decode) Activity'de
  (`entriesFrom`), böylece model saf kaldı.
- Çizim tek Canvas'lı `TezgahNotebookView`; 20 iç içe layout yok. Paint nesneleri önbellekte, çizim sayfa
  koordinatlarında, ölçek/kaydırma Matrix ile.
- Yeni kütüphane eklenmedi, SDK/AGP yükseltilmedi, veritabanı şemasına dokunulmadı. Yeni kodda bir satıra bir ifade.

## Test sonuçları

- **Tezgâh host testleri:** `bash tests/tezgah/run.sh` — tümü geçti; yeni `TezgahNotebookPageTest` **54 kontrol**:
  0/1/20/21 alım sayfalama, eskiden yeniye sıralama, numara satırları ve T doğruluğu, T'nin kayıttan gelmesi,
  toplu satışta yalnız T, başlık (GELEN KASA / boş GİDEN KASA / boş GELEN PARA), Türkçe arama
  (ismail ↔ İSMAİL, şahin ↔ ŞAHİN), hücre biçimleri, tarih biçimi.
- **Tüm host testleri:** `bash tools/run_host_tests.sh` — **108 geçti · 4 kaldı · 1 atlandı**. Kalan 4 ve atlanan
  1, bu değişiklikle ilgisiz, önceden var olan / ortam kaynaklı birimler:
  - `assistant`, `assistant_answers`: test derlemesinde `SeasonSales` sembolü eksik (stub boşluğu; dokunulmadı).
  - `security`: testin aradığı git commit'i (`c41c2da`) bu çalışma kopyasında yok.
  - `web_pairing`: `ZXING_JAR` ortamda yok.
  - `reports`: giriş betiği yok (ATLA).
  Değiştirdiğim/eklediğim kod bu birimlerin hiçbirinde derlenmiyor; yeni hata çıkmadı.

- **Android derlemesi (`./gradlew assembleDebug`): ÇALIŞTIRILAMADI.** Bu ortamda Android SDK yok (`ANDROID_HOME`
  boş, `android.jar` yok). Dolayısıyla `TezgahNotebookView` ve `TezgahDayActivity` **derlenerek doğrulanmadı**;
  yalnızca elle gözden geçirildi (çerçeve API imzaları, parantez dengesi, tip kullanımı). APK/derleme geçmiş
  olarak **raporlanmıyor**. Saf model (`TezgahNotebookPage`) host testiyle doğrulandı.

## Açık kalan konular

1. **GELEN PARA** ve **GİDEN KASA**: kararın gerekiyor (yukarıdaki seçenekler). Şimdilik boş.
2. **Caveat fontu**: indirilemedi; yedek "cursive" kullanıldı. Gerçek fonta geçiş için ttf + lisans dosyası eklenip
   tek metot değiştirilecek.
3. **Android derlemesi**: SDK'li bir ortamda `./gradlew assembleDebug` ile derlenmeli; cihazda ölçek/kaydırma,
   arama vurgusu, sayfa geçişi ve dokunuşla alım açma gözle denenmeli.
4. **Sayfayı resim olarak paylaş** (TezgahShare): isteğe bağlıydı, eklenmedi.
