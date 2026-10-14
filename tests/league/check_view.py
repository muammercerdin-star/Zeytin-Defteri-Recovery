#!/usr/bin/env python3
"""Kaynak düzeyi kontroller: Zeytin Ligi görselsiz tasarımı (host-only, cihaz testi değildir)."""
import os, re, sys
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
v = open(os.path.join(ROOT, "app/src/main/java/com/zeytindefteri/app/ZeytinLeagueView.java"), encoding="utf-8").read()
checks = {
    "no photo in league view": "home_hero_olive" not in v,
    "tiles gate numbers through LeagueBoardModel.publishable": "LeagueBoardModel.publishable(cloud.published, cloud.producerCount)" in v,
    "no single-producer preview wording": "fiyat ön izlemesi" not in v and "1 üreticili" not in v,
    "own strip rendered after header": re.search(r"header\(\);\s*ownStrip\(\);\s*filters\(\);", v) is not None,
    "loading skeleton keeps header without strip": re.search(r"renderLoading\(\) \{\s*body\.removeAllViews\(\); header\(\); filters\(\); gradeFilters\(\);", v) is not None,
    "badges computed for all regions": "LeagueBoardModel.badges(districts)" in v,
    "podium before remaining rows": re.search(r"podium\(table, region\.top\);\s*int first = Math\.min\(3", v) is not None,
    "score info toggle": "Puan nasıl hesaplanır?" in v and "scoreInfoOpen = !scoreInfoOpen" in v,
    "empty state names the region cup": "LeagueBoardModel.emptyTitle(district)" in v,
    "region tile still opens region report": "ZeytinLeagueRegionReportDialog.show(" in v,
    "own card is scroll target": "ownCardView = box;" in v,
    "season picker kept": "seasonPicker.run()" in v,
    "grade filters kept": all(g in v for g in ('"Double", "1 No", "2 No", "3 No", "4 No", "Yağlık"',)),
    "personal reports link kept": "personalReports.run()" in v,
    "top-20 region without directory data shows no threshold bar": re.search(r"\} else if \(hasTop\) \{\s*//[^\n]*\n[^\n]*\n\s*details\.addView\(label\(\"Top 20 yayında\"", v) is not None,
    "own row matched by producer id": "p.id.equals(data.mine.id)" in v,
}
bad = [k for k, ok in checks.items() if not ok]
for k, ok in checks.items(): print(("PASS " if ok else "FAIL ") + k)
sys.exit(1 if bad else 0)
