# TASLAK — Veri İşleme Sözleşmesi (Yağhane sırası)

> **TASLAKTIR. Hukuki görüş değildir; imzadan önce bir avukat tarafından kontrol edilmelidir.**
> Köşeli parantezli yerler doldurulacaktır. Özellikle §9 (yurt dışı aktarım) ve §3'teki "kişisel veri" niteliği
> avukatla netleştirilmelidir.

## 1. Taraflar

- **Veri sorumlusu:** [FABRİKA ADI], [ADRES], [VERGİ NO] ("Yağhane").
- **Veri işleyen:** [GELİŞTİRİCİ ADI SOYADI], Zeytin Defteri uygulamasının geliştiricisi, [ADRES / E-POSTA]
  ("Zeytin Defteri").

## 2. Konu ve amaç

Zeytin Defteri, Yağhane'ye "Yağhane sırası" web hizmetini sağlar: Yağhane'nin çalışanları zeytin getiren
üreticileri sıraya alır; üretici yalnız kendisine verilen linkten ya da koddan **yalnız kendi sırasını** görür.
Zeytin Defteri verileri yalnız bu hizmeti sunmak için ve Yağhane'nin talimatlarıyla işler; kendi amaçları için
kullanmaz, satmaz, reklamda kullanmaz, başkasıyla paylaşmaz.

## 3. İşlenen veriler

| Veri | Nerede, nasıl | Ne zaman silinir |
| --- | --- | --- |
| Üreticinin görünen adı (isteğe bağlı) | Yağhane'nin tarayıcısında **fabrika anahtarıyla şifrelenir**; sunucuda yalnız şifreli metin durur. Zeytin Defteri ve barındırma sağlayıcısı anahtara sahip değildir, adı okuyamaz. | 24 saat |
| Üreticinin telefon numarası | **Sunucuya gönderilmez.** Yalnız havuzcunun telefonundaki tarayıcıda, o günün kaydında durur; sıra linki havuzcunun kendi WhatsApp/SMS'iyle gönderilir. | Gün başlangıcında (tarayıcıda) |
| Telefonun son 2 hanesi | Elle girişte ikinci bilgi olarak; düz yazı değil, gizli tuzla HMAC özeti olarak | 24 saat |
| Sıra bilgisi: kod, sıra no, kasa, kilo, durum, geliş/başlama/bitiş saati, yağ kilosu | Sunucuda düz | 90 gün (sonra yalnız isimsiz gün toplamı: gün, toplam kilo, toplam yağ, işlem sayısı) |
| Çalışan hesapları: kullanıcı adı, şifre özeti (PBKDF2), rol | Sunucuda | Hesap silinince |
| Oturum, deneme sınırı | Oturum değerinin özeti (30 gün); IP adresinin kendisi değil, günlük tuzlu özeti (1 gün) | 30 gün / 1 gün |

**Not (avukata):** Sıra bilgisi isim içermez; ancak Yağhane, kodu üreticiyle eşleştirebildiği için bu bilgi
KVKK bakımından **takma adlandırılmış kişisel veri** sayılabilir. Ayrıca barındırma sağlayıcısı, her web
hizmetinde olduğu gibi, bağlantı sırasında IP adresini teknik olarak işler (sunucu kodu IP'yi saklamaz).

## 4. Barındırma ve alt işleyen

- Hizmet **Cloudflare, Inc.** (ABD) altyapısında çalışır (Workers ve D1 veritabanı). Veritabanı konumu için Doğu
  Avrupa tercih edilir; veri Türkiye dışındadır.
- Cloudflare'de okunabilir isim ve telefon numarası **tutulmaz** (§3). Yağhane bu alt işleyeni kabul eder.
  Zeytin Defteri alt işleyen değişikliğini [30] gün önceden bildirir.

## 5. Zeytin Defteri'nin yükümlülükleri

1. Verileri yalnız §2'deki amaçla ve bu sözleşmeye uygun işler.
2. Teknik tedbirler: HTTPS; şifreli isim; oturum çerezi `HttpOnly; Secure; SameSite=Strict`; şifreler PBKDF2 özeti;
   giriş ve elle giriş deneme sınırı; sunucuda kişisel veri loglanmaz; üretici sayfası yalnız tahmin edilemez
   (128 bit) bağlantıyla açılır ve başka üreticinin adını, kodunu, kilosunu göstermez; süreleri dolan veriler
   saatlik işle kendiliğinden silinir.
3. Veri ihlalini öğrendiğinde Yağhane'ye **gecikmeksizin, en geç [24] saat içinde** bildirir ve bilgi verir
   (Yağhane'nin Kurul'a ve ilgililere bildirim yükümlülüğü için).
4. İlgili kişi başvurularında (KVKK m. 11) Yağhane'ye makul destek verir.
5. Sözleşme bitince Yağhane'nin verilerini [30] gün içinde siler; isimsiz gün toplamlarını Yağhane isterse verir.

## 6. Yağhane'nin yükümlülükleri

1. Üreticileri aydınlatır (KVKK m. 10): sıraya alırken ya da kantar/havuz başında görünür bir bilgilendirme
   (Ek-1) ve üretici sayfasındaki bağlantıyla.
2. Telefon numarasını yalnız üretici verirse ve yalnız sıra linkini göndermek için kullanır.
3. **Fabrika anahtarını** ve parolasını korur; yalnız yetkili ofis bilgisayarına ve havuzcu telefonuna aktarır;
   QR'ın fotoğrafını paylaşmaz; işten ayrılan çalışanın cihazında **Çıkış** yapar ya da anahtarı yeniler.
4. Kullanıcı şifrelerini gizli tutar, cihazlarına ekran kilidi koyar.

## 7. Süre ve fesih

Sözleşme imza tarihinde başlar, [pilot süresi / belirsiz süre]. Taraflardan biri [15] gün önceden yazılı bildirimle
sona erdirebilir; §5.5 uygulanır.

## 8. Denetim

Yağhane, makul aralıklarla tedbirler hakkında yazılı bilgi isteyebilir. Kaynak kod ve kurulum belgeleri
(`cloud/zeytin-sira`, `web/sira`) bu bilgi için kullanılabilir.

## 9. Yurt dışına aktarım (avukatla netleştirilecek)

Cloudflare'de tutulan veriler yurt dışındadır. KVKK m. 9 (2024 değişikliği) kapsamında hangi yolun
kullanılacağı — örneğin **standart sözleşme** (imzadan sonra Kurul'a süresi içinde bildirimi) ya da başka bir
uygun güvence — avukatla belirlenmelidir. Şifreli isim ve telefonun hiç gönderilmemesi riski azaltır; isimsiz sıra
bilgisinin (§3 notu) durumu ayrıca değerlendirilmelidir.

## 10. Uygulanacak hukuk

Türk hukuku; uyuşmazlıklarda [YER] mahkemeleri ve icra daireleri.

---

### Ek-1: Kantar / havuz başına asılacak kısa bilgilendirme (öneri)

> **Yağhane sırası hakkında:** Zeytininizi sıraya alırken isterseniz adınızı ve telefonunuzu sorarız. Adınız
> yalnız bizim cihazlarımızda okunabilir; sunucuda şifreli durur ve 24 saatte silinir. Telefonunuz sunucuya
> gönderilmez, yalnız size sıra linkini göndermek için kullanılır. Sıra linkinde yalnız kendi sıranız görünür.
> Veri sorumlusu: [FABRİKA ADI], [TELEFON/E-POSTA]. Ayrıntı: zeytindefteri.tr/kvkk.html
