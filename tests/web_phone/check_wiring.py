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
check('if (TezgahWebClient.configured()) {' in home and 'TezgahWebLink.row(' in home,'URL empty hides row')
check('BuildConfig.TEZGAH_RELAY_URL' in client and 'getScheme().equals("https")' in client,'https config')
check("zdBuildString('TEZGAH_RELAY_URL')" in Path('app/build.gradle').read_text(),'BuildConfig pattern')
check('webReset();' in session[session.index('public void logout()'):],'logout resets')
check(session.count('webReset();')==3,'guest/user/logout reset')
check('getMethod("reset", String.class)' in (p/'LocalAccountWipe.java').read_text(),'wipe reset')
check('"restored"' in (p/'TezgahBackup.java').read_text(),'restore stops')
check('START_NOT_STICKY' in svc and 'SDK_INT>=26' in svc.replace(' ', '') and 'else activity.startService(intent)' in svc,'service API24')
check('getNoBackupFilesDir' in (p/'TezgahWebSessions.java').read_text() and 'tezgah_web' not in (p/'TezgahBackup.java').read_text(),'metadata excluded backup')
for name in ['TezgahPurchases','TezgahPayments','TezgahClosing','TezgahZ']:
 check('webSignal' in (p/(name+'.java')).read_text(),name+' mutation hook')
check('TezgahPayments.payBulk(' in (p/'TezgahStatementActivity.java').read_text() and 'if (committed)' in (p/'TezgahPayments.java').read_text(), 'shared atomic bulk payment path')
check('now-lastSent<100' in client and 'queue.size()>=128' in client and '2*1024*1024' in client,'bounded pacing independent of scheduler catchup')
check('p.generation!=TezgahWebChanges.resetGeneration()' in svc.replace(' ', ''),'pending approval invalidated by account reset')
check('intent.setData(' in svc and 'reviewId' in svc and 'room.equals(expectedRoom)' in svc, 'approval identity is room plus command')
check('isKeyguardLocked()' in svc, 'locked phone cannot approve a write')
check('prefs.getBoolean(key, false)' in home and 'webPrefs.getBoolean(webKey, false)' in home and 'writable(owner)' in home, 'read permission default and phone downgrade UI')
check('commands.revoke()' in svc and 'client.downgrade()' in svc, 'immediate revoke precedes queued downgrade')
print(f'PASS {n}/{n} web phone wiring (static, not Android runtime)')
# Adım 5: eski oturumun gecikmiş servis kapanışı yeni başlangıca uygulanamaz.
check('generation != endedGeneration' in svc and 'latestStartId != endedStartId' in svc and 'stopSelf(endedStartId)' in svc, 'generation + startId shutdown guard')
check('TezgahWebService.hasReview(owner)' in home, 'review row conditional')
check('TezgahWebSessions.recent(this, owner)' in home and 'clearHistory(this, owner)' in home, 'last three and clear history')
check(home.index('prefs.contains(key)') < home.index('TezgahQrUi.scan(this'), 'permission (asked once) before scanner')
manifest=Path('app/src/main/AndroidManifest.xml').read_text()
check('android.permission.CAMERA' not in manifest and 'TezgahWebScanActivity' not in manifest and not (p/'TezgahWebScanActivity.java').exists(), 'no CAMERA permission: Google code scanner only (system camera intent keeps working)')
check('HTTP " + r.code()' in client and 'Relay " + code' in client, 'diagnostics exclude frame contents')
print(f'PASS {n}/{n} web phone wiring including step 5 (static, not device runtime)')

ready=(p/'TezgahWebReadyService.java').read_text()
check('TezgahWebReadyService.prepare(this)' in home and 'TezgahWebReadyService.prepare' not in (p/'MainActivity.java').read_text(), 'ready service starts only from the visible Tezgâh screen (not every app open)')
check('START_NOT_STICKY' in ready and 'FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE' in ready, 'ready foreground type')
check('getNoBackupFilesDir' in (p/'TezgahWebDevices.java').read_text() and 'AndroidKeyStore' in (p/'TezgahWebDevices.java').read_text(), 'device metadata and private identity storage')
check('TezgahWebDevices.forget(this, owner' in home and 'changedDevice(this, owner' in home, 'phone forget updates active link')
check('current.optBoolean("tamKullanim") == device.optBoolean("tamKullanim")' in ready, 'permission change invalidates command executor immediately')
check('generation == version' in ready and 'stopped = true;' in ready, 'account and link generation guards')
check('SystemClock.elapsedRealtime() + 60000' in ready and 'isKeyguardLocked()' in ready, 'ready approval timeout and lock guard')
check('queue.size() >= 128' in ready and '2097152' in ready and '32.768' in ready, 'ready bounded and paced queue')
print(f'PASS {n}/{n} web phone wiring including persistent ready service (static only)')
