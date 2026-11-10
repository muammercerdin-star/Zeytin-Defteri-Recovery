"""Tartım sihirbazı sabit durur: pencere üste sabitlenir (ortalanıp kaymaz), adım değişirken
klavye tutucuya geçer (klavye kapanıp açılıp pencereyi zıplatmaz)."""
import re, sys, pathlib
J = pathlib.Path(__file__).resolve().parents[2] / "app/src/main/java/com/zeytindefteri/app"
t = (J / "TezgahActivity.java").read_text(encoding="utf-8")
m = t[t.index("private void openWizard(int firstStep)"):t.index("private void goWizard(")]
bad = []
def need(c, msg):
    if not c: bad.append(msg)
need("w.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);" in m, "üste sabit")
need("w.setGravity(Gravity.CENTER);" not in m, "ortalama kaldırıldı")
need("TezgahWizardFlow.wizardTopDp(" in m and "w.setAttributes(at);" in m, "üst boşluk")
need("SOFT_INPUT_ADJUST_RESIZE" in m, "klavye ayarı korunur")
need("wizardKeeper = new EditText(this);" in m and "wizardColumn.addView(wizardKeeper" in m, "klavye tutucu")
need(re.search(r"private void renderWizard\(\) \{\s*if \(wizardBox == null\) return;\s*View focused = wizardBox.findFocus\(\);\s*if \(focused instanceof EditText && wizardKeeper != null\) wizardKeeper.requestFocus\(\);\s*wizardBox.removeAllViews\(\);", t), "silmeden önce odak tutucuya")
if bad:
    print("FAIL", bad); sys.exit(1)
print("check_stable: ok")
