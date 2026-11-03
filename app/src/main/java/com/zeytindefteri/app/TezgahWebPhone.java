package com.zeytindefteri.app;
import org.json.*;
import java.security.KeyPair;
import java.util.*;
import java.util.function.Consumer;

/** Android'siz gerçek telefon protokol motoru. Tek istemci executor'ından çağrılır. */
public final class TezgahWebPhone implements AutoCloseable {
    public interface Commands {
        void output(Consumer<JSONObject> listener);
        boolean writable();
        JSONObject submit(JSONObject request);
        JSONObject read(String operation, JSONObject args) throws Exception;
        void downgrade();
        void decide(String id, boolean approve);
        void tick();
        void backupDone(String id, boolean ok, String message);
        void close();
    }

    private Commands commands;

    public void commands(Commands handler) {
        if (active) throw new IllegalStateException("Oturum zaten başladı.");
        commands = handler;
        handler.output(this::send);
    }

    private JSONArray permissions() {
        JSONArray result = new JSONArray().put("tezgah.read");
        if (commands != null && commands.writable()) result.put("tezgah.write");
        return result;
    }

    public void downgrade() {
        if (commands == null) return;
        commands.downgrade();
        send(TezgahWebDto.json("v", 1, "type", "olay", "event", "yetki_degisti", "permissions", permissions()));
    }

    public void decide(String id, boolean approve) {
        if (commands != null) commands.decide(id, approve);
    }

    public void tick() {
        if (commands != null) commands.tick();
    }

    public void backupDone(String id, boolean ok, String message) {
        if (commands != null) commands.backupDone(id, ok, message);
    }

    public static final class Qr {
        public final String room,capability,secret,browserKey,nb;public final long expiresAt;
        private Qr(JSONObject j)throws Exception{room=j.getString("room");capability=j.getString("phoneCapability");secret=j.getString("secret");browserKey=j.getString("browserPublicKey");nb=j.getString("nb");expiresAt=j.getLong("expiresAt");}
        public boolean compact() {
            return browserKey.isEmpty();
        }

        private static byte[] derive(String room, String secret, String purpose) {
            return TezgahWebCrypto.hkdf(TezgahWebCrypto.unbase64(room), TezgahWebCrypto.unbase64(secret),
                    TezgahWebProtocol.utf8("zd-web/compact-" + purpose + "/v1"), 32);
        }

        public Qr resolve(JSONObject frame, long seconds) throws Exception {
            if (!compact() || !room.equals(frame.getString("room"))) throw new IllegalArgumentException("GECERSIZ_ODA");
            String key = frame.getString("browserPublicKey");
            TezgahWebCrypto.publicKey(key);
            byte[] digest = TezgahWebCrypto.sha256(TezgahWebCrypto.concat(
                    TezgahWebProtocol.utf8("zd-web/compact-room/v1\n"), TezgahWebCrypto.unbase64(key)));
            if (!java.security.MessageDigest.isEqual(Arrays.copyOf(digest, 16), TezgahWebCrypto.unbase64(room))) {
                throw new IllegalArgumentException("SABIT_B_UYUSMAZ");
            }
            String nonce = frame.getString("nb");
            TezgahWebProtocol.token(nonce, 32);
            long expires = frame.getLong("expiresAt");
            if (!(frame.get("expiresAt") instanceof Number) || ((Number) frame.get("expiresAt")).doubleValue() != expires) {
                throw new IllegalArgumentException("GECERSIZ_QR");
            }
            byte[] keyMac = derive(room, secret, "bootstrap");
            byte[] message = TezgahWebProtocol.utf8("zd-web/compact-bootstrap/v1\n" + room + "\n" + expires + "\n" + key + "\n" + nonce);
            try {
                if (!java.security.MessageDigest.isEqual(TezgahWebCrypto.hmac(keyMac, message), TezgahWebCrypto.unbase64(frame.getString("mac")))) {
                    throw new IllegalArgumentException("QR_DOGRULANAMADI");
                }
            } finally {
                Arrays.fill(keyMac, (byte) 0);
            }
            if (expires <= seconds) throw new IllegalArgumentException("QR_SURESI_DOLDU");
            if (expires - seconds < 30) throw new IllegalArgumentException("QR_YENILENMEK_UZERE");
            if (expires > seconds + 90) throw new IllegalArgumentException("GECERSIZ_QR");
            return new Qr(TezgahWebDto.json("room", room, "phoneCapability", capability, "secret", secret,
                    "browserPublicKey", key, "nb", nonce, "expiresAt", expires));
        }

