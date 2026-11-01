"""Sesle bahçe işi: asistan → işlem formu dolu açılır, kendiliğinden kaydetmez."""
import os, sys
A = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "java", "com", "zeytindefteri", "app")
r = lambda f: open(os.path.join(A, f), encoding="utf-8").read()
c, f, n = r("ZeytinAssistantController.java"), r("ProducerFeedActivity.java"), r("NewOperationActivity.java")
checks = [
    ("asistan islem cumlesini taniyip formu acar", "OperationVoiceEntry.parse(raw)" in c and "host.openOperationEntry(gid,op)" in c),
    ("elek cumlesi once (islem onu ezmez)", c.index("ElekVoiceEntry.parse(raw)") < c.index("OperationVoiceEntry.parse(raw)")),
    ("ana ekran formu tur + tarih + miktar + tutar ile acar", "NewOperationActivity.EXTRA_VOICE_DAY_OFFSET" in f and "NewOperationActivity.EXTRA_VOICE_COST" in f),
    ("form sesle dolar ama kendiliginden kaydetmez", "applyVoice()" in n and n.count("saveOperation()") == 2),
    ("ornek cumleler asistanda", "SESLE BAHÇE İŞİ" in r("ZeytinAssistantText.java")),
]
bad = 0
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name); bad += 0 if ok else 1
print("operation_voice wiring: " + ("PASS" if bad == 0 else "FAIL"))
sys.exit(1 if bad else 0)
