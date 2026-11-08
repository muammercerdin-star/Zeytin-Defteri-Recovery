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
if 'tanecik.touring() && "ELEK".equals(currentSection)' not in pf: fails.append('Elek adımında Tanecik görünmez')
if 'tourMode && was && !on && tourSpoke' not in ba or 'Tura dön' not in ba: fails.append('rapor tura dönmüyor')
if 'startTanecikTour()' not in sv: fails.append('Ayarlar\'da tur satırı yok')
if 'if (tour != null) {' not in wk or 'runTourStep' not in wk: fails.append('dönüşte tur sürmüyor')
if fails:
    print('\n'.join(fails)); sys.exit(1)
print('PASS: Tanecik turu bağlantıları')
