#!/usr/bin/env python3
"""ElekSaleBridge: v20 şeması + çift dönüşüm sorgusu (host SQLite) ve kaynak kontrolleri.

Köprünün kullandığı bağlantı sorgusunu kaynak koddan aynen çıkarır ve gerçek SQLite'ta
çalıştırır. Android SQLite sürümü farklı olabilir; cihaz testi değildir.
"""
import os, re, sqlite3, sys
R = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
J = os.path.join(R, "app/src/main/java")
b = open(os.path.join(J, "com/zeytindefteri/app/ElekSaleBridge.java"), encoding="utf-8").read()
h = open(os.path.join(J, "DatabaseHelper.java"), encoding="utf-8").read()
sch = open(os.path.join(J, "DatabaseSchema.java"), encoding="utf-8").read()
pc = open(os.path.join(J, "com/zeytindefteri/app/ProductionCloudRepository.java"), encoding="utf-8").read()
ea = open(os.path.join(J, "com/zeytindefteri/app/ElekActivity.java"), encoding="utf-8").read()
fails = []
def check(name, ok):
    print(("PASS " if ok else "FAIL ") + name)
    if not ok: fails.append(name)

check("DB version is 20", re.search(r"DB_VERSION =\s*20;", h) is not None)
check("migration adds both link columns idempotently", 'addColumnIfMissing(db, "sales", "source_elek_id", "INTEGER DEFAULT 0")' in h
      and 'addColumnIfMissing(db, "sales", "source_elek_cloud_id", "TEXT DEFAULT \'\'")' in h and h.count("ensureElekSaleLinkSchema(db);") == 2)
check("fresh install schema has link columns", '"source_elek_id INTEGER DEFAULT 0,"' in sch and '"source_elek_cloud_id TEXT DEFAULT \'\',"' in sch)
check("cloud push carries source_elek_cloud_id", 'm.put("source_elek_cloud_id", saleSourceElekCloudId(c));' in pc)
check("cloud restore reads source_elek_cloud_id", '"source_elek_cloud_id"\n        };' in pc)
create = b[b.index("public long createSale("):b.index("public List<Pending> pending(")]
check("duplicate check before any insert", create.index("linkedSaleId(db, elekId) > 0") < create.index("addSaleWithGrades"))
check("market sharing starts OFF", re.search(r'"Elek kaydından oluşturuldu",\s*false,', create) is not None)
check("link written in the same transaction", create.index("db.beginTransaction()") < create.index('link.put("source_elek_id"') < create.index("db.setTransactionSuccessful()"))
check("owner-scoped elek lookup", "COALESCE(g.owner_uid,'')=?" in create)
check("form toggle defaults ON every time", "private boolean saleOnSave = true;" in ea and "saleOnSave" in ea and "getSharedPreferences(\"elek_sale" not in ea)

# Real SQLite run of the link query, reconstructed from the Java string literals.
m = re.search(r'Cursor c = db\.rawQuery\("(SELECT id FROM sales WHERE[^"]*)"\s*\+\s*\(cloud\.isEmpty\(\) \? "" : "([^"]*)"\) \+ "([^"]*)"', b)
check("link query found in source", m is not None)
if m:
    q_local = m.group(1) + m.group(3)
    q_cloud = m.group(1) + m.group(2) + m.group(3)
    db = sqlite3.connect(":memory:")
    db.execute("CREATE TABLE sales (id INTEGER PRIMARY KEY, owner_uid TEXT DEFAULT '')")
    db.execute("ALTER TABLE sales ADD COLUMN source_elek_id INTEGER DEFAULT 0")
    db.execute("ALTER TABLE sales ADD COLUMN source_elek_cloud_id TEXT DEFAULT ''")
    db.execute("INSERT INTO sales (owner_uid, source_elek_id, source_elek_cloud_id) VALUES ('u1', 7, 'E7')")   # same device
    db.execute("INSERT INTO sales (owner_uid, source_elek_id, source_elek_cloud_id) VALUES ('u1', 0, 'E9')")   # restored from cloud
    db.execute("INSERT INTO sales (owner_uid, source_elek_id, source_elek_cloud_id) VALUES ('u2', 8, 'E8')")   # other account
    db.execute("INSERT INTO sales (owner_uid) VALUES ('u1')")                                                    # manual sale
    one = lambda q, a: db.execute(q, a).fetchone()
    check("same-device link found", one(q_cloud, ("u1", "7", "E7")) is not None)
    check("cloud-restored link found by cloud id", one(q_cloud, ("u1", "99", "E9")) is not None)
    check("other account's link ignored", one(q_cloud, ("u1", "8", "E8")) is None)
    check("unconverted elek not matched by manual sales", one(q_local, ("u1", "5")) is None)
    check("manual sale with default 0 does not match elek id 0 guard", "if (elekId <= 0) return -1;" in b)
sys.exit(1 if fails else 0)
