#!/usr/bin/env python3
"""Ana Sayfa alt bölümü: kaynak düzeyi kontroller (host-only, cihaz testi değildir)."""
import os, re, sys
R = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
J = os.path.join(R, "app/src/main/java/com/zeytindefteri/app")
a = open(os.path.join(J, "ProducerFeedActivity.java"), encoding="utf-8").read()
v = open(os.path.join(J, "HomeLowerSections.java"), encoding="utf-8").read()
op = open(os.path.join(J, "NewOperationActivity.java"), encoding="utf-8").read()
m = open(os.path.join(J, "HomeSummaryModel.java"), encoding="utf-8").read()
types = re.search(r'OPERATION_TYPES = \{([^}]*)\}', m).group(1)
op_list = re.search(r'operationTypes =\s*Arrays\.asList\(([^)]*)\)', op).group(1)
norm = lambda s: [x.strip().strip('"') for x in s.split(',') if x.strip()]
checks = {
    "operation picker uses NewOperationActivity's exact type names": norm(types) == norm(op_list),
    "old 'Piyasa Özeti' title gone": "Piyasa Özeti" not in a,
    "old emoji quick buttons and helpers removed": all(x not in a for x in ("quickButton(", "homeMiniMetric(", "loadHomeMarketSnapshots", "HomeMarketSnapshot", "\"🧺\"")),
    "own prices labelled as own, not market": "SENİN ELEK FİYATLARIN" in v and "bölge piyasası değildir" in v,
    "quick actions: İşlem ekle, Hasat, Elek, Satış": all(f'"{x}"' in v for x in ("İşlem ekle", "Hasat", "Elek", "Satış")),
    "Bu Sezon opens Sezon Raporum": 'public void seasonReport() { showSection("REPORTS"); openSeasonReport(); }' in a and "seasonCard.setOnClickListener(v -> actions.seasonReport())" in v,
    "Elek quick action opens the form with the active garden": "new Intent(ProducerFeedActivity.this, ElekActivity.class)" in a,
    "Sale quick action passes garden as long": 'i.putExtra("garden_id", (long) garden.id)' in a,
    "own prices come from the scoped own-elek query": "marketRepository.getScopedOwnSalesByGrade(" in a,
    "no emoji in the new section": not re.search(r"[\U0001F300-\U0001FAFF]", v),
}
for k, ok in checks.items(): print(("PASS " if ok else "FAIL ") + k)
sys.exit(0 if all(checks.values()) else 1)
