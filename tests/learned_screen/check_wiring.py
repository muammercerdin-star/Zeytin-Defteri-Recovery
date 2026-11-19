"""Öğrendiklerim ekranı (İP6): bağlantıların statik denetimi. Android derlemesi değildir."""
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parents[2]
app = root / "app/src/main/java/com/zeytindefteri/app"


def read(name):
    return (app / name).read_text(encoding="utf-8")


act = read("LearnedActivity.java")
screen = read("LearnedScreen.java")
settings = read("SettingsView.java")
ctrl = read("ZeytinAssistantController.java")
manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
uygulama = settings[settings.index('addSection(body, activity, "UYGULAMA",'):settings.index("View management =")]
sizes = [int(x) for x in re.findall(r'label\([^;]*?, (\d+), (?:text|muted|danger), (?:true|false)\)', act)]

checks = [
    ("Ayarlar → Uygulama bölümünde 'Öğrendiklerim' satırı (aynı satır bileşeni)",
     "learnedRow(activity)" in uygulama and "return row(activity, R.drawable.ic_assistant_nav_archive, LearnedScreen.TITLE," in settings
     and "LearnedActivity.open(activity)" in settings),
    ("ekran manifestte, dışa kapalı", '<activity android:name=".LearnedActivity" android:exported="false"' in manifest),
    ("Ayarlar'ın bölüm kartı ve ayırıcısı kullanılır", "SettingsView.section(this," in act and "SettingsView.separator(this)" in act),
    ("asistan: 'öğrendiklerini göster' ekranı açar, mikrofonu açmaz",
     "LearnedActivity.open(activity);" in ctrl[ctrl.index("if(d.is(ActionCatalog.SOZLER_GOSTER)){"):]
     and "spokenSummary(taught),OperationVoiceDialog.Action.CANCEL" in ctrl),
    ("asistanla aynı sözlük (etkin hesap)", "LearnedTermsDb.store(this, owner" in act and "getActiveOwnerUid()" in act),
    ("ekran her açılışta yeniden okunur (silinen hemen kalkar)", "protected void onResume()" in act and "render();" in act),
    ("büyük yazı (en küçük 15 punto), Sil en az 48 dp", sizes and min(sizes) >= 15 and "delete.setMinHeight(dp(48));" in act),
    ("Hepsini unut onay ister", "new AlertDialog.Builder(this)" in act and ".setMessage(LearnedScreen.FORGET_ALL_ASK)" in act
     and '.setNegativeButton("Vazgeç", null)' in act),
    ("tek söz Sil: onaysız (asistanın kendi notu)", "delete.setOnClickListener(v -> forget(r.key));" in act),
    ("fark ettiklerim bölümü hazır, İP5 yokken boş (uydurma satır yok)",
     "LearnedScreen.noticed(new ArrayList<>())" in act and "NOTICED_EMPTY" in act),
    ("yalnız kullanılan sözler gösterilir", "if (usable(t))" in screen and "LearnedTerms.choiceOf(t) != null" in screen),
    ("ağ yok", not re.search(r"http|Firebase|firestore|supabase|URL\(", act + screen)),
]
bad = 0
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
    bad += 0 if ok else 1
sys.exit(1 if bad else 0)
