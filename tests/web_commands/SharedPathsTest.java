package com.zeytindefteri.app;

import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Eski ekran algoritmalarıyla ortak yolların sonuç eşitliği ve gerçek SQLite kilidi. */
public final class SharedPathsTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static TezgahModel model() {
        TezgahModel model = new TezgahModel();
        model.setTare(2);
        model.setPrice(0, 12.35);
        model.add(0, 2, 104);
        return model;
    }
    private static void compare(List<TezgahLedger.Entry> left, List<TezgahLedger.Entry> right) {
        check(left.size() == right.size(), "ledger row count");
        for (int i = 0; i < left.size(); i++) {
            TezgahLedger.Entry a = left.get(i);
            TezgahLedger.Entry b = right.get(i);
            check(a.amount == b.amount && a.kg == b.kg && a.kind == b.kind && a.method == b.method
                    && a.detail.equals(b.detail) && a.note.equals(b.note) && a.date.equals(b.date)
                    && a.party.equals(b.party) && a.zId == b.zId && a.createdAt == b.createdAt, "same native ledger output");
        }
    }
    public static void main(String[] args) throws Exception {
        SQLiteDatabase db = new SQLiteDatabase(args[0], args[1]);
        String date = "2026-11-03";
        double[] kg = {10.1, 20.2, 0, 0, 0, 0};
        double[] prices = {12.35, 8.21, 0, 0, 0, 0};
        for (int method = 1; method <= 3; method++) {
            for (double received : new double[]{0, 10, method == 1 ? TezgahLedger.gradeAmount(kg, prices) : TezgahLedger.times(30.3, 12.35)}) {
                String old = "old" + method + received;
                String shared = "shared" + method + received;
                double amount = method == 1 ? TezgahLedger.gradeAmount(kg, prices) : TezgahLedger.times(30.3, 12.35);
                double weight = method == 1 ? TezgahLedger.sum(kg) : 30.3;
                String detail = method == 1 ? TezgahLedger.gradeDetail(kg, prices) : TezgahModel.kg(weight) + " × " + TezgahModel.money(12.35) + (method == 3 ? " komisyon" : "");
                TezgahLedger.add(db, old, "Ali", TezgahLedger.SALE, amount, weight, method, detail, "not", date, 0, 10);
                if (received >= 0.01) TezgahLedger.add(db, old, "Ali", TezgahLedger.RECEIVED, received, 0, 0, "satışta alındı", "", date, 0, 11);
                check(TezgahLedger.recordSale(db, shared, "Ali", kg, prices, 30.3, 12.35, method, received, "not", date, 10) > 0, "shared sale saved");
                compare(TezgahLedger.entries(db, old), TezgahLedger.entries(db, shared));
            }
        }
        for (double owes : new double[]{150, -150}) {
            String old = "zOld" + owes;
            String shared = "zShared" + owes;
            String detail = "Z 1 · teslim 100 kg · avans 10 ₺ düşüldü";
            TezgahLedger.add(db, old, "Tüccar", TezgahLedger.SALE, owes, 100, TezgahLedger.METHOD_DAY, detail, "", date, 1, 100);
            TezgahLedger.add(db, old, "Tüccar", owes < 0 ? TezgahLedger.PAID : TezgahLedger.RECEIVED, 20, 0, 0, "Z 1", "", date, 0, 101);
            check(TezgahLedger.recordDay(db, shared, "Tüccar", owes, 100, 20, detail, date, 1, 100) > 0, "shared Z saved");
            compare(TezgahLedger.entries(db, old), TezgahLedger.entries(db, shared));
            check(TezgahLedger.recordDay(db, shared, "Tüccar", owes, 100, 20, detail, date, 1, 100) < 0, "duplicate Z rejected");
        }
        for (String owner : new String[]{"oldBulk", "sharedBulk"}) {
            for (int i = 0; i < 3; i++) TezgahPurchases.add(db, owner, date, "Ali", "", model(), i == 0 ? 1234.8 : 100, i);
        }
        TezgahStatement oldStatement = TezgahStatement.of(TezgahPurchases.all(db, "oldBulk", 100), "Ali");
        int sequence = 0;
        for (long[] payment : oldStatement.allocate(1500)) TezgahPayments.pay(db, "oldBulk", payment[0], payment[1] / 100.0, date, 100 + sequence++);
        check(TezgahPayments.payBulk(db, "sharedBulk", "Ali", 1500, date, 100) == sequence, "same bulk count");
        List<TezgahPurchases.Purchase> oldRows = TezgahPurchases.all(db, "oldBulk", 100);
        List<TezgahPurchases.Purchase> sharedRows = TezgahPurchases.all(db, "sharedBulk", 100);
        for (int i = 0; i < oldRows.size(); i++) check(oldRows.get(i).paid == sharedRows.get(i).paid, "same bulk allocation including dust balance");
        db.execSQL("CREATE TRIGGER fail_second BEFORE INSERT ON tezgah_ledger WHEN NEW.kind=2 BEGIN SELECT RAISE(ABORT,'second insert'); END");
        try {
            TezgahLedger.recordSale(db, "rollback", "Ali", kg, prices, 10, 10, 2, 20, "", date, 1);
        } catch (RuntimeException expected) { }
        check(TezgahLedger.entries(db, "rollback").isEmpty(), "sale plus receipt rollback together");
        db.execSQL("DROP TRIGGER fail_second");
        long purchase = TezgahPurchases.add(db, "serial", date, "Ali", "", model(), 0, 1);
        AtomicLong clock = new AtomicLong(10000);
        TezgahWebCommands.Host host = new TezgahWebCommands.Host() {
            public void approval(String id, String message) { }
            public void trace(String message) { }
            public void backup(String id) { }
        };
        TezgahWebCommands engine = new TezgahWebCommands(db, "serial", "session", true, () -> true, host, System::currentTimeMillis, clock::get, () -> 0);
        JSONObject command = CommandsTest.command("odeme", CommandsTest.j("alimId", Long.toString(purchase), "tutar", "10"));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<JSONObject> first = executor.submit(() -> engine.submit(command));
        Future<JSONObject> duplicate = executor.submit(() -> engine.submit(command));
        check(first.get(5, TimeUnit.SECONDS).getString("durum").equals("tamam"), "parallel first");
        check(duplicate.get(5, TimeUnit.SECONDS).getString("durum").equals("tamam"), "parallel same result");
        check(TezgahPurchases.get(db, "serial", purchase).paid == 10, "parallel single payment");
        SQLiteDatabase screen = new SQLiteDatabase(args[0], args[1]);
        screen.beginTransaction();
        check(TezgahPayments.pay(screen, "serial", purchase, 5, date, 2), "native payment inside lock");
        Future<JSONObject> web = executor.submit(() -> engine.submit(CommandsTest.command("odeme", CommandsTest.j("alimId", Long.toString(purchase), "tutar", "7"))));
        Thread.sleep(150);
        check(!web.isDone(), "web waits for actual SQLite native transaction");
        screen.setTransactionSuccessful();
        screen.endTransaction();
        check(web.get(5, TimeUnit.SECONDS).getString("durum").equals("tamam"), "web continues after screen commit");
        check(TezgahPurchases.get(db, "serial", purchase).paid == 22, "neither concurrent payment lost");
        screen.close();
        executor.shutdownNow();
        engine.close();
        db.close();
        System.out.println("PASS " + checks + " shared native paths / real SQLite serialization checks");
    }
}
