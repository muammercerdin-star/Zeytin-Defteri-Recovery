"""Sohbet (Meydan) yeniden tasarımı: bağlantılar ve eski kod kalıntıları (statik). Android derlemesi değildir."""
from pathlib import Path
import re, sys
ROOT = Path(__file__).resolve().parents[2]
J = ROOT / 'app/src/main/java'
A = J / 'com/zeytindefteri/app'
f = (A / 'ProducerFeedActivity.java').read_text(encoding='utf8')
rm = (J / 'ReactionManager.java').read_text(encoding='utf8')
fails, n = [], 0
def check(ok, msg):
    global n; n += 1
    if not ok: fails.append(msg)
def method(name, nxt):
    return f[f.index(name):f.index(nxt, f.index(name))]
EMOJI = re.compile('[\U0001F300-\U0001FAFF☀-➿⭐▥☰➤]')
section = method('private View buildMeydanSection()', 'private View buildMeydanHeader()')
header = method('private View buildMeydanHeader()', 'private View buildCategoryRow()')
cats = method('private View buildCategoryRow()', 'private View buildChatSectionHeader()')
composer = method('private View buildComposer()', 'private void sendMeydanText(')
strip = method('private View buildMarketStrip()', 'private void loadFeed()') if 'private void loadFeed()' in f[f.index('private View buildMarketStrip()'):] else ''
card = method('private View buildPostCard(', 'private View feedThreadAvatar(')
# Başlık
check('|| "MEYDAN".equals(currentSection))' in f, 'ortak büyük başlık Sohbet\'te gizlenmiyor')
check('"Sohbet"' in header and 'showSection("PROFILE")' in header, 'Sohbet başlığı / profil bağlantısı yok')
check(section.index('buildMeydanHeader()') < section.index('buildCategoryRow()') < section.index('buildMarketStrip()') < section.index('buildChatSectionHeader()'),
      'bölüm sırası tasarımdan farklı')
# Kategoriler ve emoji
for name, part in [('kategoriler', cats), ('yazma alanı', composer), ('mesaj kartı', card), ('başlık', header)]:
    check(not EMOJI.search(part), f'{name}: emoji/glif kaldı: {EMOJI.findall(part)[:4]}')
for icon in ['ic_op_chat', 'ic_op_tag', 'ic_op_basket', 'ic_op_trap', 'ic_op_water', 'ic_op_truck']:
    check(f'R.drawable.{icon}' in cats, f'kategori ikonu yok: {icon}')
for icon in ['ic_op_send', 'ic_op_reply', 'ic_op_chart', 'ic_op_chevron', 'ic_op_down']:
    check((ROOT / f'app/src/main/res/drawable/{icon}.xml').exists(), f'{icon}.xml yok')
# Yazma alanı: seçili konu görünür
check('composerTopic = text(chatTitleForActiveType()' in composer and 'showMeydanTopicPicker()' in composer, 'konu düğmesi yok')
check('composerTopic.setText(chatTitleForActiveType())' in f, 'konu değişince düğme güncellenmiyor')
check('"#Genel Sohbet"' not in f and 'return "#Genel";' in f, 'başlık adları eski')
# Piyasa şeridi: kendi ortalaman değil, Supabase bölge istatistiği + eşik
check('getMarketAveragePrice' not in f and 'getMarketAveragePrice' not in (J / 'MarketRepository.java').read_text(encoding='utf8'),
      'eski "kendi ortalaman" şeridi kalıntısı')
check('cloud.loadLeagueRegions(year, "1 No"' in f and 'MeydanMarketStrip.of(' in f, 'şerit bölge istatistiğine bağlı değil')
check('new ZeytinLeagueRepository().local(helper, year, uid)' in f, 'bölge, Zeytin Ligi kuralıyla bulunmuyor')
check('reportsSectionCache.openMarketTab()' in f and 'public void openMarketTab()' in (A / 'ZeytinLeagueContainerView.java').read_text(encoding='utf8'),
      'şerit Zeytin Ligi → Piyasa açmıyor')
check('serial != marketStripSerial' in f, 'eski isteğin sonucu yeni ekranı ezebilir')
# Mesaj kartı
check('YANITLA' not in card and 'feedThreadClock.format(new Date(at))' not in card, 'eski YANITLA / çift saat kalıntısı')
check('buildFeedThreadRow' not in f and 'Tüm yanıtları gör' not in f, 'eski satır düzeni kalıntısı')
check('reactionManager.setupReactionButton(useful, p.id, currentUserKey())' in card, 'Faydalı düğmesi bağlı değil')
check('SocialReportManager.canReport(uid)' in card and 'SocialBlockUi.confirm(' in card, 'şikâyet/engelle menüsü kayboldu')
check('openFeedThread(p.id, "", true)' in card, 'Yanıtla yazma alanını açmıyor')
# Ölü kod
for dead in ['buildMeydanPulse', 'refreshMeydanPulse', 'meydanPulseHost', 'calculateMeydanStats', 'MeydanStats',
             'buildMeydanSummaryCard', 'buildMeydanRegionCard', 'buildMeydanWaitingCard']:
    check(dead not in f, f'ölü kod kaldı: {dead}')
# Faydalı: sayı yok (tepkiler yalnız bu telefonda)
txt = rm[rm.index('public String getReactionButtonText('):rm.index('\n    }\n', rm.index('public String getReactionButtonText('))]
check('totalCount' not in txt.split(') {', 1)[1] and 'return getReactionLabel(' in txt, 'Faydalı sayısı hâlâ gösteriliyor')
check('getReactionEmoji' not in rm, 'kullanılmayan emoji yardımcısı kaldı')
check('d.mutate().setTint(color)' in rm, 'Faydalı ikonu seçilince renklenmiyor')
# 7 menü korunur
nav = re.findall(r'bottomNav\.addView\(navItem\([^,]+, "([^"]+)"', f)
check(nav == ['Ana Sayfa', 'Bahçelerim', 'Elek', 'Zeytin Ligi', 'Sohbet', 'Ayarlar'] and 'bottomNav.addView(assistantItem' in f, f'alt menü değişmiş: {nav}')
if fails:
    print('\n'.join('HATA: ' + x for x in fails)); sys.exit(1)
print(f'check_wiring: {n} kontrol geçti (statik)')
