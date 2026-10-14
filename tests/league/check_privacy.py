#!/usr/bin/env python3
"""Zeytin Ligi gizlilik eşiği: istemci 5 üretici altını asla göstermez.

MarketCloudRepository içinden veri sınıflarını ve enforceReportPrivacy metodunu
kelimesi kelimesine çıkarıp küçük bir Java koşucusunda çalıştırır (host-only,
Android SDK gerekmez). Ayrıca bölge dizini ayrıştırıcısının eşiği uyguladığını
kaynak üzerinden doğrular.
"""
import os, re, subprocess, sys, tempfile
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "app/src/main/java/com/zeytindefteri/app/MarketCloudRepository.java")
CFG = os.path.join(ROOT, "app/src/main/java/com/zeytindefteri/app/MarketBackendConfig.java")
s = open(SRC, encoding="utf-8").read()
fails = []

def block(start_pat):
    i = s.index(start_pat)
    j = s.index("{", i); depth = 0
    for k in range(j, len(s)):
        if s[k] == "{": depth += 1
        elif s[k] == "}":
            depth -= 1
            if depth == 0: return s[i:k + 1]
    raise SystemExit("unbalanced " + start_pat)

classes = "\n".join(block(f"public static final class {n}") for n in ("ChartPoint", "LeagueGradeReport", "LeagueRegionReport"))
method = block("static void enforceReportPrivacy(")

# Source-level checks
if not re.search(r'stat\.published = item\.optBoolean\("published", false\)\s*&& stat\.producerCount >= MarketBackendConfig\.PUBLIC_MARKET_MIN_PRODUCERS', s):
    fails.append("league region directory does not enforce the client threshold")
if s.count("enforceReportPrivacy(out);") != 2:
    fails.append("both region report parsers must call enforceReportPrivacy")
if "fiyat ön izlemesi" in open(os.path.join(ROOT, "app/src/main/java/com/zeytindefteri/app/ZeytinLeagueRegionReportDialog.java"), encoding="utf-8").read():
    fails.append("dialog still promises single-producer price previews")

java = r'''
package com.zeytindefteri.app;
import java.util.*;
public final class PrivacyHarness {
%s
%s
    static int checks;
    static void ok(boolean c, String l) { if (!c) throw new AssertionError(l); checks++; }
    static LeagueRegionReport report(int producers, boolean published) {
        LeagueRegionReport r = new LeagueRegionReport();
        r.producerCount = producers; r.available = published; r.saleCount = 9;
        r.totalKg = 456; r.averagePrice = 56.89; r.medianPrice = 55; r.minPrice = 40; r.maxPrice = 70;
        r.dominantGrade = "2 No"; r.trendPct = 3;
        LeagueGradeReport one = new LeagueGradeReport(); one.grade = "Double"; one.producerCount = 1;
        one.kg = 20; one.kgPublished = true; one.averagePrice = 120; one.medianPrice = 120;
        one.trendAvailable = true; one.trendCurrentProducers = 1; one.trendPreviousProducers = 1; one.trendPct = 4;
        LeagueGradeReport many = new LeagueGradeReport(); many.grade = "2 No"; many.producerCount = 6;
        many.kg = 300; many.kgPublished = true; many.averagePrice = 60; many.medianPrice = 59;
        many.trendAvailable = true; many.trendCurrentProducers = 6; many.trendPreviousProducers = 2; many.trendPct = 5;
        r.grades.add(one); r.grades.add(many);
        ChartPoint a = new ChartPoint(); a.day = "2026-10-01"; a.price = 50; a.producerCount = 1;
        ChartPoint b = new ChartPoint(); b.day = "2026-10-02"; b.price = 52; b.producerCount = 5;
        r.chart.add(a); r.chart.add(b);
        return r;
    }
    public static void main(String[] args) {
        ok(MarketBackendConfig.PUBLIC_MARKET_MIN_PRODUCERS == 5, "production floor is 5");
        // Ekran görüntüsündeki durum: sunucu 2 üreticiyle published=true gönderiyor.
        LeagueRegionReport two = report(2, true); enforceReportPrivacy(two);
        ok(!two.available, "2 producers never available");
        ok(two.totalKg == 0 && two.averagePrice == 0 && two.medianPrice == 0, "2 producers totals cleared");
        ok(two.minPrice == 0 && two.maxPrice == 0 && two.saleCount == 0, "2 producers range cleared");
        ok(two.dominantGrade.isEmpty() && two.trendPct == 0, "2 producers dominant/trend cleared");
        ok(two.chart.isEmpty(), "2 producers chart cleared");
        for (LeagueGradeReport g : two.grades) ok(g.kg == 0 && !g.kgPublished && g.averagePrice == 0 && !g.trendAvailable, "grade cleared " + g.grade);
        ok(two.producerCount == 2, "producer count stays visible");

        LeagueRegionReport four = report(4, true); enforceReportPrivacy(four);
        ok(!four.available, "4 producers still below floor");

        LeagueRegionReport six = report(6, true); enforceReportPrivacy(six);
        ok(six.available, "6 producers available");
        ok(six.totalKg == 456 && six.averagePrice == 56.89, "6 producers totals kept");
        LeagueGradeReport dbl = six.grades.get(0), second = six.grades.get(1);
        ok(dbl.averagePrice == 0 && dbl.kg == 0 && !dbl.kgPublished, "single-producer grade cleared inside open region");
        ok(!dbl.trendAvailable, "single-producer grade trend cleared");
        ok(second.averagePrice == 60 && second.kg == 300 && second.kgPublished, "6-producer grade kept");
        ok(!second.trendAvailable && second.trendPct == 0, "trend with 2-producer previous window cleared");
        ok(six.chart.size() == 1 && six.chart.get(0).producerCount == 5, "only chart days with 5+ producers kept");

        LeagueRegionReport unpublished = report(9, false); enforceReportPrivacy(unpublished);
        ok(!unpublished.available && unpublished.totalKg == 0, "server unpublished stays hidden");
        enforceReportPrivacy(null);
        System.out.println("PASS: " + checks + " league privacy checks (host only).");
    }
}
''' % (classes, method)

with tempfile.TemporaryDirectory() as d:
    pkg = os.path.join(d, "com/zeytindefteri/app"); os.makedirs(pkg)
    open(os.path.join(pkg, "PrivacyHarness.java"), "w", encoding="utf-8").write(java)
    import shutil; shutil.copy(CFG, pkg)
    env = dict(os.environ); env["JAVA_TOOL_OPTIONS"] = ""
    c = subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-encoding", "UTF-8", "-d", d,
                        os.path.join(pkg, "PrivacyHarness.java"), os.path.join(pkg, "MarketBackendConfig.java")],
                       capture_output=True, text=True, env=env)
    if c.returncode: print(c.stdout, c.stderr); sys.exit(1)
    r = subprocess.run(["java", "-cp", d, "com.zeytindefteri.app.PrivacyHarness"], capture_output=True, text=True, env=env)
    print(r.stdout.strip(), r.stderr.strip())
    if r.returncode: fails.append("harness failed")

if fails:
    print("FAIL:", *fails, sep="\n - "); sys.exit(1)
print("PASS: source checks (directory threshold, both report parsers, dialog text).")
