package com.zeytindefteri.app;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** Calendar recurrence in the device zone. No Android or network dependency. */
public final class ReminderTime {
    private ReminderTime() {}
    public static boolean valid(long time) { return time>0 && time<253402214400000L; }
    public static String rule(String value) { return value==null||value.trim().isEmpty()?"NONE":value.trim().toUpperCase(Locale.ROOT); }
    public static boolean supported(String value) {String r=rule(value);return r.equals("NONE")||r.equals("DAILY")||r.equals("WEEKLY")||r.equals("MONTHLY");}
    public static long next(long due,String repeat,long now,TimeZone zone) {
        if(!valid(due)||!valid(now)||!supported(repeat)||rule(repeat).equals("NONE"))return 0;
        if(due>now)return due;
        Calendar anchor=Calendar.getInstance(zone);anchor.setTimeInMillis(due);
        Calendar current=Calendar.getInstance(zone);current.setTimeInMillis(now);
        String r=rule(repeat);Calendar candidate=(Calendar)anchor.clone();
        if(r.equals("MONTHLY")) {
            int months=(current.get(Calendar.YEAR)-anchor.get(Calendar.YEAR))*12+current.get(Calendar.MONTH)-anchor.get(Calendar.MONTH);
            candidate.add(Calendar.MONTH,Math.max(0,months));
            if(candidate.getTimeInMillis()<=now){candidate=(Calendar)anchor.clone();candidate.add(Calendar.MONTH,Math.max(0,months)+1);}
        } else {
            int step=r.equals("WEEKLY")?7:1;
            long days=Math.max(0,(now-due)/86400000L);
            int count=(int)(days/step);
            candidate.add(Calendar.DATE,count*step);
            while(candidate.getTimeInMillis()<=now)candidate.add(Calendar.DATE,step);
        }
        long result=candidate.getTimeInMillis();return valid(result)&&result>now?result:0;
    }
}
