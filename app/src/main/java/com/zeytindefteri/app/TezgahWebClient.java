package com.zeytindefteri.app;
import okhttp3.*;
import okio.ByteString;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/** Tek executor, WSS, 10 frame/sn ve ~1.9 MiB/dk tempo; sınırlı kuyruk, sır/log yok. */
public final class TezgahWebClient implements AutoCloseable {
    public interface State {void update(boolean ready,String detail);}
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private final OkHttpClient http=new OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(0,TimeUnit.MILLISECONDS).pingInterval(30,TimeUnit.SECONDS).build();
    private final ArrayDeque<String> queue=new ArrayDeque<>();private final AtomicBoolean stopped=new AtomicBoolean();
    private final BooleanSupplier alive;private final State state;private final String url;private TezgahWebPhone core;private volatile WebSocket socket;
    private boolean opened;private int queuedBytes,attempt;private long lastReceive,heartbeat,backoffUntil,lastSent,lastPump=clock();private double tokens=65536;
    public static boolean configured(){try{URI u=new URI(BuildConfig.TEZGAH_RELAY_URL.trim());return u.getScheme().equals("https")&&u.getHost()!=null&&u.getUserInfo()==null&&(u.getPath().isEmpty()||u.getPath().equals("/"))&&u.getQuery()==null&&u.getFragment()==null;}catch(Exception e){return false;}}
    public TezgahWebClient(TezgahWebPhone.Qr qr,TezgahWebRepository repository,BooleanSupplier alive,State state)throws Exception{
        if(!configured())throw new IllegalArgumentException("YAPILANDIRILMADI");this.alive=alive;this.state=state;
        String base=BuildConfig.TEZGAH_RELAY_URL.trim();while(base.endsWith("/"))base=base.substring(0,base.length()-1);url="wss"+base.substring(5)+"/v1/socket?room="+qr.room;
        core=new TezgahWebPhone(qr,repository,this::enqueue,ready->state.update(ready,ready?"Bilgisayar bağlı":"Yeniden bağlanıyor"));core.approve();
    }
    private static long clock(){return System.nanoTime()/1000000;}
    private Runnable release = () -> { };

    public void release(Runnable callback) {
        release = callback;
    }

    public void commands(TezgahWebPhone.Commands commands) {
        core.commands(commands);
    }

    public void decide(String id, boolean approve) {
        if (!stopped.get()) worker.execute(() -> core.decide(id, approve));
    }

    public void downgrade() {
        if (!stopped.get()) worker.execute(core::downgrade);
    }

    public void backupDone(String id, boolean ok, String message) {
        if (!stopped.get()) worker.execute(() -> core.backupDone(id, ok, message));
    }

