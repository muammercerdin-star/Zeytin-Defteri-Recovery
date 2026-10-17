"""Asistan soru bataryası (test doubles, cihaz veritabanı değil). tests/assistant/run_host_tests.py taklitlerini kullanır."""
import subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[2]
src=(root/'tests/assistant/run_host_tests.py').read_text()
stubs_code=src[src.index('    stubs={'):src.index('    for name,content in stubs.items():')]
with tempfile.TemporaryDirectory(prefix='zeytin-answers-') as d:
    temp=Path(d);ns={'root':root,'temp':temp,'Path':Path}
    exec('if True:\n'+stubs_code,ns)
    for name,content in ns['stubs'].items():
        p=temp/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content)
    prod=[root/'app/src/main/java/com/zeytindefteri/app'/f for f in ['ZeytinAssistantCommand.java','ZeytinAssistantData.java','ZeytinAssistantWeather.java','ZeytinAssistantPeriod.java','GardenTodayAdvisor.java','ZeytinAssistantText.java']]
    subprocess.run(['javac','-nowarn','-encoding','UTF-8','-d',str(temp/'c')]+[str(temp/n) for n in ns['stubs']]+list(map(str,prod))+[str(root/'tests/assistant_answers/AnswersTest.java')],check=True,stderr=subprocess.DEVNULL)
    subprocess.run(['java','-Dfile.encoding=UTF-8','-cp',str(temp/'c'),'AnswersTest'],check=True)
