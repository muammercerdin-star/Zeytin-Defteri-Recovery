package com.zeytindefteri.app;
import java.util.*;
import java.text.SimpleDateFormat;
/** Date windows intersect the existing selected-season repository scope; never change it. */
public final class ZeytinAssistantPeriod {
    public final String name;
    private ZeytinAssistantPeriod(String name){this.name=name;}
    public static ZeytinAssistantPeriod parse(String s){
        for(String word:new String[]{"gecen hafta","bu hafta","gecen ay","bu ay","bugun","dun"})if(s.contains(word))return new ZeytinAssistantPeriod(word);
        return new ZeytinAssistantPeriod("");
    }
    public boolean limited(){return !name.isEmpty();}
    public long[] bounds(long now){
        Calendar start=Calendar.getInstance(TimeZone.getTimeZone("Europe/Istanbul"));start.setTimeInMillis(now);
        start.set(Calendar.HOUR_OF_DAY,0);start.set(Calendar.MINUTE,0);start.set(Calendar.SECOND,0);start.set(Calendar.MILLISECOND,0);
        if(name.contains("hafta")){int delta=(start.get(Calendar.DAY_OF_WEEK)+5)%7;start.add(Calendar.DAY_OF_MONTH,-delta);if(name.startsWith("gecen"))start.add(Calendar.DAY_OF_MONTH,-7);}
        else if(name.contains("ay")){start.set(Calendar.DAY_OF_MONTH,1);if(name.startsWith("gecen"))start.add(Calendar.MONTH,-1);}
        else if(name.equals("dun"))start.add(Calendar.DAY_OF_MONTH,-1);
        Calendar end=(Calendar)start.clone();if(name.contains("hafta"))end.add(Calendar.DAY_OF_MONTH,7);else if(name.contains("ay"))end.add(Calendar.MONTH,1);else end.add(Calendar.DAY_OF_MONTH,1);
        SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);f.setTimeZone(start.getTimeZone());
        return new long[]{ZeytinAssistantData.date(f.format(start.getTime())),ZeytinAssistantData.date(f.format(end.getTime()))};
    }
    public boolean includes(String date,long now){if(!limited())return true;Long d=ZeytinAssistantData.date(date);long[] b=bounds(now);return d!=null&&d>=b[0]&&d<b[1];}
}