    public void start(){worker.execute(this::connect);worker.scheduleAtFixedRate(this::tick,100,100,TimeUnit.MILLISECONDS);}
    private void enqueue(String frame){if(stopped.get())return;int bytes=TezgahWebProtocol.utf8(frame).length;if(queue.size()>=128||queuedBytes+bytes>2*1024*1024)throw new IllegalStateException("KUYRUK_DOLU");queue.add(frame);queuedBytes+=bytes;}
    private void connect(){if(stopped.get()||!alive.getAsBoolean()){close();return;}opened=false;
        socket=http.newWebSocket(new Request.Builder().url(url).build(),new WebSocketListener(){
            private void dispatch(Runnable run){if(!stopped.get())try{worker.execute(()->{if(!stopped.get()&&socket==current)run.run();});}catch(RejectedExecutionException ignored){}}
            private WebSocket current;
            @Override public void onOpen(WebSocket ws,Response r){current=ws;dispatch(()->{opened=true;lastReceive=clock();heartbeat=clock();core.opened();});}
            @Override public void onMessage(WebSocket ws,String text){current=ws;dispatch(()->{
                if(!alive.getAsBoolean()){close();return;}lastReceive=clock();
                try{org.json.JSONObject control=new org.json.JSONObject(text);String type=control.optString("type");if(type.equals("hiz_siniri"))backoffUntil=clock()+1500;
                    if(core.ready()&&(type.equals("resume_offer")||((type.equals("peer_joined")||type.equals("peer_left"))&&control.optString("role").equals("browser")))){queue.clear();queuedBytes=0;}
                    core.receive(text,clock());if(core.ready()){attempt=0;if(type.equals("pong"))state.update(true,"Bilgisayar bağlı");}}
                catch(Exception e){fail("Bağlantı doğrulanamadı. Yeni QR okut.");}
            });}
            @Override public void onMessage(WebSocket ws,ByteString binary){current=ws;dispatch(()->fail("Geçersiz bağlantı mesajı."));}
            @Override public void onFailure(WebSocket ws,Throwable error,Response r){current=ws;dispatch(()->lost(ws));}
            @Override public void onClosing(WebSocket ws,int code,String reason){ws.close(code,reason);onClosed(ws,code,reason);}
            @Override public void onClosed(WebSocket ws,int code,String reason){current=ws;dispatch(()->{if(reason.equals("AUTH_RED")||reason.equals("ROL_RED")||reason.equals("BAGLANTI_YENILENDI"))fail("Bağlantı kapandı. Yeni QR okut.");else lost(ws);});}
        });
    }
    private void lost(WebSocket ws){if(socket!=ws||stopped.get())return;socket=null;opened=false;queue.clear();queuedBytes=0;core.disconnected(clock());
        if(!core.canReconnect(clock())){fail("Bağlantı süresi doldu. Yeni QR okut.");return;}
        long delay=Math.min(30000,1000L<<Math.min(attempt++,5));delay=delay/2+new java.security.SecureRandom().nextInt((int)(delay/2+1));worker.schedule(this::connect,delay,TimeUnit.MILLISECONDS);
    }
    private void tick(){try{
        if(stopped.get())return;if(!alive.getAsBoolean()||core.expired(clock())){fail("Bilgisayar bağlantısı kapandı.");return;}
        core.tick();
        long now=clock();tokens=Math.min(65536,tokens+Math.max(0,now-lastPump)*32.768);lastPump=now;
        if(!opened||socket==null)return;
        if(now-lastReceive>60000){WebSocket old=socket;old.cancel();lost(old);return;}
        if(now-heartbeat>=30000){enqueue("{\"v\":1,\"type\":\"ping\"}");heartbeat=now;}
        if(now<backoffUntil||now-lastSent<100||queue.isEmpty())return;String frame=queue.peek();int bytes=TezgahWebProtocol.utf8(frame).length;
        if(tokens>=bytes){if(!socket.send(frame)){WebSocket old=socket;old.cancel();lost(old);return;}queue.remove();queuedBytes-=bytes;tokens-=bytes;lastSent=now;}
    }catch(Exception e){fail("Bilgisayar bağlantısı durduruldu.");}}
    public void changed(long revision){if(!stopped.get())try{worker.execute(()->{if(!stopped.get()&&alive.getAsBoolean())try{core.changed(revision);}catch(Exception e){fail("Bağlantı kuyruğu doldu.");}});}catch(RejectedExecutionException ignored){}}
    private void fail(String detail){state.update(false,detail);close();}
    public boolean stopped(){return stopped.get();}
    public void close(){if(!stopped.compareAndSet(false,true))return;WebSocket ws=socket;if(ws!=null){ws.send("{\"v\":1,\"type\":\"close\"}");if(!ws.close(1000,"CIKIS"))ws.cancel();}socket=null;
        try{worker.execute(()->{queue.clear();queuedBytes=0;core.close();release.run();http.dispatcher().executorService().shutdown();http.connectionPool().evictAll();worker.shutdownNow();});}catch(RejectedExecutionException ignored){}
    }
}
