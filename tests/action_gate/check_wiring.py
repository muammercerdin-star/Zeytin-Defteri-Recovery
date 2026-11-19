"""Onay kapısı bağlantıları (İP3): statik denetim. Android derlemesi değildir."""
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parents[2]
app = root / "app/src/main/java/com/zeytindefteri/app"
c = (app / "ZeytinAssistantController.java").read_text(encoding="utf-8")
f = (app / "ProducerFeedActivity.java").read_text(encoding="utf-8")
d = (app / "OperationVoiceDialog.java").read_text(encoding="utf-8")
impl = f[f.index("public long saveOperation("):]
impl = impl[:impl.index("db.addOperation(")]
checks = [
    ("asistanın kayıt yazma yolu tek (host.saveOperation)", c.count("host.saveOperation(") == 1),
    ("kayıt çağrısı çiftçinin onay belgesiyle", "Approval approval=Approval.operationConfirmed(d);" in c and "host.saveOperation(approval," in c),
    ("Host arayüzü onay belgesi ister", "default long saveOperation(Approval approval," in c),
    ("ana ekran onay belgesiz kaydetmez (kapı DB'den önce)", "ActionGate.mayWrite(ActionCatalog.ISLEM, approval)" in impl and "return -1;" in impl),
    ("onay yalnız özetten sonraki 'evet'te", d.count("confirmed = true;") == 1
     and re.search(r'has\(s, "evet\|kaydet[^"]*"\)\) \{\s*step = Step.DONE;\s*confirmed = true;', d) is not None),
]
bad = 0
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
    bad += 0 if ok else 1
sys.exit(1 if bad else 0)
