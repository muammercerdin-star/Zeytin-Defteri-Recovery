"""Aşama 2 bağlantı kontrolü (statik): her ekranın kullandığı R.id'ler düzende var mı,
ekran kodunda emoji kaldı mı, ikon anahtarlarının drawable'ı var mı, eski kalıntı silindi mi.
Android derlemesi veya cihaz testi değildir."""
from pathlib import Path
import re, sys

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'app/src/main/java'
RES = ROOT / 'app/src/main/res'
APP = JAVA / 'com/zeytindefteri/app'

SCREENS = {
    'GardenDetailActivity': (APP / 'GardenDetailActivity.java', ['activity_garden_detail', 'item_action_tile']),
    'NewOperationActivity': (APP / 'NewOperationActivity.java', ['activity_new_operation']),
    'HarvestActivity': (JAVA / 'HarvestActivity.java', ['activity_harvest', 'include_grade_rows']),
    'SaleActivity': (JAVA / 'SaleActivity.java', ['activity_sale', 'include_grade_rows']),
}
EMOJI = re.compile('[\U0001F300-\U0001FAFF☀-➿⭐✅✎]')
failures = []
checks = 0

def check(ok, msg):
    global checks
    checks += 1
    if not ok:
        failures.append(msg)

def layout_ids(names):
    ids = set()
    for n in names:
        ids |= set(re.findall(r'@\+id/(\w+)', (RES / 'layout' / (n + '.xml')).read_text(encoding='utf8')))
    return ids

for name, (src, layouts) in SCREENS.items():
    code = src.read_text(encoding='utf8')
    ids = layout_ids(layouts)
    for used in sorted(set(re.findall(r'R\.id\.(\w+)', code))):
        check(used in ids, f'{name}: R.id.{used} düzende yok')
    check(not EMOJI.search(code), f'{name}: kodda emoji kaldı: {EMOJI.findall(code)[:5]}')
    for lay in layouts:
        check(not EMOJI.search((RES / 'layout' / (lay + '.xml')).read_text(encoding='utf8')), f'{lay}: düzende emoji var')

# İkon anahtarları → drawable
icons = (APP / 'LineIcons.java').read_text(encoding='utf8')
for drawable in re.findall(r'R\.drawable\.(\w+)', icons):
    check((RES / 'drawable' / (drawable + '.xml')).exists(), f'{drawable}.xml yok')
kinds = (APP / 'OperationKinds.java').read_text(encoding='utf8')
for key in set(re.findall(r'return "(\w+)";', kinds)):
    check(f'case "{key}"' in icons or key == 'note', f'LineIcons {key} anahtarını tanımıyor')

# Bahçe detayı: eski emoji ikon fonksiyonu ve eski kutular gitti, özet aynı kaynaktan
gd = (APP / 'GardenDetailActivity.java').read_text(encoding='utf8')
check('getOperationIcon' not in gd, 'eski getOperationIcon kaldı')
check('GardenAnalyticsRepository' in gd and 'totalGradeKg()' in gd and 's.revenue' in gd, 'sezon özeti Bahçe Karnesi kaynağını kullanmıyor')
check('getGardenOperations(gardenId, 0)' in gd, 'uyarı için sezonun tüm işlemleri taranmıyor')
check('note_draft' in gd and 'screen_owner' in gd, 'not taslağı / sahip durumu saklanmıyor')
for target in ['NewOperationActivity', 'HarvestActivity', 'ElekActivity', 'SaleActivity', 'AddGardenActivity',
               'GardenAnalyticsActivity', 'OperationHistoryActivity']:
    check(f'openForGarden({target}.class)' in gd, f'{target} bahçe kimliğiyle açılmıyor')
layout = (RES / 'layout/activity_garden_detail.xml').read_text(encoding='utf8')
check('tvVariety' not in layout and 'tvLocation' not in layout, 'eski çeşit/konum satırları kaldı')

