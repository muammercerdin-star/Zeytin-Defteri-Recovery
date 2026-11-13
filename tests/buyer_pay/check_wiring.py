"""Alıcı ekranı: Ödeme aldım düğmesi, satış satırı menüsü (ödeme + silme), tek işlemde toplu kayıt."""
from pathlib import Path
A = Path('app/src/main/java/com/zeytindefteri/app')
b = (A / 'BuyersActivity.java').read_text(encoding='utf-8')
s = (A / 'BuyerPaymentSheet.java').read_text(encoding='utf-8')
p = (A / 'SalePayments.java').read_text(encoding='utf-8')
checks = {
    "ALACAĞIN kartında Ödeme aldım": 'BuyerPaymentSheet.show(this, databaseHelper, buyer.id, buyer.name, this::render)' in b,
    'satış satırı menüsü': 'row.setOnClickListener(v -> saleOptions(l));' in b and 'PaymentSheet.show(this, databaseHelper, l.saleId, this::render)' in b,
    'silme hâlâ var': 'askDeleteSale(l);' in b,
    'tek işlem': 'db.setTransactionSuccessful();' in p and 'if (!record(db, owner, p.saleId, p.amount, method, today, now)) return false;' in p,
    'fazla tutar engeli': 'plan.excess > 0.009' in s and 'plan.excess > 0.009' in p,
    'kapanan satışın hatırlatıcısı kapanır': 'if (p.closes) PaymentReminders.close(' in s,
    'buluta gider': 'syncSaleByLocalId(user.getUid(), p.saleId)' in s,
}
for k, v in checks.items(): print(('OK  ' if v else 'FAIL') + ' ' + k)
raise SystemExit(0 if all(checks.values()) else 1)
