"""Tek dokunuş bağlantı: Google okuyucu hemen açılır, ad formu yok, yetki bir kez sorulur ve hatırlanır,
okuyucu açılamazsa eski kamera okuyucu yedek; takılınca sebep + Yeniden okut; bağlanınca bildirim."""
import re, sys, pathlib
J = pathlib.Path(__file__).resolve().parents[2] / "app/src/main/java/com/zeytindefteri/app"
h = (J / "TezgahHomeActivity.java").read_text(encoding="utf-8")
w = (J / "TezgahWebService.java").read_text(encoding="utf-8")
bad = []
def need(c, m):
    if not c: bad.append(m)
pair = h[h.index("private void webPair()"):h.index("private void webScan(")]
need("EditText" not in pair, "ad formu yok")
need('"web_full_" + owner' in pair and "prefs.contains(key)" in pair, "yetki bir kez sorulur")
need("webScan(prefs.getBoolean(key, false))" in pair, "sonraki seferler doğrudan okuyucu")
scan = h[h.index("private void webScan("):h.index("private void webScanned(")]
need("TezgahQrUi.scan(this" in scan, "Google okuyucu")
need("TezgahWebScanActivity.class" in scan and "7315" in scan, "yedek okuyucu")
sc = h[h.index("private void webScanned("):]
need("TezgahWebLink.looksLikePairing(raw)" in sc and "TezgahWebService.launch(this, qr, owner, name, full)" in sc, "okununca hemen başlar")
con = h[h.index("private void webConnect()"):h.index("private void webPair()")]
need('"Yeniden okut"' in con and "TezgahWebLink.advice(" in con, "takılınca sebep ve yeniden okut")
need("TezgahWebLink.connectedToast(" in h and "justConnected" in h, "bağlanınca bildirim")
need("TezgahWebLink.row(" in h, "durum satırı")
need("public static String diagnosis(String owner)" in w and "startedAt = SystemClock.elapsedRealtime();" in w, "servis durum ve süre verir")
need("if (request == 7315)" in h, "yedek okuyucu sonucu işlenir")
if bad:
    print("FAIL", bad); sys.exit(1)
print("check_flow: ok")
