"""Sohbetle kayıt: asistan → hasat / salamura / bahçe / gönderi / elek bağlantıları."""
import os, sys
A = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "java")
r = lambda f: open(os.path.join(A, f), encoding="utf-8").read()
P = "com/zeytindefteri/app/"
c, f = r(P + "ZeytinAssistantController.java"), r(P + "ProducerFeedActivity.java")
checks = [
    ("asistan kayit sohbetlerini baslatir", "VoiceRecordDialog.detect(raw)" in c and "startRecord(" in c and "answerRecord(raw)" in c),
    ("kayit sohbeti formu her sey yazili acar", "host.openRecordForm(d,g)" in c and "public void openRecordForm(" in f),
    ("hasat formu iscilik/nakliye ile dolar", "EXTRA_VOICE_LABOR" in r("HarvestActivity.java") and "HarvestActivity.EXTRA_VOICE_TRANSPORT" in f),
    ("salamura yeni parti penceresi sesle dolu acilir", "applyVoiceToBatch(etOliveKg, etSaltKg, etWaterLiter, etVariety)" in r(P + "BrineActivity.java")),
    ("bahce formu sesle dolar (konum ve kayit kullanicida)", "applyVoice()" in r(P + "AddGardenActivity.java") and "AddGardenActivity.EXTRA_VOICE_NAME" in f),
    ("gonderi kendiliginden paylasilmaz, taslak olarak acilir", '"draft_content", d.text("text")' in f),
    ("elek: asistandan gelince sesli elek konusmasi devam eder", 'voiceSession().start("Yazdım. Başka numara var mı?' in r(P + "ElekActivity.java") and "EXTRA_VOICE_START" in f),
]
bad = 0
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name); bad += 0 if ok else 1
print("voice_records wiring: " + ("PASS" if bad == 0 else "FAIL"))
sys.exit(1 if bad else 0)
