"""Sezilen alışkanlıklar (İP5): bağlantıların statik denetimi. Android derlemesi değildir."""
import pathlib
import re
import subprocess
import sys

root = pathlib.Path(__file__).resolve().parents[2]
app = root / "app/src/main/java/com/zeytindefteri/app"
java = root / "app/src/main/java"


def read(p):
    return p.read_text(encoding="utf-8")


habit_files = {n: read(app / n) for n in ["HabitModel.java", "HabitRules.java", "HabitCorrections.java", "HabitDb.java",
                                         "HabitRecords.java", "HabitForm.java"]}
op = read(app / "NewOperationActivity.java")
sale = read(java / "SaleActivity.java")
feed = read(app / "ProducerFeedActivity.java")
acc = read(app / "AccountDeletion.java")
backup = read(root / "app/src/main/res/xml/push_backup_rules.xml")
extract = read(root / "app/src/main/res/xml/push_data_extraction_rules.xml")
tezgah = read(app / "TezgahBackup.java")
backup_activity = read(app / "BackupActivity.java") if (app / "BackupActivity.java").exists() else read(java / "BackupActivity.java")
rules = habit_files["HabitRules.java"]
everything = "\n".join(habit_files.values())
base = subprocess.run(["git", "-C", str(root), "diff", "--stat", "cea13a1", "--", "app/src/main/res/layout"],
                      capture_output=True, text=True).stdout.strip()

checks = [
    ("kurallar tek yerde: 6 kayıt, 4 yüksek, 3 orta, 180 gün, 5 red",
     "WINDOW = 6;" in rules and "HIGH = 4;" in rules and "MID = 3;" in rules and "STALE_DAYS = 180;" in rules
     and "CLOSE_AFTER = 5;" in rules),
    ("düzeltmeler ayrı dosyada (zd_habits.db)", 'FILE = "zd_habits.db"' in habit_files["HabitDb.java"]),
    ("Google yedeğinden ve telefon aktarımından hariç",
     'path="zd_habits.db"' in backup and extract.count('path="zd_habits.db"') == 2),
    ("Yedekle dosyasına girmez", "rejections" not in tezgah and "zd_habits" not in tezgah and "HabitDb" not in backup_activity),
    ("hesap silinince silinir", "HabitDb.clear(activity, uid);" in acc),
    ("sunucuya gitmez (ağ yok)", not re.search(r"http|Firebase|firestore|supabase|URL\(|HttpURLConnection", everything)),
    ("gizli izleme yok (süre, dokunuş takibi yok)",
     not re.search(r"SystemClock|elapsedRealtime|onTouch|MotionEvent|setOnTouchListener", everything)),
    ("kendiliğinden kayıt yok (alışkanlık kodu kayıt yazmaz)",
     not re.search(r"addOperation|addSale|saveSale|saveOperation|insert\w*\(\"(sales|operations)", everything)),
    ("alışkanlıklar saklanmaz: her açılışta kayıtlardan (HabitRecords.load)",
     "HabitRecords.load(this)" in op and "HabitRecords.load(this)" in sale and "CREATE TABLE IF NOT EXISTS habits" not in everything),
    ("işlem formu: sesle doldurulandan sonra (söylenen/öğretilen üstün)",
     op.index("if (savedInstanceState == null) applyVoice();") < op.index("if (savedInstanceState == null) applyHabits();")),
    ("dolu alana dokunulmaz", "HabitModel.forEmpty(field.getText().toString(), habit)" in habit_files["HabitForm.java"]),
    ("bahçe yalnız ekran bahçesiz açıldıysa",
     'getIntent().getLongExtra("garden_id", -1) == -1' in op and "requestedGardenId == -1L" in sale),
    ("tür değişince öneriler yenilenir", "selectType(index, 0);\n            applyTypeHabits();" in op),
    ("kabul yalnız Kaydet'te sayılır", "habitForm.saved();" in op and "habitForm.saved();" in sale),
    ("Bu yanlış: alan boşalır, soluk 'Doğrusu ne?', ayrı pencere yok",
     'WHAT_IS_RIGHT = "Doğrusu ne?"' in habit_files["HabitForm.java"] and "field.setHint(WHAT_IS_RIGHT);" in habit_files["HabitForm.java"]
     and "AlertDialog" not in habit_files["HabitForm.java"] and "Dialog" not in habit_files["HabitForm.java"]),
    ("orta güven yalnız çip (alanı doldurmaz)", "s.row = chip(s, () -> {" in habit_files["HabitForm.java"]),
    ("+ menüsü: yalnız alt yazı, yalnız yüksek güven; yeri ve açılan ekran aynı",
     'saleLine == null ? "Fiyat teklifi, alıcı" : saleLine, SaleActivity.class' in feed
     and "if (b == null || b.level != Level.HIGH)" in habit_files["HabitModel.java"]),
    ("form düzeni (layout XML) değişmedi", base == ""),
    ("karneye yalnız sayılar (gösterildi / kabul / Bu yanlış)",
     "AssistantScorecard.SHOWN" in habit_files["HabitForm.java"] and "AssistantScorecard.ACCEPTED" in habit_files["HabitForm.java"]
     and "AssistantScorecard.WRONG" in habit_files["HabitForm.java"]),
    ("ürkütücü dil yok", not re.search(r"(?i)izledim|seni izl|hep şunu|takip ediyorum|gözlemledim", everything)),
]
bad = 0
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
    bad += 0 if ok else 1
sys.exit(1 if bad else 0)
