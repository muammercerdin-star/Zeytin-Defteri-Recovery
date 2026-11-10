# Adım 2 — test sonuçları

Tarih: 3 Kasım 2026. Kaynak ebeveyn: `ce48c0c71efa3347979a8abf5d8da2f6cf05f505`.

## Yeni testler

İki giriş noktası: `bash tests/web_crypto/run.sh` ve `bash tests/web_relay/run.sh`.

| Bölüm | Sonuç | Sınır |
|---|---:|---|
| Java kripto/protokol (son çalıştırma, JS→Java dahil) | **174/174** | Java 17 compiler ile `--release 8`; gerçek Android cihazı değil |
| JS kripto/protokol (Java→JS dahil) | **174/174** | Node 24.22.1 WebCrypto; gerçek Chrome/Firefox/Safari oturumu değil |
| Relay mantığı | **65/65** | Node mock DO storage/alarms/WebSocketPair/olay serileştirmesi; gerçek Workers edge değil |

Toplam **413 kontrol**, sıfır hata. Crypto runner Java'yı önce 173 kontrol ile çalıştırır, JS frame üretildikten sonra aynı kontrolleri + JS→Java kontrolünü tekrar çalıştırır; tekrarlanan 173 kontrol bu toplamda ikinci kez sayılmaz.

Kapsam: RFC5869 A.1/A.2/A.3 PRK/OKM; bağımsız oracle ile P-256/HKDF/AES-GCM ortak vektörleri; iki yönde Java↔JS zarf açma; yanlış tag/AAD/key/epoch, tekrar seq ve eşzamanlı JS replay; canonical base64url/katı zarf parser; QR B sabitleme, süre/onay kapısı; iki rol resume MAC'i, nonce sırası, ratchet, 5 dk ve seq reset; QR fotoğrafıyla gerçek telefon ciphertext'inin açılamaması ve ayrı sahte telefon kanalı kurulabilmesi.

Relay: auth zaman aşımı ve geciken alarmda da auth reddi; yanlış rol/token/origin, tek rol başına socket ve eşzamanlı auth yarışı; atomik iki consume; eski QR cap reddi/rotasyon; pending/aktif süreler ve metadata temizliği; hibernation sonrası attachment/metadata; 64 KiB, 5 frame/sn, 1 MiB/dk, global create bütçesi; yalnız karşı uca iletim ve alıcı yoksa atma; log çağrılarını yakalama ve storage/attachment'larda içerik/ciphertext/raw capability bulunmaması.

Ortak fixture `tests/web_crypto/vectors.json`: RFC sabitleri ve Python cryptography 50.0.2 ile bağımsız hesaplanan sonuçlar. P-256 d=1/d=2 özel test sayıları herkesçe bilinen **açık test girdileridir**, canlı anahtar değildir. Fixture günlük testte yeniden üretilmez; Java ve JS aynı sabit beklenen değerlere karşı çalışır.

## Mevcut testler

İstenen desenlerle **105 giriş noktası** bulundu ve denendi: **103 geçti**, **2 ortam nedeniyle çalıştırılamadı**. Çalışan testlerde assertion/derleme hatası kalmadı. Bu sayılar bağımsız assertion toplamı değildir; bazı run.sh dosyaları listede ayrıca bulunan check*.py dosyalarını da çağırır.