        public static Qr parse(String text,long seconds)throws Exception{
            if(text==null||TezgahWebProtocol.utf8(text).length>2048)throw new IllegalArgumentException("GECERSIZ_QR");
            if (text.startsWith("ZD1.")) {
                if (text.length() != 68) throw new IllegalArgumentException("GECERSIZ_QR");
                byte[] bytes = TezgahWebCrypto.unbase64(text.substring(4));
                String room = TezgahWebCrypto.base64(Arrays.copyOfRange(bytes, 0, 16));
                String secret = TezgahWebCrypto.base64(Arrays.copyOfRange(bytes, 16, 48));
                byte[] cap = derive(room, secret, "phone-cap");
                try {
                    return new Qr(TezgahWebDto.json("room", room, "phoneCapability", TezgahWebCrypto.base64(cap),
                            "secret", secret, "browserPublicKey", "", "nb", "", "expiresAt", seconds + 90));
                } finally {
                    Arrays.fill(bytes, (byte) 0);
                    Arrays.fill(cap, (byte) 0);
                }
            }
            JSONObject j=new JSONObject(text);Set<String> fields=new HashSet<>(Arrays.asList("kind","v","room","expiresAt","phoneCapability","secret","browserPublicKey","nb"));
            if(j.length()!=fields.size())throw new IllegalArgumentException("GECERSIZ_QR");for(Iterator<String> it=j.keys();it.hasNext();)if(!fields.contains(it.next()))throw new IllegalArgumentException("GECERSIZ_QR");
            if(!"ZD_WEB_PAIR".equals(j.optString("kind"))||!(j.get("v") instanceof Number)||((Number)j.get("v")).doubleValue()!=1.0)throw new IllegalArgumentException("SURUM_DESTEKLENMIYOR");
            if(!(j.get("expiresAt") instanceof Number)||((Number)j.get("expiresAt")).doubleValue()!=j.getLong("expiresAt"))throw new IllegalArgumentException("GECERSIZ_QR");
            Qr q=new Qr(j);if(q.expiresAt<=seconds||q.expiresAt>seconds+90)throw new IllegalArgumentException("QR_SURESI_DOLDU");
            TezgahWebProtocol.token(q.room,16);TezgahWebProtocol.token(q.capability,32);TezgahWebProtocol.token(q.secret,32);TezgahWebProtocol.token(q.nb,32);TezgahWebCrypto.publicKey(q.browserKey);return q;
        }
    }
    private Qr qr;private final TezgahWebRepository repository;private final Consumer<String> output;private final Consumer<Boolean> state;
    private TezgahWebCrypto.Keys keys;private TezgahWebCrypto.Channel channel;private TezgahWebCrypto.ResumeGuard guard;
    private final TezgahWebProtocol.Approval approval=new TezgahWebProtocol.Approval();
    private String session,epoch,capability,np;private byte[] resumeTranscript,phoneProof,root;
    private boolean localApproval,peerConfirmed,consuming,active,ready,closed;private long lost=-1,activeAt;
    public TezgahWebPhone(Qr qr,TezgahWebRepository repository,Consumer<String> output,Consumer<Boolean> state)throws Exception{
        this.qr=qr;this.repository=repository;this.output=output;this.state=state;capability=qr.capability;
        if (!qr.compact()) initializePairing();
    }

