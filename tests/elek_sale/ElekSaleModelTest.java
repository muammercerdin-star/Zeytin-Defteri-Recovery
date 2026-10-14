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
