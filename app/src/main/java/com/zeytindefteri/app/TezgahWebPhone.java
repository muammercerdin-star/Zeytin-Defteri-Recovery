package com.zeytindefteri.app;
import org.json.*;
import java.security.KeyPair;
import java.util.*;
import java.util.function.Consumer;

/** Android'siz gerçek telefon protokol motoru. Tek istemci executor'ından çağrılır. */
public final class TezgahWebPhone implements AutoCloseable {
    public static final class Qr {
        public final String room,capability,secret,browserKey,nb;public final long expiresAt;
        private Qr(JSONObject j)throws Exception{room=j.getString("room");capability=j.getString("phoneCapability");secret=j.getString("secret");browserKey=j.getString("browserPublicKey");nb=j.getString("nb");expiresAt=j.getLong("expiresAt");}
        public static Qr parse(String text,long seconds)throws Exception{
            if(text==null||TezgahWebProtocol.utf8(text).length>2048)throw new IllegalArgumentException("GECERSIZ_QR");
            JSONObject j=new JSONObject(text);Set<String> fields=new HashSet<>(Arrays.asList("kind","v","room","expiresAt","phoneCapability","secret","browserPublicKey","nb"));
            if(j.length()!=fields.size())throw new IllegalArgumentException("GECERSIZ_QR");for(Iterator<String> it=j.keys();it.hasNext();)if(!fields.contains(it.next()))throw new IllegalArgumentException("GECERSIZ_QR");
            if(!"ZD_WEB_PAIR".equals(j.optString("kind"))||!(j.get("v") instanceof Number)||((Number)j.get("v")).doubleValue()!=1.0)throw new IllegalArgumentException("SURUM_DESTEKLENMIYOR");
            if(!(j.get("expiresAt") instanceof Number)||((Number)j.get("expiresAt")).doubleValue()!=j.getLong("expiresAt"))throw new IllegalArgumentException("GECERSIZ_QR");
            Qr q=new Qr(j);if(q.expiresAt<=seconds||q.expiresAt>seconds+90)throw new IllegalArgumentException("QR_SURESI_DOLDU");
            TezgahWebProtocol.token(q.room,16);TezgahWebProtocol.token(q.capability,32);TezgahWebProtocol.token(q.secret,32);TezgahWebProtocol.token(q.nb,32);TezgahWebCrypto.publicKey(q.browserKey);return q;
        }
    }
    private final Qr qr;private final TezgahWebRepository repository;private final Consumer<String> output;private final Consumer<Boolean> state;
    private TezgahWebCrypto.Keys keys;private TezgahWebCrypto.Channel channel;private TezgahWebCrypto.ResumeGuard guard;
    private final TezgahWebProtocol.Approval approval=new TezgahWebProtocol.Approval();
    private String session,epoch,capability,np;private byte[] resumeTranscript,phoneProof,root;
    private boolean localApproval,peerConfirmed,consuming,active,ready,closed;private long lost=-1,activeAt;
    public TezgahWebPhone(Qr qr,TezgahWebRepository repository,Consumer<String> output,Consumer<Boolean> state)throws Exception{
        this.qr=qr;this.repository=repository;this.output=output;this.state=state;capability=qr.capability;
        np=TezgahWebCrypto.base64(TezgahWebCrypto.random(32));KeyPair p=TezgahWebCrypto.keyPair();
        TezgahWebCrypto.Pairing pairing=new TezgahWebCrypto.Pairing(qr.room,qr.expiresAt,TezgahWebCrypto.unbase64(qr.secret),qr.browserKey,qr.nb);
        try{keys=pairing.phoneKeys(p,np,System.currentTimeMillis()/1000);}finally{pairing.close();}
        session=TezgahWebCrypto.base64(Arrays.copyOfRange(keys.transcriptHash,0,16));epoch=TezgahWebCrypto.base64(Arrays.copyOfRange(keys.transcriptHash,16,32));
        hello=TezgahWebDto.json("v",1,"type","phone_hello","room",qr.room,"phonePublicKey",TezgahWebCrypto.base64(p.getPublic().getEncoded()),"np",np,"proof",TezgahWebCrypto.base64(TezgahWebCrypto.proof(keys.confirmation(),"phone-hello",keys.transcriptHash))).toString();
    }
    private String hello;
    public String room(){return qr.room;}
    public boolean ready(){return ready&&!closed;}
    public boolean canReconnect(long now){return !closed&&(active?lost>=0&&now-lost<=300000:System.currentTimeMillis()/1000<qr.expiresAt);}
    public void opened(){if(closed)return;output.accept(TezgahWebDto.json("v",1,"type","auth","room",qr.room,"role","phone","capability",capability).toString());}
    public void approve(){if(closed)throw new IllegalArgumentException("OTURUM_IPTAL");localApproval=true;tryConsume();}
    private void tryConsume(){if(localApproval&&peerConfirmed&&!consuming&&!active){approval.phoneApproved();consuming=true;output.accept(TezgahWebDto.json("v",1,"type","consume","session",session).toString());}}
    public void disconnected(long now){if(closed)return;ready=false;state.accept(false);if(!active)consuming=false;if(active){if(lost<0)lost=now;guard.disconnected(now);if(channel!=null)channel.close();channel=null;}}
    public void receive(String raw,long now)throws Exception{
        if(closed)throw new IllegalArgumentException("OTURUM_IPTAL");
        if(TezgahWebProtocol.utf8(raw).length>65536)throw new IllegalArgumentException("BOYUT_SINIRI");
        JSONObject f=new JSONObject(raw);if(f.optInt("v")!=1)throw new IllegalArgumentException("SURUM_DESTEKLENMIYOR");String type=f.optString("type");
        if(type.equals("auth_ok")){if(!active&&f.optString("stage").equals("pending"))output.accept(hello);else if(!active)throw new IllegalArgumentException("YENI_QR_GEREKLI");return;}
        if(type.equals("peer_left")){if(active&&"browser".equals(f.optString("role")))disconnected(now);return;}
        if(type.equals("peer_joined")){if("browser".equals(f.optString("role"))){if(active)disconnected(now);else output.accept(hello);}return;}
        if(type.equals("pong")||type.equals("hiz_siniri"))return;
        if(!active&&System.currentTimeMillis()/1000>=qr.expiresAt)throw new IllegalArgumentException("QR_SURESI_DOLDU");
        if(type.equals("web_finished")&&active)return;
        if(type.equals("web_finished")&&!active){
            if(!qr.room.equals(f.getString("room")))throw new IllegalArgumentException("GECERSIZ_ODA");
            TezgahWebCrypto.verify(keys.confirmation(),"web-finished",keys.transcriptHash,TezgahWebCrypto.unbase64(f.getString("proof")));
            peerConfirmed=true;approval.peerConfirmed();tryConsume();return;
        }
        if(type.equals("consumed")&&!active&&consuming&&approval.canRead()){
            if(!session.equals(f.getString("session")))throw new IllegalArgumentException("GECERSIZ_OTURUM");capability=f.getString("capability");TezgahWebProtocol.token(capability,32);
            active=true;activeAt=now;root=keys.resumeRoot();guard=new TezgahWebCrypto.ResumeGuard(session,epoch,root);hello=null;activate();return;
        }
        if(type.equals("resume_offer")&&active){
            if(ready)disconnected(now);if(!canReconnect(now)||!session.equals(f.getString("session"))||!epoch.equals(f.getString("oldEpoch")))throw new IllegalArgumentException("RESUME_RED");
            String next=f.getString("epoch");np=TezgahWebCrypto.base64(TezgahWebCrypto.random(32));resumeTranscript=TezgahWebProtocol.resumeTranscript(session,epoch,next,f.getString("nb"),np);
            phoneProof=TezgahWebCrypto.proof(root,"resume-phone",TezgahWebCrypto.sha256(resumeTranscript));pendingEpoch=next;pendingNb=f.getString("nb");
            output.accept(TezgahWebDto.json("v",1,"type","resume_answer","np",np,"proof",TezgahWebCrypto.base64(phoneProof)).toString());return;
        }
        if(type.equals("resume_finished")&&active&&resumeTranscript!=null){
            TezgahWebCrypto.Keys next=guard.accept(now,epoch,pendingEpoch,pendingNb,np,phoneProof,TezgahWebCrypto.unbase64(f.getString("proof")));
            keys.close();keys=next;Arrays.fill(root,(byte)0);root=keys.resumeRoot();epoch=pendingEpoch;resumeTranscript=null;phoneProof=null;lost=-1;
            output.accept(TezgahWebDto.json("v",1,"type","resume_complete").toString());activate();return;
        }
        if(type.equals("encrypted")){
            if(!ready()||!approval.canRead())throw new IllegalArgumentException("ONAY_GEREKLI");
            JSONObject request=new JSONObject(new String(channel.open(TezgahWebProtocol.Frame.parse(raw)),java.nio.charset.StandardCharsets.UTF_8));
            String id=request.optString("id","");if(!id.matches("[A-Za-z0-9_-]{1,64}")||request.optInt("v")!=1||!request.optString("type").equals("istek"))throw new IllegalArgumentException("GECERSIZ_ISTEK");
            String op=request.optString("op");try{
                if(!op.equals("bugunun_alimlari"))throw new IllegalArgumentException("SALT_OKUNUR");
                JSONObject args=request.optJSONObject("args");if(args==null)args=new JSONObject();
                JSONObject data=repository.read(args,System.currentTimeMillis());send(TezgahWebDto.json("v",1,"type","cevap","id",id,"op",op,"ok",true,"data",data));
            }catch(Exception e){String code=e instanceof IllegalArgumentException?e.getMessage():"VERI_OKUNAMADI";send(TezgahWebDto.json("v",1,"type","hata","id",id,"code",safeCode(code),"message","Veri okunamadı. Telefonda bağlantıyı kontrol et.","retryable",true));}
            return;
        }
        throw new IllegalArgumentException("GECERSIZ_AKIS");
    }
    private String pendingEpoch,pendingNb;
    private static String safeCode(String code){return code!=null&&code.matches("[A-Z_]{1,40}")?code:"VERI_OKUNAMADI";}
    private void activate(){channel=new TezgahWebCrypto.Channel(keys,session,epoch,"p2w");ready=true;send(TezgahWebDto.json("v",1,"type","onaylandi","session",session,"permissions",new JSONArray().put("tezgah.read"),"maxAgeSeconds",28800,"idleLockSeconds",300));state.accept(true);}
    private void send(JSONObject payload){if(!ready()||!approval.canRead())return;output.accept(channel.seal(TezgahWebProtocol.utf8(payload.toString())).json());}
    public void changed(long revision){if(ready())send(TezgahWebDto.json("v",1,"type","olay","event","veri_degisti","sourceEpoch",TezgahWebChanges.SOURCE,"revision",Long.toString(revision),"scopes",new JSONArray().put("alimlar")));}
    public boolean expired(long now){return closed||(active&&now-activeAt>=28800000)||(!active&&System.currentTimeMillis()/1000>=qr.expiresAt)||(active&&lost>=0&&now-lost>300000);}
    public void close(){closed=true;ready=false;approval.close();if(channel!=null)channel.close();if(guard!=null)guard.close();if(keys!=null)keys.close();if(root!=null)Arrays.fill(root,(byte)0);repository.close();hello=null;capability=null;state.accept(false);}
}
