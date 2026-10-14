"""Hesap silme (Play şartı) bağlantı kontrolü: uygulama, sunucu, yasal metin ve web sayfası birbirine uyuyor mu.
Statik kontrol; canlı Firebase'e dağıtım ve cihaz testi değildir."""
from pathlib import Path
import json, re, sys

ROOT = Path(__file__).resolve().parents[2]
A = ROOT / 'app/src/main/java/com/zeytindefteri/app'
ad = (A / 'AccountDeletion.java').read_text(encoding='utf8')
sv = (A / 'SettingsView.java').read_text(encoding='utf8')
svc = (ROOT / 'functions/account/service.js').read_text(encoding='utf8')
fn = (ROOT / 'functions/account/functions.js').read_text(encoding='utf8')
idx = (ROOT / 'functions/index.js').read_text(encoding='utf8')
privacy = (ROOT / 'app/src/main/res/raw/legal_privacy.txt').read_text(encoding='utf8')
web = (ROOT / 'hosting/hesap-silme/index.html').read_text(encoding='utf8')
fb = json.loads((ROOT / 'firebase.json').read_text())
indexes = json.loads((ROOT / 'firestore.indexes.json').read_text())
legal = (A / 'LegalDocuments.java').read_text(encoding='utf8')
email = re.search(r'CONTACT_EMAIL = "([^"]+)"', legal).group(1)
overrides = {(o['collectionGroup'], o['fieldPath']) for o in indexes.get('fieldOverrides', [])
             if any(i.get('queryScope') == 'COLLECTION_GROUP' for i in o['indexes'])}

checks = {
    'Ayarlar satırı uygulama içi silmeyi açıyor': '"Hesabımı Sil"' in sv and 'AccountDeletion.start(activity)' in sv,
    'onay sözcüğü uygulama ve sunucuda aynı': 'CONFIRM_WORD = "SİL"' in ad and "CONFIRM_WORD = 'SİL'" in svc,
    'sunucu bölgesi aynı': 'REGION = "europe-west1"' in ad and "region: 'europe-west1'" in fn,
    'uç nokta adı aynı ve dışa aktarılmış': '.cloudfunctions.net/deleteAccount' in ad and 'exports.deleteAccount' in fn
        and "require('./account/functions')" in idx,
    'yakın giriş kuralı iki tarafta': "'recent-login-required'" in svc and '"recent-login-required"' in ad
        and 'recentlySignedIn(' in ad,
    'piyasa katkıları silmeden önce geri çekiliyor': ad.index('setContributionOptIn(false') < ad.index('callDeleteAccount(token)'),
    'telefon verisi yalnız sunucu başarılıysa siliniyor': ad.index('if (!"ok".equals(result))') < ad.index('LocalAccountWipe.wipe(db.getWritableDatabase(), uid)'),
    'misafir için yalnız yerel silme': 'confirmGuestWipe' in ad and 'LocalAccountWipe.wipe(db.getWritableDatabase(), "")' in ad,
    'e-posta talebi yedek yol olarak duruyor': 'LegalDocumentActivity.requestDeletion(activity)' in ad,
    'giriş hesabı en son siliniyor': svc.index('deleteAuthUser(uid)') > svc.index("recursiveDelete(db.doc(`users/${uid}`))"),
    'koleksiyon grubu sorgularının indeksleri tanımlı': {('comments', 'user_id'), ('comments', 'reply_to_uid'),
        ('items', 'blocked_uid'), ('rows', 'uid'), ('owners', 'uid')} <= overrides,
    'gizlilik metni uygulama içi silmeyi anlatıyor': 'Ayarlar > "Hesabımı Sil"' in privacy and 'tek adımda silen bir işlem bulunmaz' not in privacy,
    'web sayfası adımları ve iletişimi içeriyor': 'Hesabımı Sil' in web and email in web and 'SİL' in web,
    'web sayfası Firebase Hosting ile yayınlanabilir': fb.get('hosting', {}).get('public') == 'hosting',
    'sayfada uydurma bilgi yok (telefon/adres/şirket)': not re.search(r'\+90|Tel:|Adres:|Ltd|A\.Ş\.', web),
}
for k, ok in checks.items():
    print(('PASS ' if ok else 'FAIL ') + k)
sys.exit(0 if all(checks.values()) else 1)
