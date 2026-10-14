package com.zeytindefteri.app;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Ana Sayfa "Zeytin Ligi" kartının saf (Android'siz) durum kuralları.
 *
 * Kurallar:
 * - Yalnızca kullanıcının kendi sırası ve puan farkı gösterilir; başka üreticinin
 *   kilosu/tutarı asla gösterilmez.
 * - Sıra yalnızca sunucu yayımladığında yazılır; telefondaki ön puan "sıra" gibi gösterilmez.
 * - Kupa yarışı (Top 20) sunucuda en az {@link LeagueBoardModel#MIN_RANKED} sıralanabilir
 *   üreticiyle başlar; uygulama bu sayıyı yayından önce bilemez, bu yüzden "x üretici daha"
 *   gibi kesin bir sayı yazılmaz.
 */
public final class HomeLeagueModel {
    private HomeLeagueModel() {}

    public enum State { WAITING, RANKED, LEADER, MISSING }

    public static final class Input {
        /** "Alaşehir / Manisa" — yerel arşivden ya da yayından gelen bölge adı. */
        public String regionName = "";
        public double localKg;
        /** Güncel (24 saat içinde) sunucu sıralaması okundu mu. */
        public boolean published;
        /** Kullanıcının bölgesinde yayımlanmış Top 20 var mı (yarış başladı mı). */
        public boolean raceStarted;
        /** Sunucudaki doğrulanmış sıra; yoksa 0. */
        public int myRank;
        public double myScore;
        /** Kullanıcının bölgesindeki Top 20 puanları, sıra düzeninde. */
        public double[] topScores = new double[0];
        /** Supabase bölge dizinindeki üretici sayısı; bilinmiyorsa -1. */
        public int regionProducers = -1;
    }

    public static final class Card {
        public State state;
        public String overline, title, sub;
        /** İlerleme çubuğu yüzdesi; çubuk yoksa -1. */
        public int progressPercent = -1;
        public String progressText = "";
        public boolean invite;
        public boolean checklist;
        public boolean kgOk;
        public String accessibility;
    }

    private static final Locale TR = new Locale("tr", "TR");

    /** Gösterilecek bir şey yoksa (bölge bilinmiyor) null. */
    public static Card build(Input in) {
        if (in == null) return null;
        String region = in.regionName == null ? "" : in.regionName.trim();
        if (region.isEmpty() || "Bölge belirtilmemiş".equals(region)) return null;
        String district = LeagueBoardModel.splitRegion(region)[0];
        Card c = new Card();
        c.overline = "ZEYTİN LİGİ · " + district.toUpperCase(TR);
        if (in.published && in.myRank > 0) {
            if (in.myRank == 1) {
                c.state = State.LEADER;
                c.title = district + " kupası şu an sende";
                c.sub = "Sezon sonuna kadar liderliğini koru";
            } else {
                c.state = State.RANKED;
                c.title = locative(district) + " " + in.myRank + ". sıradasın";
                c.sub = rankedSub(in);
            }
        } else if (in.published && in.raceStarted) {
            c.state = State.MISSING;
            c.title = "Sıralamaya girmene bir adım var";
            c.kgOk = in.localKg >= ZeytinLeagueScore.MIN_KG;
            c.checklist = true;
            c.sub = c.kgOk ? "Bir satışı \"piyasa verisine dahil et\" ile kaydet"
                    : "Sezonda en az 100 kg elek kaydı gerekli";
        } else {
            c.state = State.WAITING;
            c.title = district + " kupası sahipsiz";
            c.sub = "Yarış bölgede " + LeagueBoardModel.MIN_RANKED + " üretici sıralamaya girince başlar";
            c.invite = true;
            if (in.regionProducers >= 0) {
                c.progressPercent = Math.min(100, Math.round(100f * in.regionProducers / LeagueBoardModel.MIN_RANKED));
                c.progressText = "Bölgede şu an " + in.regionProducers + " üretici · sıralama için 100 kg ve paylaşılan satış gerekir";
            } else {
                c.progressText = "Sıralama için 100 kg ve paylaşılan satış gerekir";
            }
        }
        c.accessibility = "Zeytin Ligi, " + district + ": " + c.title + ". " + c.sub + ".";
        return c;
    }

    private static String rankedSub(Input in) {
        double[] top = in.topScores == null ? new double[0] : in.topScores;
        if (in.myRank <= 3) {
            if (top.length == 0) return "Kürsüdesin";
            double gap = top[0] - in.myScore;
            return gap > 0.05 ? "Kürsüdesin · liderliğe " + points(gap) + " puan" : "Kürsüdesin · liderle başa baş";
        }
        if (top.length < 3) return "Kürsüye yaklaşmak için kayıtlarını güncel tut";
        double gap = top[2] - in.myScore;
        return gap > 0.05 ? "Kürsüye " + points(gap) + " puan kaldı" : "Kürsüye çok yakınsın";
    }

    /** Türkçe bulunma eki: Alaşehir'de, Edremit'te, Gemlik'te, Ayvalık'ta, Salihli'de. */
    static String locative(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return "Bölgende";
        String lower = n.toLowerCase(TR);
        char lastVowel = 'e';
        for (int i = lower.length() - 1; i >= 0; i--) {
            if ("aeıioöuü".indexOf(lower.charAt(i)) >= 0) { lastVowel = lower.charAt(i); break; }
        }
        boolean back = "aıou".indexOf(lastVowel) >= 0;
        char last = lower.charAt(lower.length() - 1);
        boolean hard = "fstkçşhp".indexOf(last) >= 0;
        return n + "'" + (hard ? "t" : "d") + (back ? "a" : "e");
    }

    static String points(double value) {
        NumberFormat nf = NumberFormat.getNumberInstance(TR);
        nf.setMinimumFractionDigits(1);
        nf.setMaximumFractionDigits(1);
        return nf.format(Math.max(0, value));
    }

    /** Kartın görünen içeriğinin özeti; aynıysa kart yeniden çizilmez (atlama olmaz). */
    public static String signature(Card c) {
        if (c == null) return "none";
        return c.state + "|" + c.overline + "|" + c.title + "|" + c.sub + "|" + c.progressPercent + "|"
                + c.progressText + "|" + c.kgOk;
    }

    /** Son durumu ayarlarda saklamak için kısa metin (uygulama yeniden açılınca anında çizilir). */
    public static String encode(Input in) { return encode(in, System.currentTimeMillis()); }

    static String encode(Input in, long savedAtMs) {
        if (in == null) return "";
        StringBuilder top = new StringBuilder();
        double[] t = in.topScores == null ? new double[0] : in.topScores;
        for (int i = 0; i < Math.min(3, t.length); i++) { if (i > 0) top.append(';'); top.append(t[i]); }
        return "v1\t" + clean(in.regionName) + "\t" + in.localKg + "\t" + (in.published ? 1 : 0) + "\t"
                + (in.raceStarted ? 1 : 0) + "\t" + in.myRank + "\t" + in.myScore + "\t" + in.regionProducers + "\t" + top + "\t" + savedAtMs;
    }

    /** Bozuk/eski kayıtta null döner; çağıran yerel arşive düşer. */
    public static Input decode(String value) { return decode(value, System.currentTimeMillis()); }

    /** Kayıt 24 saatten eskiyse sıra bilgisi atılır (sıra yalnızca güncel yayından gösterilir). */
    static Input decode(String value, long nowMs) {
        if (value == null || !value.startsWith("v1\t")) return null;
        String[] p = value.split("\t", -1);
        if (p.length != 10) return null;
        try {
            Input in = new Input();
            in.regionName = p[1];
            in.localKg = Double.parseDouble(p[2]);
            in.published = "1".equals(p[3]);
            in.raceStarted = "1".equals(p[4]);
            in.myRank = Integer.parseInt(p[5]);
            in.myScore = Double.parseDouble(p[6]);
            in.regionProducers = Integer.parseInt(p[7]);
            if (p[8].isEmpty()) in.topScores = new double[0];
            else {
                String[] t = p[8].split(";");
                in.topScores = new double[t.length];
                for (int i = 0; i < t.length; i++) in.topScores[i] = Double.parseDouble(t[i]);
            }
            long savedAt = Long.parseLong(p[9]);
            if (savedAt <= 0 || Math.abs(nowMs - savedAt) > 86400000L) {
                in.published = false; in.raceStarted = false; in.myRank = 0; in.myScore = 0; in.topScores = new double[0];
            }
            return in;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String clean(String v) { return v == null ? "" : v.replace('\t', ' ').replace('\n', ' ').trim(); }

    /** "Davet et" paylaşım metni; uydurma bağlantı eklenmez. */
    public static String inviteText(String regionName) {
        String district = LeagueBoardModel.splitRegion(regionName)[0];
        if (district.isEmpty()) district = "Bölgemizin";
        return "Zeytin Defteri'nde " + district + " kupası henüz sahipsiz; yarışın başlaması için bölgeden en az "
                + LeagueBoardModel.MIN_RANKED + " üretici gerekiyor. Elek ve satışlarını Zeytin Defteri'ne kaydet, "
                + "bölgemizin fiyatlarını birlikte görelim.";
    }
}
