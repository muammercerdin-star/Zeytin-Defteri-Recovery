"""Görünür uygulayıcı (İP7): bağlantıların statik denetimi. Android derlemesi değildir; telefonda denenmelidir."""
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parents[2]
app = root / "app/src/main/java/com/zeytindefteri/app"


def read(p):
    return p.read_text(encoding="utf-8")


runner = read(app / "ActionRunner.java")
guide = read(app / "TanecikGuide.java")
sale = read(root / "app/src/main/java/SaleActivity.java")
ctrl = read(app / "ZeytinAssistantController.java")
settings = read(app / "SettingsView.java")
reminders = read(app / "PaymentReminders.java")
confirm_body = runner[runner.index("    private void confirm() {"):runner.index("    /** Kaydedildi: hepsini")]
before_confirm = runner[:runner.index("    private void confirm() {")]

checks = [
    ("Tanecik'in uçma ve harf harf yazması yeniden kullanılır (ikinci kopya yok)",
     "new TanecikGuide(activity, steps, fast(activity), this::filled, this::cancel, this::tap)" in runner
     and "flyTo" not in runner and "propTick" not in runner),
    ("doldurma kipi: yazdığını silmez, ekranı kapatmaz, Kaydet'e basmaz",
     "if (onDone != null) {" in guide and "onDone.run();" in guide and "performClick" not in guide
     and "btnSaveSale" not in guide and "btnSaveSale" not in runner),
    ("doldurucu yalnız ödeme düğmelerine ('Alınmadı', tek adımlı satışta 'Tamamı') dokunur, Kaydet'e asla",
     runner.count("performClick") == 1
     and "if (v.getId() == R.id.chipPayNone || v.getId() == R.id.chipPayFull) {\n            v.performClick();" in runner),
    ("tek adımlı satış: ödeme söylenmediyse onaydan önce sorulur, varsayılmaz",
     "if (payment.isEmpty()) {\n            askPayment();\n            return;\n        }" in runner
     and 'text("Parasını aldın mı?", 22, true)' in runner and "ActionPlan.parseAny(planText)" in sale),
    ("tur kipi eskisi gibi (yazılanı siler, ekranı kapatır)",
     "for (EditText e : typed) e.setText(\"\");" in guide and "this(activity, steps, false, null, null, null);" in guide),
    ("gece (22:00–05:00) Tanecik konuşmaz",
     "TanecikLines.sleeping(hour)" in guide and "TanecikLines.sleeping(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))" in runner
     and "if (quiet || voice == null)" in runner),
    ("hızlı mod: animasyonsuz, onay ve Geri al aynı", "fastStep(v, st, i);" in guide and "fast(activity)" in runner),
    ("onaydan önce yazma yok: yazıcı yalnız onay ve geri alda",
     before_confirm.count("flow.confirm(") == 0 and "flow.confirm(writer())" in confirm_body
     and runner.count("writer()") == 3),
    ("satış formunda yazma yalnız runner'ın onayıyla (saveForRunner) ya da çiftçinin Kaydet'iyle",
     "runnerSaving = true;\n                saveSale();" in sale and "if (runnerSaving) {\n            // Çok adımlı akış" in sale),
    ("yarım akış: geri tuşu ve Hayır hiçbir şey yazmadan kapatır",
     "runner.abandon();" in sale and "onBackPressed" not in sale and 'button("Hayır", false, v -> cancel())' in runner),
    ("Geri al ekranda yeterince kalır (en az 15 sn), hepsini birlikte geri alır",
     int(re.search(r"UNDO_SECONDS = (\d+);", runner).group(1)) >= 15 and "flow.undo(writer())" in runner),
    ("geri al: satış alacağıyla, hatırlatma alarmıyla, yeni alıcı (kullanılmıyorsa)",
     "deleteSaleWithReceivables(saleId)" in runner and "PaymentReminders.remove(activity, reminderId)" in runner
     and "new LocalReminderScheduler(app).cancel(reminderId);" in reminders and "form.deleteBuyerIfUnused(id);" in runner),
    ("asistan: çok adımlı karar satış formunu runner'la açar",
     "if(path==AssistantOutcome.Kind.MULTI_STEP){" in ctrl and "putExtra(SaleActivity.EXTRA_PLAN,raw)" in ctrl),
    ("izin basamağı: yalnız ödeme hatırlatması, çiftçi 'hep yap' dediyse; Ayarlar'dan kapatılır",
     "ActionRunner.autoReminder(this)" in sale and "setAutoReminder(activity, true);" in runner
     and "autoReminderRow(activity)" in settings and "HarmlessPermission.told(" in sale),
    ("Ayarlar: Hızlı doldurma", "fastFillRow(activity)" in settings and "ActionRunner.setFast(activity, on);" in settings),
    ("karne: çok adımlı akış, onay, geri alma",
     "AssistantScorecard.MULTI_STARTED" in runner and "AssistantScorecard.MULTI_CONFIRMED" in runner
     and "AssistantScorecard.MULTI_UNDONE" in runner),
    ("büyük düğmeler (58 dp), büyük yazı", "dp(58)" in runner and 'text("Kaydedeyim mi?", 22, true)' in runner),
    ("iskelet sabit: onay kartı formun üstüne biner (yerleşime eklenmez)",
     "activity.findViewById(android.R.id.content)" in runner and "Gravity.BOTTOM" in runner),
]
bad = 0
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
    bad += 0 if ok else 1
sys.exit(1 if bad else 0)
