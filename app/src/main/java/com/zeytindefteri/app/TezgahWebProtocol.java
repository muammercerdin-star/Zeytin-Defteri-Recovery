package com.zeytindefteri.app;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** v1 taşıma sözleşmesi. Android/JSON bağımlılığı yok; veri sorgusu veya onay ekranı içermez. */
public final class TezgahWebProtocol {
    public static final int VERSION = 1, MAX_FRAME_BYTES = 64 * 1024, MAX_PLAIN_BYTES = 32 * 1024;
    public static final long RESUME_WINDOW_MS = 300_000L;
    private TezgahWebProtocol() { }

    public static void require(boolean ok, String code) {
        if (!ok) throw new IllegalArgumentException(code);
    }
    public static void token(String s, int bytes) {
        require(s != null && TezgahWebCrypto.unbase64(s).length == bytes, "GECERSIZ_ALAN");
    }
    public static void direction(String dir) { require("p2w".equals(dir) || "w2p".equals(dir), "GECERSIZ_YON"); }
    public static long sequence(String value) {
        require(value != null && value.matches("[1-9][0-9]{0,18}"), "GECERSIZ_SAYAC");
        try { long n = Long.parseLong(value); require(n > 0, "GECERSIZ_SAYAC"); return n; }
        catch (NumberFormatException e) { throw new IllegalArgumentException("GECERSIZ_SAYAC"); }
    }
    public static byte[] transcript(String room, long expiresAt, String nb, String np, String b, String p) {
        token(room, 16); token(nb, 32); token(np, 32);
        require(expiresAt > 0 && expiresAt <= 9_007_199_254_740_991L, "GECERSIZ_ZAMAN");
        TezgahWebCrypto.publicKey(b); TezgahWebCrypto.publicKey(p);
        return utf8("[\"zd-web\",1,\"" + room + "\"," + expiresAt + ",\"" + nb + "\",\"" + np
                + "\",\"" + b + "\",\"" + p + "\"]");
    }
    public static byte[] resumeTranscript(String session, String oldEpoch, String epoch, String nb, String np) {
        token(session, 16); token(oldEpoch, 16); token(epoch, 16); token(nb, 32); token(np, 32);
        require(!oldEpoch.equals(epoch), "ESKI_EPOCH");
        return utf8("[\"zd-web-resume\",1,\"" + session + "\",\"" + oldEpoch + "\",\"" + epoch
                + "\",\"" + nb + "\",\"" + np + "\"]");
    }
    public static byte[] aad(String session, String epoch, String dir, String seq) {
        token(session, 16); token(epoch, 16); direction(dir); sequence(seq);
        return utf8("[\"zd-web\",1,\"" + session + "\",\"" + epoch + "\",\"" + dir + "\",\"" + seq + "\"]");
    }
    public static byte[] utf8(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    public static final class Frame {
        public final String session, epoch, dir, seq, ct;
        public Frame(String session, String epoch, String dir, String seq, String ct) {
            token(session, 16); token(epoch, 16); direction(dir); sequence(seq);
            int n = TezgahWebCrypto.unbase64(ct).length;
            require(n >= 16 && n <= MAX_PLAIN_BYTES + 16, "BOYUT_SINIRI");
            this.session = session; this.epoch = epoch; this.dir = dir; this.seq = seq; this.ct = ct;
        }
        public String json() {
            return "{\"v\":1,\"type\":\"encrypted\",\"session\":\"" + session + "\",\"epoch\":\"" + epoch
                    + "\",\"dir\":\"" + dir + "\",\"seq\":\"" + seq + "\",\"ct\":\"" + ct + "\"}";
        }
        /** Yalnız düz ASCII zarf; alan sırası serbest, yinelenen/bilinmeyen/kaçışlı alan reddedilir. */
        public static Frame parse(String json) {
            require(json != null && utf8(json).length <= MAX_FRAME_BYTES, "BOYUT_SINIRI");
            FlatReader r = new FlatReader(json); Map<String, String> m = r.read();
            require(m.size() == 7 && "#1".equals(m.get("v")) && "encrypted".equals(m.get("type")), "GECERSIZ_ZARF");
            require(m.keySet().containsAll(java.util.Arrays.asList("v", "type", "session", "epoch", "dir", "seq", "ct")), "GECERSIZ_ZARF");
            return new Frame(m.get("session"), m.get("epoch"), m.get("dir"), m.get("seq"), m.get("ct"));
        }
    }
    private static final class FlatReader {
        final String s; int i;
        FlatReader(String s) { this.s = s; }
        void ws() { while (i < s.length() && " \t\r\n".indexOf(s.charAt(i)) >= 0) i++; }
        void take(char c) { ws(); require(i < s.length() && s.charAt(i++) == c, "GECERSIZ_ZARF"); }
        String string() {
            take('"'); int start = i;
            while (i < s.length() && s.charAt(i) != '"') {
                char c = s.charAt(i++); require(c >= 32 && c <= 126 && c != '\\', "GECERSIZ_ZARF");
            }
            String result = s.substring(start, i); take('"'); return result;
        }
        Map<String, String> read() {
            Map<String, String> out = new LinkedHashMap<>(); take('{');
            while (true) {
                String key = string(); take(':'); ws();
                String val;
                if (i < s.length() && s.charAt(i) == '"') { require(!key.equals("v"), "GECERSIZ_ZARF"); val = string(); }
                else { require(key.equals("v") && i < s.length() && s.charAt(i++) == '1', "GECERSIZ_ZARF"); val = "#1"; }
                require(!out.containsKey(key) && out.size() < 7, "GECERSIZ_ZARF"); out.put(key, val);
                ws(); require(i < s.length(), "GECERSIZ_ZARF"); if (s.charAt(i) == '}') { i++; break; }
                take(',');
            }
            ws(); require(i == s.length(), "GECERSIZ_ZARF"); return out;
        }
    }
    /** Adım 3 bu kapıyı gerçek telefon UI onayına bağlamalı; relay onayı bunun yerine geçmez. */
    public static final class Approval {
        private boolean confirmed, approved, closed;
        public synchronized void peerConfirmed() { require(!closed, "OTURUM_IPTAL"); confirmed = true; }
        public synchronized void phoneApproved() { require(confirmed && !closed, "ONAY_GEREKLI"); approved = true; }
        public synchronized boolean canRead() { return confirmed && approved && !closed; }
        public synchronized void close() { closed = true; approved = false; confirmed = false; }
    }
}