    private void initializePairing() throws Exception {
        np=TezgahWebCrypto.base64(TezgahWebCrypto.random(32));KeyPair p=TezgahWebCrypto.keyPair();
        TezgahWebCrypto.Pairing pairing=new TezgahWebCrypto.Pairing(qr.room,qr.expiresAt,TezgahWebCrypto.unbase64(qr.secret),qr.browserKey,qr.nb);
        try{keys=pairing.phoneKeys(p,np,System.currentTimeMillis()/1000);}finally{pairing.close();}
        session=TezgahWebCrypto.base64(Arrays.copyOfRange(keys.transcriptHash,0,16));epoch=TezgahWebCrypto.base64(Arrays.copyOfRange(keys.transcriptHash,16,32));
        hello=TezgahWebDto.json("v",1,"type","phone_hello","room",qr.room,"phonePublicKey",TezgahWebCrypto.base64(p.getPublic().getEncoded()),"np",np,"proof",TezgahWebCrypto.base64(TezgahWebCrypto.proof(keys.confirmation(),"phone-hello",keys.transcriptHash))).toString();
    }
    private void sendHello() {
        if (qr.compact()) {
            output.accept(TezgahWebDto.json("v", 1, "type", "bootstrap_request", "room", qr.room).toString());
        } else {
            output.accept(hello);
        }
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
        if(type.equals("auth_ok")){if(!active&&f.optString("stage").equals("pending"))sendHello();else if(!active)throw new IllegalArgumentException("YENI_QR_GEREKLI");return;}
        if(type.equals("peer_left")){if(active&&"browser".equals(f.optString("role")))disconnected(now);return;}
        if(type.equals("peer_joined")){if("browser".equals(f.optString("role"))){if(active)disconnected(now);else sendHello();}return;}
        if (type.equals("bootstrap")) {
            if (active || !qr.compact()) return;
            qr = qr.resolve(f, System.currentTimeMillis() / 1000);
            initializePairing();
            sendHello();
            return;
        }
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
            if ("komut".equals(request.optString("type"))) {
                if (commands != null) send(commands.submit(request));
                else send(TezgahWebDto.json("v", 1, "type", "komut_sonucu", "id", request.optString("id"),
                        "durum", "reddedildi", "hata", "Bu bilgisayar yalnız görüntüleme yetkisine sahip."));
                return;
            }
            String id=request.optString("id","");if(!id.matches("[A-Za-z0-9_-]{1,64}")||request.optInt("v")!=1||!request.optString("type").equals("istek"))throw new IllegalArgumentException("GECERSIZ_ISTEK");
            String op=request.optString("op");try{
                if(!op.equals("bugunun_alimlari") && commands == null)throw new IllegalArgumentException("SALT_OKUNUR");
                JSONObject args=request.optJSONObject("args");if(args==null)args=new JSONObject();
                JSONObject data = op.equals("bugunun_alimlari") ? repository.read(args,System.currentTimeMillis()) : commands.read(op, args);send(TezgahWebDto.json("v",1,"type","cevap","id",id,"op",op,"ok",true,"data",data));
            }catch(Exception e){String code=e instanceof IllegalArgumentException?e.getMessage():"VERI_OKUNAMADI";send(TezgahWebDto.json("v",1,"type","hata","id",id,"code",safeCode(code),"message","Veri okunamadı. Telefonda bağlantıyı kontrol et.","retryable",true));}
            return;
        }
        throw new IllegalArgumentException("GECERSIZ_AKIS");
    }
    private String pendingEpoch,pendingNb;
    private static String safeCode(String code){return code!=null&&code.matches("[A-Z_]{1,40}")?code:"VERI_OKUNAMADI";}
    private void activate(){channel=new TezgahWebCrypto.Channel(keys,session,epoch,"p2w");ready=true;send(TezgahWebDto.json("v",1,"type","onaylandi","session",session,"permissions",permissions(),"maxAgeSeconds",28800,"idleLockSeconds",300));state.accept(true);}
    private void send(JSONObject payload){if(!ready()||!approval.canRead())return;output.accept(channel.seal(TezgahWebProtocol.utf8(payload.toString())).json());}
    public void changed(long revision){if(ready())send(TezgahWebDto.json("v",1,"type","olay","event","veri_degisti","sourceEpoch",TezgahWebChanges.SOURCE,"revision",Long.toString(revision),"scopes",new JSONArray().put("alimlar")));}
    public boolean expired(long now){return closed||(active&&now-activeAt>=28800000)||(!active&&System.currentTimeMillis()/1000>=qr.expiresAt)||(active&&lost>=0&&now-lost>300000);}
    public void close(){if(commands!=null)commands.close();closed=true;ready=false;approval.close();if(channel!=null)channel.close();if(guard!=null)guard.close();if(keys!=null)keys.close();if(root!=null)Arrays.fill(root,(byte)0);repository.close();hello=null;capability=null;state.accept(false);}
}
