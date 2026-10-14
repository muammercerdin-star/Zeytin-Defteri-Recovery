"""Aşama 3 bağlantı kontrolü (statik): ekranların kullandığı R.id'ler düzende var mı, emoji / eski geri
düğmesi / palet dışı renk kaldı mı, kayıt yolları korunmuş mu, silinen ekranlar gerçekten gitmiş mi.
Android derlemesi veya cihaz testi değildir."""
from pathlib import Path
import re, sys

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'app/src/main/java'
RES = ROOT / 'app/src/main/res'
APP = JAVA / 'com/zeytindefteri/app'
MAN = (ROOT / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf8')
EMOJI = re.compile('[\U0001F300-\U0001FAFF☀-➿]')
failures, checks = [], 0

def check(ok, msg):
    global checks
    checks += 1
    if not ok:
        failures.append(msg)

def ids(layouts):
    out = set()
    for n in layouts:
        out |= set(re.findall(r'@\+id/(\w+)', (RES / 'layout' / (n + '.xml')).read_text(encoding='utf8')))
    return out

# ---- Bahçe ekle / düzenle
ag = (APP / 'AddGardenActivity.java').read_text(encoding='utf8')
agl = (RES / 'layout/activity_add_garden.xml').read_text(encoding='utf8')
for used in sorted(set(re.findall(r'R\.id\.(\w+)', ag))):
    check(used in ids(['activity_add_garden']), f'AddGardenActivity: R.id.{used} düzende yok')
for src, name in [(ag, 'AddGardenActivity'), (agl, 'activity_add_garden')]:
    check(not EMOJI.search(src), f'{name}: emoji kaldı {EMOJI.findall(src)[:4]}')
    check('‹' not in src, f'{name}: eski "‹" geri düğmesi kaldı')
check(not re.search(r'"#[0-9A-Fa-f]{6}"', ag), 'AddGardenActivity: koda gömülü renk kaldı')
check('serif' not in agl and 'AutoCompleteTextView' not in agl and '<Spinner' not in agl, 'eski il/ilçe alanları veya sulama listesi kaldı')
check('gardenRepository.addGardenForOwner(' in ag and 'gardenRepository.addGarden(' in ag and 'syncGardenByLocalId' in ag, 'ekleme kayıt yolu değişmiş')
check('gardenRepository.updateGarden(editGardenId' in ag, 'düzenleme kayıt yolu yok')
check('markFirstSetupCompleted()' in ag and 'showFirstGardenSuccess()' in ag and '"first_setup_completed"' in ag, 'ilk bahçe akışı kaldırılmış')
check('GardenFormModel.checkLocation(' in ag and 'locationUnchanged()' in ag, 'konum kuralı modelden gelmiyor')
check('isSameOwner()' in ag and '"screen_owner"' in ag, 'düzenlemede sahip kontrolü yok')
check(re.search(r'android:name="\.AddGardenActivity"[^>]*adjustResize', MAN) is not None, 'Bahçe ekle klavyede yeniden boyutlanmıyor')
check(not (APP / 'GardenEditActivity.java').exists() and 'GardenEditActivity' not in MAN, 'eski GardenEditActivity duruyor')
gd = (APP / 'GardenDetailActivity.java').read_text(encoding='utf8')
check('openForGarden(AddGardenActivity.class)' in gd, 'Bahçe detayındaki Düzenle yeni formu açmıyor')
lp = (APP / 'LocationPickerActivity.java').read_text(encoding='utf8')
check('EXTRA_STREET' not in lp and 'START_STREET' not in lp, 'konum seçicide eski sokak uyumluluk kodu kaldı')

# ---- Bahçe Karnesi
ga = (APP / 'GardenAnalyticsActivity.java').read_text(encoding='utf8')
check(not EMOJI.search(ga) and '‹' not in ga and 'SERIF' not in ga and 'Color.rgb' not in ga, 'Bahçe Karnesi: emoji / eski geri / serif / gömülü renk kaldı')
check('new GardenAnalyticsRepository(helper).load(screenOwner, season, true)' in ga, 'Bahçe Karnesi veri kaynağı değişmiş')
check('ticket != generation' in ga and 'isSameOwner()' in ga, 'Bahçe Karnesi yarış/sahip korumaları eksik')
check('<Spinner' not in ga and 'new Spinner(' not in ga and 'scopeChips' in ga, 'kapsam seçimi hâlâ açılır liste')
for metric in ['Kayıtlı ağaç', 'Ağaç başı verim', 'Dekar başı verim', 'Kg başı maliyet', 'Satılan', 'Ortalama fiyat',
               'Tahsil edilen', 'Kalan alacak', 'Baskın boy', 'Özete dahil salamura masrafı', 'Özete dahil diğer alacak']:
    check(metric in ga, f'Bahçe Karnesi: "{metric}" kaybolmuş')
for row in ['"Hasat"', '"Masraf"', '"Satış geliri"', '"Ağaç başı verim"', '"Kg başı maliyet"', '"Satılan"', '"Ortalama fiyat"']:
    check(f'compare(card, {row}' in ga, f'karşılaştırma satırı kaybolmuş: {row}')
check('GRADE_LABELS[i], current.gradeKg(i), previous.gradeKg(i)' in ga, 'boy karşılaştırması kaybolmuş')
check('Geçmiş ağaç sayısı saklanmadığı için' in ga and 'Kaydetmediğin emek' in ga, 'Bahçe Karnesi açıklama notları kaybolmuş')

# ---- İşlem geçmişi
oh = (JAVA / 'OperationHistoryActivity.java').read_text(encoding='utf8')
check(not EMOJI.search(oh) and '‹' not in oh and 'new Spinner(' not in oh and 'Spinner ' not in oh, 'İşlem geçmişi: emoji / eski geri / açılır liste kaldı')
check('getOperationsFiltered(allGardens ? null : selectedGarden, null)' in oh and 'OperationHistoryModel.matches(selectedType' in oh,
      'tür filtresi Java tarafında (büyük/küçük harf hatası) yapılmıyor')
check('OperationHistoryModel.sortNewestFirst(rows)' in oh and 'monthLabel' in oh, 'ay gruplaması yok')
check('getLongExtra("garden_id"' in oh and '"history_garden"' in oh and '"history_type"' in oh, 'bahçe ile açılış veya durum saklama yok')
check('isSameOwner()' in oh, 'İşlem geçmişinde sahip kontrolü yok')
check('Gravity.BOTTOM' in oh and '"Kapat"' in oh and '"Not"' in oh, 'alttan detay paneli yok')
check('NewOperationActivity.class' in oh and 'setClassName' not in oh, 'yeni işlem açılışı')
check(not (APP / 'GardenOperationHistoryActivity.java').exists() and 'GardenOperationHistoryActivity' not in MAN, 'eski bahçe işlem geçmişi ekranı duruyor')
check('openForGarden(OperationHistoryActivity.class)' in gd, 'Bahçe detayındaki Tümü yeni ekranı açmıyor')
dbh = (JAVA / 'DatabaseHelper.java').read_text(encoding='utf8')
i = dbh.index('public Cursor getOperationsFiltered(')
check("COALESCE(o.owner_uid,'') = ? OR COALESCE(o.owner_uid,'') = ''" in dbh[i:i + 2500], 'filtreli sorguda kayıt sahibi koruması yok')

# ---- Profil
pr = (APP / 'ProfileActivity.java').read_text(encoding='utf8')
check(not EMOJI.search(pr) and '‹' not in pr and '"•••"' not in pr and '＋' not in pr, 'Profil: emoji / eski geri / metin menü / tam genişlik artı kaldı')
check('"Zeytinliğim"' not in pr and 'createShortBio' not in pr and 'setElevation' not in pr, 'Profil: eski kapak, bilgi kartı veya gölge kaldı')
check('R.drawable.bg_segment_item' in pr and 'R.color.segment_text' in pr, 'Profil sekmeleri bölmeli düğme değil')
for keep in ['showProfilePhotoMenu()', 'showEditProfileDialog()', 'showAccountMenu()', 'confirmLogout()',
             'reactionManager.setupReactionButton(', 'commentManager.showComments(', 'sharePost(post)',
             'showOwnPostMenu(post)', 'postMediaManager.addPostMediaToCard(', 'CreatePostActivity.class', 'MainActivity.class',
             'getOwnPosts()', 'applyProfilePhoto(ivProfilePhoto, tvProfilePhotoFallback)', '"Misafir oturumu"']:
    check(keep in pr, f'Profil davranışı kaybolmuş: {keep}')
check('sessionManager.isGuest() ? "Giriş yap" : "Profili düzenle"' in pr or 'guest ? "Giriş yap" : "Profili düzenle"' in pr, 'misafirde Giriş yap düğmesi yok')
check('Gönderi paylaşmak için hesapla giriş yapmalısın.' in pr, 'misafir gönderi uyarısı kaybolmuş')

if failures:
    print('\n'.join('HATA: ' + f for f in failures))
    sys.exit(1)
print(f'check_wiring: {checks} kontrol geçti')
