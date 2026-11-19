package com.zeytindefteri.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Öğrenilen sözlerin telefondaki tablosu (İP4). Ana veritabanından AYRI bir SQLite dosyasıdır (zd_learned_terms.db):
 * böylece Android'in otomatik Google yedeğinden ve cihaz aktarımından hariç tutulabilir (push_backup_rules,
 * push_data_extraction_rules) ve Ayarlar → Yedekle dosyasına girmez. Sözler telefondan çıkmaz.
 *
 * Tablolar (hesap başına): learned_terms (söz, anlam, tür, kaynak, tarih, kullanım), declined_sentences ("Vazgeç").
 * Hata olursa sessizce geçer: sözlük okunamazsa asistan kurallarla bugünkü gibi çalışır.
 */
final class LearnedTermsDb extends SQLiteOpenHelper {
    static final String FILE = "zd_learned_terms.db";
    private static final int VERSION = 1;
    private static LearnedTermsDb instance;

    private LearnedTermsDb(Context context) {
        super(context.getApplicationContext(), FILE, null, VERSION);
    }

    static synchronized LearnedTermsDb get(Context context) {
        if (instance == null) {
            instance = new LearnedTermsDb(context);
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS learned_terms (owner TEXT NOT NULL, term TEXT NOT NULL, word TEXT NOT NULL, "
                + "action TEXT NOT NULL, field TEXT NOT NULL DEFAULT '', value TEXT NOT NULL DEFAULT '', kind TEXT NOT NULL, "
                + "source TEXT NOT NULL, created INTEGER NOT NULL, uses INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (owner, term))");
        db.execSQL("CREATE TABLE IF NOT EXISTS declined_sentences (owner TEXT NOT NULL, sentence TEXT NOT NULL, "
                + "created INTEGER NOT NULL, PRIMARY KEY (owner, sentence))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onCreate(db);
    }

    /** Bu hesabın sözlüğü. */
    static LearnedTerms.Store store(Context context, String owner) {
        return new OwnerStore(get(context), owner == null ? "" : owner);
    }

    /** Hesabın bütün sözlerini ve "Vazgeç" kayıtlarını siler (hesap silinince). */
    static void clear(Context context, String owner) {
        try {
            SQLiteDatabase db = get(context).getWritableDatabase();
            String[] args = {owner == null ? "" : owner};
            db.delete("learned_terms", "owner=?", args);
            db.delete("declined_sentences", "owner=?", args);
        } catch (RuntimeException ignored) {
            // sözlük silinemezse sessiz
        }
    }

    private static final class OwnerStore implements LearnedTerms.Store {
        private final LearnedTermsDb helper;
        private final String owner;

        OwnerStore(LearnedTermsDb helper, String owner) {
            this.helper = helper;
            this.owner = owner;
        }

        @Override
        public List<LearnedTerms.Term> all() {
            List<LearnedTerms.Term> out = new ArrayList<>();
            try (Cursor c = helper.getReadableDatabase().query("learned_terms", null, "owner=?", new String[]{owner},
                    null, null, "created")) {
                while (c.moveToNext()) {
                    out.add(read(c));
                }
            } catch (RuntimeException ignored) {
                // okunamadı: boş sözlük (kurallar çalışır)
            }
            return out;
        }

        @Override
        public LearnedTerms.Term get(String term) {
            try (Cursor c = helper.getReadableDatabase().query("learned_terms", null, "owner=? AND term=?",
                    new String[]{owner, term}, null, null, null)) {
                if (c.moveToFirst()) {
                    return read(c);
                }
            } catch (RuntimeException ignored) {
                // okunamadı
            }
            return null;
        }

        @Override
        public void put(LearnedTerms.Term t) {
            try {
                ContentValues v = new ContentValues();
                v.put("owner", owner);
                v.put("term", t.term);
                v.put("word", t.word);
                v.put("action", t.action);
                v.put("field", t.field);
                v.put("value", t.value);
                v.put("kind", t.kind);
                v.put("source", t.source);
                v.put("created", t.created);
                v.put("uses", t.uses);
                helper.getWritableDatabase().insertWithOnConflict("learned_terms", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            } catch (RuntimeException ignored) {
                // yazılamadı: öğrenme bu sefer olmaz
            }
        }

        @Override
        public boolean remove(String term) {
            try {
                return helper.getWritableDatabase().delete("learned_terms", "owner=? AND term=?", new String[]{owner, term}) > 0;
            } catch (RuntimeException e) {
                return false;
            }
        }

        @Override
        public void used(String term) {
            try {
                helper.getWritableDatabase().execSQL("UPDATE learned_terms SET uses=uses+1 WHERE owner=? AND term=?",
                        new Object[]{owner, term});
            } catch (RuntimeException ignored) {
                // sayılmadı
            }
        }

        @Override
        public boolean declined(String sentence) {
            try (Cursor c = helper.getReadableDatabase().query("declined_sentences", new String[]{"sentence"},
                    "owner=? AND sentence=?", new String[]{owner, sentence}, null, null, null)) {
                return c.moveToFirst();
            } catch (RuntimeException e) {
                return false;
            }
        }

        @Override
        public void decline(String sentence) {
            try {
                ContentValues v = new ContentValues();
                v.put("owner", owner);
                v.put("sentence", sentence);
                v.put("created", System.currentTimeMillis());
                helper.getWritableDatabase().insertWithOnConflict("declined_sentences", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            } catch (RuntimeException ignored) {
                // yazılamadı
            }
        }

        private static LearnedTerms.Term read(Cursor c) {
            return new LearnedTerms.Term(
                    c.getString(c.getColumnIndexOrThrow("term")),
                    c.getString(c.getColumnIndexOrThrow("word")),
                    c.getString(c.getColumnIndexOrThrow("action")),
                    c.getString(c.getColumnIndexOrThrow("field")),
                    c.getString(c.getColumnIndexOrThrow("value")),
                    c.getString(c.getColumnIndexOrThrow("kind")),
                    c.getString(c.getColumnIndexOrThrow("source")),
                    c.getLong(c.getColumnIndexOrThrow("created")),
                    c.getInt(c.getColumnIndexOrThrow("uses")));
        }
    }
}
