"""Sesle hasat ve alacak: asistan bağlantıları."""
import os, sys
A = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "java")
r = lambda f: open(os.path.join(A, f), encoding="utf-8").read()
c = r("com/zeytindefteri/app/ZeytinAssistantController.java"); f = r("com/zeytindefteri/app/ProducerFeedActivity.java")
h = r("HarvestActivity.java"); d = r("com/zeytindefteri/app/ZeytinAssistantData.java")
checks = [
    ("hasat cumlesi hasat formunu acar (elekten once)", "HarvestVoiceEntry.parse(raw)" in c and c.index("HarvestVoiceEntry.parse(raw)") < c.index("ElekVoiceEntry.parse(raw)")),
    ("alacak sorusu kayitlardan cevaplanir", "AssistantReceivables.asks(raw)" in c and "data.receivables()" in c and "BuyerDirectory.list" in d),
    ("ana ekran hasat formunu boy boy kilo ile acar", "HarvestActivity.EXTRA_VOICE_KG" in f),
    ("hasat formu sesle dolar ama kendiliginden kaydetmez", "applyVoice()" in h and h.count("saveHarvest()") == 2),
]
bad = 0
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name); bad += 0 if ok else 1
print("assistant_more wiring: " + ("PASS" if bad == 0 else "FAIL"))
sys.exit(1 if bad else 0)
