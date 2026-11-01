package com.zeytindefteri.app;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

/** Asistanın alacak cevapları için alıcı listesi (ad, açık alacak, satış sayısı); yalnız okuma. */
final class AssistantReceivablesData {
    private AssistantReceivablesData() {}

    static List<AssistantReceivables.Entry> load(Context context, String owner, int season) {
        List<AssistantReceivables.Entry> out = new ArrayList<>();
        DatabaseHelper db = new DatabaseHelper(context.getApplicationContext());
        try {
            for (BuyerDirectory.Summary b : BuyerDirectory.list(db.getReadableDatabase(), owner, season))
                out.add(new AssistantReceivables.Entry(b.name, b.receivable, b.saleCount));
        } catch (RuntimeException ignored) {
        } finally {
            db.close();
        }
        return out;
    }
}
