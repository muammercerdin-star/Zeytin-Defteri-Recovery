package com.zeytindefteri.app;

import android.app.Activity;
import android.content.Intent;

/**
 * Tanecik sahnesini ekrandan ekrana taşır (yönlendiren asistan). Asistan sahnenin ekranını açar (open), açılan ekran
 * sahneyi TEK oynatıcıyla (TanecikGuide sahne kipi) oynatır (playIfAsked). Her iş için ayrı kod yok: ekranlar yalnız
 * playIfAsked'i çağırır. Karne için oynatılan / sonuna kadar izlenen / durdurulan sahneler sayılır (GuideDb).
 */
final class ScenePlayer {
    static final String EXTRA_SCENE = "tanecik_sahne";
    static final String EXTRA_ANSWER = "tanecik_cevap";

    private ScenePlayer() {
    }

    /** Sahnenin açtığı ekranın sınıfı (TanecikScenes.SCREENS ile aynı adlar). */
    static Class<?> screen(String id) {
        switch (id) {
            case "BUYERS": return BuyersActivity.class;
            case "OPERATIONS": return OperationHistoryActivity.class;
            case "REMINDERS": return RemindersActivity.class;
            case "REPORT": return ReportActivity.class;
            case "MARKET": return MarketActivity.class;
            case "ELEK": return ElekActivity.class;
            case "SALE": return SaleActivity.class;
            default: return null;
        }
    }

    /** Sahnenin açtığı ekran kimliği ("BUYERS"); yoksa "". */
    static String screenOf(TanecikScenes.Scene scene) {
        for (TanecikScenes.Step st : scene.steps) {
            if (st.kind == TanecikScenes.Kind.OPEN) {
                return st.target;
            }
        }
        return "";
    }

    /** Asistandan: sahnenin ekranını açar; ekran açılınca sahne oynar. Açılamazsa false. */
    static boolean open(Activity from, TanecikScenes.Scene scene, String answer) {
        if (!SceneCheck.allowed(scene, false)) {
            return false;
        }
        Class<?> cls = screen(screenOf(scene));
        if (cls == null) {
            return false;
        }
        Intent intent = new Intent(from, cls);
        intent.putExtra(EXTRA_SCENE, scene.id);
        intent.putExtra(EXTRA_ANSWER, answer == null ? "" : answer);
        from.startActivity(intent);
        return true;
    }

    /** Açılan ekran: sahne istendiyse oynatır (ekran dizildikten sonra); istenmediyse null. */
    static TanecikGuide playIfAsked(Activity activity) {
        Intent intent = activity.getIntent();
        String id = intent == null ? null : intent.getStringExtra(EXTRA_SCENE);
        if (id == null) {
            return null;
        }
        String answer = intent.getStringExtra(EXTRA_ANSWER);
        intent.removeExtra(EXTRA_SCENE);
        intent.removeExtra(EXTRA_ANSWER);
        TanecikScenes.Scene scene = TanecikScenes.get(id);
        if (scene == null) {
            return null;
        }
        String owner = owner(activity);
        String level = scene.level.name();
        TanecikGuide guide = TanecikGuide.scene(activity, scene, answer, false, ActionRunner.fast(activity), completed -> {
            GuideDb.add(activity, owner, (completed ? "sahne_bitti:" : "sahne_durdu:") + level);
        });
        if (guide == null) {
            return null;
        }
        GuideDb.add(activity, owner, "sahne_oynatildi:" + level);
        activity.getWindow().getDecorView().postDelayed(guide::start, 600);
        return guide;
    }

    private static String owner(Activity activity) {
        DatabaseHelper helper = new DatabaseHelper(activity.getApplicationContext());
        try {
            return helper.getActiveOwnerUid();
        } catch (RuntimeException e) {
            return "";
        } finally {
            helper.close();
        }
    }
}
