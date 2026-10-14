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

    /** "Davet et" paylaşım metni; uydurma bağlantı eklenmez. */
    public static String inviteText(String regionName) {
        String district = LeagueBoardModel.splitRegion(regionName)[0];
        if (district.isEmpty()) district = "Bölgemizin";
        return "Zeytin Defteri'nde " + district + " kupası henüz sahipsiz; yarışın başlaması için bölgeden en az "
                + LeagueBoardModel.MIN_RANKED + " üretici gerekiyor. Elek ve satışlarını Zeytin Defteri'ne kaydet, "
                + "bölgemizin fiyatlarını birlikte görelim.";
    }
}
