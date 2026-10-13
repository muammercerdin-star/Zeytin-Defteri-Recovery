# Eski 2 üreticili test modu — kapatıldı

İş Emri 08 ile Android production eşiği 5 farklı üreticidir.
`TEST-ONLY-MIN-PRODUCERS-2.sql` artık hiçbir SQL tanımını değiştirmeden hata verir.
Geçmişte bu test patch'i canlıya uygulanmışsa, incelemeden sonra
`RESTORE-MIN-PRODUCERS-5.sql` uygulanmalıdır. Canlı uygulama bu teslimde yapılmadı.

Zeytin Ligi adına sahip TEST-2 dosyaları farklı kapsamdaki tarihsel/manual
scriptlerdir; bu Piyasa production işleminde çalıştırılmamalıdır.
