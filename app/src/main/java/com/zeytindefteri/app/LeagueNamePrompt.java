package com.zeytindefteri.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * "Zeytin Ligi'nde adımla görüneyim" izni (Ayarlar ve Zeytin Ligi kullanır). Varsayılan KAPALI.
 * Açınca ligdeki ön sıralamada adın; sıran, puanın, en çok çıkan numaran ve ortalama kg fiyatınla görünür
 * (onay sürümü 2). Kilon ve toplam tutarın gösterilmez. Kapatınca ad sunucudan silinir, "Anonim üretici" olarak kalırsın.
 */
final class LeagueNamePrompt {
    private LeagueNamePrompt() {}

    static String stateLabel(Activity activity) {
        return MarketContributionPrompt.repository(activity).leagueNameShownLocally() ? "Açık" : "Kapalı";
    }

    /** Profil adından öneri: "Muammer Çerdin" → "Muammer Ç." (soyadının tamamı görünmesin isteyene). */
    static String suggest(String fullName) {
        String n = fullName == null ? "" : fullName.trim().replaceAll("\\s+", " ");
        if (n.isEmpty()) return "";
        String[] parts = n.split(" ");
        if (parts.length < 2) return n;
        String last = parts[parts.length - 1];
        return n.substring(0, n.length() - last.length()) + last.substring(0, 1).toUpperCase(new java.util.Locale("tr", "TR")) + ".";
    }

    static void show(Activity activity, Runnable onChanged) {
        MarketCloudRepository repo = MarketContributionPrompt.repository(activity);
        boolean on = repo.leagueNameShownLocally();
        String saved = repo.leagueNameLocally();
        String profileName = new SessionManager(activity).getUserName();

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        TextView info = new TextView(activity);
        info.setText((repo.leagueConsentOutdated()
                ? "Yenilik: ligde artık ortalama kilogram fiyatı da görünüyor. Onaylarsan adınla birlikte ortalama fiyatın da görünür; onaylamazsan adın görünür, fiyatın \"—\" olur.\n\n" : "")
                + "Açarsan Zeytin Ligi'ndeki bölge sıralamasında adın görünür: sıran, puanın, en çok çıkan "
                + "elek numaran ve ortalama kilogram fiyatın (TL/kg) ile birlikte. Bölgendeki diğer Zeytin Defteri kullanıcıları görebilir.\n\n"
                + "Kilon ve toplam tutarın GÖSTERİLMEZ.\n\n"
                + "Kapatırsan adın sunucudan silinir; sıralamada \"Anonim üretici\" olarak kalırsın."
                + (repo.isContributionOptedIn() ? "" : "\n\nNot: Piyasaya anonim katkın kapalı; sıralamada yer almak için onu da açmalısın."));
        info.setTextSize(14);
        box.addView(info);
        EditText name = new EditText(activity);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        name.setHint("Ligde görünecek adın");
        name.setText(!saved.isEmpty() ? saved : suggest(profileName));
        name.setSelectAllOnFocus(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = pad / 2;
        box.addView(name, lp);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Ligde adımla görüneyim")
                .setView(box)
                .setPositiveButton(on ? (repo.leagueConsentOutdated() ? "Onayla" : "Adı güncelle") : "Adımla görün", null)
                .setNegativeButton("Vazgeç", null)
                .setNeutralButton(on ? "Adımı gizle" : "Gizlilik", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String value = name.getText().toString().trim().replaceAll("\\s+", " ");
                if (value.length() < 2) { name.setError("En az 2 harf"); return; }
                if (value.matches("(?i).*(https?://|www\\.|@).*")) { name.setError("Link ya da e-posta olmasın"); return; }
                dialog.dismiss();
                apply(activity, repo, value, true, onChanged);
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                if (on) { dialog.dismiss(); apply(activity, repo, "", false, onChanged); }
                else LegalDocumentActivity.open(activity, LegalDocuments.PRIVACY);
            });
        });
        dialog.show();
    }

    /**
     * "Ligde adınla görünmek ister misin?" sorusu (LeagueNameAsk karar verir). İki EŞİT düğme: hiçbiri öne
     * çıkarılmaz, varsayılan seçim yoktur (açık rıza). "Adımla görüneyim" adı yazıp onaylatan pencereyi açar;
     * ad, kullanıcı orada onaylamadan kaydedilmez. Pencere nasıl kapanırsa kapansın soru bir daha sorulmaz.
     */
    static void ask(Activity activity, LeagueNameAsk.Kind kind, int rank, String district, Runnable onChanged) {
        if (kind == LeagueNameAsk.Kind.NONE || activity.isFinishing()) return;
        MarketCloudRepository repo = MarketContributionPrompt.repository(activity);
        repo.markLeagueAsk(LeagueNameAsk.FIRST_KEY);
        if (kind == LeagueNameAsk.Kind.TOP3) repo.markLeagueAsk(LeagueNameAsk.TOP3_KEY);

        float density = activity.getResources().getDisplayMetrics().density;
        int pad = Math.round(20 * density);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, pad / 2);
        TextView info = new TextView(activity);
        info.setText(LeagueNameAsk.body(kind));
        info.setTextSize(14);
        info.setTextColor(GreenTheme.color(R.color.zd_text));
        box.addView(info);

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
        bl.topMargin = pad;
        box.addView(buttons, bl);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(LeagueNameAsk.title(kind, rank, district))
                .setView(box)
                .create();
        TextView yes = choice(activity, LeagueNameAsk.YES, density);
        TextView no = choice(activity, LeagueNameAsk.NO, density);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, Math.round(48 * density), 1f);
        left.rightMargin = Math.round(5 * density);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, Math.round(48 * density), 1f);
        right.leftMargin = Math.round(5 * density);
        buttons.addView(no, left);
        buttons.addView(yes, right);
        no.setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(activity, "Anonim kalıyorsun. Ayarlar'dan istediğin zaman değiştirebilirsin.", Toast.LENGTH_SHORT).show();
        });
        yes.setOnClickListener(v -> { dialog.dismiss(); show(activity, onChanged); });
        dialog.show();
    }

    /** İki seçenek aynı görünür (biri ötekinden çekici yapılmaz). */
    private static TextView choice(Activity activity, String label, float density) {
        TextView b = new TextView(activity);
        b.setText(label);
        b.setTextSize(14);
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        b.setGravity(android.view.Gravity.CENTER);
        b.setTextColor(GreenTheme.color(R.color.zd_green));
        b.setMaxLines(1);
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setCornerRadius(12 * density);
        d.setColor(GreenTheme.color(R.color.zd_card));
        d.setStroke(Math.round(1.5f * density), GreenTheme.color(R.color.zd_green));
        b.setBackground(d);
        b.setClickable(true);
        b.setFocusable(true);
        b.setContentDescription(label);
        return b;
    }

    private static void apply(Activity activity, MarketCloudRepository repo, String name, boolean show, Runnable onChanged) {
        repo.setLeagueProfile(name, show, (saved, nowShown, error) -> {
            if (activity.isFinishing()) return;
            String text = error != null
                    ? "Kaydedilemedi: internet bağlantını kontrol et."
                    : show ? "Ligde \"" + saved + "\" olarak görüneceksin." : "Adın ligden kaldırıldı.";
            Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
            if (onChanged != null) onChanged.run();
        });
    }
}
