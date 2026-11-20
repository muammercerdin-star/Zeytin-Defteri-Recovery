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

ctl = read("ZeytinAssistantController.java")
player = read("ScenePlayer.java")
guide = read("TanecikGuide.java")
walker = read("TanecikWalker.java")
feed = read("ProducerFeedActivity.java")


def body(src, signature):
    """Bir yöntemin gövdesi (süslü parantez sayarak)."""
    at = src.index(signature)
    start = src.index("{", at)
    depth = 0
    for i in range(start, len(src)):
        if src[i] == "{":
            depth += 1
        elif src[i] == "}":
            depth -= 1
            if depth == 0:
                return src[start:i + 1]
    return src[start:]


guide_part = ctl[ctl.index("// ============================================================ YÖNLENDİREN ASİSTAN"):
                 ctl.index("// ============================================================ ÖĞRENİLEN SÖZLER (İP4)")]
practice = body(ctl, "private void practiceAnswer(String raw)")
show_end = body(ctl, "public void show()")
submit = body(ctl, "private void submit(String raw)") if "private void submit(String raw)" in ctl else ""
writes = ("saveOperation", "insert", "saveHarvest", "OPEN_FORM", "startActivity", "recordStage")
checks += [
    ("panel karşılamayla açılır, kendiliğinden dinlemez", "else welcome();" in show_end
     and "persona().greeting(AssistantPersona.hourNow()),OperationVoiceDialog.Action.ASK" not in show_end),
    ("karşılamada soru okunur ama dinleme yok", 'guideSay(GREETING,hello+". "+GREETING,hello+" · Zeytin",false);' in guide_part),
    ("alıştırma hiçbir şey yazmaz (yalnız anlar)",
     "practiceReply" in practice and not any(w in practice for w in writes)),
    ("alıştırma cevabı yazma yolundan önce yakalanır", "if(practice){practiceAnswer(raw);return;}" in submit),
    ("yönlendirme bölümünde yeni kayıt yolu yok",
     not any(w in guide_part for w in ("saveOperation", "insert(", "saveHarvest", "db.insert"))),
    ("düğmeler bugünkü diyaloğa cevap verir (submit)", 'v->submit("evet")' in guide_part and "v->submit(g.name)" in guide_part),
    ("Vazgeç her soruda", guide_part.count('bigButton("Vazgeç"') >= 4),
    ("gece okunmaz", "if(!night()&&ttsReady&&tts!=null)speak();" in guide_part and "listenAfterSpeech=ask&&!night();" in guide_part),
    ("kısa yol beyinde doğrulanır ve 3 kez sınırlı",
     "GuidedFlow.shortcutAllowed((int)shown)" in guide_part and "GuidedFlow.verified(shortcut" in guide_part),
    ("kısa yol yalnız kaydedilen düğme işinde", "guideSaved(d,id>0);" in ctl),
    ("kart sayıları: gösterildi / kabul / red", all(k in guide_part for k in ('"kart_goster:"', '"kart_kabul:"', '"kart_red:"'))),
    ("karne sayıları: düğmeyle iş, kısa yoldan sonra konuşarak",
     '"dugme_is:"' in guide_part and '"kisa_yoldan_sonra_konusarak"' in guide_part),
    ("örneğe dokunmak yalnız yazı kutusunu doldurur", "input.setText(e);" in guide_part),
    ("yazı boyları ayarlardan (sp)", "GuideSettings.QUESTION_SP" in guide_part and "GuideSettings.BUTTON_SP" in guide_part
     and "dp(GuideSettings.BUTTON_DP)" in guide_part),
    ("iri yazıda taşmaz: içerik kayar", "new ScrollView(activity)" in guide_part),
    ("sahne yolu ekranı açar, kayıt yapmaz", "ScenePlayer.open(activity,scene,answerText)" in ctl),
    ("sahne oynatıcısı Kaydet'e dokunmaz", not any(w in player for w in ("performClick", "saveOperation", "insert("))),
    ("sahne denetimi oynatıcıda: izinsiz sahne oynamaz", "SceneCheck.allowed" in guide),
    ("tur sonu alıştırması yalnız tur tamamlanınca", "done.tourCompleted();" in walker and "practiceAfterTour()" in feed),
    ("karnede Yönlendirme satırı", "GuidedFlow.karne(GuideDb.counts(activity,host.owner()))" in body(ctl, "private void scorecardCommand(")),
    ("panel kapanınca yarım yönlendirme iz bırakmaz", "guideJob=null;" in body(ctl, "private void releasePanel()")),
]
bad = 0
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
    bad += 0 if ok else 1
sys.exit(1 if bad else 0)
