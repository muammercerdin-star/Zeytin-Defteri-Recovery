"""Yağhane (eğitim turu 1): bağlantıların statik denetimi. Android derlemesi değildir; telefonda denenmelidir."""
import pathlib
import sys

root = pathlib.Path(__file__).resolve().parents[2]
app = root / "app/src/main/java/com/zeytindefteri/app"
ctrl = (app / "ZeytinAssistantController.java").read_text(encoding="utf-8")
oil = (app / "OilActivity.java").read_text(encoding="utf-8")
rem = (app / "TaskReminders.java").read_text(encoding="utf-8")
voice = oil[oil.index("    private void editFromVoice("):oil.index("    private List<OilPressings.Pressing> load()")]
offer = oil[oil.index("    private View reminderOffer() {"):oil.index("    private void toast(String s) {")]
checks = [
    ("asistan: yağhane yolu seçicinin türüyle, sıkım formunu cümleyle açar",
     "if(path==AssistantOutcome.Kind.OIL_FORM){" in ctrl and "putExtra(OilActivity.EXTRA_VOICE,raw)" in ctrl),
    ("sesle gelen sıkım kendiliğinden kaydedilmez (yalnız formu doldurur)",
     "OilPressings.save" not in voice and "edit(null, draft, oilKg, entry.waitingOil());" in voice),
    ("kasa kiloya çevrilmez: notta kalır, zeytin kilosu ve yağhane adı sorulur",
     "draft.note = entry.note();" in voice and 'mill.setError("Hangi yağhane?");' in oil and "kaç kilo tuttu?" in oil),
    ("'Yağ çıkınca hatırlatayım mı?' her seferinde sorulur, sormadan kurulmaz",
     "if (offerReminder) {" in oil and "TaskReminders.add(" in offer and offer.index("setOnClickListener") < offer.index("TaskReminders.add(")
     and oil.count("TaskReminders.add(") == 1),
    ("iş hatırlatması geçmişe ve geceye kurulmaz", "TaskReminderEntry.allowed(at, System.currentTimeMillis())" in rem),
]
bad = 0
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
    bad += 0 if ok else 1
sys.exit(1 if bad else 0)
