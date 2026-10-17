"""Sesli asistan paneli (statik): tasarım bağlantıları ve eski kalıntılar. Android derlemesi ve cihaz testi değildir."""
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[2]
A = ROOT / 'app/src/main/java/com/zeytindefteri/app'
c = (A / 'ZeytinAssistantController.java').read_text(encoding='utf8')
w = (A / 'AssistantVoiceWaveView.java').read_text(encoding='utf8')
fails, n = [], 0
def check(ok, msg):
    global n; n += 1
    if not ok: fails.append(msg)
def part(start, end):
    i = c.index(start); return c[i:c.index(end, i + len(start))]
post = part('private void post(', 'private void speak()')
check('ZeytinAssistantText.humanDates(raw' in post and 'lastAnswer=shown' in post and 'answer.setText(shown)' in post,
      'okunur tarih ekranda ve sesli okumada aynı metne uygulanmıyor')
check('ZeytinAssistantText.source(' in post, 'cevap kaynağı etiketi yok')
check('ZeytinAssistantText.followUps(' in post, 'hazır sorular yok')
check('tts.speak(lastAnswer' in c, 'sesli okuma ekrandaki metni okumuyor')
check('LANG_MISSING_DATA' in c and 'ttsLanguageMissing' in c and 'openTtsSettings' in c, 'Türkçe ses paketi uyarısı yok')
for t in ['Mikrofon izni gerekiyor', 'Mikrofon izni kapalı', 'Seni tam anlayamadım', 'Ses tanıma bağlantısı kurulamadı']:
    check(t in c, f'durum kartı yok: {t}')
check('"Durdur"' in c and '"Yaz"' in c and '"Sesle sor"' in c and '"Tekrar sor"' in c, 'tasarımdaki düğmeler eksik')
check('FLAG_NOT_TOUCH_MODAL' in c, 'alt menüdeki küreye dokunulamaz')
check('new Button(' not in c and 'AlertDialog' not in c and 'Color.WHITE' not in c and 'panelButton' not in c,
      'eski panel kalıntısı (standart düğmeler / AlertDialog / düz beyaz yazı) kaldı')
check('wave.rms(rms)' in c and 'host.rms(rms)' in c, 'ses dalgası ya da küre gerçek ses düzeyine bağlı değil')
check('Math.random' not in w and 'WEIGHTS.length' in w, 'dalga rastgele ya da eski 5 çubuk')
# Güvenlik ve kapsam: cevap motoru değişmedi.
check('data.answer(cmd,resolution.garden,gardens,all)' in c and 'ZeytinAssistantCommand.resolve(raw,gardens,active' in c,
      'cevap motoru / bahçe çözümü değişmiş')
check('requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}' in c, 'mikrofon izni akışı yok')
for icon in ['ic_op_mic', 'ic_op_keyboard', 'ic_op_speaker', 'ic_op_stop', 'ic_op_close', 'ic_op_send']:
    check((ROOT / f'app/src/main/res/drawable/{icon}.xml').is_file() and f'R.drawable.{icon}' in c, f'{icon} eksik ya da kullanılmıyor')
print(f'check_wiring: {n} kontrol, {len(fails)} hata')
for f in fails: print('  HATA:', f)
sys.exit(1 if fails else 0)
