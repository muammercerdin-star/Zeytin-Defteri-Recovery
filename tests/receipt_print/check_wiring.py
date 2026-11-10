"""Fiş baskısı bağlantıları: kayıttan sonra açılır, sesli asistanla çift açılmaz, toplu girişte açılmaz,
yeniden çizimde tekrar basılmaz, animasyon kapalıysa atlanır, mevcut özellikler korunur."""
import re, sys, pathlib
J = pathlib.Path(__file__).resolve().parents[2] / "app/src/main/java/com/zeytindefteri/app"
r = (J / "ElekReceiptActivity.java").read_text(encoding="utf-8")
e = (J / "ElekActivity.java").read_text(encoding="utf-8")
bad = []
def need(c, m):
    if not c: bad.append(m)
need('EXTRA_PRINT = "print"' in r, "EXTRA_PRINT")
need("savedInstanceState == null" in r and "getBooleanExtra(EXTRA_PRINT, false)" in r, "yalnız ilk açılış")
need("ANIMATOR_DURATION_SCALE" in r, "animasyon kapalıysa atla")
need(re.search(r"if \(printPending\) \{\s*printPending = false;", r), "yeniden çizimde basılmaz")
need("printAnimator.cancel()" in r, "onDestroy iptal")
for f in ["private void share()", "private void editNote()", "private void confirmDelete()", "askPaymentAndConvert()", "buildSaleLink()"]:
    need(f in r, "korunan özellik: " + f)
m = re.search(r"loadSeasonArchive\(\);\s*//[^\n]*\n\s*if \(!voiceSaving && !mixedMode\) \{\s*startActivity\([^;]*EXTRA_PRINT, true\)\);", e)
need(m, "kayıt sonrası fiş basılır (ses/toplu hariç)")
need("voiceSaving = true;" in e and "finally { voiceSaving = false; }" in e, "sesli kayıtta bayrak")
need(e.count("EXTRA_PRINT, true") == 2, "asistan da basarak açar")
if bad:
    print("FAIL", bad); sys.exit(1)
print("check_wiring: ok")
