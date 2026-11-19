"""Asistan yürütmeyi AssistantOutcome'a sorar (İP5): her yol seçicinin cevabıyla açılır. Statik denetim."""
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parents[2]
c = (root / "app/src/main/java/com/zeytindefteri/app/ZeytinAssistantController.java").read_text(encoding="utf-8")
run = c[c.index("private void run(String raw,final ZeytinAssistantCommand cmd,final Pick pick){"):]
run = run[:run.index("    private void advisorReply(")]
kinds = ["HARVEST_QUESTIONS", "HARVEST_FORM", "RECEIVABLES", "ELEK_FORM", "ELEK_SCREEN", "RECORD_QUESTIONS",
         "OPERATION_QUESTIONS", "OPERATION_FORM", "SPECIAL", "TEACH", "CHAT", "NAVIGATE", "MARKET"]
checks = [
    ("kayıt yolları seçiciden", "final AssistantOutcome.Kind path=AssistantOutcome.records(decision,openForm);" in run),
    ("cevap yolları seçiciden", "AssistantOutcome.rest(decision,teach,cmd.kind==Kind.NAVIGATION,regionMarket)" in run),
    ("her yol seçicinin türüyle açılır", all(("==AssistantOutcome.Kind." + k) in run for k in kinds)),
    ("internet yardımcısı kayıt yollarından sonra, cevap yollarından önce",
     run.index("Kind.OPERATION_FORM") < run.index("cloudBrain.decide(") < run.index("AssistantOutcome.rest(")),
    ("eski karar koşulları kalmadı (ikinci yol yok)",
     not re.search(r"if\(decision\.is\(ActionCatalog\.(HASAT|ALACAK_SORU|ELEK_AC|OZEL_CEVAP)\)", run)
     and "if(cmd.kind==Kind.NAVIGATION){" not in run and "if(op!=null && \"soru\"" not in run),
]
bad = 0
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
    bad += 0 if ok else 1
sys.exit(1 if bad else 0)
