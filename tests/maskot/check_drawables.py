#!/usr/bin/env python3
"""Tanecik çizimleri: üreticiyle aynı mı, VectorDrawable kurallarına uyuyor mu, koddaki her poz var mı."""
import pathlib, re, subprocess, sys, tempfile, shutil, xml.dom.minidom
root = pathlib.Path(__file__).resolve().parents[2]
res = root / 'app/src/main/res/drawable'
gen = root / 'tools/maskot/gen_tanecik.py'
fails = []
# 1) Üretici çıktısı commit edilenle aynı (elle değiştirilmemiş)
with tempfile.TemporaryDirectory() as t:
    tr = pathlib.Path(t)
    (tr / 'tools/maskot').mkdir(parents=True); (tr / 'app/src/main/res/drawable').mkdir(parents=True)
    shutil.copy(gen, tr / 'tools/maskot/gen_tanecik.py')
    subprocess.run([sys.executable, str(tr / 'tools/maskot/gen_tanecik.py')], check=True, capture_output=True)
    for f in (tr / 'app/src/main/res/drawable').glob('tanecik_*.xml'):
        if f.read_text(encoding='utf-8') != (res / f.name).read_text(encoding='utf-8'):
            fails.append('üreticiyle farklı: ' + f.name)
# 2) Kodda kullanılan her R.drawable.tanecik_* dosyası var
used = set()
for j in (root / 'app/src/main/java').rglob('*.java'):
    used |= set(re.findall(r'R\.drawable\.(tanecik_\w+)', j.read_text(encoding='utf-8')))
for u in sorted(used):
    if not (res / (u + '.xml')).exists(): fails.append('eksik çizim: ' + u)
if not used: fails.append('maskot hiç kullanılmıyor')
# 3) VectorDrawable kuralları
ok_attrs = {'android:pathData', 'android:fillColor', 'android:fillAlpha', 'android:strokeColor', 'android:strokeWidth',
            'android:strokeLineCap', 'android:strokeLineJoin', 'android:strokeAlpha'}
for f in sorted(res.glob('tanecik_*.xml')):
    d = xml.dom.minidom.parse(str(f))
    v = d.documentElement
    if v.tagName != 'vector': fails.append(f.name + ': kök vector değil')
    for p in d.getElementsByTagName('path'):
        for k in p.attributes.keys():
            if k not in ok_attrs: fails.append(f'{f.name}: bilinmeyen öznitelik {k}')
        data = p.getAttribute('android:pathData')
        if not re.fullmatch(r'[MmLlQqAaZzHhVv0-9.,\- ]+', data): fails.append(f'{f.name}: yol verisinde geçersiz karakter')
        for a in ('android:fillAlpha', 'android:strokeAlpha'):
            if p.hasAttribute(a) and not 0 <= float(p.getAttribute(a)) <= 1: fails.append(f'{f.name}: {a} aralık dışı')
if fails:
    print('\n'.join(fails)); sys.exit(1)
print(f'PASS: {len(list(res.glob("tanecik_*.xml")))} çizim, kodda {len(used)} poz kullanılıyor')
