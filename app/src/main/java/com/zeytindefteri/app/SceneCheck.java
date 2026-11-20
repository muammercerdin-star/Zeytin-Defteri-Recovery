package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Sahne denetimi (saf, testli): Tanecik kayıt, satış, ödeme ve silme YAPMAZ; Kaydet / Sil düğmelerine hiç dokunamaz.
 * Oynatıcı (TanecikGuide) her sahneyi oynatmadan önce buradan geçirir; hatası olan sahne hiç oynatılmaz.
 *
 * - Dokunan adımlar (SELECT, TYPE) kayıt / silme / ödeme düğmesine gidemez (adına bakılır: save, kaydet, sil, delete,
 *   onay, confirm, pay...). Yalnız parlatmak (HIGHLIGHT) serbesttir: "Kaydet'e sen basarsın" diye gösterilebilir.
 * - GÖSTER ve ÖĞRET hiçbir şeye dokunmaz (SELECT yok); ÖĞRET yazdığını sonunda siler (oynatıcının tur kipi).
 * - ZARARSIZ İŞ yalnız çiftçinin izniyle oynar (allowed()).
 * - Her sahne bir ekran açar ve STOP ile biter; en çok 10 adım.
 */
final class SceneCheck {
    private static final String FORBIDDEN = "(?i).*(save|kaydet|sil|delete|remove|onay|confirm|pay|odeme|tahsil|submit).*";
    /** Doldurucunun (İP7 HAZIRLA) dokunabildiği ödeme seçim düğmeleri: kayıt değil, yalnız formdaki seçim. */
    private static final String PREPARE_CHIPS = "chipPayNone|chipPayFull";

    private SceneCheck() {
    }

    /** Sahnenin hataları; boşsa sahne oynatılabilir. */
    static List<String> errors(TanecikScenes.Scene scene) {
        List<String> out = new ArrayList<>();
        if (scene == null) {
            out.add("sahne yok");
            return out;
        }
        if (scene.steps.isEmpty() || scene.steps.size() > 10) {
            out.add("adım sayısı 1–10 olmalı");
        }
        boolean opens = false;
        for (TanecikScenes.Step st : scene.steps) {
            if (st.kind == TanecikScenes.Kind.OPEN) {
                opens = true;
                if (!TanecikScenes.SCREENS.containsKey(st.target)) {
                    out.add("bilinmeyen ekran: " + st.target);
                }
            }
            boolean touches = st.kind == TanecikScenes.Kind.SELECT || st.kind == TanecikScenes.Kind.TYPE;
            if (!touches) {
                continue;
            }
            boolean chip = st.target.matches(PREPARE_CHIPS);
            if (st.target.matches(FORBIDDEN) && !(chip && scene.level == TanecikScenes.Level.HAZIRLA)) {
                out.add("Tanecik kayıt / silme / ödeme düğmesine dokunamaz: " + st.target);
            }
            if (st.kind == TanecikScenes.Kind.SELECT && scene.level != TanecikScenes.Level.HAZIRLA
                    && scene.level != TanecikScenes.Level.ZARARSIZ) {
                out.add(scene.level + " sahnesi hiçbir şeye dokunmaz: " + st.target);
            }
            if (st.kind == TanecikScenes.Kind.TYPE && scene.level == TanecikScenes.Level.GOSTER) {
                out.add("GÖSTER sahnesi alana yazmaz: " + st.target);
            }
        }
        if (!opens) {
            out.add("sahne bir ekran açmalı");
        }
        TanecikScenes.Step last = scene.steps.isEmpty() ? null : scene.steps.get(scene.steps.size() - 1);
        if (last == null || last.kind != TanecikScenes.Kind.STOP) {
            out.add("sahne STOP ile bitmeli");
        }
        return out;
    }

    /** Sahne şimdi oynatılabilir mi: hatasız ve ZARARSIZ İŞ ise çiftçinin izni var. */
    static boolean allowed(TanecikScenes.Scene scene, boolean farmerPermitted) {
        if (!errors(scene).isEmpty()) {
            return false;
        }
        return scene.level != TanecikScenes.Level.ZARARSIZ || farmerPermitted;
    }

    /** Gece (22:00–05:00) Tanecik konuşmaz; sahne yalnız ekranda gösterilir. */
    static boolean speaks(int hour) {
        return !GuideSettings.night(hour);
    }
}
