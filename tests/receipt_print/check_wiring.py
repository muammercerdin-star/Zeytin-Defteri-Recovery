"""Fiş baskısı Tezgâh baskı önizlemesinde: yalnız ilk açılışta basılır, ayar değişince yeniden basılmaz,
animasyon kapalıysa atlanır, dokununca biter, kapanınca durur; Yazdır ve ayarlar korunur.
Elek kaydı akışı değişmedi (kayıttan sonra fiş kendiliğinden açılmaz)."""
import re, sys, pathlib
J = pathlib.Path(__file__).resolve().parents[2] / "app/src/main/java/com/zeytindefteri/app"
t = (J / "TezgahPrintPreview.java").read_text(encoding="utf-8")
e = (J / "ElekActivity.java").read_text(encoding="utf-8")
r = (J / "ElekReceiptActivity.java").read_text(encoding="utf-8")
bad = []
def need(c, m):
    if not c: bad.append(m)
need("printed = !animationsOn();" in t and "ANIMATOR_DURATION_SCALE" in t, "animasyon kapalıysa atla")
need(re.search(r"if \(!printed\) \{\s*printed = true;\s*startPrint\(", t), "yalnız bir kez basılır")
need(re.search(r"private void render\(\) \{\s*//[^\n]*\n\s*if \(printAnimator != null && printAnimator.isRunning\(\)\) printAnimator.end\(\);", t), "ayar değişince baskı tamamlanır")
need("printAnimator.cancel()" in t and "setOnDismissListener" in t, "kapanınca durur")
need("printAnimator.end()" in t and "clip.setOnClickListener" in t, "dokununca biter")
need("ReceiptPrint.progress(" in t and "ReceiptPrint.durationMs(" in t, "baskı eğrisi")
need("LAYER_TYPE_HARDWARE" in t and "LAYER_TYPE_NONE" in t, "hareket sırasında donanım katmanı")
for f in ['"Yazdır"', '"Vazgeç"', "TezgahPrinter.setWidth", "TezgahPrinter.setTurkish", "TezgahPrinter.setPrintQr", "TezgahPrinter.choose", "TezgahQrUi.bitmap"]:
    need(f in t, "korunan özellik: " + f)
need("EXTRA_PRINT" not in e and "EXTRA_PRINT" not in r, "Elek akışı eski hâlinde")
if bad:
    print("FAIL", bad); sys.exit(1)
print("check_wiring: ok")
