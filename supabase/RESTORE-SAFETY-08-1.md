# 08.1 — Güvenli restore uygulama sırası

Bu teslim canlıya bağlanmadı. Aşağıdaki adımlar daha sonra yetkili operatör içindir.
Android veya deployment hook bu SQL'leri çalıştırmaz.

1. Doğru Supabase projesini doğrulayın. Eşzamanlı function deployment/elle DDL işlemlerini durdurun. Mevcut tanımları ve özgün `private.market_threshold_patch_backup` tablosunu güvenli yerde yedekleyin. Restore bu tablonun güvenilir, client tarafından yazılamayan orijinal operator kaydı olduğunu varsayar; kriptografik geçmiş doğrulaması yapmaz.
2. `DRY-RUN-RESTORE-MIN-PRODUCERS-5.sql` dosyasının TAMAMINI çalıştırın. Transaction READ ONLY'dir; function/table oluşturmaz, değiştirmez. Sonuçlar NOTICE/messages paneline gelir. Birden fazla 5→2 batch varsa tarih sırasına güvenilmez; batch listesi raporlanır ve işlem durur. İncelenen batch_id'yi hem dry-run hem restore dosyasının `selected_batch` alanına SQL string literal olarak girin. Tek batch varsa otomatik seçilir.
3. `VERIFY-PRODUCTION-MIN-PRODUCERS-5.sql` ile Piyasa/League ve yardımcı tanımları inceleyin. İsim deseni burada yalnız envanter içindir. Batch dışında kalan bağımlılıkları ayrıca inceleyin. `SKIPPED_NO_BACKUP`, o fonksiyonun güvenli olduğu anlamına GELMEZ; restore kanıtı yoktur.
4. `UNSAFE_*` veya `MISMATCH_*` varsa UYGULAMAYIN. Eksik yedek, farklı signature, farklı gövde, tekrarlı kayıt veya belirsiz batch sorununu özgün geçmiş/tanımlarla araştırın. Kontrolleri silerek veya yedek uydurarak aşmayın. Sırf isim benziyor diye hiçbir fonksiyon restore edilmez.
5. Yalnız doğrulama uygun ve canlı uygulama için açık yetki mevcutsa `RESTORE-MIN-PRODUCERS-5.sql` dosyasının TAMAMINI çalıştırın. BEGIN/COMMIT tek transaction'dır. İlk değişiklik öncesi bütün batch kontrol edilir; tek güvensiz kayıt tüm apply'ı reddeder. Hata transaction'ı aborted bırakır; istemci otomatik rollback yapmadıysa `ROLLBACK;` çalıştırın. Dosyanın ortasından devam etmeyin. Backup tablosu apply boyunca SHARE kilidiyle korunur. Function DDL için bakım aralığı zorunludur; işbirliği yapmayan eşzamanlı DDL'ye karşı mutlak kilitleme iddiası yoktur.
6. Dry-run tekrar: `RESTORE_ELIGIBLE` yerine `SKIPPED_ALREADY_ORIGINAL` beklenir. VERIFY tekrar: bölge/elek/dönem, distinct producer, önceki dönem ve grafik dahil gerçek RPC işleyişini doğrulayın. İzole gerçek testlerde 1–4 üretici HIDDEN, 5 PUBLIC; tek üreticinin 10 satışı HIDDEN olmalı.

## Karar kuralları

- Batch'in tüm kayıtları değerlendirilir; `market_league_*` otomatik dışlanmaz ve otomatik kabul edilmez.
- schema + function name + `pg_get_function_identity_arguments` eşleşmelidir.
- 08 kaynak restore'unda bulunan üç tarihsel regexp aynen uygulanarak beklenen test gövdesi yalnız karşılaştırma için hesaplanır; 2'li gövde asla çalıştırılmaz.
- Tam `pg_get_functiondef` metni (CREATE başlığı/signature dahil) beklenen test metniyle eşleşirse RESTORE_ELIGIBLE.
- Zaten orijinal tanıma eşitse SKIPPED_ALREADY_ORIGINAL; idempotent.
- Yedeğin dönüşümden etkilenmemesi, metadata hatası, eksik/farklı signature veya farklı gövde tüm apply'ı durdurur.
- Apply yalnız orijinal yedek tanımını çalıştırır; isim üzerinden yeni gövde yazmaz, distinct hesabını değiştirmez. Yedeksiz fonksiyonlara dokunulmaz; bunların production eşiği bu restore ile garanti edilmez.

## Tekrarlanabilir yerel test

Ayrı geçici dizine `@electric-sql/pglite@0.5.8` kurun. Proje kökünde:

```sh
PGLITE_MODULE=/absolute/path/node_modules/@electric-sql/pglite/dist/index.js node supabase/tests/restore-safety.test.mjs
PGLITE_MODULE=/absolute/path/node_modules/@electric-sql/pglite/dist/index.js node supabase/tests/production-five-sql.mjs
```

Testler yerel PostgreSQL motorunda çalışır, uzak veritabanına bağlanmaz. Patch senaryoları açıkça test fixture'ıdır; eksik canlı V7 tanımlarının yerine geçmez.
