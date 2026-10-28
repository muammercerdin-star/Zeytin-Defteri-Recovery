package com.zeytindefteri.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kasa defteri: tezgahçı ile aracı (tüccar) arasındaki kasa hesabı.
 *
 * Aracı sabah boş kasa getirir (GELEN), akşam dolu kasaları alır (GİDEN). Üreticiyle kasa işi yoktur
 * (üretici malını sele sepetle getirir).
 * - Her hareket bir satır: kişi (aracı), yön (GİDEN = GAVE / GELEN = RETURNED), adet, gün.
 * - Kişi bakiyesi = giden − gelen. Eksiyse aracının kasası bende (çoğu zaman böyle); artıysa benim kasam onda.
 * - purchase_id: eski sürümde tezgah alımından işlenen satırlar için; alım silinince onlar da silinir.
 * - Yalnız bu telefonda; owner_uid sütunu olduğu için hesap silinince LocalAccountWipe temizler; yedeğe girer.
 */
public final class TezgahCrates {
    static final String TABLE = "tezgah_crates";
    public static final int GAVE = 1;      // giden kasa (dolu kasa aracıya gitti / kasa dışarı çıktı)
    public static final int RETURNED = 2;  // gelen kasa (aracıdan boş kasa geldi / kasa içeri girdi)
    public static final int MAX_COUNT = 5000;

    private TezgahCrates() {}

    public static final class Move {
        public long id, purchaseId, createdAt;
        public String party = "", date = "", note = "";
        public int kind, count;

        /** Bakiyeye etkisi: verilen +, gelen −. */
        public int signed() { return kind == GAVE ? count : -count; }
    }

    /** Kişi özeti: toplam verilen, geri gelen, bakiye, son hareket günü. */
    public static final class Party {
        public String name = "", key = "", lastDate = "";
        public int gave, returned;
        public long lastAt;

        /** Artı: kasalarım onda. Eksi: onun kasası bende. */
        public int balance() { return gave - returned; }
    }

    /** Genel durum: dışarıdaki kasa (artı bakiyeler toplamı), bende duran başkasının kasası, bugünkü hareket. */
    public static final class Summary {
        public int out, held, people, todayGave, todayReturned;

        /** Toplam kasa biliniyorsa elde kalan; bilinmiyorsa −1. */
        public int inHand(int total) { return total > 0 ? Math.max(0, total - out) : -1; }
    }

