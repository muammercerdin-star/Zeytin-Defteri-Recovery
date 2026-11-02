#!/bin/bash
set -e
DIR=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$DIR/../.." && pwd); A="$ROOT/app/src/main/java/com/zeytindefteri/app"
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
javac -encoding UTF-8 -nowarn -d "$T" "$A/HomeDashboardModel.java" "$A/GardenTodayAdvisor.java" "$DIR/SeasonTrackTest.java" 2>&1 | grep -v "^Note:" || true
java -Dfile.encoding=UTF-8 -cp "$T" com.zeytindefteri.app.SeasonTrackTest
python3 - "$A" <<'P'
import sys, os
f = open(os.path.join(sys.argv[1], "ProducerFeedActivity.java"), encoding="utf-8").read()
checks = [
    ("şerit açılışta bir kez dolar", "boolean play=!seasonTrackPlayed;" in f),
    ("buradasın halkası ekrandan çıkınca durur", "onViewDetachedFromWindow(View v){ breathe.cancel(); }" in f),
    ("hasat yazısı hasat kaydını açar", "harvest.setOnClickListener(v -> openHarvestForGarden(harvestGarden));" in f),
    ("dönem içi ilerleme gösterilir", "HomeDashboardModel.phaseProgressText(phase,progress)" in f),
]
bad = 0
for n, ok in checks:
    print(("PASS " if ok else "FAIL ") + n); bad += 0 if ok else 1
sys.exit(1 if bad else 0)
P
