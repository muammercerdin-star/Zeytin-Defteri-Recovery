"""Bölge raporu elek fiyatları: ekran ve veri katmanı bağlantıları (statik). Android derlemesi değildir."""
from pathlib import Path
import re, sys
ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'app/src/main/java/com/zeytindefteri/app'
d = (APP / 'ZeytinLeagueRegionReportDialog.java').read_text(encoding='utf8')
m = (APP / 'MarketCloudRepository.java').read_text(encoding='utf8')
sql = (ROOT / 'supabase/ZEYTIN-LIGI-BOLGE-RAPORU-PROD-5.sql').read_text(encoding='utf8')
fails, n = [], 0
def check(ok, msg):
    global n; n += 1
    if not ok: fails.append(msg)
bind = d[d.index('private void bindReport('):d.index('private LinearLayout metricCard(')]
card = d[d.index('private LinearLayout gradeCard('):d.index('private LinearLayout factCard(')]
detail = d[d.index('private void showDetailedGradeList('):d.index('private MarketCloudRepository.LeagueRegionReport fallbackFromCard(')]
check('"Elek fiyatları • medyan"' in bind, 'fiyat bölümü başlığı yok')
check(bind.index('Elek fiyatları') < bind.index('sezon özeti'), 'fiyatlar raporun en üstünde değil')
check('start += 3' in bind and 'gradeCard(grades.get(i), report.dominantGrade)' in bind, 'kutular 3x2 değil')
check('Elek Dağılımı' not in d and 'maxKg' not in d and '"— kg"' not in card, 'eski kg kutusu kalıntısı var')
check('RegionGradePrice.priceText(grade.producerCount, grade.medianPrice, grade.averagePrice)' in card, 'fiyat RegionGradePrice ile yazılmıyor')
check('RegionGradePrice.footText(' in card and 'RegionGradePrice.visible(grade.producerCount)' in card, 'eşik/alt satır bağlı değil')
check('averagePrice) + "₺"' not in d, 'eşik kontrolsüz fiyat kalıntısı')
check('LeagueRegionReportChartView' not in bind and 'LeagueRegionReportChartView' in detail, 'grafik detaylı listeye taşınmadı')
check('new ScrollView(context)' in detail, 'detaylı liste kaydırılamıyor')
parse = m[m.index('JSONArray grades = item.optJSONArray("grade_breakdown");'):m.index('JSONArray chart = item.optJSONArray("chart");')]
check(re.search(r'if \(RegionGradePrice\.visible\(grade\.producerCount\)\) \{\s*if \(!row\.isNull\("average_price"\)\)', parse) is not None,
      'veri katmanı eşik altındaki fiyatı tutuyor')
check(parse.index('grade.producerCount =') < parse.index('RegionGradePrice.visible'), 'üretici sayısı fiyat kontrolünden önce okunmuyor')
# Sunucu tanımı: elek fiyatları eşikle gönderilmeli
grade_json = sql[sql.index('grade_json as ('):sql.index(') as value', sql.index('grade_json as ('))]
check("when coalesce(gs.pc, 0) >= 1 then round(gs.balanced_average" not in grade_json
      and "when coalesce(gs.pc, 0) >= 1 then round(gs.balanced_median" not in grade_json, 'SQL eşik altında elek fiyatı gönderiyor')
check(grade_json.count('coalesce(gs.pc, 0) >= v_threshold then round(gs.balanced_') == 2, 'SQL elek fiyatları eşiğe bağlı değil')
if fails:
    print('\n'.join('HATA: ' + f for f in fails)); sys.exit(1)
print(f'check_wiring: {n} kontrol geçti (statik)')
