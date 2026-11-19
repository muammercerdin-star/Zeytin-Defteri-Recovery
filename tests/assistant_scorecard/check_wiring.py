"""Karne ve cümle kaydı bağlantıları (İP1): statik denetim. Android derlemesi değildir."""
import pathlib
import sys

root = pathlib.Path(__file__).resolve().parents[2]
app = root / "app/src/main/java/com/zeytindefteri/app"
c = (app / "ZeytinAssistantController.java").read_text(encoding="utf-8")
meter = (app / "AssistantMeter.java").read_text(encoding="utf-8")
forms = {
    "Elek": (app / "ElekActivity.java").read_text(encoding="utf-8"),
    "İşlem": (app / "NewOperationActivity.java").read_text(encoding="utf-8"),
    "Hasat": (root / "app/src/main/java/HarvestActivity.java").read_text(encoding="utf-8"),
}
checks = [
    ("karne komutu anlamadıklarımdan sonra, sohbetten önce",
     "handleScorecardCommand(raw))return;" in c
     and c.index("handleMissesCommand(raw))return;") < c.index("handleScorecardCommand(raw))return;") < c.index("persona().handle(raw)")),
    ("karne yeni ekran açmaz (asistan cevabına yazılır)",
     "AssistantMeter.show(activity,host.owner())" in c and "startActivity" not in c.split("private boolean handleScorecardCommand")[1].split("\n    }")[0]),
    ("anlaşılmayan cümle karneye de yazılır", "meter(meterTurn,raw,owner,null);" in c),
    ("anlamadıklarını sil kayıttaki anlaşılmayanları da siler", "AssistantMeter.clearMissed(activity,host.owner());" in c),
    ("elek, işlem, hasat, alacak dalları ölçülür",
     all(f'meter(token,raw,owner,"{a}")' in c for a in ["ELEK", "ISLEM", "HASAT", "ALACAK_SORU", "ELEK_AC", "OZEL_CEVAP"])),
    ("soru ve ekran açma ölçülür", 'meter(token,raw,owner,cmd.kind==Kind.NAVIGATION?"EKRAN_AC":"SORU_"+cmd.kind.name());' in c),
    ("aynı cümle yeniden sorulunca ikinci kez sayılmaz", "if(again)meteredTurn=token;" in c),
    ("sohbet anlaşılan sayılır", 'AssistantMeter.understood(activity,host.owner(),raw,"SOHBET")' in c),
    ("konuşma kaydını sil hepsini siler", "AssistantMeter.clearAll(activity,host.owner());" in c
     and "AssistantScorecard.asksClearAll(raw)" in c),
    ("ölçü yalnız telefonda (ağ yok)", all(w not in meter for w in ["http", "Firebase", "firestore", "supabase", "URL"])),
]
res = root / "app/src/main/res/xml"
backup = (res / "push_backup_rules.xml").read_text(encoding="utf-8")
extract = (res / "push_data_extraction_rules.xml").read_text(encoding="utf-8")
for f in ["zd_assistant_misses.xml", "zd_assistant_scorecard.xml"]:
    checks.append((f + " Android yedeğine girmez", f'path="{f}"' in backup and extract.count(f'path="{f}"') == 2))
for name, src in forms.items():
    checks.append((name + " formu kaydı karneye yazar", "AssistantMeter.saved(this" in src))
    checks.append((name + " formu açılış anını tutar", "meterOpenedAt = android.os.SystemClock.elapsedRealtime();" in src))
    checks.append((name + " formu sesli alanları tutar", "voiceFill.put(" in src))
bad = 0
for n, ok in checks:
    print(("PASS " if ok else "FAIL ") + n)
    bad += 0 if ok else 1
sys.exit(1 if bad else 0)
