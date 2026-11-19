"""Öğrenilen sözler bağlantıları (İP4): statik denetim. Android derlemesi değildir."""
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parents[2]
app = root / "app/src/main/java/com/zeytindefteri/app"
c = (app / "ZeytinAssistantController.java").read_text(encoding="utf-8")
db = (app / "LearnedTermsDb.java").read_text(encoding="utf-8")
lt = (app / "LearnedTerms.java").read_text(encoding="utf-8")
lb = (app / "LearnedBrain.java").read_text(encoding="utf-8")
acc = (app / "AccountDeletion.java").read_text(encoding="utf-8")
backup = (root / "app/src/main/res/xml/push_backup_rules.xml").read_text(encoding="utf-8")
extract = (root / "app/src/main/res/xml/push_data_extraction_rules.xml").read_text(encoding="utf-8")
manual = (app / "TezgahBackup.java").read_text(encoding="utf-8")
learn = c[c.index("private void learnFromCloud("):]
learn = learn[:learn.index("\n    }\n")]
checks = [
    ("asistan kararı öğrenilen sözler beyninden (kuralların önünde)",
     "learned().conversationStage(" in c and "brain.recordStage(new Brain.Input(raw),records,cmd)" in c
     and "rules.conversationStage(" not in c and "rules.recordStage(" not in c),
    ("öğretme sorusu: anlaşılmadığında, hazır cevaptan sonra, ipucundan önce",
     c.index("if(special!=null){post(") < c.index("LearnedTerms.teachable(raw,brain.store())") < c.index("ZeytinAssistantHints.hint(raw")),
    ("öğretme sorusu tek soru, okunur, büyük düğmeler", 'LearnedTerms.capital(word)+" ne işi?"' in c and "answer.setTextSize(24);" in c
     and "dp(64)" in c and 'labels.add("Vazgeç");' in c and 'labels.add("Başka");' in c),
    ("öğretilen işte form dolu açılır (soru sorulmadan)", "teachOpenForm=true;" in c and '"soru".equals(decision.variant) && !openForm' in c),
    ("vazgeç: aynı cümle bir daha sorulmaz", "learned().store().decline(LearnedTerms.sentenceKey(p[0]));" in c),
    ("sonraki kullanımda geri bildirim + Unut düğmesi", "LearnedTerms.told(taught)" in c and "addForgetButton(taught);" in c),
    ("bulut-onaylı öğrenme yalnız 'evet'le kaydedilince", "if(id>0&&opCloudRaw!=null)learnFromCloud(opCloudRaw,d.entry);" in c
     and "LearnedTerms.SOURCE_CLOUD" in learn),
    ("sözlük komutları", "ActionCatalog.SOZLER_GOSTER" in c and "ActionCatalog.SOZ_UNUT" in c),
    ("sözler ayrı dosyada (zd_learned_terms.db), ana veritabanında değil", 'FILE = "zd_learned_terms.db"' in db),
    ("Google otomatik yedeğinden ve cihaz aktarımından hariç",
     'path="zd_learned_terms.db"' in backup and extract.count('path="zd_learned_terms.db"') == 2),
    ("Ayarlar → Yedekle dosyasına girmez (tablo listesinde yok)", "learned_terms" not in manual),
    ("hesap silinince sözler de silinir", "LearnedTermsDb.clear(activity, uid);" in acc),
    ("sözlük internete gitmez", all(not re.search(w, t) for t in (db, lt, lb) for w in ["http", "Firebase", "firestore", "supabase", "URL\\("])),
]
bad = 0
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
    bad += 0 if ok else 1
sys.exit(1 if bad else 0)
