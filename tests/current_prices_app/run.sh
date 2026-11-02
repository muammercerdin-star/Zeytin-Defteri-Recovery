#!/bin/bash
set -e
DIR=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$DIR/../.." && pwd); A="$ROOT/app/src/main/java/com/zeytindefteri/app"
JSON_JAR=${JSON_JAR:-/usr/share/java/json.jar}
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
javac -encoding UTF-8 -nowarn -cp "$JSON_JAR" -d "$T" "$A/CurrentPrices.java" "$DIR/CurrentPricesTest.java" 2>&1 | grep -v "^Note:" || true
java -Dfile.encoding=UTF-8 -cp "$T:$JSON_JAR" com.zeytindefteri.app.CurrentPricesTest
python3 - "$A" "$ROOT" <<'P'
import sys, os
A, ROOT = sys.argv[1], sys.argv[2]
r = lambda f: open(os.path.join(A, f), encoding="utf-8").read()
e, m, lc, rep = r("ElekActivity.java"), r("MarketView.java"), r("ZeytinLeagueContainerView.java"), r("MarketCloudRepository.java")
lay = open(os.path.join(ROOT, "app/src/main/res/layout/activity_elek.xml"), encoding="utf-8").read()
checks = [
    ("RPC adı SQL ile aynı", "\"market_region_current_prices\"" in rep and "market_region_current_prices(" in open(os.path.join(ROOT, "supabase/ZEYTIN-LIGI-GUNCEL-FIYAT-V1.sql"), encoding="utf-8").read()),
    ("bahçe seçilince güncel fiyat yüklenir", "refreshElekInsights();\n        refreshMarketPrices();" in e),
    ("fiyat ayarları değişmez (bölge fiyatı yazılmaz)", "putString(KEY_PRICE_DOUBLE" not in e.split("GÜNCEL FİYAT")[1]),
    ("QR fişi önce gelir", "!qrPricesActive" in e.split("private void applyMarketOverlay")[1][:300]),
    ("elle girilen fiyat önce gelir", "marketPricesActive = false;\n        renderPriceSource();" in e),
    ("kayıtlı fiyat yüklenince bindirme", "updatePriceLabels();\n        applyMarketOverlay();" in e),
    ("sesli elek formdaki fiyatı kullanır", "prices() { return new double[]{priceDouble, price1" in e),
    ("formda kaynak notu", "tvElekPriceSource" in lay),
    ("Piyasa kartında güncel satır", "setCurrentPrice(" in m and "loadCurrentPrice(year, cloud.regionId" in lc),
]
bad = 0
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name); bad += 0 if ok else 1
sys.exit(1 if bad else 0)
P
