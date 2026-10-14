#!/usr/bin/env python3
"""Ana Sayfa Zeytin Ligi kartı: kaynak düzeyi bağlantı kontrolleri (host-only, cihaz testi değildir)."""
import os, re, sys
R = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
a = open(os.path.join(R, "app/src/main/java/com/zeytindefteri/app/ProducerFeedActivity.java"), encoding="utf-8").read()
v = open(os.path.join(R, "app/src/main/java/com/zeytindefteri/app/ZeytinLeagueView.java"), encoding="utf-8").read()
inp = a[a.index("private HomeLeagueModel.Input homeLeagueInput("):a.index("private void renderHomeLeagueCard(")]
checks = {
    "card added right after the hero": re.search(r"body\.addView\(hero\);\s*View leagueCard = buildHomeLeagueCard\(\);", a) is not None,
    "card states come from HomeLeagueModel.build": "HomeLeagueModel.build(in)" in a,
    "tap opens Zeytin Ligi tab": 'card.setOnClickListener(v -> showSection("REPORTS"));' in a,
    "long press hides per user": "homeLeagueHiddenKey(uid), \"1\"" in a,
    "hidden card can be restored from league screen": '"home_league_card_hidden_" + uid' in v and 'settingsDb.setSetting(homeKey, "0")' in v,
    "only own rank/score and top scores read (no other producers' kg/amount)": ".kg" not in inp.replace("me.kg", "") and ".amount" not in inp,
    "published result must be fresh": "ZeytinLeagueRepository.fresh(result.publishedAtMs" in inp,
    "network result cached (10 min) to avoid Firestore reads on every Home open": "HOME_LEAGUE_CACHE_MS" in a,
    "stale async results ignored": "serial != homeLeagueSerial" in a,
    "invite shares plain text without fabricated link": "HomeLeagueModel.inviteText(in.regionName)" in a,
    "region tiles equalised (no fixed 150dp min height)": "details.setMinimumHeight(dp(150))" not in v and "new LinearLayout.LayoutParams(tileWidth, -1)" in v,
}
for k, ok in checks.items(): print(("PASS " if ok else "FAIL ") + k)
sys.exit(0 if all(checks.values()) else 1)
