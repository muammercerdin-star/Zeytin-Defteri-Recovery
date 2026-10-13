# Zeytin Defteri – Piyasa V5.1 Deploy Gate

Tarih: 11.09.2026

## Amaç

V5 Android build'i gerçek cihazda geçtiği için bu paket, üretim Firebase'e geçmeden önce
backend deploy sürecini yanlış proje / eksik test / gereksiz function deploy risklerine karşı
kilitler. Android Piyasa hesap mantığı değiştirilmemiştir.

## Bu pakette eklenen güvenlik kapıları

- `.firebaserc` varsayılan proje olarak **zeytin-defteri** ile sabitlendi.
- `firebase.json` Functions deploy öncesinde otomatik olarak `npm run check` ve `npm test`
  çalıştıracak şekilde güncellendi.
- `tools/preflight-market-deploy.js` şu noktaları deploydan önce doğrular:
  - `google-services.json` project id ile deploy project id birebir aynı mı,
  - Firestore rules ve indexes tanımlı mı,
  - Node 22 runtime korunuyor mu,
  - altı Piyasa function exportu mevcut mu,
  - anonim public market çıktıları istemci tarafından yazılamıyor mu,
  - gereken Piyasa indeksleri mevcut mu,
  - test/predeploy release gate aktif mi.
- `DEPLOY-PIYASA-V5-1.sh` yalnızca şu backend kapsamını deploy eder:
  - Firestore rules
  - Firestore indexes
  - `zeytinMarketElekWritten`
  - `zeytinMarketConsentWritten`
  - `zeytinMarketGardenWritten`
  - `zeytinMarketBackfill`
  - `zeytinMarketPublish`
  - `zeytinMarketRefreshWindows`

Sosyal bildirim ve Zeytin Ligi functionları bu hedefli deploy komutuna dahil edilmez.

## Mevcut test sonucu

`functions` altında:

- `npm run check`: PASS
- `npm test`: 42 test / 37 PASS / 5 emulator bağımlı SKIP / 0 FAIL
- `node tools/preflight-market-deploy.js`: PASS

Emulator bağımlı testlerin skip olması üretim deployunun otomatik onayı değildir; bu paket
özellikle deploy hedefini ve kapsamını daraltmak için hazırlanmıştır.

## Kullanıcı cihazında çalıştırılacak komut

Proje kökünde:

```bash
./DEPLOY-PIYASA-V5-1.sh
```

Script Firebase CLI veya login yoksa deploy yapmadan durur. Proje id yanlışsa deploy yapmadan
durur. Testlerden biri hata verirse deploy yapmadan durur.

## Deploy sonrası smoke test

1. Uygulamada Piyasa açılır; yerel önizleme çalışmaya devam etmelidir.
2. `Anonim Katkı` açılır.
3. Mevcut Elek kayıtları private kullanıcı alanına senkronize edilir.
4. 5 üreticiden azsa `Yerel önizleme • En az 5 anonim üretici bekleniyor` beklenir.
5. Aynı sezon+bölge+elek+dönemde 5+ izinli üretici olduğunda anonim bölgesel sonuç yayınlanır.
6. Public istatistikte UID, üretici adı, bahçe adı veya tekil ham kayıt bulunmamalıdır.
7. Kullanıcı katkıyı kapatırsa ilgili private market kaynakları backfill/purge işiyle temizlenir.

## Değiştirilmeyenler

- SQLite şeması / DB_VERSION
- mevcut kullanıcı kayıtları
- Firebase project id ve `google-services.json`
- Android Piyasa UI / hesap motoru
- Zeytin Ligi veri mantığı
