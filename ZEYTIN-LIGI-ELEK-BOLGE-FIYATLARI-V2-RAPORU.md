# Zeytin Ligi - Elek Bazli Bolgesel Fiyatlar V2

Bu surum, Zeytin Ligi > Bolgelere Gore ekranina `Genel, Double, 1 No, 2 No, 3 No, 4 No, Yaglik` filtrelerini ekler.

- Kullanici bir elek sectiginde tum bolge kartlari ayni elekte karsilastirilir.
- Alaşehir kullanicisi Salihli gibi baska aktif bolgelerin secilen elek fiyatini gorebilir.
- Tek ureticili bolgede isim/UID/bahce/ham kayit yayinlanmaz.
- TEST SQL'inde 1 ureticide fiyat gorunur, kg gizli kalir; 2+ ureticide kg de acilir.
- Genel gorunumde mevcut `market_league_regions` ve mevcut esik davranisi korunur.
- Top 20 Firestore lig mantigina dokunulmadi.

Supabase'te bir kez `supabase/ZEYTIN-LIGI-ELEK-BOLGE-FIYATLARI-TEST-2.sql` calistirilmalidir.
Production gecisinde `supabase/ZEYTIN-LIGI-ELEK-BOLGE-FIYATLARI-PROD-5.sql` kullanilabilir.
