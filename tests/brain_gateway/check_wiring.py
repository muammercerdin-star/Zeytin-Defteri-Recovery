"""Tek kapı bağlantıları (İP3 Oturum 2): statik denetim. Android derlemesi değildir."""
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parents[2]
app = root / "app/src/main/java/com/zeytindefteri/app"
c = (app / "ZeytinAssistantController.java").read_text(encoding="utf-8")
d = (app / "ElekVoiceDialog.java").read_text(encoding="utf-8")
s = (app / "ElekVoiceSession.java").read_text(encoding="utf-8")
e = (app / "ElekActivity.java").read_text(encoding="utf-8")
save = e[e.index("@Override public long save(Approval approval) {"):]
save = save[:save.index("saveElekRecord();")]
checks = [
    ("ses (tanıma sonucu) ve yazı (gönder düğmesi, klavye) aynı kapıya: submit",
     "else submit(words.get(0));" in c and "send.setOnClickListener(v->submit(input.getText().toString()));" in c
     and "submit(input.getText().toString());return true;" in c),
    ("asistanda tek beyin çağrısı noktası: konuşma aşaması + kayıt aşaması",
     c.count("rules.conversationStage(") == 1 and c.count("rules.recordStage(") == 1),
    ("asistan çözücüleri doğrudan çağırmaz (karar beyinde)",
     not re.search(r"(ElekVoiceEntry|OperationVoiceEntry|HarvestVoiceEntry)\.parse\(|VoiceRecordDialog\.detect\(|AssistantReceivables\.asks\(|persona\(\)\.handle\(", c)),
    ("internet yardımcısı aynı arayüzden (CloudBrain)", "cloudBrain.decide(new Brain.Input(raw),records)" in c
     and "AssistantCloud.accept(" not in c and "CloudBrain.wants(raw,cmd,gardenNames)" in c),
    ("Elek Sesle doldur aynı kapıdan (Brain → ElekRules), doğrudan çözücü yok",
     "brain.decide(new Brain.Input(raw), BrainContext.elekScreen())" in d and "ElekVoiceEntry.parse(" not in d),
    ("Elek sesli kayıt onay belgesiyle", "host.save(Approval.elekConfirmed(logic))" in s
     and "ActionGate.mayWrite(ActionCatalog.ELEK, approval)" in save and "return -1;" in save),
    ("Elek onayı yalnız özetten sonraki 'evet'te", d.count("confirmed = true;") == 1),
]
bad = 0
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
    bad += 0 if ok else 1
sys.exit(1 if bad else 0)
