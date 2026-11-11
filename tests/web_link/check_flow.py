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
need("TezgahWebScanActivity" not in scan and "Google Play hizmetlerini" in scan, "kamera izni yok; okuyucu açılmazsa açık mesaj")
sc = h[h.index("private void webScanned("):]
need("TezgahWebLink.looksLikePairing(raw)" in sc and "TezgahWebService.launch(this, qr, owner, name, full)" in sc, "okununca hemen başlar")
con = h[h.index("private void webConnect()"):h.index("private void webPair()")]
need('"Yeniden okut"' in con and "TezgahWebLink.advice(" in con, "takılınca sebep ve yeniden okut")
need("TezgahWebLink.connectedToast(" in h and "justConnected" in h, "bağlanınca bildirim")
need("TezgahWebLink.row(" in h, "durum satırı")
need("public static String diagnosis(String owner)" in w and "startedAt = SystemClock.elapsedRealtime();" in w, "servis durum ve süre verir")
need("7315" not in h, "eski kamera okuyucu kalmadı")
r = (J / "TezgahWebReadyService.java").read_text(encoding="utf-8")
mainact = (J / "MainActivity.java").read_text(encoding="utf-8")
man = (J.parents[3] / "AndroidManifest.xml").read_text(encoding="utf-8")
need("TezgahWebLink.readyIdle(now, lastBusy)" in r and "stopSelf();" in r and "lastBusy = SystemClock.elapsedRealtime();" in r, "kayıtlı bilgisayar hizmeti boşta kapanır")
need("if (busy) lastBusy = now;" in r and '"Bağlı".equals(status)' in r and "!approvals.isEmpty()" in r, "bağlıyken ya da onay beklerken kapanmaz")
need("TezgahWebReadyService.prepare" not in mainact and "TezgahWebReadyService.prepare(this);" in h, "yalnız Tezgâh ekranından başlar")
need("android.permission.CAMERA" not in man, "kamera izni yok")
if bad:
    print("FAIL", bad); sys.exit(1)
print("check_flow: ok")
