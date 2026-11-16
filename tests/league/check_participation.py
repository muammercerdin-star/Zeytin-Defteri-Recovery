#!/usr/bin/env python3
"""Zeytin Ligi katılım izni (açık rıza) ile piyasa katkısının ayrıştırılması — kaynak düzeyi kontroller."""
import os, re, sys
R = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
def read(p): return open(os.path.join(R, p), encoding="utf-8").read()
A = "app/src/main/java/com/zeytindefteri/app/"
sql = read("supabase/ZEYTIN-LIGI-KATILIM-V4.sql")
v3 = read("supabase/ZEYTIN-LIGI-ORT-FIYAT-V3.sql")
m = read(A + "MarketCloudRepository.java")
jp = read(A + "LeagueJoinPrompt.java")
sv = read(A + "SettingsView.java")
lv = read(A + "ZeytinLeagueView.java")
acc = read(A + "AccountDeletion.java")
cp = read(A + "MarketContributionPrompt.java")
kvkk = read("app/src/main/res/raw/legal_kvkk.txt")
priv = read("app/src/main/res/raw/legal_privacy.txt")
terms = read("app/src/main/res/raw/legal_terms.txt")
def body(text):
    found = re.search(r"create or replace function public\.league_region_ranking_v3.*?\n\$\$;", text, re.S)
    return found.group(0) if found else ""
def strip(text):
    lines = [l for l in text.splitlines() if not l.strip().startswith("--")]
    return "\n".join(lines).replace("        join private.league_participants lp on lp.producer_hash = s.ph\n", "")
checks = {
    "sql: katılımcı tablosu ve RLS": "create table if not exists private.league_participants" in sql
        and "alter table private.league_participants enable row level security" in sql,
    "sql: sıralama yalnız katılımcılar": "join private.league_participants lp on lp.producer_hash = s.ph" in body(sql),
    "sql: V3 hesabının geri kalanı aynı": strip(body(sql)) == strip(body(v3)),
    "sql: gizlilik eşiği korunur (5 üretici)": "where (select n from slice_size) >= 5" in body(sql),
    "sql: ayrılınca ad da silinir": re.search(r"delete from private\.league_participants.*?delete from private\.league_profiles", sql, re.S) is not None,
    "sql: adı açan katılımcı olur (tetikleyici)": "after insert or update of show_name on private.league_profiles" in sql,
    "sql: süzgeçsiz eski sıralamalar kapatılır": "revoke execute on function public.league_region_ranking(integer, text)" in sql
        and "revoke execute on function public.league_region_ranking_v2(integer, text, text, integer)" in sql,
    "uygulama: katılım varsayılan kapalı": 'getSetting(LEAGUE_JOIN_PREFIX + uid, "0")' in m,
    "uygulama: RPC çağrıları": '"league_set_participation"' in m and '"league_get_participation"' in m,
    "uygulama: Ayarlar satırı": "leagueJoinRow(activity)" in sv and "Zeytin Ligi'nde Yer Al" in sv,
    "uygulama: lig ekranında katıl kısayolu": "joinSettingLink()" in lv and "LeagueJoinPrompt.show(" in lv,
    "rıza metni: isteğe bağlı ve geri alınabilir": "isteğe bağlıdır" in jp and "kapatabilirsin" in jp,
    "hesap silme: sıralamadan çıkış": "setLeagueParticipation(false" in acc,
    "piyasa penceresi: 'geri döndürülemez' iddiası yok": "geri döndürülemez" not in cp,
    "kvkk: ayrı izin ve m.5/1": "Zeytin Ligi'nde Yer Al" in kvkk and "KVKK m. 5/1" in kvkk,
    "kvkk: piyasa katkısı meşru menfaat": "m. 5/2-f" in kvkk and "Bu ayar açık rıza değildir" in kvkk,
    "metinler: takma ad açıklaması": "takma adlandırılmış kişisel veri" in kvkk and "takma adlandırılmış kişisel veri" in priv,
    "metinler: 'geri döndürülemez' iddiası yok": "geri döndürülemez" not in kvkk + priv,
    "koşullar: Tezgâh veri işleme maddesi": "veri işleyen" in terms and "veri sorumlusu sensin" in terms,
}
for k, ok in checks.items(): print(("PASS " if ok else "FAIL ") + k)
sys.exit(0 if all(checks.values()) else 1)
