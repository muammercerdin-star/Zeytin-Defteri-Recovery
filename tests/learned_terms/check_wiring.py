"""Öğrenilen sözler bağlantıları (İP4): statik denetim. Android derlemesi değildir."""
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parents[2]
app = root / "app/src/main/java/com/zeytindefteri/app"
c = (app / "ZeytinAssistantController.java").read_text(encoding="utf-8")
db = (app / "LearnedTermsDb.java").read_text(encoding="utf-8")
o = (app / "AssistantOutcome.java").read_text(encoding="utf-8")
lt = (app / "LearnedTerms.java").read_text(encoding="utf-8")
lb = (app / "LearnedBrain.java").read_text(encoding="utf-8")
wc = (app / "WorkCounts.java").read_text(encoding="utf-8")
pf = (app / "ProducerFeedActivity.java").read_text(encoding="utf-8")
no = (app / "NewOperationActivity.java").read_text(encoding="utf-8")
dlg = (app / "OperationVoiceDialog.java").read_text(encoding="utf-8")
acc = (app / "AccountDeletion.java").read_text(encoding="utf-8")
backup = (root / "app/src/main/res/xml/push_backup_rules.xml").read_text(encoding="utf-8")
extract = (root / "app/src/main/res/xml/push_data_extraction_rules.xml").read_text(encoding="utf-8")
manual = (app / "TezgahBackup.java").read_text(encoding="utf-8")
bact = (app / "BackupActivity.java").read_text(encoding="utf-8")
cloudb = (app / "TezgahCloudBackup.java").read_text(encoding="utf-8") + (app / "TezgahCloudPack.java").read_text(encoding="utf-8")
learn = c[c.index("private void learnFromCloud("):]
learn = learn[:learn.index("\n    }\n")]
checks = [
    ("asistan kararı öğrenilen sözler beyninden (kuralların önünde)",
     "learned().conversationStage(" in c and "brain.recordStage(new Brain.Input(raw),records,cmd)" in c
     and "rules.conversationStage(" not in c and "rules.recordStage(" not in c),
    ("öğretme sorusu: anlaşılmadığında, hazır cevaptan sonra, ipucundan önce",
     c.index("if(later==AssistantOutcome.Kind.SPECIAL){post(") < c.index("if(later==AssistantOutcome.Kind.TEACH){") < c.index("ZeytinAssistantHints.hint(raw")
     and o.index("return Kind.SPECIAL;") < o.index("return Kind.TEACH;") < o.index("return Kind.HINT;")),
    ("öğretme sorusu sohbet cümlesinde sorulmaz (iş biçiminde değilse)",
     '"sohbet".equals(d.variant) && !LearnedTerms.workShaped(raw, gardens)' in lb and "LearnedTerms.teachable(raw,brain.store())" not in c),
    ("öğretme sorusu tek soru, okunur, büyük düğmeler", 'LearnedTerms.capital(word)+" ne işi?"' in c and "answer.setTextSize(24);" in c
     and "dp(64)" in c and 'labels.add("Vazgeç");' in c and 'labels.add("Başka");' in c),
    ("öğretilen işte form dolu açılır (soru sorulmadan)", "teachOpenForm=true;" in c and 'AssistantOutcome.records(decision,openForm)' in c and 'op != null && "soru".equals(d.variant) && !openForm' in o),
    ("vazgeç: aynı cümle bir daha sorulmaz", "learned().store().decline(LearnedTerms.sentenceKey(p[0]));" in c),
    ("sonraki kullanımda geri bildirim + Unut düğmesi", "LearnedTerms.told(taught)" in c and "addForgetButton(taught);" in c),
    ("bulut-onaylı öğrenme yalnız 'evet'le kaydedilince", "if(id>0&&opCloudRaw!=null)learnFromCloud(opCloudRaw,d.entry);" in c
     and "LearnedTerms.SOURCE_CLOUD" in learn),
    ("sözlük komutları", "ActionCatalog.SOZLER_GOSTER" in c and "ActionCatalog.SOZ_UNUT" in c),
    ("sözler ayrı dosyada (zd_learned_terms.db), ana veritabanında değil", 'FILE = "zd_learned_terms.db"' in db),
    ("Google otomatik yedeğinden ve cihaz aktarımından hariç",
     'path="zd_learned_terms.db"' in backup and extract.count('path="zd_learned_terms.db"') == 2),
    ("Tezgâh tablo listesinde yok (sözler ayrı ayar satırı olarak gider)", "learned_terms" not in manual),
    ("Ayarlar → Yedekle dosyasına girer, geri yüklemede eklenir",
     "m.put(LearnedTerms.BACKUP_KEY, terms);" in bact and "LearnedTerms.importText(String.valueOf(terms), LearnedTermsDb.store(this, owner));" in bact),
    ("bulut yedeğine (sunucu) girmez", "LearnedTerms" not in cloudb and "learned_terms" not in cloudb),
    ("hesap silinince sözler de silinir", "LearnedTermsDb.clear(activity, uid);" in acc),
    ("sözlük internete gitmez", all(not re.search(w, t) for t in (db, lt, lb) for w in ["http", "Firebase", "firestore", "supabase", "URL\\("])),
    # İP5 Adım 0: yeni işler
    ("öğretme düğmeleri kayıtlardaki sıklığa göre; Başka ve Vazgeç en altta",
     "LearnedTerms.ordered(counts)" in c and c.index("LearnedTerms.ordered(counts)") < c.index('labels.add("Başka");') < c.index('labels.add("Vazgeç");')
     and "WorkCounts.load(activity,owner)" in c),
    ("düğmeler sığmazsa kayar (en çok ekranın %45'i), düğme 64 dp, soru 24 punto",
     "ScrollView scroll=new ScrollView(activity);" in c and "heightPixels*45/100" in c and "answer.setTextSize(24);" in c
     and "new LinearLayout.LayoutParams(0,dp(64),1)" in c),
    ("sıklık yalnız okunur (kayıt yazılmaz)", "insert" not in wc and "update(" not in wc and "delete(" not in wc and "execSQL" not in wc),
    ("Diğer işinin notu (eğitim turu 1: + tutarın hesabı) forma ve kayda gider",
     "intent.putExtra(NewOperationActivity.EXTRA_VOICE_NOTE, entry.formNote());" in pf and "getStringExtra(EXTRA_VOICE_NOTE)" in no
     and "entry.note" in dlg),
    ("sözlük dosyasında not sütunu (sürüm 2, eski sözler korunur)",
     "VERSION = 2" in db and "ALTER TABLE learned_terms ADD COLUMN note" in db and 'v.put("note", t.note);' in db),
    ("buluttan öğrenmede Diğer işleri karışmaz", "!c.note.equals(entry.note)" in learn),
]
bad = 0
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
    bad += 0 if ok else 1
sys.exit(1 if bad else 0)
