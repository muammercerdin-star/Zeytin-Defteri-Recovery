package com.zeytindefteri.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Asistanın ölçüsü (İP1): cümle kaydı ve karne SharedPreferences'ta, yalnız bu telefonda tutulur; hiçbir yere
 * gönderilmez. Kullanıcının gördüğü davranışı değiştirmez; hata olursa sessizce geçer (ölçü işi bozmaz).
 *
 * Cümle kaydı: zd_assistant_misses / "l_" + sahip (AssistantMisses.log; anlamadıklarım listesi "m_" aynen kalır).
 * Karne: zd_assistant_scorecard / "k_" + sahip (AssistantScorecard; yalnız sayılar).
 */
final class AssistantMeter {
    static final String MISSES_PREFS = "zd_assistant_misses";
    static final String SCORE_PREFS = "zd_assistant_scorecard";

    private AssistantMeter() {
    }

    static String logKey(String owner) {
        return "l_" + (owner == null ? "" : owner);
    }

    static String scoreKey(String owner) {
        return "k_" + (owner == null ? "" : owner);
    }

    /** Asistan cümleyi bir eyleme çevirdi. */
    static void understood(Context context, String owner, String raw, String action) {
        log(context, owner, AssistantMisses.UNDERSTOOD, raw, action);
        count(context, owner, AssistantScorecard.UNDERSTOOD, 1);
    }

    /** Asistan cümleyi anlamadı (anlamadıklarım listesine ayrıca yazılır; burası kayıt ve karne). */
    static void missed(Context context, String owner, String raw) {
        log(context, owner, AssistantMisses.MISSED, raw, "");
        count(context, owner, AssistantScorecard.MISSED, 1);
    }

    /** "Anlamadıklarını sil": kayıttaki anlaşılmayan cümleler de gider. */
    static synchronized void clearMissed(Context context, String owner) {
        try {
            SharedPreferences p = context.getSharedPreferences(MISSES_PREFS, Context.MODE_PRIVATE);
            String key = logKey(owner);
            String next = AssistantMisses.clearMissedFromLog(p.getString(key, ""));
            p.edit().putString(key, next).apply();
        } catch (RuntimeException ignored) {
            // ölçü işi bozmaz
        }
    }

    /** "Konuşma kaydını sil": cümle kaydı, karne ve anlamadıklarım listesi bu telefondan silinir. */
    static synchronized void clearAll(Context context, String owner) {
        try {
            SharedPreferences misses = context.getSharedPreferences(MISSES_PREFS, Context.MODE_PRIVATE);
            String o = owner == null ? "" : owner;
            misses.edit().remove(logKey(owner)).remove("m_" + o).apply();
            SharedPreferences score = context.getSharedPreferences(SCORE_PREFS, Context.MODE_PRIVATE);
            score.edit().remove(scoreKey(owner)).apply();
        } catch (RuntimeException ignored) {
            // ölçü işi bozmaz
        }
    }

    /**
     * Kayıt ekranında Kaydet başarılı oldu. openedAt: ekranın açıldığı (ya da önceki kaydın yapıldığı) an,
     * SystemClock.elapsedRealtime(); sesle doldurulan alanlar varsa düzeltilenler sayılır ve kayda yazılır.
     */
    static synchronized void saved(Context context, String owner, String form, long openedAt,
                      AssistantScorecard.FormFill fill, Map<String, Double> savedValues) {
        try {
            long ms = android.os.SystemClock.elapsedRealtime() - openedAt;
            SharedPreferences p = context.getSharedPreferences(SCORE_PREFS, Context.MODE_PRIVATE);
            String key = scoreKey(owner);
            String card = p.getString(key, "");
            if (openedAt > 0) {
                card = AssistantScorecard.span(card, ms);
            }
            if (fill != null && fill.size() > 0) {
                List<String> fixed = fill.corrected(savedValues);
                card = AssistantScorecard.count(card, AssistantScorecard.FILLED, fill.size());
                card = AssistantScorecard.count(card, AssistantScorecard.CORRECTED, fixed.size());
                if (!fixed.isEmpty()) {
                    log(context, owner, AssistantMisses.CORRECTED, form, join(fixed));
                }
            }
            p.edit().putString(key, card).apply();
        } catch (RuntimeException ignored) {
            // ölçü işi bozmaz
        }
    }

    /** "Karneni göster": {okunacak söz, ekrandaki metin}. */
    static String[] show(Context context, String owner) {
        String card = "";
        try {
            card = context.getSharedPreferences(SCORE_PREFS, Context.MODE_PRIVATE).getString(scoreKey(owner), "");
        } catch (RuntimeException ignored) {
            card = "";
        }
        return AssistantScorecard.show(card);
    }

    private static synchronized void log(Context context, String owner, String kind, String raw, String detail) {
        try {
            SharedPreferences p = context.getSharedPreferences(MISSES_PREFS, Context.MODE_PRIVATE);
            String key = logKey(owner);
            String day = new SimpleDateFormat("d MMM", new Locale("tr", "TR")).format(new Date());
            String next = AssistantMisses.log(p.getString(key, ""), kind, raw, detail, day);
            p.edit().putString(key, next).apply();
        } catch (RuntimeException ignored) {
            // ölçü işi bozmaz
        }
    }

    private static synchronized void count(Context context, String owner, String key, long delta) {
        try {
            SharedPreferences p = context.getSharedPreferences(SCORE_PREFS, Context.MODE_PRIVATE);
            String k = scoreKey(owner);
            String next = AssistantScorecard.count(p.getString(k, ""), key, delta);
            p.edit().putString(k, next).apply();
        } catch (RuntimeException ignored) {
            // ölçü işi bozmaz
        }
    }

    private static String join(List<String> items) {
        StringBuilder b = new StringBuilder();
        for (String s : items) {
            if (b.length() > 0) {
                b.append(", ");
            }
            b.append(s);
        }
        return b.toString();
    }
}