Desenler: tests altında run.sh, run.py, run_*.py, check*.py; ayrıca tests/*.py. Testler repo kökünden çağrıldı. Kaynak/test dosyaları sonuç almak için değiştirilmedi.

### Çalıştırılamayanlar

| Giriş noktası | Neden |
|---|---|
| `tests/league_names/run.sh` | **çalıştırılamadı (Postgres yok)**; `psql: command not found`, exit 127. Yerel PostgreSQL ve `ZD_PG` gerekiyor. |
| `tests/current_prices/run.sh` | **çalıştırılamadı (Postgres yok)**; aynı koşul, exit 127. |

### Ortam hazırlığı

İlk denemelerde `javac` çalıştırılabilir dosyası PATH'te yoktu; mevcut JDK compiler modülüne `java -m jdk.compiler/com.sun.tools.javac.Main` çağrısı yapan geçici, repo dışı bir javac wrapper'ı kullanıldı. JDK **17.0.20**. Yeni Java testleri wrapper gerektirmeden aynı compiler modülünü çağırır.

Üç mevcut JSON testinin ihtiyacı için üretim Gradle'ı değiştirilmedi. `org.json` kaynakları resmî JSON-java deposundan repo dışına alınıp Java 8 hedefiyle geçici JAR üretildi; `JSON_JAR` bunun yoluna ayarlandı. Bu üç test de sonrasında geçti. Kaynak: https://github.com/stleary/JSON-java ; commit: `2cd0a8c2112a3bc17d33ba8546c54ecb613613f1`.

`tests/security/check_static.py` için gereken eski `c41c2da` commit'i sığ klonda yoktu; aynı kaynak dalının git geçmişi alındı, test varsayılan gerçek karşılaştırma commit'iyle geçti. Testin karşılaştırması zayıflatılmadı.

### Giriş noktası listesi

| Giriş noktası | Sonuç |
|---|---|
| `tests/account/check_local_wipe.py` | GEÇTİ |
| `tests/account/check_wiring.py` | GEÇTİ |
| `tests/account/run.sh` | GEÇTİ |
| `tests/alerts/run.sh` | GEÇTİ |
| `tests/assistant/run_host_tests.py` | GEÇTİ |
| `tests/assistant_answers/run.py` | GEÇTİ |
| `tests/assistant_cloud/run.sh` | GEÇTİ |
| `tests/assistant_hints/check_wiring.py` | GEÇTİ |
| `tests/assistant_hints/run.sh` | GEÇTİ |
| `tests/assistant_market/check_wiring.py` | GEÇTİ |
| `tests/assistant_market/run.sh` | GEÇTİ |
| `tests/assistant_misses/run.sh` | GEÇTİ |
| `tests/assistant_more/check_wiring.py` | GEÇTİ |
| `tests/assistant_more/run.sh` | GEÇTİ |
| `tests/assistant_panel/check_wiring.py` | GEÇTİ |
| `tests/assistant_panel/run.sh` | GEÇTİ |
| `tests/assistant_persona/check_wiring.py` | GEÇTİ |
| `tests/assistant_persona/run.sh` | GEÇTİ |
| `tests/assistant_siri/check.py` | GEÇTİ |
| `tests/assistant_ux/run_ux_tests.py` | GEÇTİ |
| `tests/auth/check_layout_budget.py` | GEÇTİ |
| `tests/auth/run.py` | GEÇTİ |
| `tests/blocks/check_blocks.py` | GEÇTİ |
| `tests/buyers/run.py` | GEÇTİ |
| `tests/chat_typing/run.sh` | GEÇTİ |
| `tests/cloud/run.sh` | GEÇTİ |
| `tests/current_prices/run.sh` | ÇALIŞTIRILAMADI — Postgres yok (127) |
| `tests/current_prices_app/run.sh` | GEÇTİ |
| `tests/elek_archive/check_archive.py` | GEÇTİ |
| `tests/elek_insights/check_archive_query.py` | GEÇTİ |
| `tests/elek_insights/run.py` | GEÇTİ |
| `tests/elek_popup/check_prices.py` | GEÇTİ |
| `tests/elek_sale/check_bridge.py` | GEÇTİ |
| `tests/elek_sale/run.sh` | GEÇTİ |
| `tests/elek_single_entry/check.py` | GEÇTİ |
| `tests/elek_ui/check_form.py` | GEÇTİ |
| `tests/elek_voice/check_wiring.py` | GEÇTİ |
| `tests/elek_voice/run.sh` | GEÇTİ |
| `tests/feed_thread/check_preview.py` | GEÇTİ |
| `tests/final_assistant/run_weather_measure_tests.py` | GEÇTİ |
| `tests/garden_analytics/check_analytics.py` | GEÇTİ |
| `tests/garden_delete/run.sh` | GEÇTİ |
| `tests/garden_detail/check_sqlite.py` | GEÇTİ |
| `tests/gardens/check_summary_query.py` | GEÇTİ |
| `tests/gardens/run.py` | GEÇTİ |
| `tests/harvest_delete/check.py` | GEÇTİ |
| `tests/home/run.py` | GEÇTİ |
| `tests/home_league/check_wiring.py` | GEÇTİ |
| `tests/home_league/run.sh` | GEÇTİ |
| `tests/home_lower/check_wiring.py` | GEÇTİ |
| `tests/home_lower/run.sh` | GEÇTİ |
| `tests/league/check_consent.py` | GEÇTİ |
| `tests/league/check_privacy.py` | GEÇTİ |
| `tests/league/check_view.py` | GEÇTİ |
| `tests/league_names/run.sh` | ÇALIŞTIRILAMADI — Postgres yok (127) |
| `tests/league_region/check_wiring.py` | GEÇTİ |
| `tests/league_region/run.sh` | GEÇTİ |
| `tests/location/check_location.py` | GEÇTİ |
| `tests/market-production-five-java.py` | GEÇTİ |
| `tests/meydan/check_schema.py` | GEÇTİ |
| `tests/meydan_redesign/check_wiring.py` | GEÇTİ |
| `tests/meydan_redesign/run.sh` | GEÇTİ |
| `tests/meydan_sync/check_sql.py` | GEÇTİ |
| `tests/meydan_sync/check_wiring.py` | GEÇTİ |
| `tests/meydan_sync/run.sh` | GEÇTİ |
| `tests/moderation/run.sh` | GEÇTİ |
| `tests/moderation_content/check_wiring.py` | GEÇTİ |
| `tests/moderation_content/run.sh` | GEÇTİ |
| `tests/notebook/run.sh` | GEÇTİ |
| `tests/oil/run.sh` | GEÇTİ |
| `tests/operation_voice/check_wiring.py` | GEÇTİ |
| `tests/operation_voice/run.sh` | GEÇTİ |
| `tests/payments/run.sh` | GEÇTİ |
| `tests/prayer_times/run.sh` | GEÇTİ |
| `tests/release/check_target_sdk.py` | GEÇTİ |
| `tests/reminders/check_alarm_hotfix.py` | GEÇTİ |
| `tests/reminders/check_release_gate.py` | GEÇTİ |
| `tests/reminders/check_reminders.py` | GEÇTİ |
| `tests/role/run.sh` | GEÇTİ |
| `tests/screen_fit/check.py` | GEÇTİ |
| `tests/season_report/check_wiring.py` | GEÇTİ |
| `tests/season_report/run.sh` | GEÇTİ |
| `tests/season_sales/check_query.py` | GEÇTİ |
| `tests/season_sales/run.sh` | GEÇTİ |
| `tests/season_track/run.sh` | GEÇTİ |
| `tests/security/check_static.py` | GEÇTİ |
| `tests/security/run.sh` | GEÇTİ |
| `tests/settings071_check.py` | GEÇTİ |
| `tests/settings07_check.py` | GEÇTİ |
| `tests/splash_intro/check.py` | GEÇTİ |
| `tests/stage2/check_wiring.py` | GEÇTİ |
| `tests/stage2/run.sh` | GEÇTİ |
| `tests/stage3/check_wiring.py` | GEÇTİ |
| `tests/stage3/run.sh` | GEÇTİ |
| `tests/tezgah/run.sh` | GEÇTİ |
| `tests/tezgah_voice/run.sh` | GEÇTİ |
| `tests/tezgah_voice_weigh/check_wiring.py` | GEÇTİ |
| `tests/tezgah_voice_weigh/run.sh` | GEÇTİ |
| `tests/threaded/check_roundtrip.py` | GEÇTİ |
| `tests/update_gate/run.sh` | GEÇTİ |
| `tests/variety/run.sh` | GEÇTİ |
| `tests/voice_records/check_wiring.py` | GEÇTİ |
| `tests/voice_records/run.sh` | GEÇTİ |
| `tests/weather_sky/run.sh` | GEÇTİ |
| `tests/zeytin_ai_worker/run.sh` | GEÇTİ |

## Ek kontroller ve yapılmayanlar

- `wrangler.toml` TOML olarak okundu: ROOMS/TezgahRelay binding, `new_sqlite_classes`, vars ve log kapatma ayarları mevcut.
- Tasarımdaki JSON örneklerinin sözdizimi kontrol edildi; `git diff --check` geçti.
- Android Gradle/manifest, önceki üretim sınıfları ve `cloud/zeytin-ai` için içerik değişikliği yok. Yeni Java yardımcıları henüz hiçbir Activity/servise bağlanmıyor.
- Gerçek `workerd`/Miniflare, Cloudflare deploy/migration, özel domain TLS, mobil operatör ve 10 saatlik kota/pil ölçümü **doğrulanmadı**.
- APK/Android cihaz derlemesi/testi yapılmadı. Uçtan uca ilk kullanıcı demosu adım 3'tür.
