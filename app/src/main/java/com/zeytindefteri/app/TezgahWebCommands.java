package com.zeytindefteri.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

/** Telefon otoritesi: yetki, doğrulama, atomik idempotency ve telefon onayı. */
public final class TezgahWebCommands implements TezgahWebPhone.Commands {
    public static final int MAX_IDS = 2048;
    private static final String TABLE = "tezgah_web_commands";
    private final SQLiteDatabase db;
    private final String owner;
    private final String session;
    private final BooleanSupplier alive;
    private final LongSupplier wall;
    private final LongSupplier mono;
    private final DoubleSupplier defaultRate;
    private final Host host;
    private final Map<String, Long> deadlines = new HashMap<>();
    private Consumer<JSONObject> output = value -> { };
    private volatile boolean writable;
    private boolean closed;
    private long rateAt = -1;
    private int rateCount;
    private long lastExpiry;
    private long backupAt = -120000;

    public interface Host {
        void approval(String id, String message);
        void trace(String message);
        void backup(String id);
    }

    public TezgahWebCommands(SQLiteDatabase db, String owner, String session, boolean writable,
                             BooleanSupplier alive, Host host) {
        this(db, owner, session, writable, alive, host, System::currentTimeMillis,
                () -> System.nanoTime() / 1000000, () -> 0);
    }

