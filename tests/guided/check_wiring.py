"""Yönlendiren asistan: bağlantıların statik denetimi. Android derlemesi değildir; telefonda denenmelidir."""
import pathlib
import sys

root = pathlib.Path(__file__).resolve().parents[2]
app = root / "app/src/main/java/com/zeytindefteri/app"


def read(name):
    return (app / name).read_text(encoding="utf-8")


op = read("OperationVoiceDialog.java")
rec = read("VoiceRecordDialog.java")
checks = [
    ("alan soruları tek yerde: sesli işlem diyaloğu katalogdan okur",
     'ActionCatalog.question(ActionCatalog.ISLEM, "bahce", t)' in op and '"Hangi bahçeyi suladın?"' not in op),
    ("sesli kayıt diyaloğu katalogdan okur", 'q(ActionCatalog.HASAT, "bahce")' in rec and '"Kaç kilo topladın?"' not in rec),
]
bad = 0
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
    bad += 0 if ok else 1
sys.exit(1 if bad else 0)
