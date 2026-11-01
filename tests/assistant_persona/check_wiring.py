"""Asistan kişiliği bağlantıları: selamlama, sohbet, adıyla teşekkür, tanışma yalnız bir kez, yalnız telefonda saklama."""
import os, sys
A = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "java", "com", "zeytindefteri", "app")
c = open(os.path.join(A, "ZeytinAssistantController.java"), encoding="utf-8").read()
checks = [
    ("panel acilinca adiyla selamlar", "persona().greeting(AssistantPersona.hourNow())" in c),
    ("sohbet cumleleri kisilige gider (kayit/soru oncesi)", "persona().handle(raw)" in c and c.index("persona().handle(raw)") < c.index("run(raw,ZeytinAssistantCommand.parse(raw),null);\n    }")),
    ("kayit sonrasi adiyla tesekkur", "persona().thanks()" in c),
    ("tanisma bir kez, ikinci cevaptan sonra", "persona().introduceOnce()" in c),
    ("bilgiler yalniz telefonda (SharedPreferences)", "getSharedPreferences(\"zd_assistant_persona\"" in c),
]
bad = 0
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name); bad += 0 if ok else 1
print("assistant_persona wiring: " + ("PASS" if bad == 0 else "FAIL"))
sys.exit(1 if bad else 0)
