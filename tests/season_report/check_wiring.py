#!/usr/bin/env python3
"""Sezon Raporum: kaynak düzeyi bağlantı kontrolleri (host-only, cihaz testi değildir)."""
import os, re, sys
R = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
a = open(os.path.join(R, "app/src/main/java/com/zeytindefteri/app/ProducerFeedActivity.java"), encoding="utf-8").read()
v = open(os.path.join(R, "app/src/main/java/com/zeytindefteri/app/SeasonReportView.java"), encoding="utf-8").read()
checks = {
    "legacy report code removed from activity": all(x not in a for x in ("buildLegacyReportsSection", "reportBlock(", "buildGardenPerformanceBlock")),
    "league 'Kişisel raporlar' opens the new view": "this::openSeasonReport" in a and "new SeasonReportView(this, loadSeasonReportData()" in a,
    "title is not 'Zeytin Ligi'": '"Sezon Raporum"' in v and '"Zeytin Ligi"' not in v,
    "back arrow returns to league": 'public void back() { showSection("REPORTS"); }' in a,
    "system back returns to league instead of leaving the app": re.search(r"public void onBackPressed\(\) \{\s*//[^\n]*\n\s*if \(seasonReportOpen && \"REPORTS\"\.equals\(currentSection\)\)", a) is not None,
    "report stays open after returning from SaleActivity": 'seasonReportOpen && "REPORTS".equals(currentSection)) {\n            openSeasonReport();' in a,
    "navigating elsewhere clears the report flag": "currentSection = section;\n        seasonReportOpen = false;" in a,
    "existing season/owner-scoped totals reused": all(f"db.{m}()" in a for m in ("getTotalExpenses", "getTotalHarvestKg", "getTotalSalesRevenue", "getTotalSoldKg", "getRealSaleCount", "getTotalAmountReceived", "getTotalOpenReceivableBalance", "getTotalBrineOliveKg", "getTotalReadyBrineKg", "getTotalBrineCost", "getActiveBrineBatchCount")),
    "elek totals from the shared archive query": "new ElekRepository(db).getArchiveRecords(d.season, -1, uid)" in a,
    "cursors closed": "elek.close()" in a and "if (c != null) c.close();" in a,
    "detailed report and history kept": "ReportActivity.class" in a and "OperationHistoryActivity.class" in a,
    "missing-link action converts elek records in bulk": "public void convertElek() { showElekToSaleDialog(); }" in a and "actions.convertElek()" in v,
    "no emoji headings": not re.search(r"[\U0001F300-\U0001FAFF]", v),
}
for k, ok in checks.items(): print(("PASS " if ok else "FAIL ") + k)
sys.exit(0 if all(checks.values()) else 1)
