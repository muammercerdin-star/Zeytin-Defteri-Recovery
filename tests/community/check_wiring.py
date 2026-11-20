"""Ortak döngü (İP8-A): bağlantıların statik denetimi. Android derlemesi değildir.
En önemli kural: telefondan TEK BAYT çıkmaz (adres yok, gönderimi tetikleyen yer yok, yurt dışı adresi yok)."""
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parents[2]
app = root / "app/src/main/java/com/zeytindefteri/app"


def read(p):
    return p.read_text(encoding="utf-8")


def java(name):
    return read(app / name)


gradle = "".join(read(p) for p in (root / "app").glob("build.gradle*"))
sync = java("CommunitySync.java")
sender = java("CommunitySender.java")
db = java("CommunityDb.java")
ctrl = java("ZeytinAssistantController.java")
learned = java("LearnedActivity.java")
settings = java("SettingsView.java")
acc = java("AccountDeletion.java")
tezgah = java("TezgahBackup.java")
backup = read(root / "app/src/main/res/xml/push_backup_rules.xml")
extract = read(root / "app/src/main/res/xml/push_data_extraction_rules.xml")
all_java = {p.name: read(p) for p in app.glob("*.java")}
community_files = [n for n in all_java if n.startswith("Community") or n.startswith("Contribution") or n.startswith("KnowledgePack")
                   or n.startswith("Pack")]
flush_callers = [n for n, t in all_java.items() if "CommunitySync.flush(" in t and n != "CommunitySync.java"]
foreign = re.compile(r"supabase\.co|firebaseio|googleapis|amazonaws|cloudflare|workers\.dev|vercel|herokuapp|azure", re.I)

checks = [
    ("adres yok: derleme ayarında ZEYTIN_COMMUNITY_URL tanımlı değil", "ZEYTIN_COMMUNITY_URL" not in gradle),
    ("adres boşken ağ açılmaz (configured() deseni, flush başta döner)",
     "if (!CommunitySender.configured(url)) {\n            return 0;\n        }" in sync
     and "if (!configured(url) || transport == null) {\n            return 0;" in sender),
    ("İP8-A'da gönderimi tetikleyen hiçbir yer yok", flush_callers == []),
    ("ortak döngü dosyalarında hiçbir adres yazılı değil (yurt dışı dahil)",
     all(not re.search(r"https?://[a-z0-9]", all_java[n]) for n in community_files)
     and all(not foreign.search(all_java[n]) for n in community_files)),
    ("izin varsayılan KAPALI", "enabled INTEGER NOT NULL DEFAULT 0" in db and "return c.moveToFirst() && c.getInt(0) == 1;" in db),
    ("izin Ayarlar'da görünmez (gizli bayrak)", "static final boolean SETTINGS_VISIBLE = false;" in sync
     and "CommunitySync" not in settings and "CommunityDb" not in settings),
    ("öğretince (izin açıksa) kuyruğa; unutunca düşer / geri çekilir",
     "CommunitySync.taught(activity,learnedOwner,taught);" in ctrl and "CommunitySync.taught(activity,learnedOwner,cloudTaught);" in ctrl
     and "CommunitySync.forgot(activity,learnedOwner,t.term);" in ctrl and learned.count("CommunitySync.forgot(this, owner,") == 2),
    ("kuyruk Google yedeğinden ve telefon aktarımından hariç",
     'path="zd_community.db"' in backup and extract.count('path="zd_community.db"') == 2),
    ("kuyruk Yedekle dosyasına girmez", "queue" not in tezgah and "zd_community" not in tezgah),
    ("hesap silinince silinir", "CommunityDb.clear(activity, uid);" in acc),
    ("gizli anahtar telefondan çıkmaz (gönderilen JSON'da yok)",
     "secret" not in sender and "CommunityDb.secret(context)" in sync),
    ("temizleyici dışında katkı alanı yok (yalnız yedi alan + takma ad + silme özeti)",
     'field(b, "takma_ad", pseudonym);' in sender and 'field(b, "silme_ozeti", CommunityQueue.digest(i.token));' in sender),
    ("paket: kontrolcü öğrenilenleri paketle birlikte okur (öğretilen önce)",
     "new LearnedBrain(PackDb.store(activity,owner),rules);" in ctrl and "PackDb.warm(activity);" in ctrl),
    ("paket: indirme yok, açık anahtar yuvaları boş",
     'static final String[] PUBLIC_KEYS = {"", ""};' in java("KnowledgePack.java")
     and "URL" not in java("PackDb.java") and "HttpsURLConnection" not in java("PackDb.java")),
    ("paket: Herkesten öğrendiklerim bölümü kapat/aç düğmesiyle",
     "Herkesten öğrendiklerim" in java("LearnedScreen.java") and "fromAllSection()" in learned
     and "PackDb.setDisabled(" in learned),
    ("paket: kapatılan sözler Google yedeğinden, aktarımdan ve Yedekle dosyasından hariç",
     'path="zd_pack.db"' in backup and extract.count('path="zd_pack.db"') == 2 and "zd_pack" not in tezgah
     and "PackDb" not in tezgah),
    ("paket: hesap silinince kapatılan sözler silinir", "PackDb.clear(activity, uid);" in acc),
    ("paket: gömülü paket res/raw'da, doğrulama arka planda", "R.raw.bilgi_paketi" in java("PackDb.java")
     and "new Thread(" in java("PackDb.java")),
]
bad = 0
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
    bad += 0 if ok else 1
sys.exit(1 if bad else 0)
