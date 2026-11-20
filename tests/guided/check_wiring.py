"""Yönlendiren asistan: bağlantıların statik denetimi. Android derlemesi değildir; telefonda denenmelidir."""
import pathlib
import sys

root = pathlib.Path(__file__).resolve().parents[2]
app = root / "app/src/main/java/com/zeytindefteri/app"


def read(name):
    return (app / name).read_text(encoding="utf-8")


op = read("OperationVoiceDialog.java")
rec = read("VoiceRecordDialog.java")
res = root / "app/src/main/res/xml"
backup = (res / "push_backup_rules.xml").read_text(encoding="utf-8")
extract = (res / "push_data_extraction_rules.xml").read_text(encoding="utf-8")
deletion = read("AccountDeletion.java")
guide_db = read("GuideDb.java")
learned = read("LearnedActivity.java")
backup_files = list(app.glob("*Backup*.java")) + list((root / "app/src/main/java").glob("*Backup*.java"))
yedekle = "".join(f.read_text(encoding="utf-8") for f in backup_files)
checks = [
    ("kart ve kısa yol sayıları ayrı dosyada (zd_guide.db)", 'FILE = "zd_guide.db"' in guide_db),
    ("Google yedeğine ve telefon aktarımına girmez",
     'path="zd_guide.db"' in backup and extract.count('path="zd_guide.db"') == 2),
    ("Yedekle dosyasına girmez", "zd_guide" not in yedekle and "GuideDb" not in yedekle),
    ("hesap silinince silinir", "GuideDb.clear(activity, uid);" in deletion),
    ("kapanan kart türü Öğrendiklerim'den açılır", "GuideDb.save(this, owner, type, GuideCards.reopened());" in learned),
    ("alan soruları tek yerde: sesli işlem diyaloğu katalogdan okur",
     'ActionCatalog.question(ActionCatalog.ISLEM, "bahce", t)' in op and '"Hangi bahçeyi suladın?"' not in op),
    ("sesli kayıt diyaloğu katalogdan okur", 'q(ActionCatalog.HASAT, "bahce")' in rec and '"Kaç kilo topladın?"' not in rec),
]
bad = 0
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
    bad += 0 if ok else 1
sys.exit(1 if bad else 0)
