package com.zeytindefteri.app;

import java.util.*;

/** Host-only checks for creating a sale from an Elek record. */
public final class ElekSaleModelTest {
    private static int checks;
    private static void eq(Object a, Object e, String l) { if (!Objects.equals(a, e)) throw new AssertionError(l + ": " + a + " != " + e); checks++; }
    public static void main(String[] args) {
        eq(ElekSaleModel.received(ElekSaleModel.PAY_ALL, 4910, 0), 4910.0, "all");
        eq(ElekSaleModel.received(ElekSaleModel.PAY_NONE, 4910, 999), 0.0, "none ignores partial");
        eq(ElekSaleModel.received(ElekSaleModel.PAY_PART, 4910, 2000), 2000.0, "part");
        eq(ElekSaleModel.received(ElekSaleModel.PAY_PART, 4910, 9000), 4910.0, "part clamped to gross");
        eq(ElekSaleModel.received(ElekSaleModel.PAY_PART, 4910, -5), 0.0, "part floor");
        eq(ElekSaleModel.partialError(ElekSaleModel.PAY_PART, 4910, 0), "Alınan tutarı gir.", "part needs amount");
        eq(ElekSaleModel.partialError(ElekSaleModel.PAY_PART, 4910, 4910).startsWith("Tutar toplamdan küçük"), true, "part equal to gross");
        eq(ElekSaleModel.partialError(ElekSaleModel.PAY_PART, 4910, 1000), "", "part ok");
        eq(ElekSaleModel.partialError(ElekSaleModel.PAY_ALL, 4910, 0), "", "all needs nothing");
        eq(ElekSaleModel.saleDate("2026-10-13"), "13.10.2026", "iso to sale format");
        eq(ElekSaleModel.saleDate("13.10.2026"), "13.10.2026", "legacy passthrough");
        eq(ElekSaleModel.saleDate(null), "", "null date");
        eq(ElekSaleModel.region("Edremit", "Balıkesir"), "Edremit", "district first");
        eq(ElekSaleModel.region(" ", "Balıkesir"), "Balıkesir", "province fallback");
        eq(ElekSaleModel.paymentStatus(5700, 5700), "Tamamı alındı", "status full");
        eq(ElekSaleModel.paymentStatus(5700, 0), "Henüz alınmadı", "status none");
        eq(ElekSaleModel.paymentStatus(5700, 2000).endsWith("TL alındı"), true, "status part");
        eq(ElekSaleModel.remaining(5700, 2000), 3700.0, "remaining part");
        eq(ElekSaleModel.remaining(5700, 5700), 0.0, "remaining none");
        String shareText = ElekSaleModel.receiptSaleText("Özer Ağırbaş", "Girelli Mahallesi", "", 5700, 5700);
        eq(shareText.contains("Alıcı: Özer Ağırbaş · Girelli Mahallesi"), true, "share buyer");
        eq(shareText.contains("Tahsilat: Tamamı alındı"), true, "share paid");
        eq(shareText.contains("Kalan"), false, "share no remaining when paid");
        eq(shareText.contains("Telefon"), false, "share no empty phone");
        String partText = ElekSaleModel.receiptSaleText("", "", "0555", 5700, 2000);
        eq(partText.contains("Alıcı: Belirtilmedi"), true, "share no buyer");
        eq(partText.contains("Telefon: 0555"), true, "share phone");
        eq(partText.contains("Kalan: "), true, "share remaining");
        eq(ElekSaleModel.receiptSaleText("A", "", "", 5700, 0).contains("Kalan"), false, "unpaid shows status only");
        eq(ElekSaleModel.saveLabel(true), "Kaydet ve satışı oluştur", "label on");
        eq(ElekSaleModel.saveLabel(false), "Elek Kaydını Kaydet", "label off keeps old text");
        eq(ElekSaleModel.saveHint(true, 4910, "Girelli Zeytinliği", "13 Ekim 2026"),
                "Elek kaydı + 4.910 TL satış · Girelli Zeytinliği · 13 Ekim 2026", "hint on");
        eq(ElekSaleModel.saveHint(false, 4910, "Girelli", "13 Ekim").startsWith("Yalnızca elek kaydı"), true, "hint off");
        eq(ElekSaleModel.successToast(true, true), "Elek kaydı ve satış kaydedildi ✓", "toast both");
        eq(ElekSaleModel.successToast(false, true).contains("Satışa dönüştür"), true, "toast sale failed points to receipt");
        eq(ElekSaleModel.successToast(false, false), "Elek kaydı kaydedildi ✓", "toast elek only");
        eq(ElekSaleModel.convertedLine(4910, 0), "4.910 TL · tahsilat: henüz değil", "converted none");
        eq(ElekSaleModel.convertedLine(4910, 4910), "4.910 TL · tahsilat: tamamı alındı", "converted all");
        eq(ElekSaleModel.convertedLine(4910, 2000), "4.910 TL · tahsilat: 2.000 TL alındı", "converted part");
        eq(ElekSaleModel.bulkButton(0), "Elek seç", "bulk none");
        eq(ElekSaleModel.bulkButton(3), "3 eleği satışa dönüştür", "bulk count");
        System.out.println("PASS: " + checks + " elek→sale checks (host only).");
    }
}
