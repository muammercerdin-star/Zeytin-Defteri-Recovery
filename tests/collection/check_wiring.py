"""Tahsilat (eğitim turu 1): bağlantıların statik denetimi. Android derlemesi değildir; telefonda denenmelidir."""
import pathlib
import sys

root = pathlib.Path(__file__).resolve().parents[2]
app = root / "app/src/main/java/com/zeytindefteri/app"
ctrl = (app / "ZeytinAssistantController.java").read_text(encoding="utf-8")
sheet = (app / "BuyerPaymentSheet.java").read_text(encoding="utf-8")
body = ctrl[ctrl.index("    private void collect("):ctrl.index("    private void postDialog(")]
checks = [
    ("asistan: tahsilat yolu seçicinin türüyle açılır", "if(path==AssistantOutcome.Kind.COLLECTION){" in ctrl),
    ("kayıt yalnız pencerenin Kaydet'iyle: asistan ödeme yazmaz",
     "recordPlan" not in body and "SalePayments" not in body and "BuyerPaymentSheet.show(activity,db,buyer.id,buyer.name,amount,null);" in body),
    ("alıcı yoksa hiçbir şey kaydedilmez", "Hiçbir şey kaydetmedim." in body and "if(found.isEmpty()){" in body),
    ("aynı adda birden çok alıcı: panelde sorulur (standart pencere yok)",
     '"Hangi "+entry.buyer+"?"' in body and "choice(buyer.name," in body and "AlertDialog" not in body),
    ("açık borç yoksa pencere açılmaz", "if(amount<=0){" in body and "açık borç görünmüyor" in body),
    ("pencere söylenen tutarla açılır, fazlası uyarılır, borçtan fazlası yine yazılamaz",
     "amount.setText(SalePayStatus.plain(prefill > 0 ? prefill : total));" in sheet
     and "CollectionEntry.warning(prefill, total)" in sheet and "daha fazlası yazılamaz" in sheet),
]
bad = 0
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
    bad += 0 if ok else 1
sys.exit(1 if bad else 0)
