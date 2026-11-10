package com.zeytindefteri.app;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.*;
import java.util.*;

/** Uçtan uca deneme köprüsü: gerçek TezgahWebPhone, TezgahWebClient'ın yaptığı gibi sürülür. */
public final class E2EPhone {
    static long mono() { return System.nanoTime() / 1000000; }
    public static void main(String[] args) throws Exception {
        SQLiteDatabase db = new SQLiteDatabase(args[0], args[1]);
        TezgahPurchases.ensure(db);
        db.execSQL("INSERT INTO tezgah_purchases(owner_uid,record_date,seller_name,total_kg,gross_total,amount_paid,created_at) VALUES ('A','2026-11-03','Deneme',10,100,25,1)");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        ArrayList<String> out = new ArrayList<>();
        TezgahWebPhone phone = null;
        boolean[] ready = {false};
        for (String line; (line = in.readLine()) != null; ) {
            out.clear();
            JSONObject command = new JSONObject(line);
            String error = null;
            try {
                String cmd = command.getString("cmd");
                if (cmd.equals("qr")) {
                    if (phone != null) phone.close();
                    ready[0] = false;
                    TezgahWebPhone.Qr q = TezgahWebPhone.Qr.parse(command.getString("text"), System.currentTimeMillis() / 1000);
                    phone = new TezgahWebPhone(q, new TezgahWebRepository(db, "A"), out::add, v -> ready[0] = v);
                    phone.approve();
                } else if (cmd.equals("opened")) {
                    phone.opened();
                } else if (cmd.equals("receive")) {
                    if (phone.expired(mono())) throw new IllegalStateException("TICK:" + phone.expiryMessage());
                    phone.receive(command.getString("frame"), mono());
                } else if (cmd.equals("tick")) {
                    if (phone.expired(mono())) throw new IllegalStateException("TICK:" + phone.expiryMessage());
                } else if (cmd.equals("close")) {
                    if (phone != null) phone.close();
                    break;
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            JSONArray messages = new JSONArray();
            for (String m : out) messages.put(new JSONObject(m));
            System.out.println(TezgahWebDto.json("frames", messages, "error", error, "ready", ready[0]));
            System.out.flush();
        }
    }
}
