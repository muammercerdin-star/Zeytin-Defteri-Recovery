"""Gelecek zamanlı iş → hatırlatma (eğitim turu 1): bağlantıların statik denetimi. Telefonda denenmelidir."""
import pathlib
import sys

root = pathlib.Path(__file__).resolve().parents[2]
app = root / "app/src/main/java/com/zeytindefteri/app"
ctrl = (app / "ZeytinAssistantController.java").read_text(encoding="utf-8")
body = ctrl[ctrl.index("    private void askReminder("):ctrl.index("    /** Panelin cevap kartının altına büyük seçim düğmesi")]
add_at = body.index("TaskReminders.add(")
checks = [
    ("asistan: hatırlatma yolu seçicinin türüyle açılır", "if(path==AssistantOutcome.Kind.REMINDER){" in ctrl),
    ("her zaman sorulur: kurma yalnız 'Evet, hatırlat' düğmesinde",
     body.count("TaskReminders.add(") == 1 and body.rfind('choice("Evet, hatırlat",true,v->{', 0, add_at) >= 0
     and "autoReminder" not in body and "HarmlessPermission" not in body and "AlertDialog" not in body),
    ("gün söylenmediyse sorulur", "if(at<0){" in body and "ne zaman hatırlatayım?" in body),
    ("geçmiş ve gece saatine kurulmaz", "if(!TaskReminderEntry.allowed(at,System.currentTimeMillis())){" in body),
    ("kurulunca Geri al", 'choice("Geri al",false,' in body and "TaskReminders.remove(activity,id)" in body),
]
bad = 0
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
    bad += 0 if ok else 1
sys.exit(1 if bad else 0)
