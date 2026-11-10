package com.zeytindefteri.app;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.*;
public final class TezgahWebCryptoTest {
    static int checks; static Properties v=new Properties();
    static String s(String key){return v.getProperty("shared."+key);}
    static byte[] b(String key){return TezgahWebCrypto.unbase64(s(key));}
    static byte[] u(String s){return TezgahWebProtocol.utf8(s);}
    static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static void eq(byte[] a,byte[] b,String message){ok(Arrays.equals(a,b),message);}
    static void rejects(Runnable r,String message){boolean bad=false;try{r.run();}catch(IllegalArgumentException e){bad=true;}ok(bad,message);}
    static byte[] hex(String s){byte[] b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return b;}
    public static void main(String[] args)throws Exception{
        try(java.io.Reader r=Files.newBufferedReader(Paths.get(args[0]),StandardCharsets.UTF_8)){v.load(r);}
        for(int i=0;i<3;i++){
            String p="rfc5869."+i+".";
            eq(TezgahWebCrypto.extract(hex(v.getProperty(p+"salt")),hex(v.getProperty(p+"ikm"))),hex(v.getProperty(p+"prk")),"RFC PRK "+i);
            eq(TezgahWebCrypto.hkdf(hex(v.getProperty(p+"salt")),hex(v.getProperty(p+"ikm")),hex(v.getProperty(p+"info")),Integer.parseInt(v.getProperty(p+"length"))),hex(v.getProperty(p+"okm")),"RFC OKM "+i);
        }
        byte[] t=TezgahWebProtocol.transcript(s("room"),Long.parseLong(s("expiresAt")),s("nb"),s("np"),s("browserSpki"),s("phoneSpki"));
        eq(t,u(s("transcript")),"ortak transcript");
        PrivateKey bp=KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(b("testBrowserPkcs8")));
        PrivateKey pp=KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(b("testPhonePkcs8")));
        eq(TezgahWebCrypto.ecdh(bp,s("phoneSpki")),b("ecdh"),"B-P ECDH");eq(TezgahWebCrypto.ecdh(pp,s("browserSpki")),b("ecdh"),"P-B ECDH");
        TezgahWebCrypto.Keys k=TezgahWebCrypto.initial(b("ecdh"),b("secret"),t);
        eq(k.direction("p2w"),b("keys.p2w"),"p2w");eq(k.direction("w2p"),b("keys.w2p"),"w2p");eq(k.confirmation(),b("keys.confirm"),"confirm");eq(k.resumeRoot(),b("keys.resume"),"resume root");
        eq(TezgahWebProtocol.aad(s("session"),s("epoch"),"p2w","1"),u(s("aad")),"AAD");
        TezgahWebCrypto.Channel phone=new TezgahWebCrypto.Channel(k,s("session"),s("epoch"),"p2w"),web=new TezgahWebCrypto.Channel(k,s("session"),s("epoch"),"w2p");
        TezgahWebProtocol.Frame f=phone.seal(u(s("plain")));ok(f.ct.equals(s("ct")),"ortak ciphertext");
        eq(web.open(TezgahWebProtocol.Frame.parse(f.json())),u(s("plain")),"roundtrip");rejects(()->web.open(f),"replay");
        rejects(()->TezgahWebProtocol.Frame.parse(f.json().replace("\"v\":1","\"v\":1,\"v\":1")),"duplicate JSON");
        rejects(()->TezgahWebProtocol.Frame.parse(f.json().replace("\"v\":1","\"v\":\"#1\"")),"string version");
        rejects(()->TezgahWebProtocol.Frame.parse(f.json()+"x"),"JSON suffix");
        byte[] tag=b("ct");tag[tag.length-1]^=1;
        rejects(()->TezgahWebCrypto.decrypt(k.direction("p2w"),1,u(s("aad")),tag),"wrong tag");
        rejects(()->TezgahWebCrypto.decrypt(k.direction("p2w"),1,u(s("aad")+"x"),b("ct")),"wrong AAD");
        rejects(()->TezgahWebCrypto.decrypt(new byte[32],1,u(s("aad")),b("ct")),"wrong key");
        TezgahWebCrypto.Channel receiver=new TezgahWebCrypto.Channel(k,s("session"),s("epoch"),"w2p");
        rejects(()->receiver.open(new TezgahWebProtocol.Frame(f.session,s("resume.epoch"),f.dir,f.seq,f.ct)),"old/wrong epoch");
        rejects(()->receiver.open(new TezgahWebProtocol.Frame(f.session,f.epoch,f.dir,f.seq,TezgahWebCrypto.base64(tag))),"bad tag does not advance seq");
        eq(receiver.open(f),u(s("plain")),"valid after bad frame");
        for(String bad:new String[]{"A","AA=","AA+","AB","a\n","é"})rejects(()->TezgahWebCrypto.unbase64(bad),"base64 canonical");
        for(int n=0;n<100;n++){byte[] a=TezgahWebCrypto.random(n+1);eq(TezgahWebCrypto.unbase64(TezgahWebCrypto.base64(a)),a,"base64 roundtrip");}
        for(String bad:new String[]{"0","01","-1","9223372036854775808","1.0"})rejects(()->TezgahWebProtocol.sequence(bad),"seq bounds");
        rejects(()->TezgahWebCrypto.publicKey(TezgahWebCrypto.base64(new byte[91])),"invalid EC point");
        KeyPair a=TezgahWebCrypto.keyPair(),c=TezgahWebCrypto.keyPair();
        eq(TezgahWebCrypto.ecdh(a.getPrivate(),TezgahWebCrypto.base64(c.getPublic().getEncoded())),TezgahWebCrypto.ecdh(c.getPrivate(),TezgahWebCrypto.base64(a.getPublic().getEncoded())),"random ECDH");
        byte[] rt=TezgahWebProtocol.resumeTranscript(s("session"),s("epoch"),s("resume.epoch"),s("resume.nb"),s("resume.np"));
        eq(rt,u(s("resume.transcript")),"resume transcript");
        TezgahWebCrypto.Keys rk=TezgahWebCrypto.resume(k.resumeRoot(),rt);
        eq(rk.direction("p2w"),b("resume.keys.p2w"),"resume p2w");eq(rk.direction("w2p"),b("resume.keys.w2p"),"resume w2p");
        eq(rk.confirmation(),b("resume.keys.confirm"),"resume confirm");eq(rk.resumeRoot(),b("resume.keys.resume"),"ratchet root");
        ok(!Arrays.equals(rk.direction("p2w"),k.direction("p2w")),"new epoch new keys");
        TezgahWebCrypto.ResumeGuard guard=new TezgahWebCrypto.ResumeGuard(s("session"),s("epoch"),k.resumeRoot());guard.disconnected(1000);
        rejects(()->guard.accept(1001,s("epoch"),s("resume.epoch"),s("resume.nb"),s("resume.np"),new byte[32],b("resume.webProof")),"resume both proofs required");
        TezgahWebCrypto.Keys resumed=guard.accept(301000,s("epoch"),s("resume.epoch"),s("resume.nb"),s("resume.np"),b("resume.phoneProof"),b("resume.webProof"));
        eq(resumed.direction("p2w"),rk.direction("p2w"),"resume guard");guard.disconnected(302000);
        rejects(()->guard.accept(302001,s("epoch"),s("resume.epoch"),s("resume.nb"),s("resume.np"),b("resume.phoneProof"),b("resume.webProof")),"old resume rejected");
        TezgahWebCrypto.ResumeGuard expired=new TezgahWebCrypto.ResumeGuard(s("session"),s("epoch"),k.resumeRoot());expired.disconnected(0);
        rejects(()->expired.accept(300001,s("epoch"),s("resume.epoch"),s("resume.nb"),s("resume.np"),b("resume.phoneProof"),b("resume.webProof")),"5 minute window");
        TezgahWebCrypto.Channel newPhone=new TezgahWebCrypto.Channel(rk,s("session"),s("resume.epoch"),"p2w"),newWeb=new TezgahWebCrypto.Channel(rk,s("session"),s("resume.epoch"),"w2p");
        TezgahWebProtocol.Frame nf=newPhone.seal(u(s("plain")));ok(nf.seq.equals("1")&&!nf.ct.equals(f.ct),"reset seq only with new keys");eq(newWeb.open(nf),u(s("plain")),"resume encrypted");rejects(()->newWeb.open(f),"previous epoch refused");
        TezgahWebProtocol.Approval approval=new TezgahWebProtocol.Approval();ok(!approval.canRead(),"no data before approval");rejects(approval::phoneApproved,"peer proof first");approval.peerConfirmed();ok(!approval.canRead(),"proof not approval");approval.phoneApproved();ok(approval.canRead(),"explicit phone approval");approval.close();ok(!approval.canRead(),"revoked");
        // QR hırsızı kendi P'siyle B'ye bir kanal kurabilir; GERÇEK P-B kanalının anahtarını elde edemez.
        TezgahWebCrypto.Keys thief=TezgahWebCrypto.initial(TezgahWebCrypto.ecdh(a.getPrivate(),s("browserSpki")),b("secret"),t);
        rejects(()->TezgahWebCrypto.decrypt(thief.direction("p2w"),1,u(s("aad")),b("ct")),"QR thief cannot decrypt genuine phone");
        KeyPair browserPair=new KeyPair(TezgahWebCrypto.publicKey(s("browserSpki")),bp),phonePair=new KeyPair(TezgahWebCrypto.publicKey(s("phoneSpki")),pp);
        TezgahWebCrypto.Pairing pairing=new TezgahWebCrypto.Pairing(s("room"),Long.parseLong(s("expiresAt")),b("secret"),s("browserSpki"),s("nb"));
        eq(pairing.phoneKeys(phonePair,s("np"),1).direction("p2w"),k.direction("p2w"),"QR pinned B phone");
        eq(pairing.browserKeys(browserPair,s("phoneSpki"),s("np"),1).confirmation(),k.confirmation(),"QR pinned B browser");
        rejects(()->pairing.browserKeys(a,s("phoneSpki"),s("np"),1),"cannot replace optical B");
        rejects(()->pairing.phoneKeys(phonePair,s("np"),Long.parseLong(s("expiresAt"))),"QR expiry");
        TezgahWebCrypto.Keys fakePhone=pairing.phoneKeys(a,s("np"),1),fakeBrowser=pairing.browserKeys(browserPair,TezgahWebCrypto.base64(a.getPublic().getEncoded()),s("np"),1);
        eq(fakePhone.confirmation(),fakeBrowser.confirmation(),"QR thief CAN establish separate fake-phone channel");
        ok(!Arrays.equals(fakePhone.direction("p2w"),k.direction("p2w")),"fake channel is not genuine channel");
        byte[] proof=TezgahWebCrypto.proof(k.confirmation(),"phone-hello",TezgahWebCrypto.sha256(t));
        TezgahWebCrypto.verify(k.confirmation(),"phone-hello",TezgahWebCrypto.sha256(t),proof);ok(true,"initial proof");
        rejects(()->TezgahWebCrypto.verify(k.confirmation(),"web-finished",TezgahWebCrypto.sha256(t),proof),"proof reflection rejected");
        rejects(()->TezgahWebProtocol.resumeTranscript(s("session"),s("epoch"),s("epoch"),s("resume.nb"),s("resume.np")),"resume must change epoch");
        byte[] otherT=TezgahWebProtocol.resumeTranscript(s("session"),s("epoch"),s("resume.epoch"),s("resume.np"),s("resume.nb"));
        ok(!Arrays.equals(TezgahWebCrypto.resume(k.resumeRoot(),otherT).direction("p2w"),rk.direction("p2w")),"nonce order bound to resume key");
        rejects(()->TezgahWebCrypto.expand(new byte[32],new byte[0],8161),"HKDF length bound");
        rejects(()->phone.seal(new byte[32769]),"plain size bound");
        pairing.close();rejects(()->pairing.phoneKeys(phonePair,s("np"),1),"pairing closed");
        rk.close();rejects(()->rk.direction("p2w"),"closed keys rejected");
        phone.close();rejects(()->phone.seal(new byte[0]),"closed channel");
        Files.write(Paths.get(args[1]),f.json().getBytes(StandardCharsets.UTF_8));
        if(args.length>2){TezgahWebCrypto.Channel fromJs=new TezgahWebCrypto.Channel(k,s("session"),s("epoch"),"p2w");eq(fromJs.open(TezgahWebProtocol.Frame.parse(new String(Files.readAllBytes(Paths.get(args[2])),StandardCharsets.UTF_8))),u(s("plain")),"JS to Java frame");}
        System.out.println("PASS "+checks+"/"+checks+" Java web crypto/protocol");
    }
}
