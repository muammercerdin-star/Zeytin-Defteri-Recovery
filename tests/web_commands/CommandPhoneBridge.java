package com.zeytindefteri.app;

import android.database.sqlite.SQLiteDatabase;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.json.*;

/** Gerçek telefon kripto/komut motoru + gerçek SQLite; yalnız taşıma/Android bildirim UI'si taklit. */
public final class CommandPhoneBridge {
    public static void main(String[] args) throws Exception {
        SQLiteDatabase db = new SQLiteDatabase(args[0], args[1]);
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        List<String> output = new ArrayList<>();
        AtomicLong clock = new AtomicLong(10000);
        TezgahWebPhone phone = null;
        TezgahWebCommands commands = null;
        List<String> approval = new ArrayList<>();
        List<String> traces = new ArrayList<>();
        for (String line; (line = input.readLine()) != null;) {
            JSONObject request = new JSONObject(line);
            output.clear();
            String error = null;
            clock.addAndGet(1100);
            try {
                switch (request.getString("cmd")) {
                    case "qr":
                        if (phone != null) phone.close();
                        TezgahWebPhone.Qr qr = TezgahWebPhone.Qr.parse(request.getString("text"), System.currentTimeMillis() / 1000);
                        phone = new TezgahWebPhone(qr, new TezgahWebRepository(db, "A"), output::add, on -> { });
                        commands = new TezgahWebCommands(db, "A", qr.room, request.optBoolean("full", true), () -> true,
                                new TezgahWebCommands.Host() {
                                    public void approval(String id, String message) { approval.add(id); }
                                    public void trace(String message) { traces.add(message); }
                                    public void backup(String id) { }
                                }, System::currentTimeMillis, clock::get, () -> 0);
                        phone.commands(commands);
                        phone.opened();
                        break;
                    case "approve":
                        phone.approve();
                        break;
                    case "receive":
                        phone.receive(request.getString("frame"), System.nanoTime() / 1000000);
                        break;
                    case "decide":
                        phone.decide(request.getString("id"), request.getBoolean("approve"));
                        break;
                    case "change":
                        phone.changed(clock.get());
                        break;
                    case "lost":
                        phone.disconnected(System.nanoTime() / 1000000);
                        break;
                    case "downgrade":
                        phone.downgrade();
                        break;
                    case "expire":
                        clock.addAndGet(60001);
                        phone.tick();
                        break;
                    case "close":
                        phone.close();
                        db.close();
                        return;
                    default:
                        throw new IllegalArgumentException("Test komutu bilinmiyor.");
                }
            } catch (Exception e) {
                error = e.getMessage();
            }
            JSONArray frames = new JSONArray();
            for (String frame : output) frames.put(new JSONObject(frame));
            System.out.println(TezgahWebDto.json("frames", frames, "error", error, "approvals", new JSONArray(approval),
                    "traces", new JSONArray(traces), "count", TezgahPurchases.all(db, "A", 10000).size()));
            System.out.flush();
        }
        if (phone != null) phone.close();
        db.close();
    }
}
