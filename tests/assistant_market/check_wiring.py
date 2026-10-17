"""Asistan piyasa sorusu → Supabase bölge raporu (statik). Android derlemesi ve cihaz testi değildir."""
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[2]
A = ROOT / 'app/src/main/java/com/zeytindefteri/app'
c = (A / 'ZeytinAssistantController.java').read_text(encoding='utf8')
fails, n = [], 0
def check(ok, msg):
    global n; n += 1
    if not ok: fails.append(msg)
def part(start, end):
    i = c.index(start); return c[i:c.index(end, i + len(start))]
sub = part('private void submit(', 'private void advisorReply(')
check('cmd.kind==Kind.MARKET && !ZeytinAssistantMarket.ownSales(cmd.text)' in sub, 'piyasa sorusu bölge verisine yönlenmiyor')
check('new ZeytinLeagueRepository().local(helper,season,owner)' in sub and 'helper.close()' in sub, 'kendi bölgesi Zeytin Ligi kuralıyla bulunmuyor')
check('"Bölge belirtilmemiş".equals(regionName)' in sub, 'belirsiz bölge ayıklanmıyor')
rm = part('private void regionMarket(', 'private MarketCloudRepository marketRepository(')
check('cloud.canUseCloud()' in rm and '"Bölge fiyatlarına şu an ulaşılamıyor. "+fallback' in rm, 'bağlantı yokken kendi satışlarına açıkça düşmüyor')
check('loadLeagueRegionReport(season,id' in rm and 'ZeytinAssistantMarket.answer(' in rm, 'bölge raporu kullanılmıyor')
check('ZeytinAssistantMarket.regionFor(cmd.text,names)' in rm and 'unknownRegion()' in rm and 'noRegion()' in rm, 'bölge seçimi / bilinmeyen bölge yok')
check(rm.count('current(token,owner,season,active)') >= 3, 'geç gelen ağ cevabı yeni soruya yazılabilir')
check('ANONİM BÖLGE VERİSİ' in rm, 'kaynak etiketi yok')
check('marketDb.close()' in c, 'veritabanı bağlantısı kapatılmıyor')
print(f'check_wiring: {n} kontrol, {len(fails)} hata')
for f in fails: print('  HATA:', f)
sys.exit(1 if fails else 0)
