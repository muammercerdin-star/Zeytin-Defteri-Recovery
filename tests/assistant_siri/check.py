"""Siri tarzı asistan: ayrı küre yok, alt menüdeki düğme canlanır, kenar ışığı, alt menü karartılmaz."""
import os, sys
A = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "java", "com", "zeytindefteri", "app")
c = open(os.path.join(A, "ZeytinAssistantController.java"), encoding="utf-8").read()
p = open(os.path.join(A, "ProducerFeedActivity.java"), encoding="utf-8").read()
o = open(os.path.join(A, "ZeytinAssistantOrbView.java"), encoding="utf-8").read()
checks = [
    ("alt menu varken ayri kure yok", "if(!navMode){" in c and "if(orb!=null)root.addView(orb,op);" in c),
    ("pencere alt menunun ustunde biter (menu karartilmaz)", "wp.height=navTop;" in c and "FLAG_LAYOUT_IN_SCREEN" in c),
    ("yazarken klavye icin eski duzen", "if(navMode)applyWindow();" in c),
    ("kenar isigi durum ve ses seviyesiyle", "glow.state(typing?State.IDLE:next,speaking)" in c and "glow.rms(rms)" in c),
    ("acilinca/kapaninca dugmeye haber", "host.opened(true);" in c and "host.opened(false);" in c),
    ("dugme acikken kure kalir", "public void open(boolean on)" in o and "(next==State.IDLE&&!open)" in o),
    ("dugme yazisi durumu gosterir", 'label = "DİNLİYOR"' in p and 'label = "ASİSTAN"' in p),
    ("alt menu konumu verilir", "public int navTopOnScreen()" in p),
]
bad = 0
for n, ok in checks:
    print(("PASS " if ok else "FAIL ") + n); bad += 0 if ok else 1
sys.exit(1 if bad else 0)
