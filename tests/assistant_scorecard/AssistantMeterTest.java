package com.zeytindefteri.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.util.HashMap;
import java.util.Map;

/** AssistantMeter: cümle kaydı + karne telefonda (taklit SharedPreferences ile), kullanıcı başına ayrı. */
public class AssistantMeterTest {
    static int n = 0;
    static int bad = 0;

    static void check(boolean ok, String m) {
        n++;
        if (!ok) {
            bad++;
            System.out.println("FAIL " + m);
        }
    }

    /** Bellekte tutan taklit. */
    static final class Prefs implements SharedPreferences {
        final Map<String, String> data = new HashMap<>();

        @Override
        public String getString(String key, String defValue) {
            String v = data.get(key);
            return v == null ? defValue : v;
        }

        @Override
        public Editor edit() {
            return new Editor() {
                @Override
                public Editor putString(String key, String value) {
                    data.put(key, value);
                    return this;
                }

                @Override
                public Editor remove(String key) {
                    data.remove(key);
                    return this;
                }

                @Override
                public void apply() {
                }
            };
        }
    }

    static final class Ctx extends Context {
        final Map<String, Prefs> files = new HashMap<>();

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            Prefs p = files.get(name);
            if (p == null) {
                p = new Prefs();
                files.put(name, p);
            }
            return p;
        }

        String get(String file, String key) {
            return getSharedPreferences(file, 0).getString(key, "");
        }
    }

    public static void main(String[] a) {
        Ctx c = new Ctx();
        AssistantMeter.understood(c, "u1", "Bugün 3 numara 120 kilo", "ELEK");
        AssistantMeter.understood(c, "u1", "Son sulama ne zaman?", "SORU_IRRIGATION");
        AssistantMeter.missed(c, "u1", "bugün bakırladık");
        String card = c.get(AssistantMeter.SCORE_PREFS, "k_u1");
        check(AssistantScorecard.get(card, AssistantScorecard.UNDERSTOOD) == 2, "anlaşılan 2: " + card);
        check(AssistantScorecard.get(card, AssistantScorecard.MISSED) == 1, "anlaşılmayan 1: " + card);
        check(!card.contains("bakır") && !card.contains("numara"), "karnede cümle metni yok: " + card);
        String log = c.get(AssistantMeter.MISSES_PREFS, "l_u1");
        check(AssistantMisses.countLog(log, AssistantMisses.UNDERSTOOD) == 2, "kayıtta 2 anlaşılan");
        check(log.contains("ELEK") && log.contains("SORU_IRRIGATION"), "eylem kayıtta: " + log);
        check(c.get(AssistantMeter.MISSES_PREFS, "m_u1").isEmpty(), "anlamadıklarım listesine (m_) dokunulmaz");
        check(c.get(AssistantMeter.SCORE_PREFS, "k_u2").isEmpty(), "başka kullanıcının karnesi ayrı");

        AssistantMeter.clearMissed(c, "u1");
        log = c.get(AssistantMeter.MISSES_PREFS, "l_u1");
        check(AssistantMisses.countLog(log, AssistantMisses.MISSED) == 0, "anlamadıklarını sil kayıttan da siler");
        check(AssistantMisses.countLog(log, AssistantMisses.UNDERSTOOD) == 2, "anlaşılanlar kalır");

        SystemClock.now = 1_000_000L;
        long opened = SystemClock.elapsedRealtime();
        AssistantScorecard.FormFill fill = new AssistantScorecard.FormFill();
        fill.put("tür", 3);
        fill.put("miktar", 500);
        fill.put("tutar", 300);
        Map<String, Double> saved = new HashMap<>();
        saved.put("tür", 3.0);
        saved.put("miktar", 500.0);
        saved.put("tutar", 350.0);
        SystemClock.now = opened + 14_000L;
        AssistantMeter.saved(c, "u1", "İşlem formu", opened, fill, saved);
        card = c.get(AssistantMeter.SCORE_PREFS, "k_u1");
        check(AssistantScorecard.get(card, AssistantScorecard.FILLED) == 3, "doldurulan 3: " + card);
        check(AssistantScorecard.get(card, AssistantScorecard.CORRECTED) == 1, "düzeltilen 1: " + card);
        check(AssistantScorecard.medianMs(card) == 14_000L, "kayıt süresi 14 sn: " + card);
        log = c.get(AssistantMeter.MISSES_PREFS, "l_u1");
        check(log.contains("duzeltme\tİşlem formu\ttutar"), "düzeltilen alan kayıtta: " + log);

        SystemClock.now = opened + 20_000L;
        AssistantMeter.saved(c, "u1", "Elek formu", opened, new AssistantScorecard.FormFill(), saved);
        card = c.get(AssistantMeter.SCORE_PREFS, "k_u1");
        check(AssistantScorecard.get(card, AssistantScorecard.FILLED) == 3, "sessiz kayıtta doldurulan artmaz");
        check(AssistantScorecard.spans(card).size() == 2, "sessiz kayıtta da süre sayılır");

        String[] shown = AssistantMeter.show(c, "u1");
        check(shown[1].contains("Anlaşılan cümle: 2 / 3"), "karneni göster: " + shown[1]);
        check(AssistantMeter.show(c, "u9")[0].startsWith("Karnem henüz boş"), "yeni kullanıcı boş karne");
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " AssistantMeter");
        if (bad > 0) {
            System.exit(1);
        }
    }
}
