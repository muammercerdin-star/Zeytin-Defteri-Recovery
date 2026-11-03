package com.zeytindefteri.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Owner kapsamlı üretici kartları; eski alım adları da katalogda görünür. */
public final class TezgahProducers {
    private TezgahProducers() { }

    public static final class Producer {
        public String name;
        public String phone;
        public Producer(String name, String phone) {
            this.name = name;
            this.phone = phone;
        }
    }

    public static long add(SQLiteDatabase db, String owner, String name, String phone, long now) {
        if (!TezgahCrates.validParty(name) || name.length() > 100 || phone == null || phone.length() > 32) return -1;
        TezgahPurchases.ensure(db);
        boolean committed = false;
        db.beginTransaction();
        try {
            String key = TezgahCrates.key(name);
            Cursor c = db.rawQuery("SELECT id FROM tezgah_producers WHERE owner_uid=? AND name_key=?",
                    new String[]{owner, key});
            try {
                if (c.moveToFirst()) {
                    db.setTransactionSuccessful();
                    return c.getLong(0);
                }
            } finally {
                c.close();
            }
            ContentValues v = new ContentValues();
            v.put("owner_uid", owner);
            v.put("name", name.trim().replaceAll("\\s+", " "));
            v.put("name_key", key);
            v.put("phone", phone.trim());
            v.put("created_at", now);
            long id = db.insert("tezgah_producers", null, v);
            if (id <= 0) return -1;
            db.setTransactionSuccessful();
            committed = true;
            return id;
        } finally {
            db.endTransaction();
            if (committed) TezgahPurchases.webSignal(db, owner, "committed");
        }
    }

    public static List<Producer> search(SQLiteDatabase db, String owner, String search, int limit) {
        TezgahPurchases.ensure(db);
        Map<String, Producer> all = new LinkedHashMap<>();
        Cursor c = db.rawQuery("SELECT name, phone FROM tezgah_producers WHERE owner_uid=? ORDER BY name",
                new String[]{owner});
        try {
            while (c.moveToNext()) all.put(TezgahCrates.key(c.getString(0)), new Producer(c.getString(0), c.getString(1)));
        } finally {
            c.close();
        }
        c = db.rawQuery("SELECT seller_name, seller_phone FROM tezgah_purchases WHERE COALESCE(owner_uid,'')=? ORDER BY created_at DESC, id DESC",
                new String[]{owner});
        try {
            while (c.moveToNext()) {
                String name = c.getString(0);
                String key = TezgahCrates.key(name);
                if (TezgahCrates.validParty(name) && !all.containsKey(key)) all.put(key, new Producer(name, c.getString(1)));
            }
        } finally {
            c.close();
        }
        List<Producer> out = new ArrayList<>();
        String query = TezgahCrates.key(search);
        for (Map.Entry<String, Producer> row : all.entrySet()) {
            if (row.getKey().contains(query)) out.add(row.getValue());
            if (out.size() >= Math.max(1, Math.min(200, limit))) break;
        }
        return out;
    }

    public static Producer find(SQLiteDatabase db, String owner, String name) {
        TezgahPurchases.ensure(db);
        String key = TezgahCrates.key(name);
        Cursor catalog = db.rawQuery("SELECT name,phone FROM tezgah_producers WHERE owner_uid=? AND name_key=?",
                new String[]{owner, key});
        try {
            if (catalog.moveToFirst()) return new Producer(catalog.getString(0), catalog.getString(1));
        } finally {
            catalog.close();
        }
        Cursor history = db.rawQuery("SELECT seller_name,seller_phone FROM tezgah_purchases WHERE COALESCE(owner_uid,'')=? ORDER BY created_at DESC,id DESC",
                new String[]{owner});
        try {
            while (history.moveToNext()) {
                if (key.equals(TezgahCrates.key(history.getString(0)))) return new Producer(history.getString(0), history.getString(1));
            }
        } finally {
            history.close();
        }
        return null;
    }
}
