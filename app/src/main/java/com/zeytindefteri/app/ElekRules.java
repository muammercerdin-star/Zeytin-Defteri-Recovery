package com.zeytindefteri.app;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kural beyninin elek parçası (İP3; saf, hafif). Elek cümlesini ("3 numara 120 kilo, double 2 kilo 220 lira")
 * ELEK kararına çeviren TEK yer: asistan (RuleBrain'in kayıt aşaması) ve Elek ekranındaki "Sesle doldur"
 * (ElekVoiceDialog) aynı fonksiyondan geçer. Yalnız Java + çözücüler; asistanın ağır sınıflarını çekmez.
 *
 * Elek ekranında konuşma bir düzeltme de olabilir ("3 numarayı 100 olarak değiştir"): orada "değiştir/güncelle"
 * sözleri çözümden önce atılır; asistanda atılmaz (bugünkü davranış).
 */
final class ElekRules implements Brain {
    static final String NAME = Brain.RULES;
    private static final String[] GRADE_KEYS = {"double", "1no", "2no", "3no", "4no", "yaglik"};

    @Override
    public String name() {
        return NAME;
    }

    /** Elek ekranı: cümle yalnız elek olarak okunur; elek değilse ANLASILMADI (diyalog kendi sözlerine bakar). */
    @Override
    public Decision decide(Input in, BrainContext context) {
        Decision d = decide(in.raw, context == null ? BrainContext.Screen.ELEK : context.screen);
        if (d != null) {
            return d;
        }
        return Decision.miss("", "Elek sınıfı ve kilo duyulmadı", NAME);
    }

    /** Elek kararı ya da null (cümle elek değil). */
    static Decision decide(String raw, BrainContext.Screen screen) {
        String text = screen == BrainContext.Screen.ELEK ? withoutEditWords(raw) : raw;
        ElekVoiceEntry e = ElekVoiceEntry.parse(text);
        if (e == null) {
            return null;
        }
        ActionCatalog.Action a = ActionCatalog.get(ActionCatalog.ELEK);
        return new Decision(ActionCatalog.ELEK, fields(e), 1, "Elek sınıfı ve kilo söylendi", "", a.level, e, NAME);
    }

    /** Elek ekranında düzeltme sözleri ("değiştir", "güncelle") çözümden önce atılır. */
    static String withoutEditWords(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("(?iu)\\b(de[gğ]i[sş]tir|g[uü]ncelle)\\p{L}*", " ");
    }

    /** Alanlar, cümle bankasının yazımıyla: double, 1no..4no, yaglik, <boy>_fiyat, satis, alici. */
    static Map<String, String> fields(ElekVoiceEntry e) {
        Map<String, String> f = new LinkedHashMap<>();
        for (int i = 0; i < e.kg.length; i++) {
            if (e.kg[i] > 0) {
                f.put(GRADE_KEYS[i], Decision.num(e.kg[i]));
            }
            if (e.price[i] > 0) {
                f.put(GRADE_KEYS[i] + "_fiyat", Decision.num(e.price[i]));
            }
        }
        if (e.sold != null) {
            f.put("satis", e.sold ? "evet" : "hayir");
        }
        if (!e.buyer.isEmpty()) {
            f.put("alici", e.buyer);
        }
        return f;
    }
}
