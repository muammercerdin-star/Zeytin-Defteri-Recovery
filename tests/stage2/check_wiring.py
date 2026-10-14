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
for target in ['NewOperationActivity', 'HarvestActivity', 'ElekActivity', 'SaleActivity', 'GardenEditActivity',
               'GardenAnalyticsActivity', 'GardenOperationHistoryActivity']:
    check(f'openForGarden({target}.class)' in gd, f'{target} bahçe kimliğiyle açılmıyor')
layout = (RES / 'layout/activity_garden_detail.xml').read_text(encoding='utf8')
check('tvVariety' not in layout and 'tvLocation' not in layout, 'eski çeşit/konum satırları kaldı')

if failures:
    print('\n'.join('HATA: ' + f for f in failures))
    sys.exit(1)
print(f'check_wiring: {checks} kontrol geçti')
