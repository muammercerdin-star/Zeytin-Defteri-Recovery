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
det = part(act, 'private void details(', 'private void confirmRemove(')
check('operation("moderationGetReportedContent")' in det, 'detay içeriği sunucudan okumuyor')
check('setEnabled(false)' in det and 'setEnabled(ModerationPage.removable(data))' in det,
      'kaldırma düğmesi içerik görülmeden açık')
check('ModerationPage.hasContent(row)' in det, 'kullanıcı şikâyetinde kaldırma düğmesi çıkabilir')
check('!shown.isShowing()' in det and 'current(request)' in det, 'kapanmış pencereye geç yanıt yazılabilir')
conf = part(act, 'private void confirmRemove(', 'private void remove(')
check('"Kaldır"' in conf and '"Vazgeç"' in conf, 'kaldırma onay penceresi yok')
rem = part(act, 'private void remove(', 'private void choose(')
check('operation("moderationRemoveContent")' in rem and 'ModerationPage.remove(row)' in rem, 'kaldırma çağrısı yok')
check('failure(error,true)' in rem and 'Code.NOT_FOUND' in rem, 'kaldırma hataları işlenmiyor')
check('reload()' in rem, 'kaldırmadan sonra liste yenilenmiyor')
check('FirebaseFirestore' not in act, 'Moderasyon Merkezi Firestore\'a doğrudan erişiyor (sunucu sınırı)')
imp = part(com, 'private void importReply(', 'public void stop()')
check('!cloudRemoved(doc)' in imp, 'bulutta kapatılan yanıt yerel bekleyen değişiklikle ezilebilir')
check('(is_active = 1 OR sync_state = 1)' in dbh, 'bulutta kapalı gönderinin bekleyen yazması sonsuza dek denenir')
print(f'check_wiring: {n} kontrol, {len(fails)} hata')
for f in fails: print('  HATA:', f)
sys.exit(1 if fails else 0)
