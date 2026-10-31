"""Tezgah sesli tartım bağlantıları: düğme, izin, kayıt yalnız onaydan sonra, ekran canlı."""
import os, re, sys
A = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "java", "com", "zeytindefteri", "app")
act = open(os.path.join(A, "TezgahActivity.java"), encoding="utf-8").read()
ses = open(os.path.join(A, "TezgahVoiceSession.java"), encoding="utf-8").read()
dia = open(os.path.join(A, "TezgahVoiceDialog.java"), encoding="utf-8").read()
checks = [
    ("Tezgah ekraninda Sesle tart dugmesi var", "body.addView(voiceButton()" in act and '"Sesle tart"' in act),
    ("mikrofon izni sonucu oturuma iletilir", "TezgahVoiceSession.MIC_REQUEST" in act and "voice.onPermission" in act),
    ("ekrandan cikinca oturum kapanir", re.search(r"onPause\(\) \{\s*if \(voice != null\) voice.close\(\);", act) is not None),
    ("kayit ancak SAVE eyleminde", ses.count("host.save(") == 1 and "Action.SAVE" in ses),
    ("SAVE yalniz onay asamasinda evet ile", re.search(r"private Reply confirm\(String s\).*?Action\.SAVE", dia, re.S) is not None and dia.count("Action.SAVE") == 1),
    ("tartim aninda ekrana yazilir", "host.add(r.grade, r.kasa, r.kg)" in ses and "voiceCommit(g, k, kg)" in act),
    ("kayit QR fisini acar", "savePurchase(paid, true)" in act),
    ("dugme ses dalgasina donusur", "new ElekVoiceWaveView(a).onGreen()" in ses),
    ("ekran kararmaz", "FLAG_KEEP_SCREEN_ON" in ses),
    ("sessizlikte konusmaz, sessizce dinlemeye devam eder", "Bekliyorum" not in ses and "Hâlâ buradayım" not in ses and "main.postDelayed(this::listen, 250)" in ses),
]
bad = 0
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
    bad += 0 if ok else 1
print("tezgah_voice wiring: " + ("PASS" if bad == 0 else "FAIL"))
sys.exit(1 if bad else 0)