    public TezgahWebCommands(SQLiteDatabase db, String owner, String session, boolean writable,
                             BooleanSupplier alive, Host host, LongSupplier wall,
                             LongSupplier mono, DoubleSupplier defaultRate) {
        this.db = db;
        this.owner = owner;
        this.session = session;
        this.writable = writable;
        this.alive = alive;
        this.host = host;
        this.wall = wall;
        this.mono = mono;
        this.defaultRate = defaultRate;
        TezgahPurchases.ensure(db);
        TezgahPayments.ensure(db);
        TezgahCrates.ensure(db);
        TezgahLedger.ensure(db);
        TezgahZ.ensure(db);
        TezgahClosing.ensure(db);
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE + " (id TEXT NOT NULL, owner_uid TEXT NOT NULL, "
                + "session_id TEXT NOT NULL, hash TEXT NOT NULL, request TEXT NOT NULL, result TEXT NOT NULL, "
                + "state TEXT NOT NULL, scope_hash TEXT DEFAULT '', deadline INTEGER DEFAULT 0, "
                + "PRIMARY KEY(owner_uid,session_id,id))");
        db.delete(TABLE, "owner_uid=? AND session_id<>?", new String[]{owner, session});
        // Süreç yeniden kurulduysa eski onay düğmesi hiçbir yazıyı başlatamaz.
        cancelPending("Telefon oturumu yeniden başladı.");
    }

    @Override public synchronized void output(Consumer<JSONObject> listener) {
        output = listener;
    }

    @Override public boolean writable() {
        return writable && !closed && alive.getAsBoolean();
    }

    public void revoke() {
        writable = false;
    }

    @Override public synchronized void downgrade() {
        revoke();
        cancelPending("Yetki telefondan yalnız görüntülemeye düşürüldü.");
    }

    public String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(wall.getAsLong()));
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalArgumentException(message);
    }

    public static void fields(JSONObject value, String names) {
        Set<String> allowed = new HashSet<>(Arrays.asList(names.split(",")));
        for (Iterator<String> it = value.keys(); it.hasNext();) {
            require(allowed.contains(it.next()), "Beklenmeyen alan gönderildi.");
        }
    }

    public static String text(JSONObject value, String key, int max, boolean optional) throws Exception {
        if (!value.has(key) && optional) return "";
        Object raw = value.get(key);
        require(raw instanceof String, key + " metin olmalı.");
        String s = ((String) raw).trim();
        require(s.length() <= max && !s.matches("(?s).*[\\p{Cntrl}].*"), key + " çok uzun veya geçersiz.");
        require(optional || !s.isEmpty(), key + " boş olamaz.");
        return s;
    }

    public static double number(JSONObject value, String key, double min, double max, int scale) throws Exception {
        String s = text(value, key, 30, false);
        require(s.matches("[0-9]{1,12}(\\.[0-9]{1," + scale + "})?"), key + " geçerli bir sayı olmalı.");
        double n = new BigDecimal(s).doubleValue();
        require(Double.isFinite(n) && n >= min && n <= max, key + " izin verilen aralık dışında.");
        return n;
    }

    private static long id(JSONObject value, String key) throws Exception {
        String s = text(value, key, 18, false);
        require(s.matches("[1-9][0-9]{0,17}"), "Kayıt numarası geçersiz.");
        return Long.parseLong(s);
    }

    private static int integer(JSONObject value, String key, int min, int max) throws Exception {
        Object raw = value.get(key);
        require(raw instanceof Number, key + " tam sayı olmalı.");
        double d = ((Number) raw).doubleValue();
        require(Double.isFinite(d) && d == Math.floor(d) && d >= min && d <= max,
                key + " izin verilen aralık dışında.");
        return (int) d;
    }

    private String date(JSONObject value) throws Exception {
        String s = text(value, "tarih", 10, false);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setLenient(false);
        require(s.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") && s.compareTo("2000-01-01") >= 0
                && s.compareTo(today()) <= 0, "Tarih geçersiz veya gelecekte.");
        format.parse(s);
        return s;
    }

    public static TezgahModel model(JSONObject value) throws Exception {
        fields(value, "dara,fiyatlar,tartimlar");
        TezgahModel m = new TezgahModel();
        m.setTare(number(value, "dara", 0, TezgahModel.MAX_TARE, 1));
        JSONArray prices = value.getJSONArray("fiyatlar");
        require(prices.length() == 6, "Altı elek fiyatı gerekli.");
        for (int g = 0; g < 6; g++) {
            m.setPrice(g, number(TezgahWebDto.json("fiyat", prices.get(g)), "fiyat", 0, 100000, 2));
        }
        JSONArray rows = value.getJSONArray("tartimlar");
        require(rows.length() > 0 && rows.length() <= 100, "1–100 tartım gerekli.");
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            fields(row, "elek,kasa,kg");
            int grade = integer(row, "elek", 0, 5);
            int crates = integer(row, "kasa", 0, TezgahModel.MAX_KASA);
            double kg = number(row, "kg", 0.1, TezgahModel.MAX_KG, 1);
            String error = TezgahModel.addError(crates, kg, m.tare());
            require(error.isEmpty(), error);
            require(m.add(grade, crates, kg), "Tartım eklenemedi.");
        }
        require(m.missingPrices().isEmpty(), "Kullanılan her eleğin fiyatını yaz.");
        require(m.totalNet() > 0 && m.totalNet() <= 2000000 && m.total() <= TezgahLedger.MAX_AMOUNT,
                "Alım toplamı izin verilen sınırı aşıyor.");
        return m;
    }

    private static String canonical(Object value) throws Exception {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            List<String> keys = new ArrayList<>();
            for (Iterator<String> it = object.keys(); it.hasNext();) keys.add(it.next());
            Collections.sort(keys);
            StringBuilder out = new StringBuilder("{");
            for (String key : keys) {
                if (out.length() > 1) out.append(',');
                out.append(JSONObject.quote(key)).append(':').append(canonical(object.get(key)));
            }
            return out.append('}').toString();
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < array.length(); i++) {
                if (i > 0) out.append(',');
                out.append(canonical(array.get(i)));
            }
            return out.append(']').toString();
        }
        return value instanceof String ? JSONObject.quote((String) value) : String.valueOf(value);
    }

    private static String hash(Object value) throws Exception {
        return TezgahWebCrypto.base64(TezgahWebCrypto.sha256(TezgahWebProtocol.utf8(canonical(value))));
    }

    public static String fingerprint(TezgahPurchases.Purchase p) throws Exception {
        require(p != null, "Alım bulunamadı.");
        return hash(TezgahWebDto.json("id", p.id, "data", p.data, "date", p.date,
                "seller", p.seller, "phone", p.phone, "paid", p.paid, "gross", p.gross, "z", p.zId));
    }

    private JSONObject result(String id, String state, String error, JSONObject summary) {
        return TezgahWebDto.json("v", 1, "type", "komut_sonucu", "id", id, "durum", state,
                "hata", error, "ozet", summary == null ? new JSONObject() : summary);
    }

    private String[] rowArgs(String id) {
        return new String[]{owner, session, id};
    }

    private JSONObject stored(String id) throws Exception {
        Cursor c = db.rawQuery("SELECT request,result,hash,scope_hash,deadline,state FROM " + TABLE
                + " WHERE owner_uid=? AND session_id=? AND id=?", rowArgs(id));
        try {
            if (!c.moveToFirst()) return null;
            return TezgahWebDto.json("request", new JSONObject(c.getString(0)), "result", new JSONObject(c.getString(1)),
                    "hash", c.getString(2), "scope", c.getString(3), "deadline", c.getLong(4), "state", c.getString(5));
        } finally {
            c.close();
        }
    }

    private void store(JSONObject request, String hash, String scope, long deadline, JSONObject result) throws Exception {
        ContentValues v = new ContentValues();
        String id = request.getString("id");
        v.put("request", request.toString());
        v.put("result", result.toString());
        v.put("hash", hash);
        v.put("state", result.getString("durum"));
        v.put("scope_hash", scope);
        v.put("deadline", deadline);
        if (db.update(TABLE, v, "owner_uid=? AND session_id=? AND id=?", rowArgs(id)) == 0) {
            v.put("id", id);
            v.put("owner_uid", owner);
            v.put("session_id", session);
            require(db.insert(TABLE, null, v) != -1, "Komut sonucu saklanamadı.");
        }
    }

    @Override public synchronized JSONObject submit(JSONObject request) {
        String id = request.optString("id", "");
        if (!id.matches("[A-Za-z0-9_-]{22}")) return result("", "reddedildi", "Komut numarası geçersiz.", null);
        if (!writable()) return result(id, "reddedildi", "Bu bilgisayar yalnız görüntüleme yetkisine sahip.", null);
        String digest = "";
        boolean transaction = false;
        try {
            TezgahWebProtocol.token(id, 16);
            digest = hash(request);
            fields(request, "v,type,id,tur,alanlar");
            require(integer(request, "v", 1, 1) == 1 && "komut".equals(request.getString("type")), "Komut sürümü geçersiz.");
            JSONObject old = stored(id);
            if (old != null) {
                require(digest.equals(old.getString("hash")), "Aynı komut numarası farklı alanlarla kullanılamaz.");
                return old.getJSONObject("result");
            }
            long now = mono.getAsLong();
            if (rateAt < 0 || now - rateAt >= 1000) {
                rateAt = now;
                rateCount = 0;
            }
            require(++rateCount <= 3, "Çok hızlı işlem gönderildi. Bir saniye bekleyin.");
            Cursor count = db.rawQuery("SELECT COUNT(*) FROM " + TABLE + " WHERE owner_uid=? AND session_id=?", new String[]{owner, session});
            try {
                require(count.moveToFirst() && count.getInt(0) < MAX_IDS, "Oturumun komut sınırı doldu. Yeni QR ile bağlanın.");
            } finally {
                count.close();
            }
            db.beginTransaction();
            transaction = true;
            require(writable(), "Oturum yetkisi kapatıldı.");
            JSONObject prepared = prepare(request);
            if (prepared.getBoolean("approval")) {
                require(deadlines.size() < 1, "Telefonda bekleyen bir onay var. Önce onu tamamlayın.");
                JSONObject answer = result(id, "onay_bekleniyor", "", prepared);
                long deadline = wall.getAsLong() + 60000;
                store(request, digest, prepared.getString("scope"), deadline, answer);
                db.setTransactionSuccessful();
                db.endTransaction();
                transaction = false;
                deadlines.put(id, now + 60000);
                host.approval(id, prepared.getString("mesaj"));
                return answer;
            }
            if ("yedek_al".equals(request.getString("tur"))) {
                require(now - backupAt >= 120000, "Yedekleme için iki dakika bekleyin.");
                JSONObject answer = result(id, "onaylandi", "", TezgahWebDto.json("mesaj", "Telefonda yedekleme başlatılıyor; sonuç bekleniyor."));
                store(request, digest, "", wall.getAsLong() + 120000, answer);
                db.setTransactionSuccessful();
                db.endTransaction();
                transaction = false;
                backupAt = now;
                deadlines.put(id, now + 120000);
                host.backup(id);
                return answer;
            }
            JSONObject summary = execute(request);
            JSONObject answer = result(id, "tamam", "", summary);
            store(request, digest, "", 0, answer);
            require(writable(), "İşlem sırasında oturum yetkisi kapatıldı.");
            db.setTransactionSuccessful();
            db.endTransaction();
            transaction = false;
            TezgahWebChanges.committed(db, owner);
            host.trace(summary.getString("mesaj"));
            return answer;
        } catch (Exception e) {
            if (transaction) db.endTransaction();
            try {
                JSONObject saved = stored(id);
                if (saved != null && digest.equals(saved.getString("hash"))) return saved.getJSONObject("result");
            } catch (Exception ignored) { }
            JSONObject answer = result(id, "reddedildi", error(e), null);
            // Yanlış içerikle mevcut id'yi ezmeyiz; kayıt limiti aşımlarını da çoğaltmayız.
            try {
                if (!digest.isEmpty() && stored(id) == null) {
                    Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE + " WHERE owner_uid=? AND session_id=?", new String[]{owner, session});
                    boolean room;
                    try {
                        room = c.moveToFirst() && c.getInt(0) < MAX_IDS;
                    } finally {
                        c.close();
                    }
                    if (room) store(request, digest, "", 0, answer);
                }
            } catch (Exception ignored) {
                // Başarısız SQL işlemi tamamlandı diye raporlanmaz.
            }
            return answer;
        }
    }

    private static String error(Exception e) {
        return e instanceof IllegalArgumentException && e.getMessage() != null
                ? e.getMessage() : "İşlem kaydedilemedi. Telefonda kayıtları kontrol edin.";
    }

    @Override public synchronized void decide(String id, boolean approve) {
        try {
            JSONObject row = stored(id);
            if (row == null || !"onay_bekleniyor".equals(row.getString("state"))) return;
            if (!approve || !writable() || expired(id, row)) {
                finish(row, "iptal", !approve ? "İşlem telefondan reddedildi." : "Onay süresi veya oturum yetkisi sona erdi.");
                return;
            }
            boolean committed = false;
            JSONObject answer;
            db.beginTransaction();
            try {
                require(writable(), "Oturum yetkisi kapatıldı.");
                JSONObject request = row.getJSONObject("request");
                JSONObject prepared = prepare(request);
                require(row.getString("scope").equals(prepared.getString("scope")),
                        "Kayıtlar onay beklerken değişti. Yenileyip tekrar deneyin.");
                output.accept(result(id, "onaylandi", "", TezgahWebDto.json("mesaj", "Telefonda onaylandı; kaydediliyor.")));
                JSONObject summary = execute(request);
                answer = result(id, "tamam", "", summary);
                store(request, row.getString("hash"), "", 0, answer);
                require(writable(), "İşlem sırasında oturum yetkisi kapatıldı.");
                db.setTransactionSuccessful();
                committed = true;
            } catch (Exception e) {
                answer = result(id, "reddedildi", error(e), null);
            } finally {
                db.endTransaction();
            }
            deadlines.remove(id);
            if (!committed) store(row.getJSONObject("request"), row.getString("hash"), "", 0, answer);
            else {
                TezgahWebChanges.committed(db, owner);
                host.trace(answer.getJSONObject("ozet").getString("mesaj"));
            }
            output.accept(answer);
        } catch (Exception ignored) {
            // Hiçbir exception onay sayılmaz. Oturum/sonuç sorgusu durumu yeniden okur.
        }
    }

    private boolean expired(String id, JSONObject row) {
        Long deadline = deadlines.get(id);
        return deadline == null || mono.getAsLong() >= deadline || wall.getAsLong() >= row.optLong("deadline");
    }

    private void finish(JSONObject row, String state, String error) throws Exception {
        JSONObject request = row.getJSONObject("request");
        String id = request.getString("id");
        JSONObject answer = result(id, state, error, null);
        store(request, row.getString("hash"), "", 0, answer);
        deadlines.remove(id);
        output.accept(answer);
    }

    @Override public synchronized void backupDone(String id, boolean ok, String message) {
        try {
            JSONObject row = stored(id);
            if (row == null || !"onaylandi".equals(row.getString("state"))) return;
            JSONObject request = row.getJSONObject("request");
            if (!"yedek_al".equals(request.getString("tur"))) return;
            JSONObject answer = result(id, ok ? "tamam" : "reddedildi",
                    ok ? "" : message, TezgahWebDto.json("mesaj", ok ? "Telefonun bulut yedeği tamamlandı." : message));
            store(request, row.getString("hash"), "", 0, answer);
            deadlines.remove(id);
            output.accept(answer);
            if (ok) host.trace("Bulut yedeği tamamlandı.");
        } catch (Exception ignored) { }
    }

    @Override public synchronized void tick() {
        if (closed || mono.getAsLong() - lastExpiry < 500) return;
        lastExpiry = mono.getAsLong();
        for (String id : new ArrayList<>(deadlines.keySet())) {
            try {
                JSONObject row = stored(id);
                if (row != null && (expired(id, row) || !writable())) finish(row, "iptal", "İşlemin bekleme süresi veya yetkisi sona erdi.");
            } catch (Exception ignored) { }
        }
    }

    private void cancelPending(String message) {
        List<String> ids = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT id FROM " + TABLE + " WHERE owner_uid=? AND session_id=? AND state IN ('onay_bekleniyor','onaylandi')",
                new String[]{owner, session});
        try {
            while (c.moveToNext()) ids.add(c.getString(0));
        } finally {
            c.close();
        }
        for (String id : ids) {
            try {
                finish(stored(id), "iptal", message);
            } catch (Exception ignored) { }
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        writable = false;
        cancelPending("Bilgisayar oturumu kapandı.");
        deadlines.clear();
    }

    private TezgahClosing closing() {
        TezgahClosing c = TezgahClosing.fromPurchases(TezgahPurchases.open(db, owner));
        if (!TezgahClosing.load(db, owner, TezgahZ.OPEN_KEY, c)) c.rate = defaultRate.getAsDouble();
        return c;
    }

    private TezgahPurchases.Purchase purchase(JSONObject fields, boolean version) throws Exception {
        TezgahPurchases.Purchase p = TezgahPurchases.get(db, owner, id(fields, "alimId"));
        require(p != null, "Alım bu hesapta bulunamadı.");
        if (version) require(fingerprint(p).equals(text(fields, "surum", 64, false)), "Alım değişti. Listeyi yenileyin.");
        return p;
    }

    private TezgahLedger.Entry ledger(long id) {
        for (TezgahLedger.Entry row : TezgahLedger.entries(db, owner)) if (row.id == id) return row;
        throw new IllegalArgumentException("Cari satır bu hesapta bulunamadı.");
    }

    private TezgahCrates.Move crate(long id) {
        for (TezgahCrates.Move row : TezgahCrates.moves(db, owner)) if (row.id == id) return row;
        throw new IllegalArgumentException("Kasa hareketi bu hesapta bulunamadı.");
    }

    private static JSONObject ledgerJson(TezgahLedger.Entry e) {
        return TezgahWebDto.json("id", Long.toString(e.id), "kisi", e.party, "yon", e.kind,
                "tutar", TezgahWebDto.decimal(e.amount, 2), "kg", TezgahWebDto.decimal(e.kg, 1),
                "not", e.note, "tarih", e.date, "detay", e.detail, "zId", Long.toString(e.zId));
    }

    private static JSONObject crateJson(TezgahCrates.Move m) {
        return TezgahWebDto.json("id", Long.toString(m.id), "kisi", m.party, "yon", m.kind,
                "adet", m.count, "not", m.note, "tarih", m.date);
    }

    private static JSONObject closingJson(TezgahClosing c) {
        JSONArray delivered = new JSONArray();
        for (double v : c.delivered) delivered.put(TezgahWebDto.decimal(v, 1));
        return TezgahWebDto.json("teslim", delivered, "komisyon", TezgahWebDto.decimal(c.rate, 2),
                "avans", TezgahWebDto.decimal(c.advance, 2), "alimSayisi", c.purchases,
                "netKg", TezgahWebDto.decimal(c.boughtKg(), 1), "teslimKg", TezgahWebDto.decimal(c.deliveredKg(), 1),
                "tutar", TezgahWebDto.decimal(c.producerTotal, 2), "tuccar", TezgahWebDto.decimal(c.traderOwes(), 2));
    }

    private String purchaseScope(List<TezgahPurchases.Purchase> list) throws Exception {
        JSONArray a = new JSONArray();
        for (TezgahPurchases.Purchase p : list) a.put(fingerprint(p));
        return hash(a);
    }

    private JSONObject prepare(JSONObject request) throws Exception {
        String type = text(request, "tur", 40, false);
        JSONObject a = request.getJSONObject("alanlar");
        boolean approval = false;
        String message;
        String scope = "";
        switch (type) {
            case "uretici_ekle":
                fields(a, "ad,telefon");
                String name = text(a, "ad", 100, false);
                require(TezgahCrates.validParty(name), "Üretici adını yaz.");
                text(a, "telefon", 32, true);
                message = name + " üretici kartı eklenecek.";
                break;
            case "alim_ekle":
            case "alim_duzenle": {
                fields(a, type.equals("alim_ekle") ? "uretici,tarih,model,odenen" : "alimId,surum,uretici,tarih,model");
                String seller = text(a, "uretici", 100, false);
                require(TezgahProducers.find(db, owner, seller) != null, "Üreticiyi listeden seçin veya önce ekleyin.");
                String day = date(a);
                TezgahModel m = model(a.getJSONObject("model"));
                if (type.equals("alim_ekle")) {
                    require(number(a, "odenen", 0, TezgahLedger.MAX_AMOUNT, 2) <= m.total() + 0.005,
                            "Ödenen tutar alım toplamını aşamaz.");
                } else {
                    TezgahPurchases.Purchase p = purchase(a, true);
                    require(p.zId == 0, "Z ile kapanan alım düzenlenemez.");
                    require(m.total() + 0.005 >= p.paid, "Yeni tutar mevcut ödemelerin altına düşemez.");
                    approval = !p.date.equals(today()) || !day.equals(today());
                    scope = fingerprint(p);
                }
                message = seller + " · " + day + " · " + TezgahModel.kg(m.totalNet()) + " · "
                        + TezgahModel.tl(m.total()) + (type.equals("alim_ekle") ? " alım eklenecek." : " alım düzenlenecek.");
                break;
            }
            case "alim_sil": {
                fields(a, "alimId,surum");
                TezgahPurchases.Purchase p = purchase(a, true);
                require(p.zId == 0, "Z ile kapanan alım silinemez.");
                scope = fingerprint(p);
                approval = true;
                message = p.seller + " · " + p.date + " · " + TezgahModel.kg(p.kg) + " · " + TezgahModel.tl(p.gross)
                        + " alımı ve bağlı ödeme/kasa kayıtları silinecek.";
                break;
            }
            case "odeme": {
                fields(a, "alimId,tutar");
                TezgahPurchases.Purchase p = purchase(a, false);
                double amount = number(a, "tutar", 0.01, TezgahLedger.MAX_AMOUNT, 2);
                require(amount <= p.remaining() + 0.005, "Ödeme kalan borcu aşamaz.");
                message = p.seller + " için " + TezgahModel.tl(amount) + " ödeme kaydedilecek.";
                break;
            }
            case "toplu_odeme": {
                fields(a, "uretici,tutar");
                String seller = text(a, "uretici", 100, false);
                require(TezgahStatement.hasStatement(seller), "Üretici seçin.");
                TezgahStatement st = TezgahStatement.of(TezgahPurchases.all(db, owner, 1000000), seller);
                double amount = number(a, "tutar", 0.01, TezgahLedger.MAX_AMOUNT, 2);
                require(amount <= st.remaining() + 0.005, "Toplu ödeme kalan borcu aşamaz.");
                require(!st.allocate(amount).isEmpty(), "Ödenecek borç bulunamadı.");
                scope = purchaseScope(st.purchases);
                approval = true;
                message = seller + " · " + st.allocate(amount).size() + " alıma en eski borçtan başlayarak "
                        + TezgahModel.tl(amount) + " toplu ödeme yapılacak.";
                break;
            }
            case "cari_tahsilat":
            case "cari_odeme":
            case "kasa_giris":
            case "kasa_cikis":
                fields(a, "kisi,tutar,not");
                require(TezgahLedger.validParty(text(a, "kisi", 100, false)), "Kişi adını yaz.");
                text(a, "not", 500, true);
                message = text(a, "kisi", 100, false) + " · " + TezgahModel.tl(number(a, "tutar", 0.01, TezgahLedger.MAX_AMOUNT, 2))
                        + (type.equals("cari_tahsilat") || type.equals("kasa_giris") ? " para girişi / tahsilat kaydedilecek." : " para çıkışı / ödeme kaydedilecek.");
                break;
            case "cari_satis": {
                fields(a, "kisi,yontem,kilolar,fiyatlar,kg,fiyat,alinan,not");
                String party = text(a, "kisi", 100, false);
                require(TezgahLedger.validParty(party), "Alıcının adını yaz.");
                int method = integer(a, "yontem", 1, 3);
                fields(a, method == 1 ? "kisi,yontem,kilolar,fiyatlar,alinan,not" : "kisi,yontem,kg,fiyat,alinan,not");
                double total = method == 1 ? TezgahLedger.gradeAmount(array(a, "kilolar", 2000000, 1), array(a, "fiyatlar", 100000, 2))
                        : TezgahLedger.times(number(a, "kg", 0.1, 2000000, 1), number(a, "fiyat", 0.01, 100000, 2));
                require(TezgahLedger.amountError(TezgahLedger.SALE, total).isEmpty(), "Satış tutarı geçersiz.");
                require(number(a, "alinan", 0, TezgahLedger.MAX_AMOUNT, 2) <= total + 0.005, "Tahsilat satış tutarını aşamaz.");
                text(a, "not", 500, true);
                message = party + " için " + TezgahModel.tl(total) + " cari satış kaydedilecek.";
                break;
            }
            case "cari_sil": {
                fields(a, "kayitId,surum");
                TezgahLedger.Entry row = ledger(id(a, "kayitId"));
                scope = hash(ledgerJson(row));
                require(scope.equals(text(a, "surum", 64, false)), "Cari kayıt değişti; yenileyin.");
                approval = true;
                message = row.party + " · " + row.date + " · " + TezgahModel.tl(row.amount) + " cari satırı silinecek.";
                break;
            }
            case "kasa_adet_giris":
            case "kasa_adet_cikis":
                fields(a, "kisi,adet,not");
                require(TezgahCrates.validParty(text(a, "kisi", 100, false)), "Aracı adını yaz.");
                text(a, "not", 500, true);
                message = text(a, "kisi", 100, false) + " · " + integer(a, "adet", 1, TezgahCrates.MAX_COUNT)
                        + (type.equals("kasa_adet_giris") ? " gelen kasa kaydedilecek." : " giden kasa kaydedilecek.");
                break;
            case "kasa_adet_sil": {
                fields(a, "kayitId,surum");
                TezgahCrates.Move row = crate(id(a, "kayitId"));
                scope = hash(crateJson(row));
                require(scope.equals(text(a, "surum", 64, false)), "Kasa kaydı değişti; yenileyin.");
                approval = true;
                message = row.party + " · " + row.date + " · " + row.count + " kasa hareketi silinecek.";
                break;
            }
            case "aksam_hesabi":
                fields(a, "teslim,komisyon,avans,surum");
                scope = hash(closingJson(closing()));
                require(scope.equals(text(a, "surum", 64, false)), "Akşam hesabı değişti; yenileyin.");
                array(a, "teslim", 2000000, 1);
                number(a, "komisyon", 0, 100000, 2);
                number(a, "avans", 0, TezgahLedger.MAX_AMOUNT, 2);
                message = "Açık dönemin teslim, komisyon ve avans hesabı güncellenecek.";
                break;
            case "z_kapat":
            case "donem_kapat": {
                fields(a, "");
                List<TezgahPurchases.Purchase> open = TezgahPurchases.open(db, owner);
                require(!open.isEmpty(), "Açık dönemde alım yok.");
                TezgahClosing c = closing();
                scope = hash(TezgahWebDto.json("alimlar", purchaseScope(open), "hesap", closingJson(c)));
                approval = true;
                message = open.size() + " alım · " + TezgahModel.kg(c.boughtKg()) + " · " + TezgahModel.tl(c.producerTotal)
                        + " dönem Z ile kapatılacak. Alımlar kilitlenir; geri açma yoktur. Tüccar hesabı: " + TezgahModel.tl(c.traderOwes());
                break;
            }
            case "z_cariye_isle": {
                fields(a, "zId,kisi,alinan");
                TezgahZ.Record z = TezgahZ.get(db, owner, id(a, "zId"));
                require(z != null, "Z bu hesapta bulunamadı.");
                require(!TezgahLedger.hasZ(db, owner, z.id), "Bu Z zaten cari hesaba işlendi.");
                require(TezgahLedger.validParty(text(a, "kisi", 100, false)), "Tüccar adını yaz.");
                TezgahClosing c = TezgahZ.closingOf(z, TezgahPurchases.forZ(db, owner, z.id));
                require(number(a, "alinan", 0, TezgahLedger.MAX_AMOUNT, 2) <= Math.abs(c.traderOwes()) + 0.005,
                        "Ödeme/tahsilat Z tutarını aşamaz.");
                message = z.label() + " · " + TezgahModel.tl(c.traderOwes()) + " cari hesaba işlenecek.";
                break;
            }
            case "yedek_al":
                fields(a, "");
                message = "Telefonun mevcut bulut yedeği başlatılacak.";
                break;
            case "yedek_geri_yukle":
                throw new IllegalArgumentException("Yedekten geri yükleme yalnız telefondan yapılabilir.");
            default:
                throw new IllegalArgumentException("Bu komut desteklenmiyor.");
        }
        return TezgahWebDto.json("approval", approval, "scope", scope, "mesaj", message);
    }

    private static double[] array(JSONObject a, String key, double max, int scale) throws Exception {
        JSONArray values = a.getJSONArray(key);
        require(values.length() == 6, "Altı elek değeri gerekli.");
        double[] out = new double[6];
        for (int i = 0; i < 6; i++) out[i] = number(TezgahWebDto.json(key, values.get(i)), key, 0, max, scale);
        return out;
    }

    private JSONObject execute(JSONObject request) throws Exception {
        require(writable(), "Oturum yetkisi kapatıldı.");
        JSONObject prepared = prepare(request);
        JSONObject a = request.getJSONObject("alanlar");
        String type = request.getString("tur");
        long saved = 0;
        long now = wall.getAsLong();
        switch (type) {
            case "uretici_ekle":
                saved = TezgahProducers.add(db, owner, text(a, "ad", 100, false), text(a, "telefon", 32, true), now);
                break;
            case "alim_ekle": {
                TezgahProducers.Producer p = TezgahProducers.find(db, owner, a.getString("uretici"));
                saved = TezgahPurchases.add(db, owner, date(a), p.name, p.phone, model(a.getJSONObject("model")),
                        number(a, "odenen", 0, TezgahLedger.MAX_AMOUNT, 2), now);
                break;
            }
            case "alim_duzenle": {
                TezgahProducers.Producer p = TezgahProducers.find(db, owner, a.getString("uretici"));
                saved = TezgahPurchases.update(db, owner, id(a, "alimId"), date(a), p.name, p.phone,
                        model(a.getJSONObject("model"))) ? id(a, "alimId") : -1;
                break;
            }
            case "alim_sil":
                saved = TezgahPurchases.delete(db, owner, id(a, "alimId")) ? id(a, "alimId") : -1;
                break;
            case "odeme":
                saved = TezgahPayments.pay(db, owner, id(a, "alimId"), number(a, "tutar", 0.01, TezgahLedger.MAX_AMOUNT, 2), today(), now) ? id(a, "alimId") : -1;
                break;
            case "toplu_odeme":
                saved = TezgahPayments.payBulk(db, owner, a.getString("uretici"), number(a, "tutar", 0.01, TezgahLedger.MAX_AMOUNT, 2), today(), now);
                break;
            case "cari_tahsilat":
            case "cari_odeme":
            case "kasa_giris":
            case "kasa_cikis": {
                boolean incoming = type.equals("cari_tahsilat") || type.equals("kasa_giris");
                saved = TezgahLedger.add(db, owner, a.getString("kisi"), incoming ? TezgahLedger.RECEIVED : TezgahLedger.PAID,
                        number(a, "tutar", 0.01, TezgahLedger.MAX_AMOUNT, 2), 0, 0, "", text(a, "not", 500, true), today(), 0, now);
                break;
            }
            case "cari_satis": {
                int method = integer(a, "yontem", 1, 3);
                fields(a, method == 1 ? "kisi,yontem,kilolar,fiyatlar,alinan,not" : "kisi,yontem,kg,fiyat,alinan,not");
                saved = TezgahLedger.recordSale(db, owner, a.getString("kisi"),
                        method == 1 ? array(a, "kilolar", 2000000, 1) : new double[6],
                        method == 1 ? array(a, "fiyatlar", 100000, 2) : new double[6],
                        method == 1 ? 0 : number(a, "kg", 0.1, 2000000, 1),
                        method == 1 ? 0 : number(a, "fiyat", 0.01, 100000, 2), method,
                        number(a, "alinan", 0, TezgahLedger.MAX_AMOUNT, 2), text(a, "not", 500, true), today(), now);
                break;
            }
            case "cari_sil":
                saved = TezgahLedger.delete(db, owner, id(a, "kayitId")) ? id(a, "kayitId") : -1;
                break;
            case "kasa_adet_giris":
            case "kasa_adet_cikis":
                saved = TezgahCrates.add(db, owner, a.getString("kisi"), type.equals("kasa_adet_giris") ? TezgahCrates.RETURNED : TezgahCrates.GAVE,
                        integer(a, "adet", 1, TezgahCrates.MAX_COUNT), today(), 0, text(a, "not", 500, true), now);
                break;
            case "kasa_adet_sil":
                saved = TezgahCrates.delete(db, owner, id(a, "kayitId")) ? id(a, "kayitId") : -1;
                break;
            case "aksam_hesabi": {
                TezgahClosing c = closing();
                double[] delivered = array(a, "teslim", 2000000, 1);
                for (int g = 0; g < 6; g++) c.setDelivered(g, delivered[g]);
                c.rate = number(a, "komisyon", 0, 100000, 2);
                c.advance = number(a, "avans", 0, TezgahLedger.MAX_AMOUNT, 2);
                require(Math.abs(c.traderOwes()) <= TezgahLedger.MAX_AMOUNT, "Akşam hesabının tutarı çok büyük.");
                saved = TezgahClosing.save(db, owner, TezgahZ.OPEN_KEY, c, now) ? 1 : -1;
                break;
            }
            case "z_kapat":
            case "donem_kapat":
                saved = TezgahZ.close(db, owner, closing(), now);
                break;
            case "z_cariye_isle": {
                TezgahZ.Record z = TezgahZ.get(db, owner, id(a, "zId"));
                TezgahClosing c = TezgahZ.closingOf(z, TezgahPurchases.forZ(db, owner, z.id));
                saved = TezgahLedger.recordDay(db, owner, a.getString("kisi"), c.traderOwes(), c.deliveredKg(),
                        number(a, "alinan", 0, TezgahLedger.MAX_AMOUNT, 2), z.label() + " · teslim " + TezgahModel.kg(c.deliveredKg()), today(), z.id, now);
                break;
            }
            default:
                throw new IllegalArgumentException("Bu komut kaydedilemez.");
        }
        require(saved > 0, "Kayıt tamamlanamadı; hiçbir değişiklik kaydedilmedi.");
        return TezgahWebDto.json("kayitId", Long.toString(saved), "mesaj", prepared.getString("mesaj")
                .replace("eklenecek", "eklendi").replace("düzenlenecek", "düzenlendi")
                .replace("silinecek", "silindi").replace("kaydedilecek", "kaydedildi")
                .replace("yapılacak", "yapıldı").replace("güncellenecek", "güncellendi")
                .replace("kapatılacak", "kapatıldı").replace("işlenecek", "işlendi"));
    }

    private JSONObject detail(TezgahPurchases.Purchase p) throws Exception {
        JSONObject item = TezgahWebDto.purchase(p);
        TezgahModel m = TezgahModel.decode(p.data);
        JSONArray prices = new JSONArray();
        JSONArray rows = new JSONArray();
        for (double value : m.priceArray()) prices.put(TezgahWebDto.decimal(value, 2));
        for (TezgahModel.Weighing w : m.items()) rows.put(TezgahWebDto.json("elek", w.grade, "kasa", w.kasa, "kg", TezgahWebDto.decimal(w.kg, 1)));
        item.put("model", TezgahWebDto.json("dara", TezgahWebDto.decimal(m.tare(), 1), "fiyatlar", prices, "tartimlar", rows));
        item.put("surum", fingerprint(p));
        return item;
    }

    private JSONObject page(List<JSONObject> all, JSONObject args) throws Exception {
        int offset = args.has("cursor") ? integer(args, "cursor", 0, 1000000) : 0;
        int limit = args.has("limit") ? integer(args, "limit", 1, 100) : 100;
        JSONArray rows = new JSONArray();
        int bytes = 0;
        int at = Math.min(offset, all.size());
        while (at < all.size() && rows.length() < limit) {
            JSONObject row = all.get(at);
            int size = TezgahWebProtocol.utf8(row.toString()).length;
            if (bytes + size > 20000) break;
            rows.put(row);
            bytes += size;
            at++;
        }
        return TezgahWebDto.json("items", rows, "nextCursor", at < all.size() ? at : null,
                "complete", at >= all.size(), "count", all.size());
    }

    @Override public synchronized JSONObject read(String op, JSONObject args) throws Exception {
        require(!closed && alive.getAsBoolean(), "Telefon oturumu kapandı.");
        switch (op) {
            case "web_meta":
                fields(args, "");
                return TezgahWebDto.json("tarih", today(), "yetki", writable() ? "full" : "read", "komutSiniri", MAX_IDS);
            case "komut_sonucu": {
                fields(args, "id");
                String id = text(args, "id", 22, false);
                TezgahWebProtocol.token(id, 16);
                JSONObject old = stored(id);
                return old == null ? result(id, "bulunamadi", "Komut telefona ulaşmamış.", null) : old.getJSONObject("result");
            }
            case "ureticiler": {
                fields(args, "q");
                JSONArray rows = new JSONArray();
                for (TezgahProducers.Producer p : TezgahProducers.search(db, owner, text(args, "q", 100, true), 100)) {
                    rows.put(TezgahWebDto.json("ad", p.name, "telefon", p.phone));
                }
                return TezgahWebDto.json("items", rows);
            }
            case "alim_detay":
                fields(args, "alimId");
                return detail(purchase(args, false));
            case "alim_defteri":
            case "ekstre": {
                fields(args, "uretici,cursor,limit");
                String seller = text(args, "uretici", 100, true);
                List<TezgahPurchases.Purchase> all = TezgahPurchases.all(db, owner, 1000000);
                List<JSONObject> rows = new ArrayList<>();
                for (TezgahPurchases.Purchase p : all) {
                    if (seller.isEmpty() || TezgahCrates.key(seller).equals(TezgahCrates.key(p.seller))) rows.add(TezgahWebDto.purchase(p));
                }
                return page(rows, args);
            }
            case "cari": {
                fields(args, "cursor,limit");
                List<TezgahLedger.Entry> entries = TezgahLedger.entries(db, owner);
                List<JSONObject> rows = new ArrayList<>();
                for (TezgahLedger.Entry e : entries) {
                    JSONObject row = ledgerJson(e);
                    row.put("surum", hash(row));
                    rows.add(row);
                }
                JSONObject result = page(rows, args);
                double[] totals = TezgahLedger.totals(entries);
                result.put("alacak", TezgahWebDto.decimal(totals[0], 2));
                result.put("borc", TezgahWebDto.decimal(totals[1], 2));
                return result;
            }
            case "kasalar": {
                fields(args, "cursor,limit");
                List<JSONObject> rows = new ArrayList<>();
                List<TezgahCrates.Move> moves = TezgahCrates.moves(db, owner);
                for (TezgahCrates.Move m : moves) {
                    JSONObject row = crateJson(m);
                    row.put("surum", hash(row));
                    rows.add(row);
                }
                JSONObject result = page(rows, args);
                TezgahCrates.Summary summary = TezgahCrates.summary(moves, today());
                result.put("disarida", summary.out);
                result.put("bende", summary.held);
                return result;
            }
            case "aksam_hesabi": {
                fields(args, "");
                JSONObject c = closingJson(closing());
                c.put("surum", hash(c));
                return c;
            }
            case "z_listesi": {
                fields(args, "cursor,limit");
                List<JSONObject> rows = new ArrayList<>();
                for (TezgahZ.Record z : TezgahZ.list(db, owner, 1000000)) rows.add(TezgahWebDto.json("id", Long.toString(z.id),
                        "etiket", z.label(), "alimSayisi", z.count, "kg", TezgahWebDto.decimal(z.kg, 1), "tutar", TezgahWebDto.decimal(z.gross, 2),
                        "cariyeIslendi", TezgahLedger.hasZ(db, owner, z.id)));
                return page(rows, args);
            }
            default:
                throw new IllegalArgumentException("Bu görünüm desteklenmiyor.");
        }
    }
}
