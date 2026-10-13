package com.zeytindefteri.app;

import java.util.*;

/** Host-only checks for the Bahçelerim model. */
public final class GardenListModelTest {
    private static int checks;
    private static void eq(Object a, Object e, String l) { if (!Objects.equals(a, e)) throw new AssertionError(l + ": " + a + " != " + e); checks++; }
    private static void ok(boolean v, String l) { if (!v) throw new AssertionError(l); checks++; }
    private static GardenListModel.Item item(long id, String name, int trees, double kg, double cost, String op, String opDate, String harvestDate) {
        GardenListModel.Item i = new GardenListModel.Item();
        i.id = id; i.name = name; i.trees = trees; i.harvestKg = kg; i.cost = cost;
        i.lastOperation = op; i.lastOperationDate = opDate; i.lastHarvestDate = harvestDate;
        return i;
    }
    public static void main(String[] args) {
        Calendar now = Calendar.getInstance(); now.clear(); now.set(2026, Calendar.OCTOBER, 13, 22, 0);
        eq(GardenListModel.daysSince("2026-09-03", now), 40L, "40 days");
        eq(GardenListModel.daysSince("03.09.2026", now), 40L, "tr format");
        eq(GardenListModel.daysSince("2026-10-13", now), 0L, "today");
        eq(GardenListModel.daysSince("2026-10-20", now), -1L, "future ignored");
        eq(GardenListModel.daysSince("", now), -1L, "empty");
        eq(GardenListModel.daysSince("2026-09-03T08:00", now), 40L, "datetime prefix");

        GardenListModel.Item girelli = item(1, "Girelli Zeytinliği", 80, 1200, 30000, "Sulama", "2026-08-01", "2026-09-03");
        GardenListModel.Item edremit = item(2, "Edremit", 100, 0, 0, "", "", "");
        GardenListModel.Item haci = item(3, "Hacıaliler Zeytinlik", 50, 900, 0, "İlaçlama", "2026-10-10", "");
        eq(GardenListModel.lastActivity(girelli, now).label, "Hasat · 40 gün önce", "harvest newer");
        eq(GardenListModel.lastActivity(haci, now).label, "İlaçlama · 3 gün önce", "operation newer");
        eq(GardenListModel.lastActivity(edremit, now).label, "Kayıt yok", "no record");
        GardenListModel.Item typeOnly = item(4, "X", 1, 0, 0, "Sulama", "bozuk", "");
        eq(GardenListModel.lastActivity(typeOnly, now).label, "Sulama", "type without date");
        ok(GardenListModel.isStale(girelli, now), "40 days stale");
        ok(GardenListModel.isStale(edremit, now), "no record stale");
        ok(!GardenListModel.isStale(haci, now), "3 days fresh");
        List<GardenListModel.Item> all = Arrays.asList(girelli, edremit, haci);
        eq(GardenListModel.staleCount(all, now), 2, "two stale");
        eq(GardenListModel.staleText(2), "2 bahçe kayıt bekliyor", "stale text");
        eq(GardenListModel.staleText(1), "1 bahçe kayıt bekliyor", "stale text one");

        eq(GardenListModel.totalTrees(all), 230, "trees");
        eq(GardenListModel.totalHarvest(all), 2100.0, "harvest");
        eq(GardenListModel.totalCost(all), 30000.0, "cost");
        eq(GardenListModel.yieldPerTree(1200, 80), 15.0, "yield");
        ok(Double.isNaN(GardenListModel.yieldPerTree(0, 80)), "no harvest yield");
        ok(Double.isNaN(GardenListModel.yieldPerTree(10, 0)), "no trees yield");
        eq(GardenListModel.costPerKg(30000, 2100) > 14.28 && GardenListModel.costPerKg(30000, 2100) < 14.29, true, "cost per kg");
        ok(Double.isNaN(GardenListModel.costPerKg(100, 0)), "no harvest cost per kg");
        ok(Double.isNaN(GardenListModel.costPerKg(0, 100)), "no cost recorded");
        eq(GardenListModel.formatYield(15), "15 kg/ağaç", "format yield");
        eq(GardenListModel.formatYield(12.25).startsWith("12,2") || GardenListModel.formatYield(12.25).startsWith("12,3"), true, "tr decimal");
        eq(GardenListModel.formatYield(Double.NaN), "—", "format nan");
        eq(GardenListModel.formatCostPerKg(14.2857), "14,29 TL/kg", "format cost/kg");
        eq(GardenListModel.formatCostPerKg(150), "150 TL/kg", "format large");

        List<String> names = new ArrayList<>();
        for (GardenListModel.Item i : GardenListModel.sort(all, GardenListModel.Sort.ACTIVE, 1, now)) names.add(i.name);
        eq(names, Arrays.asList("Girelli Zeytinliği", "Edremit", "Hacıaliler Zeytinlik"), "active first then name");
        names.clear();
        for (GardenListModel.Item i : GardenListModel.sort(all, GardenListModel.Sort.NAME, 1, now)) names.add(i.name);
        eq(names, Arrays.asList("Edremit", "Girelli Zeytinliği", "Hacıaliler Zeytinlik"), "tr name order");
        names.clear();
        for (GardenListModel.Item i : GardenListModel.sort(all, GardenListModel.Sort.YIELD, 1, now)) names.add(i.name);
        eq(names, Arrays.asList("Hacıaliler Zeytinlik", "Girelli Zeytinliği", "Edremit"), "yield desc, missing last");
        names.clear();
        for (GardenListModel.Item i : GardenListModel.sort(all, GardenListModel.Sort.STALE, 1, now)) names.add(i.name);
        eq(names, Arrays.asList("Edremit", "Girelli Zeytinliği", "Hacıaliler Zeytinlik"), "never-recorded first, then oldest");
        eq(GardenListModel.parseSort("YIELD"), GardenListModel.Sort.YIELD, "parse sort");
        eq(GardenListModel.parseSort("bozuk"), GardenListModel.Sort.ACTIVE, "parse fallback");
        System.out.println("PASS: " + checks + " garden list checks (host only).");
    }
}
