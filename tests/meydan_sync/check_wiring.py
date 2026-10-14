"""Meydan mesaj bağlantıları (statik): silme buluta yazılıyor mu, canlı akış bağlı mı, durum etiketi var mı.
Android derlemesi ve cihaz testi değildir."""
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[2]
J = ROOT / 'app/src/main/java'
A = J / 'com/zeytindefteri/app'
scr = (J / 'SocialCloudRepository.java').read_text(encoding='utf8')
feed = (A / 'ProducerFeedActivity.java').read_text(encoding='utf8')
prof = (A / 'ProfileActivity.java').read_text(encoding='utf8')
fm = (J / 'FeedManager.java').read_text(encoding='utf8')
rules = (ROOT / 'firestore.rules').read_text(encoding='utf8')
fails, n = [], 0
def check(ok, msg):
    global n; n += 1
    if not ok: fails.append(msg)
def part(src, start, end):
    i = src.index(start); return src[i:src.index(end, i + len(start))]

imp = part(scr, 'private void importCloudPost(', 'databaseHelper.upsertPostFromCloud(')
check('MeydanPostSync.importAction(' in imp and 'isPostLocalChangePending(cloudId)' in imp, 'içe alma bekleyen yerel değişikliğe bakmıyor')
check('Action.DEACTIVATE' in imp and 'deactivatePostsFromCloud(' in imp, 'bulutta silinen gönderi kapatılmıyor')
check('Action.SKIP' in imp, 'telefonda silinen gönderi diriltilebilir')

dele = part(scr, 'public void publishPostDeletion(', 'private void markPostSynced(')
check('.update(change)' in dele and 'change.put("is_active", 0)' in dele and 'change.put("updated_at"' in dele,
      'silme buluta is_active=0 olarak yazılmıyor')
check('.delete()' not in dele, 'belge kalıcı silinmemeli (yorumlar ve bildirim kuralı is_active kullanıyor)')
check('owner.equals(user.getUid())' in dele, 'başkasının gönderisi için bulut silmesi denenebilir')
check('Code.NOT_FOUND' in dele, 'bulutta olmayan belge sonsuza dek yeniden denenir')
check(dele.count('pendingDeletes.remove(postId)') >= 3, 'aynı silme ikinci kez başlatılamaz hâle gelebilir')
# Kural tarafı: sahibin is_active/updated_at güncellemesi izinli alanlarda.
pf = part(rules, 'function postFields()', '}')
check("'is_active'" in pf and "'updated_at'" in pf, 'firestore.rules is_active/updated_at güncellemesine izin vermiyor')

retry = part(scr, 'public void retryPendingPosts()', 'public void publishPostDeletion(')
check('!isPostPending(id)) retryPendingPosts()' in retry, 'yeniden deneme senkron olmayan gönderide döngüye girebilir')
check('getPendingPostDeletionIds(user.getUid())' in retry and 'publishPostDeletion(' in retry, 'bekleyen silmeler yeniden denenmiyor')

live = part(scr, 'public void startLiveSync(', 'public void stopLiveSync()')
check('addSnapshotListener(' in live and '.limit(SYNC_LIMIT)' in live, 'canlı dinleyici yok')
check('Type.REMOVED' in live, 'pencereden çıkan belge silinmiş sayılmamalı (REMOVED içe alınmaz)')
check('reconcileWindow(' in live and 'fromServer' in live, 'canlı akış eşleştirme yapmıyor')
check('changes.isEmpty() && !firstServer' in live, 'metadata olayları ekranı gereksiz yeniliyor')
rec = part(scr, 'private void reconcileWindow(', 'private void complete(')
check('fromCache' in rec and 'missingFromCloud(' in rec, 'eşleştirme önbellek görüntüsünü ayırmıyor')
check('Source.SERVER' in rec and '!doc.exists()' in rec and 'importCloudPost(doc)' in rec,
      'eksik gönderi sunucudan doğrulanmadan kapatılıyor')
check('RECONCILE_BATCH' in rec, 'doğrulama sayısı sınırsız')
check('stopLiveSync();' in part(scr, 'public void close()', '\n'), 'close dinleyiciyi kapatmıyor')
sync = part(scr, 'public void syncLatestPosts(', 'private void importCloudPost(')
check('reconcileWindow(' in sync, 'tek seferlik senkron eşleştirme yapmıyor')

resume = part(feed, 'protected void onResume()', 'private View buildAppShell()')
check('startLiveSync(' in resume and 'syncLatestPosts(' not in resume, 'Sohbet canlı akışa bağlı değil')
check('stopLiveSync()' in part(feed, 'protected void onPause()', 'super.onPause();'), 'arka planda dinleme sürüyor')
check('liveFeedFirstEvent' in feed and 'private void onLiveFeedChanged()' in feed, 'ilk/sonraki canlı olay ayrımı yok')
send = part(feed, 'private void sendMeydanText(', 'private void showMeydanTopicPicker()')
check('createTextPost(text, activeType, sent ->' in send, 'gönderim sonucu arayüze dönmüyor')
check('Buluta gönderim bekleniyor' not in send, 'eski bekleme mesajı kaldı')
card = part(feed, 'private View buildPostCard(', 'TextView metadata =')
check('p.syncState == MeydanPostSync.PENDING' in card and 'Gönderiliyor…' in card and 'own &&' in card,
      'kendi bekleyen mesajında "Gönderiliyor…" yok')
check('item.syncState' in fm and '"sync_state"' in fm, 'FeedManager sync_state okumuyor')

pdel = part(prof, 'private void deletePost(', 'refreshProfile();')
check('publishPostDeletion(post.id' in pdel, 'profilden silme buluta gitmiyor')
check('postCloud.close()' in prof, 'profil bulut deposunu kapatmıyor')

print(f'check_wiring: {n} kontrol, {len(fails)} hata')
for f in fails: print('  HATA:', f)
sys.exit(1 if fails else 0)
