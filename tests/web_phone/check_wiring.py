from pathlib import Path
import xml.etree.ElementTree as ET
p=Path('app/src/main/java/com/zeytindefteri/app');ns='{http://schemas.android.com/apk/res/android}'
n=0
def check(ok,message):
 global n
 n+=1
 assert ok,message
manifest=ET.parse('app/src/main/AndroidManifest.xml').getroot()
permissions={e.attrib[ns+'name'] for e in manifest.findall('uses-permission')}
for name in ['FOREGROUND_SERVICE','FOREGROUND_SERVICE_CONNECTED_DEVICE','CHANGE_NETWORK_STATE']:
 check('android.permission.'+name in permissions,name)
service=next(e for e in manifest.find('application').findall('service') if e.attrib[ns+'name']=='.TezgahWebService')
check(service.attrib[ns+'exported']=='false' and service.attrib[ns+'foregroundServiceType']=='connectedDevice','service type')
home=(p/'TezgahHomeActivity.java').read_text();client=(p/'TezgahWebClient.java').read_text();svc=(p/'TezgahWebService.java').read_text();session=(p/'SessionManager.java').read_text()
check('if (TezgahWebClient.configured()) body.addView' in home,'URL empty hides row')
check('BuildConfig.TEZGAH_RELAY_URL' in client and 'getScheme().equals("https")' in client,'https config')
check("zdBuildString('TEZGAH_RELAY_URL')" in Path('app/build.gradle').read_text(),'BuildConfig pattern')
check('webReset();' in session[session.index('public void logout()'):],'logout resets')
check(session.count('webReset();')==3,'guest/user/logout reset')
check('getMethod("reset", String.class)' in (p/'LocalAccountWipe.java').read_text(),'wipe reset')
check('"restored"' in (p/'TezgahBackup.java').read_text(),'restore stops')
check('START_NOT_STICKY' in svc and 'SDK_INT>=26' in svc and 'else activity.startService(i)' in svc,'service API24')
check('getNoBackupFilesDir' in (p/'TezgahWebSessions.java').read_text() and 'tezgah_web' not in (p/'TezgahBackup.java').read_text(),'metadata excluded backup')
for name in ['TezgahPurchases','TezgahPayments','TezgahClosing','TezgahZ']:
 check('webSignal' in (p/(name+'.java')).read_text(),name+' mutation hook')
check('w2.endTransaction(); if (ok && done > 0) TezgahWebChanges.committed' in (p/'TezgahStatementActivity.java').read_text(),'outer payment commit')
check('now-lastSent<100' in client and 'queue.size()>=128' in client and '2*1024*1024' in client,'bounded pacing independent of scheduler catchup')
check('p.generation!=TezgahWebChanges.resetGeneration()' in svc,'pending approval invalidated by account reset')
print(f'PASS {n}/{n} web phone wiring (static, not Android runtime)')
