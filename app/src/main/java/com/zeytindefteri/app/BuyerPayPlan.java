package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Alıcıdan toplu tahsilat: alınan para alıcının açık satışlarına EN ESKİDEN başlayarak dağıtılır
 * (önce gecikmiş borç kapanır). Android'e bağlı değil, test edilir.
 */
public final class BuyerPayPlan {

    private BuyerPayPlan() { }

    /** Açık satış: kimlik, satış günü (yyyy-aa-gg) ve kalan tutar. */
    public static final class Open {
        public final long saleId;
        public final String date;
        public final double remaining;

        public Open(long saleId, String date, double remaining) {
            this.saleId = saleId;
            this.date = date == null ? "" : date;
            this.remaining = remaining;
        }
    }

    /** Bir satışa düşen pay. */
    public static final class Part {
        public final long saleId;
        public final double amount;
        public final boolean closes;

        Part(long saleId, double amount, boolean closes) {
            this.saleId = saleId;
            this.amount = amount;
            this.closes = closes;
        }
    }

    public static final class Result {
        public final List<Part> parts = new ArrayList<>();
        /** Ödemeden sonra alıcının kalan borcu. */
        public double leftAfter;
        /** Toplam borçtan fazla yazıldıysa fazlası (kaydedilmez). */
        public double excess;

        public int closedCount() {
            int n = 0;
            for (Part p : parts) {
                if (p.closes) n++;
            }
            return n;
        }

        public boolean empty() {
            return parts.isEmpty();
        }
    }

    public static double total(List<Open> open) {
        double t = 0;
        for (Open o : open) {
            if (o.remaining > 0.009) t += o.remaining;
        }
        return round(t);
    }

    /** En eskiden başlayarak dağıtır; tarih aynıysa küçük kimlik (önce girilen) önce. */
    public static Result plan(List<Open> open, double amount) {
        List<Open> sorted = new ArrayList<>();
        for (Open o : open) {
            if (o != null && o.remaining > 0.009) sorted.add(o);
        }
        Collections.sort(sorted, (a, b) -> {
            int d = a.date.compareTo(b.date);
            if (d != 0) return d;
            return Long.compare(a.saleId, b.saleId);
        });
        Result r = new Result();
        double left = round(Math.max(0, amount));
        double total = total(sorted);
        r.excess = round(Math.max(0, left - total));
        for (Open o : sorted) {
            if (left <= 0.009) break;
            double pay = round(Math.min(left, o.remaining));
            boolean closes = pay >= o.remaining - 0.009;
            r.parts.add(new Part(o.saleId, pay, closes));
            left = round(left - pay);
        }
        double paid = 0;
        for (Part p : r.parts) paid += p.amount;
        r.leftAfter = round(Math.max(0, total - paid));
        return r;
    }

    /** Pencerenin altındaki açıklama. */
    public static String note(Result r, int openCount) {
        if (r == null || r.empty()) return "Alınan tutarı yaz.";
        if (r.excess > 0.009) return "Toplam borçtan " + ElekSaleModel.tl(r.excess) + " fazla yazıldı.";
        int closed = r.closedCount();
        if (r.leftAfter <= 0.009) {
            return openCount <= 1 ? "Borç kapanacak." : "Bütün borç kapanacak (" + openCount + " satış).";
        }
        String head = closed > 0 ? "En eski " + closed + " satış kapanacak. " : "En eski satışa işlenecek. ";
        return head + ElekSaleModel.tl(r.leftAfter) + " borç kalacak.";
    }

    static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
