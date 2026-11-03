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
        engine.close();
        other.close();
        db.close();
        System.out.println("PASS " + checks + " command checks (real SQLite transactions)");
    }
}
