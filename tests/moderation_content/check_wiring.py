"""Moderatör içerik kaldırma: Android bağlantıları (statik). Android derlemesi ve cihaz testi değildir."""
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[2]
A = ROOT / 'app/src/main/java/com/zeytindefteri/app'
act = (A / 'ModerationActivity.java').read_text(encoding='utf8')
com = (ROOT / 'app/src/main/java/CommentCloudRepository.java').read_text(encoding='utf8')
dbh = (ROOT / 'app/src/main/java/DatabaseHelper.java').read_text(encoding='utf8')
fails, n = [], 0
def check(ok, msg):
    global n; n += 1
    if not ok: fails.append(msg)
def part(src, start, end):
    i = src.index(start); return src[i:src.index(end, i + len(start))]
det = part(act, 'private void details(', 'private void loadContent(')
lc = part(act, 'private void loadContent(', 'private void setBadge(')
check('operation("moderationGetReportedContent")' in lc, 'detay içeriği sunucudan okumuyor')
check('removeButton.setEnabled(false)' in det and 'removeButton.setEnabled(data.removable())' in lc,
      'kaldırma düğmesi içerik görülmeden açık')
check('ModerationPage.hasContent(row)' in det, 'kullanıcı şikâyetinde kaldırma düğmesi çıkabilir')
check('detailRow!=row' in lc and 'current(request)' in lc, 'başka şikâyete geçilince geç yanıt yazılabilir')
check('BackCompat.register(this,this::showList)' in det and 'public void onBackPressed()' in act,
      'detaydan geri tuşuyla listeye dönülmüyor (Android 13+ ve altı)')
check('setTextIsSelectable(true)' in det and 'ids.setVisibility(View.GONE)' in det, 'kimlikler önde ya da kopyalanamıyor')
check('update(row,"REJECTED")' in det, 'tek dokunuşla reddetme yok')
conf = part(act, 'private void confirmRemove(', 'private void remove(')
check('"Kaldır"' in conf and '"Vazgeç"' in conf, 'kaldırma onay penceresi yok')
check('Gravity.BOTTOM' in conf and 'FLAG_SECURE' in conf, 'onay alttan açılmıyor ya da ekran görüntüsüne açık')
check('Yanıt kaldırılsın mı?' in conf and 'ana gönderi yerinde kalır' in conf, 'yanıt kaldırmada ana gönderi uyarısı yok')
rem = part(act, 'private void remove(', 'private void choose(')
check('operation("moderationRemoveContent")' in rem and 'ModerationPage.remove(row)' in rem, 'kaldırma çağrısı yok')
check('failure(error,true)' in rem and 'Code.NOT_FOUND' in rem, 'kaldırma hataları işlenmiyor')
check('reload()' in rem, 'kaldırmadan sonra liste yenilenmiyor')
page = (A / 'ModerationPage.java').read_text(encoding='utf8')
check('"←  Moderasyon Merkezi"' not in act and 'action("Yenile"' not in act and 'Theme_Material_Dialog_Alert).setTitle("Şikâyet detayı")' not in act,
      'eski tasarım kalıntısı (düz düğmeler / detay penceresi) kaldı')
check('contentText(' not in page and 'static boolean removable(Object' not in page, 'eski içerik metni yardımcıları kaldı')
for icon in ['ic_op_refresh', 'ic_op_flag', 'ic_op_user', 'ic_op_eye_off', 'ic_op_code']:
    check((ROOT / f'app/src/main/res/drawable/{icon}.xml').is_file() and f'R.drawable.{icon}' in act, f'{icon} eksik ya da kullanılmıyor')
check('FirebaseFirestore' not in act, 'Moderasyon Merkezi Firestore\'a doğrudan erişiyor (sunucu sınırı)')
imp = part(com, 'private void importReply(', 'public void stop()')
check('!cloudRemoved(doc)' in imp, 'bulutta kapatılan yanıt yerel bekleyen değişiklikle ezilebilir')
check('(is_active = 1 OR sync_state = 1)' in dbh, 'bulutta kapalı gönderinin bekleyen yazması sonsuza dek denenir')
print(f'check_wiring: {n} kontrol, {len(fails)} hata')
for f in fails: print('  HATA:', f)
sys.exit(1 if fails else 0)