    static void ensure(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE + " (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "party TEXT NOT NULL, party_key TEXT NOT NULL, kind INTEGER NOT NULL, count INTEGER NOT NULL, "
                + "moved_on TEXT, purchase_id INTEGER DEFAULT 0, note TEXT DEFAULT '', created_at INTEGER DEFAULT 0, "
                + "owner_uid TEXT DEFAULT '')");
    }

    /** Kişi adının eşleştirme anahtarı: Türkçe harfsiz, küçük harf, tek boşluk ("Volkan  AFŞAR" = "volkan afsar"). */
    public static String key(String name) {
        return TezgahSearch.fold(name).replaceAll("\\s+", " ").trim();
    }

    /** Kasa takibi için geçerli ad mı (boş ya da münferit satıcı olamaz). */
    public static boolean validParty(String name) {
        String k = key(name);
        return !k.isEmpty() && !k.equals(key(TezgahWizardFlow.WALK_IN));
    }

    /** Adet hatası; boşsa geçerli. */
    public static String countError(int count) {
        if (count <= 0) return "Kasa adedini yaz.";
        if (count > MAX_COUNT) return "En çok " + MAX_COUNT + " kasa yazılabilir.";
        return "";
    }

    /** Hareket ekler; geçersizse −1. */
    public static long add(SQLiteDatabase db, String owner, String party, int kind, int count, String date,
                           long purchaseId, String note, long now) {
        if (!validParty(party) || (kind != GAVE && kind != RETURNED) || !countError(count).isEmpty()) return -1;
        ensure(db);
        ContentValues v = new ContentValues();
        v.put("party", party.trim().replaceAll("\\s+", " "));
        v.put("party_key", key(party));
        v.put("kind", kind);
        v.put("count", count);
        v.put("moved_on", date == null ? "" : date);
        v.put("purchase_id", Math.max(0, purchaseId));
        v.put("note", note == null ? "" : note.trim());
        v.put("created_at", now);
        v.put("owner_uid", owner == null ? "" : owner);
        return db.insert(TABLE, null, v);
    }

    public static boolean delete(SQLiteDatabase db, String owner, long id) {
        ensure(db);
        return db.delete(TABLE, "id=? AND COALESCE(owner_uid,'')=?",
                new String[]{String.valueOf(id), owner == null ? "" : owner}) > 0;
    }

    /** Alım silinince ondan işlenen kasa hareketleri de silinir. */
    public static void deleteFor(SQLiteDatabase db, String owner, long purchaseId) {
        if (purchaseId <= 0) return;
        ensure(db);
        db.delete(TABLE, "purchase_id=? AND COALESCE(owner_uid,'')=?",
                new String[]{String.valueOf(purchaseId), owner == null ? "" : owner});
    }

    /** Bütün hareketler, yeniden eskiye. */
    public static List<Move> moves(SQLiteDatabase db, String owner) {
        return query(db, owner, null);
    }

    /** Bir kişinin hareketleri, yeniden eskiye. */
    public static List<Move> movesOf(SQLiteDatabase db, String owner, String party) {
        return query(db, owner, key(party));
    }

    private static List<Move> query(SQLiteDatabase db, String owner, String partyKey) {
        ensure(db);
        List<Move> out = new ArrayList<>();
        String o = owner == null ? "" : owner;
        Cursor c = partyKey == null
                ? db.rawQuery("SELECT id, party, kind, count, moved_on, purchase_id, note, created_at FROM " + TABLE
                + " WHERE COALESCE(owner_uid,'')=? ORDER BY created_at DESC, id DESC", new String[]{o})
                : db.rawQuery("SELECT id, party, kind, count, moved_on, purchase_id, note, created_at FROM " + TABLE
                + " WHERE COALESCE(owner_uid,'')=? AND party_key=? ORDER BY created_at DESC, id DESC", new String[]{o, partyKey});
        try {
            while (c.moveToNext()) {
                Move m = new Move();
                m.id = c.getLong(0);
                m.party = nz(c.getString(1));
                m.kind = c.getInt(2);
                m.count = c.getInt(3);
                m.date = nz(c.getString(4));
                m.purchaseId = c.getLong(5);
                m.note = nz(c.getString(6));
                m.createdAt = c.getLong(7);
                out.add(m);
            }
        } finally {
            c.close();
        }
        return out;
    }

    /** Hareketlerden kişi özetleri: önce hesabı açık olanlar (kasa farkı çoktan aza), sonra kapananlar. */
    public static List<Party> parties(List<Move> moves) {
        Map<String, Party> map = new LinkedHashMap<>();
        for (Move m : moves) {
            String k = key(m.party);
            if (k.isEmpty()) continue;
            Party p = map.get(k);
            if (p == null) {
                p = new Party();
                p.key = k;
                p.name = m.party;          // liste yeniden eskiye: ilk görülen en son yazılan ad
                map.put(k, p);
            }
            if (m.kind == GAVE) p.gave += m.count; else if (m.kind == RETURNED) p.returned += m.count;
            if (m.createdAt > p.lastAt) { p.lastAt = m.createdAt; p.lastDate = m.date; }
            else if (p.lastDate.isEmpty()) p.lastDate = m.date;
        }
        List<Party> out = new ArrayList<>(map.values());
        Collections.sort(out, (a, b) -> {
            int aa = Math.abs(a.balance()), ab = Math.abs(b.balance());
            if ((aa == 0) != (ab == 0)) return aa == 0 ? 1 : -1;   // hesabı açık olanlar önce
            if (aa != ab) return ab - aa;                            // en çok kasa farkı olan başta
            return Long.compare(b.lastAt, a.lastAt);                 // sonra en son hareket
        });
        return out;
    }

    public static Summary summary(List<Move> moves, String today) {
        Summary s = new Summary();
        for (Party p : parties(moves)) {
            if (p.balance() > 0) { s.out += p.balance(); s.people++; }
            else if (p.balance() < 0) s.held += -p.balance();
        }
        for (Move m : moves) {
            if (today == null || !today.equals(m.date)) continue;
            if (m.kind == GAVE) s.todayGave += m.count; else if (m.kind == RETURNED) s.todayReturned += m.count;
        }
        return s;
    }

    /** Günlük kasa hesabı: devreden + bugün gelen − bugün giden = sende kalan (aracının kasası). */
    public static final class Day {
        public int carried, in, out;

        /** Akşam sende kalan kasa (eksi: senin kasan onda). */
        public int held() { return carried + in - out; }
    }

    /**
     * Bir kişinin (partyKey; null = herkes) günlük kasa hesabı. Devreden: bugünden önceki hareketlerden sende kalan.
     * Tarihi bugünden sonra olan hareket bugüne sayılır.
     */
    public static Day day(List<Move> moves, String partyKey, String today) {
        Day d = new Day();
        String t = today == null ? "" : today;
        for (Move m : moves) {
            if (partyKey != null && !key(m.party).equals(partyKey)) continue;
            boolean before = !m.date.isEmpty() && m.date.compareTo(t) < 0;
            if (before) d.carried -= m.signed();
            else if (m.kind == RETURNED) d.in += m.count;
            else if (m.kind == GAVE) d.out += m.count;
        }
        return d;
    }

    /** "Devreden 20 · gelen 100 · giden 110 · sende 10" (WhatsApp ve gün sonu için). */
    public static String dayLine(Day d) {
        int h = d.held();
        return "Devreden " + d.carried + " · gelen " + d.in + " · giden " + d.out + " · "
                + (h >= 0 ? "sende kalan " + h : "senin " + (-h) + " kasan onda");
    }

    /** Bir kişinin bakiyesi (adı yoksa 0). */
    public static int balanceOf(SQLiteDatabase db, String owner, String party) {
        if (!validParty(party)) return 0;
        int b = 0;
        for (Move m : movesOf(db, owner, party)) b += m.signed();
        return b;
    }

    /** "Aracı · 20 kasası sende" / "Aracı · 5 kasan onda" / "kasa hesabı kapalı". */
    public static String balanceLine(String name, int balance) {
        String n = name == null || name.trim().isEmpty() ? "Bu kişi" : name.trim();
        if (balance > 0) return n + " · " + balance + " kasan onda";
        if (balance < 0) return n + " · " + (-balance) + " kasası sende";
        return n + " · kasa hesabı kapalı";
    }

    /** Kasa hesabı mesajı (bilgi amaçlı, WhatsApp). */
    public static String reminder(String name, int balance, String trader) {
        String n = name == null ? "" : name.trim();
        String t = trader == null ? "" : trader.trim();
        StringBuilder b = new StringBuilder();
        b.append("Merhaba").append(n.isEmpty() ? "" : " " + n).append(",\n");
        if (balance > 0) b.append("Kayıtlarımıza göre sende ").append(balance).append(" kasamız görünüyor.");
        else if (balance < 0) b.append("Kayıtlarımıza göre bende senin ").append(-balance).append(" kasan var.");
        else b.append("Kasa hesabımız kapalı görünüyor, teşekkürler.");
        b.append("\n").append(t.isEmpty() ? "" : t + " · ").append("Zeytin Defteri kasa defteri");
        return b.toString();
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
