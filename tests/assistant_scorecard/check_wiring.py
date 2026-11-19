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
rb = (app / "RuleBrain.java").read_text(encoding="utf-8")   # İP3: karar sırası beyinde
sc = c.split("private void scorecardCommand(Decision d){")[1].split("\n    }")[0]
checks = [
    ("karne komutu anlamadıklarımdan sonra, sohbetten önce",
     "scorecardCommand(d);return;" in c
     and rb.index("AssistantMisses.asksClear(in.raw)") < rb.index("AssistantScorecard.asksShow(in.raw)") < rb.index("c.persona.handle(in.raw)")),
    ("karne yeni ekran açmaz (asistan cevabına yazılır)",
     "AssistantMeter.show(activity,host.owner())" in c and "startActivity" not in sc),
    ("anlaşılmayan cümle karneye de yazılır", "meter(meterTurn,raw,owner,null);" in c),
    ("anlamadıklarını sil kayıttaki anlaşılmayanları da siler", "AssistantMeter.clearMissed(activity,host.owner());" in c),
    ("elek, işlem, hasat, alacak, elek aç, hazır cevap, soru ve ekran dalları kararın adıyla ölçülür",
     c.count("meter(token,raw,owner,decision.action)") >= 9),
    ("aynı cümle yeniden sorulunca ikinci kez sayılmaz", "if(again)meteredTurn=token;" in c),
    ("sohbet anlaşılan sayılır", "AssistantMeter.understood(activity,host.owner(),raw,d.action);" in c and "if(d.is(ActionCatalog.SOHBET)){" in c),
    ("konuşma kaydını sil hepsini siler", "AssistantMeter.clearAll(activity,host.owner());" in c
     and "AssistantScorecard.asksClearAll(in.raw)" in rb),
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
