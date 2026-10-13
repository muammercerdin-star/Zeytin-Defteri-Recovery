"""Reuse the current production/SQLite harness; add delayed-alarm regression scenarios."""
from pathlib import Path
base=Path(__file__).with_name('check_reminders.py')
source=base.read_text()
extra=r'''
 Context hx=new Context();new SessionManager(hx).startUserSession("Hotfix","","HOTFIX");
 ReminderRepository hr=new ReminderRepository(new DatabaseHelper(hx));LocalReminderScheduler hs=new LocalReminderScheduler(hx);
 SharedPreferences metadata=hx.getSharedPreferences("local_reminder_delivery",0);long hnow=System.currentTimeMillis();
 long once=hr.addReminder(null,"Delayed once","GENERAL",hnow-300000,"NONE","");
 metadata.edit().putBoolean("alarm_"+once,true).commit();hs.reschedule();
 check(hx.alarms.active.size()==1,"HOTFIX A tracked overdue one-shot rearmed");
 check(due(hr,once)==hnow-300000,"HOTFIX A original SQLite due_at preserved");
 check(hx.alarms.active.values().iterator().next()>hnow,"HOTFIX A trigger moved to short future only in OS");
 hs.receive(once);hs.receive(once);check(hx.notifications.posted==1,"HOTFIX A occurrence delivered exactly once");
 long recurring=hr.addReminder(null,"Delayed repeat","GENERAL",hnow-300000,"DAILY","");
 metadata.edit().putBoolean("alarm_"+recurring,true).commit();hs.reschedule();
 check(due(hr,recurring)==hnow-300000,"HOTFIX B recurring current occurrence not skipped");
 hs.receive(recurring);check(hx.notifications.posted==2&&due(hr,recurring)>hnow,"HOTFIX B deliver current then plan next");
 hs.receive(recurring);check(hx.notifications.posted==2,"HOTFIX B no duplicate repeat notification");hs.cancel(recurring);hr.markReminderDone(recurring);
 long ancient=hr.addReminder(null,"Ancient","GENERAL",hnow-7*3600000L,"NONE","");metadata.edit().putBoolean("alarm_"+ancient,true).commit();hs.reschedule();hs.receive(ancient);
 check(hx.alarms.active.isEmpty()&&hx.notifications.posted==2,"HOTFIX C outside grace one-shot stays silent");
 long complete=hr.addReminder(null,"Done","GENERAL",hnow-1000,"NONE","");hr.markReminderDone(complete);metadata.edit().putBoolean("alarm_"+complete,true).commit();hs.reschedule();
 check(hx.alarms.active.isEmpty(),"HOTFIX D completed tracked record not armed");
 long missing=hr.addReminder(null,"Delete","GENERAL",hnow+600000,"NONE","");hr.deleteReminder(missing);metadata.edit().putBoolean("alarm_"+missing,true).putBoolean("alarm_9999999",true).commit();hs.reschedule();
 check(hx.alarms.active.isEmpty(),"HOTFIX E deleted and nonexistent records not armed");
 long future=hr.addReminder(null,"Future","GENERAL",hnow+600000,"NONE","");metadata.edit().putBoolean("alarm_"+future,true).commit();hs.reschedule();hs.reschedule();
 check(hx.alarms.active.size()==1,"HOTFIX F tracked plus schedulable -> one alarm");hs.cancel(future);hr.markReminderDone(future);
 long oldRepeat=hr.addReminder(null,"Old repeat","GENERAL",hnow-7*3600000L,"DAILY","");metadata.edit().putBoolean("alarm_"+oldRepeat,true).commit();hs.reschedule();
 check(due(hr,oldRepeat)>hnow&&hx.notifications.posted==2,"HOTFIX outside grace repeat advances silently");hs.cancel(oldRepeat);hr.markReminderDone(oldRepeat);
 long untracked=hr.addReminder(null,"Untracked old one-shot","GENERAL",hnow-1000,"NONE","");hs.reschedule();check(hx.alarms.active.isEmpty(),"HOTFIX untracked one-shot history not swept");
 for(String key:new String[]{"alarm_","alarm_bad","alarm_-1","alarm_99999999999999999999999","notice_bad","notice_0","alarm_01"})metadata.edit().putBoolean(key,true).apply();hs.reschedule();
 check(!metadata.getAll().containsKey("alarm_bad")&&!metadata.getAll().containsKey("notice_bad"),"HOTFIX malformed metadata cleaned without crash");
 metadata.edit().putBoolean("alarm_900",true).putBoolean("notice_900",true).apply();hs.reschedule();check(hx.alarms.active.isEmpty(),"HOTFIX foreign tracked reminder excluded");
 long privacy=hr.addReminder(null,"Private","GENERAL",hnow-1000,"NONE","");hs.receive(privacy);long open=hr.addReminder(null,"Open","GENERAL",hnow+600000,"NONE","");hs.schedule(hr,open);
 hx.getSharedPreferences("zeytin_session",0).edit().clear().commit();metadata.edit().putBoolean("notice_",true).apply();hs.reschedule();
 check(hx.alarms.active.isEmpty()&&hx.notifications.active.isEmpty(),"HOTFIX no-session reschedule clears old alarm and notification");
 metadata.edit().putBoolean("alarm_x",true).putBoolean("notice_-2",true).apply();LocalReminderScheduler.beforeSessionChange(hx);
 check(!metadata.getAll().containsKey("alarm_x")&&!metadata.getAll().containsKey("notice_-2"),"HOTFIX beforeSessionChange tolerates corrupt keys");
'''
needle=' System.out.println("PASS "+count+" runtime SQLite/production scheduler checks; OS test doubles, NOT Android build/device PASS");'
assert needle in source
source=source.replace(needle,extra+needle)
exec(compile(source,str(base),'exec'),dict(__file__=str(base.resolve()),__name__='__main__'))
