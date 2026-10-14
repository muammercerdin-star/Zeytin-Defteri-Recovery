package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.List;

/** OperationHistoryModel için ana makine testi. */
public final class OperationHistoryModelTest {
    private static int checks = 0;

    static void eq(Object expected, Object actual, String what) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual))
            throw new AssertionError(what + ": beklenen <" + expected + "> gelen <" + actual + ">");
    }

    static OperationHistoryModel.Row row(long id, String type, String date, int order) {
        return new OperationHistoryModel.Row(id, type, "Girelli", date, 0, "", 0, "", order);
    }

    public static void main(String[] a) {
        List<String> f = OperationHistoryModel.filters();
        eq("Tümü", f.get(0), "ilk filtre");
        eq("Diğer", f.get(f.size() - 1), "son filtre Diğer");
        eq(true, f.contains("Hasat") && f.contains("Sinek tuzağı"), "hasat ve tuzak filtrede");
        eq(12, f.size(), "12 filtre");

        eq(true, OperationHistoryModel.matches("Tümü", "Budama"), "Tümü her şeye uyar");
        eq(true, OperationHistoryModel.matches("Sinek tuzağı", "Sinek Tuzağı"), "eski büyük harfli yazım (eski filtre hatası)");
        eq(true, OperationHistoryModel.matches("Gübre", "Gübreleme"), "eski Gübreleme kaydı");
        eq(true, OperationHistoryModel.matches("İlaçlama", "ilaçlama"), "Türkçe küçük harf");
        eq(false, OperationHistoryModel.matches("Sulama", "Budama"), "farklı tür");

        List<OperationHistoryModel.Row> rows = new ArrayList<>();
        rows.add(row(1, "Sulama", "01.09.2026", 0));
        rows.add(row(2, "Gübre", "15.10.2026", 1));
        rows.add(row(3, "Budama", "bozuk", 2));
        rows.add(row(4, "Hasat", "2026-10-15", 3));
        rows.add(row(5, "Mazot", "15.10.2026", 4));
        OperationHistoryModel.sortNewestFirst(rows);
        StringBuilder ids = new StringBuilder();
        for (OperationHistoryModel.Row r : rows) ids.append(r.id);
        eq("24513", ids.toString(), "tarihe göre, aynı günde kayıt sırası, okunamayan en sonda");

        eq("EKİM 2026", OperationHistoryModel.monthLabel("15.10.2026"), "ay başlığı");
        eq("EKİM 2026", OperationHistoryModel.monthLabel("2026-10-15"), "eski ISO tarih");
        eq("ŞUBAT 2026", OperationHistoryModel.monthLabel("01.02.2026"), "Türkçe ay");
        eq("TARİHSİZ", OperationHistoryModel.monthLabel(""), "tarihsiz");

        OperationHistoryModel.Row r = new OperationHistoryModel.Row(9, "Sulama", "Girelli", "14.10.2026", 2.5, "ton", 1250, "", 0);
        eq("Girelli · 14.10.2026 · 2,5 ton", OperationHistoryModel.meta(r, true), "tüm bahçelerde bahçe adı");
        eq("14.10.2026 · 2,5 ton", OperationHistoryModel.meta(r, false), "tek bahçede bahçe adı yok");
        eq("1.250 TL", OperationHistoryModel.cost(1250), "masraf");
        eq("", OperationHistoryModel.cost(0), "masrafsız satır");
        eq("3 işlem · 1.250 TL", OperationHistoryModel.groupSummary(3, 1250), "ay özeti");
        eq("1 işlem", OperationHistoryModel.groupSummary(1, 0), "masrafsız ay");
        System.out.println("OperationHistoryModelTest: " + checks + " kontrol geçti");
    }
}
