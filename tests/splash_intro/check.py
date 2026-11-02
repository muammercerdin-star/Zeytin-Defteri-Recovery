# Açılış animasyonu: yönlendirme bir kez, dokununca geçer, animasyonlar kapalıysa oynamaz, Android 12+ sistem ekranı düz.
import os, sys
R = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main")
J = lambda f: open(os.path.join(R, "java/com/zeytindefteri/app", f), encoding="utf-8").read()
w, v = J("WelcomeActivity.java"), J("SplashIntroView.java")
res = lambda f: open(os.path.join(R, "res", f), encoding="utf-8").read()
checks = [
    ("animasyon bitince yönlendirme", "new SplashIntroView(this, this::route)" in w),
    ("yönlendirme bir kez", "if (routed || isFinishing() || isDestroyed()) return;" in w),
    ("yeniden oluşturulunca animasyon tekrar oynamaz", "savedInstanceState == null && SplashIntroView.enabled(this)" in w),
    ("dokununca geçer", "ACTION_UP) finish();" in v),
    ("animasyonlar kapalıysa oynamaz", "ANIMATOR_DURATION_SCALE" in v),
    ("yaklaşık 4 sn", "TOTAL = 4000" in v),
    ("Android 12+ düz sistem ekranı (gündüz + gece)", all("windowSplashScreenAnimatedIcon" in res(f) for f in ["values-v31/themes.xml", "values-night-v31/themes.xml"])),
]
bad = 0
for n, ok in checks:
    print(("PASS " if ok else "FAIL ") + n); bad += 0 if ok else 1
sys.exit(1 if bad else 0)
