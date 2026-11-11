"""Ad sorusu bağlantısı: eşit iki düğme, varsayılan kapalı, ad onaysız kaydedilmez, bir kez sorulur."""
from pathlib import Path
src = Path('app/src/main/java/com/zeytindefteri/app')
p = (src / 'LeagueNamePrompt.java').read_text()
v = (src / 'ZeytinLeagueView.java').read_text()
r = (src / 'MarketCloudRepository.java').read_text()
ask = p[p.index('static void ask('):p.index('private static void apply(')]
checks = {
    'iki düğme aynı stil': ask.count('choice(activity, LeagueNameAsk.') == 2,
    'düğmeler eşit genişlik': ask.count('new LinearLayout.LayoutParams(0, Math.round(48 * density), 1f)') == 2,
    'evet ad onay penceresini açar (doğrudan kaydetmez)': 'show(activity, onChanged)' in ask and 'setLeagueProfile' not in ask and 'apply(' not in ask,
    'gösterilince soruldu sayılır': 'markLeagueAsk(LeagueNameAsk.FIRST_KEY)' in ask,
    'AlertDialog varsayılan düğmesi yok': 'setPositiveButton' not in ask,
    'lig ekranı soruyu çağırır': 'maybeAskName(region);' in v and 'LeagueNameAsk.decide(demo' in v,
    'ad varsayılan kapalı kalır': '"league_name_show_"' in r and 'getSetting(LEAGUE_NAME_SHOW_PREFIX + uid, "0")' in r,
}
bad = [k for k, ok in checks.items() if not ok]
for k, ok in checks.items(): print(('OK  ' if ok else 'FAIL') + ' ' + k)
raise SystemExit(1 if bad else 0)
