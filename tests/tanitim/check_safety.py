#!/usr/bin/env python3
"""Tanıtım modu güvencesi: örnek veriler buluta/piyasaya/yedeğe gitmez, kapatınca hepsi silinir."""
import pathlib, re, sys
root = pathlib.Path(__file__).resolve().parents[2]
J = root / 'app/src/main/java'
A = J / 'com/zeytindefteri/app'
fails = []
def read(p): return p.read_text(encoding='utf-8')
schema = read(J / 'DatabaseSchema.java')
modu = read(A / 'TanitimModu.java')
# 1) Sahip sütunlu her tablo kapatınca silinir; alt tablolar da.
tables = [m.group(1) for m in re.finditer(r'CREATE TABLE IF NOT EXISTS (\w+) \((.*?)\)"\s*\)', schema, re.S) if 'owner_uid' in m.group(2)]
for t in tables:
    if f'"{t}"' not in modu: fails.append('kapatınca silinmiyor: ' + t)
for child in ['elek_grades', 'sale_grades', 'harvest_grades', 'brine_events']:
    if f'DELETE FROM {child}' not in modu: fails.append('alt tablo silinmiyor: ' + child)
if 'app_settings' not in modu: fails.append('tanıtım ayarları silinmiyor')
# 2) Açıkken ekranlar yalnız örnek veriyi görür.
dh = read(J / 'DatabaseHelper.java')
if 'TanitimModu.active(appContext)' not in dh or 'return TanitimModu.OWNER;' not in dh: fails.append('aktif sahip tanıtıma geçmiyor')
if not re.search(r'OWNER\s*=\s*"[^"]+"', modu): fails.append('tanıtım sahibi boş olamaz (boş sahip kullanıcıya bağlanır)')
pf = read(A / 'ProducerFeedActivity.java')
if 'if (TanitimModu.active(this)) return TanitimModu.OWNER;   // tanıtımın kendi ayarları' not in pf:
    fails.append('ana sayfa seçili bahçe ayarı tanıtımda gerçek hesaba bakıyor')
if '!TanitimModu.OWNER.equals(getString(gardenCursor, "owner_uid", ""))' not in pf:
    fails.append('ana sayfa tanıtımda gerçek bahçeyi gösterebilir')
if 'if (TanitimModu.active(this)) return TanitimModu.NAME;' not in pf:
    fails.append('selamlamada gerçek ad görünebilir')
# 3) Bulut yolları satırı Google uid'iyle seçer (örnek sahip asla eşleşmez).
pc = read(A / 'ProductionCloudRepository.java')
if '"WHERE r.owner_uid = ? " +' not in pc or '"AND g.owner_uid = ? " +' not in pc: fails.append('eşitleme sahip süzgeci değişmiş')
sc = read(A / 'SupportCloudRepository.java')
if 'WHERE owner_uid=? AND cloud_id<>' not in sc: fails.append('alacak/destek eşitleme sahip süzgeci değişmiş')
mc = read(A / 'MarketCloudRepository.java')
if '&& uid.equals(user.getUid());' not in mc: fails.append('piyasa: uid = Google hesabı şartı kalkmış')
if 'TanitimModu.OWNER.equals(databaseHelper.getActiveOwnerUid())' not in mc: fails.append('piyasa katkısı tanıtımda kilitli değil')
tb = read(A / 'TezgahCloudBackup.java')
if tb.count('if (owner == null || !owner.equals(uid))') < 2: fails.append('yedek: hesap eşleşmesi şartı kalkmış')
# 5) Zeytin Ligi ve Sohbet tanıtımda gerçek kişileri göstermez, buluta yazmaz.
lv = read(A / 'ZeytinLeagueView.java')
if 'demo = TanitimModu.active(context);' not in lv or 'if (demo) { showDemo(); return; }' not in lv:
    fails.append('Zeytin Ligi tanıtımda gerçek sıralamayı yüklüyor olabilir')
if 'if (demo) return;   // tanıtımda bulut yok' not in lv or 'if (demo) { fillDemoRegions(); render(); return; }' not in lv:
    fails.append('Zeytin Ligi tanıtımda buluta bağlanıyor')
if 'if (demo) return;   // tanıtımda bölge raporu (bulut) açılmaz' not in lv: fails.append('tanıtımda bölge raporu bulutu açıyor')
if 'if (TanitimModu.active(this)) { loadDemoFeed(); return; }' not in pf: fails.append('Sohbet tanıtımda gerçek mesajları gösteriyor')
if 'tanıtımdan gerçek Meydan\'a mesaj gitmez' not in pf: fails.append('tanıtımda Sohbet\'ten mesaj gönderilebilir')
if 'if (TanitimModu.active(this)) return;   // tanıtımda gerçek kişilerin "yazıyor"' not in pf: fails.append('tanıtımda "yazıyor" gerçek adları gösterir')
if 'chatTyping == null || TanitimModu.active(ProducerFeedActivity.this)' not in pf: fails.append('tanıtımda "yazıyor" bilgisi gönderiliyor')
if 'boolean isChat = "MEYDAN".equals(currentSection) && !TanitimModu.active(this);' not in pf: fails.append('tanıtımda canlı Sohbet akışı açılıyor')
if 'if (p.id >= 0 && SocialReportManager.canReport(uid))' not in pf or 'reactionManager != null && p.id >= 0' not in pf:
    fails.append('tanıtım mesajı şikâyet/Faydalı ile buluta yazabilir')
if 'if (postId < 0) {   // tanıtım mesajı' not in pf: fails.append('tanıtım mesajı gerçek yanıt ekranını açıyor')
if 'static final String KULLANICI = "Ahmet Yılmaz";' not in read(A / 'TanitimVerisi.java') or 'static final String NAME = "Ahmet Yılmaz";' not in modu:
    fails.append('lig tanıtım kullanıcısı adı TanitimModu.NAME ile aynı değil')
# 4) Silme ham SQL: bulut silme kaydı (tombstone) yazılmaz.
if 'sync_tombstones' in modu and 'insert' in modu.lower(): fails.append('tanıtım tombstone yazıyor olabilir')
if fails:
    print('\n'.join(fails)); sys.exit(1)
print(f'PASS: tanıtım güvencesi ({len(tables)} sahipli tablo silinir; bulut/piyasa/yedek kilitleri yerinde)')
