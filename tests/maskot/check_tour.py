#!/usr/bin/env python3
"""Tanecik turu bağlantıları: hedef etiketleri ekranda, rapor tur kipinde okuyup döner, Ayarlar'dan başlar."""
import pathlib, sys
root = pathlib.Path(__file__).resolve().parents[2]
A = root / 'app/src/main/java/com/zeytindefteri/app'
pf = (A / 'ProducerFeedActivity.java').read_text(encoding='utf-8')
ba = (A / 'BuyersActivity.java').read_text(encoding='utf-8')
sv = (A / 'SettingsView.java').read_text(encoding='utf-8')
wk = (A / 'TanecikWalker.java').read_text(encoding='utf-8')
fails = []
for tag in ['TAG_TAKVIM', 'TAG_HAVA', 'TAG_MIKROFON']:
    if f'setTag(TanecikTour.{tag})' not in pf: fails.append('hedef etiketi konmamış: ' + tag)
if 'void startTanecikTour()' not in pf or 'EXTRA_TOUR, true' not in pf: fails.append('tur başlatma/rapor açma yok')
if '&& ("ELEK".equals(currentSection) || "GARDENS".equals(currentSection)\n                || "REPORTS".equals(currentSection) || "MEYDAN".equals(currentSection)));' not in pf:
    fails.append('Elek/Bahçeler/Lig/Sohbet adımında Tanecik görünmez')
if 'tourMode && was && !on && tourSpoke' not in ba or 'Tura dön' not in ba: fails.append('rapor tura dönmüyor')
if 'startTanecikTour()' not in sv: fails.append('Ayarlar\'da tur satırı yok')
if 'if (tour != null) {' not in wk or 'runTourStep' not in wk: fails.append('dönüşte tur sürmüyor')
import re
ids = set(re.findall(r'android:id="@\+id/(\w+)"', (root / 'app/src/main/res/layout/activity_elek.xml').read_text(encoding='utf-8')))
tour = (A / 'TanecikTour.java').read_text(encoding='utf-8')
for name in re.findall(r'new GuideStep\("(\w+)"', tour):
    if name not in ids: fails.append('Elek gösterimi olmayan alanı arıyor: ' + name)
if 'item.setTag(TanecikTour.NAV + section)' not in pf: fails.append('alt menü düğmeleri etiketli değil')
el = (A / 'ElekActivity.java').read_text(encoding='utf-8')
if 'new TanecikGuide(this, TanecikTour.elekGuide())' not in el or 'tanecikGuide.release()' not in el: fails.append('Elek gösterimi bağlı değil')
gd = (A / 'TanecikGuide.java').read_text(encoding='utf-8')
if 'performClick' in gd or 'btnSaveElek' in gd: fails.append('gösterim Kaydet\'e basıyor olabilir')
if 'e.setText("")' not in gd: fails.append('gösterimde yazılan silinmiyor')
th = (A / 'TezgahHomeActivity.java').read_text(encoding='utf-8')
for tag in ['TZ_START', 'TZ_TODAY', 'TZ_BOOK', 'TZ_WEB']:
    if f'setTag(TanecikTour.{tag})' not in th: fails.append('Tezgâh hedef etiketi konmamış: ' + tag)
if 'new TanecikGuide(this, TanecikTour.tezgahGuide())' not in th or 'tanecikGuide.release()' not in th: fails.append('Tezgâh gösterimi bağlı değil')
if 'if (!tour) RoleActivity.setLastView(this, UserRole.VIEW_TEZGAH);' not in th: fails.append('tur Tezgâh\'ı son ekran yapıyor')
if 'if (tour) return;   // gösterim' not in th: fails.append('turda bilgisayar servisi/izin başlıyor')
if 'openTezgahGuide()' not in pf or 'TourHost' not in wk or 'tourHost.openTezgahGuide()' not in wk: fails.append('tur Tezgâh\'ı açmıyor')
if 'findViewWithTag(name)' not in gd: fails.append('gösterim etiketle hedef bulamıyor')
if 'startSwipe()' not in wk or 'tourHost.scroller()' not in wk or 'stopSwipe();' not in wk: fails.append('elle kaydırma bağlı değil')
if 'private View tourScroller()' not in pf: fails.append('bölümün kaydırma alanı bulunmuyor')
if 'if (!motionAllowed() || tourHost == null) return 0;' not in wk: fails.append('animasyon kapalıyken kaydırma yapılmamalı')
if fails:
    print('\n'.join(fails)); sys.exit(1)
print('PASS: Tanecik turu bağlantıları')
