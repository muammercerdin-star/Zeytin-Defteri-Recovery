package com.zeytindefteri.app;

import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public final class CommandsTest {
    static int checks;
    static SQLiteDatabase db;
    static TezgahWebCommands engine;
    static AtomicLong clock = new AtomicLong(10000);
    static List<JSONObject> events = new ArrayList<>();
    static List<String> prompts = new ArrayList<>();
    static TezgahWebCommands.Host host = new TezgahWebCommands.Host() {
        public void approval(String id, String message) { prompts.add(message); }
        public void trace(String message) { }
        public void backup(String id) { }
    };
    static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    static JSONObject j(Object... args) { return TezgahWebDto.json(args); }
    static JSONObject command(String type, JSONObject args) {
        return j("v", 1, "type", "komut", "id", TezgahWebCrypto.base64(TezgahWebCrypto.random(16)), "tur", type, "alanlar", args);
    }
    static JSONObject run(String type, JSONObject args, String state) {
        clock.addAndGet(1100);
        JSONObject result = engine.submit(command(type, args));
        check(state.equals(result.optString("durum")), type + ": " + result);
        return result;
    }
    static JSONObject model(String kg) {
        return j("dara", "0.0", "fiyatlar", new JSONArray(Arrays.asList("10.00", "0", "0", "0", "0", "0")),
                "tartimlar", new JSONArray().put(j("elek", 0, "kasa", 0, "kg", kg)));
    }
    static JSONObject purchase(String day) {
        return j("uretici", "Ahmet", "tarih", day, "model", model("10.0"), "odenen", "20.00");
    }
    static JSONObject version(long id) throws Exception {
        return j("alimId", Long.toString(id), "surum", TezgahWebCommands.fingerprint(TezgahPurchases.get(db, "A", id)));
    }
    static long add(String day) {
        return run("alim_ekle", purchase(day), "tamam").optJSONObject("ozet").optLong("kayitId");
    }
    static void decide(JSONObject result, boolean ok, String state) {
        engine.decide(result.optString("id"), ok);
        check(!events.isEmpty() && state.equals(events.get(events.size() - 1).optString("durum")), "decision " + state + " " + events);
    }
    public static void main(String[] args) throws Exception {
        db = new SQLiteDatabase(args[0], args[1]);
        engine = new TezgahWebCommands(db, "A", "room", true, () -> true, host, System::currentTimeMillis, clock::get, () -> 0);
        engine.output(events::add);
        String today = engine.today();
        run("uretici_ekle", j("ad", "Ahmet", "telefon", "555"), "tamam");
        run("uretici_ekle", j("ad", "Ahmet", "telefon", "555"), "tamam");
        check(TezgahProducers.search(db, "A", "", 200).size() == 1, "producer unique");
        check(TezgahProducers.search(db, "B", "", 200).isEmpty(), "producer owner");
        run("uretici_ekle", j("ad", "", "telefon", ""), "reddedildi");
        for (String invalid : new String[]{"NaN", "Infinity", "-1", "1e9", "1,5", "1.234.5", "100000001"}) {
            run("cari_tahsilat", j("kisi", "Ali", "tutar", invalid), "reddedildi");
        }
        run("kasa_adet_giris", j("kisi", "Ali", "adet", 1.5), "reddedildi");
        run("kasa_adet_giris", j("kisi", "Ali", "adet", 5001), "reddedildi");
        run("uretici_ekle", j("ad", String.join("", Collections.nCopies(101, "a"))), "reddedildi");
        run("uretici_ekle", j("ad", "Ali\nVeli"), "reddedildi");
        clock.addAndGet(1100);
        JSONObject wrongVersion = command("uretici_ekle", j("ad", "Ali"));
        wrongVersion.put("v", 1.5);
        check(engine.submit(wrongVersion).getString("durum").equals("reddedildi"), "fractional version");
        db.execSQL("INSERT INTO tezgah_purchases(owner_uid,seller_name,record_date,created_at) VALUES('lookup','Ali','2026-11-03',0)");
        db.execSQL("WITH RECURSIVE n(x) AS (SELECT 1 UNION ALL SELECT x+1 FROM n WHERE x<205) INSERT INTO tezgah_purchases(owner_uid,seller_name,record_date,created_at) SELECT 'lookup','Ali '||x,'2026-11-03',x FROM n");
        check(TezgahProducers.find(db, "lookup", "Ali") != null, "exact producer lookup not capped by 200 suggestions");
        long id = add(today);
        check(id > 0, "purchase id");
        check(TezgahPurchases.get(db, "A", id).gross == 100, "phone total");
        check(TezgahPurchases.get(db, "B", id) == null, "purchase owner");
        JSONObject payment = command("odeme", j("alimId", Long.toString(id), "tutar", "5.00"));
        clock.addAndGet(1100);
        JSONObject first = engine.submit(payment);
        check(first.getString("durum").equals("tamam"), "payment");
        check(engine.submit(payment).toString().equals(first.toString()), "duplicate result");
        check(TezgahPurchases.get(db, "A", id).paid == 25, "duplicate no write");
        JSONObject conflict = new JSONObject(payment.toString());
        conflict.getJSONObject("alanlar").put("tutar", "6.00");
        check(engine.submit(conflict).getString("durum").equals("reddedildi"), "duplicate changed body");
        run("odeme", j("alimId", Long.toString(id), "tutar", "1000"), "reddedildi");
        run("odeme", j("alimId", "99999", "tutar", "1"), "reddedildi");
        run("odeme", j("alimId", Long.toString(id), "tutar", -2), "reddedildi");
        run("alim_ekle", purchase(today).put("owner", "B"), "reddedildi");
        run("alim_ekle", purchase(today).put("tutar", "0.01"), "reddedildi");
        run("alim_ekle", purchase(today).put("uretici", "Bilinmeyen"), "reddedildi");
        run("alim_ekle", purchase("2099-01-01"), "reddedildi");
        run("alim_ekle", purchase(today).put("model", model("-1")), "reddedildi");
        JSONObject edit = version(id).put("uretici", "Ahmet").put("tarih", today).put("model", model("11"));
        run("alim_duzenle", edit, "tamam");
        check(TezgahPurchases.get(db, "A", id).paid == 25, "edit preserves payments");
        run("alim_duzenle", edit, "reddedildi");
        long historical = add("2020-01-01");
        JSONObject pastEdit = version(historical).put("uretici", "Ahmet").put("tarih", "2020-01-01").put("model", model("12"));
        JSONObject waiting = run("alim_duzenle", pastEdit, "onay_bekleniyor");
        check(TezgahPurchases.get(db, "A", historical).gross == 100, "not before approval");
        decide(waiting, true, "tamam");
        check(TezgahPurchases.get(db, "A", historical).gross == 120, "historical edit approved");
        waiting = run("alim_sil", version(historical), "onay_bekleniyor");
        decide(waiting, false, "iptal");
        check(TezgahPurchases.get(db, "A", historical) != null, "reject kept purchase");
        waiting = run("alim_sil", version(historical), "onay_bekleniyor");
        clock.addAndGet(60001);
        engine.tick();
        check(events.get(events.size() - 1).getString("durum").equals("iptal"), "timeout");
        engine.decide(waiting.getString("id"), true);
        check(TezgahPurchases.get(db, "A", historical) != null, "late approval no delete");
        waiting = run("alim_sil", version(historical), "onay_bekleniyor");
        TezgahPayments.pay(db, "A", historical, 1, today, System.currentTimeMillis());
        decide(waiting, true, "reddedildi");
        waiting = run("toplu_odeme", j("uretici", "Ahmet", "tutar", "10"), "onay_bekleniyor");
        decide(waiting, true, "tamam");
        check(TezgahPurchases.get(db, "A", id).paid == 35, "bulk oldest first");
        waiting = run("alim_sil", version(historical), "onay_bekleniyor");
        decide(waiting, true, "tamam");
        check(TezgahPurchases.get(db, "A", historical) == null, "deleted");
        for (String type : new String[]{"cari_tahsilat", "cari_odeme", "kasa_giris", "kasa_cikis"}) {
            run(type, j("kisi", "Tüccar", "tutar", "5.00", "not", "<img onerror>"), "tamam");
        }
        run("cari_satis", j("kisi", "Tüccar", "yontem", 2, "kg", "100.0", "fiyat", "15.00", "alinan", "20.00", "not", ""), "tamam");
        run("cari_satis", j("kisi", "Tüccar", "yontem", 3, "kg", "100.0", "fiyat", "1.00", "alinan", "0", "not", ""), "tamam");
        JSONArray six = new JSONArray(Arrays.asList("1", "0", "0", "0", "0", "0"));
        run("cari_satis", j("kisi", "Tüccar", "yontem", 1, "kilolar", six, "fiyatlar", six, "alinan", "0", "not", ""), "tamam");
        JSONObject ledger = engine.read("cari", j()).getJSONArray("items").getJSONObject(0);
        waiting = run("cari_sil", j("kayitId", ledger.getString("id"), "surum", ledger.getString("surum")), "onay_bekleniyor");
        decide(waiting, true, "tamam");
        for (String type : new String[]{"kasa_adet_giris", "kasa_adet_cikis"}) {
            run(type, j("kisi", "Aracı", "adet", 5, "not", ""), "tamam");
        }
        JSONObject crate = engine.read("kasalar", j()).getJSONArray("items").getJSONObject(0);
        waiting = run("kasa_adet_sil", j("kayitId", crate.getString("id"), "surum", crate.getString("surum")), "onay_bekleniyor");
        decide(waiting, true, "tamam");
        JSONObject closing = engine.read("aksam_hesabi", j());
        run("aksam_hesabi", j("teslim", six, "komisyon", "1", "avans", "10", "surum", closing.getString("surum")), "tamam");
        waiting = run("z_kapat", j(), "onay_bekleniyor");
        decide(waiting, true, "tamam");
        long z = TezgahPurchases.get(db, "A", id).zId;
        check(z > 0, "Z locked");
        run("alim_sil", version(id), "reddedildi");
        run("z_cariye_isle", j("zId", Long.toString(z), "kisi", "Tüccar", "alinan", "0"), "tamam");
        run("z_cariye_isle", j("zId", Long.toString(z), "kisi", "Tüccar", "alinan", "0"), "reddedildi");
        add(today);
        waiting = run("donem_kapat", j(), "onay_bekleniyor");
        decide(waiting, true, "tamam");
        waiting = run("yedek_al", j(), "onaylandi");
        engine.backupDone(waiting.getString("id"), true, "");
        check(events.get(events.size() - 1).getString("durum").equals("tamam"), "async backup complete");
        run("yedek_geri_yukle", j(), "reddedildi");
        run("bilinmeyen", j(), "reddedildi");
        clock.addAndGet(1100);
        for (int i = 0; i < 3; i++) check(engine.submit(command("uretici_ekle", j("ad", "Hız" + i))).getString("durum").equals("tamam"), "rate accepted");
        JSONObject limited = command("uretici_ekle", j("ad", "Hız aşımı"));
        check(engine.submit(limited).getString("durum").equals("reddedildi"), "rate refused");
        clock.addAndGet(1100);
        check(engine.submit(limited).getString("durum").equals("reddedildi"), "rate duplicate first result");
        long before = TezgahPurchases.all(db, "A", 999).size();
        db.execSQL("CREATE TRIGGER fail_result BEFORE INSERT ON tezgah_web_commands WHEN NEW.state='tamam' BEGIN SELECT RAISE(ABORT,'test rollback'); END");
        run("alim_ekle", purchase(today), "reddedildi");
        check(TezgahPurchases.all(db, "A", 999).size() == before, "record and result rolled back together");
        db.execSQL("DROP TRIGGER fail_result");
        TezgahWebCommands other = new TezgahWebCommands(db, "B", "other", true, () -> true, host);
        check(other.submit(command("odeme", j("alimId", Long.toString(id), "tutar", "1"))).getString("durum").equals("reddedildi"), "owner command isolation");
        id = add(today);
        waiting = run("alim_sil", version(id), "onay_bekleniyor");
        engine.downgrade();
        engine.decide(waiting.getString("id"), true);
        check(TezgahPurchases.get(db, "A", id) != null, "downgrade cancels pending");
        run("uretici_ekle", j("ad", "Yasak"), "reddedildi");
        check(!engine.writable(), "no permission upgrade");
        for (String type : new String[]{"uretici_ekle", "alim_ekle", "alim_duzenle", "alim_sil", "odeme", "toplu_odeme",
                "kasa_giris", "kasa_cikis", "cari_tahsilat", "cari_odeme", "cari_satis", "cari_sil", "kasa_adet_giris",
                "kasa_adet_cikis", "kasa_adet_sil", "aksam_hesabi", "z_kapat", "donem_kapat", "z_cariye_isle", "yedek_al"}) {
            check(engine.submit(command(type, j())).getString("hata").contains("yalnız görüntüleme"), "readonly rejects " + type);
        }
        TezgahWebCommands recovered = new TezgahWebCommands(db, "A", "room", true, () -> true, host);
        check(recovered.submit(payment).toString().equals(first.toString()), "durable same result after executor reconstruction");
        recovered.close();
        TezgahWebCommands limit = new TezgahWebCommands(db, "limit", "room", true, () -> true, host);
        db.execSQL("WITH RECURSIVE n(x) AS (SELECT 1 UNION ALL SELECT x+1 FROM n WHERE x<2048) INSERT INTO tezgah_web_commands(id,owner_uid,session_id,hash,request,result,state) SELECT 'old'||x,'limit','room','x','{}','{}','tamam' FROM n");
        check(limit.submit(command("uretici_ekle", j("ad", "Ali"))).getString("hata").contains("sınırı doldu"), "bounded ids do not evict replay protection");
        check(TezgahProducers.search(db, "limit", "", 20).isEmpty(), "limit did not write");
        limit.close();
        TezgahWebCommands deviceOne = new TezgahWebCommands(db, "devices", "device:one", true, () -> true, host);
        JSONObject deviceCommand = command("uretici_ekle", j("ad", "Cihaz Üretici"));
        JSONObject deviceResult = deviceOne.submit(deviceCommand);
        check(deviceResult.getString("durum").equals("tamam"), "persistent command saved");
        TezgahWebCommands deviceTwo = new TezgahWebCommands(db, "devices", "device:two", true, () -> true, host);
        TezgahWebCommands temporary = new TezgahWebCommands(db, "devices", "temporary", true, () -> true, host);
        check(deviceOne.submit(deviceCommand).toString().equals(deviceResult.toString()), "other device and temporary session preserve replay journal");
        check(TezgahProducers.search(db, "devices", "", 20).size() == 1, "persistent duplicate not saved twice");
        db.execSQL("WITH RECURSIVE n(x) AS (SELECT 1 UNION ALL SELECT x+1 FROM n WHERE x<2048) INSERT INTO tezgah_web_commands(id,owner_uid,session_id,hash,request,result,state) SELECT 'old'||x,'devices','device:two','x','{}','{}','tamam' FROM n");
        check(deviceTwo.submit(command("uretici_ekle", j("ad", "Yeni Cihaz Üretici"))).getString("durum").equals("tamam"), "persistent bounded journal retires oldest completed id");
        // Günün fiyatları ve canlı tartım: servis bağlamadıysa gönderilmez; bağlıysa salt okunur gelir.
        check(!engine.read("web_meta", j()).has("gunFiyat"), "no live source: meta has no prices");
        check(!engine.read("canli_tartim", j()).getBoolean("aktif"), "no live source: no live weighing");
        String liveToday = engine.today();
        TezgahModel prices = new TezgahModel();
        prices.setPrice(0, 130);
        prices.setPrice(1, 100);
        final String[] draft = {""};
        final String[] day = {liveToday};
        engine.live(() -> new TezgahWebLive.State(prices.encode(), day[0], draft[0], "<b>Ahmet</b>", 2.0));
        JSONObject meta = engine.read("web_meta", j());
        check(meta.getBoolean("canli"), "meta advertises live support");
        JSONObject gun = meta.getJSONObject("gunFiyat");
        check(gun.getBoolean("bugun") && gun.getString("kaynak").equals("gun"), "today's prices sent");
        check(gun.getJSONArray("fiyatlar").getString(0).equals("130.00") && gun.getJSONArray("fiyatlar").length() == 6, "six prices as text");
        check(gun.getString("dara").equals("2.0"), "tare sent");
        day[0] = "2000-01-01";
        check(!engine.read("web_meta", j()).getJSONObject("gunFiyat").getBoolean("bugun"), "yesterday's prices are not sent");
        check(!engine.read("web_meta", j()).getJSONObject("gunFiyat").has("fiyatlar"), "stale prices withheld");
        TezgahModel weighing = new TezgahModel();
        weighing.setTare(2);
        weighing.setPrice(0, 150);
        weighing.add(0, 10, 258);
        weighing.add(1, 2, 40);
        draft[0] = weighing.encode();
        JSONObject live = engine.read("canli_tartim", j());
        check(live.getBoolean("aktif") && live.getString("uretici").equals("<b>Ahmet</b>"), "live weighing with seller as plain text");
        check(live.getJSONArray("tartimlar").length() == 2 && live.getString("netKg").equals("274.0"), "rows and net match phone model");
        check(live.getString("tutarTL").equals("35700.00"), "amount uses draft prices (238 x 150)");
        check(live.getBoolean("eksikFiyat"), "missing grade price flagged");
        check(live.getJSONArray("tartimlar").getJSONObject(0).getString("netKg").equals("238.0"), "row net = kg - kasa x tare");
        JSONObject draftPrices = engine.read("web_meta", j()).getJSONObject("gunFiyat");
        check(draftPrices.getBoolean("bugun") && draftPrices.getString("kaynak").equals("tartim"), "open weighing prices win even on a new day");
        check(draftPrices.getJSONArray("fiyatlar").getString(0).equals("150.00"), "draft price sent");
        for (int i = 0; i < 130; i++) weighing.add(2, 1, 20);
        draft[0] = weighing.encode();
        JSONObject big = engine.read("canli_tartim", j());
        check(big.getJSONArray("tartimlar").length() == TezgahWebLive.MAX_ROWS && big.getInt("satir") == 132, "rows capped, total count kept");
        draft[0] = "bozuk|metin";
        check(!engine.read("canli_tartim", j()).getBoolean("aktif"), "corrupt draft is not live");
        boolean refused = false;
        try {
            engine.read("canli_tartim", j("x", 1));
        } catch (Exception e) {
            refused = true;
        }
        check(refused, "live op rejects unknown fields");
        deviceOne.close();
        deviceTwo.close();
        temporary.close();
        engine.close();
        other.close();
        db.close();
        System.out.println("PASS " + checks + " command checks (real SQLite transactions)");
    }
}
