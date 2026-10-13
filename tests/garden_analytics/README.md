# Bahçe analitiği fixture testleri

Proje kökünde: `python3 tests/garden_analytics/check_analytics.py`
Gereksinimler: Python 3 sqlite3, Java 17 (jdk.compiler modülü). Android SDK veya ağ gerekmez.

Test, mevcut İş Emri 01 testinin SQLite köprü sabitlerini AST üzerinden okur; o test dosyasını çalıştırarak yüklemez. Üretim DatabaseSchema DDL'si ile SQLite fixture kurar; tam analitik Java sınıflarını Java 8 hedefiyle derleyip sorgularını gerçek SQLite üzerinde çalıştırır. Eski DatabaseHelper rapor metotları ve ReportActivity'nin gerçek gösterim metotları da fixture doğrulamasına katılır. Bu yaklaşım Android framework/emülatör veya APK build doğrulaması değildir.

38 veri tabanlı kontrol: formüller, owner/sezon/bahçe, legacy/guest, alacak aynaları, NULL/0, Elek, eski sezon, Report toplamları, sabit sorgu sayısı, veri dosyasında yazma olmaması. validation/ içindeki loglar son test turunu ve gerçek build engelini içerir. Mevcut regresyon testleri değiştirilmedi.