# İşlem ekle: tür kutuları tek kaynaktan, kayıt yolu korunmuş, eski açılır tür listesi gitmiş
no = (APP / 'NewOperationActivity.java').read_text(encoding='utf8')
nol = (RES / 'layout/activity_new_operation.xml').read_text(encoding='utf8')
check('spinnerType' not in no and 'spinnerType' not in nol, 'eski spinnerType kaldı')
check('OperationKinds.TYPES' in no and 'OperationKinds.form(' in no, 'tür/form tek kaynaktan gelmiyor')
check('databaseHelper.addOperation(gardenId, type, date, quantity, unit, cost, note)' in no, 'kayıt çağrısı değişmiş')
check('syncOperationByLocalId' in no, 'bulut eşitleme kaldırılmış')
check('operation_type_index' in no and 'operation_date' in no, 'döndürmede tür/tarih saklanmıyor')
check('getStringExtra("operation_type")' in no and 'getLongExtra("garden_id"' in no, 'gelen seçim okunmuyor')
check('if (type.equals(' not in no, 'eski if-zinciri form kuralları kaldı')
man = (ROOT / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf8')
for act in ['NewOperationActivity', 'HarvestActivity', 'SaleActivity']:
    check(re.search(r'android:name="\.' + act + r'"[^>]*adjustResize', man) is not None, f'{act} klavyede yeniden boyutlanmıyor')

# Hasat: tek işlem, boylar, "Hasat" hareketi, son fiyat önerisi, ortak sayı okuyucu
hv = (JAVA / 'HarvestActivity.java').read_text(encoding='utf8')
check('db.beginTransaction()' in hv and 'db.setTransactionSuccessful()' in hv and 'db.endTransaction()' in hv, 'hasat tek işlemde değil')
check('databaseHelper.addHarvestGrade(' in hv and 'addOperation(gardenId, "Hasat"' in hv, 'hasat boyları/hareketi yazılmıyor')
check('getLastHarvestReferencePrice' in hv and 'syncHarvestByLocalId' in hv, 'son fiyat önerisi veya eşitleme kaldı')
check('GradeFormModel.parse(' in hv and 'private double readNumber' not in hv, 'hasat ortak sayı okuyucuyu kullanmıyor')
check('<include layout="@layout/include_grade_rows" />' in (RES / 'layout/activity_harvest.xml').read_text(encoding='utf8'), 'hasat ortak boy satırlarını kullanmıyor')

# Satış: kayıt yolu, izin varsayılan kapalı, eski tür listesi ve onay kutusu gitti, ödeme kuralı
sa = (JAVA / 'SaleActivity.java').read_text(encoding='utf8')
sl = (RES / 'layout/activity_sale.xml').read_text(encoding='utf8')
check('saleRepository.addSaleWithGrades(' in sa and 'deleteBuyerIfUnused' in sa and 'syncSaleByLocalId' in sa, 'satış kayıt yolu değişmiş')
check('realSale && checkMarketConsent.isChecked()' in sa, 'piyasa izni yalnızca gerçek satışta gönderilmiyor')
check(re.search(r'<Switch[^>]*checkMarketConsent[^>]*android:checked="false"', sl, re.S) is not None, 'piyasa izni varsayılan kapalı değil')
check('Alıcı adı ve telefon gibi kişisel bilgiler piyasa verisine dahil edilmez.' in sl, 'izin açıklaması değişmiş')
check('spinnerSaleKind' not in sa and 'spinnerSaleKind' not in sl and '<CheckBox' not in sl, 'eski tür listesi / onay kutusu kaldı')
check('GradeFormModel.received(payment, net' in sa, 'tahsilat ödeme seçeneğinden hesaplanmıyor')
check('Tahsilat net satıştan büyük olamaz' in sa and 'Vade satış tarihinden önce olamaz' in sa and 'Tarih aktif sezonda olmalı' in sa, 'satış doğrulamaları eksik')
check('GradeFormModel.parse(' in sa and ".replace(',', '.')" not in sa, 'satış eski sayı okuyucuyu kullanıyor (2.400 → 2,4 hatası)')
check('<include layout="@layout/include_grade_rows" />' in sl, 'satış ortak boy satırlarını kullanmıyor')

# Ortak boy satırları: altı boy, kimlikler tam
gr = (RES / 'layout/include_grade_rows.xml').read_text(encoding='utf8')
for k in ['Double', '1', '2', '3', '4', 'Oil']:
    check(all(f'@+id/{p}{k}"' in gr for p in ['etKg', 'etPrice', 'tvValue']), f'boy satırı eksik: {k}')

# Emoji tür/yer tutucu ikonları: İşlem geçmişi ve Profil çizgi ikon kullanıyor
oh = (JAVA / 'OperationHistoryActivity.java').read_text(encoding='utf8')
check('operationIcon(' not in oh and 'LineIcons.forType(type)' in oh, 'işlem geçmişi hâlâ emoji ikon kullanıyor')
check(not EMOJI.search(oh), 'işlem geçmişinde emoji kaldı')
pr = (APP / 'ProfileActivity.java').read_text(encoding='utf8')
check(not EMOJI.search(pr), f'profilde emoji kaldı: {EMOJI.findall(pr)[:5]}')
check('applyAvatarGlyph(' in pr and 'R.drawable.ic_op_pin' in pr and 'R.drawable.ic_op_camera' in pr, 'profil çizgi ikonları bağlı değil')

# Yeni tasarımlı ekranlarda metinle çizilmiş eski "‹" geri düğmesi kalmamalı
NEW_SCREENS = [APP / 'GardenDetailActivity.java', APP / 'NewOperationActivity.java', JAVA / 'HarvestActivity.java',
               JAVA / 'SaleActivity.java', APP / 'ElekActivity.java', JAVA / 'ElekArchiveActivity.java',
               APP / 'ElekReceiptActivity.java', APP / 'SeasonReportView.java']
NEW_LAYOUTS = ['activity_garden_detail', 'activity_new_operation', 'activity_harvest', 'activity_sale',
               'activity_elek', 'activity_elek_archive', 'elek_archive_header', 'item_elek_archive']
for f in NEW_SCREENS:
    check('‹' not in f.read_text(encoding='utf8'), f'{f.name}: eski "‹" geri düğmesi kaldı')
for lay in NEW_LAYOUTS:
    check('‹' not in (RES / 'layout' / (lay + '.xml')).read_text(encoding='utf8'), f'{lay}: eski "‹" geri düğmesi kaldı')
check('"Satış kaydedildi ✓"' not in (APP / 'ElekReceiptActivity.java').read_text(encoding='utf8'), 'makbuz bildiriminde onay işareti kaldı')

if failures:
    print('\n'.join('HATA: ' + f for f in failures))
    sys.exit(1)
print(f'check_wiring: {checks} kontrol geçti')
