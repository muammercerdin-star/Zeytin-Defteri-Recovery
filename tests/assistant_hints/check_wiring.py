"""Asistan anlama katmanı (statik): panel bağlantıları. Android derlemesi ya da cihaz testi değildir."""
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[2]
A = ROOT / 'app/src/main/java/com/zeytindefteri/app'
c = (A / 'ZeytinAssistantController.java').read_text(encoding='utf8')
d = (A / 'ZeytinAssistantData.java').read_text(encoding='utf8')
fails, n = [], 0
def check(ok, msg):
    global n; n += 1
    if not ok: fails.append(msg)
def part(start, end):
    i = c.index(start); return c[i:c.index(end, i + len(start))]

run = part('private void run(', 'private void advisorReply(')
unknown = run[run.index('if(cmd.kind==Kind.UNKNOWN)'):run.index('if(cmd.kind==Kind.NAVIGATION)')]
# A) Yarım anlama: önce sabit cevaplar (doz, silme, selam), sonra öneriler.
check('ZeytinAssistantText.special(raw)' in unknown and 'ZeytinAssistantHints.hint(raw' in unknown
      and unknown.index('special(raw)') < unknown.index('ZeytinAssistantHints.hint('), 'özel cevaplar önerilerden önce gelmiyor')
check('data.recordedTopics(gardens)' in unknown, 'kaydı olmayan konu süzülmüyor')
check('resolution.explicit' in unknown, 'öneriye yalnızca soruda adı geçen bahçe taşınmalı')
check('recordedTopics(' in d and 'rawQuery' not in d and 'execSQL' not in d, 'kayıtlı konular yeni SQL ile okunuyor')
# B) Konu kartları ve "Konular" düğmesi.
check('"Konular"' in c and 'ZeytinAssistantHints.cards(' in c and 'showTopic(' in c, 'konu kartları / Konular düğmesi yok')
sec = part('private void secondaryAction()', 'private void setTyping(')
check('offerTopics' in sec, 'Konular düğmesi kartları açmıyor')
# C) Anladığım etiketleri yalnızca kayıtlardan hesaplanmış cevapta.
post = part('private void post(int token,String owner,int season,long active,ZeytinAssistantCommand cmd,boolean all,String gardenName,String sourceLabel,String raw,AnswerScope scope)', 'private void postHint(')
check('ZeytinAssistantHints.understood(' in post and 'if(scope!=null)' in post, 'etiketler cevaba bağlı değil')
check('post(token,owner,season,active,cmd,all,gardenName,null,result,scope)' in run, 'kayıt cevabında etiket yok')
check('resolution.error);return;}' in run and 'gardenName,resolution.error,scope' not in run, 'hata cevabında etiket olmamalı')
trans = part('private void transition(', 'private void showProblem(')
check('!hinting' in trans and 'tagsBox.setVisibility' in trans, 'öneri gösterilirken etiketler gizlenmiyor')
# D) Etiketi düzeltme: aynı soru, yeni dönem ya da bahçe.
sheet = part('private void openTagSheet(', 'private View option(')
for t in ['Hangi zaman?', 'Hangi bahçe?', 'Seçince cevap hemen yeniden hesaplanır', 'withPeriod(', 'new Pick(', 'supportsAll(', 'periodOptions(']:
    check(t in sheet, f'düzeltme sayfasında eksik: {t}')
check('pick!=null?pick.resolve(gardens):ZeytinAssistantCommand.resolve(raw,gardens,active' in run, 'seçilen bahçe kullanılmıyor')
check('boolean all=pick!=null?pick.all:' in run, '"Tüm bahçeler" seçimi kullanılmıyor')
rel = part('private void releasePanel()', 'public void pause()')
check('tagSheet' in rel, 'panel kapanınca düzeltme sayfası açık kalıyor')
# Değişmeyenler: cevap motoru ve bugünkü akış.
check('data.answer(cmd,resolution.garden,gardens,all)' in c, 'cevap motoru değişmiş')
check('run(raw,ZeytinAssistantCommand.parse(raw),null)' in c, 'ses/yazı ile sorma akışı değişmiş')
for icon in ['ic_op_cloud', 'ic_op_calendar', 'ic_op_search']:
    check((ROOT / f'app/src/main/res/drawable/{icon}.xml').is_file() and f'R.drawable.{icon}' in c, f'{icon} eksik ya da kullanılmıyor')
print(f'check_wiring (anlama): {n} kontrol, {len(fails)} hata')
for f in fails: print('  HATA:', f)
sys.exit(1 if fails else 0)
