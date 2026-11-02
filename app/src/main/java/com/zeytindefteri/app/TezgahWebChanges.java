package com.zeytindefteri.app;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Commit sonrası, owner ayrımlı ve 400 ms birleştirilen süreç içi bildirimler. DB/Context tutulmaz. */
public final class TezgahWebChanges {
    public interface Listener { void changed(String owner,long revision); void reset(String owner); }
    private static final List<Listener> listeners=new CopyOnWriteArrayList<>();
    private static final AtomicLong revision=new AtomicLong(), resetGeneration=new AtomicLong();
    private static final Map<String,ScheduledFuture<?>> pending=new HashMap<>();
    private static final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"tezgah-web-changes");t.setDaemon(true);return t;});
    public static final String SOURCE=UUID.randomUUID().toString();
    private TezgahWebChanges() { }
    public static long resetGeneration(){return resetGeneration.get();}
    public static long revision(){return revision.get();}
    public static void listen(Listener l){listeners.add(l);}
    public static void remove(Listener l){listeners.remove(l);}
    public static void committed(Object db,String owner){
        // Nested transaction'ın bildirimi dış transaction'ı kapatan çağırana aittir.
        try { if(Boolean.TRUE.equals(db.getClass().getMethod("inTransaction").invoke(db)))return; }
        catch(ReflectiveOperationException ignored){ /* Host SQLite köprüsünde transaction API'si olmayabilir. */ }
        changed(owner);
    }
    public static synchronized void changed(String owner){
        if(listeners.isEmpty())return;
        String o=owner==null?"":owner;revision.incrementAndGet();
        ScheduledFuture<?> old=pending.remove(o);if(old!=null)old.cancel(false);
        pending.put(o,timer.schedule(()->{
            synchronized(TezgahWebChanges.class){pending.remove(o);}
            for(Listener l:listeners)try{l.changed(o,revision.get());}catch(RuntimeException ignored){}
        },400,TimeUnit.MILLISECONDS));
    }
    public static void reset(String owner){resetGeneration.incrementAndGet();for(Listener l:listeners)try{l.reset(owner);}catch(RuntimeException ignored){} }
    public static void restored(Object db,String owner){reset(owner);committed(db,owner);}
}
