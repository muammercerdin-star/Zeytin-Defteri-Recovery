"""Tezgah sesli tartım bağlantıları: düğme, izin, kayıt yalnız onaydan sonra, ekran canlı."""
import os, re, sys
A = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "java", "com", "zeytindefteri", "app")
act = open(os.path.join(A, "TezgahActivity.java"), encoding="utf-8").read()
ses = open(os.path.join(A, "TezgahVoiceSession.java"), encoding="utf-8").read()
dia = open(os.path.join(A, "TezgahVoiceDialog.java"), encoding="utf-8").read()
checks = [
    ("Tezgah ekraninda Sesle tart dugmesi var", "body.addView(voiceButton()" in act and '"Sesle tart"' in act),
    ("mikrofon izni sonucu oturuma iletilir", "TezgahVoiceSession.MIC_REQUEST" in act and "PERMISSION_GRANTED) startVoice();" in act),
    ("ekrandan cikinca oturum kapanir", re.search(r"onPause\(\) \{\s*if \(voice != null\) voice.close\(\);", act) is not None),
    ("kayit ancak SAVE eyleminde", ses.count("host.save(") == 1 and "Action.SAVE" in ses),
    ("SAVE yalniz onay asamasinda evet ile", re.search(r"private Reply confirm\(String s\).*?Action\.SAVE", dia, re.S) is not None and dia.count("Action.SAVE") == 1),
    ("tartim aninda ekrana yazilir", "host.add(r.grade, r.kasa, r.kg)" in ses and "voiceCommit(g, k, kg)" in act),
    ("kayit QR fisini acar", "savePurchase(paid, true)" in act),
    ("dugme ses dalgasina donusur", "new ElekVoiceWaveView(a).onGreen()" in ses),
    ("ekran kararmaz", "FLAG_KEEP_SCREEN_ON" in ses),
    ("ana ekrandan gelince sihirbaz kendiliginden acilmaz", "fromHome && state == null) body.post" not in act),
    ("Sesle tart sihirbaz acmaz, tartim yazma ekranini gosterir", "voiceMode = true;" in act and "new TezgahVoiceSession(this, voiceHost(), voiceBar)" in act
        and re.search(r"if \(voiceMode\) \{.*?gradeRow\(\).*?entryCard\(\).*?weighingsCard\(\)", act, re.S) is not None),
    ("asistan kapaninca ekran normale doner", "if (voiceMode) { voiceMode = false;" in act),
    ("sesle tartimda klavye acilmaz", "if (!voiceOpen()) showKeyboard(wizardKg);" in act),
    ("sihirbaz kapaninca asistan da kapanir", "if (voice != null) voice.close(); wizard = null;" in act),
    ("sessizlikte konusmaz, sessizce dinlemeye devam eder", "Bekliyorum" not in ses and "Hâlâ buradayım" not in ses and "main.postDelayed(this::listen, 250)" in ses),
]
bad = 0
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
    bad += 0 if ok else 1
print("tezgah_voice wiring: " + ("PASS" if bad == 0 else "FAIL"))
sys.exit(1 if bad else 0)
