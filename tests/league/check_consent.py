#!/usr/bin/env python3
"""Piyasaya anonim katkı izni + lig izleyicisi için kaynak düzeyi kontroller (host-only)."""
import os, re, sys
R = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
def read(p): return open(os.path.join(R, p), encoding="utf-8").read()
m = read("app/src/main/java/com/zeytindefteri/app/MarketCloudRepository.java")
lr = read("app/src/main/java/com/zeytindefteri/app/ZeytinLeagueRepository.java")
sv = read("app/src/main/java/com/zeytindefteri/app/SettingsView.java")
lv = read("app/src/main/java/com/zeytindefteri/app/ZeytinLeagueView.java")
pr = read("app/src/main/java/com/zeytindefteri/app/MarketContributionPrompt.java")
priv = read("app/src/main/res/raw/legal_privacy.txt"); kvkk = read("app/src/main/res/raw/legal_kvkk.txt")
ld = read("app/src/main/java/com/zeytindefteri/app/LegalDocuments.java")
sync = m[m.index("private void syncContributionsIfNeeded("):m.index("private void withdrawAllTracked(")]
checks = {
    "contribution requires explicit opt-in": re.search(r"localContributionEnabled\(\) \{\s*return canUseCloud\(\) && isContributionOptedIn\(\);", m) is not None,
    "opt-in defaults to off": 'getSetting(CONTRIBUTION_OPT_IN_PREFIX + uid, "0")' in m,
    "opt-in is per user": "CONTRIBUTION_OPT_IN_PREFIX + uid" in m,
    "sync withdraws (does not upload) when not opted in": re.search(r"if \(!isContributionOptedIn\(\)\) \{.*?withdrawAllTracked\(", sync, re.S) is not None
        and sync.index("isContributionOptedIn()") < sync.index("loadLocalContributions(year)"),
    "turning off withdraws tracked contributions": re.search(r"setContributionOptIn\(boolean enabled.*?withdrawAllTracked\(", m, re.S) is not None,
    "withdrawAllTracked has callers": m.count("withdrawAllTracked(") >= 3,
    "re-enable resends (sync epoch in hash key)": "market_supabase_rpc_sync_epoch_" in m and "_e\" + epoch" in m,
    "settings row present": "Piyasaya Anonim Katkı" in sv and "MarketContributionPrompt.show(" in sv,
    "league shows opt-in hint when off": "!leagueCloudRepository.isContributionOptedIn()" in lv,
    "prompt states 5-producer floor": "PUBLIC_MARKET_MIN_PRODUCERS" in pr,
    "watcher ignores cache snapshots": re.search(r"isFromCache\(\)\) return;", lr) is not None
        and "isFromCache()) { changed.accept(null)" not in lr,
    "privacy text: opt-in, default off, elek source": "Piyasaya Anonim Katkı" in priv and "varsayılan olarak kapalıdır" in priv and "elek kayıtlarındaki" in priv,
    "privacy text: no stale 'satış kayıtlarından' claim for stats": "Piyasa paylaşımını açarsan satış kayıtlarından" not in priv,
    "kvkk text updated": "Piyasaya Anonim Katkı" in kvkk and "varsayılan kapalı" in kvkk,
    "legal version bumped": 'VERSION = "1.1"' in ld,
}
for k, ok in checks.items(): print(("PASS " if ok else "FAIL ") + k)
sys.exit(0 if all(checks.values()) else 1)
