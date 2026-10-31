"""Asistan → elek formu bağlantısı: soru yolundan önce, yalnız yeni soruda; form kaydetmeden doldurur."""
import re, sys
from pathlib import Path
A = Path(__file__).resolve().parents[2] / 'app/src/main/java/com/zeytindefteri/app'
c = (A / 'ZeytinAssistantController.java').read_text(encoding='utf8')
f = (A / 'ProducerFeedActivity.java').read_text(encoding='utf8')
e = (A / 'ElekActivity.java').read_text(encoding='utf8')
fails = []
def check(ok, m):
    print(('PASS ' if ok else 'FAIL ') + m)
    if not ok: fails.append(m)
check(c.index('ElekVoiceEntry.parse(raw)') < c.index('if(cmd.kind==Kind.UNKNOWN)'), 'elek girişi soru yollarından önce denenir')
check('pick==null?ElekVoiceEntry.parse(raw):null' in c, 'etiketle yeniden sorulduğunda form açılmaz')
check('default void openElekEntry(' in c, 'Host varsayılan davranışı: boş elek formu')
check('public void openElekEntry(long gardenId, ElekVoiceEntry entry)' in f and 'EXTRA_VOICE_KG, entry.kg' in f, 'ana ekran kiloları forma gönderir')
body = e[e.index('private void applyVoiceEntry('):]
body = body[:body.index('\n    }\n') ]
check('saveElek' not in body and 'btnSaveElek.performClick' not in body and 'createSale' not in body, 'form doldurulur, kayıt/satış KENDİLİĞİNDEN yapılmaz')
check('savedInstanceState == null' in e[e.index('EXTRA_VOICE_KG)'):][:0] or 'savedInstanceState == null && getIntent() != null && getIntent().hasExtra(EXTRA_VOICE_KG)' in e, 'ekran dönünce tekrar doldurulmaz')
print('elek_voice wiring:', 'PASS' if not fails else f'{len(fails)} FAIL'); sys.exit(1 if fails else 0)
