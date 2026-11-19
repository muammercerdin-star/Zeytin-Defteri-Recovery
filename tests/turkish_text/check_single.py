"""İP2: projede tek Türkçe sadeleştirici ve tek sayı okuyucu (TurkishText) kalsın; statik denetim."""
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parents[2]
src = root / "app/src/main/java"
# Bilerek ayrı: yazıcı kod sayfası (büyük harfi korur, ESC/POS'a çevirir) ve küfür süzgeci ("ı" bilerek ayrı tutulur).
allowed = {"TurkishText.java", "TezgahPrintFormat.java", "ProfanityFilter.java"}
fold_marks = [
    re.compile(r"replace\('ı',\s*'i'\)"),
    re.compile(r'replace\("ı",\s*"i"\)'),
    re.compile(r"case 'ı'"),
    re.compile(r"Normalizer\.Form\.NFD"),
]
number_words = re.compile(r'"(sifir|dokuz|doksan|yetmis)"')
bad = 0
checks = []
for f in sorted(src.rglob("*.java")):
    text = f.read_text(encoding="utf-8")
    if f.name in allowed:
        continue
    for m in fold_marks:
        if m.search(text):
            checks.append((False, f"ikinci sadeleştirici: {f.name} ({m.pattern})"))
    if number_words.search(text):
        checks.append((False, f"ikinci yazıyla sayı okuyucu: {f.name}"))
    if "static double number(String[] t" in text:
        checks.append((False, f"ikinci sayı okuyucu: {f.name}"))
tt = (src / "com/zeytindefteri/app/TurkishText.java").read_text(encoding="utf-8")
checks.append(("public static String normalize(String raw)" in tt, "TurkishText.normalize var"))
checks.append(("public static double number(String[] t, int i, int[] end)" in tt, "TurkishText.number var"))
checks.append(("public static String unit(String w)" in tt, "TurkishText.unit var"))
app = src / "com/zeytindefteri/app"
# Eski kopyalar kaldırıldı (NotebookOcr.normalize OCR harf→rakam düzeltmesidir, OliveVariety.normalize cins adı eşler: başka iş).
for cls, names in [("ElekVoiceEntry", ["static String normalize(", "static double number("]),
                   ("ZeytinAssistantCommand", ["static String normalize("]),
                   ("TezgahVoice", ["static String norm("]),
                   ("OperationVoiceEntry", ["static String unit("]),
                   ("AssistantCloud", ["static List<Double> numbers("])]:
    text = (app / (cls + ".java")).read_text(encoding="utf-8")
    checks.append((not any(n in text for n in names), cls + ": eski kopya kaldırıldı"))
for cls in ["ElekVoiceEntry", "OperationVoiceEntry", "HarvestVoiceEntry", "ZeytinAssistantCommand", "AssistantCloud", "TezgahVoice", "TezgahVoiceDialog", "AssistantPersona"]:
    checks.append(("TurkishText." in (app / (cls + ".java")).read_text(encoding="utf-8"), cls + " TurkishText kullanır"))
if not any(not ok for ok, _ in checks):
    checks.append((True, "Türkçe harf sadeleştirme ve yazıyla sayı yalnız TurkishText'te"))
for ok, name in checks:
    print(("PASS " if ok else "FAIL ") + name)
    bad += 0 if ok else 1
sys.exit(1 if bad else 0)
