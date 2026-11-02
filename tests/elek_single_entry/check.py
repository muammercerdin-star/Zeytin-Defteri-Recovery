# Tek kayıt: elek kaydı hasat (ve satış) sayılır; üretici ayrıca hasat girmek zorunda değil.
import os, sys
R = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "java")
r = lambda f: open(os.path.join(R, f), encoding="utf-8").read()
feed, harvest, sale, grade = r("com/zeytindefteri/app/ProducerFeedActivity.java"), r("HarvestActivity.java"), r("SaleActivity.java"), r("com/zeytindefteri/app/GradeFormModel.java")
checks = [
    ("ana ekran: elek kaydı son hasat sayılır", "db.getGardenElekRecords(garden.id)" in feed and '"Hasat",\n                        getString(elek, "record_date", "")' in feed),
    ("ana ekran: hasat yazısı elek formunu açar", 'new Intent(this, ElekActivity.class).putExtra("garden_id",harvestGarden)' in feed),
    ("hasat formu: elek kayıtları otomatik listelenir", "databaseHelper.getGardenElekRecords(gardenId)" in harvest and "Elek kaydından · otomatik" in harvest),
    ("hasat formu: elek kayıtları burada silinmez", harvest.count("confirmDelete(id, date, kg, cost)") == 1),
    ("satış formu: elekten satış oluştuğu söylenir, anonim veri izni korunur", "Elek kaydında satış kendiliğinden oluşur" in sale and "anonim piyasa verisine" in sale),
    ("tarih biçimi", "public static String displayDate" in grade),
]
bad = 0
for n, ok in checks:
    print(("PASS " if ok else "FAIL ") + n); bad += 0 if ok else 1
sys.exit(1 if bad else 0)
